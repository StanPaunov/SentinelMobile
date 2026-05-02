# Sentinel Mobile

Sentinel Mobile is the Android monitoring app from the PC Sentinel project, moved into its own repository. It keeps the same dark monitoring style as PC Sentinel NET while reporting only the Android device, network, storage, location, and security information that normal Android apps are allowed to access.

Website: https://stanpaunov.github.io/SentinelMobile/

Desktop companion: https://stanpaunov.github.io/PCSentinelNet/

Desktop repository: https://github.com/StanPaunov/PCSentinelNet

## Download

Use the GitHub release APK:

https://github.com/StanPaunov/SentinelMobile/releases

If the separate repository release has not been created yet, the current APK is also available in the original project release:

https://github.com/StanPaunov/PCSentinelNet/releases/tag/v1.0-android

## What It Monitors

- GPS coordinates, with hold-to-copy support.
- Battery level, charging state, health, temperature, voltage, and calculated remaining time after the app has observed real discharge data.
- Memory usage through `ActivityManager`.
- Internal storage usage through `StatFs`.
- Aggregate upload/download rate through `TrafficStats`.
- Active network transport, VPN state, metered state, and interface inventory.
- Network, Security, Storage, and Device settings shortcuts.
- Device information such as manufacturer, model, Android version, build, hardware, and security patch.
- Security checks where Android allows access:
  - secure screen lock state
  - USB debugging setting
  - unknown app install permission for this app
  - VPN activity
  - Private DNS mode
  - developer options state

## Android Limits

Android does not expose global TCP listening ports, firewall profiles, Microsoft Defender, or full process CPU data to normal apps. Those features require root access, device-owner management APIs, or the Windows desktop app.

Location is used only for the GPS coordinate card. Copied GPS coordinates are automatically cleared from the clipboard when unchanged.

Default refresh interval is `1 minute`; the app also supports `5 seconds`, `30 seconds`, and `Never`.
