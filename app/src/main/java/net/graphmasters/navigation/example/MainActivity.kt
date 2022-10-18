package net.graphmasters.navigation.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.mapbox.android.gestures.MoveGestureDetector
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
import net.graphmasters.multiplatform.core.model.LatLng
import net.graphmasters.multiplatform.core.units.Duration
import net.graphmasters.multiplatform.core.units.Length
import net.graphmasters.multiplatform.navigation.AndroidNavigationSdk
import net.graphmasters.multiplatform.navigation.NavigationSdk
import net.graphmasters.multiplatform.navigation.model.Routable
import net.graphmasters.multiplatform.navigation.model.RoutableFactory
import net.graphmasters.multiplatform.navigation.model.Route
import net.graphmasters.multiplatform.navigation.routing.events.NavigationEventHandler.*
import net.graphmasters.multiplatform.navigation.routing.progress.RouteProgressTracker.RouteProgress
import net.graphmasters.multiplatform.navigation.routing.state.NavigationStateProvider.*
import net.graphmasters.multiplatform.navigation.ui.audio.VoiceInstructionComponent
import net.graphmasters.multiplatform.navigation.ui.camera.CameraSdk
import net.graphmasters.multiplatform.navigation.ui.camera.CameraUpdate
import net.graphmasters.multiplatform.navigation.ui.camera.NavigationCameraHandler
import net.graphmasters.multiplatform.navigation.vehicle.CarConfig
import net.graphmasters.multiplatform.navigation.vehicle.MotorbikeConfig
import net.graphmasters.multiplatform.navigation.vehicle.TruckConfig
import net.graphmasters.multiplatform.navigation.vehicle.VehicleConfig
import net.graphmasters.navigation.example.utils.EntityConverter
import net.graphmasters.navigation.example.utils.SystemUtils


class MainActivity : AppCompatActivity(), LocationListener,
    OnNavigationStateUpdatedListener, MapboxMap.OnMapLongClickListener,
    OnNavigationStartedListener,
    OnNavigationStoppedListener,
    OnDestinationReachedListener,
    OnRouteUpdateListener,
    OnRouteRequestFailedListener,
    OnNavigationStateInitializedListener, OnLeavingDestinationListener, OnOffRouteListener,
    NavigationCameraHandler.CameraUpdateListener, MapboxMap.OnMoveListener,
    MapboxMap.OnMapClickListener, NavigationCameraHandler.CameraTrackingListener {

    companion object {
        const val TAG = "MainActivity"
        const val LOCATION_PERMISSION_REQUEST_CODE = 1
        const val ROUTE_OUTLINE_LAYER_ID = "route-outline-layer"
        const val ROUTE_LINE_LAYER_ID = "route-layer"
        const val ROUTE_SOURCE_ID = "route-source"

        // Predefined truck config. All the parameters are freely customizable
        private val TRUCK_CONFIG = TruckConfig(
            weightKg = 13000.0,
            height = Length.fromMeters(3.5),
            width = Length.fromMeters(2.5),
            length = Length.fromMeters(16.5),
            trailerCount = 1
        )

        // Default car config. Does not allow for specific vehicle properties yet
        private val CAR_CONFIG = CarConfig()

        // Default motorbike config. Does not allow for specific vehicle properties yet
        private val MOTORBIKE_CONFIG = MotorbikeConfig()
    }

    enum class CameraMode {
        FREE, FOLLOW
    }

    private val context = this

    private var cameraMode: CameraMode = CameraMode.FREE
        set(value) {
            field = value

            when (value) {
                CameraMode.FREE -> {
                    this.cameraSdk.navigationCameraHandler.stopCameraTracking()
                    this.navigationInfoCard?.visibility = View.GONE
                    this.positionButton?.visibility = View.VISIBLE
                }
                CameraMode.FOLLOW -> {
                    this.cameraSdk.navigationCameraHandler.startCameraTracking()
                    this.navigationInfoCard?.visibility =
                        if (navigating) View.VISIBLE else View.GONE
                    this.positionButton?.visibility = View.GONE
                }
            }
        }

    private var vehicleConfig: VehicleConfig = CAR_CONFIG
        set(value) {
            field = value

            this.navigationSdk.vehicleConfig = value
            this.vehicleConfigButton.setImageResource(
                when (value) {
                    is TruckConfig -> R.drawable.ic_round_truck_24
                    is MotorbikeConfig -> R.drawable.ic_round_bike_24
                    else -> R.drawable.ic_round_car_24
                }
            )
        }

    private lateinit var routeSource: GeoJsonSource

    private lateinit var navigationSdk: NavigationSdk

    private lateinit var cameraSdk: CameraSdk

    private lateinit var locationManager: LocationManager

    private var mapboxMap: MapboxMap? = null

    private var lastLocation: Location? = null

    private val navigating: Boolean
        get() = this.navigationSdk.navigationStateProvider.navigationState.currentlyNavigating

    private val locationPermissionGranted: Boolean
        get() = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    private var voiceInstructionComponent: VoiceInstructionComponent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Mapbox.getInstance(this, BuildConfig.MAPBOX_TOKEN)
        setContentView(R.layout.activity_main)

        this.vehicleConfigButton.setOnClickListener {
            this.showVehicleConfigSelection()
        }

        this.positionButton.setOnClickListener {
            this.cameraMode = CameraMode.FOLLOW
        }

        this.initMapbox(savedInstanceState)
        this.initNavigationSdk()
        this.initCameraSdk()
        this.voiceInstructionComponent = VoiceInstructionComponent.Companion.init(
            this,
            this.navigationSdk
        )
    }

    private fun showVehicleConfigSelection() {
        AlertDialog.Builder(this)
            .setSingleChoiceItems(
                arrayOf("Car", "Truck", "Motorbike"), when (this.vehicleConfig) {
                    is TruckConfig -> 1
                    is MotorbikeConfig -> 2
                    else -> 0
                }
            ) { dialog, which ->
                dialog.dismiss()
                this.vehicleConfig = when (which) {
                    1 -> TRUCK_CONFIG
                    2 -> MOTORBIKE_CONFIG
                    else -> CAR_CONFIG
                }
            }
            .setNeutralButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun initMapbox(savedInstanceState: Bundle?) {
        mapView?.onCreate(savedInstanceState)
        mapView?.getMapAsync { mapboxMap ->
            mapboxMap.setStyle(Style.MAPBOX_STREETS) {
                Log.d(TAG, "Map ready")
                this.mapboxMap = mapboxMap
                mapboxMap.addOnMapLongClickListener(this)
                mapboxMap.addOnMoveListener(this)
                mapboxMap.addOnMapClickListener(this)

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

    private fun initNavigationSdk() {
        this.navigationSdk = AndroidNavigationSdk(
            context = this,
            apiKey = BuildConfig.NUNAV_API_KEY
        )

        // The currently used vehicle config can be set at any time before or during the routing, altering the route request and returning an appropriate route
        this.navigationSdk.vehicleConfig = this.vehicleConfig

        // Navigation state provides all necessary info about the current routing session.
        // By registering listeners you can be informed about any changes.
        this.navigationSdk.navigationStateProvider.addOnNavigationStateUpdatedListener(this)
        // If the navigation state is initialized the RouteProgress is available, containing all relevant routing info
        this.navigationSdk.navigationStateProvider.addOnNavigationStateInitializedListener(this)

        // Several navigation events
        this.navigationSdk.navigationEventHandler.addOnNavigationStartedListener(this)
        this.navigationSdk.navigationEventHandler.addOnNavigationStoppedListener(this)
        this.navigationSdk.navigationEventHandler.addOnRouteUpdateListener(this)
        this.navigationSdk.navigationEventHandler.addOnRouteRequestFailedListener(this)
        this.navigationSdk.navigationEventHandler.addOnDestinationReachedListener(this)
        this.navigationSdk.navigationEventHandler.addOnLeavingDestinationListener(this)
        this.navigationSdk.navigationEventHandler.addOnOffRouteListener(this)
    }

    @SuppressLint("MissingPermission")
    private fun enableMapboxLocationComponent(mapboxMap: MapboxMap, style: Style) {
        val locationComponent: LocationComponent = mapboxMap.locationComponent

        val locationComponentActivationOptions: LocationComponentActivationOptions =
            LocationComponentActivationOptions.builder(this, style)
                .useDefaultLocationEngine(false)
                .build()
        locationComponent.activateLocationComponent(locationComponentActivationOptions)

        locationComponent.isLocationComponentEnabled = true
        locationComponent.renderMode = RenderMode.GPS
    }

    private fun initRouteLayer(style: Style) {
        //Creating source, which will be manipulated to display route changes
        this.routeSource = GeoJsonSource(ROUTE_SOURCE_ID)
        style.addSource(this.routeSource)

        //Setup outline layer for the route
        style.addLayer(
            LineLayer(ROUTE_OUTLINE_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
                lineColor("#005f97"),
                lineWidth(13f),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND)
            )
        )

        //Setup inner line layer for the route
        style.addLayer(
            LineLayer(ROUTE_LINE_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
                lineColor("#4b8cc8"),
                lineWidth(10f),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND)
            )
        )
    }

    private fun moveCameraCurrentPosition() {
        this.lastLocation?.let {
            this.mapboxMap?.animateCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .zoom(15.0)
                        .target(
                            com.mapbox.mapboxsdk.geometry.LatLng(
                                it.latitude,
                                it.longitude
                            )
                        ).build()
                ),
                2000
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
        if (this.navigating) {
            this.navigationSdk.navigationEngine.stopNavigation()
            this.drawRoute(emptyList())
        } else {
            super.onBackPressed()
        }
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

    override fun onLocationChanged(location: Location) {
        Log.d(TAG, "onLocationChanged $location")
        if (this.lastLocation == null) {
            this.lastLocation = location
            this.moveCameraCurrentPosition()
        }

        this.lastLocation = location
        if (!this.navigating) {
            this.mapboxMap?.locationComponent?.forceLocationUpdate(location)
        }

        // Publish the current location to the SDK
        this.navigationSdk.updateLocation(EntityConverter.convert(location))
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        Log.d(TAG, "onStatusChanged $provider")
    }

    override fun onProviderEnabled(provider: String) {
        Log.d(TAG, "onProviderEnabled $provider")
    }

    override fun onProviderDisabled(provider: String) {
        Log.d(TAG, "onProviderDisabled $provider")
    }

    override fun onNavigationStarted(routable: Routable) {
        Toast.makeText(this, "onNavigationStarted", Toast.LENGTH_SHORT).show()
        this.voiceInstructionComponent?.enable()
        Log.d(TAG, "onNavigationStarted $routable")
    }

    override fun onNavigationStopped() {
        Toast.makeText(this, "onNavigationStopped", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "onNavigationStopped")
        this.cameraMode = CameraMode.FREE
        this.navigationInfoCard.visibility = View.GONE
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

    override fun onOffRouteDetected() {
        Log.d(TAG, "onOffRouteDetected")
    }

    override fun onOffRouteVerified() {
        Log.d(TAG, "onOffRouteVerified")
    }

    override fun onNavigationStateInitialized(navigationState: NavigationState) {
        Toast.makeText(this, "onNavigationStateInitialized", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "onNavigationStateInitialized $navigationState")

        this.cameraMode = CameraMode.FOLLOW
        this.navigationInfoCard.visibility = View.VISIBLE
    }

    override fun onNavigationStateUpdated(navigationState: NavigationState) {
        Log.d(TAG, "onNavigationStateUpdated $navigationState")

        // The NavigationState contains all relevant data for the current navigation session
        navigationState.routeProgress?.let { routeProgress ->
            this.updateNavigationInfoViews(routeProgress)

            // Updating the Mapbox position icon with the current location
            this.updateMapLocation(routeProgress, navigationState.onRoute)

            // Draw the current route on the map
            this.drawRoute(routeProgress.route.waypoints.map { it.latLng })
        }
    }

    private fun updateMapLocation(routeProgress: RouteProgress, onRoute: Boolean) {
        // If the user is still on route, use the projected location of the route...
        val location = if (onRoute) {
            routeProgress.currentLocationOnRoute
        } else {
            //... otherwise use the original gps location
            routeProgress.currentLocationOnRoute.originalLocation
        }

        EntityConverter.convert(location).let {
            this.mapboxMap?.locationComponent?.forceLocationUpdate(it)
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

    private fun drawRoute(polyline: List<LatLng>) {
        this.routeSource.setGeoJson(EntityConverter.convert(polyline))
    }

    private fun initCameraSdk() {
        this.cameraSdk = CameraSdk(this, this.navigationSdk, Duration.fromSeconds(3))

        // Attach listener and you will be notified about new camera updates
        this.cameraSdk.navigationCameraHandler.addCameraUpdateListener(this)
        this.cameraSdk.navigationCameraHandler.addCameraTrackingListener(this)
    }

    override fun onCameraUpdateReady(cameraUpdate: CameraUpdate) {
        // Convert the update to the mapbox model and pass to the map
        this.mapboxMap?.animateCamera(EntityConverter.convert(cameraUpdate), 4000)
    }

    override fun onCameraTrackingStarted() {
        Log.d(TAG, "onCameraTrackingStarted")
    }

    override fun onCameraTrackingStopped() {
        Log.d(TAG, "onCameraTrackingStopped")
    }

    override fun onMapLongClick(point: com.mapbox.mapboxsdk.geometry.LatLng): Boolean {
        SystemUtils.vibrate(this, Duration.fromMilliseconds(200))

        try {
            this.navigationSdk.navigationEngine.startNavigation(
                RoutableFactory.create(LatLng(point.latitude, point.longitude))
            )
        } catch (e: Exception) {
            Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }

        return true
    }

    override fun onMoveBegin(detector: MoveGestureDetector) {}

    override fun onMove(detector: MoveGestureDetector) {
        this.cameraMode = CameraMode.FREE
    }

    override fun onMoveEnd(detector: MoveGestureDetector) {}

    override fun onMapClick(point: com.mapbox.mapboxsdk.geometry.LatLng): Boolean {
        this.cameraMode = CameraMode.FREE
        return false
    }
}