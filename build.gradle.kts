plugins {
    // Versions come from gradle.properties via settings.gradle.kts's pluginManagement.
    id("net.fabricmc.fabric-loom")
    kotlin("jvm")
    `maven-publish`
}

val minecraftVersion = project.property("minecraft_version") as String
val loaderVersion = project.property("loader_version") as String
val fabricVersion = project.property("fabric_version") as String
val fabricKotlinVersion = project.property("fabric_kotlin_version") as String

version = project.property("mod_version") as String
group = project.property("maven_group") as String

base {
    archivesName.set(project.property("archives_base_name") as String)
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/") { name = "Fabric" }
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    // NO mappings() call, deliberately. Minecraft 26.x ships deobfuscated, so there is
    // nothing to map: the class names in the game jar ARE the real names. Confirmed three
    // ways - Yarn publishes 0 builds for any 26.x version, meta.fabricmc.net returns a
    // stub `intermediary:0.0.0`, and Odin (a live Hypixel Skyblock Kotlin mod on 26.1.2)
    // configures no mappings at all either. Adding officialMojangMappings() here fails
    // outright with "Failed to find official mojang mappings".
    // Plain `implementation`, not `modImplementation` - with the game shipping
    // deobfuscated there is no remapping step, so Loom 1.16 no longer provides the `mod*`
    // configurations at all (Odin declares its Fabric deps the same way).
    implementation("net.fabricmc:fabric-loader:$loaderVersion")
    implementation("net.fabricmc.fabric-api:fabric-api:$fabricVersion")
    implementation("net.fabricmc:fabric-language-kotlin:$fabricKotlinVersion")

    testImplementation(kotlin("test"))
}

// Minecraft 26.x requires Java 25 - Loom refuses to set the game up on anything older,
// and Odin targets 25 the same way.
java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

kotlin {
    jvmToolchain(25)
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand(mutableMapOf("version" to project.version))
    }
}

tasks.test {
    useJUnitPlatform()
}
