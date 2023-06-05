package net.rec0de.android.watchwitch

import java.io.DataOutputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.NetworkInterface


object RoutingManager {

    private lateinit var primaryInterface: NetworkInterface

    private val activeSpiForRemote = mutableMapOf<String,Pair<ByteArray, ByteArray>>()

    fun startup() {
        Logger.logCmd("Clearing ip xfrm states & policies", 0)
        exec(listOf("ip xfrm state deleteall", "ip xfrm policy deletall"))
    }

    fun getLocalIPv4Address(): Inet4Address {
        val interfaces = NetworkInterface.getNetworkInterfaces()

        val ips = interfaces.toList().flatMap { ni ->
            ni.inetAddresses.toList().filter { !it.isLoopbackAddress && !it.isLinkLocalAddress && it.isSiteLocalAddress && it is Inet4Address }
        }

        primaryInterface = NetworkInterface.getByInetAddress(ips.first())
        return ips.first() as Inet4Address
    }

    fun allocateIPv6AddressIfNotPresent(addr: Inet6Address) {
        val interfaces = NetworkInterface.getNetworkInterfaces()

        val ips = interfaces.toList().flatMap { ni ->
            ni.inetAddresses.toList().filter { !it.isLoopbackAddress && !it.isLinkLocalAddress && it is Inet6Address }
        }.map{ it as Inet6Address }

        if(!ips.contains(addr)) {
            Logger.logCmd("Setting up address: $addr on interface ${primaryInterface.name}", 0)
            exec("ip -6 addr add ${addr.hostName}/112 dev ${primaryInterface.name}")
        }
        else
            Logger.logCmd("Address already set up: $addr", 0)
    }

    fun registerAddresses() {
        val localC = LongTermKeys.getAddress(LongTermKeys.LOCAL_ADDRESS_CLASS_C)
        val localD = LongTermKeys.getAddress(LongTermKeys.LOCAL_ADDRESS_CLASS_D)
        if(localC != null)
            allocateIPv6AddressIfNotPresent(Inet6Address.getByName(localC) as Inet6Address)
        if(localD != null)
            allocateIPv6AddressIfNotPresent(Inet6Address.getByName(localD) as Inet6Address)
    }

    // using ip xfrm to create a manually keyed IPsec tunnel in the kernel
    // see https://www.sobyte.net/post/2022-10/ipsec-ip-xfrm/
    fun createTunnelClassC(remoteV4: String, initSpi: ByteArray, respSpi: ByteArray, initKey: ByteArray, respKey: ByteArray) {
        val hexKr = "0x${respKey.hex()}"
        val hexKi = "0x${initKey.hex()}"
        val hexSpiR = "0x${respSpi.hex()}"
        val hexSpiI = "0x${initSpi.hex()}"
        val localV4 = getLocalIPv4Address().hostAddress

        Logger.logCmd("Setting up class C tunnel for $hexSpiI/$hexSpiR", 0)
        Logger.logCmd("Key/SPI mappings: $localV4 $hexSpiI $hexKr", 1)
        Logger.logCmd("Key/SPI mappings: $remoteV4 $hexSpiR $hexKi", 1)

        val localV6 = LongTermKeys.getAddress(LongTermKeys.LOCAL_ADDRESS_CLASS_C)!!
        val remoteV6 = LongTermKeys.getAddress(LongTermKeys.REMOTE_ADDRESS_CLASS_C)!!

        val localV6D = LongTermKeys.getAddress(LongTermKeys.LOCAL_ADDRESS_CLASS_D)!!
        val remoteV6D = LongTermKeys.getAddress(LongTermKeys.REMOTE_ADDRESS_CLASS_D)!!

        // Remove existing tunnel for the same class
        if(activeSpiForRemote.containsKey(remoteV6)) {
            val existingSPIs = activeSpiForRemote[remoteV6]!!
            val a = existingSPIs.first.hex()
            val b = existingSPIs.second.hex()
            exec(listOf(
                "ip xfrm state deleteall reqid 0x$a",
                "ip xfrm state deleteall reqid 0x$b",
                "ip xfrm policy deletall reqid 0x$a",
                "ip xfrm policy deletall reqid 0x$b",
                "ip xfrm policy deleteall src $remoteV6/112 dst $localV6/112",
                "ip xfrm policy deleteall src $localV6/112 dst $remoteV6/112"
            ))
        }

        // do we want to remove a classD tunnel that is superseded by the classC tunnel? maybe we can leave it?
        /*if(activeSpiForRemote.containsKey(remoteV6D)) {
            val existingSPIs = activeSpiForRemote[remoteV6]!!
            val a = existingSPIs.first.hex()
            val b = existingSPIs.second.hex()
            exec(listOf(
                "ip xfrm state deleteall reqid 0x$a",
                "ip xfrm state deleteall reqid 0x$b",
                "ip xfrm policy deletall reqid 0x$a",
                "ip xfrm policy deletall reqid 0x$b",
                "ip xfrm policy deleteall src $remoteV6D/112 dst $localV6D/112",
                "ip xfrm policy deleteall src $localV6D/112 dst $remoteV6D/112"
            ))
        }*/

        val receiveState = "ip xfrm state add src $localV4 dst $remoteV4 proto esp spi $hexSpiI reqid $hexSpiI mode tunnel aead 'rfc4106(gcm(aes))' $hexKr 128 sel src \"::0/0\" dst \"::0/0\""
        val sendState =    "ip xfrm state add src $remoteV4 dst $localV4 proto esp spi $hexSpiR reqid $hexSpiR mode tunnel aead 'rfc4106(gcm(aes))' $hexKi 128 sel src \"::0/0\" dst \"::0/0\""
        val policyOut =    "ip -6 xfrm policy add src $localV6/112 dst $remoteV6/112 dir out tmpl src $localV4 dst $remoteV4 proto esp reqid $hexSpiI mode tunnel"
        val policyFwd =    "ip -6 xfrm policy add src $remoteV6/112 dst $localV6/112 dir fwd tmpl src $remoteV4 dst $localV4 proto esp reqid $hexSpiR mode tunnel"
        val policyIn =     "ip -6 xfrm policy add src $remoteV6/112 dst $localV6/112 dir in tmpl src $remoteV4 dst $localV4 proto esp reqid $hexSpiR mode tunnel"

        // Class C is a higher security context than class D, therefore a class C tunnel may be used to carry class D data
        val policyOutD =    "ip -6 xfrm policy add src $localV6D/112 dst $remoteV6D/112 dir out tmpl src $localV4 dst $remoteV4 proto esp reqid $hexSpiI mode tunnel"
        val policyFwdD =    "ip -6 xfrm policy add src $remoteV6D/112 dst $localV6D/112 dir fwd tmpl src $remoteV4 dst $localV4 proto esp reqid $hexSpiR mode tunnel"
        val policyInD =     "ip -6 xfrm policy add src $remoteV6D/112 dst $localV6D/112 dir in tmpl src $remoteV4 dst $localV4 proto esp reqid $hexSpiR mode tunnel"

        exec(listOf(receiveState, sendState, policyOut, policyFwd, policyIn, policyOutD, policyFwdD, policyInD))
        addRoutes()
        activeSpiForRemote[remoteV6] = Pair(initSpi, respSpi)
    }

    fun createTunnelClassD(remoteV4: String, initSpi: ByteArray, respSpi: ByteArray, initKey: ByteArray, respKey: ByteArray) {
        val hexKr = "0x${respKey.hex()}"
        val hexKi = "0x${initKey.hex()}"
        val hexSpiR = "0x${respSpi.hex()}"
        val hexSpiI = "0x${initSpi.hex()}"
        val localV4 = getLocalIPv4Address().hostAddress

        val localV6 = LongTermKeys.getAddress(LongTermKeys.LOCAL_ADDRESS_CLASS_D)!!
        val remoteV6 = LongTermKeys.getAddress(LongTermKeys.REMOTE_ADDRESS_CLASS_D)!!

        Logger.logCmd("Setting up class D tunnel for $hexSpiI/$hexSpiR", 0)
        Logger.logCmd("Key/SPI mappings: $localV4 $hexSpiI $hexKr", 1)
        Logger.logCmd("Key/SPI mappings: $remoteV4 $hexSpiR $hexKi", 1)

        // Remove existing tunnel for the same class
        if(activeSpiForRemote.containsKey(remoteV6)) {
            val existingSPIs = activeSpiForRemote[remoteV6]!!
            val a = existingSPIs.first.hex()
            val b = existingSPIs.second.hex()
            exec(listOf(
                "ip xfrm state deleteall reqid 0x$a",
                "ip xfrm state deleteall reqid 0x$b",
                "ip xfrm policy deletall reqid 0x$a",
                "ip xfrm policy deletall reqid 0x$b",
                "ip xfrm policy deleteall src $remoteV6/112 dst $localV6/112",
                "ip xfrm policy deleteall src $localV6/112 dst $remoteV6/112"
            ))
        }

        val receiveState = "ip xfrm state add src $localV4 dst $remoteV4 proto esp spi $hexSpiI reqid $hexSpiI mode tunnel aead 'rfc4106(gcm(aes))' $hexKr 128 sel src \"::0/0\" dst \"::0/0\""
        val sendState =    "ip xfrm state add src $remoteV4 dst $localV4 proto esp spi $hexSpiR reqid $hexSpiR mode tunnel aead 'rfc4106(gcm(aes))' $hexKi 128 sel src \"::0/0\" dst \"::0/0\""
        val policyOut =    "ip -6 xfrm policy add src $localV6/112 dst $remoteV6/112 dir out tmpl src $localV4 dst $remoteV4 proto esp reqid $hexSpiI mode tunnel"
        val policyFwd =    "ip -6 xfrm policy add src $remoteV6/112 dst $localV6/112 dir fwd tmpl src $remoteV4 dst $localV4 proto esp reqid $hexSpiR mode tunnel"
        val policyIn =     "ip -6 xfrm policy add src $remoteV6/112 dst $localV6/112 dir in tmpl src $remoteV4 dst $localV4 proto esp reqid $hexSpiR mode tunnel"

        exec(listOf(receiveState, sendState, policyOut, policyFwd, policyIn))
        addRoutes()
        activeSpiForRemote[remoteV6] = Pair(initSpi, respSpi)
    }

    fun clearRoutes() {
        val remoteC = LongTermKeys.getAddress(LongTermKeys.LOCAL_ADDRESS_CLASS_C)
        val remoteD = LongTermKeys.getAddress(LongTermKeys.LOCAL_ADDRESS_CLASS_D)

        if(remoteC != null)
            exec("ip -6 route del $remoteC/112")
        if(remoteD != null)
            exec("ip -6 route del $remoteD/112")
    }

    fun addRoutes() {
        val localC = LongTermKeys.getAddress(LongTermKeys.LOCAL_ADDRESS_CLASS_C)
        val remoteC = LongTermKeys.getAddress(LongTermKeys.REMOTE_ADDRESS_CLASS_C)
        val localD = LongTermKeys.getAddress(LongTermKeys.LOCAL_ADDRESS_CLASS_D)
        val remoteD = LongTermKeys.getAddress(LongTermKeys.REMOTE_ADDRESS_CLASS_D)

        /*
        IMPORTANT:
        Android has a pretty complicated internal routing setup with several tables
        We have to add our routes to the 'local' table explicitly to avoid default routes taking precedence
         */

        if(remoteC != null && localC != null)
            exec("ip -6 route add table local $remoteC/112 dev ${primaryInterface.name} src $localC")
        if(remoteD != null && localD != null)
            exec("ip -6 route add table local $remoteD/112 dev ${primaryInterface.name} src $localD")
    }

    private fun exec(cmd: List<String>) {
        Logger.logCmd(cmd.joinToString("\n"), 2)
        val process: Process
        try {
            process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            for (s in cmd) {
                os.writeBytes("$s\n")
                os.flush()
            }
            os.writeBytes("exit\n")
            os.flush()
            process.waitFor()
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    private fun exec(cmd: String) = exec(listOf(cmd))
}

class AddressAllocator : Thread() {
    override fun run() {
        RoutingManager.registerAddresses()
    }
}

class TunnelBuilder(
    private val remoteV4: String,
    private val initSpi: ByteArray,
    private val respSpi: ByteArray,
    private val initKey: ByteArray,
    private val respKey: ByteArray,
    private val isClassC: Boolean
    ) : Thread() {
    override fun run() {
        if(isClassC)
            RoutingManager.createTunnelClassC(remoteV4, initSpi, respSpi, initKey, respKey)
        else
            RoutingManager.createTunnelClassD(remoteV4, initSpi, respSpi, initKey, respKey)
    }
}