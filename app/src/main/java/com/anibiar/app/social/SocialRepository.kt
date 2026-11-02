package com.anibiar.app.social

import android.util.Base64
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class AuthState(val uid: String, val email: String, val verified: Boolean)

data class UserProfile(
    val uid: String,
    val username: String,
    val bio: String = "",
    val createdAt: Long = 0L,
    val thumb: ByteArray? = null,
)

class ProfileImages(val avatar: ByteArray? = null, val banner: ByteArray? = null, val bannerGif: Boolean = false, val version: Long = 0L)

class SocialRepository {
    private val auth get() = FirebaseAuth.getInstance()
    private val db get() = FirebaseDatabase.getInstance(FirebaseConfig.DB_URL)

    private val _auth = MutableStateFlow<AuthState?>(null)
    val authState: StateFlow<AuthState?> = _auth.asStateFlow()
    private val _myFollowing = MutableStateFlow<Set<String>>(emptySet())
    val myFollowing: StateFlow<Set<String>> = _myFollowing.asStateFlow()
    private val _myFollowers = MutableStateFlow<Set<String>>(emptySet())
    val myFollowers: StateFlow<Set<String>> = _myFollowers.asStateFlow()

    private var bound: String? = null
    private val bindings = ArrayList<Pair<com.google.firebase.database.DatabaseReference, ValueEventListener>>()

    fun start() {
        auth.addAuthStateListener { publish() }
        publish()
    }

    private fun publish() {
        val u = auth.currentUser
        _auth.value = u?.let { AuthState(it.uid, it.email.orEmpty(), it.isEmailVerified) }
        bind(u?.uid)
    }

    private fun bind(uid: String?) {
        if (uid == bound) return
        bindings.forEach { (ref, l) -> ref.removeEventListener(l) }
        bindings.clear()
        bound = uid
        _myFollowing.value = emptySet()
        _myFollowers.value = emptySet()
        if (uid == null) return
        fun watch(path: String, target: MutableStateFlow<Set<String>>) {
            val ref = db.getReference(path)
            val l = object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) { target.value = s.children.mapNotNull { it.key }.toSet() }
                override fun onCancelled(e: DatabaseError) {}
            }
            ref.addValueEventListener(l)
            bindings += ref to l
        }
        watch("follows/$uid/following", _myFollowing)
        watch("follows/$uid/followers", _myFollowers)
    }

    // ---------------------------------------------------------------- auth

    suspend fun signUp(email: String, username: String, password: String) {
        val lower = username.lowercase()
        guard {
            if (db.getReference("usernames/$lower").get().await().exists()) error("That username is already taken")
            val user = auth.createUserWithEmailAndPassword(email, password).await().user ?: error("Sign-up failed")
            try {
                db.getReference("usernames/$lower").setValue(user.uid).await()
                db.getReference("users/${user.uid}").setValue(
                    mapOf("username" to username, "usernameLower" to lower, "bio" to "", "createdAt" to ServerValue.TIMESTAMP),
                ).await()
            } catch (e: Exception) {
                runCatching { user.delete().await() }
                throw e
            }
            user.sendEmailVerification().await()
            publish()
        }
    }

    suspend fun signIn(email: String, password: String) = guard {
        auth.signInWithEmailAndPassword(email, password).await()
        publish()
    }

    suspend fun resendVerification() = guard { auth.currentUser?.sendEmailVerification()?.await() }

    /** Re-reads the account so a freshly clicked verification link is noticed. */
    suspend fun refreshVerification(): Boolean {
        runCatching { auth.currentUser?.reload()?.await() }
        publish()
        return auth.currentUser?.isEmailVerified == true
    }

    fun signOut() { auth.signOut(); publish() }

    private suspend fun <T> guard(block: suspend () -> T): T = try {
        block()
    } catch (e: Exception) {
        throw IllegalStateException(friendly(e), e)
    }

    private fun friendly(e: Exception): String = when (e) {
        is FirebaseAuthUserCollisionException -> "That email already has an account"
        is FirebaseAuthWeakPasswordException -> "Password is too weak (use 6+ characters)"
        is FirebaseAuthInvalidCredentialsException -> "Wrong email or password"
        is FirebaseAuthInvalidUserException -> "No account found for that email"
        is FirebaseNetworkException -> "No connection. Check your internet"
        else -> e.message ?: "Something went wrong"
    }

    // ---------------------------------------------------------------- profiles

    private fun parse(uid: String, s: DataSnapshot): UserProfile? {
        val name = s.child("username").getValue(String::class.java) ?: return null
        return UserProfile(
            uid = uid,
            username = name,
            bio = s.child("bio").getValue(String::class.java).orEmpty(),
            createdAt = s.child("createdAt").getValue(Long::class.java) ?: 0L,
            thumb = s.child("thumb").getValue(String::class.java)?.let { runCatching { Base64.decode(it, Base64.DEFAULT) }.getOrNull() },
        )
    }

    private fun <T> listen(path: String, map: (DataSnapshot) -> T): Flow<T> = callbackFlow {
        val ref = db.getReference(path)
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { trySend(map(s)) }
            override fun onCancelled(e: DatabaseError) { close() }
        }
        ref.addValueEventListener(l)
        awaitClose { ref.removeEventListener(l) }
    }

    fun profile(uid: String): Flow<UserProfile?> = listen("users/$uid") { parse(uid, it) }

    fun images(uid: String): Flow<ProfileImages> =
        listen("images/$uid") { s ->
            fun bytes(k: String) = s.child(k).getValue(String::class.java)?.let { runCatching { Base64.decode(it, Base64.DEFAULT) }.getOrNull() }
            ProfileImages(
                avatar = bytes("avatar"),
                banner = bytes("banner"),
                bannerGif = s.child("bannerMime").getValue(String::class.java) == "image/gif",
                version = System.nanoTime(),
            )
        }.flowOn(Dispatchers.Default)

    fun ids(uid: String, kind: String): Flow<Set<String>> =
        listen("follows/$uid/$kind") { s -> s.children.mapNotNull { it.key }.toSet() }

    suspend fun profiles(ids: Collection<String>): List<UserProfile> = coroutineScope {
        ids.map { id ->
            async { runCatching { parse(id, db.getReference("users/$id").get().await()) }.getOrNull() }
        }.awaitAll().filterNotNull().sortedBy { it.username.lowercase() }
    }

    suspend fun search(prefix: String): List<UserProfile> {
        val q = prefix.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val snap = db.getReference("users").orderByChild("usernameLower").startAt(q).endAt(q + "").limitToFirst(25).get().await()
        return withContext(Dispatchers.Default) { snap.children.mapNotNull { c -> c.key?.let { parse(it, c) } } }
    }

    // ---------------------------------------------------------------- writes

    suspend fun follow(target: String) {
        val me = auth.currentUser?.uid ?: return
        db.reference.updateChildren(mapOf("follows/$me/following/$target" to true, "follows/$target/followers/$me" to true)).await()
    }

    suspend fun unfollow(target: String) {
        val me = auth.currentUser?.uid ?: return
        db.reference.updateChildren(mapOf("follows/$me/following/$target" to null, "follows/$target/followers/$me" to null)).await()
    }

    suspend fun setBio(bio: String) = guard {
        val me = auth.currentUser?.uid ?: return@guard
        db.getReference("users/$me/bio").setValue(bio.take(200)).await()
    }

    suspend fun setAvatar(full: ByteArray, thumb: ByteArray) = guard {
        val me = auth.currentUser?.uid ?: return@guard
        db.reference.updateChildren(
            mapOf(
                "images/$me/avatar" to Base64.encodeToString(full, Base64.NO_WRAP),
                "users/$me/thumb" to Base64.encodeToString(thumb, Base64.NO_WRAP),
            ),
        ).await()
    }

    suspend fun setBanner(bytes: ByteArray, gif: Boolean) = guard {
        val me = auth.currentUser?.uid ?: return@guard
        db.reference.updateChildren(
            mapOf(
                "images/$me/banner" to Base64.encodeToString(bytes, Base64.NO_WRAP),
                "images/$me/bannerMime" to if (gif) "image/gif" else "image/jpeg",
            ),
        ).await()
    }
}
