pluginManagement {
    repositories {
        maven {
            name = "Fabric"
            url = uri("https://maven.fabricmc.net/")
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

// Auto-provisions the correct JDK (25, per gradle.properties) for Loom 1.17 / MC 26.2
// even if it isn't installed locally - matches the official meteor-addon-template.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
