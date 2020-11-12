package net.graphmasters.routing.routing.example.utils

import android.location.Location
import net.graphmasters.core.model.LatLng
import net.graphmasters.core.units.Length
import net.graphmasters.core.units.Speed

object EntityConverter {

    fun convert(location: Location): net.graphmasters.routing.model.Location {
        return net.graphmasters.routing.model.Location(
            provider = location.provider,
            timestamp = location.time,
            latLng = LatLng(location.latitude, location.longitude),
            altitude = if (location.hasAltitude()) Length.fromMeters(location.altitude) else Length.UNDEFINED,
            heading = if (location.hasBearing()) location.bearing.toDouble() else null,
            speed = if (location.hasSpeed()) Speed.fromMs(location.speed.toDouble()) else Speed.UNDEFINED,
            accuracy = if (location.hasAccuracy()) Length.fromMeters(location.accuracy.toDouble()) else Length.UNDEFINED
        )
    }

    fun convert(location: net.graphmasters.routing.model.Location): Location {
        val androidLocation = Location(location.provider)
        androidLocation.latitude = location.latLng.latitude
        androidLocation.longitude = location.latLng.longitude
        androidLocation.bearing = if (location.heading != null) location.heading!!.toFloat() else 0f
        androidLocation.speed = location.speed.ms().toFloat()
        androidLocation.accuracy = location.accuracy.meters().toFloat()

        return androidLocation
    }
}