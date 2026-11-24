import java.io.FileInputStream
import java.util.Properties

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.google.gms.google.services) apply false
    id("org.sonarqube") version "5.1.0.4882"
}

val sonarLocalProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        load(FileInputStream(file))
    }
}

sonar {
    properties {
        property("sonar.projectKey", "caprichometro")
        property("sonar.projectName", "Caprichometro")
        property("sonar.host.url", "http://localhost:9000")
        val token = sonarLocalProps.getProperty("SONAR_TOKEN")
            ?: System.getenv("SONAR_TOKEN")
            ?: ""
        if (token.isNotBlank()) {
            property("sonar.token", token)
        }
    }
}