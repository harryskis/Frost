package com.frost.shitters

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** One player on the M5 Shitters list, and why they're on it. */
data class ShitterEntry(val name: String, val reason: String)

/** One player as the database lists them: by [uuid] (without dashes, lowercase), by [name], or
 * both. A player listed only by UUID has their name looked up - see [UuidNames]. */
data class ShitterRecord(val name: String?, val uuid: String?, val reason: String)

/**
 * Reads a shitters list from whatever a database hands back, as long as it's one of the usual
 * shapes. Each player can be given by username or by UUID, with or without dashes:
 * - a JSON list of players, each `{"uuid": ..., "reason": ...}` (or "name"/"ign"/"username",
 *   and "note"/"description"), `["uuid or name", "reason"]`, or just `"uuid or name"`
 * - a JSON object of `"uuid or name": "reason"`, or one holding such a list under a key like
 *   "players"
 * - plain text or CSV, one player a line: `name, reason`, `name: reason` or `name - reason`
 *
 * Anything that isn't a real username or UUID is skipped, as is a CSV's header line. A reason
 * given as a list has its parts joined.
 */
object ShitterDatabase {
    private val NAME_KEYS = listOf("name", "ign", "username", "player", "user", "mcname", "minecraft")
    private val UUID_KEYS = listOf("uuid", "id", "player_uuid", "playeruuid", "mcuuid", "minecraft_uuid", "uuid_dashed")
    private val REASON_KEYS = listOf("reason", "reasons", "note", "notes", "description", "why", "comment", "comments")
    private val LIST_KEYS = listOf("players", "shitters", "list", "data", "users", "entries", "results", "blacklist")
    private val LINE_SEPARATORS = listOf(",", "\t", " - ", ":", ";", "|")
    private val UUID = Regex("^[0-9a-fA-F]{8}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{12}$")

    /** [text] as a UUID without dashes, lowercase, or null if it isn't one. */
    fun normalizeUuid(text: String): String? = text.trim().takeIf(UUID::matches)?.replace("-", "")?.lowercase()

    fun parse(text: String): List<ShitterRecord> {
        val trimmed = text.trim()
        if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            val json = runCatching { JsonParser.parseString(trimmed) }.getOrNull()
            if (json != null) return distinct(fromJson(json))
        }
        return distinct(fromLines(trimmed))
    }

    private fun distinct(records: List<ShitterRecord>) = records.distinctBy { it.uuid ?: it.name?.lowercase() }

    private fun fromJson(json: JsonElement): List<ShitterRecord> = when {
        json.isJsonArray -> fromArray(json.asJsonArray)
        json.isJsonObject -> fromObject(json.asJsonObject)
        else -> emptyList()
    }

    private fun fromArray(array: JsonArray): List<ShitterRecord> = array.mapNotNull { element ->
        when {
            element.isJsonObject -> fromPlayer(element.asJsonObject)
            element.isJsonArray -> element.asJsonArray.let { pair ->
                pair.firstOrNull()?.let(::stringOf)?.let { record(it, pair.drop(1).firstOrNull()?.let(::reasonOf)) }
            }
            else -> stringOf(element)?.let { record(it, null) }
        }
    }

    private fun fromObject(json: JsonObject): List<ShitterRecord> {
        LIST_KEYS.firstNotNullOfOrNull { key -> json.get(key)?.takeIf { it.isJsonArray || it.isJsonObject } }?.let { return fromJson(it) }
        fromPlayer(json)?.let { return listOf(it) }
        return json.entrySet().mapNotNull { (key, value) ->
            val reason = when {
                value.isJsonObject -> reasonIn(value.asJsonObject)
                else -> reasonOf(value)
            }
            // Keyed by UUID, the player's name can be inside too.
            val name = if (value.isJsonObject) NAME_KEYS.firstNotNullOfOrNull { value.asJsonObject.get(it)?.let(::stringOf) } else null
            record(key, reason, name)
        }
    }

    private fun fromPlayer(json: JsonObject): ShitterRecord? {
        val name = NAME_KEYS.firstNotNullOfOrNull { key -> json.get(key)?.let(::stringOf) }
        val uuid = UUID_KEYS.firstNotNullOfOrNull { key -> json.get(key)?.let(::stringOf)?.let(::normalizeUuid) }
        val reason = reasonIn(json)
        return when {
            uuid != null -> record(uuid, reason, name)
            name != null -> record(name, reason)
            else -> null
        }
    }

    private fun reasonIn(json: JsonObject): String? = REASON_KEYS.firstNotNullOfOrNull { key -> json.get(key)?.let(::reasonOf) }

    /** A reason, or several in a list joined into one. */
    private fun reasonOf(element: JsonElement): String? = when {
        element.isJsonArray -> element.asJsonArray.mapNotNull(::stringOf).joinToString("; ").takeIf { it.isNotEmpty() }
        else -> stringOf(element)
    }

    private fun stringOf(element: JsonElement): String? =
        element.takeIf { it.isJsonPrimitive }?.asString?.trim()?.takeIf { it.isNotEmpty() }

    private fun fromLines(text: String): List<ShitterRecord> {
        val lines = text.lineSequence().map { it.trim().removePrefix(BYTE_ORDER_MARK) }
            .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("//") }
            .toList()
        fromTable(lines)?.let { return it }
        return lines.mapNotNull { line ->
            val separator = LINE_SEPARATORS.filter { it in line }.minByOrNull { line.indexOf(it) }
            val name = (if (separator == null) line else line.substringBefore(separator)).trim().trim('"')
            val reason = separator?.let { line.substringAfter(it).trim().trim('"') }
            record(name, reason)
        }
    }

    /** A spreadsheet's CSV or TSV, read by its header row - which can have more columns than a
     * name and a reason, like a date or who added them. Null if there's no such header. */
    private fun fromTable(lines: List<String>): List<ShitterRecord>? {
        val header = lines.firstOrNull() ?: return null
        val separator = if ('\t' in header) '\t' else ','
        val columns = splitRow(header, separator).map { it.lowercase() }
        val nameColumn = columns.indexOfFirst { it in NAME_KEYS }
        val uuidColumn = columns.indexOfFirst { it in UUID_KEYS }
        if (nameColumn < 0 && uuidColumn < 0) return null
        val reasonColumn = columns.indexOfFirst { it in REASON_KEYS }
        return lines.drop(1).mapNotNull { line ->
            val cells = splitRow(line, separator)
            val name = cells.getOrNull(nameColumn)?.takeIf { it.isNotEmpty() }
            val uuid = cells.getOrNull(uuidColumn)?.let(::normalizeUuid)
            when {
                uuid != null -> record(uuid, cells.getOrNull(reasonColumn), name)
                name != null -> record(name, cells.getOrNull(reasonColumn))
                else -> null
            }
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

    /** A player from [id] - a UUID or a username - with [name] alongside a UUID if it's known. */
    private fun record(id: String, reason: String?, name: String? = null): ShitterRecord? {
        val cleanReason = reason?.takeIf { it.isNotBlank() } ?: NO_REASON
        normalizeUuid(id)?.let { uuid -> return ShitterRecord(name?.takeIf(ShitterText::isUsername), uuid, cleanReason) }
        if (!ShitterText.isUsername(id) || id.lowercase() in NAME_KEYS || id.lowercase() in UUID_KEYS) return null
        return ShitterRecord(id, null, cleanReason)
    }

    const val NO_REASON = "No reason given."
}
