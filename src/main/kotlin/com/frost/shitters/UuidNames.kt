package com.frost.shitters

import com.frost.Frost
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import net.fabricmc.loader.api.FabricLoader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * Current usernames for UUIDs, for a shitters list that only has UUIDs. Names are looked up from
 * Mojang a couple a second so as not to run into its rate limit, kept in
 * config/frost-uuid-names.json so they're there straight away next time, and looked up again
 * after a few days in case the player has changed name. Seeing the player in game updates their
 * name at once, with no lookup at all.
 */
object UuidNames {
    private const val STALE_MILLIS = 3 * 24 * 60 * 60 * 1000L
    private const val TICKS_BETWEEN_LOOKUPS = 10
    private const val MAX_IN_FLIGHT = 3
    private const val RATE_LIMIT_PAUSE_MILLIS = 60_000L
    private const val SAVE_DELAY_MILLIS = 10_000L
    private const val PROFILE_URL = "https://sessionserver.mojang.com/session/minecraft/profile/"

    /** A name, and when it was last confirmed. */
    data class Known(val name: String, val checkedAt: Long)

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val path = FabricLoader.getInstance().configDir.resolve("frost-uuid-names.json")
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

    private val known: ConcurrentHashMap<String, Known> by lazy { load() }
    private val queue = ConcurrentLinkedQueue<String>()
    private val queued = ConcurrentHashMap.newKeySet<String>()
    private val inFlight = AtomicInteger()
    @Volatile private var pausedUntil = 0L
    @Volatile private var dirtySince = 0L
    private var tickCounter = 0

    private val changes = AtomicInteger()

    /** Goes up whenever a name is learned or changes, so the list knows to rebuild. */
    val version: Int get() = changes.get()

    fun nameOf(uuid: String): String? = known[uuid]?.name

    /** How many of [uuids] have no name yet, known or being looked up. */
    fun unresolved(uuids: Collection<String>): Int = uuids.count { !known.containsKey(it) }

    /** Looks up every one of [uuids] with no name yet, or whose name is getting old. */
    fun request(uuids: Collection<String>) {
        val now = System.currentTimeMillis()
        for (uuid in uuids) {
            val entry = known[uuid]
            if (entry != null && now - entry.checkedAt < STALE_MILLIS) continue
            if (queued.add(uuid)) queue += uuid
        }
    }

    /** A name seen in game for [uuid], which is as current as it gets. */
    fun learn(uuid: String, name: String) {
        val previous = known.put(uuid, Known(name, System.currentTimeMillis()))
        if (previous?.name != name) changes.incrementAndGet()
        markDirty()
    }

    /** Called every client tick: starts the next lookup when it's due, and saves new names. */
    fun tick() {
        val now = System.currentTimeMillis()
        if (dirtySince != 0L && now - dirtySince > SAVE_DELAY_MILLIS) save()
        if (++tickCounter % TICKS_BETWEEN_LOOKUPS != 0 || now < pausedUntil || inFlight.get() >= MAX_IN_FLIGHT) return
        val uuid = queue.poll() ?: return
        lookUp(uuid)
    }

    private fun lookUp(uuid: String) {
        inFlight.incrementAndGet()
        val request = HttpRequest.newBuilder(URI.create(PROFILE_URL + uuid)).timeout(Duration.ofSeconds(15)).GET().build()
        http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete { response, error ->
            inFlight.decrementAndGet()
            val status = response?.statusCode()
            when {
                error != null || status == null -> retryLater(uuid)
                status == RATE_LIMITED -> {
                    pausedUntil = System.currentTimeMillis() + RATE_LIMIT_PAUSE_MILLIS
                    retryLater(uuid)
                }
                status in 200..299 && response.body().isNotBlank() -> {
                    val name = runCatching { JsonParser.parseString(response.body()).asJsonObject.get("name").asString }.getOrNull()
                    if (name != null) learn(uuid, name)
                    queued.remove(uuid)
                }
                // No such player (204/404): nothing to look up again until the list is reloaded.
                else -> queued.remove(uuid)
            }
        }
    }

    private fun retryLater(uuid: String) {
        queue += uuid
    }

    private fun markDirty() {
        if (dirtySince == 0L) dirtySince = System.currentTimeMillis()
    }

    private fun load(): ConcurrentHashMap<String, Known> = runCatching {
        if (!Files.exists(path)) return@runCatching ConcurrentHashMap<String, Known>()
        val type = object : TypeToken<Map<String, Known>>() {}.type
        val saved: Map<String, Known> = Files.newBufferedReader(path).use { gson.fromJson(it, type) } ?: emptyMap()
        ConcurrentHashMap(saved.filterValues { it.name.isNotEmpty() })
    }.getOrElse {
        Frost.logger.warn("Couldn't read $path, so names will be looked up again", it)
        ConcurrentHashMap()
    }

    private fun save() {
        dirtySince = 0L
        runCatching {
            Files.createDirectories(path.parent)
            Files.newBufferedWriter(path).use { gson.toJson(HashMap(known), it) }
        }.onFailure { Frost.logger.warn("Couldn't save $path", it) }
    }

    private const val RATE_LIMITED = 429
}
