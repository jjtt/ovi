import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template

class SettingsScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        return MessageTemplate.Builder("Hello world!")
            .setHeaderAction(Action.BACK)
            .build()

        /*
        val signInMethod = SignInMethod.InputSignInMethod.Builder()
            .setInputAction(Action.Builder()
                .setOnClickListener {
                    // Handle the input here
                    SignInMethod.InputSignInMethod.INPUT_COMPLETED
                }
                .setTitle("Enter text")
                .build())
            .build()

        return SignInTemplate.Builder(signInMethod)
            .setTitle("Sign In")
            .setHeaderAction(Action.APP_ICON)
            .build()

         */
    }
}