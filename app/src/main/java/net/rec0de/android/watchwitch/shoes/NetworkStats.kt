package net.rec0de.android.watchwitch.shoes

object NetworkStats {
    private val stats = mutableMapOf<String, StatsEntry>()

    fun connect(host: String, bundle: String?) {
        if(stats.containsKey(host)) {
            val entry = stats[host]!!
            entry.connects += 1
            entry.packets += 1
            if(bundle != null)
                entry.bundleIDs.add(bundle)
        }
        else if(bundle != null)
            stats[host] = StatsEntry(1, 0, 0, 1, mutableSetOf(bundle))
        else
            stats[host] = StatsEntry(1, 0, 0, 1, mutableSetOf())
    }

    fun packetReceived(host: String, bytes: Int) {
        val entry = stats[host]!!
        entry.packets += 1
        entry.bytesReceived += bytes
    }

    fun packetSent(host: String, bytes: Int) {
        val entry = stats[host]!!
        entry.packets += 1
        entry.bytesSent += bytes
    }

    fun print(): String {
        return stats.map { entry ->
            val host = entry.key
            val stats = entry.value

            "$host: ${stats.packets} pkts, ${stats.bytesSent}b snd ${stats.bytesReceived}b rcv, ${stats.bundleIDs.joinToString("/")}"
        }.joinToString("\n")
    }

    fun stats(): Map<String, StatsEntry> = stats
}

data class StatsEntry(var packets: Int = 0, var bytesSent: Int = 0, var bytesReceived: Int = 0, var connects: Int = 0, val bundleIDs: MutableSet<String> = mutableSetOf())