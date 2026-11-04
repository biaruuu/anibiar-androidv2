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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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
                    _myFriends.value = emptySet(); _incoming.value = emptySet(); _outgoing.value = emptySet(); _unread.value = 0; _myFavs.value = emptySet()
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
                    try { emit(fetch()) } catch (e: CancellationException) { throw e } catch (_: Exception) { }
                    delay(5000)
                }
            }
        }.distinctUntilChanged()

    private val cols = "id,username,bio,avatar_url,banner_url,created_at,link_slug,friend_requests,lists_visibility,display_name,status,status_text,status_emoji,favorites_visibility"
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

    fun notices(): Flow<List<Notice>> = live {
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
        }
    }

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
        realtime.join(chan)
        realtime.send(chan, "invite", buildJsonObject { put("room", room.id); put("from", me); put("title", room.title); put("ep", room.episode) })
        scope.launch { delay(4000); realtime.leave(chan) }
    }

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
