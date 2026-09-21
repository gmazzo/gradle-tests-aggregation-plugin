@file:Suppress("DEPRECATION")

package io.github.gmazzo.android.test.aggregation

import com.android.builder.model.Version.ANDROID_GRADLE_PLUGIN_VERSION
import io.github.gmazzo.test.aggregation.BuildConfig.MIN_AGP_VERSION
import io.github.gmazzo.test.aggregation.BuildConfig.MIN_GRADLE_VERSION
import java.io.File
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.internal.PluginUnderTestMetadataReading.readImplementationClasspath
import org.gradle.util.GradleVersion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.of
import org.junit.jupiter.params.provider.MethodSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestAggregationPluginIntegrationTest {

    private val tempDir = File(System.getenv("TEMP_DIR"))

    fun arguments() = listOf(
        of(MIN_GRADLE_VERSION, MIN_AGP_VERSION),
        of(GradleVersion.current().version, ANDROID_GRADLE_PLUGIN_VERSION),
    )

    @ParameterizedTest(name = "gradle={0}, android={1}")
    @MethodSource("arguments")
    fun `should aggregate projects`(gradleVersion: String, agpVersion: String) {
        val projectDir = tempDir.resolve("project/gradle-${gradleVersion}-agp-${agpVersion}")

        projectDir.deleteRecursively()
        File(javaClass.getResource("/project")!!.path).copyRecursively(projectDir)

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withGradleVersion(gradleVersion)
            .withPluginClasspath("agp-$agpVersion-metadata.properties")
            .withArguments("aggregatedTestsReport", "-s")
            .forwardOutput()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":aggregatedTestsReport")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":aggregatedTestResultsReport")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":aggregatedTestCoverageReport")?.outcome)

        val expectedCoverageXML = sequenceOf(
            "/expected-$gradleVersion-coverage.xml",
            "/expected-coverage.xml",
        ).firstNotNullOf(javaClass::getResource)

        assertEquals(
            expectedCoverageXML.readText().withoutSessionInfo,
            projectDir.resolve("build/reports/aggregated-test-coverage/coverage.xml")
                .readText().withoutSessionInfo,
        )
    }

    @ParameterizedTest(name = "gradle={0}, android={1}")
    @MethodSource("arguments")
    fun `should aggregate managed device coverage from the built-in test platform`(
        gradleVersion: String,
        agpVersion: String
    ) {
        assumeTrue(GradleVersion.version(agpVersion.replace("-.*$".toRegex(), "")) >= GradleVersion.version("9.5.0"))

        val projectDir = tempDir
            .resolve("project-builtin-test-platform/gradle-${gradleVersion}-agp-${agpVersion}")

        projectDir.deleteRecursively()
        File(javaClass.getResource("/project-builtin-test-platform")!!.path)
            .copyRecursively(projectDir)

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withGradleVersion(gradleVersion)
            .withPluginClasspath("agp-$agpVersion-metadata.properties")
            .withArguments(":lib:printDeviceTestCoverageData", "-s")
            .forwardOutput()
            .build()

        val deviceCoverageDir = result.output.lineValue("deviceCoverageDir")
        val coverageData = result.output.lineValue("coverageData[debug]")

        assertTrue(
            deviceCoverageDir in coverageData,
            "The device test's coverage directory should be aggregated: $coverageData",
        )
        assertFalse(
            "emulatorDebugAndroidTest.exec" in coverageData,
            "The host JVM's JaCoCo agent output should not be aggregated: $coverageData",
        )
    }

    @ParameterizedTest(name = "gradle={0}, android={1}")
    @MethodSource("arguments")
    fun `should ignore files that are not execution data in coverage directories`(
        gradleVersion: String,
        agpVersion: String
    ) {
        val projectDir = tempDir
            .resolve("project-coverage-data-dir/gradle-${gradleVersion}-agp-${agpVersion}")

        projectDir.deleteRecursively()
        File(javaClass.getResource("/project-coverage-data-dir")!!.path)
            .copyRecursively(projectDir)

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withGradleVersion(gradleVersion)
            .withPluginClasspath("agp-$agpVersion-metadata.properties")
            .withArguments("aggregatedTestCoverageReport", "-s")
            .forwardOutput()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":aggregatedTestCoverageReport")?.outcome)

        val coverageXML = projectDir.resolve("build/reports/aggregated-test-coverage/coverage.xml").readText()
        assertTrue(
            "<counter type=\"INSTRUCTION\" missed=\"\\d+\" covered=\"[1-9]\\d*\"/>".toRegex() in coverageXML,
            "The unit tests' execution data should still be reported: $coverageXML",
        )
    }

    private fun String.lineValue(key: String) = lineSequence()
        .single { it.startsWith("$key=") }
        .substringAfter('=')

    private val String.withoutSessionInfo
        get() = replace("<sessioninfo[^>]+/>".toRegex(), "")

    private fun GradleRunner.withPluginClasspath(vararg andOthers: String) = withPluginClasspath(
        readImplementationClasspath() +
            andOthers.flatMap {
                readImplementationClasspath(
                    Thread.currentThread().getContextClassLoader().getResource(it)
                )
            }
    )

}
