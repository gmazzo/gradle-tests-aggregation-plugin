package io.github.gmazzo.test.aggregation

import io.github.gmazzo.test.aggregation.TestAggregationCoverageReport.Variant
import javax.inject.Inject
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskProvider

internal abstract class DefaultTestAggregationCoverageReport @Inject constructor(
    project: Project,
) : AbstractTestAggregationReport<Variant, AggregatedTestCoverageTask>(),
    TestAggregationCoverageReport {

    override lateinit var reportTask: TaskProvider<AggregatedTestCoverageTask>

    override fun addTestSuite(mainSources: SourceSet, testSuite: JvmTestSuite): Variant {
        val mainAggregate = (mainSources as ExtensionAware).aggregateTests

        val variant = variants.maybeCreate(mainSources.name)
        variant.dependsOn(mainSources.classesTaskName)
        variant.aggregate.convention(mainAggregate)
        variant.sources.from(mainSources.allSource.srcDirs)
        variant.classes.from(mainSources.output.classesDirs)

        testSuite.targets.all target@{
            val suiteAggregate = (testSuite as ExtensionAware).aggregateTests
                .convention(mainAggregate)

            variant.dependsOn(testTask.map {
                if (it.aggregateTests.get()) it else emptyArray<Any>()
            })
            variant.coverageData.from(testTask.map {
                (if (it.aggregateTests.get()) it.coverageData() else null) ?: emptyArray<Any>()
            })

            testTask.configure task@{
                this@task.aggregateTests
                    .convention(suiteAggregate)
            }
        }
        return variant
    }

}
