package com.elnor.app

/** What a link in a note does when the user chooses it. */
enum class LinkType { WEB, EMAIL, PHONE }

/**
 * One link found in the text of a note.
 *
 * [start] (included) and [end] (not included) are positions in the text. [text] is the text as the
 * user typed it. [target] is what is handed to Android: "https://...", "mailto:..." or "tel:...".
 */
data class LinkSpan(
    val start: Int,
    val end: Int,
    val type: LinkType,
    val text: String,
    val target: String
)

/**
 * Finds web links, e-mail addresses and phone numbers in a note, so the editor can show them as
 * links. Nothing is stored: the links are calculated from the text every time, so they always
 * follow what the user typed.
 *
 * Rules (kept careful on purpose, a wrong link is worse than a missed one):
 *  - Web: only text that starts with http://, https:// or www. A bare "example.com" is not a link.
 *    Punctuation at the end ( . , ; : ! ? quotes, the Bengali danda, and a closing bracket
 *    that has no opening bracket inside the link) is not part of the link.
 *  - E-mail: name@domain.tld
 *  - Phone: a number that starts with +, or a Bangladeshi mobile number (01XXXXXXXXX, 8801XXXXXXXXX),
 *    or a local number that starts with 0 and has 10 or 11 digits. Spaces, dashes, dots and
 *    brackets inside the number are fine. Bengali digits (০-৯) work too. Other numbers (prices,
 *    dates, years, versions, IP addresses) are left alone.
 *  - A text is never two links: e-mails and phone numbers inside a web link are ignored, and
 *    phone numbers inside an e-mail address are ignored.
 *
 * It has no Android code, so it is tested with plain unit tests.
 */
object LinkDetector {

    private val WEB = Regex(
        """(?<![\p{L}\p{N}@])(?:https?://|www\.)[^\s<>"'।]+""",
        RegexOption.IGNORE_CASE
    )

    private val EMAIL = Regex(
        """(?<![A-Za-z0-9._%+\-@])[A-Za-z0-9._%+\-]+@[A-Za-z0-9\-]+(?:\.[A-Za-z0-9\-]+)*\.[A-Za-z]{2,}(?![A-Za-z0-9@])"""
    )

    // A run of digits that may have spaces, dashes, dots and brackets inside. Whether it really
    // is a phone number is decided by [phoneDigits].
    private val PHONE_CANDIDATE = Regex(
        """(?<![\p{L}\p{N}])(?:\+ ?)?[0-9০-৯][0-9০-৯ ().\-]*[0-9০-৯](?![\p{L}\p{N}])"""
    )

    private val WORD = Regex("""[^ ]+""")
    private val BD_MOBILE = Regex("""(?:880|0)1[3-9][0-9]{8}""")

    /** All links of [text], in the order they appear. They never overlap. */
    fun find(text: String): List<LinkSpan> {
        if (text.length < 5) return emptyList()
        val found = ArrayList<LinkSpan>()

        for (match in WEB.findAll(text)) {
            val url = trimWebEnd(match.value)
            val prefixLength = webPrefixLength(url)
            // Something must follow the prefix, and it must start like a host name
            if (url.length <= prefixLength || !url[prefixLength].isLetterOrDigit()) continue
            val start = match.range.first
            val shown = text.substring(start, start + url.length)
            val target = if (url.startsWith("www.", ignoreCase = true)) {
                "https://$shown"
            } else {
                // The scheme in lower case; the rest is kept exactly as typed
                val schemeEnd = shown.indexOf("://")
                shown.substring(0, schemeEnd).lowercase() + shown.substring(schemeEnd)
            }
            found.add(LinkSpan(start, start + url.length, LinkType.WEB, shown, target))
        }

        for (match in EMAIL.findAll(text)) {
            val range = match.range
            if (overlaps(found, range.first, range.last + 1)) continue
            found.add(
                LinkSpan(range.first, range.last + 1, LinkType.EMAIL, match.value, "mailto:${match.value}")
            )
        }

        for (match in PHONE_CANDIDATE.findAll(text)) {
            addPhones(text, match.range.first, match.range.last + 1, found)
        }

        return found.sortedBy { it.start }
    }

    /** The link that has the cursor inside it (not at its first or last edge), or null. */
    fun linkAtCursor(links: List<LinkSpan>, cursor: Int): LinkSpan? =
        links.firstOrNull { cursor > it.start && cursor < it.end }

    // ---- web ----

    private fun webPrefixLength(url: String): Int =
        if (url.startsWith("www.", ignoreCase = true)) 4 else url.indexOf("://") + 3

    /** Removes the punctuation that belongs to the sentence and not to the link. */
    private fun trimWebEnd(raw: String): String {
        var end = raw.length
        while (end > 0) {
            val c = raw[end - 1]
            val drop = when (c) {
                '.', ',', ';', ':', '!', '?' -> true
                ')' -> raw.countChar(0, end, ')') > raw.countChar(0, end, '(')
                ']' -> raw.countChar(0, end, ']') > raw.countChar(0, end, '[')
                '}' -> raw.countChar(0, end, '}') > raw.countChar(0, end, '{')
                else -> false
            }
            if (!drop) break
            end--
        }
        return raw.substring(0, end)
    }

    private fun String.countChar(from: Int, to: Int, ch: Char): Int {
        var n = 0
        for (i in from until to) if (this[i] == ch) n++
        return n
    }

    // ---- phone ----

    /** Adds the phone number(s) found in text[start, end), unless they overlap another link. */
    private fun addPhones(text: String, start: Int, end: Int, found: MutableList<LinkSpan>) {
        val whole = phoneDigits(text.substring(start, end))
        if (whole != null) {
            if (!overlaps(found, start, end)) addPhone(text, start, end, whole, found)
            return
        }
        // Two numbers next to each other ("017... 018...") are one candidate: check each word.
        for (word in WORD.findAll(text.substring(start, end))) {
            var s = start + word.range.first
            var e = start + word.range.last + 1
            while (s < e && !isDigit(text[s]) && text[s] != '+') s++
            while (e > s && !isDigit(text[e - 1])) e--
            if (e <= s) continue
            val digits = phoneDigits(text.substring(s, e)) ?: continue
            if (!overlaps(found, s, e)) addPhone(text, s, e, digits, found)
        }
    }

    private fun addPhone(text: String, start: Int, end: Int, target: String, found: MutableList<LinkSpan>) {
        found.add(LinkSpan(start, end, LinkType.PHONE, text.substring(start, end), "tel:$target"))
    }

    /**
     * The number for the "tel:" link ("+" if there is one, then English digits only), or null when
     * [candidate] does not look like a phone number.
     */
    private fun phoneDigits(candidate: String): String? {
        val plus = candidate.startsWith("+")
        val digits = StringBuilder()
        for (c in candidate) {
            if (c in '0'..'9') digits.append(c)
            else if (c in '০'..'৯') digits.append('0' + (c - '০'))
        }
        val d = digits.toString()
        val valid = when {
            BD_MOBILE.matches(d) -> true
            plus -> d.length in 8..15
            d.startsWith("0") -> d.length in 10..11
            else -> false
        }
        return if (valid) (if (plus) "+" else "") + d else null
    }

    private fun isDigit(c: Char) = c in '0'..'9' || c in '০'..'৯'

    private fun overlaps(found: List<LinkSpan>, start: Int, end: Int): Boolean =
        found.any { start < it.end && it.start < end }
}
