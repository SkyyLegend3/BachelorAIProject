plugins {
    id("com.android.library")
}

android {
    namespace = "com.arm.aichat.stub"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDir(
                rootProject.file(
                    "_restore_backup/lib_llama_broken_20260317_093717/build/intermediates/library_jni/debug/copyDebugJniLibsProjectOnly/jni"
                )
            )
        }
    }
}

dependencies {
    api(
        files(
            rootProject.file(
                "_restore_backup/lib_llama_broken_20260317_093717/build/intermediates/compile_library_classes_jar/debug/bundleLibCompileToJarDebug/classes.jar"
            )
        )
    )
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}



