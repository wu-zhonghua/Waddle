plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.waddle.android"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "dev.waddle.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "0.5.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += listOf(providers.gradleProperty("waddleAbi").getOrElse("arm64-v8a")) }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets["main"].jniLibs.srcDir("../runtime/generated/jniLibs")
    sourceSets["main"].assets.srcDir("../runtime/generated/assets")
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
    implementation("androidx.core:core-ktx:1.16.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
}

tasks.named("preBuild") {
    doFirst {
        check(file("../runtime/generated/assets/runtime/manifest.json").isFile) {
            "Prepare the embedded runtime first: python3 tools/prepare-runtime.py --ndk <Android NDK path>"
        }
        check(!file("../runtime/generated").walkTopDown().any {
            it.name in setOf("libwaddle_codex.so", "libwaddle_helper_.so", "libwaddle_supervisor.so")
        }) { "Stale Codex payload: rerun tools/prepare-runtime.py before building the AI terminal" }
        val abi = providers.gradleProperty("waddleAbi").getOrElse("arm64-v8a")
        check(file("../runtime/generated/jniLibs/$abi/libwaddle_bash.so").isFile) {
            "Embedded runtime ABI mismatch: prepare-runtime.py --abi $abi before building"
        }
    }
}
