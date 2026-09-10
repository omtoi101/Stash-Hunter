plugins {
    // Fully-qualified id, not the short "fabric-loom" legacy alias: that one resolves to
    // Loom's old remap-always plugin class, which still demands a mappings dependency even
    // though Minecraft has shipped unobfuscated since 26.1 and none exists to give it.
    id("net.fabricmc.fabric-loom") version "1.17-SNAPSHOT"
}

val archivesBaseName = providers.gradleProperty("archives_base_name").get()

base {
    archivesName = archivesBaseName
    version = providers.gradleProperty("mod_version").get()
    group = providers.gradleProperty("maven_group").get()
}

repositories {
    maven {
        name = "meteor-maven"
        url = uri("https://maven.meteordev.org/releases")
    }
    maven {
        name = "meteor-maven-snapshots"
        url = uri("https://maven.meteordev.org/snapshots")
    }
    maven {
        name = "Fabric"
        url = uri("https://maven.fabricmc.net/")
    }
    mavenCentral()
}

dependencies {
    // Fabric
    // No mappings(...) call, and plain implementation(...) instead of modImplementation(...):
    // Minecraft has shipped fully unobfuscated since 26.1 (Mojang announced Oct 2025), so Loom
    // no longer remaps anything and Mojang no longer publishes client_mappings at all (verified
    // against piston-meta for 26.2). modImplementation's remap-aware resolution would otherwise
    // still try to pull a 'mappings' dependency that no longer exists. Matches the official
    // fabric-example-mod and meteor-addon-template 26.2 branches.
    minecraft("com.mojang:minecraft:${providers.gradleProperty("minecraft_version").get()}")
    implementation("net.fabricmc:fabric-loader:${providers.gradleProperty("loader_version").get()}")
    implementation("net.fabricmc.fabric-api:fabric-api:${providers.gradleProperty("fabric_version").get()}")

    // Meteor
    implementation("meteordevelopment:meteor-client:${providers.gradleProperty("minecraft_version").get()}-SNAPSHOT")

    // GSON
    implementation("com.google.code.gson:gson:2.10.1")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(providers.gradleProperty("jdk_version").get().toInt())
    }
}

tasks {
    processResources {
        val propertyMap = mapOf(
            "version" to project.version,
            "mc_version" to providers.gradleProperty("minecraft_version").get(),
        )

        inputs.properties(propertyMap)

        filteringCharset = "UTF-8"

        filesMatching("fabric.mod.json") {
            expand(propertyMap)
        }
    }

    jar {
        inputs.property("archivesName", archivesBaseName)

        from("LICENSE") {
            rename { "${it}_${archivesBaseName}" }
        }
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-Xlint:deprecation")
        options.compilerArgs.add("-Xlint:unchecked")
    }
}
