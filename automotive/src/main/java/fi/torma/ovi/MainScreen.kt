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
import java.util.concurrent.atomic.AtomicInteger

enum class DoorStatus {
    INITIALIZED, OPEN, CLOSED, INIT_ABORTED,
}

class MainScreen(carContext: CarContext) : Screen(carContext), DefaultLifecycleObserver {
    private val mainHandler = android.os.Handler(carContext.mainLooper)
    private var shelly: Shelly = Shelly(
        carContext, screenManager, ::requestInvalidate, ::requestToast
    ) // FIXME: Should be casted to superclass Device and not relying on knowing the implementation

    private var locationManager: LocationManager? = null
    private var locationListener: LocationListener? = null
    private val toastCounter: AtomicInteger = AtomicInteger(0)

    fun requestInvalidate() {
        mainHandler.post {
            Log.d(
                "MainScreen",
                "Current thread ID: ${Thread.currentThread().id} - invalidate() requested"
            )
            invalidate()
        }
    }

    fun requestToast(message: String) {
        CarToast.makeText(
            carContext, "${toastCounter.incrementAndGet()}: $message", CarToast.LENGTH_LONG
        ).show()
    }

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
        locationManager?.requestLocationUpdates(
            LocationManager.GPS_PROVIDER, 30000L, 100f, locationListener!!
        )
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        shelly.inputStatus = DoorStatus.INITIALIZED
    }

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        shelly.refresh()
    }

    init {
        lifecycle.addObserver(this)
    }

    override fun onGetTemplate(): Template {
        val action = Action.Builder().setOnClickListener {
            screenManager.push(SettingsScreen(carContext))
        }.setTitle("Settings").build()

        val actionStrip: ActionStrip = ActionStrip.Builder().addAction(action).build()

        val listBuilder = ItemList.Builder()

        for (item in shelly.buildItems()) {
            listBuilder.addItem(item)
        }

        return GridTemplate.Builder().setTitle("Devices").setActionStrip(actionStrip)
            .setSingleList(listBuilder.build()).build()
    }
}
