import java.io.FileInputStream
import java.util.Properties

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.google.gms.google.services) apply false
    id("org.sonarqube") version "7.5.0.8588"
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

project(":app") {
    sonar {
        properties {
            property(
                "sonar.coverage.jacoco.xmlReportPaths",
                "${layout.buildDirectory.get().asFile}/reports/coverage/test/debug/report.xml"
            )
            property(
                "sonar.coverage.exclusions",
                listOf(
                    "**/*Screen.kt",
                    "**/*Screen*.kt",
                    "**/*Sheet.kt",
                    "**/*Components.kt",
                    "**/*Tab.kt",
                    "**/*Card.kt",
                    "**/*Dialog.kt",
                    "**/*Banner.kt",
                    "**/*Row.kt",
                    "**/*Viewer.kt",
                    "**/*Button.kt",
                    "**/*Selector.kt",
                    "**/ui/theme/**",
                    "**/infrastructure/ui/components/**",
                    "**/infrastructure/ui/caprice/**",
                    "**/infrastructure/ui/friendship/*Section*.kt",
                    "**/infrastructure/ui/friendship/*Header*.kt",
                    "**/infrastructure/ui/friendship/*Empty*.kt",
                    "**/infrastructure/ui/profile/ProfileCategory.kt",
                    "**/MainActivity.kt",
                    "**/CaprichometroApplication.kt",
                    "**/infrastructure/factory/**",
                    "**/infrastructure/data/**",
                    "**/infrastructure/location/**",
                    "**/infrastructure/notification/**",
                    "**/infrastructure/auth/**",
                    "**/infrastructure/security/**"
                ).joinToString(",")
            )
        }
    }
}

tasks.named("sonar") {
    dependsOn(":app:createDebugUnitTestCoverageReport")
}