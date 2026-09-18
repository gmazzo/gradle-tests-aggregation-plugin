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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.of
import org.junit.jupiter.params.provider.MethodSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AndroidTestAggregationPluginIntegrationTest {

    fun arguments() = listOf(
        of(MIN_GRADLE_VERSION, MIN_AGP_VERSION),
        of(GradleVersion.current().version, ANDROID_GRADLE_PLUGIN_VERSION),
    )

    @ParameterizedTest(name = "gradle={0}, android={1}")
    @MethodSource("arguments")
    fun `should aggregate projects`(gradleVersion: String, agpVersion: String) {
        val projectDir =
            File(System.getenv("TEMP_DIR"), "project/gradle-${gradleVersion}-agp-${agpVersion}")

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

    @Test
    fun `should aggregate managed device coverage from the built-in test platform`() {
        val projectDir = File(System.getenv("TEMP_DIR"), "project-builtin-test-platform")

        projectDir.deleteRecursively()
        File(javaClass.getResource("/project-builtin-test-platform")!!.path).copyRecursively(projectDir)

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath("agp-$ANDROID_GRADLE_PLUGIN_VERSION-metadata.properties")
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
