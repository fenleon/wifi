package com.lightphone.wifi.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
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
 * The security-protocol picker (the Tasks category-picker idiom): Heading rows,
 * the selected one underlined (thin 2dp bar — the picker selection rule), tap
 * picks and returns. Top title "Security".
 */
class SecurityPickerScreen(
    sealedActivity: SealedLightActivity,
    private val initial: Security,
) : SimpleLightScreen<Security>(sealedActivity) {

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
                    center = LightTopBarCenter.Text("Security"),
                )
                Box(modifier = Modifier.weight(1f)) {
                    LightScrollView {
                        Security.entries.forEach { security ->
                            PickerRow(
                                text = security.label,
                                selected = security == initial,
                                onClick = { goBack(security) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Heading row, underlined when selected — the picker selection rule. */
@Composable
private fun PickerRow(text: String, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .lightClickable(onClick = onClick)
            .padding(horizontal = 2f.gridUnitsAsDp(), vertical = 1.2f.gridUnitsAsDp()),
    ) {
        LightText(
            text = text,
            variant = LightTextVariant.Heading,
            modifier = if (selected) Modifier.thinUnderline() else Modifier,
        )
    }
}

/** The pickers' selection underline: a ~2dp bar at the text's bottom edge —
 *  thinner than LightText's 4dp selection bar (the tasks picker idiom). */
@Composable
internal fun Modifier.thinUnderline(): Modifier {
    val density = LocalDensity.current
    val content = LightThemeTokens.colors.content
    val thicknessPx = with(density) { 2.dp.toPx() }
    return drawBehind {
        drawRect(
            color = content,
            topLeft = Offset(0f, size.height - thicknessPx),
            size = Size(size.width, thicknessPx),
        )
    }
}
