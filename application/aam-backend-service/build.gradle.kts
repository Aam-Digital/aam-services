plugins {
    application
    distribution
    jacoco
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.sentry.jvm)
    alias(libs.plugins.ktlint)
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
    configureEach {
        // Jackson 2 only, until the code moves to Jackson 3. Spring Boot 4's web and restclient
        // starters bring Jackson 3 and its auto-configuration; with it on the classpath, Spring
        // Framework's default converters (RestClient.builder(), MockMvc standaloneSetup) switch
        // to Jackson 3 and can no longer read or write the Jackson 2 JsonNode types this code
        // passes through them. Without it, everything falls back to Jackson 2 as before.
        exclude(group = "tools.jackson.core")
        exclude(group = "org.springframework.boot", module = "spring-boot-jackson")
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
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    // RestClient.Builder and RestTemplateBuilder are auto-configured by their own module since
    // Spring Boot 4; nothing else pulls it in.
    implementation("org.springframework.boot:spring-boot-starter-restclient")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")

    implementation("org.apache.commons:commons-lang3")
    // Spring Boot's Jackson 2 auto-configuration, which ships deprecated for removal in 4.3; see
    // the Jackson 3 exclusion above.
    implementation("org.springframework.boot:spring-boot-jackson2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // The tracing auto-configuration moved out of actuator into this module in Spring Boot 4;
    // without it the tracing dependencies below are on the classpath but no tracer is set up.
    implementation("org.springframework.boot:spring-boot-micrometer-tracing-opentelemetry")
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")

    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor") // needed in some tests

    implementation(libs.keycloak.admin.client)

    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("com.github.ben-manes.caffeine:caffeine")

    implementation(libs.firebase.admin)

    runtimeOnly(libs.postgresql)

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation(libs.cucumber.java)
    testImplementation(libs.cucumber.junit)
    testImplementation(libs.cucumber.spring)
    testImplementation("org.junit.vintage:junit-vintage-engine")

    testImplementation(libs.mockito.kotlin)
    testImplementation("org.junit.jupiter:junit-jupiter-engine")
    testImplementation(libs.junit.json.params)
    testImplementation(libs.parsson)

    testImplementation("io.projectreactor:reactor-test")

    testImplementation(libs.okhttp.client)
    testImplementation(libs.okhttp.mockwebserver)

    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation(libs.testcontainers.keycloak)

    // Validates e2e request/response interactions against the OpenAPI specs in
    // docs/api-specs/ (contract testing). See e2e/contract/.
    testImplementation(libs.swagger.request.validator.core)

    constraints {
        testImplementation(libs.commons.compress) {
            because("previous versions have security issues")
        }
        testImplementation(libs.mime4j.core) {
            because("previous versions have security issues")
        }
    }

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation(kotlin("test"))
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
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
    // Set in the Docker build from a mounted secret; without it the upload is skipped.
    authToken = System.getenv("SENTRY_AUTH_TOKEN")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

ktlint {
    version.set(libs.versions.ktlint.get())
}

// Spring dependency management applies the Boot BOM, and with it kotlin-bom at our Kotlin
// version, to every configuration. That lifts ktlint's embedded Kotlin compiler to our
// compiler version, which ktlint cannot run on ("Extensions storage is not registered").
// Keep the ktlint configurations on the Kotlin versions ktlint itself asks for.
configurations.matching { it.name.startsWith("ktlint") }.configureEach {
    resolutionStrategy.eachDependency {
        val requestedVersion = requested.version
        if (requested.group == "org.jetbrains.kotlin" && !requestedVersion.isNullOrEmpty()) {
            useVersion(requestedVersion)
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    systemProperty("cucumber.junit-platform.naming-strategy", "long")
    // OpenAPI contract enforcement: which modules fail the build on spec drift.
    // Defaults to the reconciled modules; override on the command line, e.g.
    // -Dcontract.strict.modules= (empty) for report-only, or a custom list.
    // (Gradle does not pass command-line -D properties to the forked test JVM
    // automatically, hence the explicit forwarding.)
    systemProperty(
        "contract.strict.modules",
        System.getProperty("contract.strict.modules") ?: "reporting,export,notification"
    )
    testLogging {
        showStandardStreams = true
    }
}
