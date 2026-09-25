package com.georgeappdev.atxfriends.data.repository

import com.georgeappdev.atxfriends.data.TestDocs
import com.georgeappdev.atxfriends.data.model.SimpaticoAnswer
import com.georgeappdev.atxfriends.data.model.SimpaticoImportance
import com.georgeappdev.atxfriends.data.model.SimpaticoState
import com.google.firebase.firestore.FieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Simpatico write must leave the doc exactly as iOS's
 * `setData(data, mergeFields: [userID, v2Answers.{questionID}])` would. Both sides are applied
 * to the same starting doc with Firestore's documented semantics and compared.
 */
class SimpaticoWritesTest {

    private val uid = "user-1"

    /** A doc with every kind of field iOS leaves alone, including the legacy `answers` variant. */
    private fun existingDoc(): Map<String, Any?> = mapOf(
        "userID" to uid,
        "answers" to mapOf("kindness" to mapOf("value" to 5, "importance" to "very"), "humor" to 3),
        "v2CompletedAt" to TestDocs.ts(TestDocs.T1),
        "v2Answers" to mapOf(
            "hangSize" to mapOf("answer" to "oneOnOne", "acceptable" to listOf("oneOnOne", "smallCrew"), "importance" to "somewhat"),
            "saturday" to mapOf("answer" to "chill", "acceptable" to listOf("chill"), "importance" to "very"),
        ),
    )

    @Test
    fun answerUpdate_listsExactlyTheIosFields_withIosNamesAndRawValues() {
        val answer = SimpaticoAnswer("explore", listOf("outdoors", "explore"), SimpaticoImportance.LITTLE)
        val fields = SimpaticoWrites.answer(uid, "saturday", answer).fields
        assertEquals(
            mapOf(
                "userID" to uid,
                "v2Answers.saturday.answer" to "explore",
                "v2Answers.saturday.acceptable" to listOf("outdoors", "explore"),
                "v2Answers.saturday.importance" to "little",
            ),
            fields,
        )
    }

    @Test
    fun answerWithImportance_matchesIosMerge_andLeavesEverythingElseUntouched() {
        val answer = SimpaticoAnswer("explore", listOf("outdoors", "explore"), SimpaticoImportance.VERY)
        val android = applyUpdate(existingDoc(), SimpaticoWrites.answer(uid, "saturday", answer).fields)
        val ios = iosSaveV2Answer(existingDoc(), uid, "saturday", answer)

        assertEquals(ios, android)
        assertEquals(existingDoc()["answers"], android["answers"])
        assertEquals(existingDoc()["v2CompletedAt"], android["v2CompletedAt"])
        assertEquals(v2(existingDoc())["hangSize"], v2(android)["hangSize"])
        assertEquals(
            mapOf("answer" to "explore", "acceptable" to listOf("outdoors", "explore"), "importance" to "very"),
            v2(android)["saturday"],
        )
    }

    @Test
    fun doesntMatterAnswer_dropsTheOldImportance_likeIosReplacingTheWholeMap() {
        val everything = listOf("outdoors", "explore", "chill", "goOut")
        val answer = SimpaticoAnswer("chill", everything, importance = null)
        val android = applyUpdate(existingDoc(), SimpaticoWrites.answer(uid, "saturday", answer).fields)

        assertEquals(iosSaveV2Answer(existingDoc(), uid, "saturday", answer), android)
        assertEquals(mapOf("answer" to "chill", "acceptable" to everything), v2(android)["saturday"])
    }

    @Test
    fun firstAnswerForAQuestion_addsItBesideTheOthers() {
        val answer = SimpaticoAnswer("dry", listOf("dry", "silly"), SimpaticoImportance.SOMEWHAT)
        val android = applyUpdate(existingDoc(), SimpaticoWrites.answer(uid, "humor", answer).fields)

        assertEquals(iosSaveV2Answer(existingDoc(), uid, "humor", answer), android)
        assertEquals(setOf("hangSize", "saturday", "humor"), v2(android).keys)
    }

    @Test
    fun docWithoutV2Answers_legacyOnly_getsTheAnswerAndKeepsLegacy() {
        val legacyOnly = mapOf("userID" to uid, "answers" to mapOf("kindness" to 4))
        val answer = SimpaticoAnswer("daily", listOf("daily"), SimpaticoImportance.LITTLE)
        val android = applyUpdate(legacyOnly, SimpaticoWrites.answer(uid, "texting", answer).fields)

        assertEquals(iosSaveV2Answer(legacyOnly, uid, "texting", answer), android)
        assertEquals(mapOf("kindness" to 4), android["answers"])
    }

    @Test
    fun savedDoc_decodesBackToTheSameAnswers_theScoringCodeReads() {
        val answer = SimpaticoAnswer("explore", listOf("outdoors", "explore"), SimpaticoImportance.VERY)
        val android = applyUpdate(existingDoc(), SimpaticoWrites.answer(uid, "saturday", answer).fields)
        val decoded = SimpaticoState.fromFirestore(uid, android)

        assertEquals(answer, decoded.answers["saturday"])
        assertEquals(2, decoded.answers.size)
        assertTrue("legacy data is still detected", decoded.hasLegacyAnswers)
    }

    @Test
    fun completed_writesOnlyV2CompletedAt_asATimestamp() {
        val fields = SimpaticoWrites.completed(TestDocs.T2).fields
        assertEquals(mapOf("v2CompletedAt" to TestDocs.ts(TestDocs.T2)), fields)
        val android = applyUpdate(existingDoc(), fields)
        assertEquals(existingDoc() + ("v2CompletedAt" to TestDocs.ts(TestDocs.T2)), android)
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesQuestionIDsThatWouldBreakTheFieldPath() {
        SimpaticoWrites.answer(uid, "v2.answers", SimpaticoAnswer("a", listOf("a"), null))
    }

    // region Firestore semantics

    private fun v2(doc: Map<String, Any?>) = doc["v2Answers"] as Map<*, *>

    /** `DocumentReference.update(map)`: each dotted key sets (or deletes) one nested field. */
    private fun applyUpdate(doc: Map<String, Any?>, fields: Map<String, Any?>): Map<String, Any?> {
        val root = deepCopy(doc)
        for ((path, value) in fields) {
            val keys = path.split('.')
            var node = root
            for (key in keys.dropLast(1)) {
                @Suppress("UNCHECKED_CAST")
                node = node.getOrPut(key) { linkedMapOf<String, Any?>() } as MutableMap<String, Any?>
            }
            if (value is FieldValue) node.remove(keys.last()) else node[keys.last()] = value
        }
        return root
    }

    /**
     * SimpaticoService.swift `saveV2Answer`: `setData(data, mergeFields:)` replaces the value at
     * each listed path with the value at that path in `data`, and touches nothing else.
     */
    private fun iosSaveV2Answer(doc: Map<String, Any?>, uid: String, questionID: String, answer: SimpaticoAnswer): Map<String, Any?> {
        val answerDict = linkedMapOf<String, Any?>("answer" to answer.answer, "acceptable" to answer.acceptable)
        answer.importance?.let { answerDict["importance"] = it.raw }
        val data = mapOf("userID" to uid, "v2Answers" to mapOf(questionID to answerDict))

        val root = deepCopy(doc)
        root["userID"] = data["userID"]
        @Suppress("UNCHECKED_CAST")
        val v2 = root.getOrPut("v2Answers") { linkedMapOf<String, Any?>() } as MutableMap<String, Any?>
        v2[questionID] = (data["v2Answers"] as Map<*, *>)[questionID]
        return root
    }

    private fun deepCopy(map: Map<String, Any?>): MutableMap<String, Any?> =
        map.mapValuesTo(linkedMapOf()) { (_, v) ->
            @Suppress("UNCHECKED_CAST")
            if (v is Map<*, *>) deepCopy(v as Map<String, Any?>) else v
        }

    // endregion
}
