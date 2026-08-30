plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    // Source: https://github.com/kotlin/kotlinx.serialization
    kotlin("plugin.serialization") version "2.3.20"
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "org.leopoldBlk"
version = "0.0.1-SNAPSHOT"
description = "simulatedMobileSensingClients"

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
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    // Sources:
    // https://spring.io/projects/spring-amqp#overview
    // https://docs.spring.io/spring-boot/reference/messaging/amqp.html#messaging.amqp
    // https://www.svix.com/guides/rabbitmq/rabbitmq-springboot-tutorial/
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    // Library for determination of crowd density
    implementation("crowdDensityLibrary:lib:0.1")
    // Uber H3 library for Java
    // Source: https://github.com/uber/h3-java
    implementation("com.uber:h3:4.4.0")
    // Enables the use of coroutines
    // Source: https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    // Source: https://github.com/kotlin/kotlinx.serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
