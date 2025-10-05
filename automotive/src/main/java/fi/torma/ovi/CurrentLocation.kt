package fi.torma.ovi

import android.location.Location
import android.location.LocationListener

class CurrentLocation : LocationListener {
    private var location: Location? = null
    private var listeners: MutableSet<LocationListener> = mutableSetOf()

    fun addListener(listener: LocationListener) {
        listeners.add(listener)
    }

    fun getLocation(): Location? {
        return location
    }

    override fun onLocationChanged(location: Location) {
        this.location = location
        callListeners()
    }

    fun callListeners() {
        val location = this.location
        if (location != null) {
            listeners.forEach {
                it.onLocationChanged(location)
            }
        }
    }
}