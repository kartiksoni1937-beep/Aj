import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    application
    java
    id("org.jetbrains.kotlin.jvm") version "2.1.10"
}

group = "war.jnt"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(22))
    }
}

kotlin {
    jvmToolchain(22)
    sourceSets {
        named("main") {
            kotlin.srcDirs("src/main/java")
        }
        named("test") {
            kotlin.srcDirs("src/test/java", "src/test/kotlin")
        }
    }
}

sourceSets {
    named("main") {
        java.setSrcDirs(listOf("src/main/java"))
        resources.setSrcDirs(listOf("src/main/resources"))
    }
    named("test") {
        java.setSrcDirs(listOf("src/test/java", "src/test/kotlin"))
        resources.setSrcDirs(listOf("src/test/resources"))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_22)
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

dependencies {
    implementation(files("libraries/asm.jar"))
    implementation("org.benf:cfr:0.152")
    implementation("com.google.guava:guava:32.1.3-jre")
    implementation("commons-io:commons-io:2.16.1")
    implementation("net.sf.jopt-simple:jopt-simple:5.0.4")
    implementation("org.yaml:snakeyaml:2.3")
    implementation("com.fifesoft:rsyntaxtextarea:3.3.3")
    implementation("org.fxmisc.richtext:richtextfx:0.11.5")
    implementation("org.apache.commons:commons-text:1.10.0")
    implementation("com.google.code.gson:gson:2.12.1")
    implementation("com.formdev:flatlaf:3.6.1")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.1.10")
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.1.10")

    compileOnly("org.projectlombok:lombok:1.18.38")
    annotationProcessor("org.projectlombok:lombok:1.18.38")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.0")
    testCompileOnly("org.projectlombok:lombok:1.18.38")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.38")
}

application {
    mainClass.set("war.Entrypoint")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "war.Entrypoint"
    }
}
