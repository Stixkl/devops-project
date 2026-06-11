plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    kotlin("jvm")
    kotlin("plugin.spring") version "1.9.24"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.5.9"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-test")
    implementation("org.springframework.kafka:spring-kafka-test")
    implementation("org.testcontainers:junit-jupiter:1.19.7")
    implementation("org.testcontainers:postgresql:1.19.7")
    implementation("org.testcontainers:kafka:1.19.7")
    implementation("org.testcontainers:neo4j:1.19.7")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Test-only module: there is no application main class, so the Boot
// packaging tasks must not run.
tasks.named("bootJar") { enabled = false }
tasks.named("jar") { enabled = true }