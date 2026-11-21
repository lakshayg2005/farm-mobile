# farm-mobile — native Android (Kotlin + Jetpack Compose)

Native rewrite of the Expo / React Native apps in `apps/` and `packages/`. No Expo Go needed: build an APK and install it.
It talks to the same backend gateway (`farm-backend`, `/api/v1`); nothing in the backend or web app changes.

## Modules

| Module      | Replaces                     | What                                                                 |
| ----------- | ---------------------------- | -------------------------------------------------------------------- |
| `:core`     | `packages/api`, `packages/ui` | HTTP client, models, auth (OTP / password / sign-up), shared UI, login and team screens |
| `:customer` | `apps/customer`              | Produce browse + cart + checkout, Land plots + reservations + timeline, orders, tracking, rating, notifications, harvest |
| `:delivery` | `apps/delivery`              | Today's route, Google Maps navigation, pickup, proof of delivery (photo + OTP), 30 s GPS pings |

## Build

Open the `android/` folder in Android Studio and run `customer` or `delivery`. From a terminal:

```bash
cd android
./gradlew assembleRelease     # customer/build/outputs/apk/release/customer-release.apk
                              # delivery/build/outputs/apk/release/delivery-release.apk
./gradlew testDebugUnitTest   # unit tests (route, cart, checkout)
```

Release APKs are signed with the debug key, so they install directly on any phone (allow "install unknown apps").
Use your own keystore for a Play Store release.

## API server

The default gateway is `farmApiBaseUrl` in `gradle.properties` (`http://10.50.7.73/api/v1`).
It can also be changed in the app: login screen → **Server: … → Change**. The phone must be on a network that can reach that address.
Plain HTTP is allowed (`usesCleartextTraffic`) because the gateway runs without TLS.

Without a backend, the customer app falls back to the same seed produce / plots / reservations as before
(`customer/src/main/assets/*.json`), and both login screens have the "Mock … Login" dev buttons.

## Differences from the React Native version

- Push notifications (Expo push token) are not registered; the in-app notification inbox works. FCM needs a Firebase project + `google-services.json`.
- The session is stored in app-private SharedPreferences instead of expo-secure-store.
- The maps are plain canvases (as before), now placing pins by real coordinates; no Maps API key needed.
- Fixed: the pickup checklist items could not be ticked (they were disabled until all were ticked); the plot map's "Starting from" price always showed ₹0.
