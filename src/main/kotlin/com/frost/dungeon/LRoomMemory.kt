package com.frost.dungeon

import com.frost.Frost
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files

/** Every L room's layout Frost has seen whole, by room name, kept in
 * config/frost-room-layouts.json - see [LRoomLayouts]. */
object LRoomMemory {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val path = FabricLoader.getInstance().configDir.resolve("frost-room-layouts.json")
    private val layouts: MutableMap<String, LRoomLayout> by lazy { load() }

    fun layoutOf(name: String): LRoomLayout? = layouts[name]

    fun remember(name: String, layout: LRoomLayout) {
        if (layouts[name] == layout) return
        layouts[name] = layout
        runCatching {
            Files.createDirectories(path.parent)
            Files.newBufferedWriter(path).use { gson.toJson(layouts, it) }
        }.onFailure { Frost.logger.warn("Couldn't save $path", it) }
    }

    private fun load(): MutableMap<String, LRoomLayout> = runCatching {
        if (!Files.exists(path)) return@runCatching HashMap<String, LRoomLayout>()
        val type = object : TypeToken<Map<String, LRoomLayout>>() {}.type
        HashMap<String, LRoomLayout>(Files.newBufferedReader(path).use { gson.fromJson<Map<String, LRoomLayout>>(it, type) } ?: emptyMap())
    }.getOrElse {
        Frost.logger.warn("Couldn't read $path", it)
        HashMap()
    }
}
