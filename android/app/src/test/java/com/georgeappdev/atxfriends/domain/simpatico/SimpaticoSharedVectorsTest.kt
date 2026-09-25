package com.georgeappdev.atxfriends.domain.simpatico

import com.georgeappdev.atxfriends.data.model.SimpaticoAnswer
import com.georgeappdev.atxfriends.data.model.SimpaticoState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Scores every case in the repo-root shared-test-vectors/simpatico.json — the same file the
 * iOS test (Avenue3Tests/SimpaticoSharedVectorsTests.swift) checks against the Swift code —
 * so the % badge is identical on both platforms.
 */
class SimpaticoSharedVectorsTest {

    private val file = File("../../shared-test-vectors/simpatico.json")

    @Test
    fun everySharedCaseScoresExactlyAsExpected() {
        assertTrue("Missing ${file.absolutePath}", file.isFile)
        val cases = Json.parseToJsonElement(file.readText()).jsonObject["cases"]!!.jsonArray
        assertTrue("Expected about 10 cases, found ${cases.size}", cases.size >= 10)

        val failures = cases.mapNotNull { element ->
            val case = element.jsonObject
            val name = case["name"]!!.jsonPrimitive.content
            val expected = case["expected"]!!.let { if (it is JsonNull) null else it.jsonPrimitive.int }
            val actual = SimpaticoScoring.score(state("a", case["a"]!!.jsonObject), state("b", case["b"]!!.jsonObject))
            if (actual == expected) null else "$name: expected $expected, got $actual"
        }
        assertEquals("Mismatches:\n" + failures.joinToString("\n"), emptyList<String>(), failures)
    }

    /** Builds answers through the same Firestore decoder the app uses. */
    private fun state(uid: String, answers: JsonObject): SimpaticoState {
        val v2Answers = answers.mapValues { (_, value) ->
            val o = value.jsonObject
            buildMap<String, Any?> {
                put("answer", o["answer"]!!.jsonPrimitive.content)
                put("acceptable", o["acceptable"]!!.jsonArray.map { it.jsonPrimitive.content })
                o["importance"]?.jsonPrimitive?.contentOrNull?.let { put("importance", it) }
            }
        }
        val decoded = SimpaticoState.fromFirestore(uid, mapOf("v2Answers" to v2Answers))
        assertEquals("every vector answer must decode", answers.size, decoded.answers.size)
        return decoded
    }

    @Test
    fun weightRules() {
        val q = "hangSize" // 3 options
        fun answer(acceptable: List<String>, importance: String?) =
            SimpaticoAnswer.fromFirestore(mapOf("answer" to acceptable.first(), "acceptable" to acceptable, "importance" to importance))!!
        assertEquals(50, SimpaticoScoring.weight(answer(listOf("oneOnOne"), "very"), 3))
        assertEquals(10, SimpaticoScoring.weight(answer(listOf("oneOnOne"), "somewhat"), 3))
        assertEquals(1, SimpaticoScoring.weight(answer(listOf("oneOnOne"), "little"), 3))
        assertEquals(0, SimpaticoScoring.weight(answer(listOf("oneOnOne", "smallCrew", "bigGroup"), "very"), 3))
        assertEquals(0, SimpaticoScoring.weight(answer(listOf("oneOnOne"), null), 3))
        assertEquals(3, SimpaticoQuestionBank.optionCount(q))
        assertEquals(12, SimpaticoQuestionBank.optionIDs.size)
    }
}
