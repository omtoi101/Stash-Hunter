plugins {
    // Fully-qualified id, not the short "fabric-loom" legacy alias: that one resolves to
    // Loom's old remap-always plugin class, which still demands a mappings dependency even
    // though Minecraft has shipped unobfuscated since 26.1 and none exists to give it.
    // Pinned release instead of 1.17-SNAPSHOT. Loom 1.18+ needs Gradle itself to run on JDK 25
    // (and 1.19 Gradle >= 9.8), which would break builds started from a JDK 21 shell.
    id("net.fabricmc.fabric-loom") version "1.17.21"
    id("com.diffplug.spotless") version "7.0.2"
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
    // Xaero's Minimap (optional, compile-time only). Modrinth's maven only serves "maven.modrinth".
    exclusiveContent {
        forRepository {
            maven {
                name = "Modrinth"
                url = uri("https://api.modrinth.com/maven")
            }
        }
        filter {
            includeGroup("maven.modrinth")
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
    // No Fabric API: the only hook it provided (client disconnect) is covered by Meteor's GameLeftEvent.
    // Meteor bundles fabric-resource-loader-v1 (this exact version); its injected interfaces show up
    // in Minecraft's own class signatures, so javac needs the module to compile. Runtime comes from Meteor.
    // Hard pin: when porting to a newer MC/Meteor, take the version from Meteor's bundled jars list.
    compileOnly("net.fabricmc.fabric-api:fabric-resource-loader-v1:2.0.13+9edec1269e")

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

    // Xaero's Minimap (optional / soft dependency, All Rights Reserved): compileOnly, never bundled.
    // Every call is gated behind FabricLoader.isModLoaded("xaerominimap") via
    // com.stashhunter.stashhunter.xaero.XaeroBridge, like Baritone above. The version must match
    // the Minecraft version; take it from https://modrinth.com/mod/xaeros-minimap/versions.
    compileOnly("maven.modrinth:xaeros-minimap:${providers.gradleProperty("xaero_minimap_version").get()}")

    // No separate Gson dependency: Minecraft ships Gson and puts it on the classpath already.

    // Tests (pure logic only: clustering, waypoint generation, data formats)
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Whitespace only (matches .editorconfig); `build` runs spotlessCheck, `spotlessApply` fixes it.
spotless {
    // Git normalizes line endings itself (.gitattributes), so a CRLF/LF mix in a working copy is no violation.
    lineEndings = com.diffplug.spotless.LineEnding.PRESERVE
    java {
        target("src/**/*.java")
        leadingTabsToSpaces(4)
        trimTrailingWhitespace()
        endWithNewline()
    }
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

    test {
        useJUnitPlatform()
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-Xlint:deprecation")
        options.compilerArgs.add("-Xlint:unchecked")
    }
}
