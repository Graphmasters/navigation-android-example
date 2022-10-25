## About The Project

To serve as a hands on example, this project will demonstrate the integration and usage of Graphmaster's `multiplatform-navigation` library via a simple Android app.

![Preview](https://github.com/Graphmasters/navigation-android-example/blob/main/preview.png)

## Getting Started

1. Set API-Token either in the app's `build.gradle` ...
```
buildConfigField "String", "NUNAV_API_TOKEN", "\"" + "your_api_token" + "\""
```
... or directly in code
```
this.navigationSdk = NavigationSdk(
    context = this,
    apiKey = "your_api_token"
)
```

2. For map rendering you need to apply the NUNAV map style url in the app's `build.gradle` ...
```
buildConfigField "String", "MAP_STYLE_URL", "\"" + System.getenv("MAP_STYLE_URL") + "\""
```

## Inside The Example App

1. Initialize the map, bind it to your map view and set the map style url. Here you can also add your own listeners for map events.

```
private fun initMap(savedInstanceState: Bundle?) {
        this.binding.mapView.onCreate(savedInstanceState)
        this.binding.mapView.getMapAsync { mapboxMap ->
            mapboxMap.setStyle(BuildConfig.MAP_STYLE_URL) {
                Log.d(TAG, "Map ready")
                this.mapboxMap = mapboxMap
                mapboxMap.addOnMapLongClickListener(this)
                ...
```

2. Initialize the navigation sdk and hand over the NUNAV api key. Here you can set also the vehicle config (optional) and add your own listeners for handling navigation events. The vehicle config can be (re-)set at any time.

```
private fun initNavigationSdk() {
        this.navigationSdk = AndroidNavigationSdk(
            context = this,
            apiKey = BuildConfig.NUNAV_API_KEY
        )
        this.navigationSdk.vehicleConfig = this.vehicleConfig
        this.navigationSdk.navigationStateProvider.addOnNavigationStateUpdatedListener(this)
        ...
```

3. Initialize the camera component and hand over the navigation sdk. Use the camera component to add your listeners for camera events and to start/stop camera tracking.

```
private fun initCameraComponent() {
        this.cameraComponent = CameraComponent.Companion.init(this, navigationSdk)
        this.cameraComponent.addCameraUpdateListener(this)
                ...
```

4. Initialize the voice instruction component and hand over the navigation sdk. The voice instruction component brings out of the box voice instructions for turn-by-turn navigation in many different languages. You just need to enable or disable it.

```
private fun initVoiceInstructionComponent() {
        this.voiceInstructionComponent = VoiceInstructionComponent.init(
            this,
            this.navigationSdk
        )
                ...
```

## Usage
Once started the app requests permission to access the device location. After receiving a valid location, the camera will pan to that location.
Via long press on the map a destination is set and a navigation session is started. Toasts will give detailed information about navigation events.
Back press will stop an active navigation.

## Integration

* Repository
```
maven {
    url = "https://artifactory.graphmasters.net/artifactory/libs-release"
}
```

* Gradle dependency
```
implementation 'net.graphmasters.multiplatform:multiplatform-navigation-aar:{VERSION}'
implementation 'net.graphmasters.multiplatform:multiplatform-navigation-ui-aar:{VERSION}'
```

## Contact
info@graphmasters.net

