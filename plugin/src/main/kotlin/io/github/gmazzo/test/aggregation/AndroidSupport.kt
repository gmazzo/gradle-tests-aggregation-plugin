package io.github.gmazzo.test.aggregation

import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.dsl.BuildType
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.DeviceTest
import com.android.build.api.variant.HostTest
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.api.variant.TestComponent
import com.android.build.api.variant.Variant as AndroidVariant
import com.android.build.gradle.internal.tasks.AndroidTestTask
import com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestTask
import com.android.build.gradle.internal.tasks.ManagedDeviceInstrumentationTestTask
import com.android.build.gradle.internal.tasks.ManagedDeviceTestTask
import com.android.build.gradle.tasks.factory.AndroidUnitTest
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.testing.AbstractTestTask
import org.gradle.kotlin.dsl.addAndroidVariant
import org.gradle.kotlin.dsl.aggregateTests
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getByName
import org.gradle.kotlin.dsl.gradleExtensions
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.typeOf
import org.gradle.kotlin.dsl.withType
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension

internal object AndroidSupport {

    private val classesArtifact by lazy {
        try {
            ScopedArtifact.POST_COMPILATION_CLASSES

        } catch (_: NoClassDefFoundError) {
            // AGP 8.3 and below
            ScopedArtifact.CLASSES
        }
    }

    val Project.jacocoDependency
        get() = the<CommonExtension>().testCoverage.jacocoVersion
            .let { dependencies.create(BuildConfig.JACOCO_ANT_DEPENDENCY.first + ':' + it) }

    fun Project.installBase() = configure<ReportingExtension> {
        if (!isKMP) {
            plugins.withId("jacoco") {
                addRobolectricTestsSupport()
            }
        }

        reports.withType<TestAggregationResultsReport> report@{
            (this@report as ExtensionAware).extensions
                .add(
                    typeOf<TestAggregationReportAndroidExtension>(),
                    "addAndroidVariant",
                    ResultsExtension(project, this@report)
                )
        }
        reports.withType<TestAggregationCoverageReport> report@{
            (this@report as ExtensionAware).extensions
                .add(
                    typeOf<TestAggregationReportAndroidExtension>(),
                    "addAndroidVariant",
                    CoverageExtension(project, this@report)
                )
        }

        androidComponents.onVariants { variant ->
            val variantAggregate = variant.gradleExtensions.aggregateTests(objects)

            for (testComponent in variant.nestedComponents) {
                if (testComponent !is TestComponent) continue

                testComponent.gradleExtensions.aggregateTests(objects)
                    .convention(variantAggregate.map { it && testComponent.shouldAggregateByDefault })
            }
        }
    }

    fun Project.install(
        testResults: TestAggregationResultsReport,
        testCoverage: TestAggregationCoverageReport,
    ) {
        androidComponents.onVariants { variant ->
            val buildType = resolveBuildType(variant)

            for (testComponent in variant.nestedComponents) {
                if (testComponent !is TestComponent) continue

                val coverageEnabled = when (testComponent) {
                    is HostTest -> buildType?.enableUnitTestCoverage ?: false
                    is DeviceTest -> buildType?.enableAndroidTestCoverage ?: false
                    else -> false
                }
                if (coverageEnabled) {
                    testCoverage.addAndroidVariant(variant)
                    break
                }
            }

            if (variant.nestedComponents.any { it is TestComponent }) {
                testResults.addAndroidVariant(variant)
            }

            // KMP does not set `buildType` but applies `jacoco` instead
            if (buildType == null) {
                plugins.withId("jacoco") {
                    testCoverage.addAndroidVariant(variant)
                }
            }
        }
    }

    private fun Project.addRobolectricTestsSupport() {
        val android = the<CommonExtension>()
        val robolectricSupport = objects.property<Boolean>()
            .convention(true)
            .apply { finalizeValueOnRead() }

        (android as ExtensionAware).extensions.add("coverageRobolectricSupport", robolectricSupport)

        afterEvaluate {
            if (robolectricSupport.get()) {
                tasks.withType<AbstractTestTask>().configureEach task@{
                    this@task.configure<JacocoTaskExtension> {
                        isIncludeNoLocationClasses = true
                        excludes = listOf("jdk.internal.*")
                    }
                }
            }
        }
    }

    private fun Project.resolveBuildType(variant: AndroidVariant): BuildType? {
        val buildType = variant.buildType ?: return null
        val android = extensions.findByName("android") ?: return null

        return (android as CommonExtension).buildTypes[buildType]
    }

    private fun Project.testTasksOf(component: TestComponent, configure: Action<Task>) =
        when (component) {
            is HostTest -> project
                .tasksMatching(
                    regex = "(test|validate)${Regex.escape(component.name.capitalized)}".toRegex(),
                    configure
                )

            // TODO add managed devices support
            is DeviceTest -> project
                .tasksMatching(name = "connected${component.name.capitalized}", configure)

            else -> provider { emptyList() }
        }

    private val Project.android
        get() = extensions.getByName<CommonExtension>("android")

    private val Project.androidComponents
        get() = extensions.getByName<AndroidComponentsExtension<*, *, *>>("androidComponents")

    private val TestComponent.shouldAggregateByDefault
        get() = this is HostTest

    private val Project.isKMP
        get() = plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")

    class ResultsExtension(
        private val project: Project,
        private val report: TestAggregationResultsReport,
    ) : TestAggregationReportAndroidExtension {

        override fun invoke(androidVariant: AndroidVariant) {
            val testsComponents = androidVariant.nestedComponents.filterIsInstance<TestComponent>()

            check(testsComponents.isNotEmpty()) {
                "Test aggregation is only supported for variants with tests, but ${androidVariant.name} does not have any"
            }

            for (testComponent in testsComponents) {
                val testTask = project.testTasksOf(testComponent) task@{
                    this@task.aggregateTests = testComponent.aggregateTests
                }

                val variant = report.variants.maybeCreate(testComponent.name)
                variant.dependsOn(testTask)
                variant.aggregate.convention(testComponent.aggregateTests)
                variant.binaryData.from(testTask.map { list ->
                    list.mapNotNull {
                        when (it) {
                            is AbstractTestTask -> it.binaryResultsDirectory
                            is AndroidTestTask -> it.resultsDir
                            else -> null
                        }
                    }
                })
            }
        }

    }

    class CoverageExtension(
        private val project: Project,
        private val report: TestAggregationCoverageReport,
    ) : TestAggregationReportAndroidExtension {

        override fun invoke(androidVariant: AndroidVariant) {
            val classesJars = project.objects.listProperty<RegularFile>()
            val classesDirs = project.objects.listProperty<Directory>()
            // TODO review if we can make it work without creating a Sync task
            val classesTask =
                project.tasks.register<Sync>("${report.name}${androidVariant.name.capitalized}Classes") {
                    // from(classesJars) note: intentionally adds R.class and related files
                    from(classesDirs)
                    into("$temporaryDir")
                }

            androidVariant.artifacts
                .forScope(ScopedArtifacts.Scope.PROJECT)
                .use(classesTask)
                .toGet(classesArtifact, { classesJars }) { classesDirs }

            val variant = report.variants.maybeCreate(androidVariant.kmpAwareName)
            variant.dependsOn(classesTask)
            variant.aggregate.convention(androidVariant.aggregateTests)
            androidVariant.sources.java?.all?.let(variant.sources::from)
            androidVariant.sources.kotlin?.all?.let(variant.sources::from)
            variant.classes.from(classesTask)

            for (testComponent in androidVariant.nestedComponents) {
                if (testComponent !is TestComponent) continue

                val testAggregate = testComponent.aggregateTests
                val testTask = project.testTasksOf(testComponent) task@{
                    this@task.aggregateTests = testAggregate
                }

                variant.dependsOn(testAggregate.map { if (it) testTask else emptyArray<Any>() })
                variant.coverageData.from(testAggregate.zip(testTask) { agg, list ->
                    if (agg) list.mapNotNull { task ->
                        when (task) {
                            is AndroidUnitTest -> task.jacocoCoverageOutputFile
                            is DeviceProviderInstrumentTestTask -> task.coverageDirectory
                            is ManagedDeviceTestTask -> task.getCoverageDirectory()
                            is ManagedDeviceInstrumentationTestTask -> task.getCoverageDirectory()
                            is AbstractTestTask -> task.coverageFile
                            else -> null
                        }
                    }
                    else emptyArray<Any>()
                })
            }
        }

        private val AndroidVariant.kmpAwareName
            get() = if (project.isKMP && name == "androidMain") "android" else name

    }

}
