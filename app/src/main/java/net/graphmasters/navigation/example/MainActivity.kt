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
import com.mapbox.mapboxsdk.WellKnownTileServer
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
import net.graphmasters.multiplatform.core.model.LatLng
import net.graphmasters.multiplatform.core.units.Duration
import net.graphmasters.multiplatform.core.units.Length
import net.graphmasters.multiplatform.navigation.AndroidNavigationSdk
import net.graphmasters.multiplatform.navigation.NavigationSdk
import net.graphmasters.multiplatform.navigation.model.Routable
import net.graphmasters.multiplatform.navigation.model.Route
import net.graphmasters.multiplatform.navigation.routing.events.NavigationEventHandler.*
import net.graphmasters.multiplatform.navigation.routing.events.NavigationResult
import net.graphmasters.multiplatform.navigation.routing.progress.RouteProgressTracker.RouteProgress
import net.graphmasters.multiplatform.navigation.routing.state.NavigationState
import net.graphmasters.multiplatform.navigation.routing.state.OnNavigationStateInitializedListener
import net.graphmasters.multiplatform.navigation.routing.state.OnNavigationStateUpdatedListener
import net.graphmasters.multiplatform.navigation.ui.audio.VoiceInstructionComponent
import net.graphmasters.multiplatform.navigation.ui.audio.config.AudioConfig
import net.graphmasters.multiplatform.navigation.ui.audio.config.AudioConfigProvider
import net.graphmasters.multiplatform.navigation.ui.camera.CameraComponent
import net.graphmasters.multiplatform.navigation.ui.camera.CameraUpdate
import net.graphmasters.multiplatform.navigation.ui.camera.NavigationCameraHandler
import net.graphmasters.multiplatform.navigation.vehicle.*
import net.graphmasters.multiplatform.navigation.vehicle.Templates.CAR
import net.graphmasters.multiplatform.navigation.vehicle.Templates.MOTORBIKE
import net.graphmasters.multiplatform.navigation.vehicle.Templates.TRUCK
import net.graphmasters.navigation.example.databinding.ActivityMainBinding
import net.graphmasters.navigation.example.utils.EntityConverter
import net.graphmasters.navigation.example.utils.SystemUtils


class MainActivity : AppCompatActivity(), LocationListener,
    OnNavigationStateInitializedListener,
    OnNavigationStateUpdatedListener,
    OnNavigationStartedListener,
    OnNavigationStoppedListener,
    OnDestinationReachedListener,
    OnLeavingDestinationListener,
    OnRouteUpdateListener,
    OnOffRouteListener,
    OnRouteRequestFailedListener,
    MapboxMap.OnMapLongClickListener,
    MapboxMap.OnMoveListener,
    MapboxMap.OnMapClickListener,
    NavigationCameraHandler.CameraUpdateListener,
    NavigationCameraHandler.CameraTrackingListener {

    companion object {
        const val TAG = "MainActivity"
        const val LOCATION_PERMISSION_REQUEST_CODE = 1
        const val ROUTE_OUTLINE_LAYER_ID = "route-outline-layer"
        const val ROUTE_LINE_LAYER_ID = "route-layer"
        const val ROUTE_SOURCE_ID = "route-source"

        // Predefined truck config. All the parameters are freely customizable
        private val TRUCK_CONFIG = TruckConfig(
            properties = VehicleConfig.Properties(
                dimensions = VehicleConfig.Dimensions(
                    width = Length.fromMeters(2.5),
                    height = Length.fromMeters(3.5),
                    length = Length.fromMeters(6.0)
                ),
                weightKg = 17000.0,
            ),
            numberOfTrailers = 1,
        )
    }

    enum class CameraMode {
        FREE, FOLLOW
    }

    private var cameraMode: CameraMode = CameraMode.FREE
        set(value) {
            field = value

            when (value) {
                CameraMode.FREE -> {
                    this.stopCameraTracking()
                    this.binding.navigationInfoCard.visibility = View.GONE
                    this.binding.positionButton.visibility = View.VISIBLE
                }
                CameraMode.FOLLOW -> {
                    this.startCameraTracking()
                    this.binding.navigationInfoCard.visibility =
                        if (navigating) View.VISIBLE else View.GONE
                    this.binding.positionButton.visibility = View.GONE
                }
            }
        }

    private var vehicleConfig: VehicleConfig = CAR
        set(value) {
            val changed = field != value
            field = value

            this.binding.vehicleConfigButton.setImageResource(
                when (value.name) {
                    TRUCK.name -> R.drawable.ic_round_truck_24
                    MOTORBIKE.name -> R.drawable.ic_round_bike_24
                    else -> R.drawable.ic_round_car_24
                }
            )

            if (changed && this.navigating) {
                this.stopNavigation()
            }
        }

    private lateinit var routeSource: GeoJsonSource

    private lateinit var navigationSdk: NavigationSdk

    private lateinit var voiceInstructionComponent: VoiceInstructionComponent

    private lateinit var cameraComponent: CameraComponent

    private lateinit var locationManager: LocationManager

    private lateinit var binding: ActivityMainBinding

    private var mapboxMap: MapboxMap? = null

    private var lastLocation: Location? = null

    private val navigating: Boolean
        get() = this.navigationSdk.navigationState != null

    private val locationPermissionGranted: Boolean
        get() = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Mapbox.getInstance(this, null, WellKnownTileServer.MapLibre)

        this.binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(this.binding.root)

        this.binding.vehicleConfigButton.setOnClickListener {
            this.showVehicleConfigSelection()
        }

        this.binding.positionButton.setOnClickListener {
            this.cameraMode = CameraMode.FOLLOW
        }

        this.initMap(savedInstanceState)
        this.initNavigationSdk()
        this.initCameraComponent()
        this.initVoiceInstructionComponent()
    }

    private fun showVehicleConfigSelection() {
        AlertDialog.Builder(this)
            .setSingleChoiceItems(
                arrayOf(CAR.name, TRUCK.name, MOTORBIKE.name),
                when (this.vehicleConfig.name) {
                    TRUCK.name -> 1
                    MOTORBIKE.name -> 2
                    else -> 0
                }
            ) { dialog, which ->
                dialog.dismiss()
                this.vehicleConfig = when (which) {
                    1 -> TRUCK_CONFIG
                    2 -> MOTORBIKE
                    else -> CAR
                }
            }
            .setNeutralButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun initMap(savedInstanceState: Bundle?) {
        this.binding.mapView.onCreate(savedInstanceState)
        this.binding.mapView.getMapAsync { mapboxMap ->
            mapboxMap.setStyle(BuildConfig.MAP_STYLE_URL) {
                Log.d(TAG, "Map ready")
                this.mapboxMap = mapboxMap
                mapboxMap.addOnMapLongClickListener(this)
                mapboxMap.addOnMoveListener(this)
                mapboxMap.addOnMapClickListener(this)

                this.initRouteLayer(it)
                this.enableLocation()
                this.enableMapLocationComponent(
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
        // Initializes the Navigation SDK. Future calls to getInstance will return the same instance.
        this.navigationSdk = AndroidNavigationSdk.getInstance(this, BuildConfig.NUNAV_API_KEY)

        // Navigation state provides all necessary info about the current routing session.
        // By registering listeners you can be informed about any changes.
        this.navigationSdk.addOnNavigationStateUpdatedListener(this)
        // If the navigation state is initialized the RouteProgress is available, containing all relevant routing info
        this.navigationSdk.addOnNavigationStateInitializedListener(this)

        // Several navigation events
        this.navigationSdk.navigationEventHandler.addOnNavigationStartedListener(this)
        this.navigationSdk.navigationEventHandler.addOnNavigationStoppedListener(this)
        this.navigationSdk.navigationEventHandler.addOnRouteUpdateListener(this)
        this.navigationSdk.navigationEventHandler.addOnRouteRequestFailedListener(this)
        this.navigationSdk.navigationEventHandler.addOnDestinationReachedListener(this)
        this.navigationSdk.navigationEventHandler.addOnLeavingDestinationListener(this)
        this.navigationSdk.navigationEventHandler.addOnOffRouteListener(this)
    }

    private fun initVoiceInstructionComponent() {
        this.voiceInstructionComponent = VoiceInstructionComponent.init(
            context = this,
            navigationSdk = this.navigationSdk,
            // The AudioConfigProvider is optional, but gives more freedom to the audio output of choice
            audioConfigProvider = object : AudioConfigProvider {
                private val option = "android_auto"

                override val audioConfig: AudioConfig
                    get() = when (option) {
                        "voice_call" -> AudioConfig.Factory.craetaVoiceCallConfig()
                        "android_auto" -> AudioConfig.Factory.createAndroidAutoConfig()
                        else -> AudioConfig.Factory.craetaDeviceOnlyConfig()
                    }

            }
        )
    }

    @SuppressLint("MissingPermission")
    private fun enableMapLocationComponent(mapboxMap: MapboxMap, style: Style) {
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
        this.binding.mapView.onResume()
    }

    override fun onStart() {
        super.onStart()
        this.binding.mapView.onStart()
    }

    override fun onStop() {
        super.onStop()
        this.binding.mapView.onStop()
    }

    public override fun onPause() {
        super.onPause()
        this.binding.mapView.onPause()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        this.binding.mapView.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        this.binding.mapView.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        this.binding.mapView.onSaveInstanceState(outState)
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

    @Deprecated("Will be removed in the future", ReplaceWith("No replacement needed"))
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
        Log.d(TAG, "onNavigationStarted $routable")
        this.voiceInstructionComponent.enabled = true
    }

    override fun onNavigationStopped() {
        Toast.makeText(this, "onNavigationStopped", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "onNavigationStopped")
        this.cameraMode = CameraMode.FREE
        this.binding.navigationInfoCard.visibility = View.GONE
        this.voiceInstructionComponent.enabled = false
    }

    override fun onDestinationReached(navigationResult: NavigationResult) {
        Log.d(TAG, "onDestinationReached $navigationResult")
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
        this.binding.navigationInfoCard.visibility = View.VISIBLE
    }

    override fun onNavigationStateUpdated(navigationState: NavigationState?) {
        Log.d(TAG, "onNavigationStateUpdated $navigationState")

        // The NavigationState contains all relevant data for the current navigation session
        navigationState?.routeProgress?.let { routeProgress ->
            this.updateNavigationInfoViews(routeProgress)

            // Updating the map position icon with the current location
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

    @SuppressLint("SetTextI18n")
    private fun updateNavigationInfoViews(routeProgress: RouteProgress) {
        this.binding.nextMilestone.text = routeProgress.nextMilestone?.turnInfo?.turnCommand?.name
        this.binding.nextMilestoneDistance.text =
            "${routeProgress.nextMilestoneDistance.meters().toInt()}m"
        this.binding.distanceDestination.text =
            "${routeProgress.remainingDistance.meters().toInt()}m"
        this.binding.remainingTravelTime.text = "${routeProgress.remainingTravelTime.minutes()}min"
    }

    private fun drawRoute(polyline: List<LatLng>) {
        this.routeSource.setGeoJson(EntityConverter.convert(polyline))
    }

    private fun initCameraComponent() {
        this.cameraComponent = CameraComponent.Companion.init(this, navigationSdk)

        // Attach listener and you will be notified about new camera updates
        this.cameraComponent.addCameraUpdateListener(this)
        this.cameraComponent.addCameraTrackingListener(this)
    }

    private fun stopCameraTracking() {
        this.cameraComponent.stopCameraTracking()
    }

    private fun startCameraTracking() {
        this.cameraComponent.startCameraTracking()
    }

    override fun onCameraUpdateReady(cameraUpdate: CameraUpdate) {
        // Convert the update to the map model and pass to the map
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

        val latLng = LatLng(point.latitude, point.longitude)
        this.startNavigation(latLng)

        return true
    }


    /**
     * Starts the navigation to the given [latLng] and the currently selected [vehicleConfig]
     */
    private fun startNavigation(latLng: LatLng) {
        try {
            this.navigationSdk.startNavigation(
                latLng,
                this.vehicleConfig
            )
        } catch (e: Exception) {
            Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    override fun onBackPressed() {
        if (this.navigating) {
            this.stopNavigation()
        } else {
            super.onBackPressed()
        }
    }

    private fun stopNavigation() {
        this.navigationSdk.stopNavigation()
        this.drawRoute(emptyList())
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