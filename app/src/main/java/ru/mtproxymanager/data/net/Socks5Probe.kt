package ru.mtproxymanager.data.net

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Проверяет SOCKS5-прокси: рукопожатие, аутентификацию (если задана) и CONNECT до дата-центра Telegram.
 * Успех означает, что прокси реально пересылает соединение до Telegram.
 */
object Socks5Probe {
    private val TELEGRAM_DC = byteArrayOf(149.toByte(), 154.toByte(), 167.toByte(), 51)
    private const val TELEGRAM_PORT = 443

    fun verify(server: String, port: Int, username: String?, password: String?, timeoutMs: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.soTimeout = timeoutMs
                socket.connect(InetSocketAddress(server, port), timeoutMs)
                val input = socket.getInputStream()
                val output = socket.getOutputStream()
                val hasCredentials = username != null

                output.write(if (hasCredentials) byteArrayOf(5, 2, 0, 2) else byteArrayOf(5, 1, 0))
                output.flush()
                val method = input.readExactly(2)
                if (method[0] != 5.toByte()) return false

                when (method[1].toInt()) {
                    0 -> Unit
                    2 -> {
                        if (username == null) return false
                        if (!authenticate(input, output, username, password.orEmpty())) return false
                    }
                    else -> return false
                }

                output.write(byteArrayOf(5, 1, 0, 1) + TELEGRAM_DC + byteArrayOf((TELEGRAM_PORT shr 8).toByte(), TELEGRAM_PORT.toByte()))
                output.flush()
                val reply = input.readExactly(4)
                if (reply[0] != 5.toByte() || reply[1] != 0.toByte()) return false
                skipBoundAddress(input, reply[3].toInt())
                true
            }
        } catch (e: IOException) {
            false
        }
    }

    private fun authenticate(input: InputStream, output: OutputStream, username: String, password: String): Boolean {
        val user = username.toByteArray(Charsets.UTF_8)
        val pass = password.toByteArray(Charsets.UTF_8)
        output.write(byteArrayOf(1, user.size.toByte()) + user + byteArrayOf(pass.size.toByte()) + pass)
        output.flush()
        val reply = input.readExactly(2)
        return reply[1] == 0.toByte()
    }

    private fun skipBoundAddress(input: InputStream, addressType: Int) {
        when (addressType) {
            1 -> input.readExactly(4 + 2)
            4 -> input.readExactly(16 + 2)
            3 -> {
                val length = input.readExactly(1)[0].toInt() and 0xff
                input.readExactly(length + 2)
            }
            else -> throw IOException("неизвестный тип адреса")
        }
    }

    private fun InputStream.readExactly(n: Int): ByteArray {
        val buf = ByteArray(n)
        var offset = 0
        while (offset < n) {
            val read = read(buf, offset, n - offset)
            if (read < 0) throw IOException("соединение закрыто")
            offset += read
        }
        return buf
    }
}
