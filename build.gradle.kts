plugins {
    kotlin("jvm") version "2.0.10"
    kotlin("plugin.serialization") version "2.0.10"
    application
    `java-library`
    `maven-publish`
    id("io.github.goooler.shadow") version "8.1.7"
}

group = "moe.styx"
version = "0.3.3"

repositories {
    mavenCentral()
    maven("https://repo.styx.moe/releases")
    maven("https://repo.styx.moe/snapshots")
    maven("https://jitpack.io")
}

dependencies {
    implementation("moe.styx:styx-common-jvm:0.1.3")
    implementation("moe.styx:styx-db:0.2.3")
    implementation("com.github.Vodes:PircBot:master-SNAPSHOT")
    implementation("com.github.Vodes:anitomyJ:99a355bf0d")

    implementation("net.peanuuutz.tomlkt:tomlkt:0.3.7")
    implementation("com.github.ajalt.mordant:mordant:2.6.0")
    implementation("de.androidpit:color-thief:1.1.2")
    implementation("com.google.guava:guava:33.2.0-jre")
    implementation("org.postgresql:postgresql:42.7.3")
    implementation("com.apptasticsoftware:rssreader:3.6.0")
    implementation("org.apache.commons:commons-collections4:4.4")
    implementation("com.twelvemonkeys.imageio:imageio-core:3.10.1")
    runtimeOnly("com.twelvemonkeys.imageio:imageio-webp:3.10.1")

    implementation("org.javacord:javacord:3.8.0")
    implementation("org.slf4j:slf4j-simple:2.0.12")
    runtimeOnly("org.apache.logging.log4j:log4j-to-slf4j:2.23.1")
    implementation("commons-net:commons-net:3.10.0")

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("moe.styx.downloader.MainKt")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
    withJavadocJar()
}

tasks.register("shadow-ci") {
    dependsOn("shadowJar")
    doLast {
        val buildDir = File(projectDir, "build")
        buildDir.walk().find { it.extension == "jar" && it.name.contains("-all") }?.copyTo(File(projectDir, "app.jar"))
    }
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