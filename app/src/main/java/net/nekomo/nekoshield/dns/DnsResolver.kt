package net.nekomo.nekoshield.dns

import android.net.VpnService
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLSocketFactory

/**
 * Forwards a raw DNS wire query to the configured upstream and returns the
 * raw wire response. Supports plain UDP/53, DoT (853) and DoH (POST).
 * All sockets are routed OUTSIDE the tunnel via VpnService.protect().
 */
object DnsResolver {

    enum class Proto { UDP, DOT, DOH }

    data class Upstream(
        val proto: Proto,
        val host: String,          // IP/host for udp+dot; url for doh
        val port: Int = 53,
        val dohHost: String = "",  // hostname for TLS SNI on DoT (usually = host)
        val label: String = "",
    )

    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    // DoH needs its sockets protect()ed too, otherwise every query loops back
    // into the tunnel — and under always-on "block connections without VPN"
    // the self-disallowed app path is blocked outright. One client per
    // VpnService instance; the factory tags each raw socket before TLS wraps it.
    private var httpVpn: VpnService? = null
    private var httpProtected: OkHttpClient? = null

    @Synchronized
    private fun dohClient(vpn: VpnService?): OkHttpClient {
        if (vpn == null) return http
        if (httpProtected != null && httpVpn === vpn) return httpProtected!!
        val protect = { socket: Socket -> vpn.protect(socket); socket }
        httpProtected = http.newBuilder()
            .socketFactory(object : javax.net.SocketFactory() {
                private val d = getDefault()
                override fun createSocket() = protect(d.createSocket())
                override fun createSocket(host: String?, port: Int) = protect(d.createSocket(host, port))
                override fun createSocket(host: String?, port: Int, local: InetAddress?, localPort: Int) =
                    protect(d.createSocket(host, port, local, localPort))
                override fun createSocket(host: InetAddress?, port: Int) = protect(d.createSocket(host, port))
                override fun createSocket(host: InetAddress?, port: Int, local: InetAddress?, localPort: Int) =
                    protect(d.createSocket(host, port, local, localPort))
            })
            .build()
        httpVpn = vpn
        return httpProtected!!
    }

    fun resolve(vpn: VpnService?, up: Upstream, query: ByteArray, qLen: Int): ByteArray? {
        return try {
            when (up.proto) {
                Proto.UDP -> udp(vpn, up, query, qLen)
                Proto.DOT -> dot(vpn, up, query, qLen)
                Proto.DOH -> doh(dohClient(vpn), up, query, qLen)
            }
        } catch (_: Exception) { null }
    }

    private fun udp(vpn: VpnService?, up: Upstream, query: ByteArray, qLen: Int): ByteArray? {
        val s = DatagramSocket()
        return try {
            vpn?.protect(s)
            s.soTimeout = 5000
            s.connect(InetSocketAddress(InetAddress.getByName(up.host), up.port))
            s.send(DatagramPacket(query, qLen, InetAddress.getByName(up.host), up.port))
            val buf = ByteArray(4096)
            val p = DatagramPacket(buf, buf.size)
            s.receive(p)
            DnsMessage.passthrough(query, qLen, buf, p.length)
        } finally { runCatching { s.close() } }
    }

    // A fresh TLS handshake per query makes DoT feel sluggish, so the tunnel
    // keeps one persistent socket per upstream and serializes queries over it.
    private val dotSock = java.util.concurrent.ConcurrentHashMap<String, Socket>()
    private val dotLock = Any()

    /** Drop every persistent DoT socket — call when the tunnel stops. */
    fun closePersistent() {
        synchronized(dotLock) {
            dotSock.values.forEach { runCatching { it.close() } }
            dotSock.clear()
        }
    }

    private fun dot(vpn: VpnService?, up: Upstream, query: ByteArray, qLen: Int): ByteArray? {
        val key = "${up.host}:${up.port}"
        synchronized(dotLock) {
            // Retry once on a fresh socket: a reused connection the server
            // already closed fails the first read after an idle gap.
            for (attempt in 0..1) {
                var s = dotSock[key]
                if (s == null || s.isClosed || !s.isConnected) {
                    s?.let { runCatching { it.close() } }
                    s = openDot(vpn, up) ?: return null
                    dotSock[key] = s
                }
                try {
                    val out = DataOutputStream(s!!.getOutputStream())
                    out.writeShort(qLen)
                    out.write(query, 0, qLen)
                    out.flush()
                    val inp = DataInputStream(s!!.getInputStream())
                    val rlen = inp.readUnsignedShort()
                    return if (rlen <= 0 || rlen > 65535) null else {
                        val buf = ByteArray(rlen)
                        inp.readFully(buf)
                        DnsMessage.passthrough(query, qLen, buf, rlen)
                    }
                } catch (_: Exception) {
                    runCatching { s?.close() }
                    dotSock.remove(key)
                }
            }
            return null
        }
    }

    private fun openDot(vpn: VpnService?, up: Upstream): Socket? {
        return try {
            val raw = Socket()
            vpn?.protect(raw)
            raw.connect(InetSocketAddress(InetAddress.getByName(up.host), up.port), 5000)
            // TLS peers against the hostname (dohHost), not the IP literal —
            // otherwise SNI is wrong and nothing verifies the certificate.
            val peer = up.dohHost.ifEmpty { up.host }
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            factory.createSocket(raw, peer, up.port, true).apply {
                soTimeout = 8000
                (this as javax.net.ssl.SSLSocket).apply {
                    sslParameters = sslParameters.apply {
                        endpointIdentificationAlgorithm = "HTTPS"
                    }
                    startHandshake()
                }
            }
        } catch (_: Exception) { null }
    }

    private fun doh(client: OkHttpClient, up: Upstream, query: ByteArray, qLen: Int): ByteArray? {
        val body = query.copyOf(qLen).toRequestBody("application/dns-message".toMediaType())
        val req = Request.Builder()
            .url(up.host)
            .header("Accept", "application/dns-message")
            .post(body)
            .build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return null
            val b = r.body?.bytes() ?: return null
            return DnsMessage.passthrough(query, qLen, b, b.size)
        }
    }
}
