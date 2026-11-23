plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val distribution = providers.gradleProperty("waddleDistribution").getOrElse("international")
require(distribution in setOf("international", "domestic"))
val runtimeDir = "../runtime/generated/$distribution"

android {
    namespace = "dev.waddle.android"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "dev.waddle.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "0.6.0-$distribution"
        buildConfigField("boolean", "BUNDLED_AGENTS", (distribution == "international").toString())
        testInstrumentationRunner = "dev.waddle.android.WaddleTestRunner"
        ndk { abiFilters += listOf(providers.gradleProperty("waddleAbi").getOrElse("arm64-v8a")) }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets["main"].jniLibs.srcDir("$runtimeDir/jniLibs")
    sourceSets["main"].assets.srcDir("$runtimeDir/assets")
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        jniLibs.useLegacyPackaging = true
        jniLibs.keepDebugSymbols += "**/*.so"
    }
}

kotlin { compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17 } }

dependencies {
    implementation(project(":terminal-view"))
    implementation(platform("androidx.compose:compose-bom:2025.08.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.core:core-ktx:1.19.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
}

tasks.named("preBuild") {
    doFirst {
        check(file("$runtimeDir/assets/runtime/manifest.json").isFile) {
            "Prepare the embedded runtime first: python3 tools/prepare-runtime.py --ndk <Android NDK path>"
        }
        val manifest = file("$runtimeDir/assets/runtime/manifest.json").readText()
        check(manifest.contains("\"distribution\": \"$distribution\"")) { "Runtime distribution mismatch" }
        if (distribution == "domestic") check(!file(runtimeDir).walkTopDown().any {
            it.name in setOf("libwaddle_codex.so", "libwaddle_helper_.so", "libwaddle_supervisor.so")
        }) { "Domestic runtime contains agent binaries" }
        val abi = providers.gradleProperty("waddleAbi").getOrElse("arm64-v8a")
        check(file("$runtimeDir/jniLibs/$abi/libwaddle_bash.so").isFile) {
            "Embedded runtime ABI mismatch: prepare-runtime.py --abi $abi before building"
        }
    }
}
