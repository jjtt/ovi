package fi.torma.ovi

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

class ParseAuthHeaderTest {

    @Test
    fun testParseAuthHeader() {
        val authHeader =
            "WWW-Authenticate: Digest qop=\"auth\", realm=\"some-realm\", nonce=\"a-nonce-value\", algorithm=SHA-256"
        val result = parseAuthHeader(authHeader)

        assertEquals("some-realm", result["realm"])
        assertEquals("a-nonce-value", result["nonce"])
        assertEquals("SHA-256", result["algorithm"])
    }

    @Test
    fun testHexHash() {
        assertEquals(
            "6370ec69915103833b5222b368555393393f098bfbfbb59f47e0590af135f062",
            hexHash("dummy_method:dummy_uri", "SHA-256")
        )
    }

    @Test
    fun test() {
        assertEquals(
            "d0fbc864aa1e03003ae332e320835526927cea7d7c251e02a78b4f5ac6025d58", auth(
                "shellyplus1-d4d4da3b37fc",
                "1707396962",
                "MWU3OTkzMzg2NDRmZTMyM2FiZDA1ZDgzMzgwNjZkYWM=",
                "/rpc/Input.GetStatus?id=0",
                "SHA-256",
                "password"
            )
        )
    }

    @Test
    fun testCoroutine() = runTest {
        println("Running testCoroutine")
        val result = httpGetRequestWithDigestAuth()
        println(result)
    }
}