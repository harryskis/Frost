package com.frost.shitters

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** One player on the M5 Shitters list, and why they're on it. */
data class ShitterEntry(val name: String, val reason: String)

/**
 * Reads a shitters list from whatever a database hands back, as long as it's one of the usual
 * shapes:
 * - a JSON list of players, each `{"name": ..., "reason": ...}` (or "ign"/"username" and
 *   "note"/"description"), `["name", "reason"]`, or just `"name"`
 * - a JSON object of `"name": "reason"`, or one holding such a list under a key like "players"
 * - plain text or CSV, one player a line: `name, reason`, `name: reason` or `name - reason`
 *
 * Anything that isn't a real username is skipped, as is a CSV's header line.
 */
object ShitterDatabase {
    private val NAME_KEYS = listOf("name", "ign", "username", "player", "user", "mcname", "minecraft")
    private val REASON_KEYS = listOf("reason", "note", "notes", "description", "why", "comment", "comments")
    private val LIST_KEYS = listOf("players", "shitters", "list", "data", "users", "entries", "results")
    private val LINE_SEPARATORS = listOf(",", "\t", " - ", ":", ";", "|")

    fun parse(text: String): List<ShitterEntry> {
        val trimmed = text.trim()
        if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            val json = runCatching { JsonParser.parseString(trimmed) }.getOrNull()
            if (json != null) return fromJson(json).distinctBy { it.name.lowercase() }
        }
        return fromLines(trimmed).distinctBy { it.name.lowercase() }
    }

    private fun fromJson(json: JsonElement): List<ShitterEntry> = when {
        json.isJsonArray -> fromArray(json.asJsonArray)
        json.isJsonObject -> fromObject(json.asJsonObject)
        else -> emptyList()
    }

    private fun fromArray(array: JsonArray): List<ShitterEntry> = array.mapNotNull { element ->
        when {
            element.isJsonObject -> fromPlayer(element.asJsonObject)
            element.isJsonArray -> element.asJsonArray.let { pair ->
                pair.firstOrNull()?.let(::stringOf)?.let { entry(it, pair.drop(1).firstOrNull()?.let(::stringOf)) }
            }
            else -> stringOf(element)?.let { entry(it, null) }
        }
    }

    private fun fromObject(json: JsonObject): List<ShitterEntry> {
        LIST_KEYS.firstNotNullOfOrNull { key -> json.get(key)?.takeIf { it.isJsonArray || it.isJsonObject } }?.let { return fromJson(it) }
        fromPlayer(json)?.let { return listOf(it) }
        return json.entrySet().mapNotNull { (name, value) ->
            val reason = when {
                value.isJsonObject -> REASON_KEYS.firstNotNullOfOrNull { key -> value.asJsonObject.get(key)?.let(::stringOf) }
                else -> stringOf(value)
            }
            entry(name, reason)
        }
    }

    private fun fromPlayer(json: JsonObject): ShitterEntry? {
        val name = NAME_KEYS.firstNotNullOfOrNull { key -> json.get(key)?.let(::stringOf) } ?: return null
        return entry(name, REASON_KEYS.firstNotNullOfOrNull { key -> json.get(key)?.let(::stringOf) })
    }

    private fun stringOf(element: JsonElement): String? =
        element.takeIf { it.isJsonPrimitive }?.asString?.trim()?.takeIf { it.isNotEmpty() }

    private fun fromLines(text: String): List<ShitterEntry> {
        val lines = text.lineSequence().map { it.trim().removePrefix(BYTE_ORDER_MARK) }
            .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("//") }
            .toList()
        fromTable(lines)?.let { return it }
        return lines.mapNotNull { line ->
            val separator = LINE_SEPARATORS.filter { it in line }.minByOrNull { line.indexOf(it) }
            val name = (if (separator == null) line else line.substringBefore(separator)).trim().trim('"')
            val reason = separator?.let { line.substringAfter(it).trim().trim('"') }
            entry(name, reason)
        }
    }

    /** A spreadsheet's CSV or TSV, read by its header row - which can have more columns than a
     * name and a reason, like a date or who added them. Null if there's no such header. */
    private fun fromTable(lines: List<String>): List<ShitterEntry>? {
        val header = lines.firstOrNull() ?: return null
        val separator = if ('\t' in header) '\t' else ','
        val columns = splitRow(header, separator).map { it.lowercase() }
        val nameColumn = columns.indexOfFirst { it in NAME_KEYS }.takeIf { it >= 0 } ?: return null
        val reasonColumn = columns.indexOfFirst { it in REASON_KEYS }
        return lines.drop(1).mapNotNull { line ->
            val cells = splitRow(line, separator)
            val name = cells.getOrNull(nameColumn) ?: return@mapNotNull null
            entry(name, cells.getOrNull(reasonColumn))
        }
    }

    /** One CSV row's cells: quoted cells can hold the separator, and "" inside quotes is a ". */
    private fun splitRow(line: String, separator: Char): List<String> {
        val cells = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && quoted && line.getOrNull(index + 1) == '"' -> {
                    cell.append('"')
                    index++
                }
                char == '"' -> quoted = !quoted
                char == separator && !quoted -> {
                    cells += cell.toString().trim()
                    cell.clear()
                }
                else -> cell.append(char)
            }
            index++
        }
        cells += cell.toString().trim()
        return cells
    }

    private const val BYTE_ORDER_MARK = "\uFEFF"
    private val GOOGLE_SHEET = Regex("^https?://docs\\.google\\.com/spreadsheets/d/([\\w-]+)")
    private val SHEET_TAB = Regex("[#&?]gid=(\\d+)")
    private val PASTEBIN = Regex("^https?://pastebin\\.com/(?!raw/)(\\w+)/?$")
    private val GITHUB_FILE = Regex("^https?://github\\.com/([^/]+)/([^/]+)/blob/(.+)$")

    /** Where to fetch [url]'s raw data from, for links to a page showing it rather than the data
     * itself: a Google Sheet as CSV (its open tab), and Pastebin or GitHub files as raw text. */
    fun directUrl(url: String): String {
        val trimmed = url.trim()
        GOOGLE_SHEET.find(trimmed)?.let { sheet ->
            if ("/export" in trimmed || "/pub" in trimmed || "output=csv" in trimmed) return trimmed
            val tab = SHEET_TAB.find(trimmed)?.groupValues?.get(1)?.let { "&gid=$it" }.orEmpty()
            return "https://docs.google.com/spreadsheets/d/${sheet.groupValues[1]}/export?format=csv$tab"
        }
        PASTEBIN.find(trimmed)?.let { return "https://pastebin.com/raw/${it.groupValues[1]}" }
        GITHUB_FILE.find(trimmed)?.let { match ->
            val (owner, repo, path) = match.destructured
            return "https://raw.githubusercontent.com/$owner/$repo/$path"
        }
        return trimmed
    }

    private fun entry(name: String, reason: String?): ShitterEntry? {
        if (!ShitterText.isUsername(name) || name.lowercase() in NAME_KEYS) return null
        return ShitterEntry(name, reason?.takeIf { it.isNotBlank() } ?: NO_REASON)
    }

    const val NO_REASON = "No reason given."
}
