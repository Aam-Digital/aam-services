plugins {
    kotlin("jvm") version "2.0.21"
}

group = "com.aam-digital"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.keycloak:keycloak-core:26.1.0")
    compileOnly("org.keycloak:keycloak-server-spi:26.1.0")
    compileOnly("org.keycloak:keycloak-server-spi-private:26.1.0")
    compileOnly("org.keycloak:keycloak-services:26.1.0")
    compileOnly("org.projectlombok:lombok:1.18.36")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}