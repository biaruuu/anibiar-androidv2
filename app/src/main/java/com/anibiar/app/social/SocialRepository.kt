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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
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
)

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

    private val _auth = MutableStateFlow<AuthState?>(null)
    val authState: StateFlow<AuthState?> = _auth.asStateFlow()
    private val _myFollowing = MutableStateFlow<Set<String>>(emptySet())
    val myFollowing: StateFlow<Set<String>> = _myFollowing.asStateFlow()
    private val _myFollowers = MutableStateFlow<Set<String>>(emptySet())
    val myFollowers: StateFlow<Set<String>> = _myFollowers.asStateFlow()
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
                if (me == null) { _myFollowing.value = emptySet(); _myFollowers.value = emptySet() }
                else runCatching {
                    _myFollowing.value = fetchIds(me, "following")
                    _myFollowers.value = fetchIds(me, "followers")
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
        code.contains("rate_limit") || http == 429 -> "Too many attempts. Wait a few minutes and try again"
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

    private val cols = "id,username,bio,avatar_url,banner_url,created_at"
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
}
