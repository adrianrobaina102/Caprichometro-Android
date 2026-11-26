package com.example.caprichometro.architecture

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

class HexagonalBoundariesTest {

    private fun resolveAppPackageRoot(): File {
        val candidates = listOf(
            File("src/main/java/com/example/caprichometro"),
            File("app/src/main/java/com/example/caprichometro")
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("Could not locate main Kotlin package root from ${File(".").absolutePath}")
    }

    @Test
    fun `domain layer does not depend on framework, Firebase or infrastructure`() {
        val root = resolveAppPackageRoot()
        val domainDir = File(root, "domain")
        val knownContextException = setOf(
            "repository${File.separator}AuthRepository.kt"
        )

        val violations = mutableListOf<String>()
        domainDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val rel = file.relativeTo(domainDir).path
                file.readLines().forEachIndexed { index, raw ->
                    val line = raw.trim()
                    if (!line.startsWith("import ")) return@forEachIndexed
                    val isForbidden =
                        line.startsWith("import androidx.") ||
                            line.startsWith("import com.google.firebase.") ||
                            line.startsWith("import com.example.caprichometro.application.") ||
                            line.startsWith("import com.example.caprichometro.infrastructure.") ||
                            (line.startsWith("import android.") && rel !in knownContextException)
                    if (isForbidden) {
                        violations += "$rel:${index + 1} -> $line"
                    }
                }
            }

        assertThat(violations).isEmpty()
    }

    @Test
    fun `application layer does not depend on infrastructure`() {
        val root = resolveAppPackageRoot()
        val applicationDir = File(root, "application")

        val violations = mutableListOf<String>()
        applicationDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val rel = file.relativeTo(applicationDir).path
                file.readLines().forEachIndexed { index, raw ->
                    val line = raw.trim()
                    if (line.startsWith("import com.example.caprichometro.infrastructure.")) {
                        violations += "$rel:${index + 1} -> $line"
                    }
                }
            }

        assertThat(violations).isEmpty()
    }

    @Test
    fun `domain ports live under domain repository package`() {
        val root = resolveAppPackageRoot()
        val domainDir = File(root, "domain")
        val strayPortFiles = domainDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name.endsWith("Repository.kt") }
            .map { it.relativeTo(domainDir).path }
            .filterNot { it.startsWith("repository${File.separator}") }
            .toList()

        assertThat(strayPortFiles).isEmpty()
    }

    @Test
    fun `application use cases are grouped in feature subpackages`() {
        val root = resolveAppPackageRoot()
        val useCaseDir = File(root, "application/usecase")
        val looseFiles = useCaseDir.listFiles()
            ?.filter { it.isFile && it.extension == "kt" }
            ?.map { it.name }
            .orEmpty()

        assertThat(looseFiles).isEmpty()
    }
}
