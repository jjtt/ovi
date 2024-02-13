package fi.torma.ovi

import com.burgstaller.okhttp.AuthenticationCacheInterceptor
import com.burgstaller.okhttp.CachingAuthenticatorDecorator
import com.burgstaller.okhttp.digest.CachingAuthenticator
import com.burgstaller.okhttp.digest.Credentials
import com.burgstaller.okhttp.digest.DigestAuthenticator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap


suspend fun requestInputStatus(password: String): String? = withContext(Dispatchers.IO) {
    request("https://foobar.invalid/Input.GetStatus?id=0", password)
}

suspend fun requestSwitchOn(password: String): String? = withContext(Dispatchers.IO) {
    request("https://foobar.invalid/Switch.Set?id=0&on=true", password)
}

suspend fun request(url: String, password: String): String? {
    return withContext(Dispatchers.IO) {
        withTimeout(10000) {
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

            response.body?.string()
        }
    }
}