import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootEnvSpec

plugins {
    alias(libs.plugins.android.multiplatform)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.ksp)
    id("io.github.gmazzo.test.aggregation")
}

java.toolchain.languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get().toInt()))

kotlin {
    android {
        namespace = "com.example.login"

        compileSdk = libs.versions.android.compileSDK.get().toInt()
        minSdk = libs.versions.android.minSDK.get().toInt()

        withHostTest { enableCoverage = true }
        withDeviceTest { enableCoverage = true }
        aggregateTests = true
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    jvm { aggregateTests = true }
    js { nodejs(); aggregateTests = true }
    linuxX64()
    linuxArm64()
}

// FIXME remove this block
androidComponents.onVariants { variant ->
    variant.aggregateTests = true
    variant.hostTests.values.forEach {
        it.aggregateTests = true
    }
    variant.deviceTests.values.forEach {
       it.aggregateTests = true
    }
}

listOf(
    the<NodeJsEnvSpec>(),
    rootProject.the<NodeJsEnvSpec>(),
    rootProject.the<YarnRootEnvSpec>(),
).forEach {
    it.download = false
    it.downloadBaseUrl = null
}

dependencies {
    "kspCommonMainMetadata"(libs.moshi.codegen)
    "androidMainImplementation"(libs.moshi.kotlin)
    "jvmMainImplementation"(libs.moshi.kotlin)
    commonTestImplementation(libs.kotlin.test)
}
