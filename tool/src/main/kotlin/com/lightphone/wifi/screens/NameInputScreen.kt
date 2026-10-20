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
import com.thelightphone.sdk.ui.LightTextInputEditor
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.scaledForScreenHeight
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The network-name editor — the tasks TitleEditor idiom: LP3 keyboard up
 * immediately, no caps (SSIDs are case-sensitive), SAVE returns the trimmed
 * text ("" clears); back keeps the old value.
 */
class NameInputScreen(
    sealedActivity: SealedLightActivity,
    private val initial: String,
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
                title = "Network Name",
                state = textState,
                keyboardOptionsFlow = keyboardOptionsFlow,
                onSubmit = { result -> goBack(result.toString().trim()) },
                onBack = { goBack() },
                modifier = Modifier.background(LightThemeTokens.colors.background),
                submitLabel = "SAVE",
                bottomAligned = false,
                centered = true,
                singleLine = true,
                initialCaps = false,
                inputTextStyle = inputStyle,
            )
        }
    }
}
