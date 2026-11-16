package com.kyudo.aimhelper.quality

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer

/**
 * The bundled heading font is cut down to the characters the app uses. If someone adds a new
 * kanji to the code without re-making the font, it would show as an empty box. This test
 * catches that: every Japanese character in the source must exist in the font.
 *
 * Android unit tests can't use java.awt, so the font's character map is read directly.
 */
class FontCoverageTest {

    private val fontChars: Set<Int> =
        cmapCodePoints(File("src/main/res/font/shippori_mincho_b1_extrabold.ttf").readBytes())

    private val japanese = setOf(
        Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA,
    )

    @Test
    fun theFontHasACharacterMap() {
        assertTrue("only ${fontChars.size} characters", fontChars.size > 100)
    }

    @Test
    fun everyJapaneseCharacterInTheCode_isInTheFont() {
        val sources = File("src/main/java").walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue("no sources found", sources.isNotEmpty())
        val missing = sortedSetOf<String>()
        sources.forEach { f ->
            f.readText().codePoints().filter { Character.UnicodeScript.of(it) in japanese }.forEach { cp ->
                if (cp !in fontChars) missing += "${String(Character.toChars(cp))} (${f.name})"
            }
        }
        assertTrue("Missing from the bundled font, re-subset it: $missing", missing.isEmpty())
    }

    @Test
    fun tallyMarksAndHeadingText_areInTheFont() {
        val needed = "○×·Kyudo Aim Helper Shot log review Nerai & setup How to use the app 0123456789"
        val missing = needed.filterNot { it.code in fontChars }
        assertTrue("Missing: '$missing'", missing.isEmpty())
    }

    companion object {
        /** Unicode code points mapped to a real glyph in a TrueType font (cmap formats 4 and 12). */
        fun cmapCodePoints(bytes: ByteArray): Set<Int> {
            val b = ByteBuffer.wrap(bytes) // big-endian, like TrueType
            fun u16(o: Int) = b.getShort(o).toInt() and 0xFFFF
            fun s16(o: Int) = b.getShort(o).toInt()

            var cmap = -1
            for (i in 0 until u16(4)) {
                val rec = 12 + i * 16
                if (String(bytes, rec, 4, Charsets.US_ASCII) == "cmap") cmap = b.getInt(rec + 8)
            }
            require(cmap >= 0) { "no cmap table" }

            val out = HashSet<Int>()
            for (i in 0 until u16(cmap + 2)) {
                val rec = cmap + 4 + i * 8
                val platform = u16(rec)
                val encoding = u16(rec + 2)
                val off = cmap + b.getInt(rec + 4)
                if (!(platform == 0 || (platform == 3 && (encoding == 1 || encoding == 10)))) continue
                when (u16(off)) {
                    4 -> {
                        val segX2 = u16(off + 6)
                        val ends = off + 14
                        val starts = ends + segX2 + 2
                        val deltas = starts + segX2
                        val ranges = deltas + segX2
                        for (s in 0 until segX2 / 2) {
                            val end = u16(ends + 2 * s)
                            val start = u16(starts + 2 * s)
                            val delta = s16(deltas + 2 * s)
                            val rangeOffset = u16(ranges + 2 * s)
                            for (c in start..end) {
                                if (c == 0xFFFF) continue
                                val glyph = if (rangeOffset == 0) {
                                    (c + delta) and 0xFFFF
                                } else {
                                    val g = u16(ranges + 2 * s + rangeOffset + 2 * (c - start))
                                    if (g == 0) 0 else (g + delta) and 0xFFFF
                                }
                                if (glyph != 0) out += c
                            }
                        }
                    }
                    12 -> {
                        val groups = b.getInt(off + 12)
                        for (g in 0 until groups) {
                            val r = off + 16 + g * 12
                            val start = b.getInt(r)
                            val end = b.getInt(r + 4)
                            val startGlyph = b.getInt(r + 8)
                            for (c in start..end) if (startGlyph + (c - start) != 0) out += c
                        }
                    }
                }
            }
            return out
        }
    }
}
