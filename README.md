# IPv64net Android App

The official Android app for [IPv64.net](https://ipv64.net/): DynDNS, healthchecks, CDN64 and your Node64 nodes in one place.

Current version: **2.1.0** — see [CHANGELOG.md](CHANGELOG.md) for what changed.

## Features

The app has five tabs:

*   **DynDNS:** Your domains with IP status, DNS records, manual DynDNS updates and update URLs.
*   **Healthchecks:** Healthchecks 2.0 of your account, plus the local checks of every connected node. Detail view with status, statistics, incidents and events.
*   **CDN64 / Proxy64:** Without a node, the CDN64 cloud servers. With a connected node that runs the cdn64 module, the tab becomes **Proxy64**: proxy hosts (create, edit, enable/disable), CDN64 per host with mode choice, and CDN statistics.
*   **DNS64:** Shown when a connected node runs the dns64 module. Dashboard with blocking (pause for 5 min to "until resumed"), query timeline, top lists, upstreams and cache, a live query log and local DNS records.
*   **More:**
    *   Account, API keys and account links
    *   Nodes: link a node to your account or connect directly by local or public IP and API key
    *   Push notifications: channels and notification settings
    *   Security: IP lookup & My IP, password check, Honeypot
    *   Logs, lockscreen (biometric), YouTube and Discord

Push notifications are end-to-end encrypted via [SecNtfy](https://secntfy.app). After the first login the app asks for the notification permission and registers the device.

## Requirements

*   Android 12 (API 31) or newer
*   An IPv64.net account and API key (log in by entering the key or scanning its QR code)

## Technologies

*   **Kotlin** and **Jetpack Compose** with Material 3
*   **[IPv64-Package](https://github.com/androidseb25/IPv64-Package):** client for the IPv64 API v2, Healthchecks 2.0 and the Node64 API
*   **SecNtfy-Android** and **Firebase Cloud Messaging** for push notifications
*   **CameraX** and **ZXing** for the QR code scanner
*   **AndroidX Biometric** for the lockscreen

## Building the Project

1.  Clone the repository: `git clone https://github.com/androidseb25/IPv64-Android.git`
2.  Put your Firebase config at `app/google-services.json` (not in the repo; CI writes it from the `GOOGLE_SERVICES_JSON` secret).
3.  Open the project in Android Studio, or build from the terminal: `./gradlew :app:assembleDebug`
4.  Run the unit tests: `./gradlew :app:testDebugUnitTest`

### Mock API for development

`tools/mockapi` is a dummy IPv64 API (cloud, Healthchecks 2.0, two Node64 nodes, SecNtfy registration) with fictitious data, for screenshots and setup tests on the emulator:

```bash
python3 tools/mockapi/server.py --port 8064
./gradlew :app:installDebug -PmockApi=http://10.0.2.2:8064
```

Log in with the API key `demo`. Details in [tools/mockapi/README.md](tools/mockapi/README.md).

## Releases

Pushing to `main` builds a signed APK/AAB and creates a GitHub release (see `.github/workflows/android-release.yml`).

## Contributing

Contributions are welcome! Please open an issue or submit a pull request. Questions: [Discord](https://discord.gg/rpicloud).
