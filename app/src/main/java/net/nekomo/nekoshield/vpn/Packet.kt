package net.nekomo.nekoshield.vpn

import java.net.InetAddress
import java.nio.ByteBuffer

/**
 * IPv4 + UDP packet handling for the TUN interface. The tunnel only routes
 * the virtual DNS addresses, so traffic here is almost always a single DNS
 * query to answer — build a matching response packet and write it back.
 */
object Packet {

    const val PROTO_UDP = 17
    const val PROTO_TCP = 6

    data class Udp(
        val srcIp: ByteArray, val dstIp: ByteArray,
        val srcPort: Int, val dstPort: Int,
        val payload: ByteArray, val payloadLen: Int,
    )

    /** Parses a raw IPv4 packet; returns a Udp when it's one, else null. */
    fun parseUdp(packet: ByteArray, len: Int): Udp? {
        if (len < 28) return null
        if ((packet[0].toInt() shr 4) != 4) return null
        val ihl = (packet[0].toInt() and 0xF) * 4
        if (ihl < 20 || len < ihl + 8) return null
        if ((packet[9].toInt() and 0xFF) != PROTO_UDP) return null
        val srcIp = packet.copyOfRange(12, 16)
        val dstIp = packet.copyOfRange(16, 20)
        val srcPort = ((packet[ihl].toInt() and 0xFF) shl 8) or (packet[ihl + 1].toInt() and 0xFF)
        val dstPort = ((packet[ihl + 2].toInt() and 0xFF) shl 8) or (packet[ihl + 3].toInt() and 0xFF)
        val pOff = ihl + 8
        return Udp(srcIp, dstIp, srcPort, dstPort, packet, len - pOff).also {
            // Slide the payload down to offset 0 for convenience.
            System.arraycopy(packet, pOff, it.payload, 0, it.payloadLen)
        }
    }

    fun isTcp(packet: ByteArray, len: Int): Boolean =
        len >= 20 && (packet[9].toInt() and 0xFF) == PROTO_TCP

    /** Builds an IPv4+UDP response packet (swapped src/dst) wrapping `payload`. */
    fun buildUdpResponse(req: Udp, payload: ByteArray): ByteArray {
        val ipLen = 20 + 8 + payload.size
        val out = ByteArray(ipLen)
        val bb = ByteBuffer.wrap(out)
        // IPv4 header
        bb.put(0x45.toByte())            // version 4, IHL 5
        bb.put(0)                        // tos
        bb.putShort(ipLen.toShort())     // total length
        bb.putShort(0)                   // id
        bb.putShort(0x4000.toShort())    // DF
        bb.put(64.toByte())              // ttl
        bb.put(PROTO_UDP.toByte())
        bb.putShort(0)                   // checksum (fill later)
        bb.put(req.dstIp)                // src = original dst
        bb.put(req.srcIp)                // dst = original src
        // UDP header
        bb.putShort(req.dstPort.toShort())
        bb.putShort(req.srcPort.toShort())
        bb.putShort((8 + payload.size).toShort())
        bb.putShort(0)                   // udp checksum optional on IPv4
        bb.put(payload)
        // IP checksum
        val c = ipChecksum(out, 0, 20)
        out[10] = (c shr 8).toByte(); out[11] = c.toByte()
        return out
    }

    private fun ipChecksum(b: ByteArray, off: Int, len: Int): Int {
        var sum = 0L
        var i = off
        while (i < off + len - 1) {
            sum += ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (i < off + len) sum += (b[i].toInt() and 0xFF) shl 8
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return (sum.inv() and 0xFFFF).toInt()
    }

    fun ipToString(b: ByteArray): String = try {
        InetAddress.getByAddress(b).hostAddress
    } catch (_: Exception) { "?" }
}
