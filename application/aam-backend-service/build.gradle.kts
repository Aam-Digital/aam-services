plugins {
    application
    distribution
    jacoco
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.spring") version "2.1.20"
    kotlin("plugin.jpa") version "2.1.20"
    id("org.springframework.boot") version "3.5.12"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.jetbrains.kotlin.kapt") version "2.1.20"
    id("io.sentry.jvm.gradle") version "6.2.0"
    id("org.jlleitschuh.gradle.ktlint") version "12.3.0"
}

group = "com.aam-digital"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

application {
    mainClass.set("com.aamdigital.aambackendservice.ApplicationKt")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    implementation("org.apache.commons:commons-lang3")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")

    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor") // needed in some tests

    implementation("org.keycloak:keycloak-admin-client:23.0.7")

    implementation("com.google.firebase:firebase-admin:9.8.0")

    runtimeOnly("org.postgresql:postgresql:42.7.10")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("io.cucumber:cucumber-java:7.34.3")
    testImplementation("io.cucumber:cucumber-junit:7.34.3")
    testImplementation("io.cucumber:cucumber-spring:7.34.3")
    testImplementation("org.junit.vintage:junit-vintage-engine")

    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testImplementation("org.junit.jupiter:junit-jupiter-engine")
    testImplementation("net.joshka:junit-json-params:5.10.2-r0")
    testImplementation("org.eclipse.parsson:parsson:1.1.7")

    testImplementation("io.projectreactor:reactor-test")

    testImplementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")

    testImplementation("org.testcontainers:junit-jupiter:1.21.4")
    testImplementation("org.testcontainers:rabbitmq:1.21.4")
    testImplementation("com.github.dasniko:testcontainers-keycloak:3.9.1")

    constraints {
        testImplementation("org.apache.commons:commons-compress:1.28.0") {
            because("previous versions have security issues")
        }
        testImplementation("org.apache.james:apache-mime4j-core:0.8.13") {
            because("previous versions have security issues")
        }
    }

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation(kotlin("test"))
}

jacoco {
    toolVersion = "0.8.12"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        html.required.set(false)
        csv.required.set(true)
        xml.required.set(true)
    }
}

sentry {
    // Generates a JVM (Java, Kotlin, etc.) source bundle and uploads your source code to Sentry.
    // This enables source context, allowing you to see your source
    // code as part of your stack traces in Sentry.
    includeSourceContext = true

    org = "aam-digital"
    projectName = "aam-backend-service"
    authToken = System.getenv("SENTRY_AUTH_TOKEN")
    version = System.getenv("APPLICATION_VERSION")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

ktlint {
    version.set("1.5.0")
}

tasks.withType<Test> {
    useJUnitPlatform()
    systemProperty("cucumber.junit-platform.naming-strategy", "long")
}
