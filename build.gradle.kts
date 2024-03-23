plugins {
    kotlin("jvm") version "1.9.23"
    kotlin("plugin.serialization") version "1.9.23"
    application
    `java-library`
    `maven-publish`
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "moe.styx"
version = "0.0.5"

repositories {
    mavenCentral()
    maven("https://repo.styx.moe/releases")
    maven("https://jitpack.io")
}

dependencies {
    implementation("moe.styx:styx-db:0.0.8")
    implementation("com.github.Vodes:PircBot:873bc4aa78")

    implementation("net.peanuuutz.tomlkt:tomlkt:0.3.7")
    implementation("com.github.ajalt.mordant:mordant:2.4.0")
    implementation("de.androidpit:color-thief:1.1.2")
    implementation("com.google.guava:guava:33.1.0-jre")
    implementation("com.apptasticsoftware:rssreader:3.6.0")
    implementation("com.mysql:mysql-connector-j:8.3.0")
    implementation("org.apache.commons:commons-collections4:4.4")
    implementation("com.twelvemonkeys.imageio:imageio-core:3.10.1")
    runtimeOnly("com.twelvemonkeys.imageio:imageio-webp:3.10.1")

    implementation("org.javacord:javacord:3.8.0")
    implementation("org.slf4j:slf4j-simple:2.0.12")
    runtimeOnly("org.apache.logging.log4j:log4j-to-slf4j:2.23.1")

    implementation("com.github.btmxh:anitomyJ:f6e9cea8f8")
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
    repositories {
        maven {
            name = "Styx"
            url = if (version.toString().contains("-SNAPSHOT", true))
                uri("https://repo.styx.moe/snapshots")
            else
                uri("https://repo.styx.moe/releases")
            credentials {
                username = System.getenv("STYX_REPO_TOKEN")
                password = System.getenv("STYX_REPO_SECRET")
            }
            authentication {
                create<BasicAuthentication>("basic")
            }
        }
    }
    publications {
        create<MavenPublication>("build") {
            groupId = project.group.toString()
            artifactId = "styx-downloader"
            version = project.version.toString()

            from(components["java"])
        }
    }
}