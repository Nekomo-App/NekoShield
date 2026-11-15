package net.nekomo.nekoshield.data

import java.util.concurrent.ConcurrentLinkedDeque

/** In-memory ring buffer of recent DNS decisions — nothing leaves the device. */
object QueryLog {

    const val MAX = 500

    data class Entry(val domain: String, val blocked: Boolean, val resolver: String, val at: Long = System.currentTimeMillis())

    private val buf = ConcurrentLinkedDeque<Entry>()

    fun add(e: Entry) {
        buf.addFirst(e)
        while (buf.size > MAX) buf.pollLast()
    }

    fun list(): List<Entry> = buf.toList()

    fun clear() = buf.clear()
}
