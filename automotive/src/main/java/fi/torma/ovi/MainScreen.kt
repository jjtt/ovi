package fi.torma.ovi

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template


class MainScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        val row: Row = Row.Builder()
            .setTitle("Terve, #badgamers").build()

        val toast: () -> Unit = {
            CarToast.makeText(carContext, "Moi!", CarToast.LENGTH_SHORT).show()
        }
        val action = Action.Builder().setOnClickListener(toast)
            .setTitle("Sano moi")
            .build()

        val actionStrip: ActionStrip = ActionStrip.Builder().addAction(action).build()

        return PaneTemplate.Builder(
            Pane.Builder()
                .addRow(row)
                .build()
        )
            .setActionStrip(actionStrip)
            .setHeaderAction(Action.APP_ICON)
            .build()
    }
}