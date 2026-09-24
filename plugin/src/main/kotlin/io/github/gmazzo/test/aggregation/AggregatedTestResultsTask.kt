@file:OptIn(ExperimentalPathApi::class)

package io.github.gmazzo.test.aggregation

import io.github.gmazzo.test.aggregation.TestAggregationResultsReport.Variant
import java.nio.file.Path
import javax.inject.Inject
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.name
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.internal.tasks.testing.junit.result.JUnitXmlResultOptions
import org.gradle.api.internal.tasks.testing.report.generic.GenericHtmlTestReportGenerator
import org.gradle.api.internal.tasks.testing.report.generic.JunitXmlTestReportGenerator
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.newInstance
import org.gradle.kotlin.dsl.setProperty

@CacheableTask
public abstract class AggregatedTestResultsTask : DefaultTask() {

    @get:Inject
    protected abstract val objects: ObjectFactory

    @Transient
    @get:Internal
    public val variants: SetProperty<Variant> = objects.setProperty()

    @get:Internal
    protected abstract val isolatedVariants: SetProperty<Variant>

    @get:Input
    internal val variantsNames =
        isolatedVariants.map { v -> v.map { it.name } }

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    @get:SkipWhenEmpty
    internal val variantsBinaryData =
        isolatedVariants.map { v -> v.map { it.binaryData.asFileTree } }

    @get:Input
    @get:Optional
    public abstract val htmlRequired: Property<Boolean>

    @get:OutputDirectory
    @get:Optional
    public abstract val htmlOutputLocation: DirectoryProperty

    @get:Input
    @get:Optional
    public abstract val junitXMLRequired: Property<Boolean>

    @get:OutputDirectory
    @get:Optional
    public abstract val junitXMLOutputLocation: DirectoryProperty

    init {
        dependsOn(variants.map { set -> set.map { it.dependsOn } })

        isolatedVariants
            .value(variants.map { set -> set.map { it.isolated }})
            .finalizeValueOnRead()

        htmlRequired
            .convention(true)

        htmlOutputLocation
            .convention(project.layout.buildDirectory.dir("reports/$name/html"))

        junitXMLRequired
            .convention(false)

        junitXMLOutputLocation
            .convention(project.layout.buildDirectory.dir("reports/$name/junit-xml"))
    }

    @TaskAction
    internal fun generateHTMLReport() {
        val outputDir = htmlOutputLocation.asFile.orNull?.toPath() ?: return
        outputDir.deleteRecursively()
        if (!htmlRequired.getOrElse(true)) return

        val generator = objects.newInstance<GenericHtmlTestReportGenerator>(outputDir)
        generator.generate(isolatedVariants.get().flatMap { it.binaryDataDirs })

        logger.lifecycle("View generated report at ${outputDir.resolve("index.html").toUri()}")
    }

    @TaskAction
    internal fun generateXMLReport() {
        val outputDir = junitXMLOutputLocation.asFile.orNull?.toPath() ?: return
        outputDir.deleteRecursively()
        if (!junitXMLRequired.getOrElse(true)) return

        val options = JUnitXmlResultOptions(true, true, true, true)

        fun generate(binaryDir: Path, reportDir: Path) = objects
            .newInstance<JunitXmlTestReportGenerator>(reportDir, options)
            .generate(listOf(binaryDir))

        for (variant in isolatedVariants.get()) {
            val variantOutDir = outputDir.resolve(variant.name.replace(':', '_'))
            val binaryDirs = variant.binaryDataDirs

            when (binaryDirs.size) {
                1 -> generate(binaryDirs.single(), variantOutDir)
                else -> {
                    val used = mutableMapOf<String, Int>()
                    for (binaryDir in binaryDirs) {
                        val count = used.compute(binaryDir.name) { _, count -> (count ?: 0) + 1 }
                        val name = when (count) {
                            1 -> binaryDir.name
                            else -> "${binaryDir.name}-$count"
                        }
                        generate(binaryDir, variantOutDir.resolve(name))
                    }
                }
            }
        }
    }

    private val Variant.isolated
        get() = objects.newInstance<Variant>(name).apply new@{
            this@new.binaryData.from(this@isolated.binaryData).disallowChanges()
        }

    private val Variant.binaryDataDirs
        get() = binaryData.asFileTree.mapNotNullTo(linkedSetOf()) {
            it.parentFile.toPath().takeIf { dir ->
                // TODO check if it's possible to transform Android's device tests format to Gradle's one
                // expected files from SerializableTestResultStore
                dir.resolve("results-generic.bin").exists() &&
                    dir.resolve("output-events.bin").exists()
            }
        }

}
