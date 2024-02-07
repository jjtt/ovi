package fi.torma.ovi

import com.burgstaller.okhttp.AuthenticationCacheInterceptor
import com.burgstaller.okhttp.CachingAuthenticatorDecorator
import com.burgstaller.okhttp.digest.CachingAuthenticator
import com.burgstaller.okhttp.digest.Credentials
import com.burgstaller.okhttp.digest.DigestAuthenticator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap


fun parseAuthHeader(authHeader: String): Map<String, String> {
    val regex = """(realm|nonce|algorithm)=["]?([a-zA-Z0-9-]+)["]?""".toRegex()
    val matches = regex.findAll(authHeader)

    return matches.associate { it.groupValues[1] to it.groupValues[2] }
}

fun hexHash(input: String, algorithm: String): String {
    return MessageDigest.getInstance(algorithm)
        .digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
}

fun auth(
    realm: String,
    nonce: String,
    cnonce: String,
    uri: String,
    algorithm: String,
    password: String
): String {
    val ha1 = hexHash("admin:$realm:$password", algorithm)
    val ha2 = hexHash("GET:$uri", algorithm)

    return hexHash("$ha1:$nonce:00000001:$cnonce:auth:$ha2", algorithm)
}

suspend fun httpGetRequestWithDigestAuth(): String? = withContext(Dispatchers.IO) {
    try {
        println("Running httpGetRequestWithDigestAuth")
        val url = URL("https://foobar.invalid/Input.GetStatus")
        val connection = url.openConnection() as HttpURLConnection

        connection.requestMethod = "GET"

        val responseCode = connection.responseCode
        println("Response code: $responseCode")
        if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
            val authHeader = connection.getHeaderField("WWW-Authenticate")
            val auth = parseAuthHeader(authHeader)
            val nonce = auth["nonce"]
            val realm = auth["realm"]
            val algorithm = auth["algorithm"]

            val username = "admin"
            val cnonce = "foobar"

            val response = auth(realm!!, nonce!!, cnonce, url.path, algorithm!!, "password")

            val authHeaderValue =
                "Digest username=\"$username\", realm=\"$realm\", nonce=\"$nonce\", uri=\"${url.path}\", cnonce=\"$cnonce\", nc=\"00000001\", qop=\"auth\", response=\"$response\""
            println("Auth header: $authHeaderValue")

            TODO()

            connection.disconnect()
            val connection2 = url.openConnection() as HttpURLConnection
            connection2.setRequestProperty("Authorization", authHeaderValue)

            val secondResponseCode = connection2.responseCode
            println("Second response code: $secondResponseCode")
            if (secondResponseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection2.inputStream))
                val r = reader.readText()
                reader.close()
                r
            } else {
                println("Server responded to second request with status code: $secondResponseCode")
                null
            }
        } else {
            println("Server responded with status code: $responseCode")
            null
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}


fun main() {
    val authenticator = DigestAuthenticator(Credentials("admin", "password"))

    val authCache: Map<String, CachingAuthenticator> = ConcurrentHashMap()
    val client: OkHttpClient = OkHttpClient.Builder()
        .authenticator(CachingAuthenticatorDecorator(authenticator, authCache))
        .addInterceptor(AuthenticationCacheInterceptor(authCache))
        .build()

    val url = "https://foobar.invalid/Input.GetStatus?id=0"
    val request: Request = Request.Builder()
        .url(url)
        .get()
        .build()
    val response = client.newCall(request).execute()

    println(response.body?.string())
}