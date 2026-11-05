package com.frost.qol

import com.frost.config.ChatRegexRule
import com.frost.config.ModConfig
import com.frost.gui.TextAlerts
import com.frost.util.Sounds
import com.frost.util.TextUtils
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents

/**
 * Chat Regex: rules that react to chat lines. When a line matches a rule's message, the rule can
 * show text on screen - where, how big, how long and in what color it was set up with - and play
 * a sound. Action bar text doesn't count, only real chat lines.
 */
object ChatRegex {
    private const val MILLIS_PER_SECOND = 1000L
    private const val SOURCE_PREFIX = "chat-regex:"
    const val TEST_MESSAGE = "This is a test of Chat Regex."

    private data class Key(val pattern: String, val regex: Boolean, val matchCase: Boolean)

    // Compiling a regex for every rule on every chat line would be wasteful, so each is kept until
    // the rule's message changes.
    private val compiled = HashMap<Key, Regex?>()

    fun register() {
        ClientReceiveMessageEvents.GAME.register { message, overlay -> if (!overlay) onMessage(message.string) }
        ClientReceiveMessageEvents.CHAT.register { message, _, _, _, _ -> onMessage(message.string) }
    }

    private fun matcher(rule: ChatRegexRule): Regex? =
        compiled.getOrPut(Key(rule.pattern, rule.regex, rule.matchCase)) { ChatRegexMatcher.compile(rule.pattern, rule.regex, rule.matchCase) }

    /** Whether [rule]'s message is a regex that can't be used. */
    fun isBroken(rule: ChatRegexRule): Boolean = rule.regex && rule.pattern.isNotBlank() && matcher(rule) == null

    private fun onMessage(raw: String) {
        if (!ModConfig.isChatRegexEnabled()) return
        val message = TextUtils.stripFormatting(raw)
        for (rule in ModConfig.getChatRegexRules()) {
            if (!rule.enabled) continue
            val match = matcher(rule)?.find(message) ?: continue
            fire(rule, ChatRegexMatcher.displayText(rule.text, match, message))
        }
    }

    /** What [rule] does as if a message matched, for the menu's test button. */
    fun test(rule: ChatRegexRule) {
        val match = Regex(".+").find(TEST_MESSAGE) ?: return
        fire(rule, ChatRegexMatcher.displayText(rule.text, match, TEST_MESSAGE))
    }

    /** The text a rule shows, as near as it can be told without a message - for the move screen:
     * its Text, or the message it matches when that's blank. */
    fun sampleText(rule: ChatRegexRule): String =
        rule.text.takeIf { it.isNotBlank() }?.let(ChatRegexMatcher::colorCodes) ?: rule.pattern.ifBlank { TEST_MESSAGE }

    private fun fire(rule: ChatRegexRule, text: String) {
        if (rule.showText) {
            TextAlerts.show(SOURCE_PREFIX + rule.id, text, rule.color, rule.x, rule.y, rule.size, rule.seconds * MILLIS_PER_SECOND)
        }
        if (rule.playSound) Sounds.play(rule.sound, rule.volume, rule.pitch)
    }
}
