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
import okhttp3.OkHttpClient
import okhttp3.Request
import password
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

private const val BASE_URL = "https://foobar.invalid"

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

    private val authStats = RequestTimingStatistics();
    private val clientStats = RequestTimingStatistics();
    private val requestStats = RequestTimingStatistics();

    override fun buildItems(): List<GridItem> {
        return listOf(buildDoor(), buildRefresh(),
            buildStats("min: ${authStats.getMinDuration() / 1000000} ms", R.drawable.heart1_24),
            buildStats("avg: ${authStats.getAverageDuration()/ 1000000} ms", R.drawable.heart1_24),
            buildStats("max: ${authStats.getMaxDuration()/ 1000000} ms", R.drawable.heart1_24),
            buildStats("req: ${authStats.getNumberOfRequests()}", R.drawable.heart1_24),
            buildStats("min: ${clientStats.getMinDuration() / 1000000} ms", R.drawable.outline_door_front_24),
            buildStats("avg: ${clientStats.getAverageDuration()/ 1000000} ms", R.drawable.outline_door_front_24),
            buildStats("max: ${clientStats.getMaxDuration()/ 1000000} ms", R.drawable.outline_door_front_24),
            buildStats("req: ${clientStats.getNumberOfRequests()}", R.drawable.outline_door_front_24),
            buildStats("min: ${requestStats.getMinDuration() / 1000000} ms", R.drawable.heart1_24),
            buildStats("avg: ${requestStats.getAverageDuration()/ 1000000} ms", R.drawable.heart1_24),
            buildStats("max: ${requestStats.getMaxDuration()/ 1000000} ms", R.drawable.heart1_24),
            buildStats("req: ${requestStats.getNumberOfRequests()}", R.drawable.heart1_24),
        )
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

    private fun buildStats(title: String, icon: Int): GridItem {
        return GridItem.Builder()
            .setTitle(title)
            .setImage(
                CarIcon.Builder(
                    IconCompat.createWithResource(carContext, icon)
                ).build()
            )
            .build()
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
                            requestSwitchOn(password(carContext))
                            requestToast("Door operating")
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

    private fun requestInputStatus(password: String): String? {
        return request("$BASE_URL/Input.GetStatus?id=0", password)
    }

    private fun requestSwitchOn(password: String): String? {
        return request("$BASE_URL/Switch.Set?id=0&on=true", password)
    }

    private fun request(url: String, password: String): String? {
        val start = System.nanoTime()
        val authenticator = DigestAuthenticator(Credentials("admin", password))

        val beforeClient = System.nanoTime()
        val authCache: Map<String, CachingAuthenticator> = ConcurrentHashMap()
        val client: OkHttpClient = OkHttpClient.Builder().callTimeout(Duration.ofSeconds(1))
            .connectTimeout(Duration.ofSeconds(1)).readTimeout(Duration.ofSeconds(1))
            .writeTimeout(Duration.ofSeconds(1))
            .authenticator(CachingAuthenticatorDecorator(authenticator, authCache))
            .addInterceptor(AuthenticationCacheInterceptor(authCache)).build()

        val afterClient = System.nanoTime()
        val request: Request = Request.Builder().url(url).get().build()
        val response = client.newCall(request).execute()

        return response.body?.string()
            .also {
                authStats.addDuration(beforeClient - start)
                clientStats.addDuration(afterClient - beforeClient)
                requestStats.addDuration(System.nanoTime() - afterClient)
            }
    }
}



class RequestTimingStatistics {
    private val durations = mutableListOf<Long>()

    fun addDuration(duration: Long) {
        durations.add(duration)
    }

    fun getAverageDuration(): Long {
        return durations.average().toLong()
    }

    fun getMinDuration(): Long {
        return durations.minOrNull() ?: 0
    }

    fun getMaxDuration(): Long {
        return durations.maxOrNull() ?: 0
    }

    fun getNumberOfRequests(): Int {
        return durations.size
    }

    fun clear() {
        durations.clear()
    }
}