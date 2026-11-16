plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinCompose)
}

kotlin {
    jvmToolchain(17)
}

android {
    namespace = "kaist.iclab.benchmark.wearable"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "kaist.iclab.benchmark"
        minSdk = 30
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"
        ndk {
            // The SM-L325N Wear OS image exposes armeabi-v7a only; excluding
            // emulator/unused JNI libraries makes wireless ADB deployment smaller.
            abiFilters += "armeabi-v7a"
        }
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Week 3 research build installs beside the lab benchmark app.
            applicationIdSuffix = ".week3"
            versionNameSuffix = "-week3"
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
    }

}
androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            output.outputFileName.set("EnPULSE-Watch-Benchmark.apk")
        }
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.wear.compose.material)
    implementation(libs.wear.input)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.wear.tooling.preview)
    implementation(libs.compose.activity)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.compose.foundation.layout)
    debugImplementation(libs.compose.ui.tooling)

    // icons
    implementation(libs.compose.material.icons.extended)

    // core
    implementation(libs.androidx.core.ktx)
    implementation(libs.tensorflow.lite)
    testImplementation(libs.junit)

    // wearable data layer
    implementation(libs.android.gms.wearable)
}
