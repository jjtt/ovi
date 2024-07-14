package fi.torma.ovi

import ConfirmScreen
import SettingsScreen
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.OnScreenResultListener
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import password

enum class DoorStatus {
    UNINITIALIZED, INITIALIZED, OPEN, CLOSED, INIT_ABORTED,
}

class MainScreen(carContext: CarContext) : Screen(carContext), DefaultLifecycleObserver {
    private var inputStatus: DoorStatus = DoorStatus.UNINITIALIZED
    private var closeToDoor: Boolean = false
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
            if (closeToDoor != (distance < 200)) {
                closeToDoor = distance < 200
                inputStatus = DoorStatus.INITIALIZED
                invalidate()
            }
        }

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

    private fun requestLocation() {
        locationManager?.requestLocationUpdates(
            LocationManager.GPS_PROVIDER, 30000L, 100f, locationListener!!
        )
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        inputStatus = DoorStatus.UNINITIALIZED
    }

    init {
        lifecycle.addObserver(this)
    }

    override fun onGetTemplate(): Template {
        val invalidate = if (inputStatus == DoorStatus.UNINITIALIZED) {
            inputStatus = DoorStatus.INITIALIZED
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

        val door = GridItem.Builder().setTitle("Garage door")
        when (inputStatus) {
            DoorStatus.UNINITIALIZED -> CarToast.makeText(
                carContext, "Door status uninitialized, this is a bug", CarToast.LENGTH_LONG
            ).show()

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

        val refresh = GridItem.Builder().setTitle("Refresh")

        when (inputStatus) {
            DoorStatus.INITIALIZED -> {
                refresh.setImage(
                    CarIcon.Builder(
                        IconCompat.createWithResource(carContext, R.drawable.baseline_cancel_24)
                    ).build()
                ).setOnClickListener {
                    inputStatus = DoorStatus.INIT_ABORTED
                    invalidate()
                }
            }

            else -> {
                refresh.setImage(
                    CarIcon.Builder(
                        IconCompat.createWithResource(carContext, R.drawable.baseline_refresh_24)
                    ).build()
                ).setOnClickListener {
                    inputStatus = DoorStatus.INITIALIZED
                    invalidate()
                }
            }
        }

        listBuilder.addItem(
            door.build()
        ).addItem(
            refresh.build()
        )

        if (invalidate) {
            GlobalScope.launch {
                delay(100)
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
                if (newStatus != inputStatus) {
                    inputStatus = newStatus
                    invalidate()
                }
            } catch (e: Exception) {
                Log.d("MainScreen", "Failed to fetch door status", e)
                CarToast.makeText(
                    carContext, "Door status error: " + e.message, CarToast.LENGTH_LONG
                ).show()
                inputStatus = DoorStatus.INIT_ABORTED
                invalidate()
            }
        }
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
                                invalidate()
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
}
