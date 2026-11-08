package com.anibiar.app.data

import android.content.Context
import kotlinx.serialization.json.*

/** One file with everything that lives only on this phone: history, watchlist, preferences, reminders. Never includes login or PIN. */
object Backup {
    private val files = listOf("watch_history", "watchlist", "watch_prefs", "caption_prefs", "reminders")

    fun export(c: Context): String {
        val root = buildJsonObject {
            put("app", "kiro"); put("v", 1)
            put("prefs", buildJsonObject {
                for (name in files) put(name, buildJsonObject {
                    for ((k, v) in c.getSharedPreferences(name, Context.MODE_PRIVATE).all) {
                        put(k, when (v) {
                            is Boolean -> buildJsonObject { put("t", "b"); put("v", v) }
                            is Int -> buildJsonObject { put("t", "i"); put("v", v) }
                            is Long -> buildJsonObject { put("t", "l"); put("v", v) }
                            is Float -> buildJsonObject { put("t", "f"); put("v", v) }
                            is Set<*> -> buildJsonObject { put("t", "set"); put("v", buildJsonArray { v.forEach { add(it.toString()) } }) }
                            else -> buildJsonObject { put("t", "s"); put("v", v.toString()) }
                        })
                    }
                })
            })
        }
        return root.toString()
    }

    /** Replaces local data with the backup. Returns how many items were restored, or throws if the file isn't a Kiro backup. */
    fun restore(c: Context, text: String): Int {
        val root = Json.parseToJsonElement(text).jsonObject
        require(root["app"]?.jsonPrimitive?.content == "kiro") { "This isn't a Kiro backup file." }
        var n = 0
        for ((name, obj) in root["prefs"]!!.jsonObject) {
            if (name !in files) continue
            val e = c.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
            for ((k, el) in obj.jsonObject) {
                val o = el.jsonObject; val v = o["v"]!!
                when (o["t"]!!.jsonPrimitive.content) {
                    "b" -> e.putBoolean(k, v.jsonPrimitive.boolean)
                    "i" -> e.putInt(k, v.jsonPrimitive.int)
                    "l" -> e.putLong(k, v.jsonPrimitive.long)
                    "f" -> e.putFloat(k, v.jsonPrimitive.float)
                    "set" -> e.putStringSet(k, v.jsonArray.map { it.jsonPrimitive.content }.toSet())
                    else -> e.putString(k, v.jsonPrimitive.content)
                }
                n++
            }
            e.commit()
        }
        return n
    }
}
