package com.georgeappdev.atxfriends

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit tests run on desktop Java, whose regex engine accepts things Android's (ICU) rejects at
 * runtime. A pattern in a property initializer then crashes the app the moment its class loads
 * — how Settings → Activities crashed on `Regex("(?U)\\s+")`, while every unit test passed.
 * This fails the build if app code uses a regex feature Android can't compile.
 */
class AndroidRegexCompatibilityTest {

    private val unsupported = listOf(
        "(?U)" to "the UNICODE_CHARACTER_CLASS flag — use Char.isWhitespace()/isLetter() instead",
        "UNICODE_CHARACTER_CLASS" to "not supported by Android's regex engine",
        "RegexOption.CANON_EQ" to "canonical equivalence isn't supported on Android",
        "Pattern.CANON_EQ" to "canonical equivalence isn't supported on Android",
    )

    @Test
    fun appCodeUsesOnlyRegexFeaturesAndroidSupports() {
        val sourceRoot = File("src/main/java")
        assertTrue("Run from the app module: ${sourceRoot.absolutePath}", sourceRoot.isDirectory)

        val violations = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { i, line ->
                    val trimmed = line.trimStart()
                    // KDoc and // comments may mention these.
                    if (trimmed.startsWith("*") || trimmed.startsWith("/*") || trimmed.startsWith("//")) return@mapIndexedNotNull null
                    unsupported.firstOrNull { (token, _) -> token in line }
                        ?.let { (token, why) -> "${file.relativeTo(sourceRoot)}:${i + 1}: $token — $why\n    ${line.trim()}" }
                }
            }
            .toList()

        assertTrue("Regex features Android can't compile:\n" + violations.joinToString("\n"), violations.isEmpty())
    }
}
