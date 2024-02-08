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
import java.util.concurrent.ConcurrentHashMap


suspend fun requestInputStatus(): String? = withContext(Dispatchers.IO) {
    request("https://foobar.invalid/Input.GetStatus?id=0", "password")
}

suspend fun requestSwitchOn(): String? = withContext(Dispatchers.IO) {
    request("https://foobar.invalid/Switch.Set?id=0&on=true", "password")
}

fun request(url: String, password: String) : String {
    val authenticator = DigestAuthenticator(Credentials("admin", password))

    val authCache: Map<String, CachingAuthenticator> = ConcurrentHashMap()
    val client: OkHttpClient = OkHttpClient.Builder()
        .authenticator(CachingAuthenticatorDecorator(authenticator, authCache))
        .addInterceptor(AuthenticationCacheInterceptor(authCache))
        .build()

    val request: Request = Request.Builder()
        .url(url)
        .get()
        .build()
    val response = client.newCall(request).execute()

    return response.body?.string()!!
}