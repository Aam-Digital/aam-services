import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    distribution
    jacoco
    id("org.springframework.boot") version "3.2.3"
    id("io.spring.dependency-management") version "1.1.4"
    id("io.sentry.jvm.gradle") version "4.3.1"
    kotlin("kapt") version "1.9.22"
    kotlin("jvm") version "1.9.22"
    kotlin("plugin.spring") version "1.9.22"
}

group = "com.aam-digital"
version = "0.0.1-SNAPSHOT"

application {
    mainClass.set("com.aamdigital.aambackendservice.ApplicationKt")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    implementation("org.springframework.data:spring-data-r2dbc")

    implementation("org.springframework.security:spring-security-oauth2-resource-server")
    implementation("org.springframework.security:spring-security-oauth2-jose")

    implementation("org.apache.commons:commons-lang3")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")

    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

    implementation("io.r2dbc:r2dbc-h2")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("io.cucumber:cucumber-java:7.14.0")
    testImplementation("io.cucumber:cucumber-junit:7.14.0")
    testImplementation("io.cucumber:cucumber-spring:7.14.0")
    testImplementation("org.junit.vintage:junit-vintage-engine:5.10.2")

    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    testImplementation("net.joshka:junit-json-params:5.10.2-r0")
    testImplementation("org.eclipse.parsson:parsson:1.1.7")

    testImplementation("io.projectreactor:reactor-test")

    testImplementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")

    testImplementation("org.testcontainers:junit-jupiter:1.19.7") {
        constraints {
            testImplementation("org.apache.commons:commons-compress:1.26.1") {
                because("previous versions have security issues")
            }
        }
    }
    testImplementation("org.testcontainers:rabbitmq:1.19.7")
    testImplementation("com.github.dasniko:testcontainers-keycloak:3.4.0") {
        constraints {
            testImplementation("org.apache.james:apache-mime4j-core:0.8.11") {
                because("previous versions have security issues")
            }
        }
    }

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.security:spring-security-test")
}

jacoco {
    toolVersion = "0.8.11"
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

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict"
        jvmTarget = "17"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    systemProperty("cucumber.junit-platform.naming-strategy", "long")
}
