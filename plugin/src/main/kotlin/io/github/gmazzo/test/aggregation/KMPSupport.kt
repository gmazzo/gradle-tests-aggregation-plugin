package io.github.gmazzo.test.aggregation

import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.testing.AbstractTestTask
import org.gradle.kotlin.dsl.addKotlinTarget
import org.gradle.kotlin.dsl.aggregateTests
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.typeOf
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.ExecutionTaskHolder
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation.Companion.MAIN_COMPILATION_NAME
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinTargetWithTests
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinTargetWithBinaries
import org.jetbrains.kotlin.gradle.testing.internal.KotlinTestReport

internal object KMPSupport {

    fun Project.installBase() {
        configure<ReportingExtension> {
            reports.withType<TestAggregationResultsReport> report@{
                (this@report as ExtensionAware).extensions
                    .add(
                        typeOf<TestAggregationReportKotlinExtension>(),
                        "addKotlinTarget",
                        ResultsExtension(this@report)
                    )
            }
            reports.withType<TestAggregationCoverageReport> report@{
                (this@report as ExtensionAware).extensions
                    .add(
                        typeOf<TestAggregationReportKotlinExtension>(),
                        "addKotlinTarget",
                        CoverageExtension(this@report)
                    )
            }
        }
    }

    fun Project.install(
        testResults: TestAggregationResultsReport,
        testCoverage: TestAggregationCoverageReport,
    ) {
        the<KotlinMultiplatformExtension>().targets.all target@{
            if (platformType == KotlinPlatformType.common) return@target // it would never have tests
            if (platformType == KotlinPlatformType.androidJvm) return@target // will be handled by AndroidSupport

            testResults.addKotlinTarget(this@target)
            if (this@target.supportsJaCoCo) {
                testCoverage.addKotlinTarget(this@target)
            }
        }
    }

    private val KotlinTarget.supportsJaCoCo
        get() = when (platformType) {
            KotlinPlatformType.jvm, KotlinPlatformType.androidJvm -> true
            else -> false
        }

    class ResultsExtension(
        private val report: TestAggregationResultsReport,
    ) : TestAggregationReportKotlinExtension {

        override fun invoke(target: KotlinTarget) {
            val variant = report.variants.maybeCreate(target.name)
            variant.aggregate.convention(target.aggregateTests)

            when (target) {
                is KotlinTargetWithTests<*, *> -> {
                    target.testRuns.all run@{
                        if (this@run !is ExecutionTaskHolder<*>) return@run

                        executionTask.configure task@{
                            this@task.aggregateTestResults
                                .convention(variant.aggregate)
                        }
                        variant.dependsOn(executionTask.map {
                            if (it.aggregateTestResults.get()) it else emptyArray<Any>()
                        })
                        variant.binaryData.from(executionTask.map { task ->
                            when (val task = task.takeIf { it.aggregateTestResults.get() }) {
                                is AbstractTestTask -> task.binaryResultsDirectory
                                is KotlinTestReport -> task.testResults
                                else -> emptyArray<Any>()
                            }
                        })
                    }
                }

                is KotlinTargetWithBinaries<*, *> -> {
                    val testTasks = target.project
                        .tasksMatching(name = "${target.disambiguationClassifier}Test") task@{
                        this@task.aggregateTestResults
                            .convention(variant.aggregate)
                    }

                    variant.dependsOn(testTasks.map { list ->
                        list.filter { it.aggregateTestResults.get() }
                    })
                    variant.binaryData.from(testTasks.map { list ->
                        list.mapNotNull {
                            if (it.aggregateTestResults.get()) (it as AbstractTestTask).binaryResultsDirectory else null
                        }
                    })
                }

                else -> error("Test aggregation is only supported for targets with tests, but ${target.name} does not have any test runs")
            }
        }
    }

    class CoverageExtension(
        private val report: TestAggregationCoverageReport,
    ) : TestAggregationReportKotlinExtension {

        override fun invoke(target: KotlinTarget) {
            check(target.supportsJaCoCo) {
                "Target '${target.name}' does not supports JaCoCo coverage. Only JVM-based are supported"
            }

            val targetAggregate = target.aggregateTests
            val main = target.compilations.getByName(MAIN_COMPILATION_NAME)

            val variant = report.variants.maybeCreate(target.name)
            variant.dependsOn(main.compileTaskProvider)
            variant.aggregate.convention(targetAggregate)
            variant.sources.from(main.allKotlinSourceSets.asSequence().map {
                listOf(
                    it.kotlin.srcDirs,
                    it.generatedKotlin.srcDirs,
                )
            }.asIterable())
            variant.classes.from(main.output.classesDirs)

            (target as? KotlinTargetWithTests<*, *>)?.testRuns?.all run@{
                if (this@run !is ExecutionTaskHolder<*>) return@run

                executionTask.configure task@{
                    this@task.aggregateTestCoverage
                        .convention(targetAggregate)
                }
                variant.dependsOn(executionTask.map {
                    if (it.aggregateTestCoverage.get()) it else emptyArray<Any>()
                })
                variant.coverageData.from(executionTask.map { task ->
                    when (val task = task.takeIf { it.aggregateTestCoverage.get() }) {
                        is AbstractTestTask -> task.coverageData()
                        is KotlinTestReport -> task.testTasks.map { it.coverageData() }
                        else -> null
                    } ?: emptyArray<Any>()
                })
            }
        }

    }

}
