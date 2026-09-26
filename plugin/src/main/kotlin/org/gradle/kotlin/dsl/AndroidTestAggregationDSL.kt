package org.gradle.kotlin.dsl

import com.android.build.api.dsl.Device
import com.android.build.api.dsl.TestOptions
import com.android.build.api.variant.Component
import com.android.build.api.variant.TestComponent
import com.android.build.api.variant.Variant
import com.android.build.gradle.internal.tasks.ManagedDeviceTestTask
import com.android.build.gradle.tasks.factory.AndroidUnitTest
import io.github.gmazzo.test.aggregation.TestAggregationCoverageReport
import io.github.gmazzo.test.aggregation.TestAggregationReportAndroidExtension
import io.github.gmazzo.test.aggregation.TestAggregationResultsReport
import io.github.gmazzo.test.aggregation.aggregateTestCoverage
import io.github.gmazzo.test.aggregation.aggregateTestResults
import io.github.gmazzo.test.aggregation.aggregateTests
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.Property

public fun TestAggregationResultsReport.addAndroidVariant(androidVariant: Variant) {
    (this as ExtensionAware).the<TestAggregationReportAndroidExtension>()(androidVariant)
}

public fun TestAggregationCoverageReport.addAndroidVariant(androidVariant: Variant) {
    (this as ExtensionAware).the<TestAggregationReportAndroidExtension>()(androidVariant)
}

// returns an inner field that can extensions and its shared across all DSL callbacks
private val Component.gradleExtensions: ExtensionAware
    get() = compileConfiguration as ExtensionAware

public val Variant.aggregateTests: Property<Boolean>
    get() = gradleExtensions.aggregateTests

public val TestComponent.aggregateTests: Property<Boolean>
    get() = gradleExtensions.aggregateTests

public val Device.aggregateTests: Property<Boolean>
    get() = (this as ExtensionAware).aggregateTests

@Suppress("UNCHECKED_CAST")
public val TestOptions.aggregateConnectedDevices: Property<Boolean>
    get() = (this as ExtensionAware).extensions.getByName(::aggregateConnectedDevices.name) as Property<Boolean>

public val AndroidUnitTest.aggregateTestResults: Property<Boolean>
    get() = (this as ExtensionAware).aggregateTestResults

public val AndroidUnitTest.aggregateTestCoverage: Property<Boolean>
    get() = (this as ExtensionAware).aggregateTestCoverage

public val ManagedDeviceTestTask.aggregateTestResults: Property<Boolean>
    get() = (this as ExtensionAware).aggregateTestResults

public val ManagedDeviceTestTask.aggregateTestCoverage: Property<Boolean>
    get() = (this as ExtensionAware).aggregateTestCoverage
