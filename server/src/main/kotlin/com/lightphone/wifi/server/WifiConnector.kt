package com.lightphone.wifi.server

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * WiFi state + suggestion submission (SPEC §Flows). Joining a network from a
 * normal app goes through `WifiNetworkSuggestion` — the platform joins it when
 * in range (Phase 0 verified on LightOS). Scan/toggle use WifiManager; radio
 * toggle may be firmware-blocked for normal apps ([toggleWifi] reports that).
 *
 * Callback- and broadcast-driven only; the scan loop lives in the foreground
 * screen, never in the connector. Same process as the tool — screens collect
 * these flows directly, no IPC.
 */
object WifiConnector {

    sealed interface State {
        data object Disconnected : State
        data object Validated : State
        data class Portal(val ssid: String, val portalUrl: String) : State
    }

    /** One row of the scan list; [secured] from the beacon capabilities. */
    data class ScanEntry(val ssid: String, val secured: Boolean)

    private val _state = MutableStateFlow<State>(State.Disconnected)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _wifiEnabled = MutableStateFlow(false)
    val wifiEnabled: StateFlow<Boolean> = _wifiEnabled.asStateFlow()

    private val _scanEntries = MutableStateFlow<List<ScanEntry>>(emptyList())
    val scanEntries: StateFlow<List<ScanEntry>> = _scanEntries.asStateFlow()

    /** Set when the platform refuses scan results (runtime permission). */
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private var scope: CoroutineScope? = null
    private var callback: ConnectivityManager.NetworkCallback? = null
    private var receiver: BroadcastReceiver? = null
    private var wifiNetwork: Network? = null
    private var lastCaps: NetworkCapabilities? = null

    private var appContext: Context? = null

    /** The one suggestion we own; its ssid is shown in the portal banner. */
    private var lastSuggestion: WifiNetworkSuggestion? = null
    private var lastSsid: String? = null

    /** Set by [suggest], cleared once the suggested network validates. */
    private var awaitingJoin = false

    /** Called from [ServerBootstrapProvider] — screens have no Context access. */
    fun init(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    fun start() {
        val appContext = appContext ?: return
        if (callback != null) return
        val cm = appContext.getSystemService(ConnectivityManager::class.java)
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                wifiNetwork = network
                lastCaps = caps
                evaluate(network, caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
            }

            override fun onLost(network: Network) {
                if (network == wifiNetwork) {
                    wifiNetwork = null
                    lastCaps = null
                    _connectedSsid.value = null
                    _state.value = State.Disconnected
                }
            }
        }
        cm.registerNetworkCallback(request, callback!!)
        // WiFi radio state — the toggle's truth. A sticky broadcast fires on
        // registration, so this also initializes the flow.
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val wm = appContext?.getSystemService(WifiManager::class.java) ?: return
                _wifiEnabled.value = wm.isWifiEnabled
                if (!wm.isWifiEnabled) {
                    _scanEntries.value = emptyList()
                    _scanning.value = false
                }
            }
        }
        appContext.registerReceiver(
            receiver,
            IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION),
            Context.RECEIVER_NOT_EXPORTED,
        )
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        restore(appContext)
        _wifiEnabled.value = appContext.getSystemService(WifiManager::class.java).isWifiEnabled
    }

    /**
     * The suggestion lives platform-side and survives process death; our
     * bookkeeping doesn't. Re-adopt it on every start so the FORGET button
     * (and pending-join label) reflect reality after a reinstall/reboot.
     */
    private fun restore(context: Context) {
        val existing = context.getSystemService(WifiManager::class.java).networkSuggestions
        if (existing.isEmpty() || lastSuggestion != null) return
        lastSuggestion = existing.first()
        lastSsid = existing.first().ssid ?: return
        if (_state.value is State.Disconnected) awaitingJoin = true
    }

    fun stop() {
        callback?.let {
            appContext?.getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(it)
        }
        receiver?.let { appContext?.unregisterReceiver(it) }
        callback = null
        receiver = null
        scope?.cancel()
        scope = null
    }

    /**
     * Flips the radio. Returns false when the firmware blocks a normal app
     * from toggling Wi-Fi (the Android 10+ restriction) — the screen then
     * routes to the native Wi-Fi screen instead. Never throws.
     */
    fun toggleWifi(): Boolean = try {
        val wm = appContext?.getSystemService(WifiManager::class.java) ?: return false
        wm.setWifiEnabled(!wm.isWifiEnabled)
    } catch (e: SecurityException) {
        false
    }

    /**
     * True when the platform will refuse scan results / SSID reads: the
     * location permission is missing. (The device's location master switch
     * matters too — while it is off the platform returns empty results
     * WITHOUT throwing, so the screen checks `locationEnabled` separately.)
     */
    fun scanBlocked(): Boolean {
        val context = appContext ?: return true
        return context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    /**
     * Requests a scan and reads the current results. [WifiManager.startScan]
     * is best-effort (firmware throttles it); the results list is whatever
     * the radio last saw. Requires the location permission AND the device
     * location switch — the platform returns empty results silently otherwise.
     * Call from the foreground screen only (battery rule).
     */
    fun refreshScan() {
        val wm = appContext?.getSystemService(WifiManager::class.java) ?: return
        if (!wm.isWifiEnabled) return
        _scanning.value = true
        runCatching { wm.startScan() }
        scope?.launch {
            val results = runCatching { wm.scanResults }.getOrDefault(emptyList())
            _scanEntries.value = results
                .filter { it.SSID.isNotEmpty() }
                .sortedByDescending { it.level }
                .distinctBy { it.SSID }
                .map {
                    ScanEntry(
                        ssid = it.SSID,
                        secured = it.capabilities.contains("WPA") ||
                            it.capabilities.contains("RSN") ||
                            it.capabilities.contains("WEP"),
                    )
                }
            _scanning.value = false
        }
    }

    /** True when we hold a suggestion for this SSID — i.e. the tool can
     *  forget it. Networks joined via native Settings are system-owned and
     *  only Settings' forget works on them. */
    fun ownsSsid(ssid: String): Boolean = runCatching {
        appContext?.getSystemService(WifiManager::class.java)
            ?.networkSuggestions?.any { it.ssid == ssid } == true
    }.getOrDefault(false)

    /** The connected network's SSID, or null (quotes/<unknown> stripped). */
    fun currentSsid(): String? {
        if (wifiNetwork == null) return null
        val wm = appContext?.getSystemService(WifiManager::class.java) ?: return null
        return try {
            wm.connectionInfo?.ssid
                ?.removeSurrounding("\"")
                ?.takeIf { it.isNotEmpty() && it != "<unknown ssid>" }
                ?: lastSsid
        } catch (e: SecurityException) {
            lastSsid
        }
    }

    /** The WiFi network to bind the portal WebView's process to (SPEC §2.3). */
    val boundNetwork: Network? get() = wifiNetwork

    private val _pendingSsid = MutableStateFlow<String?>(null)

    /** SSID of a submitted suggestion that hasn't joined yet (times out). */
    val pendingSsid: StateFlow<String?> = _pendingSsid.asStateFlow()

    private val _connectedSsid = MutableStateFlow<String?>(null)

    /** SSID of the connected network (reactive — read it in composables). */
    val connectedSsid: StateFlow<String?> = _connectedSsid.asStateFlow()

    /** SSID of the network we currently hold a suggestion for, if any. */
    val suggestedSsid: String? get() = if (lastSuggestion != null) lastSsid else null

    /**
     * Withdraws our suggestion — the only way to disconnect from a
     * suggestion-joined network (Settings' forget does nothing for them; the
     * platform re-joins suggestions whenever in range). Clears everything we
     * could hold; we only ever own one suggestion.
     */
    fun forget() {
        val wm = appContext?.getSystemService(WifiManager::class.java) ?: return
        val existing = wm.networkSuggestions
        if (existing.isNotEmpty()) wm.removeNetworkSuggestions(existing)
        lastSuggestion = null
        lastSsid = null
        awaitingJoin = false
    }

    /**
     * Submits a parsed QR as a suggestion. Returns a short user-facing error,
     * or null on success. One suggestion at a time: the previous set is
     * replaced so scanning network B stops advertising network A.
     *
     * Note: no way to un-pend on rejection — if the network never joins,
     * Home keeps showing "Joining…" until another network validates or the
     * user scans something else. The platform sends no rejection signal we
     * could observe without polling.
     */
    fun suggest(code: WifiQrCode): String? {
        if (code !is WifiQrCode.Open && code !is WifiQrCode.Psk) return "Not supported"
        val builder = WifiNetworkSuggestion.Builder().setSsid(code.ssid)
        when (code) {
            is WifiQrCode.Psk ->
                if (code.wpa3) builder.setWpa3Passphrase(code.password)
                else builder.setWpa2Passphrase(code.password)
            else -> {}
        }
        if (code.hidden) builder.setIsHiddenSsid(true)
        val suggestion = builder.build()

        val wm = appContext?.getSystemService(WifiManager::class.java) ?: return "Not ready"
        wm.removeNetworkSuggestions(listOfNotNull(lastSuggestion))
        val status = wm.addNetworkSuggestions(listOf(suggestion))
        if (status != WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
            return "Could not add network (error $status)"
        }
        lastSuggestion = suggestion
        lastSsid = code.ssid
        awaitingJoin = true
        _pendingSsid.value = code.ssid
        _state.value = State.Disconnected
        // The platform gives no rejection signal: stop claiming "Connecting"
        // after 30 s (the suggestion stays; it joins when in range).
        scope?.launch {
            delay(30_000)
            if (awaitingJoin) {
                awaitingJoin = false
                _pendingSsid.value = null
            }
        }
        return null
    }

    /** Re-checks the cached network state — called when Home becomes visible
     *  again, so the caption recovers if a validation event was missed. */
    fun reevaluate() {
        val network = wifiNetwork ?: return
        lastCaps?.let { evaluate(network, it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) }
    }

    private fun evaluate(network: Network, validated: Boolean) {
        _connectedSsid.value = currentSsid() ?: lastSsid ?: _connectedSsid.value
        if (validated) {
            awaitingJoin = false
            _pendingSsid.value = null
            _state.value = State.Validated
            return
        }
        scope?.launch {
            when (val result = PortalDetector.probe(network)) {
                is PortalProbeResult.Portal -> _state.value = State.Portal(
                    lastSsid ?: "this network",
                    result.url,
                )
                is PortalProbeResult.Open -> _state.value = State.Validated
                // Probe failed: keep the current state; the callback re-runs
                // us on the next capability change. No retry loop.
                is PortalProbeResult.Error -> {}
            }
        }
    }
}
