package com.lightphone.wifi.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.lightphone.wifi.server.WifiConnector
import com.lightphone.wifi.server.WifiQrCode
import com.thelightphone.sdk.LightBarcodeScanner
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp

/**
 * The add-network panel: the QR scanner fills the screen; ADD MANUAL in the
 * bottom bar swaps to [ManualAddScreen]. A scanned `WIFI:` QR submits its
 * suggestion and pops back (Home shows "Connecting to …"); a non-WiFi or
 * unsupported payload stays scanning with an inline error.
 */
class AddNetworkScreen(sealedActivity: SealedLightActivity) :
    SimpleLightScreen<Unit>(sealedActivity) {

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        var error by remember { mutableStateOf<String?>(null) }
        var scannedCode by remember { mutableStateOf<WifiQrCode?>(null) }

        // A code was decoded: run the suggestion + pop back outside the
        // scanner's frame callback (the passes ScanScreen pattern).
        LaunchedEffect(scannedCode) {
            val code = scannedCode ?: return@LaunchedEffect
            val joinError = WifiConnector.suggest(code)
            if (joinError != null) error = joinError
            else goBack()
        }

        LightTheme(colors = themeColors) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                LightBarcodeScanner(
                    title = "Add Network",
                    onScanned = { scanned ->
                        val code = WifiQrCode.parse(scanned.value)
                        when (code) {
                            null -> error = "Not a WiFi code"
                            is WifiQrCode.Unsupported -> error = "Network not supported"
                            is WifiQrCode.Open, is WifiQrCode.Psk -> scannedCode = code
                        }
                    },
                    onBack = { goBack() },
                    modifier = Modifier.background(LightThemeTokens.colors.background),
                )
                error?.let {
                    LightText(
                        text = it,
                        variant = LightTextVariant.Copy,
                        align = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 3f.gridUnitsAsDp()),
                    )
                }
                LightBottomBar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding(),
                    items = listOf(
                        LightBarButton.Text(
                            text = "ADD MANUAL",
                            // Manual's back AND its JOIN both land on Home:
                            // this screen pops alongside the manual panel.
                            onClick = {
                                navigateTo(screenFactory = { ManualAddScreen(it) }) { goBack() }
                            },
                        ),
                    ),
                )
            }
        }
    }
}
