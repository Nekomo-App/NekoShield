package net.nekomo.nekoshield.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import net.nekomo.nekoshield.R
import net.nekomo.nekoshield.data.Blocklists
import net.nekomo.nekoshield.data.Prefs
import net.nekomo.nekoshield.data.Providers
import net.nekomo.nekoshield.data.QueryLog
import net.nekomo.nekoshield.dns.DnsMessage
import net.nekomo.nekoshield.dns.DnsResolver
import net.nekomo.nekoshield.dns.FilterEngine
import net.nekomo.nekoshield.ui.MainActivity
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * DNS-only VPN: the tunnel routes just the virtual DNS IPs, so every packet
 * arriving here is a DNS query to answer (or drop). All other device traffic
 * never touches the app — that's what keeps this cheap on battery and keeps
 * streaming/playback fast.
 */
class ShieldVpnService : VpnService() {

    companion object {
        const val ACTION_START = "net.nekomo.nekoshield.START"
        const val ACTION_STOP = "net.nekomo.nekoshield.STOP"
        const val ACTION_STATE = "net.nekomo.nekoshield.STATE"
        const val EXTRA_ON = "on"

        const val NOTIF_ID = 42
        const val CHANNEL = "shield"

        // Virtual DNS IPs — the only things routed into the tunnel. The
        // system resolver sends its queries here; we answer them ourselves.
        private const val TUN_ADDR = "10.55.66.1"
        private const val DNS1 = "10.55.66.2"
        private const val DNS2 = "10.55.66.3"

        @Volatile var running = false
            private set
        @Volatile var upstreamLabel = ""
            private set
    }

    private var tun: ParcelFileDescriptor? = null
    private var thread: Thread? = null
    private val quit = AtomicBoolean(false)
    private val pool = Executors.newFixedThreadPool(8)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        android.util.Log.i("NekoShield", "onStartCommand action=${intent?.action}")
        when (intent?.action) {
            ACTION_STOP -> { shutdown(); return START_NOT_STICKY }
            else -> start()
        }
        return START_STICKY
    }

    private fun start() {
        if (running) { refresh(); return }
        val prefs = Prefs.get(this)

        val builder = Builder()
            .setSession("NekoShield")
            .setMtu(1500)
            .addAddress(TUN_ADDR, 32)
            .addDnsServer(DNS1)
            .addDnsServer(DNS2)
            .addRoute(DNS1, 32)
            .addRoute(DNS2, 32)

        // Per-app opt-outs — excluded apps keep their normal DNS path.
        for (pkg in prefs.excludedApps) {
            runCatching { builder.addDisallowedApplication(pkg) }
        }
        runCatching { builder.addDisallowedApplication(packageName) }

        tun = builder.establish()
        android.util.Log.i("NekoShield", "establish -> ${if (tun == null) "null (DENIED)" else "ok"}")
        if (tun == null) { stopSelf(); return }

        // Resolve which upstream answers queries.
        val upstream = upstreamFor(prefs)
        upstreamLabel = upstream.label.ifEmpty { upstream.host }
        val filter = buildFilter(prefs)

        foregroundNotify()
        quit.set(false)
        thread = Thread { loop(upstream, filter) }.apply {
            name = "ns-tun"
            isDaemon = true
            start()
        }
        running = true
        broadcast()
    }

    private fun upstreamFor(prefs: Prefs): DnsResolver.Upstream {
        if (prefs.useCustom) {
            Providers.parseCustom(prefs.customUpstream)?.let { return it }
        }
        return Providers.byId(prefs.providerId)?.upstream
            ?: Providers.PRESETS[1].upstream // Cloudflare fallback
    }

    private fun buildFilter(prefs: Prefs): FilterEngine {
        val blocked = HashSet<String>()
        if (prefs.blocklistsEnabled) blocked += Blocklists.load(this, prefs.enabledLists)
        blocked += prefs.userBlock
        return FilterEngine(blocked, prefs.userAllow)
    }

    private fun loop(upstream: DnsResolver.Upstream, filter: FilterEngine) {
        val prefs = Prefs.get(this)
        val fd = tun?.fileDescriptor ?: return
        val input = FileInputStream(fd)
        val output = FileOutputStream(fd)
        val packet = ByteArray(32767)

        while (!quit.get()) {
            val len = try { input.read(packet) } catch (_: Exception) { break }
            if (len <= 0) continue

            val udp = Packet.parseUdp(packet, len) ?: continue
            if (udp.dstPort != 53 && udp.srcPort != 53) continue // only DNS lives here

            if (udp.payloadLen < 12) continue
            val qname = DnsMessage.queryName(udp.payload, udp.payloadLen)
            val qcopy = udp.payload.copyOf(udp.payloadLen)

            // Resolve off the read thread so a slow upstream can't back up
            // the tunnel — netd retries anyway.
            pool.execute {
                val blocked = filter.isBlocked(qname)
                val answer: ByteArray? = when {
                    blocked -> DnsMessage.nxdomain(qcopy, qcopy.size)
                    upstream.proto == DnsResolver.Proto.UDP && upstream.host.isEmpty() ->
                        systemResolve(qcopy)
                    else -> DnsResolver.resolve(this@ShieldVpnService, upstream, qcopy, qcopy.size)
                }
                val wire = answer ?: DnsMessage.servfail(qcopy, qcopy.size)

                prefs.queryCount += 1
                if (blocked) prefs.blockedCount += 1
                if (prefs.logQueries && qname.isNotEmpty())
                    QueryLog.add(QueryLog.Entry(qname, blocked, upstreamLabel))

                synchronized(output) {
                    runCatching { output.write(Packet.buildUdpResponse(udp, wire)) }
                }
            }
        }
        pool.shutdownNow()
    }

    /** "System" upstream = the device's real DNS before the VPN (DHCP servers). */
    private fun systemResolve(query: ByteArray): ByteArray? {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val net = cm.activeNetwork ?: return null
        val lp = cm.getLinkProperties(net) ?: return null
        val dns = lp.dnsServers.firstOrNull { it is java.net.Inet4Address } ?: lp.dnsServers.firstOrNull()
            ?: return null
        val up = DnsResolver.Upstream(DnsResolver.Proto.UDP, dns.hostAddress ?: return null, 53, label = "System")
        return DnsResolver.resolve(this, up, query, query.size)
    }

    private fun refresh() {
        // Settings changed while running — restart the tunnel with them.
        shutdown(restart = true)
    }

    private fun shutdown(restart: Boolean = false) {
        quit.set(true)
        running = false
        runCatching { tun?.close() }
        tun = null
        thread = null
        if (!restart) Prefs.get(this).enabled = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        broadcast()
        if (restart) start() else stopSelf()
    }

    private fun broadcast() {
        sendBroadcast(Intent(ACTION_STATE).putExtra(EXTRA_ON, running).setPackage(packageName))
    }

    private fun foregroundNotify() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "NekoShield", NotificationManager.IMPORTANCE_LOW))
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, ShieldVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE)
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("NekoShield active")
            .setContentText("DNS protection on — tap to open")
            .setContentIntent(pi)
            .addAction(0, "Stop", stop)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34)
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        else startForeground(NOTIF_ID, n)
    }

    override fun onDestroy() {
        quit.set(true)
        running = false
        runCatching { tun?.close() }
        pool.shutdownNow()
        super.onDestroy()
    }
}
