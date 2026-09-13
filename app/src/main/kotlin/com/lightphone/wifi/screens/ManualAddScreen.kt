package com.lightphone.wifi.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lightphone.wifi.server.WifiConnector
import com.lightphone.wifi.server.WifiQrCode
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
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

/** Security choices for manual entry. WEP is omitted: the suggestion API
 *  cannot join WEP networks, and they are practically extinct. */
enum class Security(val label: String) {
    NONE("None"), WPA("WPA"), WPA2("WPA2"), WPA3("WPA3");

    val needsPassword: Boolean get() = this != NONE
}

/**
 * The manual panel's draft — held OUTSIDE composition (the Keypad pattern):
 * the name/security/password sub-screens push this screen out of composition,
 * and neither remember{} nor rememberSaveable survives the SDK's screen swap
 * reliably. Cleared after a successful JOIN.
 */
object ManualDraft {
    var name by mutableStateOf<String?>(null)
    var security by mutableStateOf(Security.NONE)
    var password by mutableStateOf<String?>(null)
    var error by mutableStateOf<String?>(null)

    // Field-level "required" errors, shown as the row's top tag.
    var nameError by mutableStateOf<String?>(null)
    var passwordError by mutableStateOf<String?>(null)

    fun clear() {
        name = null
        security = Security.NONE
        password = null
        error = null
        nameError = null
        passwordError = null
    }
}

/**
 * Manual network entry: Network Name / Security / Password rows over the
 * native editor idioms, JOIN in the bottom bar. JOIN requires a name (and a
 * password for secured types); the joined suggestion shows on Home as
 * "Connecting to …". Every exit (back or JOIN) returns to the main panel —
 * the result is always true, which pops the scanner screen with it.
 * Row grammar: `Detail` tag above a `Heading` value — the tasks edit-row idiom.
 */
class ManualAddScreen(sealedActivity: SealedLightActivity) :
    SimpleLightScreen<Boolean>(sealedActivity) {

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
                        onClick = {
                            ManualDraft.clear()
                            goBack(true)
                        },
                        contentDescription = "Back",
                    ),
                    center = LightTopBarCenter.Text("Add Manual"),
                )
                // First row: no tag — just the prompt/name (user feedback).
                EntryRow(
                    value = ManualDraft.name?.trim()?.ifEmpty { null } ?: "Add Network Name",
                    error = ManualDraft.nameError,
                    onClick = {
                        navigateTo(screenFactory = { NameInputScreen(it, ManualDraft.name ?: "") }) { result ->
                            if (result is String) {
                                ManualDraft.name = result
                                ManualDraft.nameError = null
                            }
                        }
                    },
                )
                EntryRow(
                    tag = "Security",
                    value = ManualDraft.security.label,
                    onClick = {
                        navigateTo(screenFactory = { SecurityPickerScreen(it, ManualDraft.security) }) { result ->
                            if (result is Security) {
                                ManualDraft.security = result
                                if (!result.needsPassword) ManualDraft.password = null
                            }
                        }
                    },
                )
                if (ManualDraft.security.needsPassword) {
                    // Dots-only display replaced by "Edit Password" (feedback).
                    EntryRow(
                        value = if (ManualDraft.password.isNullOrEmpty()) "Add Password" else "Edit Password",
                        error = ManualDraft.passwordError,
                        onClick = {
                            navigateTo(
                                screenFactory = {
                                    PasswordEntryScreen(
                                        it,
                                        ManualDraft.name ?: "network",
                                        ManualDraft.password ?: "",
                                        tagged = false,
                                    )
                                },
                            ) { result ->
                                if (result is String) {
                                    ManualDraft.password = result
                                    ManualDraft.passwordError = null
                                }
                            }
                        },
                    )
                }
                ManualDraft.error?.let {
                    LightText(
                        text = it,
                        variant = LightTextVariant.Detail,
                        modifier = Modifier.padding(horizontal = 2f.gridUnitsAsDp()),
                    )
                }
                Box(Modifier.weight(1f))
                LightBottomBar(
                    modifier = Modifier.navigationBarsPadding(),
                    items = listOf(
                        LightBarButton.Text(
                            text = "JOIN",
                            onClick = {
                                val ssid = ManualDraft.name?.trim().orEmpty()
                                if (ssid.isEmpty()) {
                                    ManualDraft.nameError = "Network name required"
                                    return@Text
                                }
                                if (ManualDraft.security.needsPassword && ManualDraft.password.isNullOrEmpty()) {
                                    ManualDraft.passwordError = "Password required"
                                    return@Text
                                }
                                val code: WifiQrCode = when (ManualDraft.security) {
                                    Security.NONE -> WifiQrCode.Open(ssid, hidden = false)
                                    Security.WPA3 -> WifiQrCode.Psk(ssid, ManualDraft.password.orEmpty(), hidden = false, wpa3 = true)
                                    else -> WifiQrCode.Psk(ssid, ManualDraft.password.orEmpty(), hidden = false)
                                }
                                val joinError = WifiConnector.suggest(code)
                                if (joinError != null) {
                                    ManualDraft.error = joinError
                                } else {
                                    ManualDraft.clear()
                                    goBack(true)
                                }
                            },
                        ),
                    ),
                )
            }
        }
    }
}

/** A tasks-edit-style row: optional tag / error line over a `Heading` value —
 *  errors render BELOW the value (feedback). */
@Composable
private fun EntryRow(
    value: String,
    onClick: () -> Unit,
    tag: String? = null,
    error: String? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .lightClickable(onClick = onClick)
            .padding(horizontal = 2f.gridUnitsAsDp(), vertical = 0.75f.gridUnitsAsDp()),
    ) {
        if (error == null && tag != null) {
            LightText(text = tag, variant = LightTextVariant.Detail)
        }
        LightText(
            text = value,
            variant = LightTextVariant.Heading,
            modifier = if (tag != null && error == null) Modifier.offset(y = (-3).dp) else Modifier,
        )
        error?.let {
            LightText(
                text = it,
                variant = LightTextVariant.Detail,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
