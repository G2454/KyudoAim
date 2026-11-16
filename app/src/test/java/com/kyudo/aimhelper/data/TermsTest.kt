package com.kyudo.aimhelper.data

import com.kyudo.aimhelper.pose.HASSETSU_JP
import com.kyudo.aimhelper.pose.Stage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TermsTest {

    private fun hasCjk(s: String) = s.codePoints().anyMatch {
        Character.UnicodeScript.of(it) in setOf(
            Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA,
        )
    }

    @Test
    fun eightStages_matchTheKanjiShownOnTheStepBar() {
        assertEquals(8, Terms.HASSETSU.size)
        assertEquals(HASSETSU_JP, Terms.HASSETSU.map { it.kanji })
    }

    @Test
    fun trackerStages_coverAllEightStagesOnceInOrder() {
        val covered = Stage.entries.flatMap { Terms.termsFor(it) }
        assertEquals(Terms.HASSETSU, covered)
    }

    @Test
    fun reviewSteps_followTheTrackerStages() {
        assertEquals(Stage.entries.toList(), Terms.STEPS.map { it.stage })
        Terms.STEPS.forEach { assertTrue(it.explanation.length > 40) }
    }

    @Test
    fun romaji_isPlainAsciiWithoutKanji() {
        // Plain ASCII: the bundled heading font has no macron letters (ā, ō, ū).
        (Terms.HASSETSU + Terms.KYUDO + Terms.NERAI).forEach { t ->
            assertTrue("${t.romaji} should be plain ASCII", t.romaji.all { it.code in 32..126 })
            assertFalse(hasCjk(Terms.name(t, showKanji = false)))
            assertTrue(hasCjk(Terms.name(t, showKanji = true)))
            assertTrue(t.english.isNotBlank())
        }
    }

    @Test
    fun stepNames_followTheSetting() {
        val kai = Terms.STEPS.first { it.stage == Stage.KAI }
        assertEquals("Kai", Terms.stepName(kai, showKanji = false))
        assertEquals("会 Kai", Terms.stepName(kai, showKanji = true))
        assertEquals("full draw", Terms.stepEnglish(kai))
        val prepare = Terms.STEPS.first()
        assertEquals("Ashibumi · Dozukuri · Yugamae", Terms.stepName(prepare, showKanji = false))
    }

    @Test
    fun neraiTitles_haveNoKanjiOfTheirOwn() {
        NeraiRef.entries.forEach { assertFalse("${it.title} must be romaji", hasCjk(it.title + it.label + it.desc)) }
    }
}
