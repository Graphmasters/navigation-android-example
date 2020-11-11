package net.graphmasters.routing.routing.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.style.layers.LineLayer
import com.mapbox.mapboxsdk.style.layers.Property
import com.mapbox.mapboxsdk.style.layers.PropertyFactory.*
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource
import kotlinx.android.synthetic.main.activity_main.*
import net.graphmasters.core.model.LatLng
import net.graphmasters.core.time.TimeProvider
import net.graphmasters.core.units.Length
import net.graphmasters.core.units.Speed
import net.graphmasters.routing.NavigationSdk
import net.graphmasters.routing.model.Route
import net.graphmasters.routing.navigation.state.NavigationStateProvider
import net.graphmasters.routing.navigation.state.NavigationStateProvider.NavigationState


class MainActivity : AppCompatActivity(), LocationListener,
    NavigationStateProvider.OnNavigationStateUpdatedListener {

    companion object {
        const val TAG = "MainActivity"

        const val LOCATION_PERMISSION_REQUEST_CODE = 1

        const val ROUTE_LAYER_ID = "route-layer"

        const val ROUTE_SOURCE_ID = "route-source"
    }

    private lateinit var locationManager: LocationManager

    private lateinit var mapboxMap: MapboxMap

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Mapbox.getInstance(this, BuildConfig.MAPBOX_TOKEN);

        setContentView(R.layout.activity_main)

        mapView?.onCreate(savedInstanceState)
        mapView?.getMapAsync { mapboxMap ->
            this.mapboxMap.setStyle(Style.MAPBOX_STREETS) {
                Log.d(TAG, "Map ready")
                this.mapboxMap = mapboxMap

                this.initRouteLayer(it)
                this.enableLocation()
            }
        }

        NavigationSdk.init(
            config = NavigationSdk.Config(
                username = BuildConfig.NUNAV_USERNAME,
                password = BuildConfig.NUNAV_PASSWORD,
                serviceUrl = BuildConfig.NUNAV_SERVICE_URL,
                instanceId = "dev"
            ),
            timeProvider = object : TimeProvider {
                override val currentTimeMillis: Long
                    get() {
                        return System.currentTimeMillis()
                    }
            },
            mainThreadExecutor = MainThreadExecutor(Handler())
        )

        NavigationSdk.navigationStateProvider.addOnNavigationStateUpdatedListener(this)
        NavigationSdk.navigationEngine.startNavigation()
    }

    private fun initRouteLayer(style: Style) {
        style.addSource(GeoJsonSource(ROUTE_SOURCE_ID))

        val lineLayer = LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
            lineColor("#0000ff"),
            lineWidth(10f),
            lineCap(Property.LINE_CAP_ROUND)
        )
        lineLayer.minZoom = 0f
        style.addLayer(
            lineLayer
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            1 -> {
                val permissionGranted =
                    grantResults.getOrElse(0) { PackageManager.PERMISSION_DENIED } == PackageManager.PERMISSION_GRANTED

                if (permissionGranted) {
                    this.enableLocation()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableLocation() {
        if (this.hasPermission()) {
            this.locationManager =
                this.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            this.locationManager.getProvider(LocationManager.GPS_PROVIDER)?.let {
                this.locationManager.requestLocationUpdates(
                    it.name,
                    0,
                    1f,
                    this
                )
            }
        } else {
            this.requestLocationPermission()
        }
    }

    private fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    public override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onStart() {
        super.onStart()
        mapView?.onStart()
    }

    override fun onStop() {
        super.onStop()
        mapView?.onStop()
    }

    public override fun onPause() {
        super.onPause()
        mapView?.onPause()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView?.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView?.onSaveInstanceState(outState)
    }

    override fun onLocationChanged(location: Location?) {
        Log.d(TAG, location?.toString())

        location?.let {
            NavigationSdk.updateLocation(
                location = net.graphmasters.routing.model.Location(
                    provider = it.provider,
                    timestamp = it.time,
                    latLng = LatLng(it.latitude, it.longitude),
                    altitude = if (it.hasAltitude()) Length.fromMeters(it.altitude) else Length.UNDEFINED,
                    heading = if (it.hasBearing()) it.bearing.toDouble() else null,
                    speed = if (it.hasSpeed()) Speed.fromMs(it.speed.toDouble()) else Speed.UNDEFINED,
                    accuracy = if (it.hasAccuracy()) Length.fromMeters(it.accuracy.toDouble()) else Length.UNDEFINED
                )
            )
        }
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        Log.d(TAG, "onStatusChanged $provider")
    }

    override fun onProviderEnabled(provider: String?) {
        Log.d(TAG, "onProviderEnabled $provider")
    }

    override fun onProviderDisabled(provider: String?) {
        Log.d(TAG, "onProviderDisabled $provider")
    }

    override fun onNavigationStateUpdated(navigationState: NavigationState) {
        navigationState.route?.let {
            this.drawRoute(it)
        }
    }

    private fun drawRoute(route: Route) {

    }
}