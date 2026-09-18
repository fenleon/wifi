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
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.scaledForScreenHeight
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * One portal-form field (SPEC §2b): the SDK editor with the field's label as
 * the title and NEXT / CONNECT as the submit — the PasswordEntryScreen idiom
 * (real cursor, LP3 keyboard, no AOSP IME). Text returns plain; no masking
 * (the plain editor is what the SDK screen offers, and portal passwords are
 * the rare field).
 */
class PortalFieldScreen(
    sealedActivity: SealedLightActivity,
    private val label: String,
    private val submitLabel: String,
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
        val textState = rememberTextFieldState()

        LightTheme(colors = themeColors) {
            LightTextInputEditor(
                title = label,
                state = textState,
                keyboardOptionsFlow = keyboardOptionsFlow,
                onSubmit = { goBack(textState.text.toString()) },
                onBack = { goBack() },
                modifier = Modifier.background(LightThemeTokens.colors.background),
                submitLabel = submitLabel,
                bottomAligned = true,
                centered = true,
                singleLine = true,
                initialCaps = false,
                inputTextStyle = inputStyle,
                submitBottomRight = false,
                bottomBarCenterButton = LightBarButton.Text(
                    text = submitLabel,
                    onClick = { goBack(textState.text.toString()) },
                ),
            )
        }
    }
}
