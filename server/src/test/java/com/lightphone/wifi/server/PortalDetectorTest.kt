package com.lightphone.wifi.server

import kotlin.test.Test
import kotlin.test.assertEquals

class PortalDetectorTest {

    @Test
    fun `204 is open`() {
        assertEquals(PortalProbeResult.Open, PortalDetector.classify(204, null))
    }

    @Test
    fun `302 with location is a portal`() {
        assertEquals(
            PortalProbeResult.Portal("http://portal.example/login"),
            PortalDetector.classify(302, "http://portal.example/login"),
        )
    }

    @Test
    fun `redirect without location is an error`() {
        assertEquals(
            PortalProbeResult.Error("redirect without Location header"),
            PortalDetector.classify(302, null),
        )
    }

    @Test
    fun `200 on the probe url is a portal`() {
        assertEquals(
            PortalProbeResult.Portal(PortalDetector.PROBE_URL),
            PortalDetector.classify(200, null),
        )
    }

    @Test
    fun `server error is an error`() {
        assertEquals(
            PortalProbeResult.Error("HTTP 500"),
            PortalDetector.classify(500, null),
        )
    }
}
