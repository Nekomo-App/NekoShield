package net.nekomo.nekoshield.dns

import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.nio.ByteBuffer

/**
 * Minimal DNS message codec — enough to parse a query, forward it upstream
 * untouched, and build a synthetic NXDOMAIN response for filtered domains.
 * Answers are parsed only for logging (not rewritten).
 */
object DnsMessage {

    data class Question(val name: String, val type: Int, val qclass: Int)

    data class Parsed(
        val id: Int,
        val flags: Int,
        val questions: List<Question>,
        val rawQuestionEnd: Int, // offset where the question section ends
    ) {
        val isResponse: Boolean get() = flags and 0x8000 != 0
        val opcode: Int get() = (flags shr 11) and 0xF
        val firstName: String get() = questions.firstOrNull()?.name ?: ""
    }

    fun parseQuery(buf: ByteBuffer): Parsed? {
        if (buf.remaining() < 12) return null
        val start = buf.position()
        val id = buf.short.toInt() and 0xFFFF
        val flags = buf.short.toInt() and 0xFFFF
        val qd = buf.short.toInt() and 0xFFFF
        buf.short; buf.short; buf.short // an/ns/ar counts
        val qs = ArrayList<Question>(qd)
        for (i in 0 until qd) {
            val name = readName(buf) ?: return null
            if (buf.remaining() < 4) return null
            qs += Question(name, buf.short.toInt() and 0xFFFF, buf.short.toInt() and 0xFFFF)
        }
        return Parsed(id, flags, qs, buf.position() - start)
    }

    private fun readName(buf: ByteBuffer): String? {
        val sb = StringBuilder()
        var jumped = false
        var jumps = 0
        var resume = -1
        while (true) {
            if (!buf.hasRemaining()) return null
            val len = buf.get().toInt() and 0xFF
            if (len == 0) break
            if (len and 0xC0 == 0xC0) {
                if (!buf.hasRemaining()) return null
                val off = ((len and 0x3F) shl 8) or (buf.get().toInt() and 0xFF)
                if (++jumps > 16 || off >= buf.limit()) return null
                if (!jumped) { jumped = true; resume = buf.position() }
                buf.position(off)
                continue
            }
            if (len > 63 || buf.remaining() < len) return null
            if (sb.isNotEmpty()) sb.append('.')
            val label = ByteArray(len)
            buf.get(label)
            sb.append(String(label, Charsets.US_ASCII))
        }
        if (jumped) buf.position(resume)
        return sb.toString().lowercase()
    }

    /** Extracts the question name without touching the position on failure. */
    fun queryName(raw: ByteArray, len: Int): String {
        return try {
            val bb = ByteBuffer.wrap(raw, 0, len)
            parseQuery(bb)?.firstName ?: ""
        } catch (_: Exception) { "" }
    }

    /** Builds a response that forwards an upstream wire answer verbatim. */
    fun passthrough(query: ByteArray, qLen: Int, upstream: ByteArray, uLen: Int): ByteArray {
        val out = ByteArray(uLen)
        System.arraycopy(upstream, 0, out, 0, uLen)
        // Copy the query's ID so the resolver matches it.
        out[0] = query[0]; out[1] = query[1]
        return out
    }

    /** NXDOMAIN response for a filtered domain. */
    fun nxdomain(query: ByteArray, qLen: Int): ByteArray {
        val qd = ((query[4].toInt() and 0xFF) shl 8) or (query[5].toInt() and 0xFF)
        // header(12) + the original question section
        val qEnd = questionSectionEnd(query, qLen)
        val out = ByteArrayOutputStream(12 + (qEnd - 12))
        out.write(query, 0, 2)                              // id
        out.write(byteArrayOf(0x81.toByte(), 0x83.toByte())) // QR + RA + RCODE=3
        out.write(query, 4, 2)                              // qdcount (same)
        out.write(byteArrayOf(0, 0, 0, 0, 0, 0))            // an/ns/ar = 0
        if (qEnd > 12) out.write(query, 12, qEnd - 12)      // echo question
        val r = out.toByteArray()
        // fix qdcount if it was insane
        if (qd <= 0 || qd > 64) { r[4] = 0; r[5] = 1 }
        return r
    }

    /** SERVFAIL — used when upstream resolution itself fails. */
    fun servfail(query: ByteArray, qLen: Int): ByteArray {
        val qEnd = questionSectionEnd(query, qLen)
        val out = ByteArrayOutputStream(12 + (qEnd - 12))
        out.write(query, 0, 2)
        out.write(byteArrayOf(0x81.toByte(), 0x82.toByte())) // QR + RA + RCODE=2
        out.write(query, 4, 2)
        out.write(byteArrayOf(0, 0, 0, 0, 0, 0))
        if (qEnd > 12) out.write(query, 12, qEnd - 12)
        return out.toByteArray()
    }

    private fun questionSectionEnd(query: ByteArray, qLen: Int): Int {
        // Walk the question section without a full parse.
        var pos = 12
        val qd = ((query[4].toInt() and 0xFF) shl 8) or (query[5].toInt() and 0xFF)
        var n = qd
        while (n-- > 0 && pos < qLen) {
            while (pos < qLen) {
                val l = query[pos].toInt() and 0xFF
                if (l == 0) { pos++; break }
                if (l and 0xC0 == 0xC0) { pos += 2; break }
                pos += l + 1
            }
            pos += 4 // type + class
        }
        return pos.coerceIn(12, qLen)
    }

    /** Builds a standard recursive A-query for `domain`. */
    fun buildQuery(domain: String, type: Int = 1): ByteArray {
        val out = ByteArrayOutputStream(64)
        val id = java.security.SecureRandom().nextInt(0x10000)
        out.write(id shr 8); out.write(id)
        out.write(0x01); out.write(0x00)     // RD
        out.write(0); out.write(1)           // qd=1
        out.write(0); out.write(0); out.write(0); out.write(0); out.write(0); out.write(0)
        for (label in domain.trimEnd('.').split('.')) {
            val b = label.toByteArray(Charsets.US_ASCII)
            out.write(b.size); out.write(b)
        }
        out.write(0)
        out.write(type shr 8); out.write(type) // qtype
        out.write(0); out.write(1)             // IN
        return out.toByteArray()
    }

    fun firstAddress(raw: ByteArray): String? = answers(raw, raw.size).firstOrNull()

    /** Human-readable summary of a response for diagnostics. */
    fun describe(raw: ByteArray): String {
        val sb = StringBuilder()
        try {
            val buf = ByteBuffer.wrap(raw)
            val id = buf.short.toInt() and 0xFFFF
            val flags = buf.short.toInt() and 0xFFFF
            val qd = buf.short.toInt() and 0xFFFF
            val an = buf.short.toInt() and 0xFFFF
            val rcode = flags and 0xF
            sb.append("id=$id rcode=$rcode qd=$qd an=$an\n")
            val addr = answers(raw, raw.size)
            if (addr.isEmpty()) sb.append("(no address records)")
            else sb.append(addr.joinToString("\n"))
        } catch (e: Exception) {
            sb.append("unparseable: ${e.message}")
        }
        return sb.toString()
    }

    /** Extracts A/AAAA results from a response — for diagnostics display. */
    fun answers(raw: ByteArray, len: Int): List<String> {
        val out = ArrayList<String>()
        try {
            val buf = ByteBuffer.wrap(raw, 0, len)
            buf.position(6)
            val an = buf.short.toInt() and 0xFFFF
            buf.position(questionSectionEnd(raw, len))
            repeat(an) {
                readName(buf) ?: return out
                val type = buf.short.toInt() and 0xFFFF
                buf.short // class
                buf.int   // ttl
                val rdlen = buf.short.toInt() and 0xFFFF
                if (buf.remaining() < rdlen) return out
                val off = buf.position()
                if (type == 1 && rdlen == 4) {
                    val a = ByteArray(4); buf.get(a)
                    InetAddress.getByAddress(a).hostAddress?.let { out += it }
                } else if (type == 28 && rdlen == 16) {
                    val a = ByteArray(16); buf.get(a)
                    InetAddress.getByAddress(a).hostAddress?.let { out += it }
                } else {
                    buf.position(off + rdlen)
                }
            }
        } catch (_: Exception) { }
        return out
    }
}
