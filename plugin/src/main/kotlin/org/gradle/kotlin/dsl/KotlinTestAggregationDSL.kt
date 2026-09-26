package org.gradle.kotlin.dsl

import io.github.gmazzo.test.aggregation.TestAggregationCoverageReport
import io.github.gmazzo.test.aggregation.TestAggregationReportKotlinExtension
import io.github.gmazzo.test.aggregation.TestAggregationResultsReport
import io.github.gmazzo.test.aggregation.aggregateTestCoverage
import io.github.gmazzo.test.aggregation.aggregateTestResults
import io.github.gmazzo.test.aggregation.aggregateTests
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.Property
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.testing.internal.KotlinTestReport

public fun TestAggregationResultsReport.addKotlinTarget(target: KotlinTarget) {
    (this as ExtensionAware).the<TestAggregationReportKotlinExtension>()(target)
}

public fun TestAggregationCoverageReport.addKotlinTarget(target: KotlinTarget) {
    (this as ExtensionAware).the<TestAggregationReportKotlinExtension>()(target)
}

public val KotlinTarget.aggregateTests: Property<Boolean>
    get() = (this as ExtensionAware).aggregateTests

public val KotlinTestReport.aggregateTestResults: Property<Boolean>
    get() = (this as ExtensionAware).aggregateTestResults

public val KotlinTestReport.aggregateTestCoverage: Property<Boolean>
    get() = (this as ExtensionAware).aggregateTestCoverage
