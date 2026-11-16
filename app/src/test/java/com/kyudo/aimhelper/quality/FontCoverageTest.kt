package com.kyudo.aimhelper.quality

import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Font
import java.io.File

/**
 * The bundled heading font is cut down to the characters the app uses. If someone adds a new
 * kanji to the code without re-making the font, it would show as an empty box. This test
 * catches that: every Japanese character in the source must exist in the font.
 */
class FontCoverageTest {

    private val font: Font = File("src/main/res/font/shippori_mincho_b1_extrabold.ttf")
        .inputStream().use { Font.createFont(Font.TRUETYPE_FONT, it) }

    private val japanese = setOf(
        Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA,
    )

    @Test
    fun everyJapaneseCharacterInTheCode_isInTheFont() {
        val sources = File("src/main/java").walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue("no sources found", sources.isNotEmpty())
        val missing = sortedSetOf<String>()
        sources.forEach { f ->
            f.readText().codePoints().filter { Character.UnicodeScript.of(it) in japanese }.forEach { cp ->
                if (!font.canDisplay(cp)) missing += "${String(Character.toChars(cp))} (${f.name})"
            }
        }
        assertTrue("Missing from the bundled font, re-subset it: $missing", missing.isEmpty())
    }

    @Test
    fun tallyMarksAndHeadingText_areInTheFont() {
        val needed = "○×·Kyudo Aim Helper Shot log review Nerai & setup How to use the app 0123456789"
        val missing = needed.filterNot { font.canDisplay(it) }
        assertTrue("Missing: '$missing'", missing.isEmpty())
    }
}
