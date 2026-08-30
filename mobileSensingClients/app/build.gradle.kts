plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // Source: https://github.com/google/secrets-gradle-plugin
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
    // Source: https://github.com/kotlin/kotlinx.serialization
    kotlin("plugin.serialization") version "2.3.20"
}

android {
    namespace = "com.leopoldBlk.mobileSensingClients"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.leopoldBlk.mobileSensingClients"
        minSdk = 36
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Necessary for running H3 Library on Android, Source: https://github.com/isaacbrodsky/h3-android-sample.git
        ndk {
            abiFilters.add("armeabi-v7a")
            abiFilters.add("arm64-v8a")
        }

        // Necessary for running H3 Library on Android, Source: https://github.com/isaacbrodsky/h3-android-sample.git
        externalNativeBuild {
            cmake {
                arguments.add("-DANDROID_STL=c++_shared")
                cppFlags += ""
            }
        }

        buildFeatures {
            buildConfig = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    packaging {
        resources {
            pickFirsts.add("META-INF/INDEX.LIST")
            pickFirsts.add("META-INF/io.netty.versions.properties")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }

    // Necessary for running H3 Library on Android, Source: https://github.com/isaacbrodsky/h3-android-sample.git
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.material)
    implementation(libs.play.services.location)
    // Library for determination of crowd density
    implementation("crowdDensityLibrary:lib:0.1")
    // Sources: https://h3geo.org/docs/api/indexing,
    // https://mvnrepository.com/artifact/com.uber/h3-android,
    // https://github.com/isaacbrodsky/h3-android-sample
    implementation("com.uber:h3-android:4.4.0")
    // Sources: https://maplibre.org/maplibre-compose/getting-started/
    // https://madewithmaplibre.com/sdks/maplibre-compose
    // https://maplibre.org/maplibre-compose/api/lib/maplibre-compose/
    implementation("org.maplibre.compose:maplibre-compose:0.13.0")
    // Source: https://maplibre.org/spatial-k/geojson/
    implementation("org.maplibre.spatialk:geojson:0.7.0")
    // Sources: https://mvnrepository.com/artifact/androidx.compose.runtime/runtime-livedata
    // https://stackoverflow.com/questions/66560404/jetpack-compose-unresolved-reference-observeasstate
    implementation("androidx.compose.runtime:runtime-livedata:1.11.2")
    // Source: https://www.rabbitmq.com/client-libraries/java-client
    implementation("com.rabbitmq:amqp-client:5.31.0")
    // Sources: https://android-developers.googleblog.com/2025/02/trustedtime-api-introducing-reliable-approach-to-time-keeping-for-apps.html
    // https://developers.google.com/location-context/time
    implementation("com.google.android.gms:play-services-time:16.0.1")
    // Source: https://github.com/kotlin/kotlinx.serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}