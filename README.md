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

1. For map rendering you need to apply the NUNAV map style url in the app's `build.gradle` ...
```
buildConfigField "String", "MAP_STYLE_URL", "\"" + System.getenv("MAP_STYLE_URL") + "\""
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
```

## Contact
info@graphmasters.net

