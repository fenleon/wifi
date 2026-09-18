package com.lightphone.wifi.server

import android.app.Activity
import android.net.ConnectivityManager
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Captive-portal sign-in page (SPEC §Flows): a bare WebView on the portal
 * page with progress-bar chrome. Opened via OPEN PORTAL from
 * [com.lightphone.wifi.screens.PortalSignInScreen] after the invisible
 * sign-in walk gave up; reads the portal URL from [WifiConnector.state]
 * (same process, no extras).
 *
 * The process is bound to the WiFi network before loading — a captive portal
 * sits on a network the system won't route default traffic through, and
 * without the bind the WebView either loads over cellular or fails.
 *
 * Video-ad portals unlock server-side mid-flow, so the page is watched with
 * a 2.5 s probe: the moment the network unlocks the activity closes itself
 * and reports the sign-in on the tool screen. No logging of the portal
 * content (it can carry room-number / guest tokens).
 */
class PortalActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val portal = WifiConnector.state.value as? WifiConnector.State.Portal
        val network = WifiConnector.boundNetwork
        if (portal == null || network == null) {
            finish()
            return
        }
        getSystemService(ConnectivityManager::class.java).bindProcessToNetwork(network)

        val density = resources.displayMetrics.density
        val row = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        progress.isIndeterminate = true
        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                checkConnected(network)
            }
        }

        val done = Button(this)
        done.text = "Done"
        done.setOnClickListener { checkConnected(network, closeAnyway = true) }

        val webParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f,
        )
        webParams.setMargins(0, (2 * density).toInt(), 0, 0)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(progress, row)
            addView(webView, webParams)
            addView(done, row)
        }
        setContentView(layout)

        webView.loadUrl(portal.portalUrl)

        scope.launch {
            while (true) {
                delay(2_500)
                if (PortalDetector.probe(network) is PortalProbeResult.Open) {
                    connected()
                    return@launch
                }
            }
        }
    }

    private fun connected() {
        PortalSignIn.markConnected()
        finish()
    }

    /** Probe the connectivity check; a clean 204 means the portal is done. */
    private fun checkConnected(network: android.net.Network, closeAnyway: Boolean = false) {
        scope.launch {
            if (PortalDetector.probe(network) is PortalProbeResult.Open) {
                connected()
            } else if (closeAnyway) {
                finish()
            } else {
                progress.visibility = View.GONE
            }
        }
    }

    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
