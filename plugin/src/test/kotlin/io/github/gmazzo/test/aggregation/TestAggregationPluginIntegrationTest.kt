package io.github.gmazzo.test.aggregation

import com.android.builder.model.Version.ANDROID_GRADLE_PLUGIN_VERSION
import io.github.gmazzo.test.aggregation.BuildConfig.MIN_AGP_VERSION
import io.github.gmazzo.test.aggregation.BuildConfig.MIN_GRADLE_VERSION
import java.io.File
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.internal.PluginUnderTestMetadataReading.readImplementationClasspath
import org.gradle.util.GradleVersion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.of
import org.junit.jupiter.params.provider.MethodSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
sealed class TestAggregationPluginIntegrationTest(
    private val baseProject: String,
) {

    class Java : TestAggregationPluginIntegrationTest("project-java") {

        fun arguments() = listOf(
            of(MIN_GRADLE_VERSION, true),
            of(GradleVersion.current().version, true),
            of(GradleVersion.current().version, false),
        )

        @ParameterizedTest(name = "gradle={0}, groupByVariant={1}")
        @MethodSource("arguments")
        fun `should aggregate projects`(
            gradleVersion: String,
            groupByVariant: Boolean,
        ) = testPlugin(
            gradleVersion,
            pathSuffix = if (groupByVariant) "" else "-flat",
            expectedCoverageFile = if (groupByVariant) "java-coverage.csv" else "java-flat-coverage.csv",
        ) { projectDir ->
            if (!groupByVariant) {
                projectDir.resolve("build.gradle").appendText(
                    """

                reporting.reports.withType(io.github.gmazzo.test.aggregation.TestAggregationCoverageReport).configureEach {
                    groupByVariant = false
                }
                """.trimIndent()
                )
            }
        }

    }

    class Android : TestAggregationPluginIntegrationTest("project-android") {

        fun arguments() = listOf(
            of(MIN_GRADLE_VERSION, MIN_AGP_VERSION, false),
            of(GradleVersion.current().version, ANDROID_GRADLE_PLUGIN_VERSION, false),
            of(GradleVersion.current().version, ANDROID_GRADLE_PLUGIN_VERSION, true),
        )

        @ParameterizedTest(name = "gradle={0}, android={1}, agpTestSuites={2}")
        @MethodSource("arguments")
        fun `should aggregate projects`(
            gradleVersion: String,
            agpVersion: String,
            agpTestSuites: Boolean,
        ) = testPlugin(
            gradleVersion,
            pathSuffix = "-agp-${agpVersion}${if (agpTestSuites) "-test-suites" else ""}",
            pluginClasspath = "agp-$agpVersion-metadata.properties",
            expectedCoverageFile = "android-coverage.csv",
        ) { projectDir ->
            if (agpTestSuites) {
                projectDir.resolve("gradle.properties").appendText(
                    """
                android.experimental.androidTest.builtin_test_platform=true
                """.trimIndent()
                )
            }
        }

    }

    private val tempDir = File(System.getenv("TEMP_DIR"))

    protected fun testPlugin(
        gradleVersion: String,
        pathSuffix: String = "",
        expectedCoverageFile: String,
        pluginClasspath: String? = null,
        prepareBuild: (File) -> Unit = {},
    ) {
        val projectDir = tempDir.resolve("$baseProject/gradle-${gradleVersion}${pathSuffix}")

        projectDir.deleteRecursively()
        File(javaClass.getResource("/$baseProject")!!.path).copyRecursively(projectDir)

        prepareBuild(projectDir)

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withGradleVersion(gradleVersion)
            .withPluginClasspath(pluginClasspath)
            .withArguments("aggregatedTestsReport", ":utils:aggregatedTestCoverageReport", "-s")
            .forwardOutput()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":aggregatedTestsReport")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":aggregatedTestResultsReport")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":aggregatedTestCoverageReport")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":utils:aggregatedTestCoverageReport")?.outcome)

        assertCoverage(expectedCoverageFile, projectDir)
        assertCoverage("utils-coverage.csv", projectDir.resolve("utils"))
    }

    private fun assertCoverage(expectedCoverageFile: String, projectDir: File) {
        val expectedCoverage = checkNotNull(javaClass.getResource("/coverage-expects/$expectedCoverageFile")) {
            "Expected coverage file not found: $expectedCoverageFile"
        }.readText()

        assertEquals(
            expectedCoverage.withoutSessionInfo,
            projectDir.resolve("build/reports/aggregated-test-coverage/coverage.csv")
                .readText().withoutSessionInfo,
        )
    }

    private val String.withoutSessionInfo
        get() = replace("<sessioninfo[^>]+/>".toRegex(), "")

    private fun GradleRunner.withPluginClasspath(vararg andOthers: String?) = withPluginClasspath(
        readImplementationClasspath() +
            andOthers.filterNotNull().flatMap {
                readImplementationClasspath(
                    Thread.currentThread().getContextClassLoader().getResource(it)
                )
            }
    )

}
