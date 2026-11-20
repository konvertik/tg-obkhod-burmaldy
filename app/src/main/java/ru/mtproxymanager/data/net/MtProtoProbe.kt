package ru.mtproxymanager.data.net

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Проверяет MTProto-прокси настоящим запросом req_pq_multi через прокси до дата-центра Telegram.
 * Успех только если в ответе вернулся наш nonce: это подтверждает работу прокси, а не только открытый порт.
 * Для FakeTLS (секрет ee) сначала выполняется TLS-подобное рукопожатие с HMAC-подписью по секрету.
 */
object MtProtoProbe {
    private const val RES_PQ_CTOR = 0x05162463
    private const val REQ_PQ_CTOR = 0xbe7e8ef1L
    private const val MAX_RESPONSE_BYTES = 4096
    private const val TELEGRAM_DC_ID = 2
    private const val DEFAULT_SNI = "www.google.com"
    private val FORBIDDEN_STARTS = setOf("HEAD", "POST", "GET ", "OPTI", "\u00ee\u00ee\u00ee\u00ee")
    private val TLS_CHANGE_CIPHER = byteArrayOf(0x14, 0x03, 0x03, 0x00, 0x01, 0x01)

    fun verify(server: String, port: Int, secret: String, timeoutMs: Int): Boolean {
        val parts = SecretParts.parse(secret) ?: return false
        return try {
            Socket().use { socket ->
                socket.soTimeout = timeoutMs
                socket.connect(InetSocketAddress(server, port), timeoutMs)
                val input = socket.getInputStream()
                val output = socket.getOutputStream()
                val wire: Wire = if (parts.fakeTls) {
                    FakeTlsWire(input, output).also {
                        it.handshake(FakeTls.clientHello(parts.key, parts.domain ?: DEFAULT_SNI.toByteArray()), socket)
                    }
                } else {
                    PlainWire(input, output)
                }
                exchange(wire, parts.key)
            }
        } catch (e: IOException) {
            false
        }
    }

    private fun exchange(wire: Wire, key: ByteArray): Boolean {
        val init = randomObfuscationNonce()
        for (i in 56 until 60) init[i] = 0xef.toByte()
        init[60] = (TELEGRAM_DC_ID and 0xff).toByte()
        init[61] = ((TELEGRAM_DC_ID shr 8) and 0xff).toByte()

        val reversed = init.reversedArray()
        val encryptor = aesCtr(Cipher.ENCRYPT_MODE, sha256(init.copyOfRange(8, 40) + key), init.copyOfRange(40, 56))
        val decryptor = aesCtr(Cipher.DECRYPT_MODE, sha256(reversed.copyOfRange(8, 40) + key), reversed.copyOfRange(40, 56))

        val encryptedInit = encryptor.update(init)
        val header = init.copyOf(56) + encryptedInit.copyOfRange(56, 64)

        val pqNonce = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val body = ByteBuffer.allocate(40).order(ByteOrder.LITTLE_ENDIAN)
        body.put(ByteArray(8))
        body.putLong((System.currentTimeMillis() / 1000L) shl 32)
        body.putInt(20)
        body.putInt(REQ_PQ_CTOR.toInt())
        body.put(pqNonce)
        val frame = byteArrayOf(10) + body.array()

        wire.send(header + encryptor.update(frame))

        val firstByte = decryptor.update(wire.receive(1))[0].toInt() and 0xff
        var words = firstByte
        if (firstByte == 0x7f) {
            val len = decryptor.update(wire.receive(3))
            words = (len[0].toInt() and 0xff) or ((len[1].toInt() and 0xff) shl 8) or ((len[2].toInt() and 0xff) shl 16)
        }
        val size = words * 4
        if (size < 40 || size > MAX_RESPONSE_BYTES) return false

        val payload = decryptor.update(wire.receive(size))
        val constructor = ByteBuffer.wrap(payload, 20, 4).order(ByteOrder.LITTLE_ENDIAN).int
        return constructor == RES_PQ_CTOR && payload.copyOfRange(24, 40).contentEquals(pqNonce)
    }

    private fun randomObfuscationNonce(): ByteArray {
        val random = SecureRandom()
        while (true) {
            val nonce = ByteArray(64).also { random.nextBytes(it) }
            val start = String(nonce, 0, 4, Charsets.ISO_8859_1)
            if (nonce[0] == 0xef.toByte()) continue
            if (start in FORBIDDEN_STARTS) continue
            if (nonce[4] == 0.toByte() && nonce[5] == 0.toByte() && nonce[6] == 0.toByte() && nonce[7] == 0.toByte()) continue
            return nonce
        }
    }

    private fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

    private fun aesCtr(mode: Int, key: ByteArray, iv: ByteArray): Cipher =
        Cipher.getInstance("AES/CTR/NoPadding").apply {
            init(mode, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        }

    private class SecretParts(val key: ByteArray, val fakeTls: Boolean, val domain: ByteArray?) {
        companion object {
            fun parse(secret: String): SecretParts? {
                val s = secret.lowercase()
                return when {
                    s.startsWith("ee") && s.length >= 34 -> SecretParts(
                        hex(s.substring(2, 34)),
                        fakeTls = true,
                        domain = s.substring(34).takeIf { it.isNotEmpty() }?.let { hex(it) },
                    )
                    s.startsWith("dd") && s.length == 34 -> SecretParts(hex(s.substring(2)), fakeTls = false, domain = null)
                    s.length == 32 -> SecretParts(hex(s), fakeTls = false, domain = null)
                    else -> null
                }
            }

            private fun hex(s: String): ByteArray = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        }
    }

    private interface Wire {
        fun send(data: ByteArray)
        fun receive(n: Int): ByteArray
    }

    private class PlainWire(private val input: InputStream, private val output: OutputStream) : Wire {
        override fun send(data: ByteArray) {
            output.write(data)
            output.flush()
        }

        override fun receive(n: Int): ByteArray = input.readExactly(n)
    }

    private class FakeTlsWire(private val input: InputStream, private val output: OutputStream) : Wire {
        private var buffer = ByteArray(0)

        fun handshake(clientHello: ByteArray, socket: Socket) {
            output.write(clientHello)
            output.flush()
            var sawAppData = false
            repeat(MAX_HANDSHAKE_RECORDS) {
                if (!sawAppData) {
                    when (readRecord().first) {
                        0x17 -> sawAppData = true
                        0x16, 0x14 -> Unit
                        else -> throw IOException("неожиданная TLS-запись")
                    }
                }
            }
            if (!sawAppData) throw IOException("TLS-ответ не получен")
            drainServerRecords(socket)
            output.write(TLS_CHANGE_CIPHER)
            output.flush()
        }

        /** Прокси шлёт несколько зашифрованных записей-заглушек подряд; вычитываем их до паузы. */
        private fun drainServerRecords(socket: Socket) {
            val original = socket.soTimeout
            socket.soTimeout = DRAIN_TIMEOUT_MS
            try {
                repeat(MAX_DRAIN_RECORDS) { readRecord() }
            } catch (e: SocketTimeoutException) {
                Unit
            } finally {
                socket.soTimeout = original
            }
        }

        override fun send(data: ByteArray) {
            var offset = 0
            while (offset < data.size) {
                val chunk = minOf(MAX_TLS_RECORD, data.size - offset)
                output.write(byteArrayOf(0x17, 0x03, 0x03, (chunk shr 8).toByte(), chunk.toByte()))
                output.write(data, offset, chunk)
                offset += chunk
            }
            output.flush()
        }

        override fun receive(n: Int): ByteArray {
            while (buffer.size < n) buffer += nextAppData()
            val result = buffer.copyOf(n)
            buffer = buffer.copyOfRange(n, buffer.size)
            return result
        }

        private fun nextAppData(): ByteArray {
            repeat(MAX_HANDSHAKE_RECORDS) {
                val (type, payload) = readRecord()
                when (type) {
                    0x17 -> return payload
                    0x14, 0x16 -> Unit
                    else -> throw IOException("неожиданная TLS-запись")
                }
            }
            throw IOException("данные не получены")
        }

        private fun readRecord(): Pair<Int, ByteArray> {
            val header = input.readExactly(5)
            val length = ((header[3].toInt() and 0xff) shl 8) or (header[4].toInt() and 0xff)
            return (header[0].toInt() and 0xff) to input.readExactly(length)
        }

        private companion object {
            const val MAX_TLS_RECORD = 16_384
            const val MAX_HANDSHAKE_RECORDS = 10
            const val MAX_DRAIN_RECORDS = 50
            const val DRAIN_TIMEOUT_MS = 400
        }
    }

    /** Формирует ClientHello с digest по секрету, который проверяет FakeTLS-прокси. */
    private object FakeTls {
        fun clientHello(key: ByteArray, sni: ByteArray): ByteArray {
            val random = SecureRandom()
            val sessionId = ByteArray(32).also { random.nextBytes(it) }
            val keyShare = ByteArray(32).also { random.nextBytes(it) }
            val body = ByteArrayOutputStream()
            body.write(byteArrayOf(0x03, 0x03))
            body.write(ByteArray(32))
            body.write(32)
            body.write(sessionId)
            val suites = byteArrayOf(
                0x13, 0x01, 0x13, 0x02, 0x13, 0x03,
                0xc0.toByte(), 0x2b, 0xc0.toByte(), 0x2f, 0xc0.toByte(), 0x2c, 0xc0.toByte(), 0x30,
            )
            body.u16(suites.size)
            body.write(suites)
            body.write(byteArrayOf(0x01, 0x00))

            val extensions = ByteArrayOutputStream()
            extensions.extension(0x0000) {
                u16(sni.size + 3)
                write(0x00)
                u16(sni.size)
                write(sni)
            }
            extensions.extension(0x000a) {
                u16(4)
                u16(0x001d)
                u16(0x0017)
            }
            extensions.extension(0x000b) {
                write(byteArrayOf(0x01, 0x00))
            }
            extensions.extension(0x000d) {
                u16(8)
                u16(0x0804)
                u16(0x0805)
                u16(0x0401)
                u16(0x0403)
            }
            extensions.extension(0x002b) {
                write(0x04)
                write(byteArrayOf(0x03, 0x04, 0x03, 0x03))
            }
            extensions.extension(0x002d) {
                write(byteArrayOf(0x01, 0x01))
            }
            extensions.extension(0x0033) {
                u16(36)
                u16(0x001d)
                u16(32)
                write(keyShare)
            }
            body.u16(extensions.size())
            body.write(extensions.toByteArray())

            val handshake = ByteArrayOutputStream()
            handshake.write(0x01)
            handshake.write(body.size() shr 16)
            handshake.write((body.size() shr 8) and 0xff)
            handshake.write(body.size() and 0xff)
            handshake.write(body.toByteArray())

            val record = ByteArrayOutputStream()
            record.write(byteArrayOf(0x16, 0x03, 0x01))
            record.u16(handshake.size())
            record.write(handshake.toByteArray())
            val hello = record.toByteArray()

            val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }
            val digest = mac.doFinal(hello)
            val timestamp = (System.currentTimeMillis() / 1000L).toInt()
            for (i in 0 until 4) {
                digest[28 + i] = (digest[28 + i].toInt() xor ((timestamp shr (8 * i)) and 0xff)).toByte()
            }
            digest.copyInto(hello, destinationOffset = RANDOM_OFFSET)
            return hello
        }

        private const val RANDOM_OFFSET = 11

        private fun ByteArrayOutputStream.u16(value: Int) {
            write((value shr 8) and 0xff)
            write(value and 0xff)
        }

        private fun ByteArrayOutputStream.extension(type: Int, data: ByteArrayOutputStream.() -> Unit) {
            val payload = ByteArrayOutputStream().apply(data)
            u16(type)
            u16(payload.size())
            write(payload.toByteArray())
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
