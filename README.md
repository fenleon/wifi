# Wifi

A Wi-Fi tool for the Light Phone 3. LightOS can join networks, but it has no
login pages for networks that need one, and no way to scan a QR to connect.
This tool fills those gaps.

## What it does

- **Scan a QR to connect.**
- **Portal sign-in.** Some networks (hotels, cafés, work) make you sign in on
  a web page before the internet works. LightOS never shows that page. This
  tool detects it and opens the sign-in page for you.
- **Manual entry.** Type the network name, pick the security (None, WPA, WPA2,
  WPA3), and enter the password.

## Install

Sideload the APK from the
[releases page](https://github.com/fenleon/wifi/releases). In LightOS settings,
set **External tools** to **All tools** (the app is not Light-signed).

## Notes

- Scanning for networks needs the phone's Location switched on, and a one-time
  location permission. This is an Android rule for all apps — nearby network
  names can reveal where you are.
- Networks joined through this tool are remembered by the tool, not by
  LightOS settings. To remove one, they have to be disconnected through this tool. This is a restriction of how Android provisions Wifi connections.
- Enterprise (EAP) and old WEP networks are not supported.

## Building

Building from source needs the Light SDK as a local included build next to
this repo (folder name `light-sdk`) — **with the workspace's SDK patches**,
since this tool uses one of them (`exitTool`; see the light-phone workspace's
`LIGHT-SDK-PATCHES.md`). If you just want the app, use the release APK.

```sh
git clone <patched light-sdk> ../light-sdk
./gradlew :app:assembleDebug   # APK at app/build/outputs/apk/debug/
```
