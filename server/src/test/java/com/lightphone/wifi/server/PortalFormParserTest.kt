package com.lightphone.wifi.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PortalFormParserTest {

    @Test
    fun `terms-checkbox portal parses and auto-submits`() {
        val html = """
            <html><body>
            <form method="POST" action="/login/?url=http://x">
              <input type="hidden" name="redirect" value="http://captive/ok">
              <input type="checkbox" name="accept" checked>
              <input type="submit" name="go" value="Connect">
            </form></body></html>
        """.trimIndent()
        val form = PortalFormParser.parse("http://192.168.0.1/index.html", html)
        assertNotNull(form)
        assertEquals("POST", form.method)
        assertEquals("http://192.168.0.1/login/?url=http://x", form.actionUrl)
        assertEquals(3, form.fields.size)
        assertEquals(
            PortalFormField("redirect", "hidden", "http://captive/ok"),
            form.fields[0],
        )
        assertEquals(PortalFormField("accept", "checkbox", "", label = "accept"), form.fields[1])
        // The submit button passes through as hidden — portals often require
        // its name=value in the POST.
        assertEquals(PortalFormField("go", "hidden", "Connect"), form.fields[2])
        assertTrue(form.fillable.isEmpty())
    }

    @Test
    fun `room and password fields get labels`() {
        val html = """
            <form method="post" action="login">
              <label for="room">Room number</label>
              <input id="room" name="room" type="tel">
              <input name="pass" type="password" placeholder="Password">
            </form>
        """.trimIndent()
        val form = PortalFormParser.parse("http://portal.example/", html)
        assertNotNull(form)
        assertEquals(2, form.fillable.size)
        assertEquals("Room number", form.fillable[0].label)
        assertEquals("tel", form.fillable[0].type)
        assertEquals("Password", form.fillable[1].label)
        assertEquals("password", form.fillable[1].type)
        // Relative action resolves against the page URL.
        assertEquals("http://portal.example/login", form.actionUrl)
    }

    @Test
    fun `js-built page has no form`() {
        assertNull(
            PortalFormParser.parse("http://p.example/", "<div id=root></div><script>build()</script>"),
        )
    }

    @Test
    fun `select field means webview fallback`() {
        val html = """
            <form action="/go">
              <select name="lang"><option>en</option></select>
              <input name="user" type="text">
            </form>
        """.trimIndent()
        assertNull(PortalFormParser.parse("http://p.example/", html))
    }

    @Test
    fun `checkbox labels resolve for consent display`() {
        val html = """
            <form method="POST" action="/go">
              <input type="checkbox" name="a" id="terms">
              <label for="terms">I accept the Terms of Service</label>
              <label><input type="checkbox" name="b" checked> I agree to marketing email</label>
              <input type="checkbox" name="c" checked>
            </form>
        """.trimIndent()
        val form = PortalFormParser.parse("http://p.example/", html)
        assertNotNull(form)
        assertEquals("I accept the Terms of Service", form.consents[0].label)
        assertEquals("I agree to marketing email", form.consents[1].label)
        // No label anywhere — fall back to the field name.
        assertEquals("c", form.consents[2].label)
        assertTrue(form.fillable.isEmpty())
    }

    @Test
    fun `get method is respected`() {
        val html = "<form method='GET' action='/auth'><input name='u'></form>"
        val form = PortalFormParser.parse("http://p.example/a", html)
        assertNotNull(form)
        assertEquals("GET", form.method)
        assertEquals("http://p.example/auth", form.actionUrl)
    }

    @Test
    fun `unquoted and entity attributes parse`() {
        val html = "<form action=/go><input name=room value=\"12&amp;3\"></form>"
        val form = PortalFormParser.parse("http://p.example/", html)
        assertNotNull(form)
        assertEquals("http://p.example/go", form.actionUrl)
        assertEquals("12&3", form.fields[0].value)
    }
}
