package com.lightphone.wifi.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WifiQrCodeTest {

    @Test
    fun `wpa with password parses as psk`() {
        assertEquals(
            WifiQrCode.Psk("Home", "pw123", hidden = false),
            WifiQrCode.parse("WIFI:S:Home;T:WPA;P:pw123;;"),
        )
    }

    @Test
    fun `wpa2 and wpa3 map to psk`() {
        assertEquals(
            WifiQrCode.Psk("a", "p", hidden = false),
            WifiQrCode.parse("WIFI:S:a;T:WPA2;P:p;;"),
        )
        assertEquals(
            WifiQrCode.Psk("a", "p", hidden = false, wpa3 = true),
            WifiQrCode.parse("WIFI:S:a;T:WPA3;P:p;;"),
        )
        assertEquals(
            WifiQrCode.Psk("a", "p", hidden = false, wpa3 = true),
            WifiQrCode.parse("WIFI:S:a;T:SAE;P:p;;"),
        )
    }

    @Test
    fun `nopass without password parses as open`() {
        assertEquals(
            WifiQrCode.Open("Cafe", hidden = false),
            WifiQrCode.parse("WIFI:S:Cafe;T:nopass;;"),
        )
    }

    @Test
    fun `ssid only parses as open`() {
        assertEquals(
            WifiQrCode.Open("Cafe", hidden = false),
            WifiQrCode.parse("WIFI:S:Cafe;;"),
        )
    }

    @Test
    fun `wep parses as unsupported`() {
        assertEquals(
            WifiQrCode.Unsupported("Old"),
            WifiQrCode.parse("WIFI:S:Old;T:WEP;P:abc;;"),
        )
    }

    @Test
    fun `hidden flag is read case-insensitively`() {
        assertEquals(
            WifiQrCode.Open("h", hidden = true),
            WifiQrCode.parse("WIFI:S:h;H:true;;"),
        )
        assertEquals(
            WifiQrCode.Open("h", hidden = false),
            WifiQrCode.parse("WIFI:S:h;H:FALSE;;"),
        )
    }

    @Test
    fun `escapes decode inside ssid and password`() {
        assertEquals(
            WifiQrCode.Psk("My;Net", "p\\:1", hidden = false),
            WifiQrCode.parse("WIFI:S:My\\;Net;T:WPA;P:p\\\\\\:1;;"),
        )
        assertEquals(
            WifiQrCode.Open("a,b", hidden = false),
            WifiQrCode.parse("WIFI:S:a\\,b;;"),
        )
    }

    @Test
    fun `enterprise fields parse as unsupported with ssid`() {
        assertEquals(
            WifiQrCode.Unsupported("Corp"),
            WifiQrCode.parse("WIFI:S:Corp;T:WPA2-EAP;I:id;U:user;P:pw;;"),
        )
    }

    @Test
    fun `password present but wpa type with empty password is unsupported`() {
        assertEquals(
            WifiQrCode.Unsupported("a"),
            WifiQrCode.parse("WIFI:S:a;T:WPA;P:;;"),
        )
    }

    @Test
    fun `garbage returns null`() {
        assertNull(WifiQrCode.parse("not-wifi"))
        assertNull(WifiQrCode.parse("WIFI:;;"))
        assertNull(WifiQrCode.parse("WIFI:T:WPA;P:x;;"))
        assertNull(WifiQrCode.parse(""))
        assertNull(WifiQrCode.parse("https://example.com"))
    }

    @Test
    fun `prefix is case-insensitive`() {
        assertEquals(
            WifiQrCode.Open("Cafe", hidden = false),
            WifiQrCode.parse("wifi:S:Cafe;;"),
        )
    }
}
