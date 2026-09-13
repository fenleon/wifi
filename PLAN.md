# WiFi — implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A LightOS tool (`com.lightphone.wifi`) that completes captive-portal sign-ins on the LP3 and joins WiFi networks by scanning the standard `WIFI:` QR.

**Architecture:** Single APK (the tasks/audiobooks pattern): `:app` is the plugin-scanned tool (Light screens, SDK `LightBarcodeScanner`); `:server` is an unscanned Android library merged into the APK holding every `WifiManager`/`ConnectivityManager`/WebView API plus the portal-login Activity. Same process — the tool calls the `:server` facade directly, no IPC. Binds to itself (`serverPackage = com.lightphone.wifi`).

**Tech stack:** Kotlin 2.3.20, AGP 8.12.3, Compose, Light SDK 0.1.0 (composite included build), ZXing via SDK `LightBarcodeScanner` (ML Kit/CameraX groups NOT excluded — unlike Tasks, we scan), kotlin-test + JUnit for `:server` unit tests.

**Spec:** `wifi/SPEC.md` (read both; the spec argues the design).

## Global Constraints

- **No Gradle builds, no unit-test runs, no `adb install` without the user's explicit per-instance permission** ("too many concurrent sessions"). Every gradle/adb step below is marked **[GATED]** — reach it, stop, ask.
- Build only via `tools/build --dir wifi :app:assembleDebug` (RAM guard); never two builds at once.
- `:app` module contains zero WifiManager/ConnectivityManager/WebView/Camera APIs (plugin ban) — all of it in `:server`.
- Light design system only: `LightText` variants, `gridUnitsAsDp`, `LightBottomBar`, no custom colors.
- minSdk 34, compileSdk 36, jvmTarget 17, signing = `../../light-sdk/sdk/keys/lightsdk-dev.jks`.
- Privacy: never log SSIDs' passwords or portal credentials; no verbose logging.
- Emulator: `serverPackage` stays our own id; the emulator flips nothing (we host the service).

## File map

```
wifi/
├── PLAN.md                        # this file
├── SPEC.md                        # approved spec
├── settings.gradle.kts            # + composite SDK substitution (copy tasks)
├── gradle.properties              # + sdkVersion=0.1.0
├── gradle/libs.versions.toml      # copy of tasks' (same versions)
├── gradlew, gradlew.bat, gradle/wrapper/*   # copied from tasks/
├── app/                           # the tool (plugin-scanned)
│   ├── build.gradle.kts
│   ├── lighttool.toml
│   └── src/main/kotlin/com/lightphone/wifi/screens/
│       ├── HomeScreen.kt          # @InitialScreen: status + portal banner + Scan QR
│       └── ScanScreen.kt          # LightBarcodeScanner → parse → suggest → joining
└── server/                        # unscanned Android library
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml    # uses-permissions + PortalActivity
        └── kotlin/com/lightphone/wifi/server/
            ├── WifiConnector.kt   # suggestions + state flow (WifiManager)
            ├── PortalDetector.kt  # probe classification (pure, testable) + fetcher
            ├── WifiQrCode.kt      # WIFI: MECARD parser (pure, testable)
            ├── ServerBootstrapProvider.kt  # SDK server wiring (tasks pattern)
            ├── PlatformRelay.kt   # copied verbatim-pattern from tasks/audiobooks
            ├── RelaySdkServerSettings.kt   # copied pattern from tasks
            └── PortalActivity.kt  # WebView + bindProcessToNetwork + probe + report
```

---

### Task 1: Project scaffold (buildable single-APK tool with a stub Home screen)

**Files:** Create: `settings.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradlew*` + `gradle/wrapper/*` (copied from `tasks/`), `app/build.gradle.kts`, `app/lighttool.toml`, `app/src/main/kotlin/com/lightphone/wifi/screens/HomeScreen.kt`, `server/build.gradle.kts`, `server/src/main/AndroidManifest.xml`, `server/src/main/kotlin/com/lightphone/wifi/server/{ServerBootstrapProvider,PlatformRelay,RelaySdkServerSettings}.kt` (last two copied from `tasks/server` with package renamed).

**Interfaces:**
- Produces: package `com.lightphone.wifi` everywhere; `:server` namespace `com.lightphone.wifi.server`; `LightWifiApplication` not needed (plugin generates the application class).

- [x] **Step 1: `settings.gradle.kts`** — copy `tasks/settings.gradle.kts` verbatim, rename `rootProject.name = "wifi"`, comments updated (tasks→wifi).

- [x] **Step 2: `gradle.properties`** — copy tasks' (`-Xmx4g`, `-Xmx3g`, android.useAndroidX, `sdkVersion=0.1.0`).

- [x] **Step 3: `gradle/libs.versions.toml`** — copy tasks' verbatim (same AGP/Kotlin/SDK versions).

- [x] **Step 4: wrapper** — `cp tasks/gradlew tasks/gradlew.bat wifi/ && cp -r tasks/gradle/wrapper wifi/gradle/wrapper && cp tasks/local.properties wifi/local.properties` (machine-specific, gitignored). Create `wifi/.gitignore` mirroring tasks'.

- [x] **Step 5: `app/lighttool.toml`**

```toml
[tool]
id = "com.lightphone.wifi"
label = "WiFi"
versionCode = 1
versionName = "0.1.0"
# Single-APK build: the merged :server library hosts the SDK server wiring and
# the portal-login Activity; the tool binds to itself (serverPackage = own id).
permissions = [
    "android.permission.INTERNET",
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.CAMERA",
]
serverPackage = "com.lightphone.wifi"
orientation = "portrait"
```

- [x] **Step 6: `app/build.gradle.kts`** — copy tasks', two changes: (a) keep `exclude(group = "com.google.mlkit")` but DELETE the `com.github.markusfisch` exclusion (zxing-cpp is the scanner backend `LightBarcodeScanner` uses; Tasks excluded it because it never scans — we do); keep the `androidx.camera` include. (b) Same `implementation(project(":server"))` + dev-signing config + R8 release block verbatim.

- [x] **Step 7: `server/build.gradle.kts`** — copy tasks' server module; add `implementation("androidx.webkit:webkit:1.13.0")` is NOT needed — platform WebView API only. Keep sdk-server, coroutines; add `testImplementation(libs.kotlin.test)`, `testImplementation(libs.junit)`. Namespace `com.lightphone.wifi.server`.

- [x] **Step 8: `server/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <!-- Normal install-time permissions; not in the tool-plugin allowlist, so
         they live here in the unscanned library (the Trixnity-in-chats seam). -->
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    <uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />

    <application>
        <provider
            android:name=".ServerBootstrapProvider"
            android:authorities="com.lightphone.wifi.server.bootstrap"
            android:exported="false" />

        <!-- Captive-portal sign-in. Not exported: launched only via
             startServerActivity from the tool's own screens. -->
        <activity
            android:name=".PortalActivity"
            android:exported="false"
            android:theme="@android:style/Theme.Material.NoActionBar" />
    </application>
</manifest>
```

- [x] **Step 9: `ServerBootstrapProvider.kt`** — copy `tasks/server/.../ServerBootstrapProvider.kt`, package renamed, `TaskRepository.init(...)` line removed (no store), everything else identical (client filter, dev-cert SHA-256 check, platform relay, `PlatformRelay.bind`).

- [x] **Step 10: stub `HomeScreen.kt`** — the tasks `LightScreen<Unit, VM>` pattern (HomeScreen.kt:170-183):

```kotlin
package com.lightphone.wifi.screens

import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.annotations.InitialScreen
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize

@InitialScreen
class HomeScreen(sealedActivity: SealedLightActivity) : LightScreen<Unit, HomeViewModel>(sealedActivity) {
    override val viewModelClass: Class<HomeViewModel> get() = HomeViewModel::class.java
    override fun createViewModel(): HomeViewModel = HomeViewModel()

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        LightTheme(colors = themeColors) {
            LightText("WiFi", variant = LightTextVariant.Heading,
                modifier = Modifier.fillMaxSize().background(LightThemeTokens.colors.background))
        }
    }
}
class HomeViewModel : com.thelightphone.sdk.LightViewModel()
```

  (Adjust imports to whatever compiles against the real SDK signatures — `@InitialScreen`'s exact package and `LightViewModel` constructor may differ; the scaffold's only contract is: `@InitialScreen` on a `LightScreen` subclass, build green. Check `tasks/HomeScreen.kt` imports.)

- [x] **Step 11: [GATED — build permission]** `tools/build --dir wifi :app:assembleDebug`. Expected: BUILD SUCCESSFUL; APK at `app/build/outputs/apk/debug/app-debug.apk`.

---

### Task 2: `WIFI:` QR parser (pure Kotlin, `:server`, unit-tested)

**Files:** Create: `server/src/main/kotlin/com/lightphone/wifi/server/WifiQrCode.kt`, Test: `server/src/test/java/com/lightphone/wifi/server/WifiQrCodeTest.kt`.

**Interfaces:**
- Produces (Task 6 consumes):
```kotlin
sealed interface WifiQrCode {
    val ssid: String
    val hidden: Boolean
    data class Open(override val ssid: String, override val hidden: Boolean) : WifiQrCode
    data class Psk(override val ssid: String, val password: String, override val hidden: Boolean, val wpa3: Boolean = false) : WifiQrCode
    data class Unsupported(override val ssid: String) : WifiQrCode   // WEP / enterprise
    companion object { fun parse(raw: String): WifiQrCode? }
}
```

- [x] **Step 1: failing tests** — one test class covering: full `WIFI:S:Home;T:WPA;P:pw123;;`; `T:WPA2`/`T:WPA3` map to `Psk`; `T:nopass` with no `P` → `Open`; `T:WEP` → `Unsupported`; `S` only → `Open`; `H:true` → `hidden=true`; escapes `\;` `\:` `\\` `\,` decoded inside `S` and `P`; garbage (`"not-wifi"`, `"WIFI:;;"`, missing `S`) → `null`.

```kotlin
class WifiQrCodeTest {
    @Test fun `wpa2 with password parses as psk`() {
        assertEquals(WifiQrCode.Psk("Home", "pw123", hidden = false), WifiQrCode.parse("WIFI:S:Home;T:WPA2;P:pw123;;"))
    }
    // ... open, wep→Unsupported, H:true, escapes, garbage cases
}
```

- [x] **Step 2: [GATED — build permission]** `tools/build --dir wifi :server:testDebugUnitTest`. Expected: new tests FAIL (unresolved reference).

- [x] **Step 3: implement `WifiQrCode.parse`** — strip optional `WIFI:`/`wifi:` prefix; split on unescaped `;` (scan for `\` escapes while splitting); map fields `S/P/T/H`; `T` ∈ {WPA, WPA2, WPA3, SAE} → Psk, WEP → Unsupported, nopass/absent → Open, anything else with `E:`/`I:`/`U:`/`R:` fields → Unsupported(ssid). MECARD unescape `\X` → `X` for the reserved four. Return null when no `S`.

- [x] **Step 4: [GATED]** run tests, expect PASS.

- [x] **Step 5: commit** `git add wifi/server && git commit -m "wifi: WIFI: QR payload parser"`

---

### Task 3: `PortalDetector` (probe classification pure + thin fetcher, `:server`, unit-tested)

**Files:** Create: `server/src/main/kotlin/com/lightphone/wifi/server/PortalDetector.kt`, Test: `server/src/test/java/com/lightphone/wifi/server/PortalDetectorTest.kt`.

**Interfaces:**
- Produces:
```kotlin
object PortalDetector {
    /** Pure: classify an HTTP probe response. */
    fun classify(statusCode: Int, locationHeader: String?): PortalProbeResult
    suspend fun probe(network: Network?): PortalProbeResult   // HttpURLConnection on probe URL, 10s timeouts
}
sealed interface PortalProbeResult {
    data object Open : PortalProbeResult                       // 204 → validated
    data class Portal(val url: String) : PortalProbeResult     // 302 Location, or 200 → probe URL itself
    data class Error(val message: String) : PortalProbeResult
}
```

- [x] **Step 1: failing tests** — `classify(204, null)` → Open; `classify(302, "http://portal/x")` → Portal(that url); `classify(200, null)` → Portal(PROBE_URL) (the probe URL was served — classic portal); `classify(500, null)` → Error.
- [x] **Step 2: [GATED]** run, expect FAIL.
- [x] **Step 3: implement** — `PROBE_URL = "http://connectivitycheck.gstatic.com/generate_204"`; `probe` runs the GET on a `Dispatchers.IO` coroutine, `network?.socketFactory ?: default`, calls `classify`. No logging of portal URLs (they can carry room-number tokens — privacy rule).
- [x] **Step 4: [GATED]** tests PASS.
- [x] **Step 5: commit** `git commit -m "wifi: captive-portal probe classifier"`

---

### Task 4: `WifiConnector` (suggestions + state, `:server`, device-verified)

**Files:** Create: `server/src/main/kotlin/com/lightphone/wifi/server/WifiConnector.kt`.

**Interfaces:**
- Consumes: `WifiQrCode` (Task 2), `PortalDetector` (Task 3).
- Produces (Tasks 5/6/7 consume):
```kotlin
object WifiConnector {
    sealed interface State {
        data object Off : State
        data object Disconnected : State
        data object Validated : State            // connected, no portal
        data class Portal(val ssid: String) : State
    }
    val state: StateFlow<State>
    /** Submits a suggestion from a parsed QR. Returns error string? or null. */
    fun suggest(context: Context, code: WifiQrCode): String?
    fun start(context: Context)                    // registerNetworkCallback + initial probe, idempotent
    fun stop()
}
```

- [x] **Step 1: implement** — `suggest()`: map `Psk` → `WifiNetworkSuggestion.Builder().setSsid(ssid).setWpa2Passphrase(pw)` (WPA3 → `setWpa3Passphrase`), `Open` → ssid only, `Unsupported` → error "not supported"; remove the previous suggestion list then `addNetworkSuggestions(list)`; map status codes (`STATUS_NETWORK_SUGGESTIONS_ERROR_…`) to short user strings. `start()`: `ConnectivityManager.registerNetworkCallback(NetworkRequest TRANSPORT_WIFI)` watching `NET_CAPABILITY_VALIDATED` + `WifiManager.connectionInfo?.ssid` for the portal banner text; on caps change without VALIDATED → run `PortalDetector.probe(network)`. No polling loop — callback-driven only (battery rule). Suggestion cap: send at most one suggestion at a time.
- [x] **Step 2: no unit test possible** (WifiManager is a mocked-API swamp; value is on-device) — Phase 0 spike (Task 7) is its test. Keep the class < 150 lines.
- [x] **Step 3: commit** `git commit -m "wifi: suggestion submission + validated-state observation"`

---

### Task 5: Home screen (status + portal banner + Scan QR)

**Files:** Create (replaces stub): `app/src/main/kotlin/com/lightphone/wifi/screens/HomeScreen.kt`.

**Interfaces:** Consumes `WifiConnector.state`, `WifiConnector.suggest`, `startServerActivity("com.lightphone.wifi/com.lightphone.wifi.server.PortalActivity")` (SDK `LightScreen.startServerActivity` — the Location-tool pattern). Produces: nothing.

- [x] **Step 1: UI** — `LightScreen<Unit, HomeViewModel>`; collect `WifiConnector.state`; render per state: `Validated` → "Connected" + ssid-free (privacy: no SSID display? show it — it's the user's own device; keep it, it aids orientation) + "SCAN QR" bottom-bar button → `navigateTo(ScanScreen)`; `Portal(ssid)` → banner row "Sign in to <ssid>" (Heading, underlined, `lightClickable`) + explanation Copy line "This network requires sign-in" + the same bottom bar; `Disconnected`/`Off` → "Not connected" + bottom bar; `connecting-after-scan` → "Joining <ssid>…" (Task 6 writes a pending-join state into the VM). Design: tasks `ConfirmDeleteScreen` grammar — Column, generous grid-unit padding, `LightBottomBar` CANCEL-less single-action right slot. Banner tap → `startServerActivity(...)` with the flattened component name.
- [x] **Step 2: [GATED — build + emulator]** build, install, `am start`, `uiautomator dump` shows states (emulator wifi off = "Not connected").
- [x] **Step 3: commit** `git commit -m "wifi: home screen with portal banner"`

---

### Task 6: Scan screen (SDK scanner → parse → suggest → joining)

**Files:** Create: `app/src/main/kotlin/com/lightphone/wifi/screens/ScanScreen.kt`.

**Interfaces:** Consumes `LightBarcodeScanner` (SDK, the passes ScanScreen pattern), `WifiQrCode.parse`, `WifiConnector.suggest`. Produces: pops back with the parsed ssid for the Home "Joining…" state.

- [x] **Step 1: UI** — copy the `passes/ScanScreen.kt` skeleton: `SimpleLightScreen<String?>`, full-screen `LightBarcodeScanner(title = "Scan WiFi QR", onScanned = …, onBack = { goBack() })`, no TYPE-A-CODE bar item (non-goal). `onScanned` → `WifiQrCode.parse(code.value)`; null/Unsupported → inline `LightText` error overlay ("Not a WiFi code" / "Enterprise networks not supported"), stay scanning; Open/Psk → `WifiConnector.suggest(code)`; null error → overlay text; success → `goBack(ssid)`.
- [x] **Step 2: [GATED — build + emulator]** build, install; generate a real `WIFI:` QR (any online generator, open network) on a second screen or `qrencode -t UTF8`, hold to emulator camera (emulator has a virtual scene with a QR injection path — `adb emu sensor set` alternative: use the extended-controls camera image), verify "Joining…" + suggestion dialog.
- [x] **Step 3: commit** `git commit -m "wifi: QR scan screen"`

---

### Task 7: `PortalActivity` (WebView sign-in, `:server`)

**Files:** Create: `server/src/main/kotlin/com/lightphone/wifi/server/PortalActivity.kt`.

**Interfaces:** Consumes `WifiConnector.state`/`PortalDetector`. Launched via `startServerActivity` from Home (`startServerActivity` carries no extras); reads `ssid`/`portalUrl` from `WifiConnector.state` (same process). Produces: closes itself on success.

- [x] **Step 1: implement** — plain Activity (Material NoActionBar theme), vertical LinearLayout: a thin `ProgressBar` (horizontal) + `WebView`. On create: `ConnectivityManager.bindProcessToNetwork(wifiNetwork)` (the bound Network from `WifiConnector`; if null → finish with error text); `webView.settings { javaScriptEnabled; domStorageEnabled; userAgentString = default + nothing custom }`; `loadUrl(portalUrl)` (passed via extra from `WifiConnector`'s last probe). `webViewClient = object : WebViewClient() { onPageFinished → launch { when (PortalDetector.probe(boundNetwork)) { Open → reportNetworkConnectivity(boundNetwork, true); setResult + finish } else → hide progress } }`; redirects handled naturally by WebView. `shouldOverrideUrlLoading` returns false always (stay in WebView). Handle back: `onBackPressed → webView.canGoBack() ? goBack() : finish()`. A "DONE" button bottom → probe once more, then finish regardless (user may know better).
- [x] **Step 2: [GATED — emulator with fake portal]** fake-portal rig: `adb shell settings put global captive_portal_http_url ...` is platform-internal — instead verify end-to-end via a real hotspot from the host or `adb reverse`ed 302 server as the probe URL through system properties (see SPEC "Open questions"): the cheap emulator check is to connect the emulator to a WiFi network whose gateway returns 302 on the probe, observe Home banner, tap through, page loads, probe flips, activity closes. If the rig is too heavy, validate the Activity loads arbitrary URLs and the probe logic via the existing unit tests, and mark full portal verification for a real-network test.
- [x] **Step 3: commit** `git commit -m "wifi: portal sign-in activity"`

---

### Task 8: Phase 0 spike — suggestion join on emulator, then real LP3

**Files:** none (verification only). Spec section "Phase 0".

- [ ] **Step 1: [GATED]** emulator: install, scan an open-network QR, confirm the platform approval dialog appears once and the network joins (emulator AVD wifi via extended controls).
- [ ] **Step 2: [GATED + user drives device]** real LP3 with External tools = "All tools": same flow. **This is the go/no-go for the QR feature** (spec open question). Record the outcome in `WORKLOG.md`.
- [ ] **Step 3:** also record: does `net.http_url` system property exist for a normal app (`adb shell getprop net.http_url`)? If yes, read it in `PortalDetector.probe` instead of the hardcoded gstatic URL (one-line change).

---

### Task 9: Docs + governance

- [x] **Step 1:** root `AGENTS.md` Layout section: add `wifi/` one-liner (id, single-APK pattern, build cmd, `wifi/SPEC.md` pointer) — keeps the index rule (root ≤ 180 lines).
- [x] **Step 2:** `WORKLOG.md` top entry: session, what was built, Phase 0 outcome (fill after Task 8).
- [x] **Step 3:** `tools/check-agents-size` passes.
- [x] **Step 4:** `lightos-design` skill review over HomeScreen/ScanScreen (end-of-session gate).

## Verification summary

| What | How | Gate |
|---|---|---|
| Scaffold compiles | `tools/build --dir wifi :app:assembleDebug` | permission |
| QR parser | `:server:testDebugUnitTest` | permission |
| Scanner + suggestion | emulator + QR image | permission (build+install) |
| Portal WebView | emulator fake portal / real network | permission |
| LP3 suggestion join | real device, user drives | user |
