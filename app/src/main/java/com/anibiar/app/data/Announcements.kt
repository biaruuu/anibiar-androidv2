package com.anibiar.app.data

import android.content.Context
import com.anibiar.app.AniBiarApp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** A message from the owner. Published ones reach everyone; drafts are only returned to the owner account, so they can be previewed first. */
data class Announcement(val id: Long, val title: String, val body: String, val important: Boolean, val draft: Boolean)

object Announcements {
    private val json = Json { ignoreUnknownKeys = true }
    private const val PREFS = "announcements"

    suspend fun load(app: AniBiarApp): List<Announcement> {
        val (code, text) = app.social.rest("GET", "/rest/v1/announcements?select=*&order=created_at.desc&limit=5")
        if (code !in 200..299) return emptyList()
        return runCatching {
            (json.parseToJsonElement(text) as JsonArray).map { it.jsonObject }.mapNotNull { o ->
                Announcement(
                    id = o["id"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null,
                    title = o["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    body = o["body"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    important = o["important"]?.jsonPrimitive?.booleanOrNull ?: false,
                    draft = o["status"]?.jsonPrimitive?.contentOrNull != "published",
                )
            }
        }.getOrDefault(emptyList())
    }

    fun dismissed(context: Context): Set<Long> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet("gone", emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()

    fun dismiss(context: Context, id: Long) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.edit().putStringSet("gone", p.getStringSet("gone", emptySet()).orEmpty() + id.toString()).apply()
    }
}
