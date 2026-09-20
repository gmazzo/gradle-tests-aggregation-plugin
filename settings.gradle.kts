plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

apply(from = "gradle/shared.settings.gradle.kts")

rootProject.name = "gradle-test-aggregation-plugin"

includeBuild("plugin")
include("demo-project:app")
include("demo-project:domain")
include("demo-project:login")
include("demo-project:kmp")
include("demo-project:ui-tests")
