package io.github.gmazzo.test.aggregation

import io.github.gmazzo.test.aggregation.TestAggregationCoverageReport.Content
import io.github.gmazzo.test.aggregation.TestAggregationCoverageReport.Variant
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.GroovyBuilderScope
import org.gradle.kotlin.dsl.newInstance
import org.gradle.kotlin.dsl.setProperty
import org.gradle.kotlin.dsl.withGroovyBuilder
import org.gradle.workers.WorkerExecutor

@CacheableTask
public abstract class AggregatedTestCoverageTask : DefaultTask() {

    @get:Inject
    protected abstract val objects: ObjectFactory

    @get:Inject
    protected abstract val workerExecutor: WorkerExecutor

    @Transient
    @get:Internal
    public val variants: SetProperty<Variant> = objects.setProperty()

    @get:Nested
    public abstract val content: Content

    @get:Internal
    protected abstract val isolatedVariants: SetProperty<Variant>

    @get:Input
    internal val variantsNames =
        isolatedVariants.map { v -> v.map { it.name } }

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    internal val variantsSources =
        isolatedVariants.map { v -> v.map { it.sources.asFileTree } }

    @get:Classpath
    @get:SkipWhenEmpty
    internal val variantsClasses =
        isolatedVariants.map { v -> v.map { it.classes.asFileTree } }

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    internal val variantsCoverageData =
        isolatedVariants.map { v -> v.map { it.coverageData.asFileTree } }

    @get:Classpath
    public abstract val jacocoClasspath: ConfigurableFileCollection

    @get:Input
    @get:Optional
    public abstract val htmlRequired: Property<Boolean>

    @get:OutputDirectory
    @get:Optional
    public abstract val htmlOutputLocation: DirectoryProperty

    @get:Input
    @get:Optional
    public abstract val xmlRequired: Property<Boolean>

    @get:OutputFile
    @get:Optional
    public abstract val xmlOutputLocation: RegularFileProperty

    @get:Input
    @get:Optional
    public abstract val csvRequired: Property<Boolean>

    @get:OutputFile
    @get:Optional
    public abstract val csvOutputLocation: RegularFileProperty

    init {
        dependsOn(variants.map { set -> set.map { it.dependsOn } })

        isolatedVariants
            .value(variants.map { set -> set.map { it.isolated } })
            .finalizeValueOnRead()

        htmlRequired
            .convention(true)

        htmlOutputLocation
            .convention(project.layout.buildDirectory.dir("reports/$name/html"))

        xmlRequired
            .convention(false)

        xmlOutputLocation
            .convention(project.layout.buildDirectory.file("reports/$name/jacoco.xml"))

        csvRequired
            .convention(false)

        csvOutputLocation
            .convention(project.layout.buildDirectory.file("reports/$name/jacoco.csv"))
    }

    @TaskAction
    internal fun generateCoverageReport() {
        val htmlDir = htmlOutputLocation.asFile.orNull?.apply { deleteRecursively() }
        val xmlFile = xmlOutputLocation.asFile.orNull?.apply { deleteRecursively() }
        val csvFile = csvOutputLocation.asFile.orNull?.apply { deleteRecursively() }
        val variants = isolatedVariants.get()

        ant.withGroovyBuilder {
            "taskdef"(
                "name" to "jacocoReport",
                "classname" to "io.github.gmazzo.test.aggregation.jacoco.GroupingReportTask",
                "classpath" to jacocoClasspath.asPath
            )

            "jacocoReport" {
                "structure"(mapOf("name" to this@AggregatedTestCoverageTask.name)) {
                    when (variants.size) {
                        1 -> bindData(variants.single())
                        else -> for (variant in variants) {
                            "group"("name" to variant.name) {
                                bindData(variant)
                            }
                        }
                    }
                }
                if (htmlDir != null) {
                    "html"(mapOf("destdir" to htmlDir))
                }
                if (xmlFile != null) {
                    "xml"(mapOf("destfile" to xmlFile))
                }
                if (csvFile != null) {
                    "csv"(mapOf("destfile" to csvFile))
                }
            }
        }

        if (htmlDir != null) {
            logger.lifecycle("View generated report at ${htmlDir.resolve("index.html").toURI()}")
        }
    }

    private fun GroovyBuilderScope.bindData(variant: Variant) {
        "classfiles" {
            resources(variant.classes)
        }
        "sourcefiles" {
            resources(variant.sources)
        }
        "executiondata" {
            resources(variant.coverageData, awaitFileClosed = true)
        }
    }

    private fun GroovyBuilderScope.resources(
        files: FileCollection,
        awaitFileClosed: Boolean = false
    ) {
        "resources" {
            for (file in files.asFileTree) {
                // JaCoCo agent sometimes keeps running after the task has ended, causing a corrupted file read
                if (awaitFileClosed) {
                    file.waitUntilReady()
                }

                "file"("file" to file.absolutePath.replace("$$", "$$$$"))
            }
        }
    }

    private val Variant.isolated
        get() = objects.newInstance<Variant>(this@isolated.name)
            .apply new@{
                this@new.sources.from(this@isolated.sources).disallowChanges()
                this@new.classes.from(this@isolated.classes.filtered(content)).disallowChanges()
                this@new.coverageData.from(this@isolated.coverageData).disallowChanges()
            }

    private fun File.waitUntilReady(
        timeout: Duration = 10.seconds,
        waitStep: Duration = 500.milliseconds,
    ): Boolean {
        val until = System.currentTimeMillis() + timeout.inWholeMilliseconds
        while (until > System.currentTimeMillis()) {
            if (!exists()) return false

            try {
                // if it's open, the lock will fail here
                RandomAccessFile(this, "rw").use {
                    it.channel.use { return true }
                }

            } catch (_: IOException) {
                Thread.sleep(waitStep.inWholeMilliseconds)
            }
        }
        return false // Timed out
    }

}
