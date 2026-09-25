package com.georgeappdev.atxfriends.domain.simpatico

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SimpaticoQuestionsTest {

    /** Firestore stores these IDs; changing one orphans every saved answer. */
    @Test
    fun questionAndOptionIDs_areExactlyTheIosOnes_inFlowOrder() {
        val expected = linkedMapOf(
            "saturday" to listOf("outdoors", "explore", "chill", "goOut"),
            "hangSize" to listOf("oneOnOne", "smallCrew", "bigGroup"),
            "planning" to listOf("planner", "loose", "spontaneous"),
            "bestTime" to listOf("earlyMorning", "daytime", "evening", "lateNight"),
            "socialBattery" to listOf("energized", "quietDay", "wiped"),
            "convoDepth" to listOf("light", "mix", "deep"),
            "texting" to listOf("daily", "fewTimesWeek", "plansOnly"),
            "humor" to listOf("dry", "silly", "dark", "wholesome"),
            "drinking" to listOf("regularly", "socially", "rarely", "never"),
            "nightOutCost" to listOf("under20", "20to50", "over50", "whatever"),
            "punctuality" to listOf("early", "tenMin", "suggestion"),
            "schedule" to listOf("nineToFive", "shifts", "flexible", "parent"),
        )
        val actual = SimpaticoQuestions.all.associate { q -> q.id to q.options.map { it.id } }
        assertEquals(expected.toList(), actual.toList())
        // The scoring table (android-03) reads the same bank.
        assertEquals(expected.toList(), SimpaticoQuestionBank.optionIDs.toList())
    }

    @Test
    fun categories_areFourPerSection_inIosOrder() {
        val byCategory = SimpaticoQuestions.all.groupBy({ it.category }, { it.id })
        assertEquals(listOf("saturday", "hangSize", "planning", "bestTime"), byCategory[SimpaticoCategory.HANGING_OUT])
        assertEquals(listOf("socialBattery", "convoDepth", "texting", "humor"), byCategory[SimpaticoCategory.SOCIAL_STYLE])
        assertEquals(listOf("drinking", "nightOutCost", "punctuality", "schedule"), byCategory[SimpaticoCategory.LIFESTYLE])
        assertEquals(
            listOf("hangingOut" to "Hanging Out", "socialStyle" to "Social Style", "lifestyle" to "Lifestyle"),
            SimpaticoCategory.entries.map { it.raw to it.displayName },
        )
    }

    /** Catches a Swift edit that wasn't followed by re-running generate_simpatico_questions.py. */
    @Test
    fun generatedBank_matchesTheSwiftSource() {
        val swift = File("../../SimpaticoModels.swift")
        assertTrue("Missing ${swift.absolutePath}", swift.isFile)
        val source = swift.readText()

        val prompts = Regex("""prompt:\s*"((?:[^"\\]|\\.)*)"""").findAll(source).map { it.groupValues[1].replace("\\\"", "\"") }.toList()
        val options = Regex("""SimpaticoOption\(id:\s*"([^"]*)",\s*text:\s*"((?:[^"\\]|\\.)*)"\)""")
            .findAll(source).map { it.groupValues[1] to it.groupValues[2].replace("\\\"", "\"") }.toList()

        assertEquals(SimpaticoQuestions.all.map { it.prompt }, prompts)
        assertEquals(SimpaticoQuestions.all.flatMap { q -> q.options.map { it.id to it.text } }, options)
    }

    @Test
    fun lookupByID() {
        assertEquals("My humor is…", SimpaticoQuestions.question("humor")?.prompt)
        assertEquals(null, SimpaticoQuestions.question("nope"))
    }
}
