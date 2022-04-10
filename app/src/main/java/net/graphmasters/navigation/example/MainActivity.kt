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
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.geojson.Feature
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.*
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.layers.properties.generated.LineCap
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.sources.getSource
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.flyTo
import com.mapbox.maps.plugin.gestures.*
import com.mapbox.maps.plugin.locationcomponent.LocationComponentPlugin
import com.mapbox.maps.plugin.locationcomponent.location
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
import net.graphmasters.multiplatform.navigation.routing.progress.RouteProgressTracker
import net.graphmasters.multiplatform.navigation.routing.state.NavigationStateProvider.*
import net.graphmasters.multiplatform.navigation.vehicle.CarConfig
import net.graphmasters.multiplatform.navigation.vehicle.MotorbikeConfig
import net.graphmasters.multiplatform.navigation.vehicle.TruckConfig
import net.graphmasters.multiplatform.navigation.vehicle.VehicleConfig
import net.graphmasters.multiplatform.ui.camera.CameraSdk
import net.graphmasters.multiplatform.ui.camera.CameraUpdate
import net.graphmasters.multiplatform.ui.camera.NavigationCameraHandler
import net.graphmasters.navigation.example.utils.EntityConverter
import net.graphmasters.navigation.example.utils.SystemUtils


class MainActivity : AppCompatActivity(), LocationListener,
    OnNavigationStateUpdatedListener,
    OnNavigationStartedListener,
    OnNavigationStoppedListener,
    OnDestinationReachedListener,
    OnRouteUpdateListener,
    OnRouteRequestFailedListener,
    OnNavigationStateInitializedListener, OnLeavingDestinationListener, OnOffRouteListener,
    NavigationCameraHandler.CameraUpdateListener,
    OnMapLongClickListener, OnMoveListener, OnMapClickListener {

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

    private var cameraMode: CameraMode = CameraMode.FREE
        set(value) {
            field = value

            when (value) {
                CameraMode.FREE -> {
                    this.cameraSdk.navigationCameraHandler.stopCameraTracking()
                    this.mapboxMap?.setCamera(
                        CameraOptions.Builder()
                            .padding(
                                EdgeInsets(
                                    0.0,
                                    0.0,
                                    0.0,
                                    0.0
                                )
                            )
                            .build()
                    )
                    this.navigationInfoCard?.visibility = View.GONE
                    this.positionButton?.visibility = View.VISIBLE
                }
                CameraMode.FOLLOW -> {
                    this.cameraSdk.navigationCameraHandler.startCameraTracking()
                    this.navigationInfoCard?.visibility = View.VISIBLE
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

    private var mapView: MapView? = null

    private var lastLocation: Location? = null

    private val locationPermissionGranted: Boolean
        get() {
            return ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }

    private val screenHeight: Int
        get() = (this.getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.height

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ResourceOptionsManager.getDefault(this, BuildConfig.MAPBOX_TOKEN).update {
            tileStoreUsageMode(TileStoreUsageMode.READ_ONLY)
        }

        this.mapView = findViewById(R.id.mapView)
        setContentView(R.layout.activity_main)

        this.vehicleConfigButton.setOnClickListener {
            this.showVehicleConfigSelection()
        }

        this.positionButton.setOnClickListener {
            if (this.navigationSdk.navigationStateProvider.navigationState.currentlyNavigating) {
                this.cameraMode = CameraMode.FOLLOW
            } else {
                this.moveCameraCurrentPosition()
            }
        }

        this.initMapbox()
        this.initNavigationSDK()
        this.initCameraSdk()
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
                kotlin.run {
                    dialog.dismiss()
                    this.vehicleConfig = when (which) {
                        1 -> TRUCK_CONFIG
                        2 -> MOTORBIKE_CONFIG
                        else -> CAR_CONFIG
                    }
                }

            }
            .setNeutralButton(
                "Cancel"
            ) { dialog, _ -> dialog.dismiss() }
            .show()

    }

    private fun initMapbox() {
        this.mapView?.let {
            this.mapboxMap = it.getMapboxMap()

            it.getMapboxMap().loadStyleUri(
                Style.MAPBOX_STREETS,
                onStyleLoaded = { style ->
                    Log.d(TAG, "Map ready")
                    mapboxMap!!.addOnMapLongClickListener(this)
                    mapboxMap!!.addOnMoveListener(this)
                    mapboxMap!!.addOnMapClickListener(this)
                    this.initRouteLayer(style)
                    this.enableLocation()
                    this.enableMapboxLocationComponent(it.location, this)
                }
            )
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

    private fun initNavigationSDK() {
        this.navigationSdk = AndroidNavigationSdk(
            context = this,
            apiKey =  net.graphmasters.navigation.example.BuildConfig.NUNAV_API_KEY
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

    private fun enableMapboxLocationComponent(location: LocationComponentPlugin , context: Context) {
        location.apply {
            this.locationPuck = navigationPuck2D(context)
            this.enabled = true
            this.pulsingEnabled = true
        }
    }

    private fun navigationPuck2D(context: Context) = LocationPuck2D(
        bearingImage = ContextCompat.getDrawable(context, R.drawable.navigation_puck_icon)
    )

    private fun initRouteLayer(style: Style) {
        //Creating source, which will be manipulated to display route changes
        this.routeSource = geoJsonSource(ROUTE_SOURCE_ID)
        style.addSource(this.routeSource)

        //Setup outline layer for the route
        style.addLayer(
            lineLayer(ROUTE_OUTLINE_LAYER_ID, ROUTE_SOURCE_ID) {
                lineColor("#005f97")
                lineCap(LineCap.ROUND)
                lineJoin(LineJoin.ROUND)
                lineWidth(13.0)
            })

        //Setup inner line layer for the route
        style.addLayer(
            lineLayer(ROUTE_LINE_LAYER_ID, ROUTE_SOURCE_ID) {
                lineColor("#4b8cc8")
                lineCap(LineCap.ROUND)
                lineJoin(LineJoin.ROUND)
                lineWidth(10.0)
            }
        )
    }

    private fun moveCameraCurrentPosition() {
        this.lastLocation?.let {
            val cameraOption = CameraOptions.Builder()
                .zoom(15.0)
                .center(Point.fromLngLat(it.longitude, it.latitude))
                .build()

            this.mapboxMap?.flyTo(
                cameraOption,
                MapAnimationOptions.mapAnimationOptions {
                    this.duration(2000)
                }
            )
        }
    }

    override fun onBackPressed() {
        if (this.navigationSdk.navigationStateProvider.navigationState.currentlyNavigating) {
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
        if (this.lastLocation == null) {
            this.lastLocation = location
            this.moveCameraCurrentPosition()
        }

//        this.lastLocation = location
//        if (!this.navigationSdk.navigationStateProvider.navigationState.initialized) {
//            this.mapView?.location.getLocationProvider().
//
//            //locationComponent?.forceLocationUpdate(location)
//        }

        // Publish the current location to the SDK
        this.navigationSdk.updateLocation(
            location = EntityConverter.convert(location)
        )
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

    override fun onOffRoute() {
        Toast.makeText(this, "onOffRoute", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "onOffRoute")
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

//            // Updating the Mapbox position icon with the location on the route instead of the raw one received from the GPS
//            routeProgress.currentLocationOnRoute?.let
//                this.mapboxMap?.locationComponent?.forceLocationUpdate(
//                    EntityConverter.convert(
//                        it
//                    )
//                )
//            }

            // Draw the current route on the map
            this.drawRoute(routeProgress.route.waypoints.map { it.latLng })
        }
    }

    private fun updateNavigationInfoViews(routeProgress: RouteProgressTracker.RouteProgress) {
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

        this.mapboxMap?.getStyle { style ->
            style.getSource(ROUTE_SOURCE_ID)?.let {
                (it as GeoJsonSource).feature(feature)
            }
        }
    }

    private fun initCameraSdk() {
        this.cameraSdk = CameraSdk(navigationSdk = this.navigationSdk)

        // Attach listener and you will be notified about new camera updates
        this.cameraSdk.navigationCameraHandler.addCameraUpdateListener(this)
    }

    override fun onCameraUpdateReady(cameraUpdate: CameraUpdate) {
        // Convert the CameraUpdate provided by the SDK into the desired format - in this case Mapbox
        val cameraOption = CameraOptions.Builder()
        cameraUpdate.bearing?.let {
            cameraOption.bearing(it.toDouble())
        }
        cameraUpdate.tilt?.let {
            cameraOption.pitch(it.toDouble())
        }
        cameraUpdate.zoom?.let {
            cameraOption.zoom(it.toDouble())
        }

        cameraOption.center(
            Point.fromLngLat(
                cameraUpdate.latLng.longitude,
                cameraUpdate.latLng.latitude
            )
        )

        // The padding is represented as four float values ranging from 0..1 .
        // 0 would represent a padding of 0, while 1 would represent the max height or max width of the device's screen
        cameraOption.padding(
            EdgeInsets(
                0.0,
                0.0,
                (cameraUpdate.padding.bottom * this.screenHeight * -1).toDouble(),
                0.0
            )
        )

        // Pass the update to Mapbox
        this.mapboxMap?.flyTo(
            cameraOption.build(),
            MapAnimationOptions.mapAnimationOptions {
                cameraUpdate.duration.let {
                    this.duration(it.milliseconds() * 2)
                }
            }
        )
    }

    override fun onMapLongClick(point: Point): Boolean {
        SystemUtils.vibrate(this, Duration.fromMilliseconds(200))

        try {
            this.navigationSdk.navigationEngine.startNavigation(
                RoutableFactory.fromLatLng(
                    LatLng(
                        point.latitude(),
                        point.longitude()
                    )
                )
            )
        } catch (e: Exception) {
            Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
        }

        return true
    }

    override fun onMoveBegin(detector: MoveGestureDetector) {
    }

    override fun onMove(detector: MoveGestureDetector): Boolean {
        this.cameraMode = CameraMode.FREE
        return true
    }

    override fun onMoveEnd(detector: MoveGestureDetector) {
    }

    override fun onMapClick(point: Point): Boolean {
        this.cameraMode = CameraMode.FREE
        return false
    }
}