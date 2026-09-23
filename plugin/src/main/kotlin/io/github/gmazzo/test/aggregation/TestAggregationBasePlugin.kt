package io.github.gmazzo.test.aggregation

import java.io.File
import java.lang.ref.WeakReference
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ConfigurablePublishArtifact
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationPublications
import org.gradle.api.artifacts.ConfigurationVariant
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE
import org.gradle.api.attributes.Category.VERIFICATION
import org.gradle.api.attributes.HasConfigurableAttributes
import org.gradle.api.attributes.LibraryElements.CLASSES
import org.gradle.api.attributes.LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE
import org.gradle.api.attributes.VerificationType.JACOCO_RESULTS
import org.gradle.api.attributes.VerificationType.MAIN_SOURCES
import org.gradle.api.attributes.VerificationType.TEST_RESULTS
import org.gradle.api.attributes.VerificationType.VERIFICATION_TYPE_ATTRIBUTE
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.reporting.ReportingExtension
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.newInstance
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withType
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.testing.jacoco.plugins.JacocoPlugin.ANT_CONFIGURATION_NAME

public class TestAggregationBasePlugin : Plugin<Project> {

    public companion object {
        public const val DEFAULT_RESULTS_NAME: String = "aggregatedTestResults"
        public const val DEFAULT_COVERAGE_NAME: String = "aggregatedTestCoverage"

        public const val USAGE_AGGREGATED_TEST_RESULTS: String = "aggregated-test-results"
        public const val USAGE_AGGREGATED_TEST_COVERAGE: String = "aggregated-test-coverage"
        public const val TYPE_VARIANTS_LIST: String = "variants-list"
        public val REPORT_ATTRIBUTE: Attribute<String> =
            Attribute.of("io.github.gmazzo.test.aggregation.report", String::class.java)
        public val REPORT_VARIANT_ATTRIBUTE: Attribute<String> =
            Attribute.of("io.github.gmazzo.test.aggregation.report.variant", String::class.java)
    }

    override fun apply(target: Project): Unit = with(target) {
        objectsRef = WeakReference(objects)

        apply(plugin = "reporting-base")

        val jacocoAntConfig = configurations.dependencyScope("aggregatedTestCoverageJacocoAnt") {
            plugins.withId("jacoco") {
                extendsFrom(configurations.getByName(ANT_CONFIGURATION_NAME))
            }
        }

        val jacocoAntClasspath =
            configurations.resolvable("aggregatedTestCoverageJacocoAntClasspath") {
                extendsFrom(jacocoAntConfig.get())
            }

        dependencies {
            jacocoAntConfig(BuildConfig.JACOCO_ANT_DEPENDENCY.run { "$first:$second" })
            jacocoAntConfig(BuildConfig.JACOCO_ANT_GROUPING_DEPENDENCY) {
                capabilities {
                    requireCapability(BuildConfig.JACOCO_ANT_GROUPING_CAPABILITY)
                }
            }
        }

        val reporting = the<ReportingExtension>()
        with(reporting.reports) {
            registerFactory(TestAggregationResultsReport::class.java) {
                createResultsReport(it, reporting.baseDirectory)
            }
            registerFactory(TestAggregationCoverageReport::class.java) {
                createCoverageReport(it, reporting.baseDirectory, jacocoAntClasspath)
            }

            withType<AbstractTestAggregationReport<*, *>> { configure() }
        }

        tasks.register("aggregatedTestsReport") {
            group = LifecycleBasePlugin.VERIFICATION_GROUP
            description = "Aggregates test results and coverage for all test variants"

            dependsOn(
                reporting.reports.withType<TestAggregationResultsReport>(),
                reporting.reports.withType<TestAggregationCoverageReport>(),
            )
        }

        plugins.withId("org.jetbrains.kotlin.multiplatform") {
            with(KMPSupport) { installBase() }
        }

        for (androidPluginId in listOf(
            "com.android.base",
            "com.android.kotlin.multiplatform.library"
        )) {
            plugins.withId(androidPluginId) {
                with(AndroidSupport) { installBase() }
            }
        }
    }

    private fun Project.createResultsReport(name: String, baseDirectory: DirectoryProperty) =
        objects.newInstance<DefaultTestAggregationResultsReport>(name).apply report@{

            variants.configureEach {

                aggregate
                    .convention(true)
                    .finalizeValueOnRead()

                binaryData.finalizeValueOnRead()

            }

            htmlRequired
                .convention(true)
                .finalizeValueOnRead()

            htmlOutputLocation
                .convention(baseDirectory.dir(name.defaultResultsDir + "/html"))
                .finalizeValueOnRead()

            junitXMLRequired
                .convention(true)
                .finalizeValueOnRead()

            junitXMLOutputLocation
                .convention(baseDirectory.dir(name.defaultResultsDir + "/junit"))
                .finalizeValueOnRead()

            reportTask =
                tasks.register<AggregatedTestResultsTask>("aggregatedTestResultsReport${name.taskSuffix}") task@{
                    group = LifecycleBasePlugin.VERIFICATION_GROUP
                    description = "Aggregates test results for all test variants"

                    dependsOn(filteredVariants.map { it.dependsOn })
                    this@task.variants.addAll(this@report.filteredVariants.map { it.isolated })
                    this@task.variants.addAll(this@report.variantsFromDependencies)
                    this@task.htmlRequired.value(this@report.htmlRequired)
                    this@task.htmlOutputLocation.value(this@report.htmlOutputLocation)
                    this@task.junitXMLRequired.value(this@report.junitXMLRequired)
                    this@task.junitXMLOutputLocation.value(this@report.junitXMLOutputLocation)
                }
        }

    private fun Project.createCoverageReport(
        name: String,
        baseDirectory: DirectoryProperty,
        jacocoAntClasspath: Provider<out Configuration>
    ) =
        objects.newInstance<DefaultTestAggregationCoverageReport>(name).apply report@{

            variants.configureEach variant@{

                sources.finalizeValueOnRead()

                classes.finalizeValueOnRead()

                coverageData.finalizeValueOnRead()

            }

            content {

                includes.finalizeValueOnRead()

                excludes.finalizeValueOnRead()

            }

            groupByVariant
                .convention(true)
                .finalizeValueOnRead()

            htmlRequired
                .convention(true)
                .finalizeValueOnRead()

            htmlOutputLocation
                .convention(baseDirectory.dir(name.defaultCoverageDir + "/html"))
                .finalizeValueOnRead()

            xmlRequired
                .convention(true)
                .finalizeValueOnRead()

            xmlOutputLocation
                .convention(baseDirectory.file(name.defaultCoverageDir + "/coverage.xml"))
                .finalizeValueOnRead()

            csvRequired
                .convention(true)
                .finalizeValueOnRead()

            csvOutputLocation
                .convention(baseDirectory.file(name.defaultCoverageDir + "/coverage.csv"))
                .finalizeValueOnRead()

            reportTask =
                tasks.register<AggregatedTestCoverageTask>("aggregatedTestCoverageReport${name.taskSuffix}") task@{
                    group = LifecycleBasePlugin.VERIFICATION_GROUP
                    description = "Aggregates test coverage report for all test variants"

                    dependsOn(filteredVariants.map { it.dependsOn })
                    this@task.variants.addAll(this@report.filteredVariants.map { it.isolated })
                    this@task.variants.addAll(this@report.variantsFromDependencies)
                    this@task.jacocoClasspath.from(jacocoAntClasspath)
                    this@task.groupByVariant.value(this@report.groupByVariant)
                    this@task.htmlRequired.value(this@report.htmlRequired)
                    this@task.htmlOutputLocation.value(this@report.htmlOutputLocation)
                    this@task.xmlRequired.value(this@report.xmlRequired)
                    this@task.xmlOutputLocation.value(this@report.xmlOutputLocation)
                    this@task.csvRequired.value(this@report.csvRequired)
                    this@task.csvOutputLocation.value(this@report.csvOutputLocation)
                }
        }

    context(project: Project)
    private val TestAggregationResultsReport.Variant.isolated
        get() = project.objects.newInstance<TestAggregationResultsReport.Variant>(name).apply new@{
            this@new.binaryData.from(this@isolated.binaryData).disallowChanges()
        }

    context(project: Project, report: TestAggregationCoverageReport)
    private val TestAggregationCoverageReport.Variant.isolated
        get() = project.objects.newInstance<TestAggregationCoverageReport.Variant>(this@isolated.name)
            .apply new@{
                this@new.sources.from(this@isolated.sources).disallowChanges()
                this@new.classes.from(this@isolated.classes.contentFiltered).disallowChanges()
                this@new.coverageData.from(this@isolated.coverageData).disallowChanges()
            }

    context(project: Project)
    private fun AbstractTestAggregationReport<*, *>.configure() {
        val usage: Usage = objects.named(
            when (this) {
                is TestAggregationResultsReport -> USAGE_AGGREGATED_TEST_RESULTS
                is TestAggregationCoverageReport -> USAGE_AGGREGATED_TEST_COVERAGE
                else -> return
            }
        )

        aggregateFrom = project.configurations.resolvable("${this@configure.name}AggregateFrom") {
            attributes {
                attribute(CATEGORY_ATTRIBUTE, objects.named(VERIFICATION))
                attribute(USAGE_ATTRIBUTE, usage)
                attribute(REPORT_ATTRIBUTE, this@configure.name)
            }
        }

        val variantsFile = project.providers.of(VariantsFileValueSource::class.java) {
            parameters {
                variantsFile.value(
                    project.layout.buildDirectory
                        .file("intermediates/test-aggregation/${this@configure.name}/variants.txt")
                )
                variantNames.value(project.provider { this@configure.filteredVariants.names })
            }
        }

        project.configurations.consumable(this@configure.name) config@{
            attributes { allOf(aggregateFrom.get()) }
            outgoing {
                variants.create("variants-spec") {
                    attributes {
                        allOf(this@config)
                        attribute(VERIFICATION_TYPE_ATTRIBUTE, objects.named(TYPE_VARIANTS_LIST))
                    }
                    artifact(variantsFile)
                }

                this@configure.variants.all variant@{
                    when (this@variant) {
                        is TestAggregationResultsReport.Variant -> createResultsVariant()
                        is TestAggregationCoverageReport.Variant -> createCoverageVariant()
                    }
                }
            }
        }
    }

    context(
        project: Project,
        variant: TestAggregationResultsReport.Variant,
        config: Configuration,
    )
    private fun ConfigurationPublications.createResultsVariant() {
        variants.create("${variant.name}-binary-results") {
            attributes {
                allOf(config)
                attribute(REPORT_VARIANT_ATTRIBUTE, variant.name)
                attribute(
                    VERIFICATION_TYPE_ATTRIBUTE,
                    project.objects.named(TEST_RESULTS)
                )
            }
            artifacts(variant.binaryData.elements) {
                builtBy(variant.dependsOn)
            }
        }
    }

    context(
        project: Project,
        variant: TestAggregationCoverageReport.Variant,
        config: Configuration,
    )
    private fun ConfigurationPublications.createCoverageVariant() {
        variants.create("${variant.name}-classes") {
            attributes {
                allOf(config)
                attribute(REPORT_VARIANT_ATTRIBUTE, variant.name)
                attribute(LIBRARY_ELEMENTS_ATTRIBUTE, project.objects.named(CLASSES))
            }
            artifacts(variant.classes.elements) {
                builtBy(variant.dependsOn)
            }
        }
        variants.create("${variant.name}-main-sources") {
            attributes {
                allOf(config)
                attribute(REPORT_VARIANT_ATTRIBUTE, variant.name)
                attribute(
                    VERIFICATION_TYPE_ATTRIBUTE,
                    project.objects.named(MAIN_SOURCES)
                )
            }
            artifacts(variant.sources.elements) {
                builtBy(variant.dependsOn)
            }
        }
        variants.create("${variant.name}-coverage-data") {
            attributes {
                allOf(config)
                attribute(REPORT_VARIANT_ATTRIBUTE, variant.name)
                attribute(
                    VERIFICATION_TYPE_ATTRIBUTE,
                    project.objects.named(JACOCO_RESULTS)
                )
            }
            artifacts(variant.coverageData.elements) {
                builtBy(variant.dependsOn)
            }
        }
    }

    context(project: Project)
    private val TestAggregationResultsReport.variantsFromDependencies
        get() = aggregateFrom().map { view ->
            view.artifacts.flatMap { artifact ->
                val projectPath = artifact.projectPath

                artifact.file.readLines().map { variantName ->
                    val depsBinaryData = aggregateFrom(artifact) {
                        attribute(REPORT_VARIANT_ATTRIBUTE, variantName)
                        attribute(VERIFICATION_TYPE_ATTRIBUTE, project.objects.named(TEST_RESULTS))
                    }.map { it.files }

                    project.objects.newInstance<TestAggregationResultsReport.Variant>("$projectPath:$variantName")
                        .apply {
                            aggregate.disallowChanges()
                            dependsOn.value(setOf(depsBinaryData)).disallowChanges()
                            binaryData.from(depsBinaryData).disallowChanges()
                        }
                }
            }
        }

    context(project: Project)
    private val TestAggregationCoverageReport.variantsFromDependencies
        get() = aggregateFrom().map { view ->
            view.artifacts.flatMap { artifact ->
                val projectPath = artifact.projectPath
                val variants = artifact.file.readLines()

                variants.map { variantName ->
                    val depsSources = aggregateFrom(artifact) {
                        attribute(REPORT_VARIANT_ATTRIBUTE, variantName)
                        attribute(VERIFICATION_TYPE_ATTRIBUTE, project.objects.named(MAIN_SOURCES))
                    }.map { it.files }

                    val depsClasses = aggregateFrom(artifact) {
                        attribute(REPORT_VARIANT_ATTRIBUTE, variantName)
                        attribute(LIBRARY_ELEMENTS_ATTRIBUTE, project.objects.named(CLASSES))
                    }.map { it.files.contentFiltered }

                    val depsCoverageData = aggregateFrom(artifact) {
                        attribute(REPORT_VARIANT_ATTRIBUTE, variantName)
                        attribute(
                            VERIFICATION_TYPE_ATTRIBUTE,
                            project.objects.named(JACOCO_RESULTS)
                        )
                    }.map { it.files }

                    val aggregatedName = when (variants.size) {
                        1 -> projectPath
                        else -> "$projectPath:$variantName"
                    }
                    project.objects
                        .newInstance<TestAggregationCoverageReport.Variant>(aggregatedName)
                        .apply {
                            aggregate.disallowChanges()
                            dependsOn.value(setOf(depsSources, depsClasses, depsCoverageData))
                                .disallowChanges()
                            sources.from(depsSources).disallowChanges()
                            classes.from(depsClasses).disallowChanges()
                            coverageData.from(depsCoverageData).disallowChanges()
                        }
                }
            }
        }

    context(project: Project)
    private fun TestAggregationReport<*, *>.aggregateFrom(
        forArtifact: ResolvedArtifactResult? = null,
        forAttrs: Action<AttributeContainer> = {
            attribute(VERIFICATION_TYPE_ATTRIBUTE, project.objects.named(TYPE_VARIANTS_LIST))
        },
    ) = aggregateFrom.map { config ->
        config.incoming.artifactView {
            componentFilter {
                it is ProjectComponentIdentifier &&
                    (forArtifact == null || it.projectPath == forArtifact.projectPath)
            }
            forAttrs.execute(attributes)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun AttributeContainer.allOf(config: HasConfigurableAttributes<*>) {
        for (attribute in config.attributes.keySet() as Set<Attribute<Any>>) {
            attribute(attribute, config.attributes.getAttribute(attribute) as Any)
        }
    }

    // workaround of missing `.artifacts` DSL in `ConfigurationVariant`
    context(project: Project)
    private fun ConfigurationVariant.artifacts(
        provider: Provider<out Iterable<*>>,
        configure: Action<ConfigurablePublishArtifact> = {},
    ) = artifacts.addAllLater(provider.map {
        val helper = project.configurations.detachedConfiguration().outgoing

        helper.artifacts(provider, configure)
        helper.artifacts
    })

    private val String.defaultResultsDir
        get() = when (this) {
            DEFAULT_RESULTS_NAME, DEFAULT_COVERAGE_NAME -> "aggregated-test-results"
            else -> "aggregated-test/$this/results"
        }

    private val String.defaultCoverageDir
        get() = when (this) {
            DEFAULT_RESULTS_NAME, DEFAULT_COVERAGE_NAME -> "aggregated-test-coverage"
            else -> "aggregated-test/$this/coverage"
        }

    private val String.taskSuffix
        get() = when (this) {
            DEFAULT_RESULTS_NAME, DEFAULT_COVERAGE_NAME -> ""
            else -> "For${capitalized}"
        }

    private val ResolvedArtifactResult.projectPath
        get() = (id.componentIdentifier as ProjectComponentIdentifier).projectPath

    context(report: TestAggregationCoverageReport)
    private val FileCollection.contentFiltered
        get() = asFileTree.matching {
            include(report.content.includes.get())
            exclude(report.content.excludes.get())
        }

    internal abstract class VariantsFileValueSource :
        ValueSource<File, VariantsFileValueSourceParams> {

        override fun obtain() =
            parameters.variantsFile.asFile.get().apply {
                parentFile.mkdirs()
                writer().use { parameters.variantNames.get().forEach(it::appendLine) }
            }

    }

    internal interface VariantsFileValueSourceParams : ValueSourceParameters {
        val variantNames: SetProperty<String>
        val variantsFile: RegularFileProperty
    }

}
