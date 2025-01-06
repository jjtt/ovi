package fi.torma.ovi

import ConfirmScreen
import android.content.SharedPreferences
import android.location.Location
import androidx.car.app.CarContext
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
import kotlinx.coroutines.Job
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
    private val requestInvalidate: () -> Unit,
    private val requestToast: (String) -> Unit,
    private val requestNoRefreshOnResume: () -> Unit
) : Device() {

    var inputStatus: DoorStatus = DoorStatus.UNKNOWN
    var closeToDoor: Boolean = false
    var statusJob: Job? = null
    var doorJob: Job? = null

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
            refresh()
        }
    }

    private fun buildDoor(): GridItem {
        val door = GridItem.Builder().setTitle("Garage door")
        when (inputStatus) {
            DoorStatus.UNKNOWN -> door.setLoading(true)
            DoorStatus.CLOSED -> door.setImage(
                CarIcon.Builder(
                    IconCompat.createWithResource(
                        carContext,
                        if (closeToDoor) R.drawable.baseline_door_front_24 else R.drawable.outline_door_front_24
                    )
                ).build()
            ).setOnClickListener(this::toggleDoor)

            DoorStatus.OPEN -> door.setImage(
                CarIcon.Builder(
                    IconCompat.createWithResource(
                        carContext,
                        if (closeToDoor) R.drawable.baseline_meeting_room_24 else R.drawable.outline_meeting_room_24
                    )
                ).build()
            ).setOnClickListener(this::toggleDoor)

            DoorStatus.INIT_ABORTED -> door.setImage(
                CarIcon.Builder(
                    IconCompat.createWithResource(
                        carContext,
                        if (closeToDoor) R.drawable.baseline_no_meeting_room_24 else R.drawable.outline_no_meeting_room_24
                    )
                ).build()
            ).setOnClickListener(this::toggleDoor)
        }
        return door.build()
    }

    private fun buildRefresh(): GridItem {
        val refresh = GridItem.Builder().setTitle("Refresh")

        when (inputStatus) {
            DoorStatus.UNKNOWN -> {
                refresh.setImage(
                    CarIcon.Builder(
                        IconCompat.createWithResource(carContext, R.drawable.baseline_cancel_24)
                    ).build()
                ).setOnClickListener {
                    inputStatus = DoorStatus.INIT_ABORTED
                    requestInvalidate()
                }
            }

            else -> {
                refresh.setImage(
                    CarIcon.Builder(
                        IconCompat.createWithResource(carContext, R.drawable.baseline_refresh_24)
                    ).build()
                ).setOnClickListener {
                    refresh()
                }
            }
        }
        return refresh.build()
    }

    private fun toggleDoor() {
        if (closeToDoor == true) {
            val listener = OnScreenResultListener { result ->
                if (result == true) {
                    doorJob?.cancel()
                    requestNoRefreshOnResume()
                    inputStatus = DoorStatus.UNKNOWN
                    doorJob = GlobalScope.launch(Dispatchers.IO) {
                        try {
                            val response = requestSwitchOn(password(carContext))
                            if (response != null) {
                                requestToast("Door operating")
                            }
                        } catch (e: Exception) {
                            //Log.d("Shelly", "Failed to open door", e)
                            requestToast("Failed to open door: ${e.message}")
                        }
                        refresh()
                    }
                }
            }

            screenManager.pushForResult(ConfirmScreen(carContext), listener)
        } else {
            requestToast("You are not close enough to the door")
        }
    }

    override fun reset() {
        inputStatus = DoorStatus.UNKNOWN
    }

    override fun saveState(sharedPreferences: SharedPreferences) {
        with(sharedPreferences.edit()) {
            putBoolean("closeToDoor", closeToDoor)
            apply()
        }
    }

    override fun loadState(sharedPreferences: SharedPreferences) {
        closeToDoor = sharedPreferences.getBoolean("closeToDoor", false)
    }

    override fun refresh() {
        inputStatus = DoorStatus.UNKNOWN
        requestInvalidate()
        statusJob?.cancel()
        statusJob = GlobalScope.launch(Dispatchers.IO) {
            try {
                //Log.d("Shelly", "Fetching door status")
                val status = requestInputStatus(password(carContext))
                //Log.d("Shelly", "Door status: $status")
                val newStatus = when (status) {
                    """{"id":0,"state":true}""" -> DoorStatus.CLOSED
                    """{"id":0,"state":false}""" -> DoorStatus.OPEN
                    else -> DoorStatus.INIT_ABORTED
                }
                if (newStatus != inputStatus) {
                    inputStatus = newStatus
                    requestInvalidate()
                }
            } catch (e: Exception) {
                //Log.d("Shelly", "Failed to fetch door status", e)
                requestToast("Door status error: ${e.message}")
                inputStatus = DoorStatus.INIT_ABORTED
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
    return withTimeoutOrNull(1000) {
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