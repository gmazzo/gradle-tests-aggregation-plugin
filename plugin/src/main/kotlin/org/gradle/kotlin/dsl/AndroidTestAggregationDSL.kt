package org.gradle.kotlin.dsl

import com.android.build.api.dsl.BuildType
import com.android.build.api.dsl.Device
import com.android.build.api.dsl.ProductFlavor
import com.android.build.api.dsl.TestOptions
import com.android.build.api.variant.Component
import com.android.build.api.variant.TestComponent
import com.android.build.api.variant.Variant
import io.github.gmazzo.test.aggregation.TestAggregationCoverageReport
import io.github.gmazzo.test.aggregation.TestAggregationReportAndroidExtension
import io.github.gmazzo.test.aggregation.TestAggregationResultsReport
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
