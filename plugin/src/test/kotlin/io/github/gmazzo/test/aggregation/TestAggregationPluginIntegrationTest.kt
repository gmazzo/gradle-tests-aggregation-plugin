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
sealed class TestAggregationPluginIntegrationTest(private val platform: String) {

    class Java : TestAggregationPluginIntegrationTest("java") {

        fun arguments() = listOf(
            of(MIN_GRADLE_VERSION),
            of(GradleVersion.current().version),
        )

        @ParameterizedTest(name = "gradle={0}")
        @MethodSource("arguments")
        fun `should aggregate projects`(gradleVersion: String) = testPlugin(gradleVersion)

    }

    class Android : TestAggregationPluginIntegrationTest("android") {

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
        pluginClasspath: String? = null,
        prepareBuild: (File) -> Unit = {},
    ) {
        val baseProject = "project-$platform"
        val projectDir = tempDir.resolve("$baseProject/gradle-${gradleVersion}${pathSuffix}")

        projectDir.deleteRecursively()
        File(javaClass.getResource("/$baseProject")!!.path).copyRecursively(projectDir)

        prepareBuild(projectDir)

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withGradleVersion(gradleVersion)
            .withPluginClasspath(pluginClasspath)
            .withArguments("aggregatedTestsReport", "-s")
            .forwardOutput()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":aggregatedTestsReport")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":aggregatedTestResultsReport")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":aggregatedTestCoverageReport")?.outcome)

        assertEquals(TaskOutcome.SUCCESS, result.task(":app:aggregatedTestsReport")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":app:aggregatedTestResultsReport")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":app:aggregatedTestCoverageReport")?.outcome)

        assertEquals(TaskOutcome.SUCCESS, result.task(":lib:aggregatedTestsReport")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":lib:aggregatedTestResultsReport")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":lib:aggregatedTestCoverageReport")?.outcome)

        assertEquals(TaskOutcome.SUCCESS, result.task(":utils:aggregatedTestsReport")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":utils:aggregatedTestResultsReport")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":utils:aggregatedTestCoverageReport")?.outcome)

        assertCoverage("coverage.csv", projectDir)
        assertCoverage("app-coverage.csv", projectDir.resolve("app"))
        assertCoverage("lib-coverage.csv", projectDir.resolve("lib"))
        assertCoverage("utils-coverage.csv", projectDir.resolve("utils"))
    }

    private fun assertCoverage(expectedCoverageFile: String, projectDir: File) {
        val expectedCoverage = checkNotNull(javaClass.getResource("/expects/coverage-$platform/$expectedCoverageFile")) {
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
