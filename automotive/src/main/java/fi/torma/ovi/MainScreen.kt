package fi.torma.ovi

import SettingsScreen
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ItemList
import androidx.car.app.model.Template
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import password


class MainScreen(carContext: CarContext) : Screen(carContext), DefaultLifecycleObserver {
    private var inputStatus: String? = null
    private var closeToDoor: Boolean? = false
    private var locationManager: LocationManager? = null
    private var locationListener: LocationListener? = null

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)


        locationManager = carContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        locationListener = LocationListener { location ->
            val targetLocation = Location("target").apply {
                latitude = 60.0
                longitude = 25.0
            }
            val distance = location.distanceTo(targetLocation)
            closeToDoor = distance < 30
        }

        if (ContextCompat.checkSelfPermission(
                carContext,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            )
            != PackageManager.PERMISSION_GRANTED
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

    private fun requestLocation() {
        locationManager?.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            10000L,
            10f,
            locationListener!!
        )
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        inputStatus = null
    }

    init {
        lifecycle.addObserver(this)
    }

    override fun onGetTemplate(): Template {
        fetchDoorStatus()

        val action = Action.Builder().setOnClickListener {
            screenManager.push(SettingsScreen(carContext))
        }
            .setTitle("Settings")
            .build()

        val actionStrip: ActionStrip = ActionStrip.Builder().addAction(action).build()

        val listBuilder = ItemList.Builder()

        val door = GridItem.Builder()
            .setTitle("Garage door")
        when (inputStatus) {
            null -> door.setLoading(true)
            """{"id":0,"state":true}""" -> door
                .setImage(
                    CarIcon.Builder(
                        IconCompat.createWithResource(carContext, R.drawable.baseline_door_front_24)
                    ).build()
                )
                .setOnClickListener(this::toggleDoor)

            """{"id":0,"state":false}""" -> door
                .setImage(
                    CarIcon.Builder(
                        IconCompat.createWithResource(
                            carContext,
                            R.drawable.baseline_meeting_room_24
                        )
                    ).build()
                )
                .setOnClickListener(this::toggleDoor)

            else -> {
                door.setLoading(true)
                CarToast.makeText(carContext, "Failed to fetch door status", CarToast.LENGTH_LONG)
                    .show()
            }
        }

        listBuilder.addItem(
            door.build()
        )

        return GridTemplate.Builder()
            .setTitle("Devices")
            .setActionStrip(actionStrip)
            .setSingleList(listBuilder.build())
            .build()
    }

    private fun fetchDoorStatus() {
        GlobalScope.launch {
            val newStatus = requestInputStatus(password(carContext))
            if (newStatus != inputStatus) {
                inputStatus = newStatus
                invalidate()
            }
        }
    }

    private fun toggleDoor() {
        if (closeToDoor == true) {
            GlobalScope.launch {
                requestSwitchOn(password(carContext))
                inputStatus = null
                invalidate()
            }
        } else {
            CarToast.makeText(
                carContext,
                "You are not close enough to the door",
                CarToast.LENGTH_LONG
            )
                .show()
        }
    }
}
