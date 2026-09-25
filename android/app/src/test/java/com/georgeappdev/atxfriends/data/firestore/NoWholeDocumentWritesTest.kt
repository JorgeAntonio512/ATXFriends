package com.georgeappdev.atxfriends.data.firestore

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Enforces the shared-database rule in CLAUDE.md: Android never replaces a whole document.
 * Fails if app code calls Firestore `set(...)` (with or without SetOptions) or maps documents
 * through reflection (`toObject`), which silently drops fields iOS uses. Existing documents
 * change only through DocumentUpdate → `update()`.
 */
class NoWholeDocumentWritesTest {

    private val forbidden = listOf(
        // Also catches Transaction.set / WriteBatch.set.
        Regex("""\.set\s*\(""") to "set() replaces the whole document — use DocumentUpdate",
        Regex("""\bSetOptions\b""") to "set(…, SetOptions) — use DocumentUpdate",
        Regex("""\.toObjects?\s*\(""") to "toObject() reflection mapping — decode with the model's fromFirestore()",
    )

    /**
     * The one reviewed exception (file path, exact code line): `WriteBatch.createDocument` in
     * NewDocument.kt, which only targets a fresh auto-ID or a deterministic ID just found missing.
     */
    private val allowed = setOf(
        "com/georgeappdev/atxfriends/data/firestore/NewDocument.kt" to
            "fun WriteBatch.createDocument(ref: DocumentReference, doc: NewDocument): WriteBatch = this.set(ref, doc.fields)",
    )

    @Test
    fun appCodeNeverOverwritesWholeDocuments() {
        val sourceRoot = File("src/main/java")
        assertTrue("Run from the app module: ${sourceRoot.absolutePath}", sourceRoot.isDirectory)

        val violations = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { i, line ->
                    // Ignore comments (KDoc and // lines) so docs can name the forbidden calls.
                    val trimmed = line.trimStart()
                    if (trimmed.startsWith("*") || trimmed.startsWith("/*")) return@mapIndexedNotNull null
                    val code = line.substringBefore("//")
                    if ((file.relativeTo(sourceRoot).invariantSeparatorsPath to line.trim()) in allowed) return@mapIndexedNotNull null
                    forbidden.firstOrNull { (regex, _) -> regex.containsMatchIn(code) }
                        ?.let { (_, why) -> "${file.relativeTo(sourceRoot)}:${i + 1}: $why\n    ${line.trim()}" }
                }
            }
            .toList()

        assertTrue("Whole-document writes are not allowed:\n" + violations.joinToString("\n"), violations.isEmpty())
    }
}
