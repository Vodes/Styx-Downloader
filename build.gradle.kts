plugins {
    kotlin("jvm") version "2.3.20"
    kotlin("plugin.serialization") version "2.3.20"
    application
    `java-library`
    `maven-publish`
    id("io.github.goooler.shadow") version "8.1.8"
    id("com.github.gmazzo.buildconfig") version "6.0.9"
}

group = "moe.styx"
version = "0.6.1"

repositories {
    mavenCentral()
    mavenLocal()
    maven("https://repo.styx.moe/releases")
    maven("https://repo.styx.moe/snapshots")
    maven("https://jitpack.io")
}

dependencies {
    implementation("moe.styx:styx-common:0.6.4")
    implementation("moe.styx:styx-db:0.6.1")
    implementation("com.github.Vodes:PircBot:0.2")
    implementation("com.github.Vodes:anitomyJ:80f36aceb2")

    implementation("com.github.ajalt.mordant:mordant:3.0.2")
    implementation("de.androidpit:color-thief:1.1.2")
    implementation("com.google.guava:guava:33.6.0-jre")
    implementation("org.postgresql:postgresql:42.7.11")
    implementation("com.apptasticsoftware:rssreader:3.12.0")
    implementation("org.apache.commons:commons-collections4:4.5.0")
    implementation("com.twelvemonkeys.imageio:imageio-core:3.13.1")
    runtimeOnly("com.twelvemonkeys.imageio:imageio-webp:3.13.1")

    implementation("org.javacord:javacord:3.8.0")
    implementation("org.slf4j:slf4j-simple:2.0.18")
    runtimeOnly("org.apache.logging.log4j:log4j-to-slf4j:2.26.0")
    implementation("commons-net:commons-net:3.13.0")

    testImplementation(kotlin("test"))
}

buildConfig {
    packageName("moe.styx.downloader")
    val isDev = System.getenv("STYX_DEV")?.isNotBlank() == true
    buildConfigField("APP_NAME", project.name.replace("Downloader", "DL"))
    buildConfigField("APP_VERSION", provider { "${project.version}".let { if (isDev) "$it-dev" else it } })
    buildConfigField("IS_DEV", isDev)
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
