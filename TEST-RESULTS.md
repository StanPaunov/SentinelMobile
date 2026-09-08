# Test results — Sentinel Mobile for older adults

Verified on 8 September 2026.

## Completed

- Clean Gradle build: debug APK, signed release APK, signed release AAB, and framework-only instrumentation APK.
- Android lint: **0 errors, 0 warnings** on the final source.
- **13 battery-estimator assertions passed**: charging, invalid readings, insufficient observation time, stable discharge, unstable discharge, background gaps, system estimates, and a non-increasing countdown.
- **31 device smoke assertions passed** on an Android 15 / API 35 emulator: scans, all five tabs, theme redraw, Bulgarian localization, three text sizes, persisted settings, refresh scheduling, real five-second refreshes, Never, horizontal swipe dispatch, copying full simulated GPS coordinates by long press, and returning from all five Android Settings destinations.
- The visible refresh dropdown was used to verify that the **30-second and one-minute timers actually refresh data**.
- UI tap tests exercised English / Bulgarian, light / dark themes, 1.8x system font scaling, 720×1280 at 320 dpi, denied location permissions, location services switched off, and swiping through all tabs.
- Signed release installation and cold launch on the emulator succeeded.
- The APK signature verifies with the existing Sentinel Mobile RSA-4096 key.
- The AAB validates using bundletool; its certificate matches the APK.
- AAB configuration explicitly disables language splitting. Both languages remain available offline.
- Final manifests report package com.stanpaunov.sentinelmobile, version 1.0.1, version code 2, min SDK 26, and target/compile SDK 36.
- Final APK requests exactly ACCESS_NETWORK_STATE, ACCESS_COARSE_LOCATION, and ACCESS_FINE_LOCATION. No INTERNET or broad package visibility.
- Source archive excludes signing keys, passwords, local.properties, caches, and build output.

Signing certificate SHA-256:

    7f5050c54f2c0f0250ec643399f7d375ee47858a6cc11c585b656411eee52986

## Coverage limits

No physical phone or API 26 / API 36 emulator was available for testing. Outdoor satellite acquisition, real battery discharge over hours, TalkBack interaction, and manufacturer-specific settings screens still need physical-device validation. GPS clipboard assertions used explicitly simulated coordinates.

The Windows sandbox exposed a JDK 21 ZIP-cleanup/canonical-path bug. Compilation completed using the external javac process; javac printed a cleanup diagnostic after successful compilation. The clean Gradle build, DEX packaging, lint, bundle validation, and device tests all completed successfully. This environment-specific build adjustment is not a runtime dependency of the app.

Android security checks are informational. The app does not certify that a device is secure.
