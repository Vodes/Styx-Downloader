plugins {
    kotlin("jvm") version "1.9.21"
    kotlin("plugin.serialization") version "1.9.21"
    application
    `java-library`
    `maven-publish`
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

val ktorVersion = "2.3.8"

group = "moe.styx"
version = "0.1"

repositories {
    mavenCentral()
    mavenLocal()
    maven("https://jitpack.io")
}

dependencies {
    //implementation("moe.styx:styx-types:0.3")
    implementation("moe.styx:styx-db:0.5")
    implementation("com.github.Vodes:PircBot:873bc4aa78")
    implementation("net.peanuuutz.tomlkt:tomlkt:0.3.7")
    implementation("org.javacord:javacord:3.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")
    implementation("com.github.ajalt.mordant:mordant:2.2.0")
    implementation("de.androidpit:color-thief:1.1.2")
    // https://mvnrepository.com/artifact/com.google.guava/guava
    implementation("com.google.guava:guava:33.0.0-jre")
    // https://mvnrepository.com/artifact/com.apptasticsoftware/rssreader
    implementation("com.apptasticsoftware:rssreader:3.6.0")
    // https://mvnrepository.com/artifact/com.mysql/mysql-connector-j
    implementation("com.mysql:mysql-connector-j:8.2.0")
    // https://mvnrepository.com/artifact/org.apache.commons/commons-collections4
    implementation("org.apache.commons:commons-collections4:4.4")
    // https://mvnrepository.com/artifact/com.twelvemonkeys.imageio/imageio-core
    implementation("com.twelvemonkeys.imageio:imageio-core:3.10.1")
    // https://mvnrepository.com/artifact/com.twelvemonkeys.imageio/imageio-webp
    runtimeOnly("com.twelvemonkeys.imageio:imageio-webp:3.10.1")

    // https://mvnrepository.com/artifact/com.dgtlrepublic/anitomyJ
    // implementation("com.dgtlrepublic:anitomyJ:0.0.7")
    // Better fork, randomly found and just using the jitpack builds
    implementation("com.github.btmxh:anitomyJ:f6e9cea8f8")

    // https://mvnrepository.com/artifact/org.slf4j/slf4j-simple
    implementation("org.slf4j:slf4j-simple:2.0.9")
    // https://mvnrepository.com/artifact/commons-net/commons-net
    implementation("commons-net:commons-net:3.10.0")

    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-java:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-client-encoding:$ktorVersion")

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
            version = "0.1"

            from(components["java"])
        }
    }
}

configurations {

}