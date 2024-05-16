package net.rec0de.android.watchwitch.shoes

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.rec0de.android.watchwitch.Logger
import net.rec0de.android.watchwitch.bitmage.ByteOrder
import net.rec0de.android.watchwitch.bitmage.fromBytes
import net.rec0de.android.watchwitch.bitmage.hex
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.OutputStream
import java.net.Socket

object ShoesProxyHandler {

    var connectionExpensive = false
    var connectionWiFi = true
    var connectionCellular = false

    suspend fun handleConnection(fromWatch: DataInputStream, toWatch: DataOutputStream) {
        var fromRemote: DataInputStream? = null
        var toRemote: OutputStream? = null

        try {
            val headerLen = withContext(Dispatchers.IO) {
                fromWatch.readUnsignedShort()
            }

            //Logger.logShoes("TLS rcv trying to read $headerLen header bytes", 10)

            val requestBytes = ByteArray(headerLen)
            withContext(Dispatchers.IO) {
                fromWatch.readFully(requestBytes, 0, requestBytes.size)
            }
            val request = ShoesRequest.parse(requestBytes)

            Logger.logShoes("SHOES raw bytes ${requestBytes.hex()}", 2)
            Logger.logShoes("SHOES request $request", 0)

            when(request) {
                is ShoesHostnameRequest, is ShoesIPv4Request, is ShoesIPv6Request -> {
                    val host = when(request) {
                        is ShoesHostnameRequest -> request.hostname
                        is ShoesIPv4Request -> request.ip.hostAddress
                        is ShoesIPv6Request -> request.ip.hostAddress
                        else -> throw Exception("unreachable")
                    }

                    NetworkStats.connect(host, request.bundleId)

                    var networkFlags = if(connectionExpensive) 0x80 else 0x00
                    networkFlags = networkFlags or if(connectionCellular) 0x40 else 0x00
                    networkFlags = networkFlags or if(connectionWiFi) 0x20 else 0x00

                    // I'm not 100% sure this is how it works but I think the request flags specify under which conditions the phone should reject the connection
                    // we'll also reject connection attempts explicitly blocked by the user based on hostname & requesting process
                    if(request.flags and networkFlags != 0 || !NetworkStats.shouldAllowConnection(host)) {
                        Logger.logShoes("Firewall: Host $host blocked connection", 0)
                        val reply = ShoesReply.reject()
                        Logger.logShoes("SHOES reply $reply", 1)
                        Logger.logShoes("SHOES raw reply ${reply.render().hex()}", 2)
                        withContext(Dispatchers.IO) {
                            toWatch.write(reply.render())
                            toWatch.flush()
                        }
                        return
                    }

                    val reply = ShoesReply.simple(connectionExpensive, connectionCellular, connectionWiFi)

                    Logger.logShoes("SHOES reply $reply", 1)
                    Logger.logShoes("SHOES raw reply ${reply.render().hex()}", 2)

                    withContext(Dispatchers.IO) {
                        toWatch.write(reply.render())
                        toWatch.flush()
                    }

                    val clientSocket = withContext(Dispatchers.IO) {
                        Socket(host, request.port)
                    }
                    toRemote = withContext(Dispatchers.IO) {
                        clientSocket.getOutputStream()
                    }
                    fromRemote = DataInputStream(withContext(Dispatchers.IO) {
                        clientSocket.getInputStream()
                    })

                    GlobalScope.launch { forwardForever(fromRemote, toWatch, host) }

                    while(NetworkStats.shouldAllowConnection(host)) {
                        // we expect a TLS record header here, which is 1 byte record type, 2 bytes TLS version, 2 bytes length
                        val recordHeader = ByteArray(5)
                        withContext(Dispatchers.IO) {
                            fromWatch.readFully(recordHeader)
                        }
                        val len = Int.fromBytes(recordHeader.sliceArray(3 until 5), ByteOrder.BIG)
                        val packet = ByteArray(5+len)
                        recordHeader.copyInto(packet)

                        withContext(Dispatchers.IO) {
                            fromWatch.readFully(packet, 5, len)
                            toRemote.write(packet)
                            toRemote.flush()
                        }

                        //Logger.logShoes("TLS to remote: ${packet.hex()}", 10)
                        NetworkStats.packetSent(host, len)
                    }

                    Logger.logShoes("Firewall: Host $host terminated existing connection", 0)

                    // if users block a connection that's already been established, we'll hit this on the next packet & close streams
                    // not exactly a graceful shutdown, but it should work?
                    withContext(Dispatchers.IO) {
                        fromWatch.close()
                        toWatch.close()
                        fromRemote?.close()
                        toRemote?.close()
                    }
                }
                else -> {
                    Logger.logShoes("Unsupported shoes request type: $request, denying", 0)
                    val reply = ShoesReply.reject()
                    withContext(Dispatchers.IO) {
                        toWatch.write(reply.render())
                        toWatch.flush()
                        toWatch.close()
                        fromWatch.close()
                    }
                }
            }
        } catch (e: Exception) {
            try {
                withContext(Dispatchers.IO) {
                    fromWatch.close()
                    toWatch.close()
                    fromRemote?.close()
                    toRemote?.close()
                }
            } catch (ex: IOException) {
                ex.printStackTrace()
            }
        }
    }

    private suspend fun forwardForever(fromRemoteStream: DataInputStream, toLocalStream: OutputStream, hostname: String) {
        try {
            while(true) {
                // we expect a TLS record header here, which is 1 byte record type, 2 bytes TLS version, 2 bytes length
                val recordHeader = ByteArray(5)
                val len = withContext(Dispatchers.IO) {
                    fromRemoteStream.readFully(recordHeader)
                    Int.fromBytes(recordHeader.sliceArray(3 until 5), ByteOrder.BIG)
                }

                val packet = ByteArray(5+len)
                recordHeader.copyInto(packet)

                withContext(Dispatchers.IO) {
                    fromRemoteStream.readFully(packet, 5, len)
                }

                //Logger.logShoes("TLS from remote: ${packet.hex()}", 10)
                NetworkStats.packetReceived(hostname, len)

                withContext(Dispatchers.IO) {
                    toLocalStream.write(packet)
                    toLocalStream.flush()
                }
            }
        } catch (e: Exception) {
            try {
                withContext(Dispatchers.IO) {
                    fromRemoteStream.close()
                    toLocalStream.close()
                }
            } catch (ex: IOException) {
                ex.printStackTrace()
            }
        }
    }
}