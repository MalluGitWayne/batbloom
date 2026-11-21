plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.malluwayne.batbloom"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.malluwayne.batbloom"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "1.4"
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
    implementation(platform("androidx.compose:compose-bom:2026.02.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

// The logic tests run before every release build, so a broken one stops the release.
tasks.matching { it.name == "assembleRelease" }.configureEach { dependsOn("testDebugUnitTest") }
