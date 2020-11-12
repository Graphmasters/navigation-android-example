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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.mapbox.geojson.Feature
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.location.LocationComponent
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions
import com.mapbox.mapboxsdk.location.modes.RenderMode
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.style.layers.LineLayer
import com.mapbox.mapboxsdk.style.layers.Property
import com.mapbox.mapboxsdk.style.layers.PropertyFactory.*
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource
import kotlinx.android.synthetic.main.activity_main.*
import net.graphmasters.core.model.LatLng
import net.graphmasters.core.units.Duration
import net.graphmasters.routing.NavigationSdk
import net.graphmasters.routing.model.Routable
import net.graphmasters.routing.model.Route
import net.graphmasters.routing.navigation.events.NavigationEventHandler.*
import net.graphmasters.routing.navigation.progress.RouteProgressTracker.RouteProgress
import net.graphmasters.routing.navigation.state.NavigationStateProvider.*
import net.graphmasters.routing.routing.example.concurrency.MainThreadExecutor
import net.graphmasters.routing.routing.example.utils.EntityConverter
import net.graphmasters.routing.routing.example.utils.SystemUtils


class MainActivity : AppCompatActivity(), LocationListener,
    OnNavigationStateUpdatedListener, MapboxMap.OnMapLongClickListener,
    OnNavigationStartedListener,
    OnNavigationStoppedListener,
    OnDestinationReachedListener,
    OnRouteUpdateListener,
    OnRouteRequestFailedListener,
    OnNavigationStateInitializedListener, OnLeavingDestinationListener, OnOffRouteListener {

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

    private val locationPermissionGranted: Boolean
        get() {
            return ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Mapbox.getInstance(this, BuildConfig.MAPBOX_TOKEN);
        setContentView(R.layout.activity_main)


        this.initMapbox(savedInstanceState)
        this.initializeNavigationSDK()
    }

    private fun initMapbox(savedInstanceState: Bundle?) {
        mapView?.onCreate(savedInstanceState)
        mapView?.getMapAsync { mapboxMap ->
            mapboxMap.setStyle(Style.MAPBOX_STREETS) {
                Log.d(TAG, "Map ready")
                this.mapboxMap = mapboxMap
                mapboxMap.addOnMapLongClickListener(this)

                this.initRouteLayer(it)
                this.enableLocation()
                this.enableMapboxLocationComponent(
                    mapboxMap = mapboxMap,
                    style = it
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableLocation() {
        if (this.locationPermissionGranted) {
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

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.VIBRATE),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                val permissionGranted =
                    grantResults.getOrElse(0) { PackageManager.PERMISSION_DENIED } == PackageManager.PERMISSION_GRANTED

                if (permissionGranted) {
                    this.enableLocation()
                }
            }
        }
    }

    private fun initializeNavigationSDK() {
        this.navigationSdk = NavigationSdk(
            config = NavigationSdk.Config(
                username = BuildConfig.NUNAV_USERNAME,
                password = BuildConfig.NUNAV_PASSWORD,
                serviceUrl = BuildConfig.NUNAV_SERVICE_URL,
                instanceId = "**Unique id for each device running the SDK. i.e DeviceId**"
            ),
            mainExecutor = MainThreadExecutor(Handler())
        )

        // Navigation state provides all necessary info about the current routing session.
        // By registering listeners you can be informed about any changes.
        navigationSdk.navigationStateProvider.addOnNavigationStateUpdatedListener(this)
        // If the navigation state is initialized the RouteProgress is available, containing all relevant routing info
        navigationSdk.navigationStateProvider.addOnNavigationStateInitializedListener(this)

        // Several navigation events
        navigationSdk.navigationEventHandler.addOnNavigationStartedListener(this)
        navigationSdk.navigationEventHandler.addOnNavigationStoppedListener(this)
        navigationSdk.navigationEventHandler.addOnRouteUpdateListener(this)
        navigationSdk.navigationEventHandler.addOnRouteRequestFailedListener(this)
        navigationSdk.navigationEventHandler.addOnDestinationReachedListener(this)
        navigationSdk.navigationEventHandler.addOnLeavingDestinationListener(this)
        navigationSdk.navigationEventHandler.addOnOffRouteListener(this)
    }

    @SuppressLint("MissingPermission")
    private fun enableMapboxLocationComponent(mapboxMap: MapboxMap, style: Style) {
        val locationComponent: LocationComponent = mapboxMap.locationComponent

        val locationComponentActivationOptions: LocationComponentActivationOptions =
            LocationComponentActivationOptions.builder(this, style)
                .useDefaultLocationEngine(false)
                .build()
        locationComponent.activateLocationComponent(locationComponentActivationOptions);

        locationComponent.isLocationComponentEnabled = true;
        locationComponent.renderMode = RenderMode.GPS;
    }

    private fun initRouteLayer(style: Style) {
        //Creating source, which will be manipulated to display route changes
        this.routeSource = GeoJsonSource(ROUTE_SOURCE_ID)
        style.addSource(this.routeSource)

        //Setup outline layer for the route
        style.addLayer(
            LineLayer(ROUTE_OUTLINE_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
                lineColor("#005f97"),
                lineWidth(10f),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND)
            )
        )

        //Setup inner line layer for the route
        style.addLayer(
            LineLayer(ROUTE_LINE_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
                lineColor("#4b8cc8"),
                lineWidth(7f),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND)
            )
        )
    }


    override fun onMapLongClick(point: com.mapbox.mapboxsdk.geometry.LatLng): Boolean {
        SystemUtils.vibrate(this, Duration.fromMilliseconds(200))

        this.navigationSdk.navigationEngine.startNavigation(
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

            if (!this.navigationSdk.navigationStateProvider.navigationState.initialized) {
                this.mapboxMap!!.locationComponent.forceLocationUpdate(location)
            }

            // Publish the current location to the SDK
            this.navigationSdk.updateLocation(
                location = EntityConverter.convert(location)
            )
        }
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

    override fun onBackPressed() {
        if (this.navigationSdk.navigationStateProvider.navigationState.currentlyNavigating) {
            this.navigationSdk.navigationEngine.stopNavigation()
            this.drawRoute(emptyList())
        } else {
            super.onBackPressed()
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
        Toast.makeText(this, "onNavigationStarted", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "onNavigationStarted $routable")
    }

    override fun onNavigationStopped() {
        Toast.makeText(this, "onNavigationStopped", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "onNavigationStopped")
    }

    override fun onDestinationReached(routable: Routable) {
        Log.d(TAG, "onDestinationReached $routable")
    }

    override fun onLeavingDestination(routable: Routable) {
        Log.d(TAG, "onDestinationReached $routable")
    }

    override fun onRouteUpdated(route: Route) {
        Log.d(TAG, "onRouteUpdated $route")
    }

    override fun onRouteRequestFailed(e: Exception) {
        Log.d(TAG, "onRouteRequestFailed $e")
    }

    override fun onOffRoute() {
        Toast.makeText(this, "onOffRoute", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "onOffRoute")
    }

    override fun onNavigationStateInitialized(navigationState: NavigationState) {
        Toast.makeText(this, "onNavigationStateInitialized", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "onNavigationStateInitialized $navigationState")
    }

    override fun onNavigationStateUpdated(navigationState: NavigationState) {
        Log.d(TAG, "onNavigationStateUpdated $navigationState")

        // The NavigationState contains all relevant data for the current navigation session
        navigationState.routeProgress?.let { routeProgress ->
            this.updateNavigationInfoViews(routeProgress)

            // Updating the Mapbox position icon with the location on the route instead of the raw one received from the GPS
            routeProgress.currentLocationOnRoute?.let {
                this.mapboxMap?.locationComponent?.forceLocationUpdate(
                    EntityConverter.convert(
                        it
                    )
                )
            }

            // Draw the current route on the map
            this.drawRoute(routeProgress.route.waypoints.map { it.latLng })
        }
    }

    private fun updateNavigationInfoViews(routeProgress: RouteProgress) {
        this.nextMilestone.text = routeProgress.nextMilestone?.turnInfo?.turnCommand?.name
        this.nextMilestoneDistance.text =
            "${routeProgress.nextMilestoneDistance.meters().toInt()}m"
        this.distanceDestination.text =
            "${routeProgress.remainingDistance.meters().toInt()}m"
        this.remainingTravelTime.text = "${routeProgress.remainingTravelTime.minutes()}min"
    }

    private fun drawRoute(latLng: List<LatLng>) {
        val feature = Feature.fromGeometry(LineString.fromLngLats(latLng.map {
            Point.fromLngLat(it.longitude, it.latitude)
        }))

        this.routeSource.setGeoJson(feature)
    }
}