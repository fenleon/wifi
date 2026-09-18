package com.lightphone.wifi.server

import java.net.URL

/** One input of a parsed portal form. */
data class PortalFormField(
    val name: String,
    /** "text" | "password" | "email" | "tel" | "checkbox" | "hidden". */
    val type: String,
    val value: String = "",
    /** Label shown above the native editor (label-for > placeholder > name). */
    val label: String? = null,
)

/** A portal's form, reduced to what a native screen can ask and submit. */
data class PortalForm(
    val actionUrl: String,
    val method: String,
    val fields: List<PortalFormField>,
) {
    /** Fields the user must fill — everything else submits automatically. */
    val fillable: List<PortalFormField>
        get() = fields.filter { it.type != "hidden" && it.type != "checkbox" }

    /** Consent checkboxes — submitted checked only after the user agrees. */
    val consents: List<PortalFormField>
        get() = fields.filter { it.type == "checkbox" }
}

/**
 * Reduces a portal page to a [PortalForm] with plain regex parsing — no
 * dependencies, pure and unit-tested. Returns null whenever the form needs a
 * real browser (JS-built markup, select/radio/file/textarea fields): the
 * caller falls back to the WebView. First `<form>` on the page wins.
 */
object PortalFormParser {

    private val FORM_RX = Regex("<form\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val INPUT_RX = Regex("<input\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val ATTR_RX =
        Regex("([a-zA-Z_:][-a-zA-Z0-9_:.]*)\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s\"'>]+))")
    private val LABEL_RX = Regex(
        "<label\\b[^>]*for\\s*=\\s*[\"']?([^\"'\\s>]+)[\"']?[^>]*>(.*?)</label>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val WRAPPED_LABEL_RX = Regex(
        "<label\\b[^>]*>(.*?)</label>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val TAG_RX = Regex("<[^>]*>")

    /** Tags that need a real browser — any of them means WebView fallback. */
    private val UNSUPPORTED_TAG_RX =
        Regex("<(select|textarea|datalist|keygen|output)\\b", RegexOption.IGNORE_CASE)

    fun parse(pageUrl: String, html: String): PortalForm? {
        val formTag = FORM_RX.find(html) ?: return null
        val formAttrs = attrs(formTag.value)
        val body = html.substring(formTag.range.last + 1)
        if (UNSUPPORTED_TAG_RX.containsMatchIn(body)) return null
        val labels = LABEL_RX.findAll(body).associate {
            it.groupValues[1] to TAG_RX.replace(it.groupValues[2], "").trim()
        }

        val fields = ArrayList<PortalFormField>()
        for (match in INPUT_RX.findAll(body)) {
            val a = attrs(match.value)
            val name = a["name"]?.trim().takeUnless { it.isNullOrEmpty() } ?: continue
            val type = (a["type"] ?: "text").lowercase().trim()
            val field = when (type) {
                "hidden", "submit", "image", "button" ->
                    PortalFormField(name, "hidden", a["value"].orEmpty())
                "checkbox" -> {
                    val wrapped = WRAPPED_LABEL_RX.findAll(body)
                        .map { it.groupValues[1] }
                        .firstOrNull { it.contains(match.value) }
                        ?.let { TAG_RX.replace(it, "").trim() }
                        ?.takeIf { it.isNotBlank() }
                    PortalFormField(
                        name, "checkbox", a["value"].orEmpty(),
                        label = a["id"]?.let { id -> labels[id] }?.takeIf { it.isNotBlank() }
                            ?: wrapped ?: name,
                    )
                }
                "text", "password", "email", "tel", "search" ->
                    PortalFormField(
                        name = name,
                        type = if (type == "search") "text" else type,
                        value = a["value"].orEmpty(),
                        label = a["placeholder"]?.takeIf { it.isNotBlank() }
                            ?: a["id"]?.let { id -> labels[id] }?.takeIf { it.isNotBlank() }
                            ?: name,
                    )
                else -> return null
            }
            fields += field
        }

        val action = formAttrs["action"]?.trim().orEmpty()
        val actionUrl = if (action.isEmpty()) pageUrl else URL(URL(pageUrl), action).toString()
        val method = (formAttrs["method"] ?: "post").trim().uppercase()
        return PortalForm(actionUrl, if (method == "GET") "GET" else "POST", fields)
    }

    /** name→value attributes of one tag, entities unescaped. */
    private fun attrs(tag: String): Map<String, String> = ATTR_RX.findAll(tag).associate {
        val value = it.groupValues[3]
            .ifEmpty { it.groupValues[4] }
            .ifEmpty { it.groupValues[5] }
        it.groupValues[1].lowercase() to unescape(value)
    }

    private fun unescape(value: String): String = value
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
}
