import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template

class ConfirmScreen(carContext: CarContext) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        return MessageTemplate.Builder("Confirm?").setTitle("Switch on?")
            .addAction(Action.Builder().setTitle("Yes").setOnClickListener {
                setResult(true)
                screenManager.pop()
            }.build()).addAction(Action.Builder().setTitle("No").setOnClickListener {
                setResult(false)
                screenManager.pop()
            }.build()).build()
    }
}