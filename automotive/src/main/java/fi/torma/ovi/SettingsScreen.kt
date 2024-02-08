package fi.torma.ovi

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template

class SettingsScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        val row: Row = Row.Builder()
            .setTitle("Welcome to the new screen!").build()

        return PaneTemplate.Builder(
            Pane.Builder()
                .addRow(row)
                .build()
        )
            .setHeaderAction(Action.APP_ICON)
            .build()
    }
}

