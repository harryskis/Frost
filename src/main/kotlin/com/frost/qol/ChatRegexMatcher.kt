package com.frost.qol

/**
 * How a Chat Regex rule matches chat. Plain text matches anywhere in a line; with Regex on, the
 * message is a regular expression, also matched anywhere unless it's anchored with ^ or $. Either
 * way case is ignored unless Match Case is on. Chat is matched without its color codes.
 */
object ChatRegexMatcher {
    private val GROUP = Regex("\\$(\\d)")
    private val COLOR_CODE = Regex("&([0-9a-fk-orA-FK-OR])")

    /** What [pattern] matches with, or null if there's nothing to match or the regex is broken. */
    fun compile(pattern: String, regex: Boolean, matchCase: Boolean): Regex? {
        if (pattern.isBlank()) return null
        val options = if (matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE)
        return if (regex) runCatching { Regex(pattern, options) }.getOrNull() else Regex(Regex.escape(pattern.trim()), options)
    }

    /**
     * The text to show for a match: [template] with $0 (the whole match) to $9 (a regex's groups)
     * filled in and & color codes turned into real ones - or the chat line itself if [template]
     * is blank.
     */
    fun displayText(template: String, match: MatchResult, message: String): String {
        if (template.isBlank()) return message
        val filled = GROUP.replace(template) { group -> match.groupValues.getOrNull(group.groupValues[1].toInt()).orEmpty() }
        return colorCodes(filled)
    }

    /** [text] with & color codes, like &c, turned into the game's own. */
    fun colorCodes(text: String): String = COLOR_CODE.replace(text) { "§" + it.groupValues[1].lowercase() }
}
