plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.ocmobile"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.ocmobile"
        minSdk = 26
        // 28 — осознанно: proot запускает бинарники из каталога данных приложения,
        // а с targetSdk >= 29 Android запрещает exec из app_data_file (как у Termux).
        // APK раздаём файлом, не через Play.
        targetSdk = 28
        versionCode = 1
        versionName = "0.0.1 beta-1"
        ndk { abiFilters += "arm64-v8a" }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Пока нет своего ключа — подписываем debug-ключом, чтобы APK ставился на телефон.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures { compose = true; buildConfig = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        // proot и его библиотеки лежат как lib*.so и должны быть распакованы на диск,
        // иначе их нельзя запустить как исполняемые файлы.
        jniLibs.useLegacyPackaging = true
        jniLibs.keepDebugSymbols += "**/*.so"
        resources.excludes += listOf("/META-INF/{AL2.0,LGPL2.1}", "DebugProbesKt.bin")
    }

    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.coil.network)

    testImplementation(libs.junit)
}
