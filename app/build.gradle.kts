plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.regainfocus.app"
    compileSdk = 35
    buildToolsVersion = "36.1.0"

    defaultConfig {
        applicationId = "com.regainfocus.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 101
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            val ksFile = rootProject.file("keystore/regainfocus-release.jks")
            if (ksFile.exists()) {
                storeFile = ksFile
                storePassword = "regainfocus2026"
                keyAlias = "regainfocus"
                keyPassword = "regainfocus2026"
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("sideload") {
            dimension = "distribution"
            // Direct browser/file-manager sideload edition: omits BIND_ACCESSIBILITY_SERVICE so it
            // does not trigger Google Play Protect Enhanced Fraud Protection on internet-sideloaded
            // installs; uses UsageStatsManager + SYSTEM_ALERT_WINDOW shield during Focus Sessions.
            buildConfigField("boolean", "HAS_ACCESSIBILITY_BLOCKER", "false")
            versionNameSuffix = "-sideload"
        }
        create("playstore") {
            dimension = "distribution"
            // Google Play Store / ADB edition: uses policy-compliant AccessibilityService
            // (canRetrieveWindowContent="false", prominent in-app disclosure) without SYSTEM_ALERT_WINDOW.
            buildConfigField("boolean", "HAS_ACCESSIBILITY_BLOCKER", "true")
            versionNameSuffix = "-playstore"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "DebugProbesKt.bin"
            excludes += "kotlin-tooling-metadata.json"
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
