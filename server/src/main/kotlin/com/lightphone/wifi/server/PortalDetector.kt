package com.lightphone.wifi.server

import android.net.Network
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

sealed interface PortalProbeResult {
    /** 204 — the network is genuinely open (validated). */
    data object Open : PortalProbeResult

    /** Redirect or a captive page — sign-in is required at [url]. */
    data class Portal(val url: String) : PortalProbeResult

    data class Error(val message: String) : PortalProbeResult
}

/**
 * Captive-portal detection: GET the connectivity check and classify the
 * response (SPEC §Flows). No portal URL is ever logged (they can carry
 * room-number / guest tokens).
 */
object PortalDetector {

    const val PROBE_URL = "http://connectivitycheck.gstatic.com/generate_204"

    /** Pure classification — unit-testable without a network. */
    fun classify(statusCode: Int, locationHeader: String?): PortalProbeResult = when {
        statusCode == 204 -> PortalProbeResult.Open
        statusCode in 300..399 ->
            locationHeader?.let { PortalProbeResult.Portal(it) }
                ?: PortalProbeResult.Error("redirect without Location header")
        statusCode in 200..299 ->
            // The probe URL itself was served a page: a captive portal.
            PortalProbeResult.Portal(PROBE_URL)
        else -> PortalProbeResult.Error("HTTP $statusCode")
    }

    /**
     * Performs the probe, bound to [network] when given (a captive portal sits
     * on a network the system won't route default traffic through). Follows no
     * redirects — the 302's Location IS the portal URL.
     */
    suspend fun probe(network: Network?): PortalProbeResult = withContext(Dispatchers.IO) {
        try {
            val url = URL(PROBE_URL)
            val conn = (network?.openConnection(url) ?: url.openConnection()) as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("Connection", "close")
            val code = conn.responseCode
            val location = conn.getHeaderField("Location")
            runCatching { conn.inputStream?.close() }
            runCatching { conn.errorStream?.close() }
            conn.disconnect()
            classify(code, location)
        } catch (e: IOException) {
            PortalProbeResult.Error(e.message ?: "network error")
        }
    }
}
