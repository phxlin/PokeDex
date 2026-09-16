package com.pokedex.app.data.remote

import com.google.common.truth.Truth.assertThat
import okhttp3.Call
import okhttp3.Connection
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import java.io.IOException

class RetryInterceptorTest {

    private val interceptor = RetryInterceptor(maxRetries = 2, baseDelayMs = 1L)
    private val request = Request.Builder().url("https://example.test/pokemon/1").build()

    @Test
    fun `returns the first successful response without retrying`() {
        val chain = FakeChain(request, listOf({ response(200) }, { response(200) }))
        val result = interceptor.intercept(chain)

        assertThat(result.code).isEqualTo(200)
        assertThat(chain.calls).isEqualTo(1)
    }

    @Test
    fun `retries a 429 and returns the eventual success`() {
        val chain = FakeChain(request, listOf({ response(429) }, { response(200) }))
        val result = interceptor.intercept(chain)

        assertThat(result.code).isEqualTo(200)
        assertThat(chain.calls).isEqualTo(2)
    }

    @Test
    fun `retries a 503 then gives up after maxRetries, returning the last error response`() {
        val chain = FakeChain(request, List(3) { { response(503) } })
        val result = interceptor.intercept(chain)

        assertThat(result.code).isEqualTo(503)
        assertThat(chain.calls).isEqualTo(3) // initial + 2 retries
    }

    @Test
    fun `does not retry a 404`() {
        val chain = FakeChain(request, listOf({ response(404) }, { response(200) }))
        val result = interceptor.intercept(chain)

        assertThat(result.code).isEqualTo(404)
        assertThat(chain.calls).isEqualTo(1)
    }

    @Test
    fun `retries an IOException and succeeds`() {
        val chain = FakeChain(request, listOf({ throw IOException("boom") }, { response(200) }))
        val result = interceptor.intercept(chain)

        assertThat(result.code).isEqualTo(200)
        assertThat(chain.calls).isEqualTo(2)
    }

    @Test
    fun `rethrows an IOException after exhausting retries`() {
        val chain = FakeChain(request, List(3) { { throw IOException("boom") } })

        val thrown = runCatching { interceptor.intercept(chain) }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(IOException::class.java)
        assertThat(chain.calls).isEqualTo(3)
    }

    private fun response(code: Int): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("test")
        .body("".toResponseBody(null))
        .build()

    /** Minimal [Interceptor.Chain] that plays back a scripted list of proceed() outcomes. */
    private class FakeChain(
        private val request: Request,
        private val outcomes: List<() -> Response>,
    ) : Interceptor.Chain {
        var calls = 0

        override fun request(): Request = request

        override fun proceed(request: Request): Response {
            val outcome = outcomes[calls]
            calls++
            return outcome()
        }

        override fun connection(): Connection? = null
        override fun call(): Call = throw UnsupportedOperationException()
        override fun connectTimeoutMillis(): Int = 0
        override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
        override fun readTimeoutMillis(): Int = 0
        override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
        override fun writeTimeoutMillis(): Int = 0
        override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
    }
}
