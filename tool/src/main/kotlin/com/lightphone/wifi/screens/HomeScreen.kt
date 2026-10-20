package com.lightphone.wifi.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lightphone.wifi.server.WifiConnector
import com.lightphone.wifi.server.WifiPermissionActivity
import com.lightphone.wifi.server.WifiQrCode
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.delay

private const val NATIVE_WIFI_SETTINGS =
    "com.android.settings/.Settings\$WifiSettingsActivity"

private const val LOCATION_SETTINGS =
    "com.android.settings/.Settings\$LocationSettingsActivity"

/**
 * The native Wi-Fi screen, mirrored pixel-for-pixel (native captures in
 * `screenshots/`): top bar (back + "Wifi"), the toggle row with its state
 * caption (Off / Connecting to X / Requires sign on / Connected / Scanning... /
 * X networks found), the connected network's row (Wi-Fi glyph + name — the
 * "Requires sign on" state shows the no-internet glyph), then the
 * "Other networks" scan list. ADD NETWORK in the bottom bar opens the
 * add-network panel.
 *
 * The toggle flips the radio through [WifiConnector.toggleWifi]; firmwares
 * that block it fall back to the native Wi-Fi screen (the Location-tool
 * pattern). The scan loop runs only while this screen is composed.
 */
@InitialScreen
class HomeScreen(sealedActivity: SealedLightActivity) :
    SimpleLightScreen<Unit>(sealedActivity) {

    init {
        WifiConnector.start()
    }

    // The framework location switch gates scan results AND SSID reads for
    // normal apps; refresh on every show (the Location-tool pattern). The
    // tick forces the scan loop's keys to re-evaluate even when the values
    // didn't change (e.g. only the location permission was granted).
    private var locationOn by mutableStateOf(true)
    private var showTick by mutableStateOf(0)

    override fun willShow() {
        locationOn = lightContext.locationEnabled
        showTick++
        WifiConnector.reevaluate()
    }

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val wifiEnabled by WifiConnector.wifiEnabled.collectAsState()
        val wifiState by WifiConnector.state.collectAsState()
        val scans by WifiConnector.scanEntries.collectAsState()
        val scanning by WifiConnector.scanning.collectAsState()
        val scanBlocked = WifiConnector.scanBlocked()
        val connectingSsid by WifiConnector.pendingSsid.collectAsState()
        val connectedSsid by WifiConnector.connectedSsid.collectAsState()

        // Foreground-only scan loop (battery rule). Pauses while the radio is
        // off or the platform would refuse results (location off / no grant).
        LaunchedEffect(wifiEnabled, locationOn, scanBlocked, showTick) {
            if (!wifiEnabled || !locationOn || scanBlocked) return@LaunchedEffect
            while (true) {
                WifiConnector.refreshScan()
                delay(10_000)
            }
        }

        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(
                        icon = LightIcons.BACK,
                        onClick = { exitTool() },
                        contentDescription = "Exit to tools",
                    ),
                    center = LightTopBarCenter.Text("Wifi"),
                )
                ToggleRow(
                    enabled = wifiEnabled,
                    caption = caption(
                        wifiEnabled = wifiEnabled,
                        locationOn = locationOn,
                        permission = !scanBlocked,
                        connectingSsid = connectingSsid,
                        state = wifiState,
                        scanning = scanning,
                        count = scans.size,
                    ),
                    onClick = {
                        if (!WifiConnector.toggleWifi()) {
                            startServerActivity(NATIVE_WIFI_SETTINGS)
                        }
                    },
                )
                if (wifiEnabled) {
                    val portal = wifiState as? WifiConnector.State.Portal
                    val network = portal?.ssid ?: connectedSsid
                    if (!locationOn) {
                        // The platform redacts the connected SSID while the
                        // location switch is off — show the fix, not a stale
                        // network name.
                        LocationPromptRow(
                            onClick = { startServerActivity(LOCATION_SETTINGS) },
                        )
                    } else if (network != null && (wifiState is WifiConnector.State.Validated || portal != null)) {
                        NetworkRow(
                            ssid = network,
                            portal = portal != null,
                            onClick = {
                                navigateTo(
                                    screenFactory = {
                                        NetworkDetailScreen(it, network, portal != null)
                                    },
                                )
                            },
                        )
                    }
                    if (locationOn && scans.isNotEmpty()) {
                        LightText(
                            text = "Other networks",
                            variant = LightTextVariant.Detail,
                            modifier = Modifier.padding(
                                top = 1f.gridUnitsAsDp(),
                                start = 2f.gridUnitsAsDp(),
                            ),
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        LightScrollView {
                            if (scanBlocked) {
                                LightText(
                                    text = "Allow location to scan networks",
                                    variant = LightTextVariant.Detail,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .lightClickable {
                                            WifiPermissionActivity.defaultPermission =
                                                android.Manifest.permission.ACCESS_FINE_LOCATION
                                            startServerActivity(
                                                "com.lightphone.wifi/" +
                                                    "com.lightphone.wifi.server.WifiPermissionActivity",
                                            )
                                        }
                                        .padding(horizontal = 2f.gridUnitsAsDp(), vertical = 0.75f.gridUnitsAsDp()),
                                )
                            } else if (locationOn) {
                                scans
                                    .filter { it.ssid != network }
                                    .forEach { entry ->
                                        LightText(
                                            text = entry.ssid,
                                            variant = LightTextVariant.Heading,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .lightClickable {
                                                    if (entry.secured) {
                                                        navigateTo(
                                                            screenFactory = {
                                                                PasswordEntryScreen(it, entry.ssid)
                                                            },
                                                        ) { password ->
                                                            if (password is String && password.isNotEmpty()) {
                                                                WifiConnector.suggest(
                                                                    WifiQrCode.Psk(entry.ssid, password, hidden = false),
                                                                )
                                                            }
                                                        }
                                                    } else {
                                                        WifiConnector.suggest(
                                                            WifiQrCode.Open(entry.ssid, hidden = false),
                                                        )
                                                    }
                                                }
                                                .padding(horizontal = 2f.gridUnitsAsDp(), vertical = 0.5f.gridUnitsAsDp()),
                                        )
                                    }
                            }
                        }
                    }
                } else {
                    Box(modifier = Modifier.weight(1f))
                }
                LightBottomBar(
                    modifier = Modifier.navigationBarsPadding(),
                    items = listOf(
                        LightBarButton.Text(
                            text = "ADD NETWORK",
                            onClick = { navigateTo(screenFactory = { AddNetworkScreen(it) }) },
                        ),
                    ),
                )
            }
        }
    }
}

/** The state line under "Wifi" — priority: Off > blocked > Connecting > portal/connected > scanning > count. */
private fun caption(
    wifiEnabled: Boolean,
    locationOn: Boolean,
    permission: Boolean,
    connectingSsid: String?,
    state: WifiConnector.State,
    scanning: Boolean,
    count: Int,
): String = when {
    !wifiEnabled -> "Off"
    connectingSsid != null -> "Connecting to $connectingSsid"
    state is WifiConnector.State.Portal -> "Requires sign on"
    state is WifiConnector.State.Validated -> "Connected"
    !locationOn -> "Enable location to scan"
    !permission -> "Allow location to scan"
    scanning -> "Scanning..."
    else -> if (count == 0) "Scanning..." else "$count networks found"
}

/** The toggle row: glyph top-aligned left of "Wifi" (the Location-tool grammar). */
@Composable
private fun ToggleRow(enabled: Boolean, caption: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .lightClickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier.size(36.dp),
            contentAlignment = Alignment.Center,
        ) {
            LightIcon(
                icon = if (enabled) LightIcons.TOGGLE_STATE_ON else LightIcons.TOGGLE_STATE_OFF,
                size = 2f,
                contentDescription = if (enabled) "Wi-Fi on" else "Wi-Fi off",
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            LightText(text = "Wifi", variant = LightTextVariant.Heading)
            LightText(
                text = caption,
                variant = LightTextVariant.Detail,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * The location-off prompt (the NetworkRow's slot): the platform redacts scan
 * results AND the connected SSID while the location switch is off, so the row
 * offers the fix instead of a possibly stale network name. Text button —
 * native Settings grammar, no glyph, no toggle.
 */
@Composable
private fun LocationPromptRow(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .lightClickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 22.dp),
    ) {
        LightText(text = "Enable Location", variant = LightTextVariant.Heading)
        LightText(
            text = "to scan networks",
            variant = LightTextVariant.Detail,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** The connected network's row: glyph + name, opens the detail panel. */
@Composable
private fun NetworkRow(ssid: String, portal: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .lightClickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LightIcon(
            icon = if (portal) LightIcons.WIFI_NO_INTERNET else LightIcons.WIFI,
            size = 2f,
            contentDescription = if (portal) "Connected, sign-in required" else "Connected",
        )
        Spacer(Modifier.width(12.dp))
        LightText(text = ssid, variant = LightTextVariant.Heading)
    }
}
