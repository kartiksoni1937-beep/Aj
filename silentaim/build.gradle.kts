plugins {
    // 1.21.x is obfuscated, so the remapping flavour of Loom is required.
    id("net.fabricmc.fabric-loom-remap") version "1.16-SNAPSHOT"
}

group = property("maven_group") as String
version = property("mod_version") as String

base {
    archivesName = property("archives_base_name") as String
}

repositories {
    mavenCentral()
}

dependencies {
    add("minecraft", "com.mojang:minecraft:${property("minecraft_version")}")
    add("mappings", "net.fabricmc:yarn:${property("yarn_mappings")}:v2")
    add("modImplementation", "net.fabricmc:fabric-loader:${property("loader_version")}")
    add("modImplementation", "net.fabricmc.fabric-api:fabric-api:${property("fabric_version")}")
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
    options.encoding = "UTF-8"
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand(mapOf("version" to project.version.toString()))
    }
}
