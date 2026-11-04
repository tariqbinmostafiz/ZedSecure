package dev.cluvex.zedsecure.core

import com.jcraft.jsch.Proxy
import com.jcraft.jsch.SocketFactory
import dev.cluvex.zedsecure.domain.config.SshProfile
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Turns a payload template into bytes.
 *
 * Placeholders: [host] [port] [host_port] [method] [protocol] [crlf] [cr] [lf]
 * [split] (flush and wait the profile delay) and [split=N] (flush and wait N ms).
 * Real line breaks and the escapes \r \n \t are accepted too. Unknown [tokens] are kept as text.
 */
object PayloadText {
    class Part(val bytes: ByteArray, val delayAfterMs: Int)

    const val DEFAULT_PROXY_PAYLOAD = "CONNECT [host_port] HTTP/1.1[crlf]Host: [host_port][crlf][crlf]"

    private val TOKEN = Regex("\\[([A-Za-z_]+)(?:=(\\d{1,6}))?]")
    private val KNOWN = setOf("host", "port", "host_port", "method", "protocol", "crlf", "cr", "lf", "split")

    fun unknownTokens(template: String): List<String> =
        TOKEN.findAll(template).map { it.groupValues[1].lowercase() }.filter { it !in KNOWN }.distinct().toList()

    fun render(template: String, host: String, port: Int, defaultDelayMs: Int): List<Part> {
        val text = normalizeBreaks(template)
        val parts = ArrayList<Part>()
        var buffer = ByteArrayOutputStream()
        var last = 0

        fun add(chunk: String) {
            if (chunk.isNotEmpty()) buffer.write(chunk.toByteArray(Charsets.UTF_8))
        }

        for (match in TOKEN.findAll(text)) {
            add(text.substring(last, match.range.first))
            last = match.range.last + 1
            val name = match.groupValues[1].lowercase()
            val arg = match.groupValues[2].toIntOrNull()
            when (name) {
                "host" -> add(host)
                "port" -> add(port.toString())
                "host_port" -> add(hostPort(host, port))
                "method" -> add("CONNECT")
                "protocol" -> add("HTTP/1.1")
                "crlf" -> add("\r\n")
                "cr" -> add("\r")
                "lf" -> add("\n")
                "split" -> {
                    if (buffer.size() > 0) {
                        parts.add(Part(buffer.toByteArray(), (arg ?: defaultDelayMs).coerceIn(0, 60_000)))
                        buffer = ByteArrayOutputStream()
                    }
                }
                else -> add(match.value)
            }
        }
        add(text.substring(last))
        if (buffer.size() > 0) parts.add(Part(buffer.toByteArray(), 0))
        return parts
    }

    private fun hostPort(host: String, port: Int): String =
        if (host.contains(':') && !host.startsWith("[")) "[$host]:$port" else "$host:$port"

    private fun normalizeBreaks(template: String): String =
        template
            .replace("\r\n", "\n").replace("\r", "\n").replace("\n", "\r\n")
            .replace("\\r", "\r").replace("\\n", "\n").replace("\\t", "\t")
}

/**
 * JSch proxy that opens the transport socket itself:
 * connect (to the SSH host, or to an HTTP proxy) -> optional TLS with a custom SNI ->
 * send the payload -> swallow the HTTP reply -> hand the same socket to the SSH handshake.
 */
class PayloadProxy(
    private val profile: SshProfile,
    private val log: (String) -> Unit = {},
) : Proxy {

    private var socket: Socket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null

    override fun connect(socketFactory: SocketFactory?, host: String, port: Int, timeout: Int) {
        val viaProxy = profile.payloadMode == SshProfile.PAYLOAD_PROXY
        val dialHost = if (viaProxy) profile.proxyHost.trim() else host
        val dialPort = if (viaProxy) profile.proxyPort else port
        if (dialHost.isEmpty()) throw IOException("proxy host is empty")
        if (dialPort !in 1..65535) throw IOException("invalid port $dialPort")

        val template = profile.payload.ifBlank { if (viaProxy) PayloadText.DEFAULT_PROXY_PAYLOAD else "" }
        val unknown = PayloadText.unknownTokens(template)
        if (unknown.isNotEmpty()) log("payload has unknown tags kept as plain text: ${unknown.joinToString { "[$it]" }}")
        val parts = PayloadText.render(template, host, port, profile.payloadDelayMs)
        val connectTimeout = if (timeout > 0) timeout else DEFAULT_TIMEOUT_MS

        var current: Socket? = null
        try {
            val raw = Socket()
            current = raw
            raw.tcpNoDelay = true
            raw.connect(InetSocketAddress(dialHost, dialPort), connectTimeout)
            log("transport connected to $dialHost:$dialPort (${if (viaProxy) "proxy" else "direct"})")

            var active: Socket = raw
            val sni = profile.sni.trim()
            if (sni.isNotEmpty()) {
                raw.soTimeout = connectTimeout
                active = wrapTls(raw, sni, dialPort)
                current = active
                log("TLS up with SNI $sni (outer certificate is not verified on purpose)")
            }

            val out = active.getOutputStream()
            if (parts.isNotEmpty()) {
                for ((index, part) in parts.withIndex()) {
                    out.write(part.bytes)
                    out.flush()
                    if (part.delayAfterMs > 0 && index < parts.lastIndex) Thread.sleep(part.delayAfterMs.toLong())
                }
                log("payload sent: ${parts.sumOf { it.bytes.size }} bytes in ${parts.size} part(s): ${preview(parts)}")
            }

            val pushback = PushbackInputStream(active.getInputStream(), PEEK_BYTES)
            if (parts.isNotEmpty() && profile.responseMode != SshProfile.RESPONSE_NONE) {
                active.soTimeout = connectTimeout
                consumeHttpReplies(pushback, profile.responseMode, log)
            }
            active.soTimeout = 0

            socket = active
            input = pushback
            output = out
        } catch (e: Exception) {
            runCatching { current?.close() }
            if (e is IOException) throw e
            throw IOException(e.message ?: e.javaClass.simpleName, e)
        }
    }

    override fun getInputStream(): InputStream? = input

    override fun getOutputStream(): OutputStream? = output

    override fun getSocket(): Socket? = socket

    override fun close() {
        runCatching { input?.close() }
        runCatching { output?.close() }
        runCatching { socket?.close() }
        input = null
        output = null
        socket = null
    }

    private fun wrapTls(raw: Socket, sni: String, port: Int): SSLSocket {
        val context = SSLContext.getInstance("TLS")
        context.init(null, arrayOf<TrustManager>(TrustAnyCertificate), SecureRandom())
        val ssl = context.socketFactory.createSocket(raw, sni, port, true) as SSLSocket
        runCatching {
            val params = ssl.sslParameters
            params.serverNames = listOf(SNIHostName(sni))
            ssl.sslParameters = params
        }
        ssl.startHandshake()
        return ssl
    }

    // The outer TLS layer only carries the payload under a chosen SNI, so there is no meaningful
    // hostname to verify. Authenticity comes from the SSH host key (see the host key pin setting).
    private object TrustAnyCertificate : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    companion object {
        private const val DEFAULT_TIMEOUT_MS = 30_000
        private const val PEEK_BYTES = 8
        private const val MAX_HEADER_BYTES = 16 * 1024
        private const val MAX_HTTP_REPLIES = 5
        private val HTTP_MAGIC = "HTTP/".toByteArray(Charsets.US_ASCII)

        private fun preview(parts: List<PayloadText.Part>): String {
            val all = parts.joinToString("|") { String(it.bytes, Charsets.UTF_8) }
                .replace("\r", "\\r").replace("\n", "\\n")
            return if (all.length > 300) all.take(300) + "..." else all
        }

        /**
         * Reads and drops HTTP replies that arrive before the SSH banner. Anything that does not start
         * with "HTTP/" is pushed back untouched so the SSH layer sees it (usually the SSH banner).
         */
        fun consumeHttpReplies(input: PushbackInputStream, mode: String, log: (String) -> Unit = {}) {
            var replies = 0
            while (replies < MAX_HTTP_REPLIES) {
                val head = ByteArray(HTTP_MAGIC.size)
                var filled = 0
                while (filled < head.size) {
                    val read = input.read(head, filled, head.size - filled)
                    if (read < 0) break
                    filled += read
                }
                if (filled == 0) throw IOException("connection closed before any reply (payload rejected?)")
                if (filled < head.size || !head.contentEquals(HTTP_MAGIC)) {
                    input.unread(head, 0, filled)
                    return
                }

                val block = ByteArrayOutputStream()
                block.write(head)
                while (!endsWithBlankLine(block)) {
                    val b = input.read()
                    if (b < 0) throw IOException("connection closed in the middle of the reply")
                    block.write(b)
                    if (block.size() > MAX_HEADER_BYTES) throw IOException("reply headers too large")
                }

                val statusLine = String(block.toByteArray(), Charsets.ISO_8859_1).lineSequence().first().trim()
                val status = statusLine.split(' ').getOrNull(1)?.toIntOrNull()
                    ?: throw IOException("unreadable status line: $statusLine")
                log("server replied: $statusLine")
                val accepted = if (mode == SshProfile.RESPONSE_STRICT) status in 200..299 || status == 101 else status < 400
                if (!accepted) throw IOException("payload rejected: $statusLine")
                replies++
            }
        }

        private fun endsWithBlankLine(block: ByteArrayOutputStream): Boolean {
            val size = block.size()
            if (size < 2) return false
            val bytes = block.toByteArray()
            if (bytes[size - 1] == '\n'.code.toByte() && bytes[size - 2] == '\n'.code.toByte()) return true
            return size >= 4 && bytes[size - 4] == '\r'.code.toByte() && bytes[size - 3] == '\n'.code.toByte() &&
                bytes[size - 2] == '\r'.code.toByte() && bytes[size - 1] == '\n'.code.toByte()
        }
    }
}

/** SHA-256 fingerprints of SSH host keys, in the same "SHA256:..." form that OpenSSH prints. */
object HostKeyPin {
    @OptIn(ExperimentalEncodingApi::class)
    fun fingerprint(base64Key: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(Base64.decode(base64Key.trim()))
        return "SHA256:" + Base64.encode(digest).trimEnd('=')
    }

    fun matches(pin: String, fingerprint: String): Boolean {
        val wanted = pin.trim().removePrefix("SHA256:").removePrefix("sha256:").trimEnd('=')
        return wanted.isNotEmpty() && wanted == fingerprint.removePrefix("SHA256:").trimEnd('=')
    }
}
