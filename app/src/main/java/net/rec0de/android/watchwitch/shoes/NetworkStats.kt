package net.rec0de.android.watchwitch.shoes

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.rec0de.android.watchwitch.Logger

object NetworkStats {
    private val stats = mutableMapOf("default" to StatsEntry(0, 0, 0, 0, mutableSetOf(), true))

    // this is a little cursed but we'd like to include the default firewall behaviour in the JSON we send to the UI
    // so we'll put it in as a synthetic entry and use custom accessors for prettier access
    var allowByDefault: Boolean
        get() = stats["default"]!!.allow!!
        set(value) {
            stats["default"]!!.allow = value
        }

    fun shouldAllowConnection(host: String) = stats[host]?.allow ?: allowByDefault

    fun setRule(host: String, allow: Boolean) {
        if(!stats.containsKey(host))
            return
        stats[host]!!.allow = allow
        Logger.logShoes("Firewall: Host $host allowed? $allow", 0)
    }

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

    fun json() = Json.encodeToString(stats)
}

@Serializable
data class StatsEntry(var packets: Int = 0, var bytesSent: Int = 0, var bytesReceived: Int = 0, var connects: Int = 0, val bundleIDs: MutableSet<String> = mutableSetOf(), var allow: Boolean? = null)