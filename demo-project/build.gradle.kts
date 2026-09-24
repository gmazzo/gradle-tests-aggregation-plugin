import com.android.build.api.dsl.CommonExtension
import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils
import groovy.xml.MarkupBuilder
import io.github.gmazzo.test.aggregation.TestAggregationCoverageReport
import kotlin.math.max

buildscript {
    dependencies {
        classpath(libs.diffUtils)
    }
}

plugins {
    base
    jacoco
    id("io.github.gmazzo.test.aggregation")
}

reporting.reports.withType<TestAggregationCoverageReport>().configureEach {
    content {
        exclude("**/*ToBeExcluded*")
    }
}

dependencies {
    aggregateTestsFrom(projects.demoProject.app)
    aggregateTestsFrom(projects.demoProject.domain)
    aggregateTestsFrom(projects.demoProject.kmp)
    aggregateTestsFrom(projects.demoProject.login)
    aggregateTestsFrom(projects.demoProject.uiTests)
}

subprojects {
    plugins.withId("com.android.base") {
        the<CommonExtension>().testOptions.managedDevices.localDevices {
            configureEach {
                device = "Pixel 6"
                apiLevel = 33
                systemImageSource = "aosp_atd"
                aggregateTests = true
            }
            register("emulator")
            register("emulator2") {
                device = "Pixel 6a"
                aggregateTests = false
            }
        }
    }
}

val aggregatedReportsSpecs = layout.projectDirectory.dir("specs/aggregated-reports")

fun Sync.reportsSpec(): CopySpec {
    val rootDir = rootDir.absolutePath
    val dataSortRegEx = "\\bdata-sort-value=\"\\d+\"".toRegex()
    val tookRegEx = "\\b\\d+(?:\\.\\d+)?s\\b|(?<=\\btime=\")\\d+(?:\\.\\d+)?(?=\")".toRegex()
    val attrsRegEx = "\\b(timestamp|hostname)=\"[^\"]+\"\\s+".toRegex()
    val spansTimeRegEx =
        "\\d{4}-\\d?\\d-\\d?\\d \\d?\\d:\\d?\\d:\\d?\\d(?:\\.\\d+ \\w+)?".toRegex()
    val emulatorName = "emulator-\\d+(\\s*-?\\s*\\d*)?".toRegex()
    val androidHome = providers.environmentVariable("ANDROID_HOME").get()
    val coverageTask = tasks.aggregatedTestCoverageReport
    val resultsTypes = tasks.aggregatedTestResultsReport

    return project.copySpec {
        into("coverage") {
            from(coverageTask) { include("**/*.csv") }
        }
        into("tests") {
            from(resultsTypes)
        }
        filter {
            when {
                it.startsWith("<a href=\"https://www.gradle.org\">") -> ""
                else -> it
                    .replace(attrsRegEx, "")
                    .replace(dataSortRegEx, "data-sort-value=\"100\"")
                    .replace(tookRegEx, "0.100s")
                    .replace(spansTimeRegEx, "2016-01-01 00:00")
                    .replace(emulatorName, "emulator-XXXX")
                    .replace(rootDir, "")
                    .replace(androidHome, "~/.android/sdk")
            }
        }
        eachFile {
            path = path.replace(emulatorName, "emulator-XXXX")
        }
        includeEmptyDirs = false
        doLast {
            val cdataRegex = "<!\\[CDATA\\[.*?\\]\\]>".toRegex(RegexOption.DOT_MATCHES_ALL)
            val preRegex = "<pre id=\".*\">.*?</pre>".toRegex(RegexOption.DOT_MATCHES_ALL)

            for (file in outputs.files.asFileTree) {
                when (file.extension) {
                    // makes sure CSV file is sorted alphabetically
                    "csv" -> file.writeText(
                        file
                            .readLines()
                            .let { it.take(1) + it.drop(1).sorted() }
                            .joinToString("\n")
                    )

                    // removes multiple CDATA
                    "xml" -> file.writeText(
                        file
                            .readText()
                            .replace(cdataRegex, "<![CDATA[]]>")
                    )

                    // removes pre tags content
                    "html" -> file.writeText(
                        file
                            .readText()
                            .replace(preRegex, "<pre id=\"...\">...</pre>")
                    )
                }
            }
        }
    }
}

tasks.register<Sync>("updateSpecs") {
    outputs.upToDateWhen { false }
    dependsOn(gradle.includedBuild("plugin").task(":updateSpecs"))
    with(reportsSpec())
    into(aggregatedReportsSpecs)
}

val checkReportsTask = tasks.register<Sync>("checkAggregatedReportsContent") {
    val reportFile = layout.buildDirectory.file("reports/$name/report.xml")

    outputs.file(reportFile).optional()
    outputs.upToDateWhen { false }
    into("expects") {
        from(aggregatedReportsSpecs)
    }
    into("actual") {
        with(reportsSpec())
    }
    into(temporaryDir)
    doLast {
        fun File.collect() = walkTopDown()
            .filter(File::isFile)
            .associateBy { it.toRelativeString(this) }

        val expected = File(temporaryDir, "expects").collect()
        val actual = File(temporaryDir, "actual").collect()
        val diffs = (expected.keys + actual.keys).associateWith {
            val expectedLines = expected[it]?.readLines().orEmpty()
            val actualLines = actual[it]?.readLines().orEmpty()

            when (actualLines) {
                expectedLines -> null
                else -> UnifiedDiffUtils.generateUnifiedDiff(
                    "expected:${it}", "actual:${it}",
                    expectedLines,
                    DiffUtils.diff(expectedLines, actualLines),
                    3
                ).joinToString("\n")
            }
        }
        val failures = diffs.values.filterNotNull()

        reportFile.get().asFile.apply { parentFile.mkdirs() }.writer().use { out ->
            val xml = MarkupBuilder(out)
            xml.withGroovyBuilder {
                "testsuite"(
                    mapOf(
                        "name" to this@register.name,
                        "tests" to max(expected.keys.size, actual.keys.size),
                        "failures" to failures.size
                    )
                ) {
                    for ((file, diff) in diffs) {
                        "testcase"(mapOf("name" to file)) {
                            if (diff != null) {
                                "failure"(mapOf("message" to "File '$file' mismatch", "type" to "AssertionError")) {
                                    xml.mkp.yield(diff)
                                }
                            }
                        }
                    }
                }
            }
        }

        check(failures.isEmpty()) {
            failures.joinToString(
                prefix = "The generated reports are different than the expected ones:\n",
                separator = "\n\n\n"
            )
        }
    }
}

tasks.check {
    dependsOn(checkReportsTask)
}
