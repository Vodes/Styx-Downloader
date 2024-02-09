plugins {
    kotlin("jvm") version "1.9.22"
    kotlin("plugin.serialization") version "1.9.22"
    application
    `java-library`
    `maven-publish`
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

val ktorVersion = "2.3.8"

group = "moe.styx"
version = "0.0.2"

repositories {
    mavenCentral()
    mavenLocal()
    maven("https://jitpack.io")
}

dependencies {
    implementation("moe.styx:styx-db:0.0.5")
    implementation("com.github.Vodes:PircBot:873bc4aa78")

    implementation("net.peanuuutz.tomlkt:tomlkt:0.3.7")
    implementation("org.javacord:javacord:3.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")
    implementation("com.github.ajalt.mordant:mordant:2.2.0")
    implementation("de.androidpit:color-thief:1.1.2")
    implementation("com.google.guava:guava:33.0.0-jre")
    implementation("com.apptasticsoftware:rssreader:3.6.0")
    implementation("com.mysql:mysql-connector-j:8.2.0")
    implementation("org.apache.commons:commons-collections4:4.4")
    implementation("com.twelvemonkeys.imageio:imageio-core:3.10.1")
    runtimeOnly("com.twelvemonkeys.imageio:imageio-webp:3.10.1")

    implementation("com.github.btmxh:anitomyJ:f6e9cea8f8")
    implementation("org.slf4j:slf4j-simple:2.0.9")
    implementation("commons-net:commons-net:3.10.0")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("moe.styx.downloader.MainKt")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("build") {
            groupId = "moe.styx"
            artifactId = "styx-downloader"
            version = "0.0.2"

            from(components["java"])
        }
    }
}

configurations {

}