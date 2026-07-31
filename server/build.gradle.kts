plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.spring") version "2.2.21"
    id("org.springframework.boot") version "4.0.6"
    id("io.spring.dependency-management") version "1.1.7"
}

version = "0.0.1-SNAPSHOT"
description = "Server"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("tools.jackson.module:jackson-module-kotlin")
    // Library for determination of crowd density
    implementation("crowdDensityLibrary:lib:0.1")
    // Sources:
    // https://spring.io/projects/spring-amqp#overview
    // https://docs.spring.io/spring-boot/reference/messaging/amqp.html#messaging.amqp
    // https://www.svix.com/guides/rabbitmq/rabbitmq-springboot-tutorial/
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    // Enables the use of coroutines
    // Source: https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}