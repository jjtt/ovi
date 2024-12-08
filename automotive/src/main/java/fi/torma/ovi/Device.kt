package fi.torma.ovi

import android.content.SharedPreferences
import android.location.LocationListener
import androidx.car.app.model.GridItem

abstract class Device : LocationListener {
    abstract fun buildItems(): List<GridItem>
    abstract fun refresh()
    abstract fun reset()

    abstract fun saveState(sharedPreferences: SharedPreferences)
    abstract fun loadState(sharedPreferences: SharedPreferences)
}