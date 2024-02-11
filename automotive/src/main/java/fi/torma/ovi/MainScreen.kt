package fi.torma.ovi

import SettingsScreen
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
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import password


class MainScreen(carContext: CarContext) : Screen(carContext), DefaultLifecycleObserver {
    private var inputStatus: String? = null
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

        var door = GridItem.Builder()
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
        GlobalScope.launch {
            requestSwitchOn(password(carContext))
            inputStatus = null
            invalidate()
        }
    }
}
