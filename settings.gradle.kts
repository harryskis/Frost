pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/") {
            name = "Fabric"
        }
    }

    val loom_version: String by settings
    val kotlin_version: String by settings

    plugins {
        // Loom 1.17.x hard-requires a `mappings` dependency, which Minecraft 26.x has
        // nothing to supply (it ships deobfuscated - see build.gradle.kts). The 1.16 line
        // is what the 26.x-era ecosystem actually builds against; Odin pins the same
        // version for the same reason.
        id("net.fabricmc.fabric-loom") version loom_version
        kotlin("jvm") version kotlin_version
    }
}

// Minecraft 26.x requires Java 25 (Loom refuses to set the game up on anything older).
// This resolver lets Gradle fetch a matching JDK on demand for anyone building the repo
// who doesn't already have one.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "frost"
