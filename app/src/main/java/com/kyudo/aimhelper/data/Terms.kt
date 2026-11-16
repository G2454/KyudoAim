package com.kyudo.aimhelper.data

import com.kyudo.aimhelper.pose.Stage

/**
 * A kyudo term. The app shows [romaji] (plus [english] where it helps); [kanji] is added only
 * when the "Show kanji" setting is on. Romaji is written without macrons (Kyudo, Dozukuri).
 */
data class Term(val romaji: String, val kanji: String, val english: String)

/** One step of the shot as Shot review shows it, with a short explanation. */
data class StepInfo(
    val stage: Stage,
    val terms: List<Term>,
    /** What should happen in this step, in plain words. */
    val explanation: String,
)

object Terms {
    val KYUDO = Term("Kyudo", "弓道", "Japanese archery")
    val NERAI = Term("Nerai", "狙い", "aim")

    /** The eight stages of the shot (hassetsu). */
    val HASSETSU = listOf(
        Term("Ashibumi", "足踏み", "footing"),
        Term("Dozukuri", "胴造り", "posture"),
        Term("Yugamae", "弓構え", "readying the bow"),
        Term("Uchiokoshi", "打起し", "raising the bow"),
        Term("Hikiwake", "引分け", "drawing"),
        Term("Kai", "会", "full draw"),
        Term("Hanare", "離れ", "release"),
        Term("Zanshin", "残心", "remaining form"),
    )

    /** The terms covered by a tracker stage, e.g. Prepare = Ashibumi, Dozukuri, Yugamae. */
    fun termsFor(stage: Stage): List<Term> = HASSETSU.subList(stage.first, stage.last + 1)

    /** The five steps of Shot review, in order. Their start times are [Shot.marks]. */
    val STEPS = listOf(
        StepInfo(
            Stage.PREPARE, termsFor(Stage.PREPARE),
            "Set your feet about an arrow-length apart, settle the hips and torso, then take " +
                "the bow ready with the arrow nocked and turn your gaze to the target.",
        ),
        StepInfo(
            Stage.UCHIOKOSHI, termsFor(Stage.UCHIOKOSHI),
            "Raise the bow and arrow evenly with both hands, keeping the arrow about level and " +
                "the shoulders low and relaxed.",
        ),
        StepInfo(
            Stage.HIKIWAKE, termsFor(Stage.HIKIWAKE),
            "Open the bow by pushing with the bow hand and pulling with the string hand at the " +
                "same time, bringing the arrow down evenly to mouth level.",
        ),
        StepInfo(
            Stage.KAI, termsFor(Stage.KAI),
            "Hold the full draw. Arrow at mouth level, shoulders and hips parallel to the arrow, " +
                "and keep stretching in both directions until the release comes.",
        ),
        StepInfo(
            Stage.ZANSHIN, termsFor(Stage.ZANSHIN),
            "The release should come from the stretch, not a pluck. Keep the finished form " +
                "and your eyes on the target for a moment after the arrow leaves.",
        ),
    )

    /** "Kai" or "会 Kai" depending on the setting. */
    fun name(t: Term, showKanji: Boolean): String = if (showKanji) "${t.kanji} ${t.romaji}" else t.romaji

    /** "Hanare · Zanshin" style label for a step. */
    fun stepName(step: StepInfo, showKanji: Boolean): String =
        step.terms.joinToString(" · ") { name(it, showKanji) }

    /** "release · remaining form" style gloss for a step. */
    fun stepEnglish(step: StepInfo): String = step.terms.joinToString(" · ") { it.english }
}
