package net.graphmasters.navigation.example.utils

import com.mapbox.geojson.Feature
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import android.location.Location as AndroidLocation
import net.graphmasters.multiplatform.core.location.Location
import net.graphmasters.multiplatform.core.model.LatLng
import net.graphmasters.multiplatform.core.units.Length
import net.graphmasters.multiplatform.core.units.Speed
import net.graphmasters.multiplatform.navigation.ui.camera.CameraUpdate

object EntityConverter {

    fun convert(location: AndroidLocation): Location = Location(
        provider = location.provider ?: "no-provider",
        timestamp = location.time,
        latLng = LatLng(location.latitude, location.longitude),
        altitude = if (location.hasAltitude()) Length.fromMeters(location.altitude) else null,
        heading = if (location.hasBearing()) location.bearing.toDouble() else null,
        speed = if (location.hasSpeed()) Speed.fromMs(location.speed.toDouble()) else null,
        accuracy = if (location.hasAccuracy()) Length.fromMeters(location.accuracy.toDouble()) else null
    )

    fun convert(location: Location): AndroidLocation = AndroidLocation(location.provider).apply {
        this.latitude = location.latLng.latitude
        this.longitude = location.latLng.longitude

        location.heading?.let { this.bearing = it.toFloat() }
        location.speed?.let { this.speed = it.ms().toFloat() }
        location.accuracy?.let { this.accuracy = it.meters().toFloat() }
    }

    fun convert(cameraUpdate: CameraUpdate): com.mapbox.mapboxsdk.camera.CameraUpdate {
        val builder = CameraPosition.Builder()

        builder.target(
            com.mapbox.mapboxsdk.geometry.LatLng(
                cameraUpdate.latLng.latitude,
                cameraUpdate.latLng.longitude
            )
        )
        cameraUpdate.zoom?.let { builder.zoom(it) }
        cameraUpdate.tilt?.let { builder.tilt(it) }
        cameraUpdate.bearing?.let { builder.bearing(it) }
        cameraUpdate.padding.let {
            builder.padding(
                it.left.toDouble(),
                it.top.toDouble(),
                it.right.toDouble(),
                it.bottom.toDouble(),
            )
        }

        return CameraUpdateFactory.newCameraPosition(builder.build())
    }

    fun convert(polyline: List<LatLng>): Feature =
        Feature.fromGeometry(LineString.fromLngLats(polyline.map {
            Point.fromLngLat(it.longitude, it.latitude)
        }))
}