package fi.torma.ovi

import SettingsScreen
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationListener
import android.location.LocationManager
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ItemList
import androidx.car.app.model.Template
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import password

enum class DoorStatus {
    UNINITIALIZED, INITIALIZED, OPEN, CLOSED, INIT_ABORTED,
}

class MainScreen(carContext: CarContext) : Screen(carContext), DefaultLifecycleObserver {
    private var shelly: Shelly = Shelly(
        carContext, screenManager, ::invalidate
    ) // FIXME: Should be casted to superclass Device and not relying on knowing the implementation

    private var locationManager: LocationManager? = null
    private var locationListener: LocationListener? = null

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)

        locationManager = carContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        locationListener = shelly

        if (ContextCompat.checkSelfPermission(
                carContext, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Permission is not granted
            // Request the permission
            CarToast.makeText(carContext, "Requesting location permission", CarToast.LENGTH_LONG)
                .show()
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
        locationManager?.requestLocationUpdates(
            LocationManager.GPS_PROVIDER, 30000L, 100f, locationListener!!
        )
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        shelly.inputStatus = DoorStatus.UNINITIALIZED
    }

    init {
        lifecycle.addObserver(this)
    }

    override fun onGetTemplate(): Template {
        val invalidate = if (shelly.inputStatus == DoorStatus.UNINITIALIZED) {
            shelly.inputStatus = DoorStatus.INITIALIZED
            true
        } else {
            false
        }

        fetchDoorStatus()

        val action = Action.Builder().setOnClickListener {
            screenManager.push(SettingsScreen(carContext))
        }.setTitle("Settings").build()

        val actionStrip: ActionStrip = ActionStrip.Builder().addAction(action).build()

        val listBuilder = ItemList.Builder()

        for (item in shelly.buildItems()) {
            listBuilder.addItem(item)
        }

        if (invalidate) {
            GlobalScope.launch {
                delay(100)
                Log.d(
                    "MainScreen",
                    "Current thread ID: ${Thread.currentThread().id} - delayed call from onGetTemplate()"
                )
                invalidate()
            }
        }

        return GridTemplate.Builder().setTitle("Devices").setActionStrip(actionStrip)
            .setSingleList(listBuilder.build()).build()
    }


    private fun fetchDoorStatus() {
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
                if (newStatus != shelly.inputStatus) {
                    shelly.inputStatus = newStatus
                    Log.d(
                        "MainScreen",
                        "Current thread ID: ${Thread.currentThread().id} - door status has updated"
                    )
                    invalidate()
                }
            } catch (e: Exception) {
                Log.d("MainScreen", "Failed to fetch door status", e)
                CarToast.makeText(
                    carContext, "Door status error: " + e.message, CarToast.LENGTH_LONG
                ).show()
                shelly.inputStatus = DoorStatus.INIT_ABORTED
                Log.d(
                    "MainScreen",
                    "Current thread ID: ${Thread.currentThread().id} - door status update failed"
                )
                invalidate()
            }
        }
    }

}
