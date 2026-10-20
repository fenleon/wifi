package com.lightphone.wifi.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.thelightphone.lp3Keyboard.ui.KeyboardOptions
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightTextInputEditor
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.scaledForScreenHeight
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The native password-entry screen: two-line title ("Enter Password for /
 * {ssid}"), the SDK text editor (the Add-Manual name-input idiom — real
 * cursor, LP3 keyboard, no AOSP IME) with JOIN as the bottom-bar center
 * button. Typed text is plain here; dots appear only on the manual panel's
 * Password row.
 */
class PasswordEntryScreen(
    sealedActivity: SealedLightActivity,
    private val ssid: String,
    private val initial: String = "",
    // From the scan list: top bar shows the network name and an "Enter
    // Password" tag sits above the input (the Radio search-panel pattern).
    // From the manual panel: the title IS "Enter Password", no tag.
    private val tagged: Boolean = true,
) : SimpleLightScreen<String>(sealedActivity) {

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val keyboardOptionsFlow = remember {
            MutableStateFlow(
                KeyboardOptions(
                    emojis = emptyList(),
                    displayReturn = false,
                    displayVoice = false,
                    enableKeyAnimation = true,
                    swipeEnabled = false,
                ),
            )
        }
        val inputStyle = LightThemeTokens.typography.heading
            .copy(color = LightThemeTokens.colors.content)
            .scaledForScreenHeight()
        val textState = rememberTextFieldState(initial)

        LightTheme(colors = themeColors) {
            LightTextInputEditor(
                title = if (tagged) ssid else "Enter Password",
                state = textState,
                keyboardOptionsFlow = keyboardOptionsFlow,
                onSubmit = { goBack(textState.text.toString()) },
                onBack = { goBack() },
                modifier = Modifier.background(LightThemeTokens.colors.background),
                // Scan-list networks JOIN; the manual flow's editor SAVEs the
                // draft password into its panel (which then JOINs).
                submitLabel = if (tagged) "JOIN" else "SAVE",
                bottomAligned = true,
                centered = true,
                singleLine = true,
                initialCaps = false,
                inputTextStyle = inputStyle,
                submitBottomRight = false,
                bottomBarCenterButton = LightBarButton.Text(
                    text = if (tagged) "JOIN" else "SAVE",
                    onClick = { goBack(textState.text.toString()) },
                ),
                topTag = if (tagged) {
                    { LightText("Enter Password", variant = LightTextVariant.Detail) }
                } else {
                    null
                },
            )
        }
    }
}
