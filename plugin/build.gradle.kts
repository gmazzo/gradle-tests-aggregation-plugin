@file:OptIn(ExperimentalAbiValidation::class)

import org.gradle.api.internal.catalog.ExternalModuleDependencyFactory.PluginNotationSupplier
import org.gradle.plugin.devel.tasks.PluginUnderTestMetadata.METADATA_FILE_NAME
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.android) apply false
    alias(libs.plugins.buildconfig)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.samReceiver)
    alias(libs.plugins.dokka)
    alias(libs.plugins.mavenPublish)
    alias(libs.plugins.gitVersion)
    alias(libs.plugins.gradle.pluginPublish)
    alias(libs.plugins.gradle.testkit.jacoco)
    alias(libs.plugins.publicationsReport)
    `jacoco-report-aggregation`
    `java-test-fixtures`
}

group = "io.github.gmazzo.test.aggregation"
description = "Test Aggregation Plugin for Android"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get().toInt()))

    registerFeature("antGrouping") {
        usingSourceSet(sourceSets.maybeCreate("antGrouping"))
        withSourcesJar()
        withJavadocJar()
    }
}

samWithReceiver.annotation(HasImplicitReceiver::class.qualifiedName!!)

kotlin {
    abiValidation()
    explicitApi()
}

val kotlinTest = testing.suites.create<JvmTestSuite>("kotlinTest")

val minGradleVersion = "9.3.0"
val minAGPVersion = "8.5.0"

buildConfig {
    packageName = "io.github.gmazzo.test.aggregation"
    buildConfigField("MIN_GRADLE_VERSION", minGradleVersion)
    buildConfigField("MIN_AGP_VERSION", minAGPVersion)
    buildConfigField("JACOCO_ANT_DEPENDENCY", libs.ant.jacoco.map { "${it.group}:${it.name}" to "${it.version}" })
    buildConfigField("JACOCO_ANT_GROUPING_DEPENDENCY", provider { "${project.group}:${project.name}:${project.version}" })
    buildConfigField("JACOCO_ANT_GROUPING_CAPABILITY", provider { "${project.group}:${project.name}-ant-grouping" })
}

val originUrl = providers
    .exec { commandLine("git", "remote", "get-url", "origin") }
    .standardOutput.asText.map { it.trim() }

gradlePlugin {
    vcsUrl = originUrl
    website = originUrl

    testSourceSets += kotlinTest.sources

    plugins.create("test-aggregation") {
        id = "io.github.gmazzo.test.aggregation"
        displayName = name
        implementationClass = "io.github.gmazzo.test.aggregation.TestAggregationPlugin"
        description = "Simple test aggregation support for Android/JVM modules"
        tags.addAll(
            "android",
            "agp",
            "coverage",
            "jacoco",
            "test",
            "aggregation",
            "test-report-aggregation",
            "jacoco-report-aggregation"
        )
    }

    plugins.create("test-coverage-aggregation") {
        id = "io.github.gmazzo.test.aggregation.coverage"
        displayName = name
        implementationClass =
            "io.github.gmazzo.android.test.aggregation.TestCoverageAggregationPlugin"
        description = "Jacoco coverage aggregation support for Android/JVM modules"
        tags.addAll(
            "android",
            "agp",
            "coverage",
            "jacoco",
            "test",
            "aggregation",
            "jacoco-report-aggregation"
        )
    }

    plugins.create("test-results-aggregation") {
        id = "io.github.gmazzo.test.aggregation.results"
        displayName = name
        implementationClass =
            "io.github.gmazzo.android.test.aggregation.TestResultsAggregationPlugin"
        description = "Test results aggregation support for Android/JVM modules"
        tags.addAll("android", "agp", "test", "aggregation", "test-report-aggregation")
    }
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)

    pom {
        name = "${rootProject.name}-${project.name}"
        description = provider { project.description }
        url = originUrl

        licenses {
            license {
                name = "MIT License"
                url = "https://opensource.org/license/mit/"
            }
        }

        developers {
            developer {
                id = "gmazzo"
                name = id
                email = "gmazzo65@gmail.com"
            }
        }

        scm {
            connection = originUrl
            developerConnection = originUrl
            url = originUrl
        }
    }
}

fun DependencyHandler.plugin(dependency: Provider<PluginDependency>) =
    dependency.get().run { create("$pluginId:$pluginId.gradle.plugin:$version") }

fun DependencyHandler.plugin(dependency: PluginNotationSupplier) =
    plugin(dependency.asProvider())

fun dependencyMetadata(prefix: String, dependency: Any) = dependencies.create(dependency).let { dependency ->
    val baseName = "$prefix-${dependency.version}"

    tasks.register<PluginUnderTestMetadata>("pluginUnderTestMetadata-${baseName}") {
        pluginClasspath.from(configurations.detachedConfiguration(dependency))
        outputDirectory.convention(layout.buildDirectory.dir(this@register.name))
        doLast {
            with(outputDirectory.asFile.get()) {
                resolve(METADATA_FILE_NAME).renameTo(resolve("$baseName-metadata.properties"))
            }
        }
    }
}

val kotlinBOM = with(dependencies) { create(platform(libs.kotlin.bom).get()) }

dependencies {
    compileOnly(gradleKotlinDsl())
    compileOnly(plugin(libs.plugins.android))
    compileOnly(plugin(libs.plugins.kotlin.multiplatform))

    implementation(kotlinBOM)

    testFixturesApi(gradleKotlinDsl())
    testFixturesApi(platform(libs.junit.bom))
    testFixturesApi(libs.junit.params)

    testFixturesCompileOnly(plugin(libs.plugins.android))

    testImplementation(plugin(libs.plugins.android))
    testImplementation(libs.mockk)

    "kotlinTestImplementation"(testFixtures(project))
    "kotlinTestImplementation"(plugin(libs.plugins.android))
    "kotlinTestImplementation"(plugin(libs.plugins.kotlin.multiplatform))

    "antGroupingCompileOnly"(libs.ant)
    "antGroupingCompileOnly"(libs.ant.jacoco)
}

testing.suites.withType<JvmTestSuite> {
    useJUnitJupiter()
}

val test = sourceSets.getByName("test")
val testFixtures = sourceSets.getByName("testFixtures")

test.resources.srcDirs(
    dependencyMetadata("agp", dependencies.plugin(libs.plugins.android)),
    dependencyMetadata("agp", dependencies.plugin(libs.plugins.android).run {
        "$group:$name:$minAGPVersion"
    }),
)

components.named<AdhocComponentWithVariants>("java") {
    sequenceOf(
        testFixtures.apiElementsConfigurationName,
        testFixtures.runtimeElementsConfigurationName,
    ).forEach { withVariantsFromConfiguration(configurations.getByName(it)) { skip() } }
}

// allows `antGrouping` feature to be resolvable on tests
val localRepoDir = layout.buildDirectory.dir("repo")
publishing.repositories.maven(localRepoDir) { name = "Local" }
tasks.processTestResources {
    val localRepo = localRepoDir.get().asFile.absolutePath

    filesMatching("*/settings.gradle") {
        expand(mapOf("localRepo" to localRepo))
    }
}

tasks.withType<Test>().configureEach {
    dependsOn("publishAllPublicationsToLocalRepository")

    testClassesDirs += testFixtures.output.classesDirs
    environment("TEMP_DIR", temporaryDir)
    finalizedBy("${name}CodeCoverageReport")
}

tasks.withType<JacocoReport>().configureEach {
    reports.xml.required = true
}

tasks.register<Sync>("updateSpecs") {
    val gradleVersion = GradleVersion.current().version

    outputs.upToDateWhen { false }

    from(tasks.test.map { it.ignoreFailures = true; it.temporaryDir }) {
        include("project-*/gradle-$gradleVersion*/build/reports/aggregated-test-coverage/coverage.csv")
        eachFile {
            path = when {
                path.startsWith("project-java/") -> "java-coverage.csv"
                path.startsWith("project-android/") -> "android-coverage.csv"
                else -> error("Unexpected file: $path")
            }
        }
    }
    into(layout.projectDirectory.dir("src/test/resources/coverage-expects"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    includeEmptyDirs = false
}

afterEvaluate {
    tasks.named<Jar>("javadocJar") {
        from(tasks.dokkaGeneratePublicationJavadoc)
    }
}

tasks.validatePlugins {
    enableStricterValidation = true
}

tasks.jar {
    // workaround for missing jar when consuming `libs/plugin-ant-grouping.jar` capability from demo build
    dependsOn("antGroupingJar")
}

tasks.check {
    dependsOn(tasks.withType<JacocoReport>())
}

tasks.withType<PublishToMavenRepository>().configureEach {
    mustRunAfter(tasks.publishPlugins)
}

tasks.publishPlugins {
    enabled = "$version".matches("\\d+(\\.\\d+)+".toRegex())
}

tasks.publish {
    dependsOn(tasks.publishPlugins)
}
