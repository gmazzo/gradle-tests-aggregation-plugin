package io.github.gmazzo.test.aggregation

import io.github.gmazzo.test.aggregation.TestAggregationReport.BaseVariant
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.api.provider.Property
import org.gradle.api.tasks.TaskProvider

public interface TestAggregationResultsReport :
    TestAggregationReport<TestAggregationResultsReport.Variant, AggregatedTestResultsTask> {

    public override val variants: NamedDomainObjectContainer<Variant>

    public val htmlRequired: Property<Boolean>

    public val htmlOutputLocation: DirectoryProperty

    public val junitXMLRequired: Property<Boolean>

    public val junitXMLOutputLocation: DirectoryProperty

    public override val reportTask: TaskProvider<AggregatedTestResultsTask>

    public fun addTestSuite(testSuite: JvmTestSuite): Variant

    public interface Variant : BaseVariant {

        public val binaryData: ConfigurableFileCollection

        /***
         * Disables this variant, and configures [variant] to collect its data instead.
         */
        public fun supersededBy(variant: Variant) {
            check(variant != this) { "Cannot supersede a variant with itself" }

            aggregate.value(false)

            variant.binaryData.from(binaryData)
            variant.dependsOn(dependsOn)
        }

    }

}
