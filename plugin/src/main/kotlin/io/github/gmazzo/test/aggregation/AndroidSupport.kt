package io.github.gmazzo.test.aggregation

import com.android.build.api.artifact.ScopedArtifact
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
import com.android.build.gradle.tasks.TestSuiteTestTask
import com.android.build.gradle.tasks.factory.AndroidUnitTest
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.SetProperty
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.testing.AbstractTestTask
import org.gradle.kotlin.dsl.addAndroidVariant
import org.gradle.kotlin.dsl.aggregateTests
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByName
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.setProperty
import org.gradle.kotlin.dsl.typeOf
import org.gradle.kotlin.dsl.withType
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension

internal object AndroidSupport {

    fun Project.installBase() = configure<ReportingExtension> {
        if (!isKMP) {
            addRobolectricTestsSupport()
        }

        val aggregatedDevices = computeAggregatedDevices()

        reports.withType<TestAggregationResultsReport> report@{
            (this@report as ExtensionAware).extensions
                .add(
                    typeOf<TestAggregationReportAndroidExtension>(),
                    "addAndroidVariant",
                    ResultsExtension(project, this@report, aggregatedDevices)
                )
        }

        reports.withType<TestAggregationCoverageReport> report@{
            (this@report as ExtensionAware).extensions
                .add(
                    typeOf<TestAggregationReportAndroidExtension>(),
                    "addAndroidVariant",
                    CoverageExtension(project, this@report, aggregatedDevices)
                )
        }

        androidComponents.onVariants { variant ->
            val variantAggregate = variant.aggregateTests

            for (testComponent in variant.nestedComponents) {
                if (testComponent !is TestComponent) continue

                testComponent.aggregateTests
                    .convention(variantAggregate)
            }
        }
    }

    fun Project.install(
        testResults: TestAggregationResultsReport,
        testCoverage: TestAggregationCoverageReport,
    ) {
        androidComponents.onVariants { variant ->
            if (variant.nestedComponents.any { it is TestComponent }) {
                testResults.addAndroidVariant(variant)
                testCoverage.addAndroidVariant(variant)
            }
        }
    }

    private fun Project.addRobolectricTestsSupport() {
        val robolectricSupport = objects.property<Boolean>()
            .convention(true)
            .apply { finalizeValueOnRead() }

        (android as ExtensionAware).extensions.add("coverageRobolectricSupport", robolectricSupport)

        afterEvaluate {
            if (robolectricSupport.get()) {
                plugins.withId("jacoco") {
                    tasks.withType<AbstractTestTask>().configureEach task@{
                        this@task.configure<JacocoTaskExtension> {
                            isIncludeNoLocationClasses = true
                            excludes = listOf("jdk.internal.*")
                        }
                    }
                }
            }
        }
    }

    private fun Project.computeAggregatedDevices() = objects
        .setProperty<String>()
        .apply { finalizeValueOnRead() }
        .also { devices ->
            val testOptions = android?.testOptions ?: return@also

            val aggregateConnected = objects.property<Boolean>()
                .convention(false)
                .also {
                    (testOptions as ExtensionAware).extensions.add(
                        "aggregateConnectedDevices",
                        it
                    )
                }

            devices.addAll(aggregateConnected.map {
                if (it) listOf("connected") else emptyList()
            })

            testOptions.managedDevices.allDevices.configureEach device@{
                val aggregateDevice = (this@device as ExtensionAware).aggregateTests
                    .convention(false)

                devices.addAll(aggregateDevice.map {
                    if (it) listOf(this@device.name) else emptyList()
                })
            }
        }

    private fun Project.testTasksOf(
        devices: Set<String>,
        component: TestComponent,
        configure: Action<Task>,
    ) = when (component) {
        is HostTest -> project.tasksMatching(
            regex = "(test|validate)${Regex.escape(component.name.capitalized)}".toRegex(),
            configure
        )

        is DeviceTest -> project.tasksMatching(
            regex = devices.joinToString(
                prefix = "(",
                separator = "|",
                postfix = ")${Regex.escape(component.name.capitalized)}",
                transform = Regex::escape
            ).toRegex(),
            configure
        )

        else -> provider { emptyList() }
    }

    private val Project.android
        get() = extensions.findByName("android") as CommonExtension?

    private val Project.androidComponents
        get() = extensions.getByName<AndroidComponentsExtension<*, *, *>>("androidComponents")

    private val Project.isKMP
        get() = plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")

    private val AndroidVariant.testComponents
        get(): List<TestComponent> = nestedComponents
            .filterIsInstance<TestComponent>()
            .also {
                check(it.isNotEmpty()) {
                    "Test aggregation is only supported for variants with tests, but '$name' does not have any"
                }
            }

    internal val Task.enableCoverageDSLHint
        get() = when {
            (try { this is TestSuiteTestTask } catch (_: NoClassDefFoundError) { false } ) -> "enableAndroidTestCoverage = true"
            this is AbstractTestTask -> "enableUnitTestCoverage = true"
            else -> "enableAndroidTestCoverage = true"
        }

    class ResultsExtension(
        private val project: Project,
        private val report: TestAggregationResultsReport,
        private val devices: SetProperty<String>,
    ) : TestAggregationReportAndroidExtension {

        override fun invoke(androidVariant: AndroidVariant) {
            for (testComponent in androidVariant.testComponents) {
                val testTasks = project.testTasksOf(devices.get(), testComponent) task@{
                    this@task.aggregateTests
                        .convention(testComponent.aggregateTests)
                }

                val variant = report.variants.maybeCreate(testComponent.name)
                variant.dependsOn(testTasks.map { list ->
                    list.mapNotNull {
                        if (it.aggregateTests.get()) it else null
                    }
                })
                variant.aggregate.convention(testComponent.aggregateTests)
                variant.binaryData.from(testTasks.map { list ->
                    list.mapNotNull { task ->
                        when (val task = task.takeIf { it.aggregateTests.get() }) {
                            is AbstractTestTask -> task.binaryResultsDirectory
                            is AndroidTestTask -> task.resultsDir
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
        private val devices: SetProperty<String>,
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
                .toGet(ScopedArtifact.CLASSES, { classesJars }) { classesDirs }

            val variant = report.variants.maybeCreate(androidVariant.kmpAwareName)
            variant.dependsOn(classesTask)
            variant.aggregate.convention(androidVariant.aggregateTests)
            androidVariant.sources.java?.all?.let(variant.sources::from)
            androidVariant.sources.kotlin?.all?.let(variant.sources::from)
            variant.classes.from(classesTask)

            for (testComponent in androidVariant.testComponents) {
                val testAggregate = testComponent.aggregateTests
                val testTasks = project.testTasksOf(devices.get(), testComponent) task@{
                    this@task.aggregateTests
                        .convention(testAggregate)
                }

                variant.dependsOn(testTasks.map { list ->
                    list.mapNotNull {
                        if (it.aggregateTests.get()) it else null
                    }
                })
                variant.coverageData.from(testTasks.map { list ->
                    list.mapNotNull { task ->
                        when (val task = task.takeIf { it.aggregateTests.get() }) {
                            is AndroidUnitTest -> task.coverageData { jacocoCoverageOutputFile.orNull }
                            is DeviceProviderInstrumentTestTask -> task.coverageData { coverageDirectory.orNull }
                            is ManagedDeviceTestTask -> task.coverageData { getCoverageDirectory().orNull }
                            is ManagedDeviceInstrumentationTestTask -> task.coverageData { getCoverageDirectory().orNull }
                            is AbstractTestTask -> task.coverageData { suiteAwareCoverageData }
                            else -> null
                        }
                    }
                })
            }
        }

        private val AndroidVariant.kmpAwareName
            get() = if (project.isKMP && name == "androidMain") "android" else name

        // AGP's built-in test platform runs device tests as a `Test` task, but coverage goes to `coverageDir` (AGP 9+)
        private val AbstractTestTask.suiteAwareCoverageData
            get() = try {
                (this as? TestSuiteTestTask)?.coverageDir?.orNull?.asFileTree?.matching {
                    include("**/*.ec", "**/*.exec")
                }

            } catch (_: NoClassDefFoundError) {
                jacocoDataFile
            }

    }

}
