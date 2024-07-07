import android.content.Context
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.InputCallback
import androidx.car.app.model.Template
import androidx.car.app.model.signin.InputSignInMethod
import androidx.car.app.model.signin.SignInTemplate

class SettingsScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        val callback = object : InputCallback {
            override fun onInputSubmitted(text: String) {
                val sharedPreferences =
                    carContext.getSharedPreferences("MyApp", Context.MODE_PRIVATE)
                sharedPreferences.edit().putString("password", text).apply()
            }
        }

        val passwordInput = InputSignInMethod.Builder(callback).setHint("Password")
            .setDefaultValue(password(carContext))
            .setInputType(InputSignInMethod.INPUT_TYPE_PASSWORD).build()

        return SignInTemplate.Builder(passwordInput).setTitle("Settings")
            .setInstructions("Enter password for device").setHeaderAction(Action.BACK).build()
    }
}

fun password(carContext: CarContext): String {
    val sharedPreferences = carContext.getSharedPreferences("MyApp", Context.MODE_PRIVATE)
    return sharedPreferences.getString("password", "") ?: ""
}
