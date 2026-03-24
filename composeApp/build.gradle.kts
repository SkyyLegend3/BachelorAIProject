import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

// Lese lokale Properties (wird NICHT in VCS eingecheckt)
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) load(file.inputStream())
}
val openAiApiKey: String = localProperties.getProperty("openai.api.key", "")
val llamaModelPath: String = localProperties.getProperty("llama.model.path", "")
val whisperModelPath: String = localProperties.getProperty("whisper.model.path", "")
val llamaPerformanceMode: Boolean = localProperties
    .getProperty("llama.performance.mode", "true")
    .toBooleanStrictOrNull()
    ?: true
val llamaPredictLength: Int = localProperties
    .getProperty(
        "llama.predict.length",
        if (llamaPerformanceMode) "32" else "128",
    )
    .toIntOrNull()
    ?.coerceAtLeast(1)
    ?: if (llamaPerformanceMode) 32 else 128
val llamaInferenceTimeoutMs: Long = localProperties
    .getProperty(
        "llama.inference.timeout.ms",
        if (llamaPerformanceMode) "60000" else "90000",
    )
    .toLongOrNull()
    ?.coerceAtLeast(5_000L)
    ?: if (llamaPerformanceMode) 60_000L else 90_000L
val llamaContextSize: Int = localProperties
    .getProperty("llama.n.ctx", "512")
    .toIntOrNull()
    ?.coerceAtLeast(128)
    ?: 512
val llamaTemperature: Double = localProperties
    .getProperty("llama.temperature", "0.0")
    .toDoubleOrNull()
    ?.coerceAtLeast(0.0)
    ?: 0.0
val llamaThreadsMin: Int = localProperties
    .getProperty("llama.threads.min", "2")
    .toIntOrNull()
    ?.coerceAtLeast(1)
    ?: 2
val llamaThreadsMax: Int = localProperties
    .getProperty("llama.threads.max", "4")
    .toIntOrNull()
    ?.coerceAtLeast(llamaThreadsMin)
    ?: 4

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinxSerialization)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    @Suppress("OPT_IN_USAGE")
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }
    
    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.client.okhttp)
            implementation(project(":whisperAndroidLib"))
            // Reihenfolge bewusst: bei kollidierenden ggml-Shared-Libs soll die
            // llamaAndroidLib-Variante im finalen APK bevorzugt werden.
            implementation(project(":llamaAndroidLib"))
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

android {
    namespace = "com.example.bachelor_ai_project"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.example.bachelor_ai_project"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "OPENAI_API_KEY", "\"$openAiApiKey\"")
        buildConfigField("String", "LLAMA_MODEL_PATH", "\"$llamaModelPath\"")
        buildConfigField("String", "WHISPER_MODEL_PATH", "\"$whisperModelPath\"")
        buildConfigField("boolean", "LLAMA_PERFORMANCE_MODE", "$llamaPerformanceMode")
        buildConfigField("int", "LLAMA_PREDICT_LENGTH", "$llamaPredictLength")
        buildConfigField("long", "LLAMA_INFERENCE_TIMEOUT_MS", "${llamaInferenceTimeoutMs}L")
        buildConfigField("int", "LLAMA_N_CTX", "$llamaContextSize")
        buildConfigField("float", "LLAMA_TEMPERATURE", "${llamaTemperature}f")
        buildConfigField("int", "LLAMA_THREADS_MIN", "$llamaThreadsMin")
        buildConfigField("int", "LLAMA_THREADS_MAX", "$llamaThreadsMax")
    }
    packaging {
        jniLibs {
            // llamaAndroidLib und whisperAndroidLib liefern beide libomp.so aus.
            // Wir nehmen die erste gefundene Variante, um den Merge-Konflikt zu vermeiden.
            pickFirsts += setOf(
                "**/libomp.so",
                "**/libggml*.so",
            )
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(libs.compose.uiTooling)
}
