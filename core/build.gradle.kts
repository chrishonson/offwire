import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
    alias(libs.plugins.kotlinSerialization)
}

// The sherpa-onnx AAR is not on Maven Central, and a library module cannot take a files() AAR dependency.
// Repositories are declared in settings.gradle.kts dependencyResolutionManagement.repositories.

kotlin {
    androidTarget {
        @OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
        instrumentedTestVariant.sourceSetTree.set(org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree.test)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        androidMain.dependencies {
            api(libs.litertlm.android)
            implementation(libs.onnxruntime)
            api(mapOf("name" to "sherpa-onnx-static-link-onnxruntime-2.0.1", "ext" to "aar"))
            implementation(libs.commons.compress)
            implementation(libs.androidx.activity)
            implementation(libs.androidx.core.ktx)
        }
        commonMain.dependencies {
            api(libs.androidx.lifecycle.viewmodel)
            api(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        val androidInstrumentedTest by getting {
            dependencies {
                implementation(libs.androidx.test.runner)
                implementation(libs.androidx.test.core)
                implementation(libs.androidx.testExt.junit)
            }
        }
    }
}

android {
    buildFeatures {
        buildConfig = true
    }
    namespace = "com.chrishonson.gemmavoice.core"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    packaging {
        jniLibs {
            // The instrumented test APK merges the sherpa-onnx AAR with onnxruntime-android, and only their x86
            // emulator builds both ship libonnxruntime.so; arm64 has none.
            pickFirsts += "lib/x86/libonnxruntime.so"
        }
    }
}

kover {
    reports {
        filters {
            excludes {
                classes(
                    "*ReplyVolume*",
                    "*PocketSpeechSynthesizer*",
                    "*VoiceResources*"
                )
            }
        }
        verify {
            rule {
                minBound(54)
            }
        }
    }
}

detekt {
    toolVersion = libs.versions.detekt.get()
    config.setFrom(file("../config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    allRules = false
    autoCorrect = false
    source.setFrom(files("src"))
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    reports {
        html.required.set(true)
        xml.required.set(true)
        txt.required.set(false)
        sarif.required.set(false)
    }
}
