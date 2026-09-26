package io.github.gmazzo.test.aggregation

import io.github.gmazzo.test.aggregation.TestAggregationResultsReport.Variant
import javax.inject.Inject
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.aggregateTests

internal abstract class DefaultTestAggregationResultsReport @Inject constructor() :
    AbstractTestAggregationReport<Variant, AggregatedTestResultsTask>(),
    TestAggregationResultsReport {

    override lateinit var reportTask: TaskProvider<AggregatedTestResultsTask>

    override fun addTestSuite(testSuite: JvmTestSuite): Variant {
        val suiteAggregate = testSuite.aggregateTests

        val variant = variants.maybeCreate(testSuite.name)
        variant.aggregate.convention(suiteAggregate)

        testSuite.targets.all target@{
            variant.dependsOn(testTask.map {
                if (it.aggregateTestResults.get()) it else emptyArray<Any>()
            })
            variant.binaryData.from(testTask.map {
                if (it.aggregateTestResults.get()) it.binaryResultsDirectory else emptyArray<Any>()
            })

            testTask.configure task@{
                this@task.aggregateTestResults
                    .convention(suiteAggregate)
            }
        }
        return variant
    }

}
