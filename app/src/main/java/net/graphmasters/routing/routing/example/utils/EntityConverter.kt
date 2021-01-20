package net.graphmasters.routing.routing.example.utils

import android.location.Location as AndroidLocation
import net.graphmasters.multiplatform.core.location.Location
import net.graphmasters.multiplatform.core.model.LatLng
import net.graphmasters.multiplatform.core.units.Length
import net.graphmasters.multiplatform.core.units.Speed

object EntityConverter {

    fun convert(location: AndroidLocation): Location {
        return Location(
            provider = location.provider,
            timestamp = location.time,
            latLng = LatLng(location.latitude, location.longitude),
            altitude = if (location.hasAltitude()) Length.fromMeters(location.altitude) else Length.UNDEFINED,
            heading = if (location.hasBearing()) location.bearing.toDouble() else null,
            speed = if (location.hasSpeed()) Speed.fromMs(location.speed.toDouble()) else Speed.UNDEFINED,
            accuracy = if (location.hasAccuracy()) Length.fromMeters(location.accuracy.toDouble()) else Length.UNDEFINED
        )
    }

    fun convert(location: Location): AndroidLocation {
        val androidLocation = AndroidLocation(location.provider)
        androidLocation.latitude = location.latLng.latitude
        androidLocation.longitude = location.latLng.longitude
        androidLocation.bearing = if (location.heading != null) location.heading!!.toFloat() else 0f
        androidLocation.speed = location.speed.ms().toFloat()
        androidLocation.accuracy = location.accuracy.meters().toFloat()

        return androidLocation
    }
}