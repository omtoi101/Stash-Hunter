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
    // exclusiveContent restricts Meteor's own groups (meteor-client, baritone, and meteor-client's
    // "org.meteordev:starscript" transitive dependency) to these two repos only, so Gradle never
    // also probes other declared/Loom-injected repos (e.g. Mojang's libraries.minecraft.net) for
    // them - without this, a hiccup on any other repo in search order can hard-fail resolution
    // even though these repos do have the artifact.
    exclusiveContent {
        forRepositories(
            maven {
                name = "meteor-maven"
                url = uri("https://maven.meteordev.org/releases")
            },
            maven {
                name = "meteor-maven-snapshots"
                url = uri("https://maven.meteordev.org/snapshots")
            }
        )
        filter {
            includeGroup("meteordevelopment")
            includeGroup("org.meteordev")
        }
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

    // Baritone (optional / soft dependency)
    // compileOnly, not implementation: Baritone is a separate mod the end user installs
    // themselves (see fabric.mod.json "recommends"). This addon only compiles against its
    // API surface and gates every runtime call behind FabricLoader.isModLoaded("baritone-meteor")
    // via com.stashhunter.stashhunter.baritone.BaritoneBridge, so it must start and run fine
    // without Baritone present. Resolved from the meteor-maven-snapshots repo declared above
    // (no artifact currently exists under /releases for this GAV - verify at
    // https://maven.meteordev.org/snapshots/meteordevelopment/baritone/maven-metadata.xml
    // if this ever stops resolving; fallback is building baritone-api from the
    // MeteorDevelopment/baritone 26.2 branch locally and publishing to mavenLocal()).
    compileOnly("meteordevelopment:baritone:${providers.gradleProperty("baritone_version").get()}")

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
