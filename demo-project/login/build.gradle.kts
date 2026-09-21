plugins {
    alias(libs.plugins.android.lib)
    alias(libs.plugins.kotlin.compose)
    id("io.github.gmazzo.test.aggregation")
}

java.toolchain.languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get().toInt()))

androidComponents {
    onVariants {
        it.aggregateTests = it.buildType == "debug"
    }
}

android {
    namespace = "com.example.login"
    buildFeatures.viewBinding = true
    testFixtures.enable = true

    compileSdk = libs.versions.android.compileSDK.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSDK.get().toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        debug {
            enableUnitTestCoverage = true
            enableAndroidTestCoverage = true
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    testOptions {
        screenshotTests.create("screenshotTest") {
            engineVersion = libs.versions.screenshot.tests.get()
        }
    }
}

dependencies {
    api(platform(libs.androidx.compose))

    implementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.google.material)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.livedata)
    implementation(libs.androidx.viewmodel)

    testImplementation(libs.kotlin.test.junit)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso)

    "screenshotTestImplementation"(libs.androidx.compose.ui.tooling)
    "screenshotTestImplementation"(libs.android.tools.screenshot.validation.api)
}
