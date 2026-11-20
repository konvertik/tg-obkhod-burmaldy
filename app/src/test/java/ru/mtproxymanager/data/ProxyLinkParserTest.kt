package ru.mtproxymanager.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProxyLinkParserTest {

    private val secret = "dd" + "0123456789abcdef0123456789abcdef"

    @Test
    fun parsesTgDeepLink() {
        val candidate = ProxyLinkParser.parse("tg://proxy?server=203.0.113.10&port=443&secret=$secret")
        assertEquals("203.0.113.10", candidate.server)
        assertEquals(443, candidate.port)
        assertEquals(secret, candidate.secret)
    }

    @Test
    fun parsesTmeLinkWithHtmlEscapedAmpersand() {
        val candidate = ProxyLinkParser.parse("https://t.me/proxy?server=Example.COM&amp;port=8443&amp;secret=$secret")
        assertEquals("example.com", candidate.server)
        assertEquals(8443, candidate.port)
    }

    @Test
    fun rejectsInvalidPort() {
        assertThrows(ProxyFormatException::class.java) {
            ProxyLinkParser.parse("tg://proxy?server=203.0.113.10&port=70000&secret=$secret")
        }
    }

    @Test
    fun rejectsNonHexSecret() {
        assertThrows(ProxyFormatException::class.java) {
            ProxyLinkParser.parse("tg://proxy?server=203.0.113.10&port=443&secret=zzzz")
        }
    }

    @Test
    fun rejectsLoopbackAndForeignLinks() {
        assertThrows(ProxyFormatException::class.java) {
            ProxyLinkParser.parse("tg://proxy?server=127.0.0.1&port=443&secret=$secret")
        }
        assertThrows(ProxyFormatException::class.java) {
            ProxyLinkParser.parse("https://example.com/proxy?server=203.0.113.10&port=443&secret=$secret")
        }
    }

    @Test
    fun parsesSocksLinkWithCredentials() {
        val candidate = ProxyLinkParser.parse("tg://socks?server=203.0.113.5&port=1080&user=alice&pass=s%40cret")
        assertEquals(ProxyKind.SOCKS5, candidate.kind)
        assertEquals("alice", candidate.username)
        assertEquals("s@cret", candidate.password)
    }

    @Test
    fun extractsUniqueValidLinksFromText() {
        val text = """
            <a href="https://t.me/proxy?server=203.0.113.10&amp;port=443&amp;secret=$secret">one</a>
            tg://proxy?server=203.0.113.10&port=443&secret=$secret
            tg://proxy?server=198.51.100.7&port=9443&secret=bad!
            https://t.me/proxy?server=198.51.100.8&port=443&secret=$secret,
        """.trimIndent()

        val result = ProxyLinkParser.extractAll(text)

        assertEquals(2, result.candidates.size)
        assertEquals(1, result.rejected)
    }
}
