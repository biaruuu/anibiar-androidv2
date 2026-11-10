package com.anibiar.app.social

import android.content.Context
import com.anibiar.app.data.Http
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** One favourited anime. */
data class Fav(val animeId: Int, val title: String, val cover: String?)

data class AuthState(val uid: String, val email: String, val verified: Boolean)

data class DmConversation(val otherId: String, val lastBody: String, val lastAt: Long, val lastMine: Boolean, val unread: Int, val streak: Int)
data class DmMessage(val id: Long, val mine: Boolean, val body: String, val at: Long, val read: Boolean, val replyTo: Long? = null, val kind: String = "text", val mediaUrl: String? = null, val style: String? = null,
    /** emoji to count, and the one I picked (one per person per message). */
    val reactions: Map<String, Int> = emptyMap(), val myReaction: String? = null)

data class UserProfile(
    val uid: String,
    val username: String,
    val bio: String = "",
    val createdAt: Long = 0L,
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
    val followers: Int? = null,
    val following: Int? = null,
    val linkSlug: String? = null,
    /** "mutuals" or "nobody". */
    val friendRequests: String = "mutuals",
    /** "everyone", "friends" or "me". */
    val listsVisibility: String = "everyone",
    val displayName: String? = null,
    /** "online", "idle", "dnd" or "invisible". */
    val status: String = "online",
    val statusText: String? = null,
    val statusEmoji: String? = null,
    /** "everyone", "friends" or "me". */
    val favoritesVisibility: String = "everyone",
    val avatarDeco: String? = null,
    val profileEffect: String? = null,
    val profileTheme: String? = null,
    val shareActivity: Boolean = true,
) {
    /** The name people see: the display name if set, otherwise the username. */
    val shownName: String get() = displayName?.takeIf { it.isNotBlank() } ?: username
}

/** One inbox entry. [type] is follow, friend_request or friend_accepted. */
data class Notice(val id: Long, val type: String, val actor: UserProfile?, val actorId: String, val read: Boolean, val createdAt: Long, val ref: String? = null)

/** A Watch Together room. */
data class Room(val id: String, val hostId: String, val animeId: Int, val title: String, val cover: String?, val episode: Int, val audio: String, val ended: Boolean)

enum class SignUpResult { SignedIn, ConfirmEmail }

private data class Session(val access: String, val refresh: String, val expiresAt: Long, val uid: String, val email: String, val verified: Boolean)

/**
 * Supabase over plain REST (GoTrue for accounts, PostgREST for data, Storage for images).
 * "Live" data is refreshed every few seconds while a screen is showing it, and immediately after any write.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SocialRepository(context: Context, private val scope: CoroutineScope) {
    private val http get() = Http.plain
    private val json = Json { ignoreUnknownKeys = true }
    private val prefs = context.getSharedPreferences("kiro_session", Context.MODE_PRIVATE)
    private val refreshLock = Mutex()
    private var session: Session? = null
    val realtime = Realtime(scope)
    val myId: String? get() = session?.uid

    private val _auth = MutableStateFlow<AuthState?>(null)
    val authState: StateFlow<AuthState?> = _auth.asStateFlow()
    private val _myFollowing = MutableStateFlow<Set<String>>(emptySet())
    val myFollowing: StateFlow<Set<String>> = _myFollowing.asStateFlow()
    private val _myFollowers = MutableStateFlow<Set<String>>(emptySet())
    val myFollowers: StateFlow<Set<String>> = _myFollowers.asStateFlow()
    private val _myFavs = MutableStateFlow<Set<Int>>(emptySet())
    /** Anime ids I have favourited, for the bookmark button. */
    val myFavorites: StateFlow<Set<Int>> = _myFavs.asStateFlow()

    private val _myFriends = MutableStateFlow<Set<String>>(emptySet())
    val myFriends: StateFlow<Set<String>> = _myFriends.asStateFlow()
    private val _incoming = MutableStateFlow<Set<String>>(emptySet())
    /** People who asked to be my friend and are waiting for my answer. */
    val incomingRequests: StateFlow<Set<String>> = _incoming.asStateFlow()
    private val _outgoing = MutableStateFlow<Set<String>>(emptySet())
    /** People I asked and who haven't answered. */
    val outgoingRequests: StateFlow<Set<String>> = _outgoing.asStateFlow()
    private val _unread = MutableStateFlow(0)
    val unread: StateFlow<Int> = _unread.asStateFlow()
    /** A profile opened from a kiro://u/<link> deep link; the UI consumes it. */
    private val _pendingUser = MutableStateFlow<String?>(null)
    val pendingUser: StateFlow<String?> = _pendingUser.asStateFlow()
    fun consumePendingUser() { _pendingUser.value = null }
    private val _dmUnread = MutableStateFlow(0)
    /** Unread direct messages across all chats, for the Inbox tab badge. */
    val dmUnread: StateFlow<Int> = _dmUnread.asStateFlow()
    private val _pinned = MutableStateFlow<Set<String>>(prefs.getStringSet("pinned_chats", emptySet())?.toSet() ?: emptySet())
    /** Chats I pinned to the top of the Inbox (kept on this device). */
    val pinnedChats: StateFlow<Set<String>> = _pinned.asStateFlow()
    fun togglePin(uid: String) {
        val next = if (uid in _pinned.value) _pinned.value - uid else _pinned.value + uid
        _pinned.value = next
        prefs.edit().putStringSet("pinned_chats", next).apply()
    }
    private val tick = MutableStateFlow(0)

    /** Bumped when a confirmation link signs the user in, so the UI can jump to the profile. */
    private val _linkLogins = MutableStateFlow(0)
    val linkLogins: StateFlow<Int> = _linkLogins.asStateFlow()

    private val REDIRECT = "kiro://auth"

    /**
     * Handles `kiro://auth#access_token=...&refresh_token=...` from the confirmation email.
     * Returns an error message to show, or null when the user is now signed in.
     */
    suspend fun handleAuthLink(uri: android.net.Uri): String? {
        val frag = uri.encodedFragment ?: uri.encodedQuery ?: return null
        val params = frag.split("&").mapNotNull { kv -> kv.split("=", limit = 2).takeIf { it.size == 2 }?.let { it[0] to android.net.Uri.decode(it[1]) } }.toMap()
        params["error_description"]?.let { return it.replace('+', ' ') }
        val access = params["access_token"] ?: return null
        val refresh = params["refresh_token"] ?: return "That link is incomplete. Try logging in instead."
        val expires = params["expires_at"]?.toLongOrNull() ?: (System.currentTimeMillis() / 1000 + (params["expires_in"]?.toLongOrNull() ?: 3600))
        return try {
            val (code, text) = withContext(Dispatchers.IO) {
                http.newCall(
                    Request.Builder().url("${SupabaseConfig.URL}/auth/v1/user").header("apikey", SupabaseConfig.PUBLISHABLE_KEY)
                        .header("Authorization", "Bearer $access").get().build(),
                ).execute().use { it.code to it.body?.string().orEmpty() }
            }
            if (code !in 200..299) return "That link has expired. Log in to request a new one."
            val u = json.parseToJsonElement(text).jsonObject
            store(Session(access, refresh, expires, u["id"]!!.jsonPrimitive.content, u["email"]?.jsonPrimitive?.contentOrNull.orEmpty(), true))
            _linkLogins.value++
            null
        } catch (e: CancellationException) { throw e } catch (e: Exception) { "Couldn't finish signing in. Try logging in." }
    }

    private class ApiError(val code: String, override val message: String) : Exception(message)

    fun start() {
        session = prefs.getString("session", null)?.let { runCatching { decodeSession(json.parseToJsonElement(it).jsonObject) }.getOrNull() }
        publish()
        scope.launch {
            if (session != null) refreshVerification()
            // Keep my own follow sets current for the Follow / Follow back buttons.
            tick.flatMapLatest { flow { while (true) { emit(Unit); delay(6000) } } }.collect {
                val me = session?.uid
                if (me == null) {
                    _myFollowing.value = emptySet(); _myFollowers.value = emptySet()
                    _myFriends.value = emptySet(); _incoming.value = emptySet(); _outgoing.value = emptySet(); _unread.value = 0; _myFavs.value = emptySet(); _dmUnread.value = 0
                } else {
                    runCatching {
                        _myFollowing.value = fetchIds(me, "following")
                        _myFollowers.value = fetchIds(me, "followers")
                    }
                    runCatching {
                        val rows = getRows("/rest/v1/friend_requests?or=(requester_id.eq.$me,addressee_id.eq.$me)&select=requester_id,addressee_id,status")
                        val friends = HashSet<String>(); val inc = HashSet<String>(); val out = HashSet<String>()
                        for (r in rows) {
                            val o = r.jsonObject
                            val a = o["requester_id"]!!.jsonPrimitive.content; val b = o["addressee_id"]!!.jsonPrimitive.content
                            val other = if (a == me) b else a
                            if (o["status"]!!.jsonPrimitive.content == "accepted") friends.add(other) else if (a == me) out.add(other) else inc.add(other)
                        }
                        _myFriends.value = friends; _incoming.value = inc; _outgoing.value = out
                    }
                    runCatching { _myFavs.value = getRows("/rest/v1/favorites?user_id=eq.$me&select=anime_id").map { it.jsonObject["anime_id"]!!.jsonPrimitive.content.toInt() }.toSet() }
                    runCatching { _unread.value = getRows("/rest/v1/notifications?user_id=eq.$me&read=eq.false&select=id").size }
                    runCatching { _dmUnread.value = getRows("/rest/v1/dm_messages?recipient_id=eq.$me&read_at=is.null&select=id").size }
                }
            }
        }
    }

    // ---------------------------------------------------------------- plumbing

    private fun encode(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun decodeSession(o: JsonObject): Session {
        val user = o["user"]?.jsonObject
        return Session(
            access = o["access_token"]!!.jsonPrimitive.content,
            refresh = o["refresh_token"]!!.jsonPrimitive.content,
            expiresAt = o["expires_at"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                ?: (System.currentTimeMillis() / 1000 + (o["expires_in"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 3600)),
            uid = (user?.get("id") ?: o["uid"])!!.jsonPrimitive.content,
            email = (user?.get("email") ?: o["email"])?.jsonPrimitive?.contentOrNull.orEmpty(),
            verified = user?.get("email_confirmed_at")?.let { it !is JsonNull } ?: (o["verified"]?.jsonPrimitive?.contentOrNull == "true"),
        )
    }

    private fun store(s: Session?) {
        if (session?.uid != s?.uid) runCatching { msgCache.clear(); convCache = null; noticeCache = null; outbox.value = emptyMap() }
        session = s
        prefs.edit().apply {
            if (s == null) remove("session") else putString(
                "session",
                buildJsonObject {
                    put("access_token", s.access); put("refresh_token", s.refresh); put("expires_at", s.expiresAt.toString())
                    put("uid", s.uid); put("email", s.email); put("verified", s.verified.toString())
                }.toString(),
            )
        }.apply()
        publish()
    }

    private fun publish() {
        _auth.value = session?.let { AuthState(it.uid, it.email, it.verified) }
        tick.value++
    }

    /** Plain REST call with the signed-in user's token when there is one (used for admin-managed content and releases). */
    suspend fun rest(method: String, path: String, body: String? = null, prefer: String? = null): Pair<Int, String> =
        try { raw(method, path, body?.toByteArray(), extra = if (prefer != null) mapOf("Prefer" to prefer) else emptyMap()) } catch (e: CancellationException) { throw e } catch (e: Exception) { 0 to "" }

    private suspend fun raw(
        method: String,
        path: String,
        body: ByteArray? = null,
        contentType: String = "application/json",
        authed: Boolean = true,
        extra: Map<String, String> = emptyMap(),
    ): Pair<Int, String> = withContext(Dispatchers.IO) {
        if (authed) ensureFresh()
        val b = Request.Builder().url(SupabaseConfig.URL + path).header("apikey", SupabaseConfig.PUBLISHABLE_KEY)
        session?.takeIf { authed }?.let { b.header("Authorization", "Bearer ${it.access}") }
        extra.forEach { (k, v) -> b.header(k, v) }
        val rb = body?.toRequestBody(contentType.toMediaType())
        b.method(method, rb ?: if (method == "GET" || method == "HEAD") null else ByteArray(0).toRequestBody(null))
        http.newCall(b.build()).execute().use { it.code to it.body?.string().orEmpty() }
    }

    private suspend fun ensureFresh() = refreshLock.withLock {
        val s = session ?: return@withLock
        if (s.expiresAt - System.currentTimeMillis() / 1000 > 60) return@withLock
        val (code, text) = withContext(Dispatchers.IO) {
            val req = Request.Builder().url("${SupabaseConfig.URL}/auth/v1/token?grant_type=refresh_token")
                .header("apikey", SupabaseConfig.PUBLISHABLE_KEY)
                .post(buildJsonObject { put("refresh_token", s.refresh) }.toString().toRequestBody("application/json".toMediaType())).build()
            http.newCall(req).execute().use { it.code to it.body?.string().orEmpty() }
        }
        if (code in 200..299) {
            val fresh = decodeSession(json.parseToJsonElement(text).jsonObject)
            store(fresh.copy(verified = fresh.verified || s.verified))
        } else if (code == 400 || code == 401) store(null)
    }

    private fun fail(code: Int, text: String): Nothing {
        val o = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
        val errCode = o?.get("error_code")?.jsonPrimitive?.contentOrNull ?: o?.get("code")?.jsonPrimitive?.contentOrNull ?: ""
        val msg = o?.get("msg")?.jsonPrimitive?.contentOrNull ?: o?.get("message")?.jsonPrimitive?.contentOrNull
            ?: o?.get("error_description")?.jsonPrimitive?.contentOrNull ?: "Something went wrong ($code)"
        throw ApiError(errCode, friendly(errCode, msg, code))
    }

    private fun friendly(code: String, msg: String, http: Int): String = when {
        code == "user_already_exists" || code == "email_exists" -> "That email already has an account"
        code == "weak_password" -> "Password is too weak (use 6+ characters)"
        code == "invalid_credentials" -> "Wrong email or password"
        code == "email_not_confirmed" -> "Confirm your email first. Check your inbox and spam folder"
        code.contains("rate_limit") || http == 429 ->
            "RATE_LIMIT:" + (Regex("(\\d+)\\s*second").find(msg)?.groupValues?.get(1)?.toIntOrNull() ?: 60)
        code == "email_address_invalid" -> "That email address isn't accepted"
        code == "email_address_not_authorized" -> "Email sending is limited for this project. Try again later"
        msg.contains("Database error saving new user", true) -> "That username is already taken"
        else -> msg
    }

    private inline fun <T> net(block: () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: ApiError) {
        throw IllegalStateException(e.message, e)
    } catch (e: java.io.IOException) {
        throw IllegalStateException("No connection. Check your internet", e)
    }

    // ---------------------------------------------------------------- auth

    suspend fun signUp(email: String, username: String, password: String): SignUpResult = net {
        val (c0, t0) = raw("POST", "/rest/v1/rpc/username_available", buildJsonObject { put("u", username) }.toString().toByteArray(), authed = false)
        if (c0 in 200..299 && t0.trim() == "false") throw ApiError("username_taken", "That username is already taken")
        val (code, text) = raw(
            "POST", "/auth/v1/signup?redirect_to=${encode(REDIRECT)}",
            buildJsonObject { put("email", email); put("password", password); put("data", buildJsonObject { put("username", username) }) }.toString().toByteArray(),
            authed = false,
        )
        if (code !in 200..299) fail(code, text)
        val o = json.parseToJsonElement(text).jsonObject
        if (o["access_token"] != null) { store(decodeSession(o)); SignUpResult.SignedIn } else SignUpResult.ConfirmEmail
    }

    /** Passwordless: emails a one-tap sign-in link. Creates the account on first use. */
    suspend fun sendSignInLink(email: String) = net {
        val (code, text) = raw(
            "POST", "/auth/v1/otp?redirect_to=${encode(REDIRECT)}",
            buildJsonObject { put("email", email); put("create_user", true) }.toString().toByteArray(), authed = false,
        )
        if (code !in 200..299) fail(code, text)
    }

    suspend fun setUsername(name: String) = net {
        val me = session?.uid ?: return@net
        val (c0, t0) = raw("POST", "/rest/v1/rpc/username_available", buildJsonObject { put("u", name) }.toString().toByteArray())
        if (c0 in 200..299 && t0.trim() == "false") throw ApiError("username_taken", "That username is already taken")
        val (code, text) = raw("PATCH", "/rest/v1/profiles?id=eq.$me", buildJsonObject { put("username", name) }.toString().toByteArray(), extra = mapOf("Prefer" to "return=minimal"))
        if (code == 409) throw ApiError("username_taken", "That username is already taken")
        if (code !in 200..299) fail(code, text)
        refreshSoon()
    }

    suspend fun signIn(email: String, password: String) = net {
        val (code, text) = raw(
            "POST", "/auth/v1/token?grant_type=password",
            buildJsonObject { put("email", email); put("password", password) }.toString().toByteArray(), authed = false,
        )
        if (code !in 200..299) fail(code, text)
        store(decodeSession(json.parseToJsonElement(text).jsonObject))
    }

    suspend fun resendVerification(email: String? = null) = net {
        val target = email ?: session?.email ?: error("No email to send to")
        val (code, text) = raw("POST", "/auth/v1/resend?redirect_to=${encode(REDIRECT)}", buildJsonObject { put("type", "signup"); put("email", target); put("options", buildJsonObject { put("email_redirect_to", REDIRECT) }) }.toString().toByteArray(), authed = false)
        if (code !in 200..299) fail(code, text)
    }

    /** Re-reads the account so a freshly confirmed email is noticed. */
    suspend fun refreshVerification(): Boolean {
        val s = session ?: return false
        return try {
            val (code, text) = raw("GET", "/auth/v1/user")
            if (code == 401) { store(null); return false }
            val confirmed = json.parseToJsonElement(text).jsonObject["email_confirmed_at"]?.let { it !is JsonNull } == true
            if (confirmed != s.verified) session?.let { store(it.copy(verified = confirmed)) }
            confirmed
        } catch (e: CancellationException) { throw e } catch (e: Exception) { s.verified }
    }

    fun signOut() {
        val s = session
        store(null)
        if (s != null) scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    http.newCall(Request.Builder().url("${SupabaseConfig.URL}/auth/v1/logout").header("apikey", SupabaseConfig.PUBLISHABLE_KEY)
                        .header("Authorization", "Bearer ${s.access}").post(ByteArray(0).toRequestBody(null)).build()).execute().close()
                }
            }
        }
    }

    // ---------------------------------------------------------------- reads

    private fun parseDate(s: String?): Long = runCatching {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(s!!.take(19))!!.time
    }.getOrDefault(0L)

    private fun parseProfile(o: JsonObject) = UserProfile(
        uid = o["id"]!!.jsonPrimitive.content,
        username = o["username"]!!.jsonPrimitive.content,
        bio = o["bio"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        createdAt = parseDate(o["created_at"]?.jsonPrimitive?.contentOrNull),
        avatarUrl = o["avatar_url"]?.jsonPrimitive?.contentOrNull,
        bannerUrl = o["banner_url"]?.jsonPrimitive?.contentOrNull,
        followers = count(o["followers"]),
        following = count(o["following"]),
        linkSlug = o["link_slug"]?.jsonPrimitive?.contentOrNull,
        friendRequests = o["friend_requests"]?.jsonPrimitive?.contentOrNull ?: "mutuals",
        listsVisibility = o["lists_visibility"]?.jsonPrimitive?.contentOrNull ?: "everyone",
        displayName = o["display_name"]?.jsonPrimitive?.contentOrNull,
        status = o["status"]?.jsonPrimitive?.contentOrNull ?: "online",
        statusText = o["status_text"]?.jsonPrimitive?.contentOrNull,
        statusEmoji = o["status_emoji"]?.jsonPrimitive?.contentOrNull,
        favoritesVisibility = o["favorites_visibility"]?.jsonPrimitive?.contentOrNull ?: "everyone",
        avatarDeco = o["avatar_deco"]?.jsonPrimitive?.contentOrNull,
        profileEffect = o["profile_effect"]?.jsonPrimitive?.contentOrNull,
        profileTheme = o["profile_theme"]?.jsonPrimitive?.contentOrNull,
        shareActivity = o["share_activity"]?.jsonPrimitive?.booleanOrNull ?: true,
    )

    private fun count(e: JsonElement?): Int? = runCatching { e?.jsonArray?.firstOrNull()?.jsonObject?.get("count")?.jsonPrimitive?.contentOrNull?.toInt() }.getOrNull()

    private suspend fun getRows(path: String): JsonArray {
        val (code, text) = raw("GET", path)
        if (code !in 200..299) fail(code, text)
        return json.parseToJsonElement(text).jsonArray
    }

    private fun <T> live(fetch: suspend () -> T): Flow<T> =
        tick.flatMapLatest {
            flow {
                while (true) {
                    // Nothing is fetched while the app is in the background; it catches up within seconds of coming back.
                    if (com.anibiar.app.data.AppState.foreground) {
                        try { emit(fetch()) } catch (e: CancellationException) { throw e } catch (_: Exception) { }
                        delay(5000)
                    } else delay(2000)
                }
            }
        }.distinctUntilChanged()

    private val cols = "id,username,bio,avatar_url,banner_url,created_at,link_slug,friend_requests,lists_visibility,display_name,status,status_text,status_emoji,favorites_visibility,avatar_deco,profile_effect,profile_theme,share_activity"
    private val cardCols = "$cols,followers:follows!follows_following_id_fkey(count),following:follows!follows_follower_id_fkey(count)"

    suspend fun fetchProfile(uid: String): UserProfile? = getRows("/rest/v1/profiles?id=eq.$uid&select=$cols").firstOrNull()?.let { parseProfile(it.jsonObject) }

    fun profile(uid: String): Flow<UserProfile?> = live { fetchProfile(uid) }

    fun ids(uid: String, kind: String): Flow<Set<String>> = live { fetchIds(uid, kind) }

    private suspend fun fetchIds(uid: String, kind: String): Set<String> =
        if (kind == "followers") getRows("/rest/v1/follows?following_id=eq.$uid&select=follower_id").map { it.jsonObject["follower_id"]!!.jsonPrimitive.content }.toSet()
        else getRows("/rest/v1/follows?follower_id=eq.$uid&select=following_id").map { it.jsonObject["following_id"]!!.jsonPrimitive.content }.toSet()

    suspend fun profiles(ids: Collection<String>): List<UserProfile> {
        if (ids.isEmpty()) return emptyList()
        return try {
            getRows("/rest/v1/profiles?id=in.(${ids.joinToString(",")})&select=$cols&order=username.asc").map { parseProfile(it.jsonObject) }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyList() }
    }

    suspend fun search(prefix: String): List<UserProfile> = net {
        val q = prefix.trim().filter { it.isLetterOrDigit() || it == '_' || it == '.' }.replace("_", "\\_")
        if (q.isEmpty()) return@net emptyList()
        getRows("/rest/v1/profiles?username=ilike.${encode("$q*")}&select=${encode(cardCols)}&order=username.asc&limit=25").map { parseProfile(it.jsonObject) }
    }

    // ---------------------------------------------------------------- direct messages

    private fun isoMs(s: String?): Long = runCatching { java.time.OffsetDateTime.parse(s).toInstant().toEpochMilli() }.getOrDefault(0L)

    private suspend fun fetchConversations(): List<DmConversation> {
        val (code, text) = raw("POST", "/rest/v1/rpc/dm_conversations", "{}".toByteArray())
        if (code !in 200..299) fail(code, text)
        val me = session?.uid
        return json.parseToJsonElement(text).jsonArray.map {
            val o = it.jsonObject
            DmConversation(
                otherId = o["other_id"]!!.jsonPrimitive.content,
                lastBody = o["last_body"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                lastAt = isoMs(o["last_at"]?.jsonPrimitive?.contentOrNull),
                lastMine = o["last_sender"]?.jsonPrimitive?.contentOrNull == me,
                unread = o["unread"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
                streak = o["streak"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
            )
        }
    }

    /** My chats, newest first, refreshed every few seconds. */
    fun conversations(): Flow<List<DmConversation>> = live { fetchConversations().also { convCache = it } }.onStart { convCache?.let { emit(it) } }

    private suspend fun fetchMessages(other: String): List<DmMessage> {
        val me = session?.uid ?: return emptyList()
        val base = "/rest/v1/dm_messages?or=(and(sender_id.eq.$me,recipient_id.eq.$other),and(sender_id.eq.$other,recipient_id.eq.$me))&order=id.desc&limit=150"
        val cols = "id,sender_id,body,created_at,read_at,reply_to,kind,media_url,style"
        // One request: reactions come embedded with the messages. Falls back to a second request if the server can't embed.
        val embedded = try { getRows("$base&select=$cols,dm_reactions(user_id,emoji)") } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        val rows = embedded ?: getRows("$base&select=$cols")
        val reacts: Map<Long, List<Pair<String, String>>> = if (embedded != null) emptyMap() else {
            val ids = rows.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content }
            if (ids.isEmpty()) emptyMap() else try {
                getRows("/rest/v1/dm_reactions?message_id=in.(${ids.joinToString(",")})&select=message_id,user_id,emoji").groupBy(
                    { it.jsonObject["message_id"]!!.jsonPrimitive.content.toLong() },
                    { it.jsonObject["user_id"]!!.jsonPrimitive.content to it.jsonObject["emoji"]!!.jsonPrimitive.content },
                )
            } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyMap() }
        }
        return rows.map {
            val o = it.jsonObject
            val rs: List<Pair<String, String>> = if (embedded != null) {
                (o["dm_reactions"] as? kotlinx.serialization.json.JsonArray).orEmpty().map { r -> r.jsonObject["user_id"]!!.jsonPrimitive.content to r.jsonObject["emoji"]!!.jsonPrimitive.content }
            } else reacts[o["id"]!!.jsonPrimitive.content.toLong()].orEmpty()
            DmMessage(
                reactions = rs.groupingBy { r -> r.second }.eachCount(),
                myReaction = rs.firstOrNull { r -> r.first == me }?.second,
                id = o["id"]!!.jsonPrimitive.content.toLong(),
                mine = o["sender_id"]!!.jsonPrimitive.content == me,
                body = o["body"]!!.jsonPrimitive.content,
                at = isoMs(o["created_at"]?.jsonPrimitive?.contentOrNull),
                read = o["read_at"]?.jsonPrimitive?.contentOrNull != null,
                replyTo = o["reply_to"]?.jsonPrimitive?.contentOrNull?.toLongOrNull(),
                kind = o["kind"]?.jsonPrimitive?.contentOrNull ?: "text",
                mediaUrl = o["media_url"]?.jsonPrimitive?.contentOrNull,
                style = o["style"]?.jsonPrimitive?.contentOrNull,
            )
        }.reversed()
    }

    private val msgCache = java.util.concurrent.ConcurrentHashMap<String, List<DmMessage>>()
    private var convCache: List<DmConversation>? = null
    private var noticeCache: List<Notice>? = null
    /** Messages I just sent that the server hasn't echoed back yet (shown at once, negative ids). */
    private val outbox = MutableStateFlow<Map<String, List<DmMessage>>>(emptyMap())

    private val chatTick = MutableStateFlow(0)

    /** Messages with one person, oldest first. The last copy shows instantly; a fresh one follows, then a poll every ~4 s while the app is open. */
    fun messages(other: String): Flow<List<DmMessage>> {
        val server = chatTick.flatMapLatest {
            flow {
                while (true) {
                    if (com.anibiar.app.data.AppState.foreground) {
                        try { emit(fetchMessages(other).also { msgCache[other] = it }) } catch (e: CancellationException) { throw e } catch (_: Exception) { }
                        delay(4000)
                    } else delay(2000)
                }
            }
        }.onStart { msgCache[other]?.let { emit(it) } }
        return combine(server, outbox.map { it[other].orEmpty() }, incoming.map { it[other].orEmpty() }) { base, pending, pushed ->
            if (pending.isEmpty() && pushed.isEmpty()) base else {
                val known = base.mapTo(HashSet()) { it.id }
                // Each pending message hides behind at most one server copy, so two identical sends both stay visible until echoed.
                val free = base.filter { it.mine }.toMutableList()
                val still = pending.filter { p ->
                    val hit = free.indexOfFirst { it.body == p.body && it.kind == p.kind && it.at >= p.at - 60_000 }
                    if (hit >= 0) { free.removeAt(hit); false } else true
                }
                base + pushed.filter { it.id !in known } + still
            }
        }.distinctUntilChanged()
    }

    /** Messages the other side pushed over the realtime socket before the next fetch. */
    private val incoming = MutableStateFlow<Map<String, List<DmMessage>>>(emptyMap())

    /** A "msg" ping arrived. It carries the message itself, so it shows at once; a fetch then confirms it. */
    fun onDm(b: Broadcast) {
        if (b.event != "msg") return
        val p = b.payload
        val from = p["from"]?.jsonPrimitive?.contentOrNull
        val id = p["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
        if (from != null && id != null) {
            val m = DmMessage(
                id = id, mine = false, body = p["body"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                at = p["at"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: System.currentTimeMillis(), read = false,
                replyTo = p["rt"]?.jsonPrimitive?.contentOrNull?.toLongOrNull(), kind = p["kind"]?.jsonPrimitive?.contentOrNull ?: "text",
                mediaUrl = p["media"]?.jsonPrimitive?.contentOrNull?.ifEmpty { null }, style = p["style"]?.jsonPrimitive?.contentOrNull?.ifEmpty { null },
            )
            val cur = incoming.value[from].orEmpty()
            if (cur.none { it.id == id }) incoming.value = incoming.value + (from to (cur + m).takeLast(30))
        }
        nudge()
    }

    /** A cache copy of what is being sent, so the bubble can show/play it before the upload finishes. */
    private fun localCopy(bytes: ByteArray, ext: String): String? = runCatching {
        val dir = java.io.File(com.anibiar.app.data.Http.context!!.cacheDir, "outgoing").apply { mkdirs() }
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 3600_000 }?.forEach { it.delete() }
        java.io.File(dir, "${System.nanoTime()}.$ext").also { it.writeBytes(bytes) }.toURI().toString()
    }.getOrNull()

    private fun addPending(to: String, m: DmMessage) { outbox.value = outbox.value + (to to (outbox.value[to].orEmpty() + m)) }
    private fun dropPending(to: String, id: Long) { outbox.value = outbox.value + (to to outbox.value[to].orEmpty().filter { it.id != id }) }

    private suspend fun insertDm(to: String, body: String, kind: String, mediaUrl: String?, replyTo: Long?, style: String? = null) {
        val me = session?.uid ?: throw IllegalStateException("Log in to send messages.")
        val (code, resp) = raw(
            "POST", "/rest/v1/dm_messages",
            buildJsonObject {
                put("sender_id", me); put("recipient_id", to); put("body", body); put("kind", kind)
                if (mediaUrl != null) put("media_url", mediaUrl)
                if (replyTo != null) put("reply_to", replyTo)
                if (style != null) put("style", style)
            }.toString().toByteArray(),
            extra = mapOf("Prefer" to "return=representation"),
        )
        if (code !in 200..299) {
            val msg = runCatching { json.parseToJsonElement(resp).jsonObject["message"]?.jsonPrimitive?.contentOrNull }.getOrNull()
            throw IllegalStateException(msg?.takeIf { it.isNotBlank() } ?: "Couldn't send ($code)")
        }
        val row = runCatching { json.parseToJsonElement(resp).jsonArray.first().jsonObject }.getOrNull()
        val id = row?.get("id")?.jsonPrimitive?.contentOrNull
        chatTick.value++; refreshSoon()
        // The ping carries the message so the other phone can show it without waiting for a fetch.
        sendDm(to, "msg", if (id == null) emptyMap() else buildMap {
            put("id", id); put("body", body.take(1500)); put("kind", kind); put("media", mediaUrl.orEmpty()); put("style", style.orEmpty())
            put("at", isoMs(row["created_at"]?.jsonPrimitive?.contentOrNull).toString()); if (replyTo != null) put("rt", replyTo.toString())
        })
    }

    suspend fun sendMessage(to: String, body: String, replyTo: Long? = null) = net {
        val text = body.trim()
        if (text.isEmpty()) return@net
        // Shows in the chat immediately; the server copy replaces it when it comes back.
        val pid = -System.nanoTime()
        addPending(to, DmMessage(id = pid, mine = true, body = text, at = System.currentTimeMillis(), read = false, replyTo = replyTo))
        try { insertDm(to, text, "text", null, replyTo) } catch (e: Throwable) { dropPending(to, pid); throw e }
        scope.launch { delay(10_000); dropPending(to, pid) }
    }

    /** Uploads [bytes] to the dm-media bucket and sends it as a photo. */
    suspend fun sendMedia(to: String, bytes: ByteArray, mime: String, replyTo: Long? = null) = net {
        val me = session?.uid ?: throw IllegalStateException("Log in to send messages.")
        if (bytes.size > 8 * 1024 * 1024) throw IllegalStateException("That file is over 8 MB.")
        val ext = when (mime) { "image/png" -> "png"; "image/gif" -> "gif"; "image/webp" -> "webp"; else -> "jpg" }
        val path = "$me/${java.util.UUID.randomUUID()}.$ext"
        // The photo shows in the chat from the local copy right away, while it uploads.
        val pid = -System.nanoTime()
        val local = localCopy(bytes, ext)
        if (local != null) addPending(to, DmMessage(id = pid, mine = true, body = "Photo", at = System.currentTimeMillis(), read = false, replyTo = replyTo, kind = "image", mediaUrl = local))
        try {
            val (code, resp) = raw("POST", "/storage/v1/object/dm-media/$path", bytes, contentType = mime)
            if (code !in 200..299) fail(code, resp)
            insertDm(to, "Photo", "image", "${SupabaseConfig.URL}/storage/v1/object/public/dm-media/$path", replyTo)
        } catch (e: Throwable) { dropPending(to, pid); throw e }
        scope.launch { delay(12_000); dropPending(to, pid) }
    }

    /** Voice note: [bytes] is AAC in an MP4 container. [style] carries "<seconds:3 digits><20 loudness digits>". */
    suspend fun sendVoice(to: String, bytes: ByteArray, seconds: Int, bars: String, replyTo: Long? = null) = net {
        val me = session?.uid ?: throw IllegalStateException("Log in to send messages.")
        if (bytes.size > 6 * 1024 * 1024) throw IllegalStateException("That recording is too long.")
        val path = "$me/${java.util.UUID.randomUUID()}.m4a"
        val style = "%03d".format(seconds.coerceIn(0, 999)) + bars.take(20).padEnd(20, '0')
        val pid = -System.nanoTime()
        val local = localCopy(bytes, "m4a")
        if (local != null) addPending(to, DmMessage(id = pid, mine = true, body = "Voice message", at = System.currentTimeMillis(), read = false, replyTo = replyTo, kind = "voice", mediaUrl = local, style = style))
        try {
            val (code, resp) = raw("POST", "/storage/v1/object/dm-media/$path", bytes, contentType = "audio/mp4")
            if (code !in 200..299) fail(code, resp)
            insertDm(to, "Voice message", "voice", "${SupabaseConfig.URL}/storage/v1/object/public/dm-media/$path", replyTo, style)
        } catch (e: Throwable) { dropPending(to, pid); throw e }
        scope.launch { delay(12_000); dropPending(to, pid) }
    }

    // ---- instant nudges over the shared realtime socket ("dmu-<uid>" is my own inbox channel)

    /** Events pushed to me while the app is open: "msg" (something changed) and "typing". */
    fun dmStream(): Flow<Broadcast> = _auth.map { it?.uid }.distinctUntilChanged().flatMapLatest { uid ->
        if (uid == null) emptyFlow() else {
            val name = "dmu-$uid"
            realtime.join(name).onCompletion { realtime.leave(name) }
        }
    }

    /** Join the other person's channel while a chat is open so I can ping them. */
    fun openChat(peer: String) { realtime.join("dmu-$peer") }
    fun closeChat(peer: String) { realtime.leave("dmu-$peer") }

    fun sendDm(peer: String, event: String, extra: Map<String, String> = emptyMap()) {
        val me = session?.uid ?: return
        realtime.send("dmu-$peer", event, buildJsonObject { put("from", me); extra.forEach { (k, v) -> put(k, v) } })
    }

    /** Something changed (a ping arrived): refetch chats and messages now. */
    fun nudge() { chatTick.value++; refreshSoon() }

    /** Tell [peer] I'm typing (or stopped). Fire and forget. */
    suspend fun setTyping(peer: String, on: Boolean) {
        runCatching { raw("POST", "/rest/v1/rpc/dm_set_typing", buildJsonObject { put("peer", peer); put("is_on", on) }.toString().toByteArray()) }
    }

    /** Is [peer] typing to me right now? (Server-side clock, last 5 s.) */
    suspend fun isTyping(peer: String): Boolean = try {
        val (code, text) = raw("POST", "/rest/v1/rpc/dm_is_typing", buildJsonObject { put("peer", peer) }.toString().toByteArray())
        code in 200..299 && text.trim() == "true"
    } catch (e: CancellationException) { throw e } catch (_: Exception) { false }

    /** Tap the same emoji again to remove it; a different one replaces yours. */
    suspend fun react(messageId: Long, emoji: String, peer: String, remove: Boolean) = net {
        val me = session?.uid ?: throw IllegalStateException("Log in to react.")
        val (code, text) = if (remove) raw("DELETE", "/rest/v1/dm_reactions?message_id=eq.$messageId&user_id=eq.$me", extra = mapOf("Prefer" to "return=minimal"))
        else raw(
            "POST", "/rest/v1/dm_reactions?on_conflict=message_id,user_id",
            buildJsonObject { put("message_id", messageId); put("user_id", me); put("emoji", emoji) }.toString().toByteArray(),
            extra = mapOf("Prefer" to "return=minimal,resolution=merge-duplicates"),
        )
        if (code !in 200..299) fail(code, text)
        chatTick.value++; refreshSoon(); sendDm(peer, "msg")
    }

    suspend fun unsendMessage(id: Long) = net {
        val (code, text) = raw("DELETE", "/rest/v1/dm_messages?id=eq.$id", extra = mapOf("Prefer" to "return=minimal"))
        if (code !in 200..299) fail(code, text)
        chatTick.value++; refreshSoon()
    }

    suspend fun markChatRead(other: String) {
        val me = session?.uid ?: return
        runCatching {
            raw(
                "PATCH", "/rest/v1/dm_messages?recipient_id=eq.$me&sender_id=eq.$other&read_at=is.null",
                buildJsonObject { put("read_at", java.time.Instant.now().toString()) }.toString().toByteArray(),
                extra = mapOf("Prefer" to "return=minimal"),
            )
            _dmUnread.value = 0; refreshSoon(); sendDm(other, "msg")
        }
    }

    /** People I don't follow yet, for the "Suggested accounts" strips. Friends-of-friends first, then newest. */
    suspend fun suggestions(exclude: Set<String> = emptySet(), limit: Int = 12): List<UserProfile> {
        val me = session?.uid ?: return emptyList()
        return try {
            val skip = (exclude + _myFollowing.value + me)
            val rows = getRows("/rest/v1/profiles?select=${encode(cardCols)}&order=created_at.desc&limit=60").map { parseProfile(it.jsonObject) }
            val mine = _myFollowers.value
            rows.filter { it.uid !in skip }.sortedByDescending { if (it.uid in mine) 1 else 0 }.take(limit)
        } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyList() }
    }

    // ---------------------------------------------------------------- writes

    private fun refreshSoon() { tick.value++ }

    suspend fun follow(target: String) {
        val me = session?.uid ?: return
        val (code, text) = raw(
            "POST", "/rest/v1/follows",
            buildJsonObject { put("follower_id", me); put("following_id", target) }.toString().toByteArray(),
            extra = mapOf("Prefer" to "return=minimal"),
        )
        if (code !in 200..299 && code != 409) fail(code, text)
        _myFollowing.value = _myFollowing.value + target
        refreshSoon()
    }

    suspend fun unfollow(target: String) {
        val me = session?.uid ?: return
        val (code, text) = raw("DELETE", "/rest/v1/follows?follower_id=eq.$me&following_id=eq.$target")
        if (code !in 200..299) fail(code, text)
        _myFollowing.value = _myFollowing.value - target
        refreshSoon()
    }

    suspend fun setBio(bio: String) = net {
        val me = session?.uid ?: return@net
        val (code, text) = raw("PATCH", "/rest/v1/profiles?id=eq.$me", buildJsonObject { put("bio", bio.take(200)) }.toString().toByteArray(), extra = mapOf("Prefer" to "return=minimal"))
        if (code !in 200..299) fail(code, text)
        refreshSoon()
    }

    private suspend fun uploadImage(bucket: String, bytes: ByteArray, mime: String, ext: String, column: String, old: String?) {
        val me = session?.uid ?: return
        val path = "$me/${System.currentTimeMillis()}.$ext"
        val (c1, t1) = raw("POST", "/storage/v1/object/$bucket/$path", bytes, contentType = mime, extra = mapOf("cache-control" to "max-age=31536000"))
        if (c1 !in 200..299) fail(c1, t1)
        val url = "${SupabaseConfig.URL}/storage/v1/object/public/$bucket/$path"
        val (c2, t2) = raw("PATCH", "/rest/v1/profiles?id=eq.$me", buildJsonObject { put(column, url) }.toString().toByteArray(), extra = mapOf("Prefer" to "return=minimal"))
        if (c2 !in 200..299) fail(c2, t2)
        // Best-effort cleanup of the previous file.
        old?.substringAfter("/object/public/$bucket/", "")?.takeIf { it.isNotEmpty() && it.startsWith("$me/") }?.let { runCatching { raw("DELETE", "/storage/v1/object/$bucket/$it") } }
        refreshSoon()
    }

    suspend fun setAvatar(bytes: ByteArray) = net {
        val old = session?.uid?.let { fetchProfile(it)?.avatarUrl }
        uploadImage("avatars", bytes, "image/jpeg", "jpg", "avatar_url", old)
    }

    suspend fun setBanner(bytes: ByteArray, gif: Boolean) = net {
        val old = session?.uid?.let { fetchProfile(it)?.bannerUrl }
        uploadImage("banners", bytes, if (gif) "image/gif" else "image/jpeg", if (gif) "gif" else "jpg", "banner_url", old)
    }

    // ---------------------------------------------------------------- friends

    /** Accepted friends of [uid], refreshed live. */
    fun friendIds(uid: String): Flow<Set<String>> = live {
        getRows("/rest/v1/friend_requests?status=eq.accepted&or=(requester_id.eq.$uid,addressee_id.eq.$uid)&select=requester_id,addressee_id").map {
            val o = it.jsonObject
            val a = o["requester_id"]!!.jsonPrimitive.content; val b = o["addressee_id"]!!.jsonPrimitive.content
            if (a == uid) b else a
        }.toSet()
    }

    suspend fun sendFriendRequest(target: String) = net {
        val me = session?.uid ?: return@net
        val (code, text) = raw(
            "POST", "/rest/v1/friend_requests",
            buildJsonObject { put("requester_id", me); put("addressee_id", target) }.toString().toByteArray(),
            extra = mapOf("Prefer" to "return=minimal"),
        )
        if (code == 403) throw ApiError("denied", "You can't send this person a friend request")
        if (code !in 200..299 && code != 409) fail(code, text)
        _outgoing.value = _outgoing.value + target
        refreshSoon()
    }

    suspend fun acceptFriend(requester: String) = net {
        val me = session?.uid ?: return@net
        val (code, text) = raw(
            "PATCH", "/rest/v1/friend_requests?requester_id=eq.$requester&addressee_id=eq.$me",
            buildJsonObject { put("status", "accepted") }.toString().toByteArray(), extra = mapOf("Prefer" to "return=minimal"),
        )
        if (code !in 200..299) fail(code, text)
        _incoming.value = _incoming.value - requester
        _myFriends.value = _myFriends.value + requester
        refreshSoon()
    }

    /** Cancels a request I sent, declines one I got, or removes an existing friend. */
    suspend fun removeFriend(other: String) = net {
        val me = session?.uid ?: return@net
        val (code, text) = raw("DELETE", "/rest/v1/friend_requests?or=(and(requester_id.eq.$me,addressee_id.eq.$other),and(requester_id.eq.$other,addressee_id.eq.$me))")
        if (code !in 200..299) fail(code, text)
        _incoming.value = _incoming.value - other; _outgoing.value = _outgoing.value - other; _myFriends.value = _myFriends.value - other
        refreshSoon()
    }

    // ---------------------------------------------------------------- inbox

    fun notices(): Flow<List<Notice>> = live<List<Notice>> {
        val me = session?.uid ?: return@live emptyList()
        val rows = getRows("/rest/v1/notifications?user_id=eq.$me&select=id,type,read,created_at,actor_id,ref&order=created_at.desc&limit=100")
        val actors = profiles(rows.map { it.jsonObject["actor_id"]!!.jsonPrimitive.content }.toSet()).associateBy { it.uid }
        rows.map {
            val o = it.jsonObject
            val aid = o["actor_id"]!!.jsonPrimitive.content
            Notice(
                id = o["id"]!!.jsonPrimitive.content.toLong(), type = o["type"]!!.jsonPrimitive.content, actor = actors[aid], actorId = aid,
                read = o["read"]?.jsonPrimitive?.contentOrNull == "true", createdAt = parseDate(o["created_at"]?.jsonPrimitive?.contentOrNull),
                ref = o["ref"]?.jsonPrimitive?.contentOrNull,
            )
        }.also { noticeCache = it }
    }.onStart { noticeCache?.let { emit(it) } }

    suspend fun markAllRead() {
        val me = session?.uid ?: return
        runCatching {
            raw("PATCH", "/rest/v1/notifications?user_id=eq.$me&read=eq.false", buildJsonObject { put("read", true) }.toString().toByteArray(), extra = mapOf("Prefer" to "return=minimal"))
            _unread.value = 0
            refreshSoon()
        }
    }

    suspend fun deleteNotice(id: Long) {
        runCatching { raw("DELETE", "/rest/v1/notifications?id=eq.$id"); refreshSoon() }
    }

    // ---------------------------------------------------------------- settings & links

    private suspend fun patchProfile(body: JsonObject) {
        val me = session?.uid ?: return
        val (code, text) = raw("PATCH", "/rest/v1/profiles?id=eq.$me", body.toString().toByteArray(), extra = mapOf("Prefer" to "return=minimal"))
        if (code == 409) throw ApiError("taken", "That link is already taken")
        if (code !in 200..299) fail(code, text)
        refreshSoon()
    }

    suspend fun setLinkSlug(slug: String) = net {
        val v = slug.trim()
        if (v.isNotEmpty()) {
            if (!Regex("^[A-Za-z0-9_.-]{3,30}$").matches(v)) throw ApiError("bad", "Use 3-30 letters, numbers, _ . or -")
            val (c0, t0) = raw("POST", "/rest/v1/rpc/link_available", buildJsonObject { put("s", v) }.toString().toByteArray())
            val mine = session?.uid?.let { fetchProfile(it)?.linkSlug }
            if (c0 in 200..299 && t0.trim() == "false" && !v.equals(mine, ignoreCase = true)) throw ApiError("taken", "That link is already taken")
        }
        patchProfile(buildJsonObject { put("link_slug", if (v.isEmpty()) JsonNull else JsonPrimitive(v)) })
    }

    suspend fun setDisplayName(name: String) = net {
        val v = name.trim()
        patchProfile(buildJsonObject { put("display_name", if (v.isEmpty()) JsonNull else JsonPrimitive(v)) })
    }

    suspend fun setStatus(v: String) = net { patchProfile(buildJsonObject { put("status", v) }) }
    suspend fun setCustomStatus(emoji: String?, text: String?) = net {
        val t = text?.trim().orEmpty().take(60); val e = emoji?.trim().orEmpty().take(8)
        patchProfile(buildJsonObject {
            put("status_text", if (t.isEmpty()) JsonNull else JsonPrimitive(t))
            put("status_emoji", if (e.isEmpty()) JsonNull else JsonPrimitive(e))
        })
    }

    // ---- Friends hub: activity feed and shared lists ----

    suspend fun setShareActivity(on: Boolean) = net { patchProfile(buildJsonObject { put("share_activity", on) }) }

    /** What my friends watched recently. Friends who turned sharing off never appear (the database enforces it). */
    suspend fun friendActivity(): List<FriendActivity> = net {
        val me = session?.uid ?: return@net emptyList()
        val friends = getRows("/rest/v1/friend_requests?status=eq.accepted&or=(requester_id.eq.$me,addressee_id.eq.$me)&select=requester_id,addressee_id").map {
            val o = it.jsonObject; val a = o["requester_id"]!!.jsonPrimitive.content; val b = o["addressee_id"]!!.jsonPrimitive.content
            if (a == me) b else a
        }
        if (friends.isEmpty()) return@net emptyList()
        val rows = getRows("/rest/v1/watch_progress?user_id=in.(${friends.joinToString(",")})&select=user_id,media_id,title,image,episode,progress,updated_at&order=updated_at.desc&limit=50")
        val people = profiles(friends).associateBy { it.uid }
        rows.mapNotNull { r ->
            val o = r.jsonObject
            val u = people[o["user_id"]!!.jsonPrimitive.content] ?: return@mapNotNull null
            FriendActivity(
                u, o["media_id"]!!.jsonPrimitive.content.toInt(), o["title"]?.jsonPrimitive?.contentOrNull.orEmpty(), o["image"]?.jsonPrimitive?.contentOrNull,
                o["episode"]!!.jsonPrimitive.content.toInt(), o["progress"]!!.jsonPrimitive.content.toFloat(), isoMs(o["updated_at"]?.jsonPrimitive?.contentOrNull),
            )
        }
    }

    suspend fun myLists(): List<SharedList> = net {
        val me = session?.uid ?: return@net emptyList()
        getRows("/rest/v1/shared_lists?select=id,name,owner&order=created_at.desc").map {
            val o = it.jsonObject; val owner = o["owner"]!!.jsonPrimitive.content
            SharedList(o["id"]!!.jsonPrimitive.content, o["name"]!!.jsonPrimitive.content, owner, owner == me)
        }
    }

    suspend fun createList(name: String) = net {
        val (code, text) = raw("POST", "/rest/v1/shared_lists", buildJsonObject { put("name", name.trim().take(60)) }.toString().toByteArray(), extra = mapOf("Prefer" to "return=minimal"))
        if (code !in 200..299) fail(code, text)
    }

    suspend fun deleteList(id: String) = net { raw("DELETE", "/rest/v1/shared_lists?id=eq.$id"); Unit }
    suspend fun leaveList(id: String) = net { val me = session?.uid ?: return@net; raw("DELETE", "/rest/v1/shared_list_members?list_id=eq.$id&user_id=eq.$me"); Unit }

    suspend fun listItems(listId: String): List<ListItem> = net {
        val me = session?.uid
        val votes = getRows("/rest/v1/shared_list_votes?list_id=eq.$listId&select=media_id,user_id").groupBy({ it.jsonObject["media_id"]!!.jsonPrimitive.content.toInt() }, { it.jsonObject["user_id"]!!.jsonPrimitive.content })
        getRows("/rest/v1/shared_list_items?list_id=eq.$listId&select=media_id,title,image,added_by&order=created_at.asc").map {
            val o = it.jsonObject; val id = o["media_id"]!!.jsonPrimitive.content.toInt()
            ListItem(id, o["title"]!!.jsonPrimitive.content, o["image"]?.jsonPrimitive?.contentOrNull, o["added_by"]!!.jsonPrimitive.content, votes[id]?.size ?: 0, me != null && votes[id]?.contains(me) == true)
        }.sortedByDescending { it.votes }
    }

    suspend fun addToList(listId: String, mediaId: Int, title: String, image: String?) = net {
        val (code, text) = raw(
            "POST", "/rest/v1/shared_list_items?on_conflict=list_id,media_id",
            buildJsonObject { put("list_id", listId); put("media_id", mediaId); put("title", title.take(200)); put("image", if (image == null) JsonNull else JsonPrimitive(image.take(600))) }.toString().toByteArray(),
            extra = mapOf("Prefer" to "return=minimal,resolution=ignore-duplicates"),
        )
        if (code !in 200..299) fail(code, text)
    }

    suspend fun removeFromList(listId: String, mediaId: Int) = net { raw("DELETE", "/rest/v1/shared_list_items?list_id=eq.$listId&media_id=eq.$mediaId"); Unit }

    suspend fun vote(listId: String, mediaId: Int, on: Boolean) = net {
        val me = session?.uid ?: throw IllegalStateException("Log in to vote.")
        val (code, text) = if (on) raw(
            "POST", "/rest/v1/shared_list_votes?on_conflict=list_id,media_id,user_id",
            buildJsonObject { put("list_id", listId); put("media_id", mediaId); put("user_id", me) }.toString().toByteArray(),
            extra = mapOf("Prefer" to "return=minimal,resolution=ignore-duplicates"),
        ) else raw("DELETE", "/rest/v1/shared_list_votes?list_id=eq.$listId&media_id=eq.$mediaId&user_id=eq.$me")
        if (code !in 200..299) fail(code, text)
    }

    suspend fun listMembers(listId: String): Set<String> = net {
        getRows("/rest/v1/shared_list_members?list_id=eq.$listId&select=user_id").map { it.jsonObject["user_id"]!!.jsonPrimitive.content }.toSet()
    }

    suspend fun inviteToList(listId: String, userId: String) = net {
        val (code, text) = raw(
            "POST", "/rest/v1/shared_list_members?on_conflict=list_id,user_id",
            buildJsonObject { put("list_id", listId); put("user_id", userId) }.toString().toByteArray(),
            extra = mapOf("Prefer" to "return=minimal,resolution=ignore-duplicates"),
        )
        if (code !in 200..299) fail(code, text)
        runCatching { nudge() }
    }

    suspend fun myFriendIds(): List<String> = net {
        val me = session?.uid ?: return@net emptyList()
        getRows("/rest/v1/friend_requests?status=eq.accepted&or=(requester_id.eq.$me,addressee_id.eq.$me)&select=requester_id,addressee_id").map {
            val o = it.jsonObject; val a = o["requester_id"]!!.jsonPrimitive.content; val b = o["addressee_id"]!!.jsonPrimitive.content
            if (a == me) b else a
        }
    }

    suspend fun setLook(deco: String?, effect: String?, theme: String? = null) = net {
        patchProfile(buildJsonObject {
            put("avatar_deco", if (deco == null) JsonNull else JsonPrimitive(deco))
            put("profile_effect", if (effect == null) JsonNull else JsonPrimitive(effect))
            put("profile_theme", if (theme == null) JsonNull else JsonPrimitive(theme))
        })
    }
    suspend fun setFavoritesVisibility(v: String) = net { patchProfile(buildJsonObject { put("favorites_visibility", v) }) }

    /** [uid]'s favourites, newest first. The server returns nothing when their privacy setting hides them from me. */
    fun favorites(uid: String): Flow<List<Fav>> = live {
        getRows("/rest/v1/favorites?user_id=eq.$uid&select=anime_id,title,cover&order=created_at.desc&limit=300").map {
            val o = it.jsonObject
            Fav(o["anime_id"]!!.jsonPrimitive.content.toInt(), o["title"]!!.jsonPrimitive.content, o["cover"]?.jsonPrimitive?.contentOrNull)
        }
    }

    /** How many people favourited [id], live: re-read on a short timer and right away when I toggle it. */
    fun favoriteCount(id: Int): Flow<Int> = _myFavs.map { id in it }.distinctUntilChanged().flatMapLatest {
        live {
            val (code, text) = raw("POST", "/rest/v1/rpc/favorite_count", buildJsonObject { put("a", id) }.toString().toByteArray())
            if (code !in 200..299) fail(code, text)
            text.trim().toInt()
        }
    }

    suspend fun toggleFavorite(id: Int, title: String, cover: String?) = net {
        val me = session?.uid ?: throw ApiError("auth", "Log in to save favorites")
        if (id in _myFavs.value) {
            val (code, text) = raw("DELETE", "/rest/v1/favorites?user_id=eq.$me&anime_id=eq.$id")
            if (code !in 200..299) fail(code, text)
            _myFavs.value = _myFavs.value - id
        } else {
            val body = buildJsonObject { put("user_id", me); put("anime_id", id); put("title", title); put("cover", if (cover == null) JsonNull else JsonPrimitive(cover)) }
            val (code, text) = raw("POST", "/rest/v1/favorites", body.toString().toByteArray(), extra = mapOf("Prefer" to "return=minimal,resolution=ignore-duplicates"))
            if (code !in 200..299) fail(code, text)
            _myFavs.value = _myFavs.value + id
        }
    }

    /** Mirrors one "continue watching" entry to my account so it shows on my other devices. Never throws. */
    private val pendingProgress = java.util.concurrent.ConcurrentHashMap<Int, com.anibiar.app.data.HistoryItem>()

    /** Progress that couldn't be sent (offline) goes up when the connection returns. */
    suspend fun flushPending() { pendingProgress.values.toList().forEach { pendingProgress.remove(it.mediaId); pushProgress(it) } }

    suspend fun pushProgress(item: com.anibiar.app.data.HistoryItem) {
        if (session == null) return
        runCatching {
            val body = buildJsonObject {
                put("media_id", item.mediaId); put("title", item.title.take(200)); put("image", if (item.image == null) JsonNull else JsonPrimitive(item.image.take(600)))
                put("episode", item.episode.coerceIn(0, 5000)); put("progress", item.progress.toDouble().coerceIn(0.0, 1.0))
                put("updated_at", java.time.Instant.ofEpochMilli(item.at.takeIf { it > 0 } ?: System.currentTimeMillis()).toString())
            }
            raw("POST", "/rest/v1/watch_progress?on_conflict=user_id,media_id", body.toString().toByteArray(), extra = mapOf("Prefer" to "return=minimal,resolution=merge-duplicates"))
        }.onFailure { if (it !is CancellationException) pendingProgress[item.mediaId] = item }
    }

    suspend fun removeProgress(mediaId: Int) {
        val me = session?.uid ?: return
        runCatching { raw("DELETE", "/rest/v1/watch_progress?user_id=eq.$me&media_id=eq.$mediaId") }
    }

    /** My saved "continue watching" list from the account (newest first), empty when logged out or offline. */
    suspend fun pullProgress(): List<com.anibiar.app.data.HistoryItem> {
        val me = session?.uid ?: return emptyList()
        return runCatching {
            getRows("/rest/v1/watch_progress?user_id=eq.$me&select=media_id,title,image,episode,progress,updated_at&order=updated_at.desc&limit=20").mapNotNull { r ->
                val o = r.jsonObject
                com.anibiar.app.data.HistoryItem(
                    o["media_id"]!!.jsonPrimitive.content.toInt(), o["title"]?.jsonPrimitive?.contentOrNull.orEmpty(), o["image"]?.jsonPrimitive?.contentOrNull,
                    o["episode"]!!.jsonPrimitive.content.toInt(), o["progress"]!!.jsonPrimitive.content.toFloat(),
                    runCatching { java.time.Instant.parse(o["updated_at"]!!.jsonPrimitive.content).toEpochMilli() }.getOrDefault(0L),
                )
            }
        }.getOrDefault(emptyList())
    }

    suspend fun setFriendRequests(v: String) = net { patchProfile(buildJsonObject { put("friend_requests", v) }) }
    suspend fun setListsVisibility(v: String) = net { patchProfile(buildJsonObject { put("lists_visibility", v) }) }

    /** Opens the profile behind a kiro://u/<link> link (needs a signed-in session to read profiles). */
    suspend fun openLink(slug: String): Boolean {
        if (session == null) return false
        return try {
            val id = getRows("/rest/v1/profiles?link_slug=ilike.${encode(slug.replace("_", "\\_"))}&select=id&limit=1").firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.content
            if (id != null) _pendingUser.value = id
            id != null
        } catch (e: CancellationException) { throw e } catch (e: Exception) { false }
    }

    // ---------------------------------------------------------------- watch together

    private fun parseRoom(o: JsonObject) = Room(
        id = o["id"]!!.jsonPrimitive.content, hostId = o["host_id"]!!.jsonPrimitive.content,
        animeId = o["anime_id"]!!.jsonPrimitive.content.toInt(), title = o["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        cover = o["cover"]?.jsonPrimitive?.contentOrNull, episode = o["episode"]!!.jsonPrimitive.content.toInt(),
        audio = o["audio"]?.jsonPrimitive?.contentOrNull ?: "sub", ended = o["ended"]?.jsonPrimitive?.contentOrNull == "true",
    )

    suspend fun createRoom(animeId: Int, title: String, cover: String?, episode: Int, audio: String): Room = net {
        val me = session?.uid ?: throw ApiError("auth", "Log in to watch together")
        val body = buildJsonObject {
            put("host_id", me); put("anime_id", animeId); put("title", title); put("episode", episode); put("audio", audio)
            put("cover", if (cover == null) JsonNull else JsonPrimitive(cover))
        }
        val (code, text) = raw("POST", "/rest/v1/watch_rooms", body.toString().toByteArray(), extra = mapOf("Prefer" to "return=representation"))
        if (code !in 200..299) fail(code, text)
        parseRoom(json.parseToJsonElement(text).jsonArray[0].jsonObject)
    }

    suspend fun fetchRoom(id: String): Room? = net {
        getRows("/rest/v1/watch_rooms?id=eq.${encode(id)}&select=*&limit=1").firstOrNull()?.let { parseRoom(it.jsonObject) }
    }

    /** Friends who already have an invite to [roomId]. */
    suspend fun invitedIds(roomId: String): Set<String> = net {
        getRows("/rest/v1/watch_invites?room_id=eq.$roomId&select=user_id").map { it.jsonObject["user_id"]!!.jsonPrimitive.content }.toSet()
    }

    /** Invites a friend: stored (so it also lands in their inbox) and pushed live over realtime. */
    suspend fun inviteToRoom(room: Room, userId: String) = net {
        val me = session?.uid ?: return@net
        val body = buildJsonObject { put("room_id", room.id); put("user_id", userId); put("from_id", me) }
        val (code, text) = raw("POST", "/rest/v1/watch_invites", body.toString().toByteArray(), extra = mapOf("Prefer" to "return=minimal,resolution=ignore-duplicates"))
        if (code !in 200..299) fail(code, text)
        val chan = "inv-$userId"
        // A second invite must not be cut off by the first one's cleanup.
        inviteLeave.remove(chan)?.cancel()
        realtime.join(chan)
        delay(400)
        realtime.send(chan, "invite", buildJsonObject { put("room", room.id); put("from", me); put("title", room.title); put("ep", room.episode); put("n", System.currentTimeMillis()) })
        inviteLeave[chan] = scope.launch { delay(5000); realtime.leave(chan); inviteLeave.remove(chan) }
    }
    private val inviteLeave = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Job>()

    suspend fun updateRoom(id: String, episode: Int, audio: String) = runCatching {
        raw("PATCH", "/rest/v1/watch_rooms?id=eq.$id", buildJsonObject { put("episode", episode); put("audio", audio) }.toString().toByteArray(), extra = mapOf("Prefer" to "return=minimal"))
    }

    suspend fun switchRoomAnime(r: Room) = runCatching {
        val body = buildJsonObject {
            put("anime_id", r.animeId); put("title", r.title); put("episode", r.episode); put("audio", r.audio)
            put("cover", if (r.cover == null) JsonNull else JsonPrimitive(r.cover))
        }
        raw("PATCH", "/rest/v1/watch_rooms?id=eq.${r.id}", body.toString().toByteArray(), extra = mapOf("Prefer" to "return=minimal"))
    }

    suspend fun endRoom(id: String) = runCatching {
        raw("PATCH", "/rest/v1/watch_rooms?id=eq.$id", buildJsonObject { put("ended", true) }.toString().toByteArray(), extra = mapOf("Prefer" to "return=minimal"))
    }

    /** Milliseconds to add to this phone's clock to get server time (best of a few round trips). */
    suspend fun clockOffset(): Long? {
        var best = Long.MAX_VALUE; var offset = 0L
        repeat(5) {
            runCatching {
                val t0 = System.currentTimeMillis()
                val (code, text) = raw("POST", "/rest/v1/rpc/server_time", "{}".toByteArray(), authed = false)
                val t1 = System.currentTimeMillis()
                if (code in 200..299 && t1 - t0 < best) { best = t1 - t0; offset = text.trim().toLong() - (t0 + (t1 - t0) / 2) }
            }
        }
        return if (best == Long.MAX_VALUE) null else offset
    }

    /** Live invites pushed to me while the app is open. */
    fun inviteStream(): Flow<Broadcast> = _auth.map { it?.uid }.distinctUntilChanged().flatMapLatest { uid ->
        if (uid == null) emptyFlow() else {
            val name = "inv-$uid"
            realtime.join(name).onCompletion { realtime.leave(name) }
        }
    }
}
