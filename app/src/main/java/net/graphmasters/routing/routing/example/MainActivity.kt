package net.graphmasters.routing.routing.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.mapbox.geojson.Feature
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
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
import net.graphmasters.routing.model.Routable
import net.graphmasters.routing.model.Route
import net.graphmasters.routing.navigation.events.NavigationEventHandler
import net.graphmasters.routing.navigation.route.RouteRepository
import net.graphmasters.routing.navigation.route.provider.RouteProvider
import net.graphmasters.routing.navigation.state.NavigationStateProvider
import net.graphmasters.routing.navigation.state.NavigationStateProvider.NavigationState
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor


class MainActivity : AppCompatActivity(), LocationListener,
    NavigationStateProvider.OnNavigationStateUpdatedListener, MapboxMap.OnMapLongClickListener,
    NavigationEventHandler.OnNavigationStartedListener,
    NavigationEventHandler.OnNavigationStoppedListener,
    NavigationEventHandler.OnDestinationReachedListener,
    NavigationEventHandler.OnRouteUpdateListener,
    NavigationEventHandler.OnRouteRequestFailedListener,
    NavigationStateProvider.OnNavigationStateInitializedListener,
    RouteRepository.RouteUpdatedListener {

    enum class CameraState {
        FREE, FOLLOWING
    }

    companion object {
        const val TAG = "MainActivity"

        const val LOCATION_PERMISSION_REQUEST_CODE = 1

        const val ROUTE_OUTLINE_LAYER_ID = "route-outline-layer"

        const val ROUTE_LINE_LAYER_ID = "route-layer"

        const val ROUTE_SOURCE_ID = "route-source"
    }

    private lateinit var routeSource: GeoJsonSource

    private lateinit var navigationSdk: NavigationSdk

    private lateinit var locationManager: LocationManager

    private var mapboxMap: MapboxMap? = null

    private var lastLocation: Location? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Mapbox.getInstance(this, BuildConfig.MAPBOX_TOKEN);

        setContentView(R.layout.activity_main)

        mapView?.onCreate(savedInstanceState)
        mapView?.getMapAsync { mapboxMap ->
            mapboxMap.setStyle(Style.MAPBOX_STREETS) {
                Log.d(TAG, "Map ready")
                this.mapboxMap = mapboxMap
                mapboxMap.addOnMapLongClickListener(this)

                this.initRouteLayer(it)
                this.enableLocation()
            }
        }

        this.navigationSdk = NavigationSdk(
            config = NavigationSdk.Config(
                username = BuildConfig.NUNAV_USERNAME,
                password = BuildConfig.NUNAV_PASSWORD,
                serviceUrl = "https://nunav-android-bff-routing.graphmasters.net/v2/routing/",
                instanceId = "dev"
            ),
            timeProvider = object : TimeProvider {
                override val currentTimeMillis: Long
                    get() {
                        return System.currentTimeMillis()
                    }
            },
            executorProvider = AndroidExecutorProvider()
        )

        navigationSdk.navigationStateProvider.addOnNavigationStateUpdatedListener(this)
        navigationSdk.navigationStateProvider.addOnNavigationStateInitializedListener(this)

        navigationSdk.routeRepository.addRouteUpdatedListener(this)

        navigationSdk.navigationEngine.navigationEventHandler.addOnNavigationStartedListener(this)
        navigationSdk.navigationEngine.navigationEventHandler.addOnNavigationStoppedListener(this)
        navigationSdk.navigationEngine.navigationEventHandler.addOnDestinationReachedListener(this)
        navigationSdk.navigationEngine.navigationEventHandler.addOnRouteUpdateListener(this)
        navigationSdk.navigationEngine.navigationEventHandler.addOnRouteRequestFailedListener(this)
    }

    private fun initRouteLayer(style: Style) {
        this.routeSource = GeoJsonSource(ROUTE_SOURCE_ID)
        style.addSource(this.routeSource)

        style.addLayer(
            LineLayer(ROUTE_OUTLINE_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
                lineColor("#005f97"),
                lineWidth(10f),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND)
            )
        )

        style.addLayer(
            LineLayer(ROUTE_LINE_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
                lineColor("#4b8cc8"),
                lineWidth(7f),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND)
            )
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

    override fun onMapLongClick(point: com.mapbox.mapboxsdk.geometry.LatLng): Boolean {
        navigationSdk.navigationEngine.startNavigation(
            Routable.fromLatLng(LatLng(point.latitude, point.longitude))
        )

        return true
    }

    override fun onLocationChanged(location: Location?) {
        location?.let {
            if (lastLocation == null) {
                this.lastLocation = location
                this.mapboxMap?.animateCamera(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder()
                            .zoom(15.0)
                            .target(
                                com.mapbox.mapboxsdk.geometry.LatLng(
                                    location.latitude,
                                    location.longitude
                                )
                            ).build()
                    ),
                    2000
                )
            }

            this.navigationSdk.updateLocation(
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

    override fun onNavigationStarted(routable: Routable) {
        Log.d(TAG, "onNavigationStarted $routable")
    }

    override fun onNavigationStopped() {
        Log.d(TAG, "onNavigationStopped")
    }

    override fun onDestinationReached(routable: Routable) {
        Log.d(TAG, "onDestinationReached $routable")
    }

    override fun onRouteUpdateCanceled(routeRequest: RouteProvider.RouteRequest) {
        Log.d(TAG, "onRouteUpdateCanceled $routeRequest")
    }

    override fun onRouteUpdateFailed(e: Exception) {
        Log.d(TAG, "onRouteUpdateFailed $e")
    }

    override fun onRouteUpdateStarted(routeRequest: RouteProvider.RouteRequest) {
        Log.d(TAG, "onRouteUpdateStarted $routeRequest")
    }

    override fun onRouteUpdated(route: Route) {
        Log.d(TAG, "onRouteUpdated $route")
    }

    override fun onRouteRequestFailed(e: Exception) {
        Log.d(TAG, "onRouteRequestFailed $e")
    }


    override fun onNavigationStateInitialized(navigationState: NavigationState) {
        Log.d(TAG, "onNavigationStateInitialized $navigationState")
    }

    override fun onNavigationStateUpdated(navigationState: NavigationState) {
        // The NavigationState contains all relevant data for the current navigation session
        navigationState.route?.let {
            this.drawRoute(it.waypoints.filter { !it.reached }.map { it.latLng })
        }
    }

    private fun drawRoute(latLng: List<LatLng>) {
        val feature = Feature.fromGeometry(LineString.fromLngLats(latLng.map {
            Point.fromLngLat(it.longitude, it.latitude)
        }))


        this.routeSource.setGeoJson(feature)
    }
}