![GitHub](https://img.shields.io/github/license/gmazzo/gradle-tests-aggregation-plugin)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.gmazzo.test.aggregation/io.github.gmazzo.test.aggregation.gradle.plugin)](https://central.sonatype.com/artifact/io.github.gmazzo.test.aggregation/io.github.gmazzo.test.aggregation.gradle.plugin)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/io.github.gmazzo.test.aggregation)](https://plugins.gradle.org/plugin/io.github.gmazzo.test.aggregation)
[![Build Status](https://github.com/gmazzo/gradle-tests-aggregation-plugin/actions/workflows/ci-cd.yaml/badge.svg)](https://github.com/gmazzo/gradle-tests-aggregation-plugin/actions/workflows/ci-cd.yaml)
[![Coverage](https://codecov.io/gh/gmazzo/gradle-tests-aggregation-plugin/branch/main/graph/badge.svg?token=D5cDiPWvcS)](https://codecov.io/gh/gmazzo/gradle-tests-aggregation-plugin)
[![Users](https://img.shields.io/badge/users_by-Sourcegraph-purple)](https://sourcegraph.com/search?q=content:io.github.gmazzo.test.aggregation+-repo:github.com/gmazzo/gradle-tests-aggregation-plugin)

[![Contributors](https://contrib.rocks/image?repo=gmazzo/gradle-tests-aggregation-plugin)](https://github.com/gmazzo/gradle-tests-aggregation-plugin/graphs/contributors)

# gradle-tests-aggregation-plugin

A Gradle plugin to simplify test aggregations across multiple modules and its variants (e.g. JVM
test suites, Android Variants or Kotlin Multiplatform's Targets) in Android projects.

> [^NOTE]
> *Disclaimer*: since version `3.x`, this plugin no longer relies on
> [JaCoCo Report Aggregation Plugin](https://docs.gradle.org/current/userguide/jacoco_report_aggregation_plugin.html)
> neither
> on [Test Report Aggregation Plugin](https://docs.gradle.org/current/userguide/test_report_aggregation_plugin.html)
> due technical limitations of the Gradle API.
> See [migration guide](MIGRATION-3.x.md) for more details.

# Usage

Apply the plugin on all the projects that needs to be aggregated and/or at the root one:

```kotlin
plugins {
  id("io.github.gmazzo.test.aggregation") version "<latest>"
}
```

Then use the `aggregatedTestsReport` to generate the reports (at its default locations):

- `build/reports/aggregated-test-coverage` for coverage
- `build/reports/aggregated-test-results` for test results

The plugin will automatically detect and aggregate:

- For `java` projects:
  - Any `JvmTestSuite` will be automatically aggregated
  - For coverage:
    - The `jacoco` plugin is required
    - Because API limitations, only the default `test` jvm suite will be automatically computed
    - You can register further jvm suites through the
      `io.github.gmazzo.test.aggregation.TestAggregationCoverageReport.addTestSuite` API
- For `com.android.application`, `com.android.library` and `com.android.library.multiplatform`
  projects:
  - Any `Variant` which its `BuilType` has `enableUnitTestCoverage = true` and/or
    `enableAndroidTestCoverage = true` configured
  - Any `Variant` with either `HostTest` or `DeviceTest` test components. (for `DeviceTest`s, their
    test will only be aggregated if its test `Task` is run)
- For `org.jetbrains.kotlin.multiplatform` projects:
  - Any `KotlinTarget` that with tests. Coverage is only supported for JVM-based ones.

## Aggregating other modules

Besides the variants of a single module, you can also aggregate test results and coverage from other
modules of the build in a single root report.

For this, you can use the `aggregateTestsFrom` configuration to declare a dependency to the modules
to be aggregated:

```kotlin
dependencies {
  aggregateTestsFrom(project(":foo"))
  aggregateTestsFrom(project(":bar"))
}
```

> [^IMPORTANT]
> Keep in mind that every referenced module must also apply the plugin,
> the report will fail otherwise.

## Filtering coverage classes

You can use the DSL to include/exclude `.class` **files** from the aggregated JaCoCo coverage
report:

```kotlin
reporting.reports.withType<TestAggregationCoverageReport>().configureEach {
  content {
    include("com/**/Login*") // will only include classes starting with `com.` containing `Login` on its name
    exclude("**/*ToBeExcluded*") // will exclude classes with its name ending in `ToBeExcluded`
  }
}
```

It's important to realize the filtering is done at `.class` file level (compiled classes). You
should not use classes names here but GLOB patterns.

## Producing an aggregated report for the whole project

This following a is a basic and quick configuration for generating an aggregated report for all
modules of the build, at the root project add:

```kotlin
plugins {
  id("io.github.gmazzo.test.aggregation")
}

dependencies {
  allprojects {
    aggregateTestsFrom(project)
  }
}
```

Then run:

```shell
./gradlew aggregateTestsFrom
```

## Choosing which variants of each module are aggregated

By default, any detected variant (JVM test suites, Android Variant or Kotlin Target) will be
aggregated.

However, you can filter which variants are aggregated by using the `aggregateTests` API:

For Java:

```kotlin
testing.suites.create<JvmTestSuite>("integrationTest") {
  aggregateTests = false // this suite will not be aggregated
}
```

For Android:

```kotlin
androidComponents {
  onVariants { variant ->
    variant.aggregateTests = false
  }
}
```

For Kotlin Multiplatform:

```kotlin
kotlin {
  android {
    aggregateTests = false
  }
  jvm()
  js {
    aggregateTests = false
  }
}
```

### Aggregating Android's device tests

Device tests aggregation can be optionally enabled through the DSL.

To consume the results of connected devices, set:

```kotlin
testOptions {
  aggregateConnectedDevices = true
}
```

> [^CAUTION]
> With this set, the build will fail if no connected devices are available.

Also, [Managed Devices](https://developer.android.com/studio/test/managed-devices) are supported,
on both the Unified Test Platform and AGP's built-in test platform
(`android.experimental.androidTest.builtin_test_platform=true`).

You can aggregate them with `aggregateTests` DSL:
```kotlin
android {
  testOptions {
    managedDevices.localDevices.all {
      aggregateTests = true
    }
  }
}
```
