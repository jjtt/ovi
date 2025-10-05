package fi.torma.ovi

import android.content.Context
import android.location.Location
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.InputCallback
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.model.signin.InputSignInMethod
import androidx.car.app.model.signin.SignInTemplate
import androidx.car.app.model.Header

private const val PASSWORD = "password"
private const val BASE_URL = "base_url"
private const val LAT_LON = "lat_lon"

class SettingsScreen(carContext: CarContext) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val sharedPreferences = carContext.getSharedPreferences("MyApp", Context.MODE_PRIVATE)

        val baseUrlRow = Row.Builder()
            .setTitle("Base URL")
            .addText(sharedPreferences.getString(BASE_URL, "") ?: "")
            .setBrowsable(true)
            .setOnClickListener {
                screenManager.push(
                    buildInputScreen(
                        BASE_URL,
                        "Enter base URL for accessing the door",
                        "https://mydoor.example.invalid/garage01"
                    )
                )
            }
            .build()

        val passwordRow = Row.Builder()
            .setTitle("Password")
            .setBrowsable(true)
            .setOnClickListener {
                screenManager.push(
                    buildInputScreen(
                        PASSWORD,
                        "Enter password for accessing the door",
                        "Password",
                        InputSignInMethod.INPUT_TYPE_PASSWORD
                    )
                )
            }
            .build()

        val latLonRow = Row.Builder()
            .setTitle("Door location")
            .addText(sharedPreferences.getString(LAT_LON, "") ?: "")
            .setBrowsable(true)
            .setOnClickListener {
                screenManager.push(
                    buildInputScreen(
                        LAT_LON,
                        "Enter Lat,Lon coordinates of the door",
                        "60.123,24.456"
                    )
                )
            }
            .build()

        val itemList = ItemList.Builder()
            .addItem(baseUrlRow)
            .addItem(passwordRow)
            .addItem(latLonRow)
            .build()

        return ListTemplate.Builder()
            .setSingleList(itemList)
            .setHeader(
                Header.Builder()
                    .setTitle("Settings")
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .build()
    }

    private fun buildInputScreen(
        key: String,
        title: String,
        hint: String,
        inputType: Int = InputSignInMethod.INPUT_TYPE_DEFAULT
    ): Screen {
        return object : Screen(carContext) {
            override fun onGetTemplate(): Template {
                val callback = object : InputCallback {
                    override fun onInputSubmitted(text: String) {
                        carContext.getSharedPreferences("MyApp", Context.MODE_PRIVATE)
                            .edit()
                            .putString(key, text)
                            .apply()
                        screenManager.pop()
                    }
                }

                val inputBuilder = InputSignInMethod.Builder(callback)
                    .setHint(hint)
                    .setDefaultValue(
                        carContext.getSharedPreferences("MyApp", Context.MODE_PRIVATE)
                            .getString(key, "") ?: ""
                    )

                if (inputType != InputSignInMethod.INPUT_TYPE_DEFAULT) {
                    inputBuilder.setInputType(inputType)
                }

                return SignInTemplate.Builder(inputBuilder.build())
                    .setTitle(title)
                    .setHeaderAction(Action.BACK)
                    .build()
            }
        }
    }
}

fun password(carContext: CarContext): String {
    val sharedPreferences = carContext.getSharedPreferences("MyApp", Context.MODE_PRIVATE)
    return sharedPreferences.getString(PASSWORD, "") ?: ""
}

fun baseUrl(carContext: CarContext): String {
    val sharedPreferences = carContext.getSharedPreferences("MyApp", Context.MODE_PRIVATE)
    return sharedPreferences.getString(BASE_URL, "") ?: ""
}

fun doorLocation(carContext: CarContext): Location? {
    val sharedPreferences = carContext.getSharedPreferences("MyApp", Context.MODE_PRIVATE)
    val latLonString = sharedPreferences.getString(LAT_LON, null) ?: return null

    return try {
        val parts = latLonString.split(",")
        if (parts.size == 2) {
            val lat = parts[0].trim().toDouble()
            val lon = parts[1].trim().toDouble()
            Location("target").apply {
                latitude = lat
                longitude = lon
            }
        } else {
            null
        }
    } catch (e: NumberFormatException) {
        null
    }
}