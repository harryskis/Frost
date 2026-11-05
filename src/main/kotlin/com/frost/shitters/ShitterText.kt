package com.frost.shitters

/**
 * Finding usernames in Hypixel's text, which often carries its colors as legacy "§" codes inside
 * the text itself (see [com.frost.util.TextUtils]). Names are matched as whole words, ignoring
 * case like Minecraft does, and their positions are given in the raw text, codes and all, so the
 * text can be split around them.
 */
object ShitterText {
    private const val SECTION = '§'
    private const val MAX_NAME_LENGTH = 16
    private const val COLOR_CODES = "0123456789abcdef"
    private const val FORMAT_CODES = "klmno"
    private const val RESET = 'r'

    private val USERNAME = Regex("^[A-Za-z0-9_]{1,16}$")

    /** A valid Minecraft username - 1 to 16 letters, digits or underscores. */
    fun isUsername(text: String): Boolean = USERNAME.matches(text)

    /** One name found in raw text: it covers [start] until [end], and reads [name]. */
    data class Span(val start: Int, val end: Int, val name: String)

    private fun isNameChar(char: Char) = char.isLetterOrDigit() && char.code < 128 || char == '_'

    /** Every whole-word name in [raw] that [isListed], in order. */
    fun findNames(raw: String, isListed: (String) -> Boolean): List<Span> {
        // The visible characters, and where each one is in the raw text.
        val visible = StringBuilder()
        val rawIndex = ArrayList<Int>()
        var index = 0
        while (index < raw.length) {
            if (raw[index] == SECTION && index + 1 < raw.length) {
                index += 2
                continue
            }
            visible.append(raw[index])
            rawIndex += index
            index++
        }
        val result = mutableListOf<Span>()
        var wordStart = -1
        for (position in 0..visible.length) {
            val inWord = position < visible.length && isNameChar(visible[position])
            if (inWord && wordStart < 0) wordStart = position
            if (inWord || wordStart < 0) continue
            val word = visible.substring(wordStart, position)
            if (word.length <= MAX_NAME_LENGTH && isListed(word)) result += Span(rawIndex[wordStart], rawIndex[position - 1] + 1, word)
            wordStart = -1
        }
        return result
    }

    /** The color and formatting codes in effect at [upTo] in [raw], as codes that bring them back
     * at the start of new text. A color code clears formatting, as it does in the game. */
    fun activeCodes(raw: String, upTo: Int): String {
        var color: Char? = null
        val formats = LinkedHashSet<Char>()
        var index = 0
        while (index < upTo - 1) {
            if (raw[index] == SECTION) {
                val code = raw[index + 1].lowercaseChar()
                when {
                    code in COLOR_CODES -> {
                        color = code
                        formats.clear()
                    }
                    code in FORMAT_CODES -> formats += code
                    code == RESET -> {
                        color = null
                        formats.clear()
                    }
                }
                index += 2
            } else {
                index++
            }
        }
        return buildString {
            color?.let { append(SECTION).append(it) }
            formats.forEach { append(SECTION).append(it) }
        }
    }

    /** Just the formatting (bold, italic and so on) in effect at [upTo], without the color - so a
     * name can be recolored while keeping it bold. */
    fun activeFormats(raw: String, upTo: Int): String =
        activeCodes(raw, upTo).chunked(2).filter { it[1] in FORMAT_CODES }.joinToString("")
}
