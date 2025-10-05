package fi.torma.ovi

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ItemList
import androidx.car.app.model.Template
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import java.time.LocalTime
import java.util.LinkedList

enum class DoorStatus {
    UNKNOWN, OPEN, CLOSED, INIT_ABORTED,
}

class MainScreen(carContext: CarContext) : Screen(carContext), DefaultLifecycleObserver {
    private val mainHandler = android.os.Handler(carContext.mainLooper)
    private lateinit var shellyPreferences: SharedPreferences
    private var shelly: Device = Shelly(
        carContext, screenManager, ::requestInvalidate, ::requestToast, ::requestNoRefreshOnResume
    )
    private var currentLocation: CurrentLocation = CurrentLocation()

    private var locationManager: LocationManager? = null
    private var noRefreshOnResume: Boolean = false
    private var logs: LinkedList<String> = LinkedList()

    fun requestInvalidate() {
        mainHandler.post {
            invalidate()
        }
    }

    fun requestToast(message: String) {
        logs.addFirst("[${LocalTime.now()}] $message")
        while (logs.size > 100) {
            logs.removeLast()
        }
    }

    fun requestNoRefreshOnResume() {
        noRefreshOnResume = true
    }

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)

        shellyPreferences = carContext.getSharedPreferences("Shelly", Context.MODE_PRIVATE)

        shelly.loadState(shellyPreferences)

        locationManager = carContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        if (ContextCompat.checkSelfPermission(
                carContext, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Permission is not granted
            // Request the permission
            requestToast("Requesting location permission")
            carContext.requestPermissions(
                listOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            ) { grantedPermissions, _ ->
                if (grantedPermissions.contains(android.Manifest.permission.ACCESS_FINE_LOCATION)) {
                    requestLocation()
                }
            }
        } else {
            requestLocation()
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestLocation() {
        currentLocation.addListener(shelly)
        locationManager?.requestLocationUpdates(
            LocationManager.GPS_PROVIDER, 30000L, 100f, currentLocation
        )
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        shelly.saveState(shellyPreferences)
        shelly.reset()
    }

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        if (noRefreshOnResume) {
            noRefreshOnResume = false
        } else {
            shelly.refresh()
        }
    }

    init {
        lifecycle.addObserver(this)
    }

    override fun onGetTemplate(): Template {
        val action = Action.Builder().setOnClickListener {
            screenManager.push(SettingsScreen(carContext, currentLocation))
        }.setTitle("Settings").build()

        val logs = Action.Builder().setOnClickListener {
            screenManager.push(LogsScreen(carContext, logs))
        }.setIcon(
            CarIcon.Builder(
                IconCompat.createWithResource(
                    carContext, R.drawable.heart0_24
                )
            ).build()
        ).build()

        val actionStrip: ActionStrip = ActionStrip.Builder()
            .addAction(action)
            .addAction(logs)
            .build()

        val listBuilder = ItemList.Builder()

        for (item in shelly.buildItems()) {
            listBuilder.addItem(item)
        }

        return GridTemplate.Builder().setTitle("Devices").setActionStrip(actionStrip)
            .setSingleList(listBuilder.build()).build()
    }
}
