plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.malluwayne.batbloom"
    // Built for Android 17. Its target-37 changes were checked (widgets: see WidgetLook.update; no audio is played).
    compileSdk = 37
    compileSdkMinor = 1

    defaultConfig {
        applicationId = "com.malluwayne.batbloom"
        minSdk = 26
        targetSdk = 37
        versionCode = 31
        versionName = "1.30"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release key: kept outside the project (path and password in ~/.gradle/gradle.properties, never in git).
    // Every update must be signed with it, or Android refuses to install it over the old version.
    val releaseKey = providers.gradleProperty("BATBLOOM_STORE_FILE").orNull?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (releaseKey != null) create("release") {
            storeFile = releaseKey
            storePassword = providers.gradleProperty("BATBLOOM_STORE_PASSWORD").get()
            keyAlias = providers.gradleProperty("BATBLOOM_KEY_ALIAS").get()
            keyPassword = providers.gradleProperty("BATBLOOM_KEY_PASSWORD").get()
        }
    }

    buildTypes {
        release {
            // Shrunk and optimised: smaller APK, faster start. Keep rules in proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName(if (releaseKey != null) "release" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

ksp {
    arg("room.generateKotlin", "true")
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.11.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("junit:junit:4.13.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.11.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// The logic tests run before every release build, so a broken one stops the release.
tasks.matching { it.name == "assembleRelease" }.configureEach { dependsOn("testDebugUnitTest") }
