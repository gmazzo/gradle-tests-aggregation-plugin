@file:OptIn(ExperimentalPathApi::class)

package io.github.gmazzo.test.aggregation

import io.github.gmazzo.test.aggregation.TestAggregationCoverageReport.Variant
import java.io.File
import javax.inject.Inject
import kotlin.io.path.ExperimentalPathApi
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.GroovyBuilderScope
import org.gradle.kotlin.dsl.withGroovyBuilder
import org.gradle.workers.WorkerExecutor


@CacheableTask
public abstract class AggregatedTestCoverageTask : DefaultTask() {

    @get:Inject
    protected abstract val objects: ObjectFactory

    @get:Inject
    protected abstract val workerExecutor: WorkerExecutor

    @get:Internal
    public abstract val variants: SetProperty<Variant>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    internal val variantsSources =
        variants.map { v -> v.map { it.sources.asFileTree } }

    @get:Classpath
    @get:SkipWhenEmpty
    internal val variantsClasses =
        variants.map { v -> v.map { it.classes.asFileTree } }

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    internal val variantsCoverageData =
        variants.map { v -> v.map { it.coverageData.asFileTree } }

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

    @TaskAction
    internal fun generateCoverageReport() {
        val htmlDir = htmlOutputLocation.asFile.orNull?.apply { deleteRecursively() }
        val xmlFile = xmlOutputLocation.asFile.orNull?.apply { deleteRecursively() }
        val csvFile = csvOutputLocation.asFile.orNull?.apply { deleteRecursively() }
        val variants = variants.get()

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
            resources(variant.classes.asFileTree)
        }
        "sourcefiles" {
            resources(variant.sources.asFileTree)
        }
        "executiondata" {
            resources(variant.executionData)
        }
    }

    private fun GroovyBuilderScope.resources(files: Iterable<File>) {
        "resources" {
            for (file in files) {
                "file"("file" to file.absolutePath.replace("$$", "$$$$"))
            }
        }
    }

    // Coverage directories (e.g. AGP's device `coverageDir`) may hold non-execution data files (`metadata.txt`)
    // JaCoCo fails to load. They are filtered here and not at the source, since the published element must be
    // the directory itself: its content does not exist yet when the aggregating project resolves it
    private val Variant.executionData
        get() = coverageData.files.flatMap { file ->
            when {
                file.isDirectory -> file.walkTopDown()
                    .filter { it.isFile && it.extension in EXECUTION_DATA_EXTENSIONS }
                    .toList()

                file.isFile -> listOf(file)
                else -> emptyList()
            }
        }

    private companion object {
        val EXECUTION_DATA_EXTENSIONS = setOf("ec", "exec")
    }

}
