plugins {
    alias(libs.plugins.android)
    id("io.github.gmazzo.test.aggregation")
}

java.toolchain.languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get().toInt()))

androidComponents {
    onVariants {
        it.aggregateTests = it.buildType == "debug"
    }
}

android {
    namespace = "com.example.myapplication"
    buildFeatures.viewBinding = true

    compileSdk = libs.versions.android.compileSDK.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSDK.get().toInt()

        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            enableUnitTestCoverage = true
        }
        release {
            isMinifyEnabled = false
            signingConfig = getByName("debug").signingConfig
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    flavorDimensions += "environment"
    productFlavors {
        create("stage") {
            isDefault = true
            dimension = "environment"
        }
        create("prod") {
            dimension = "environment"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(projects.demoProject.domain)
    implementation(projects.demoProject.login)

    implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.google.material)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.livedata)
    implementation(libs.androidx.viewmodel)
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)

    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.robolectric)
}
