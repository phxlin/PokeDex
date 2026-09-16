package com.pokedex.app.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import kotlin.random.Random

/**
 * Retries a request on a transient failure — a network error, a `429`, or a `5xx`
 * — with exponential backoff and a little jitter. PokeAPI occasionally rate-limits
 * or 5xxs under the bulk request fan-out the team builder does (move pools, item
 * effects), and a single dropped response there silently blanks one row.
 *
 * This is an OkHttp *application* interceptor, so calling `chain.proceed()` more
 * than once is allowed. Backoff uses `Thread.sleep` because interceptors run on
 * OkHttp's blocking dispatcher threads.
 */
class RetryInterceptor(
    private val maxRetries: Int = 3,
    private val baseDelayMs: Long = 400L,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var lastError: IOException? = null

        for (attempt in 0..maxRetries) {
            if (attempt > 0) sleepBackoff(attempt)
            try {
                val response = chain.proceed(request)
                if (response.isSuccessful || !isTransient(response.code) || attempt == maxRetries) {
                    return response
                }
                response.close() // drop the retryable error body before trying again
            } catch (e: IOException) {
                lastError = e
                if (attempt == maxRetries) throw e
            }
        }
        throw lastError ?: IOException("Request failed after $maxRetries retries")
    }

    private fun isTransient(code: Int): Boolean = code == 429 || code in 500..599

    private fun sleepBackoff(attempt: Int) {
        val delay = baseDelayMs * (1L shl (attempt - 1)) + Random.nextLong(0, 250)
        try {
            Thread.sleep(delay)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Retry backoff interrupted", e)
        }
    }
}
