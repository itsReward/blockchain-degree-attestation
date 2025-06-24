pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "api-gateway"

// This tells Gradle where to find the parent project
includeBuild("../..") {
    dependencySubstitution {
        substitute(module("org.degreechain:common")).using(project(":shared:common"))
    }
}