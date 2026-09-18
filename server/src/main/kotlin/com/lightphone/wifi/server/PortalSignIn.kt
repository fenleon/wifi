package com.lightphone.wifi.server

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.CookieManager
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

sealed interface PortalSignInState {
    data object Idle : PortalSignInState

    /** Stage 1 running: walking the portal's redirects, then re-probing. */
    data object Working : PortalSignInState

    /** Stage 2: the portal's form, askable natively. */
    data class NeedsForm(val form: PortalForm) : PortalSignInState

    data object Submitting : PortalSignInState
    data object Connected : PortalSignInState
    data class Failed(val message: String) : PortalSignInState
}

/**
 * WebView-free portal sign-in (SPEC §2b, stages 1–2). Stage 1 walks the
 * portal URL's redirect chain with a cookie jar and re-probes — "tap agree"
 * portals open here with no UI at all. Stage 2 reduces the landing page to a
 * form ([PortalFormParser]) and submits it natively; forms with no fillable
 * fields submit themselves. Anything a real browser is needed for lands in
 * [PortalSignInState.Failed] and the screen falls back to [PortalActivity]'s
 * WebView.
 *
 * No logging: portal pages and URLs can carry room numbers and guest tokens.
 */
object PortalSignIn {

    private const val MAX_REDIRECTS = 6
    private const val TIMEOUT_MS = 10_000

    /** Invisible stage-1 walk budget before handing over to the portal page. */
    private const val SIGNIN_TIMEOUT_MS = 30_000L
    private val CHARSET_RX = Regex("(?i)charset=([\\w-]+)")

    private val _state = MutableStateFlow<PortalSignInState>(PortalSignInState.Idle)
    val state: StateFlow<PortalSignInState> = _state.asStateFlow()

    private var appContext: Context? = null
    private var scope: CoroutineScope? = null
    private var network: Network? = null
    private var form: PortalForm? = null
    private var cookies = CookieManager()

    /** Called from [ServerBootstrapProvider] — screens have no Context access. */
    fun init(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    /** Entry — the CONNECT tap. Safe to re-call: cancels any previous run. */
    fun start(portalUrl: String) {
        val net = WifiConnector.boundNetwork ?: run {
            _state.value = PortalSignInState.Failed("Not connected")
            return
        }
        network = net
        form = null
        cookies = CookieManager()
        scope?.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        _state.value = PortalSignInState.Working
        scope!!.launch {
            delay(SIGNIN_TIMEOUT_MS)
            if (_state.value is PortalSignInState.Working) fail("Requires portal sign in")
        }
        scope!!.launch {
            val page = load(portalUrl)
            if (probeOpen()) {
                done()
                return@launch
            }
            val html = page?.body ?: run {
                fail("No answer from the portal")
                return@launch
            }
            // Stage 2: reduce the landing page to a form we can ask natively.
            val parsed = PortalFormParser.parse(page.url, html) ?: run {
                fail("This portal needs its web page")
                return@launch
            }
            form = parsed
            if (parsed.fillable.isEmpty() && parsed.consents.isEmpty()) submit(parsed, emptyMap())
            else _state.value = PortalSignInState.NeedsForm(parsed)
        }
    }

    /** Stage 2 submit: [values] keyed by field name, for [PortalForm.fillable]. */
    fun connect(values: Map<String, String>) {
        val f = form ?: return
        _state.value = PortalSignInState.Submitting
        scope?.launch { submit(f, values) }
    }

    fun reset() {
        scope?.cancel()
        scope = null
        _state.value = PortalSignInState.Idle
    }

    private suspend fun submit(f: PortalForm, values: Map<String, String>) {
        // CONNECT is the consent: every checkbox submits checked (its value,
        // or "on" when it has none — the HTML default).
        val params = f.fields.map { field ->
            field.name to when {
                field.type == "checkbox" -> field.value.ifEmpty { "on" }
                else -> values[field.name] ?: field.value
            }
        }
        when (f.method) {
            "GET" -> load(appendQuery(f.actionUrl, params))
            else -> load(f.actionUrl, body = params.formUrlencoded())
        }
        if (probeOpen()) done() else fail("Sign-in did not take — try the web page")
    }

    private fun appendQuery(url: String, params: List<Pair<String, String>>): String {
        if (params.isEmpty()) return url
        return url + (if (url.contains('?')) "&" else "?") + params.formUrlencoded()
    }

    private fun List<Pair<String, String>>.formUrlencoded(): String =
        joinToString("&") { (name, value) ->
            URLEncoder.encode(name, Charsets.UTF_8) + "=" + URLEncoder.encode(value, Charsets.UTF_8)
        }

    private suspend fun probeOpen(): Boolean =
        PortalDetector.probe(network) is PortalProbeResult.Open

    private fun done() {
        appContext?.getSystemService(ConnectivityManager::class.java)?.let { cm ->
            network?.let { runCatching { cm.reportNetworkConnectivity(it, true) } }
        }
        _state.value = PortalSignInState.Connected
    }

    private fun fail(message: String) {
        _state.value = PortalSignInState.Failed(message)
    }

    /** Portal completed in the WebView — report it on the sign-in screen. */
    fun markConnected() {
        done()
    }

    private data class Page(val url: String, val body: String?)

    /**
     * GET/POST with manual redirects and the shared cookie jar. Returns the
     * final page (body only when 2xx); null on any failure. IO context only.
     */
    private fun load(startUrl: String, body: String? = null): Page? = try {
        var url = startUrl
        var payload = body
        repeat(MAX_REDIRECTS) {
            val target = URL(url)
            val conn =
                (network?.openConnection(target) ?: target.openConnection()) as HttpURLConnection
            try {
                conn.connectTimeout = TIMEOUT_MS
                conn.readTimeout = TIMEOUT_MS
                conn.instanceFollowRedirects = false
                conn.setRequestProperty("Connection", "close")
                if (payload != null) {
                    conn.requestMethod = "POST"
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                }
                runCatching { cookies.get(target.toURI(), emptyMap())["Cookie"] }
                    .getOrNull()
                    ?.firstOrNull()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { conn.setRequestProperty("Cookie", it) }
                payload?.let { conn.outputStream.use { out -> out.write(it.toByteArray()) } }
                val code = conn.responseCode
                runCatching { cookies.put(target.toURI(), conn.headerFields ?: emptyMap()) }
                val location = conn.getHeaderField("Location")
                val responseBody = if (code in 200..299) {
                    val charsetName = conn.contentType
                        ?.let { CHARSET_RX.find(it)?.groupValues?.get(1) }
                    String(
                        conn.inputStream.readBytes(),
                        runCatching { charset(charsetName ?: "UTF-8") }
                            .getOrDefault(Charsets.UTF_8),
                    )
                } else {
                    null
                }
                if (code in 300..399 && !location.isNullOrEmpty()) {
                    url = URL(target, location).toString()
                    payload = null
                    return@repeat
                }
                return if (code in 200..299) Page(url, responseBody) else null
            } finally {
                runCatching { conn.disconnect() }
            }
        }
        null
    } catch (e: Exception) {
        null
    }
}
