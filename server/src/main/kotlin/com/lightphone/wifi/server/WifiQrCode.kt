package com.lightphone.wifi.server

/**
 * The standard `WIFI:` QR payload (MECARD, as Android/iOS share from their
 * Wi-Fi screens): `WIFI:S:<ssid>;T:<type>;P:<password>;H:<hidden>;;`.
 * `T` ∈ WPA|WPA2|WPA3|SAE (all PSK), WEP, nopass. Escapes are the MECARD
 * reserved four: `\;` `\:` `\\` `\,`.
 */
sealed interface WifiQrCode {
    val ssid: String
    val hidden: Boolean

    data class Open(override val ssid: String, override val hidden: Boolean) : WifiQrCode

    /** [wpa3] selects `setWpa3Passphrase` on the suggestion (WPA3/SAE QRs). */
    data class Psk(
        override val ssid: String,
        val password: String,
        override val hidden: Boolean,
        val wpa3: Boolean = false,
    ) : WifiQrCode

    /** WEP or enterprise (EAP) — we cannot join these from a QR. */
    data class Unsupported(
        override val ssid: String,
        override val hidden: Boolean = false,
    ) : WifiQrCode

    companion object {
        fun parse(raw: String): WifiQrCode? {
            if (!raw.startsWith("WIFI:", ignoreCase = true)) return null

            var ssid: String? = null
            var password: String? = null
            var type: String? = null
            var hidden = false
            var enterprise = false

            for (field in splitFields(raw.substring(5))) {
                val key = field.getOrNull(0) ?: continue
                val value = field.drop(2)
                when (key) {
                    'S' -> if (ssid == null) ssid = value
                    'P' -> if (password == null) password = value
                    'T' -> if (type == null) type = value
                    'H' -> hidden = value.equals("true", ignoreCase = true)
                    // Enterprise fields: EAP method / identity / username / realm
                    'E', 'I', 'U', 'R' -> enterprise = true
                }
            }

            val s = ssid?.takeIf { it.isNotEmpty() } ?: return null
            if (enterprise) return Unsupported(s)
            return when (type?.uppercase()) {
                null, "", "NOPASS" -> Open(s, hidden)
                "WPA", "WPA2" -> password?.takeIf { it.isNotEmpty() }
                    ?.let { Psk(s, it, hidden) } ?: Unsupported(s)
                "WPA3", "SAE" -> password?.takeIf { it.isNotEmpty() }
                    ?.let { Psk(s, it, hidden, wpa3 = true) } ?: Unsupported(s)
                // WEP or an unknown security type
                else -> Unsupported(s)
            }
        }

        /** Splits on unescaped `;`, unescaping `\X` → `X` (MECARD reserved four). */
        private fun splitFields(body: String): List<String> {
            val current = StringBuilder()
            val fields = mutableListOf<String>()
            var i = 0
            while (i < body.length) {
                val c = body[i]
                when {
                    c == '\\' && i + 1 < body.length -> {
                        current.append(body[i + 1]); i += 2
                    }
                    c == ';' -> {
                        fields.add(current.toString()); current.clear(); i++
                    }
                    else -> {
                        current.append(c); i++
                    }
                }
            }
            if (current.isNotEmpty()) fields.add(current.toString())
            return fields.filter { it.isNotEmpty() }
        }
    }
}
