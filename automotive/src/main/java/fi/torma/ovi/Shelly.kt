package fi.torma.ovi

import ConfirmScreen
import android.location.Location
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.OnScreenResultListener
import androidx.car.app.ScreenManager
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridItem
import androidx.core.graphics.drawable.IconCompat
import com.burgstaller.okhttp.AuthenticationCacheInterceptor
import com.burgstaller.okhttp.CachingAuthenticatorDecorator
import com.burgstaller.okhttp.digest.CachingAuthenticator
import com.burgstaller.okhttp.digest.Credentials
import com.burgstaller.okhttp.digest.DigestAuthenticator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import password
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

class Shelly(
    private val carContext: CarContext,
    private val screenManager: ScreenManager,
    private val requestInvalidate: () -> Unit
) : Device() {

    var inputStatus: DoorStatus = DoorStatus.INITIALIZED
    var closeToDoor: Boolean = false

    init {
        refresh()
    }

    override fun buildItems(): List<GridItem> {
        return listOf(buildDoor(), buildRefresh())
    }

    override fun onLocationChanged(location: Location) {
        val targetLocation = Location("target").apply {
            latitude = 60.0
            longitude = 25.0
        }
        val distance = location.distanceTo(targetLocation)
        if (closeToDoor != (distance < 200)) {
            closeToDoor = distance < 200
            inputStatus = DoorStatus.INITIALIZED
            Log.d("Shelly", "Current thread ID: ${Thread.currentThread().id}")
            requestInvalidate()
        }
    }

    private fun buildDoor(): GridItem {
        val door = GridItem.Builder().setTitle("Garage door")
        when (inputStatus) {
            DoorStatus.INITIALIZED -> door.setLoading(true)
            DoorStatus.CLOSED -> door.setImage(
                CarIcon.Builder(
                    IconCompat.createWithResource(carContext, R.drawable.baseline_door_front_24)
                ).build()
            ).setOnClickListener(this::toggleDoor)

            DoorStatus.OPEN -> door.setImage(
                CarIcon.Builder(
                    IconCompat.createWithResource(
                        carContext, R.drawable.baseline_meeting_room_24
                    )
                ).build()
            ).setOnClickListener(this::toggleDoor)

            DoorStatus.INIT_ABORTED -> door.setImage(
                CarIcon.Builder(
                    IconCompat.createWithResource(
                        carContext, R.drawable.baseline_no_meeting_room_24
                    )
                ).build()
            ).setOnClickListener(this::toggleDoor)
        }
        return door.build()
    }

    private fun buildRefresh(): GridItem {
        val refresh = GridItem.Builder().setTitle("Refresh")

        when (inputStatus) {
            DoorStatus.INITIALIZED -> {
                refresh.setImage(
                    CarIcon.Builder(
                        IconCompat.createWithResource(carContext, R.drawable.baseline_cancel_24)
                    ).build()
                ).setOnClickListener {
                    inputStatus = DoorStatus.INIT_ABORTED
                    Log.d("Shelly", "Current thread ID: ${Thread.currentThread().id}")
                    requestInvalidate()
                }
            }

            else -> {
                refresh.setImage(
                    CarIcon.Builder(
                        IconCompat.createWithResource(carContext, R.drawable.baseline_refresh_24)
                    ).build()
                ).setOnClickListener {
                    inputStatus = DoorStatus.INITIALIZED
                    Log.d("Shelly", "Current thread ID: ${Thread.currentThread().id}")
                    refresh()
                    requestInvalidate()
                }
            }
        }
        return refresh.build()
    }

    private fun toggleDoor() {
        if (closeToDoor == true) {
            val listener = OnScreenResultListener { result ->
                if (result == true) {
                    GlobalScope.launch {
                        try {
                            val response = requestSwitchOn(password(carContext))
                            if (response != null) {
                                CarToast.makeText(
                                    carContext, "Door operating", CarToast.LENGTH_LONG
                                ).show()
                                inputStatus = DoorStatus.INITIALIZED
                                Log.d("Shelly", "Current thread ID: ${Thread.currentThread().id}")
                                requestInvalidate()
                            }
                        } catch (e: Exception) {
                            Log.d("MainScreen", "Failed to open door", e)
                            CarToast.makeText(
                                carContext,
                                "Failed to open door: " + e.message,
                                CarToast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }

            screenManager.pushForResult(ConfirmScreen(carContext), listener)
        } else {
            CarToast.makeText(
                carContext, "You are not close enough to the door", CarToast.LENGTH_LONG
            ).show()
        }
    }

    override fun refresh() {
        GlobalScope.launch {
            try {
                Log.d("MainScreen", "Fetching door status")
                val status = requestInputStatus(password(carContext))
                Log.d("MainScreen", "Door status: $status")
                val newStatus = when (status) {
                    """{"id":0,"state":true}""" -> DoorStatus.CLOSED
                    """{"id":0,"state":false}""" -> DoorStatus.OPEN
                    else -> DoorStatus.INIT_ABORTED
                }
                if (newStatus != inputStatus) {
                    inputStatus = newStatus
                    Log.d(
                        "Shelly",
                        "Current thread ID: ${Thread.currentThread().id} - door status has updated"
                    )
                    requestInvalidate()
                }
            } catch (e: Exception) {
                Log.d("MainScreen", "Failed to fetch door status", e)
                CarToast.makeText(
                    carContext, "Door status error: " + e.message, CarToast.LENGTH_LONG
                ).show()
                inputStatus = DoorStatus.INIT_ABORTED
                Log.d(
                    "Shelly",
                    "Current thread ID: ${Thread.currentThread().id} - door status update failed"
                )
                requestInvalidate()
            }
        }
    }
}


suspend fun requestInputStatus(password: String): String? = withContext(Dispatchers.IO) {
    request("https://foobar.invalid/Input.GetStatus?id=0", password)
}

suspend fun requestSwitchOn(password: String): String? = withContext(Dispatchers.IO) {
    request("https://foobar.invalid/Switch.Set?id=0&on=true", password)
}

suspend fun request(url: String, password: String): String? {
    return withContext(Dispatchers.IO) {
        withTimeoutOrNull(1000) {
            val authenticator = DigestAuthenticator(Credentials("admin", password))

            val authCache: Map<String, CachingAuthenticator> = ConcurrentHashMap()
            val client: OkHttpClient = OkHttpClient.Builder().callTimeout(Duration.ofSeconds(1))
                .connectTimeout(Duration.ofSeconds(1)).readTimeout(Duration.ofSeconds(1))
                .writeTimeout(Duration.ofSeconds(1))
                .authenticator(CachingAuthenticatorDecorator(authenticator, authCache))
                .addInterceptor(AuthenticationCacheInterceptor(authCache)).build()

            val request: Request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()

            response.body?.string()
        }
    }
}