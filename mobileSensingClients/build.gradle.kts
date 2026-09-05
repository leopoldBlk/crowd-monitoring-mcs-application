// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.8.20" apply false
}

buildscript {
    dependencies {
        // Plugin for integrating the config parameters specified in a local.properties file into
        // the BuildConfig class in order to use the parameters within the implementation.
        // Source: https://github.com/google/secrets-gradle-plugin
        classpath("com.google.android.libraries.mapsplatform.secrets-gradle-plugin:secrets-gradle-plugin:2.0.1")
    }
}