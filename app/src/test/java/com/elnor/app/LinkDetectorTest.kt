package com.elnor.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkDetectorTest {

    private fun links(text: String) = LinkDetector.find(text)

    private fun only(text: String): LinkSpan {
        val found = links(text)
        assertEquals("links in: $text", 1, found.size)
        return found[0]
    }

    // ---- web ----

    @Test
    fun httpsLink() {
        val link = only("see https://example.com/page?a=1 now")
        assertEquals(LinkType.WEB, link.type)
        assertEquals("https://example.com/page?a=1", link.text)
        assertEquals("https://example.com/page?a=1", link.target)
        assertEquals(4, link.start)
        assertEquals(4 + "https://example.com/page?a=1".length, link.end)
    }

    @Test
    fun wwwLinkGetsHttps() {
        val link = only("go to www.example.com today")
        assertEquals("www.example.com", link.text)
        assertEquals("https://www.example.com", link.target)
    }

    @Test
    fun upperCaseSchemeIsLowerCasedInTarget() {
        val link = only("HTTP://Example.com/Path")
        assertEquals("HTTP://Example.com/Path", link.text)
        assertEquals("http://Example.com/Path", link.target)
    }

    @Test
    fun bareDomainIsNotALink() {
        assertTrue(links("write to example.com or visit google.com").isEmpty())
    }

    @Test
    fun sentencePunctuationIsNotPartOfTheLink() {
        assertEquals("https://example.com", only("Visit https://example.com.").text)
        assertEquals("https://example.com", only("Visit https://example.com, then").text)
        assertEquals("https://example.com", only("Really https://example.com?!").text)
        assertEquals("https://example.com/a", only("(https://example.com/a)").text)
    }

    @Test
    fun bracketInsideTheLinkIsKept() {
        assertEquals(
            "https://en.wikipedia.org/wiki/Kotlin_(language)",
            only("https://en.wikipedia.org/wiki/Kotlin_(language)").text
        )
        assertEquals(
            "https://en.wikipedia.org/wiki/Kotlin_(language)",
            only("(https://en.wikipedia.org/wiki/Kotlin_(language))").text
        )
    }

    @Test
    fun bengaliDandaEndsTheLink() {
        val link = only("সাইট https://example.com।")
        assertEquals("https://example.com", link.text)
    }

    @Test
    fun linkInsideBengaliText() {
        val link = only("আমার সাইট হলো https://example.com/bn এবং ভালো")
        assertEquals("https://example.com/bn", link.text)
    }

    @Test
    fun prefixAloneIsNotALink() {
        assertTrue(links("just https:// or www. here").isEmpty())
    }

    @Test
    fun prefixInsideAWordIsNotALink() {
        assertTrue(links("awww.example.com").isEmpty())
    }

    // ---- e-mail ----

    @Test
    fun emailLink() {
        val link = only("mail me: name.surname+tag@mail.example.co.uk please")
        assertEquals(LinkType.EMAIL, link.type)
        assertEquals("name.surname+tag@mail.example.co.uk", link.text)
        assertEquals("mailto:name.surname+tag@mail.example.co.uk", link.target)
    }

    @Test
    fun emailAtEndOfSentence() {
        assertEquals("a@b.com", only("Send it to a@b.com.").text)
    }

    @Test
    fun emailNextToBengaliText() {
        assertEquals("me@example.com", only("me@example.comএ পাঠান").text)
    }

    @Test
    fun notEmails() {
        assertTrue(links("@handle and a@b and user@localhost").isEmpty())
    }

    @Test
    fun emailInsideAWebLinkIsNotASecondLink() {
        val link = only("https://example.com/user/a@b.com/profile")
        assertEquals(LinkType.WEB, link.type)
    }

    // ---- phone ----

    @Test
    fun bangladeshiMobileNumbers() {
        assertEquals("tel:01712345678", only("call 01712345678 now").target)
        assertEquals("tel:01712345678", only("call 01712-345678 now").target)
        assertEquals("tel:01712345678", only("call 01712 345 678 now").target)
        assertEquals("tel:+8801712345678", only("call +8801712345678 now").target)
        assertEquals("tel:+8801712345678", only("call +880 1712-345678 now").target)
        assertEquals("tel:8801712345678", only("call 8801712345678 now").target)
    }

    @Test
    fun bengaliDigitsAreConverted() {
        val link = only("ফোন করুন ০১৭১২৩৪৫৬৭৮ এখনই")
        assertEquals(LinkType.PHONE, link.type)
        assertEquals("০১৭১২৩৪৫৬৭৮", link.text)
        assertEquals("tel:01712345678", link.target)
    }

    @Test
    fun internationalNumberWithPlus() {
        assertEquals("tel:+14155552671", only("US: +1 (415) 555-2671").target)
        assertEquals("tel:+442071838750", only("UK +44 20 7183 8750.").target)
    }

    @Test
    fun localLandlineNumber() {
        assertEquals("tel:0255667788", only("office 02-55667788").target)
    }

    @Test
    fun phoneInBrackets() {
        val link = only("Rahim (01712345678) will call")
        assertEquals("01712345678", link.text)
    }

    @Test
    fun twoNumbersNextToEachOther() {
        val found = links("01712345678 01812345678")
        assertEquals(2, found.size)
        assertEquals("tel:01712345678", found[0].target)
        assertEquals("tel:01812345678", found[1].target)
        assertEquals(12, found[1].start)
    }

    @Test
    fun otherNumbersAreNotPhones() {
        assertTrue(links("price 1500000 taka").isEmpty())
        assertTrue(links("date 2026-10-25").isEmpty())
        assertTrue(links("version 1.2.3.4.5.6").isEmpty())
        assertTrue(links("ip 192.168.100.200").isEmpty())
        assertTrue(links("pi is 0.5000000").isEmpty())
        assertTrue(links("order ID12345678901").isEmpty())
        assertTrue(links("year 2026 and 12 notes").isEmpty())
        assertTrue(links("too short +12345").isEmpty())
    }

    @Test
    fun numberInsideAWebLinkIsNotASecondLink() {
        val link = only("https://example.com/call/01712345678")
        assertEquals(LinkType.WEB, link.type)
    }

    @Test
    fun numberInsideAnEmailIsNotASecondLink() {
        val link = only("01712345678@example.com")
        assertEquals(LinkType.EMAIL, link.type)
    }

    // ---- several links, positions ----

    @Test
    fun severalLinksInOrder() {
        val text = "Mail a@b.com, call 01712345678 or open www.example.com"
        val found = links(text)
        assertEquals(
            listOf(LinkType.EMAIL, LinkType.PHONE, LinkType.WEB),
            found.map { it.type }
        )
        for (link in found) assertEquals(link.text, text.substring(link.start, link.end))
        assertTrue(found.zipWithNext().all { (a, b) -> a.end <= b.start })
    }

    @Test
    fun linksOnSeveralLines() {
        val text = "first\nwww.example.com\n01712345678\n"
        val found = links(text)
        assertEquals(2, found.size)
        for (link in found) assertEquals(link.text, text.substring(link.start, link.end))
    }

    @Test
    fun emptyAndPlainText() {
        assertTrue(links("").isEmpty())
        assertTrue(links("just some words, nothing else.").isEmpty())
        assertTrue(links("শুধু বাংলা লেখা, কোনো লিংক নেই।").isEmpty())
    }

    // ---- cursor ----

    @Test
    fun linkAtCursorOnlyInsideTheLink() {
        val text = "ab www.example.com cd"
        val found = links(text)
        val link = found[0] // 3 until 18
        assertNull(LinkDetector.linkAtCursor(found, 0))
        assertNull(LinkDetector.linkAtCursor(found, link.start)) // right before the link
        assertEquals(link, LinkDetector.linkAtCursor(found, link.start + 1))
        assertEquals(link, LinkDetector.linkAtCursor(found, link.end - 1))
        assertNull(LinkDetector.linkAtCursor(found, link.end)) // right after the link: typing there
        assertNull(LinkDetector.linkAtCursor(found, text.length))
    }
}
