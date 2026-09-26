package org.gradle.kotlin.dsl

import io.github.gmazzo.test.aggregation.aggregateTestCoverage
import io.github.gmazzo.test.aggregation.aggregateTestResults
import io.github.gmazzo.test.aggregation.aggregateTests
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.api.provider.Property
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.testing.AbstractTestTask

public val SourceSet.aggregateTests: Property<Boolean>
    get() = (this as ExtensionAware).aggregateTests

public val JvmTestSuite.aggregateTests: Property<Boolean>
    get() = (this as ExtensionAware).aggregateTests

public val AbstractTestTask.aggregateTestResults: Property<Boolean>
    get() = (this as ExtensionAware).aggregateTestResults

public val AbstractTestTask.aggregateTestCoverage: Property<Boolean>
    get() = (this as ExtensionAware).aggregateTestCoverage
