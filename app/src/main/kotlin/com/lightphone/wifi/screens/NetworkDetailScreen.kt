package com.lightphone.wifi.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.lightphone.wifi.server.WifiConnector
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
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

/**
 * The connected network's detail panel (the native "Forget Network" panel):
 * title = the network name, "Sign in to Network" when the network sits behind
 * a captive portal (opens the portal screen), "Forget Network" below —
 * withdrawing the suggestion, the only removal path for suggestion-joined
 * networks (LP3 Settings' forget is a no-op for them). Forget pops back;
 * Home shows the radio state, not the connection.
 */
class NetworkDetailScreen(
    sealedActivity: SealedLightActivity,
    private val ssid: String,
    private val portal: Boolean,
) : SimpleLightScreen<Unit>(sealedActivity) {

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()

        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(
                        icon = LightIcons.BACK,
                        onClick = { goBack() },
                        contentDescription = "Back",
                    ),
                    center = LightTopBarCenter.Text(ssid),
                )
                LightScrollView {
                    if (portal) {
                        LightText(
                            text = "Sign in to Network",
                            variant = LightTextVariant.Heading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .lightClickable {
                                    startServerActivity(
                                        "com.lightphone.wifi/com.lightphone.wifi.server.PortalActivity",
                                    )
                                    goBack()
                                }
                                .padding(horizontal = 1f.gridUnitsAsDp(), vertical = 0.75f.gridUnitsAsDp()),
                        )
                    }
                    // Forget only exists for networks WE suggested — Settings'
                    // forget is a no-op for those, and ours is a no-op for
                    // Settings-joined ones (system-owned).
                    if (WifiConnector.ownsSsid(ssid)) {
                        LightText(
                            text = "Forget Network",
                            variant = LightTextVariant.Heading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .lightClickable {
                                    WifiConnector.forget()
                                    goBack()
                                }
                                .padding(horizontal = 1f.gridUnitsAsDp(), vertical = 0.75f.gridUnitsAsDp()),
                        )
                    }
                }
            }
        }
    }
}
