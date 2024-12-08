package fi.torma.ovi

import android.location.LocationListener
import androidx.car.app.model.GridItem

abstract class Device : LocationListener {
    abstract fun buildItems(): List<GridItem>
}