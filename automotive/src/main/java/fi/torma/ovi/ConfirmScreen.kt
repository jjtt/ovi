import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import fi.torma.ovi.MainScreen

class ConfirmScreen(carContext: CarContext, private val mainScreen: MainScreen) :
    Screen(carContext) {

    override fun onGetTemplate(): Template {
        return MessageTemplate.Builder("Confirm?")
            .setTitle("Switch on?")
            .addAction(
                Action.Builder()
                    .setTitle("Yes")
                    .setOnClickListener {
                        mainScreen.toggle = true
                        screenManager.pop()
                    }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("No")
                    .setOnClickListener {
                        mainScreen.toggle = false
                        screenManager.pop()
                    }
                    .build()
            )
            .build()
    }
}