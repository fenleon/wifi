package com.lightphone.wifi.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.lightphone.wifi.server.PortalSignIn
import com.lightphone.wifi.server.PortalSignInState
import com.lightphone.wifi.server.PortalForm
import com.lightphone.wifi.server.WifiConnector
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable

private const val PORTAL_ACTIVITY =
    "com.lightphone.wifi/com.lightphone.wifi.server.PortalActivity"

/**
 * Native portal sign-in (SPEC §2b): opening the screen runs stage 1 — the
 * portal's redirect chain is walked invisibly; "tap agree" portals connect
 * with no form at all. If the portal has askable fields (stage 2) they're
 * driven through the SDK editor one at a time, then submitted. WEB PAGE in
 * the bottom bar is the escape hatch to the WebView portal for anything the
 * ladder can't reduce.
 */
class PortalSignInScreen(sealedActivity: SealedLightActivity) :
    SimpleLightScreen<Unit>(sealedActivity) {

    // Class properties, not remember{} — they must survive the child editor
    // covering this screen (the HomeScreen idiom).
    private var askForm: PortalForm? = null
    private var askIndex by mutableStateOf(0)
    private val askValues = mutableMapOf<String, String>()
    private var consentGiven = false
    private var askConsent by mutableStateOf<PortalForm?>(null)

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val wifiState by WifiConnector.state.collectAsState()
        val signState by PortalSignIn.state.collectAsState()
        val portal = wifiState as? WifiConnector.State.Portal

        LaunchedEffect(portal?.portalUrl) {
            val url = portal?.portalUrl ?: return@LaunchedEffect
            if (PortalSignIn.state.value is PortalSignInState.Idle) {
                PortalSignIn.start(url)
            }
        }

        // NeedsForm: ask each fillable field through the editor, then submit.
        // The askForm identity guard keeps the walk from restarting when the
        // composition is recreated on return from the editor.
        LaunchedEffect(signState) {
            val form = (signState as? PortalSignInState.NeedsForm)?.form ?: return@LaunchedEffect
            if (form !== askForm) {
                askForm = form
                askValues.clear()
                askIndex = 0
                consentGiven = false
                askNext(form)
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
                    center = LightTopBarCenter.Text(portal?.ssid ?: "Wi-Fi"),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 2f.gridUnitsAsDp(), vertical = 2f.gridUnitsAsDp()),
                ) {
                    when (val s = signState) {
                        PortalSignInState.Working, PortalSignInState.Submitting ->
                            LightText("Connecting…", variant = LightTextVariant.Heading)
                        PortalSignInState.Connected ->
                            LightText("Connected", variant = LightTextVariant.Heading)
                        is PortalSignInState.NeedsForm ->
                            LightText(
                                "Sign in to ${portal?.ssid ?: "this network"}",
                                variant = LightTextVariant.Heading,
                            )
                        is PortalSignInState.Failed ->
                            LightText(
                                "Requires portal sign in",
                                variant = LightTextVariant.Heading,
                            )
                        PortalSignInState.Idle -> {}
                    }
                    askConsent?.let { consent ->
                        LightText(
                            "To connect you accept:",
                            variant = LightTextVariant.Detail,
                            modifier = Modifier.padding(top = 2f.gridUnitsAsDp()),
                        )
                        consent.consents.forEach { field ->
                            LightText(
                                field.label ?: field.name,
                                variant = LightTextVariant.Copy,
                                modifier = Modifier.padding(top = 1f.gridUnitsAsDp()),
                            )
                        }
                    }
                }
                val cancel = LightBarButton.LightIcon(
                    icon = LightIcons.CLOSE,
                    onClick = {
                        PortalSignIn.reset()
                        goBack()
                    },
                    contentDescription = "Cancel sign in",
                )
                val done = signState is PortalSignInState.Connected
                LightBottomBar(
                    modifier = Modifier.navigationBarsPadding(),
                    items = listOf(
                        when {
                            done -> LightBarButton.Text(text = "DONE", onClick = { exitTool() })
                            signState is PortalSignInState.Failed -> LightBarButton.Text(
                                text = "OPEN PORTAL",
                                onClick = { startServerActivity(PORTAL_ACTIVITY) },
                            )
                            askConsent != null -> {
                                val consentForm = askConsent
                                LightBarButton.Text(
                                    text = "CONNECT",
                                    onClick = {
                                        consentGiven = true
                                        askConsent = null
                                        if (consentForm != null) askNext(consentForm)
                                    },
                                )
                            }
                            signState is PortalSignInState.NeedsForm -> LightBarButton.Text(
                                text = "OPEN PORTAL",
                                onClick = { startServerActivity(PORTAL_ACTIVITY) },
                            )
                            else -> cancel
                        },
                    ) + if (signState is PortalSignInState.Failed) listOf(cancel) else emptyList(),
                )
            }
        }
    }

    private fun askNext(form: PortalForm) {
        val fillable = form.fillable
        if (askIndex < fillable.size) {
            val field = fillable[askIndex]
            navigateTo(
                screenFactory = {
                    PortalFieldScreen(
                        it,
                        label = field.label ?: field.name,
                        submitLabel = if (askIndex == fillable.size - 1) "CONNECT" else "NEXT",
                    )
                },
            ) { value ->
                if (value is String && value.isNotEmpty()) askValues[field.name] = value
                askIndex++
                askNext(form)
            }
        } else {
            if (!consentGiven && form.consents.isNotEmpty()) {
                askConsent = form
            } else {
                PortalSignIn.connect(askValues)
            }
        }
    }
}
