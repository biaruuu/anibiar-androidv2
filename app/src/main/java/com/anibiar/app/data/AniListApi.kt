package com.anibiar.app.data

import android.content.Context
import com.google.net.cronet.okhttptransport.CronetInterceptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.chromium.net.CronetEngine
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Retries when AniList answers 429, honouring Retry-After, so bursts never surface as errors. */
class RateLimitInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var response = chain.proceed(chain.request())
        var attempts = 0
        while (response.code == 429 && attempts < 3) {
            val waitSeconds = (response.header("Retry-After")?.toLongOrNull() ?: 2L).coerceIn(1L, 10L)
            response.close()
            try {
                Thread.sleep(waitSeconds * 1000)
            } catch (e: InterruptedException) {
                throw IOException("Interrupted while waiting for rate limit")
            }
            response = chain.proceed(chain.request())
            attempts++
        }
        return response
    }
}

/**
 * Sends the request through Cronet (HTTP/3 over QUIC). If Cronet fails for any reason the same request
 * is retried over plain OkHttp (HTTP/2), so a QUIC problem never turns into an error screen.
 */
class CronetWithFallback(
    private val cronet: Interceptor,
    private val fallback: OkHttpClient,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        try {
            cronet.intercept(chain)
        } catch (e: IOException) {
            fallback.newCall(chain.request()).execute()
        } catch (e: RuntimeException) {
            fallback.newCall(chain.request()).execute()
        }
}

object Http {
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val client: OkHttpClient by lazy { create() }

    private fun create(): OkHttpClient {
        val plain = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build()

        val builder = plain.newBuilder().addInterceptor(RateLimitInterceptor())
        cronetInterceptor()?.let { builder.addInterceptor(CronetWithFallback(it, plain)) }
        return builder.build()
    }

    private fun cronetInterceptor(): Interceptor? {
        val context = appContext ?: return null
        return try {
            val engine = CronetEngine.Builder(context)
                .enableHttp2(true)
                .enableQuic(true)
                .enableBrotli(true)
                .addQuicHint("graphql.anilist.co", 443, 443)
                .addQuicHint("s4.anilist.co", 443, 443)
                .addQuicHint("api.themoviedb.org", 443, 443)
                .addQuicHint("image.tmdb.org", 443, 443)
                .build()
            CronetInterceptor.newBuilder(engine).build()
        } catch (e: Throwable) {
            null // no Cronet provider on this device: stay on OkHttp
        }
    }
}

object Queries {
    private const val HERO_FIELDS = """
        fragment HeroFields on Media {
          id
          title { romaji english }
          description(asHtml: false)
          episodes
          format
          status
          seasonYear
          startDate { year }
          genres
          averageScore
          bannerImage
          coverImage { extraLarge large color }
          nextAiringEpisode { episode airingAt }
        }
    """

    val DISCOVER = """
        query {
          top: Page(page: 1, perPage: 10) {
            media(type: ANIME, status: RELEASING, sort: TRENDING_DESC, isAdult: false) { ...HeroFields }
          }
          upcoming: Page(page: 1, perPage: 10) {
            media(type: ANIME, status: NOT_YET_RELEASED, sort: POPULARITY_DESC, isAdult: false) { ...HeroFields }
          }
        }
        $HERO_FIELDS
    """.trimIndent()

    val LATEST = """
        query (${'$'}now: Int, ${'$'}page: Int) {
          latest: Page(page: ${'$'}page, perPage: 50) {
            airingSchedules(airingAt_lesser: ${'$'}now, sort: TIME_DESC) {
              episode
              airingAt
              media {
                id
                title { romaji english }
                episodes
                format
                seasonYear
                startDate { year }
                coverImage { extraLarge large color }
                countryOfOrigin
                isAdult
              }
            }
          }
        }
    """.trimIndent()
}

class AniListApi(private val client: OkHttpClient) {

    /** Returns the raw JSON body so callers can cache it as-is. */
    suspend fun execute(query: String, variables: Map<String, Int> = emptyMap()): String =
        withContext(Dispatchers.IO) {
            val payload = buildJsonObject {
                put("query", query)
                put("variables", buildJsonObject {
                    variables.forEach { (key, value) -> put(key, value) }
                })
            }.toString()

            val request = Request.Builder()
                .url(ENDPOINT)
                .header("Accept", "application/json")
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IOException("AniList error ${response.code}")
                body
            }
        }

    private companion object {
        const val ENDPOINT = "https://graphql.anilist.co"
    }
}
