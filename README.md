## About The Project

To serve as a hands on example, this project will demonstrate the integration and usage of Graphmaster's `multiplatform-navigation` library via a simple Android app.

![Preview](https://github.com/Graphmasters/navigation-android-example/blob/main/preview.png)

## Getting Started

1. Set API-Token either in the app's `build.gradle` or directly in code
```
buildConfigField "String", "NUNAV_API_TOKEN", "\"" + "your_api_token" + "\""
```
```
this.navigationSdk = NavigationSdk(
    context = this,
    apiKey = "your_api_token"
)
```

1. For map rendering the Mapbox Android SDK is used. You can create a free account with them and create a test token. Apply the token either in the app's `build.gradle` or directly in code
```
buildConfigField "String", "MAPBOX_TOKEN", "\"" + "your_mapbox_token" + "\""
```
```
Mapbox.getInstance(this, "your_mapbox_token");
```

## Usage
Once started the app requests permission to access the device location. After receiving a valid location, the camera will pan to that location.
Via long press on the map a routing destination is set and a routing session is started. Toasts will give detailed information about navigation events.
Back press will stop an active navigation.

## Contact
info@graphmasters.net

