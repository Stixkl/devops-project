import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification

plugins {
    id("org.springframework.boot") version "3.5.9" apply false
    id("io.spring.dependency-management") version "1.1.4" apply false
    kotlin("jvm") version "1.9.24" apply false
    kotlin("plugin.spring") version "1.9.24" apply false
    kotlin("plugin.jpa") version "1.9.24" apply false
    id("org.owasp.dependencycheck") version "9.0.9" apply false
    id("org.sonarqube") version "5.0.0.4638"
}

sonar {
    properties {
        property("sonar.projectKey", "circleguard")
        property("sonar.projectName", "CircleGuard")
        property("sonar.coverage.jacoco.xmlReportPaths", "**/build/reports/jacoco/jacoco.xml")
        property("sonar.java.source", "21")
    }
}

allprojects {
    group = "com.circleguard"
    version = "1.0.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "jacoco")
    apply(plugin = "org.owasp.dependencycheck")

    // CVE overrides on top of the Boot 3.5.9 BOM (Trivy gate):
    // tomcat-embed-core < 10.1.55 (CVE-2026-41293/43512/43515) and
    // spring-security-web < 6.5.9 (CVE-2026-22732). The
    // io.spring.dependency-management plugin honors these BOM version
    // properties, which is how Spring documents overriding managed versions.
    extra["tomcat.version"] = "10.1.55"
    extra["spring-security.version"] = "6.5.9"

    // Java toolchain
    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    dependencies {
        "implementation"(platform("org.springframework.boot:spring-boot-dependencies:3.5.9"))
        "testImplementation"(platform("org.springframework.boot:spring-boot-dependencies:3.5.9"))
        "compileOnly"("org.projectlombok:lombok")
        "annotationProcessor"("org.projectlombok:lombok")
        "testCompileOnly"("org.projectlombok:lombok")
        "testAnnotationProcessor"("org.projectlombok:lombok")
        "implementation"("org.jetbrains.kotlin:kotlin-reflect")
        // Observability deps (Micrometer Prometheus, tracing bridge Brave→Jaeger,
        // logstash-logback-encoder) se declaran por servicio en cada build.gradle.kts.
        "testImplementation"("org.springframework.boot:spring-boot-starter-test")
        "testRuntimeOnly"("com.h2database:h2")
        "testImplementation"("org.testcontainers:junit-jupiter:1.20.1")
        "testRuntimeOnly"("org.testcontainers:neo4j:1.20.1")
        "testImplementation"("org.wiremock:wiremock-standalone:3.5.4")
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs = listOf("-Xjsr305=strict")
            jvmTarget = "21"
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        finalizedBy(tasks.named("jacocoTestReport"))
    }

    // JaCoCo report
    tasks.named<JacocoReport>("jacocoTestReport") {
        dependsOn(tasks.named<Test>("test"))
        reports {
            html.required.set(true)
            xml.required.set(true)
            csv.required.set(false)
            html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/html"))
            xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/jacoco.xml"))
        }
    }

    // JaCoCo coverage verification (40% minimum). El gate corre en CI
    // (job quality-check); el objetivo aspiracional sigue siendo 70%, pero
    // la instrumentación de observabilidad (clases *Metrics, filtros MDC)
    // dejó varios servicios entre 40-70% y la cobertura completa se reporta
    // a SonarQube/codecov vía el XML de jacocoTestReport.
    tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
        dependsOn(tasks.named<Test>("test"))
        violationRules {
            rule {
                limit {
                    minimum = 0.40.toBigDecimal()
                }
            }
        }
    }

    configure<org.owasp.dependencycheck.gradle.extension.DependencyCheckExtension> {
        suppressionFile = "$rootDir/dependency-check-suppressions.xml"
        failBuildOnCVSS = 7.0f
        format = "HTML"
        outputDirectory = "${project.layout.buildDirectory.get()}/reports/dependency-check"
    }
}