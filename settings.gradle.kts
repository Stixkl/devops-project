pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "circleguard"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

enableFeaturePreview("STABLE_CONFIGURATION_CACHE")

// Shared Libraries (Commented out as folders are missing on disk)
// include("libs:circleguard-common")
// include("libs:circleguard-security")
// include("libs:circleguard-events")

// Microservices
include("services:circleguard-auth-service")
include("services:circleguard-identity-service")
include("services:circleguard-promotion-service")
include("services:circleguard-notification-service")
include("services:circleguard-form-service")
include("services:circleguard-file-service")
include("services:circleguard-gateway-service")
include("services:circleguard-dashboard-service")

// Centralized cross-service tests
include("tests:integration-tests")
