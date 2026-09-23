package io.github.gmazzo.test.aggregation

import io.github.gmazzo.test.aggregation.TestAggregationReport.BaseVariant
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskProvider

public interface TestAggregationCoverageReport :
    TestAggregationReport<TestAggregationCoverageReport.Variant, AggregatedTestCoverageTask> {

    public override val variants: NamedDomainObjectContainer<Variant>

    @get:Nested
    public val content: Content

    public fun content(configure: Action<Content>) {
        configure.execute(content)
    }

    public val htmlRequired: Property<Boolean>

    public val htmlOutputLocation: DirectoryProperty

    public val xmlRequired: Property<Boolean>

    public val xmlOutputLocation: RegularFileProperty

    public val csvRequired: Property<Boolean>

    public val csvOutputLocation: RegularFileProperty

    public override val reportTask: TaskProvider<AggregatedTestCoverageTask>

    public fun addTestSuite(mainSources: SourceSet, testSuite: JvmTestSuite): Variant

    public interface Variant : BaseVariant {

        public val sources: ConfigurableFileCollection

        public val classes: ConfigurableFileCollection

        public val coverageData: ConfigurableFileCollection

        /***
         * Disables this variant, and configures [variant] to collect its data instead.
         */
        public fun supersededBy(variant: Variant) {
            check(variant != this) { "Cannot supersede a variant with itself" }

            aggregate.value(false)

            variant.sources.from(sources)
            variant.classes.from(classes)
            variant.coverageData.from(coverageData)
            variant.dependsOn(dependsOn)
        }

    }

    public interface Content {

        @get:Input
        public val includes: SetProperty<String>

        public fun include(vararg includes: String): Content = apply {
            this.includes.addAll(*includes)
        }

        @get:Input
        public val excludes: SetProperty<String>

        public fun exclude(vararg excludes: String): Content = apply {
            this.excludes.addAll(*excludes)
        }

    }

}
