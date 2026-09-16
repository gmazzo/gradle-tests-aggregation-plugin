plugins {
    alias(libs.plugins.kotlin.jvm)
    id("io.github.gmazzo.test.aggregation")
    jacoco
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get().toInt()))

    registerFeature("foo") {
        usingSourceSet(sourceSets.maybeCreate("foo"))
    }
    registerFeature("bar") {
        usingSourceSet(sourceSets.maybeCreate("bar"))
    }
}

testing.suites {
    withType<JvmTestSuite> { useJUnit() }
    register<JvmTestSuite>("testFoo") {
        aggregatedTestCoverage.addTestSuite(sourceSets.getByName("foo"), this)
    }
    register<JvmTestSuite>("testBar"){
        aggregatedTestCoverage.addTestSuite(sourceSets.getByName("bar"), this)
    }
}

dependencies {
    "testFooImplementation"(project(path)) {
        capabilities {
            requireCapability("$group:$name-foo")
        }
    }
    "testBarImplementation"(project(path)) {
        capabilities {
            requireCapability("$group:$name-bar")
        }
    }
}
