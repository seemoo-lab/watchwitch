package net.rec0de.android.watchwitch.ike

import net.rec0de.android.watchwitch.Logger
import net.rec0de.android.watchwitch.LongTermStorage
import net.rec0de.android.watchwitch.Utils
import net.rec0de.android.watchwitch.bitmage.ByteOrder
import net.rec0de.android.watchwitch.bitmage.fromBytes
import net.rec0de.android.watchwitch.bitmage.fromHex
import net.rec0de.android.watchwitch.bitmage.fromIndex
import net.rec0de.android.watchwitch.bitmage.hex
import net.rec0de.android.watchwitch.nwsc.NWSCManager
import org.bouncycastle.crypto.AsymmetricCipherKeyPair
import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.agreement.X448Agreement
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator
import org.bouncycastle.crypto.generators.X448KeyPairGenerator
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.crypto.params.X448KeyGenerationParameters
import org.bouncycastle.crypto.params.X448PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.SecureRandom


class IKEv2Session(
    private val transport: IKETransport,
    private val initiatorSPI: ByteArray
) {
    private val random = SecureRandom()

    private val responderSPI = randomBytes(8)
    private var nextMsgId = 0
    private var dataProtectionClass = 0 // we'll set this to the correct value before we ever need it

    private val cryptoValues = mutableMapOf<String, ByteArray>()
    private val x25519LocalKeys: AsymmetricCipherKeyPair
    private val x448LocalKeys: AsymmetricCipherKeyPair
    private var sessionKeysReady = false

    private var isX448Session = false

    init {
        val keygen25519 = X25519KeyPairGenerator()
        keygen25519.init(X25519KeyGenerationParameters(random))
        x25519LocalKeys = keygen25519.generateKeyPair()

        val keygen448 = X448KeyPairGenerator()
        keygen448.init(X448KeyGenerationParameters(random))
        x448LocalKeys = keygen448.generateKeyPair()

        cryptoValues["nr"] = randomBytes(32) // our nonce
        cryptoValues["espSPIr"] = randomBytes(4) // eventually, our ESP SPI

        Logger.logIKE(
            "init session: ${
                initiatorSPI.sliceArray(0 until 4).hex()
            }-${responderSPI.sliceArray(0 until 4).hex()}", 2
        )
    }

    private fun randomBytes(size: Int): ByteArray {
        val bytes = ByteArray(size)
        random.nextBytes(bytes)
        return bytes
    }

    fun ingestPacket(data: ByteArray) {
        val initiatorSPI = data.sliceArray(0 until 8)
        val responderSPI = data.sliceArray(8 until 16)
        val nextHeader = data[16]
        //val version = data[17]
        val exchange = data[18]
        //val flags = data[19]
        //val msgID = data.sliceArray(20 until 24)
        //val length = data.sliceArray(24 until 28)

        Logger.logIKE(
            "rcv: ${
                initiatorSPI.sliceArray(0 until 4).hex()
            }-${
                responderSPI.sliceArray(0 until 4).hex()
            } ${IKEDefs.exchangeType(exchange)} ${IKEDefs.nextPayload(nextHeader.toInt())}", 0
        )

        val payloads = parsePayloads(nextHeader.toInt(), data.fromIndex(28), data)
        payloads.forEach {
            processPayload(it)
            logPayload(it)
        }

        when(IKEDefs.exchangeType(exchange)) {
            "SA_INIT" -> {
                val reply = replyToSAInit(isX448Session)
                transport.send(reply)
                Logger.logIKE("snd: SA_INIT", 0)
                Logger.logIKE(reply.hex(), 3)

                // save for authentication purposes
                cryptoValues["peerSAinit"] = data
                cryptoValues["ownSAinit"] = reply

                // reset NWSC session
                NWSCManager.reset()
            }
            "AUTH" -> {
                if(!sessionKeysReady)
                    return
                val reply = replyToAuth()
                transport.send(reply)
                Logger.logIKE("snd: AUTH", 0)
                Logger.logIKE(reply.hex(), 3)
            }
            "INFO" -> {
                if(!sessionKeysReady)
                    return
                val reply = replyToHeartbeat()
                transport.send(reply)
                Logger.logIKE("snd: HEARTBEAT", 0)
                Logger.logIKE(reply.hex(), 3)
            }
        }
    }

    private fun parsePayloads(firstPayloadType: Int, data: ByteArray, rawPacket: ByteArray): List<Pair<Int, ByteArray>> {
        var nextPayload = firstPayloadType
        val containedPayloads = mutableListOf<Pair<Int, ByteArray>>()
        var remainingData = data
        var offset = 0

        while (nextPayload != 0 && remainingData.size > 3) {
            val payloadLength = UInt.fromBytes(remainingData.sliceArray(2 until 4), ByteOrder.BIG)

            // encrypted payload
            if(nextPayload == 46 && sessionKeysReady) {
                val iv = remainingData.sliceArray(4 until 12)
                val ciphertext = remainingData.sliceArray(12 until payloadLength.toInt()) // last 16 bytes are tag

                // AEAD encryption schemes have to authenticate everything up to the IV in the encrypted payload
                // see https://datatracker.ietf.org/doc/html/rfc5282#section-5.1
                // this is a bit inconvenient
                val aad = rawPacket.sliceArray(0 until offset+28+4) // from start of packet: 28 bytes main IKE header, possible preceding payloads, 4 bytes encrypted payload header
                val nonce = cryptoValues["skiSalt"]!! + iv
                val plaintext =
                    Utils.chachaPolyDecrypt(key = cryptoValues["ski"]!!, nonce, aad, ciphertext)

                return containedPayloads + parsePayloads(remainingData[0].toInt(), plaintext, plaintext) // we have to pass something as rawPacket but we won't use it
            }
            else if(nextPayload == 46) {
                Logger.logIKE("Ignoring encrypted payload before keys ready", 1)
                return containedPayloads
            }

            containedPayloads.add(Pair(nextPayload, remainingData.sliceArray(4 until payloadLength.toInt())))
            nextPayload = remainingData[0].toInt()
            offset += payloadLength.toInt()
            remainingData = remainingData.fromIndex(payloadLength.toInt())
        }

        return containedPayloads
    }

    private fun processPayload(payload: Pair<Int, ByteArray>) {
        val typeStr = IKEDefs.nextPayload(payload.first)
        val data = payload.second

        when(typeStr) {
            "NONCE" -> {
                cryptoValues["ni"] = data // initiator nonce
                cryptoValuesUpdated() // we might be able to compute something with this
            }
            "KEx" -> {
                val dhGroup = UInt.fromBytes(data.sliceArray(0 until 2), ByteOrder.BIG)

                // we're expecting curve25519 DH exchange and support nothing else
                when(dhGroup) {
                    31u -> {
                        val dhData = data.fromIndex(4)
                        val agreement = X25519Agreement()
                        agreement.init(x25519LocalKeys.private)
                        val sharedSecret = ByteArray(agreement.agreementSize)
                        agreement.calculateAgreement(X25519PublicKeyParameters(dhData), sharedSecret, 0)
                        //log("SHARED SECRET: ${sharedSecret.hex()}")
                        cryptoValues["dhShared"] = sharedSecret
                        cryptoValuesUpdated()
                    }
                    32u -> {
                        val dhData = data.fromIndex(4)
                        val agreement = X448Agreement()
                        agreement.init(x448LocalKeys.private)
                        val sharedSecret = ByteArray(agreement.agreementSize)
                        agreement.calculateAgreement(X448PublicKeyParameters(dhData), sharedSecret, 0)
                        //log("SHARED SECRET: ${sharedSecret.hex()}")
                        cryptoValues["dhShared"] = sharedSecret
                        cryptoValuesUpdated()
                        isX448Session = true
                    }
                    else -> {
                        Logger.logIKE("ERROR: Unsupported KEx DH group $dhGroup", 0)
                        throw Exception("Unsupported DH group used in key exchange: $dhGroup, expecting 31 (Curve25519) or 32 (Curve448)")
                    }
                }


            }
            "AUTH" -> {
                val asn1Length = data[4].toInt()
                val sig = data.fromIndex(5+asn1Length)
                verifyAuthSignature(sig)
            }
            "SA" -> {
                var remainingData = data
                while(remainingData.isNotEmpty()) {
                    val proposalLen = UInt.fromBytes(remainingData.sliceArray(2 until 4), ByteOrder.BIG).toInt()
                    val proposal = remainingData.sliceArray(0 until proposalLen)
                    val protocol =proposal[5].toInt()
                    if(protocol == 0x03) { // ESP
                        val spiSize = proposal[6].toInt()
                        val spi = proposal.sliceArray(8 until 8+spiSize)
                        cryptoValues["espSPIi"] = spi
                        createTunnelIfReady()
                    }
                    remainingData = remainingData.fromIndex(proposalLen)
                }
            }
            "IDinit" -> {
                cryptoValues["IDi"] = data
                val idStr = data.toString(Charsets.UTF_8)
                dataProtectionClass = when(idStr.last()) {
                    'A' -> 1
                    'C' -> 3
                    'D' -> 4
                    else -> throw Exception("Unknown data protection class: $idStr")
                }
            }
            "IDresp" -> {
                cryptoValues["IDr"] = data
            }
        }
    }

    private fun logPayload(payload: Pair<Int, ByteArray>) {
        val typeStr = IKEDefs.nextPayload(payload.first)
        val data = payload.second

        when(typeStr) {
            "NOTIFY" -> {
                val notifyType = UInt.fromBytes(data.sliceArray(2 until 4), ByteOrder.BIG)
                if(notifyType < 16384u)
                    Logger.logIKE("-> NOTIFY - error: ${IKEDefs.errorTypes[notifyType.toInt()]}", 1)
                else if (notifyType > 40960u) {
                    if(IKEDefs.privateNotifyTypes.containsKey(notifyType.toInt()))
                        Logger.logIKE(
                            "-> NOTIFY - ${IKEDefs.privateNotifyTypes[notifyType.toInt()]}",
                            1
                        )
                    else
                        Logger.logIKE("-> NOTIFY - unknown private type $notifyType", 1)
                }
                else
                    Logger.logIKE(
                        "-> NOTIFY - ${IKEDefs.notifyTypes[notifyType.toInt() - 16384]} ${data.hex()}",
                        1
                    )
            }
            "KEx" -> {
                val dhGroup = data.sliceArray(0 until 2)
                val dhData = data.fromIndex(4)
                Logger.logIKE("-> KEx - group ${dhGroup.hex()}: ${dhData.hex()}", 3)
            }
            "AUTH" -> {
                val asn1Length = data[4].toInt()
                val asn1 = data.sliceArray(5 until 5+asn1Length)
                val sig = data.fromIndex(5+asn1Length)
                Logger.logIKE("AUTH -> asn1: ${asn1.hex()} sig: ${sig.hex()}", 3)
            }
            "SA" -> {
                Logger.logIKE("-> SA", 1)
                var remainingData = data
                while(remainingData.isNotEmpty()) {
                    val proposalLen = Int.fromBytes(remainingData.sliceArray(2 until 4), ByteOrder.BIG)
                    val proposal = remainingData.sliceArray(0 until proposalLen)
                    val propNum = proposal[4].toInt()
                    val protocol = listOf("IKE", "AH", "ESP")[proposal[5].toInt() - 1]
                    val spiSize = proposal[6].toInt()
                    val numTransforms = proposal[7].toInt()
                    //val spi = proposal.sliceArray(8 until 8+spiSize)
                    remainingData = remainingData.fromIndex(proposalLen)
                    Logger.logIKE(
                        "--> #$propNum: $protocol SPI size $spiSize, $numTransforms tfms",
                        2
                    )
                    Logger.logIKE(proposal.hex(), 3)
                }
            }
            "NONCE" -> Logger.logIKE("-> NONCE ${data.hex()}", 2)
            "IDinit", "IDresp" -> Logger.logIKE(
                "-> $typeStr - ${
                    data.fromIndex(4).toString(Charsets.UTF_8)
                }", 2
            )
            else -> Logger.logIKE("-> $typeStr (no logging implemented)", 1)
        }
    }

    private fun replyToSAInit(useX448: Boolean): ByteArray {
        val sa = IKESAPayload(useX448)

        val kex = if(useX448)
            KExPayloadX448(x448LocalKeys.public as X448PublicKeyParameters)
        else
            KExPayloadX25519(x25519LocalKeys.public as X25519PublicKeyParameters)

        val nonce = NoncePayload(cryptoValues["nr"]!!)

        // sending trash NAT detection payloads, they don't seem to do anything here either way
        val natSource = NATDetectionPayload(initiatorSPI, responderSPI, 0, 0, true)
        val natDestination = NATDetectionPayload(initiatorSPI, responderSPI, 1, 1, false)
        val fragSupport = NotifyPayload(16430)
        val sigHashAlgos = NotifyPayload(16431, byteArrayOf(0x00, 0x02, 0x00, 0x05))

        nextMsgId = 0
        val msg = IKEMessage(exchangeType = 34, nextMsgId, initiatorSPI, responderSPI)
        msg.addPayloads(listOf(sa, kex, nonce, natSource, natDestination, fragSupport, sigHashAlgos))
        nextMsgId += 1

        return msg.assemble()
    }

    private fun replyToAuth(): ByteArray {
        val initialContact = NotifyPayload(16384)
        val idResp = IdPayload(cryptoValues["IDr"]!!)
        val auth = AuthPayload(generateAuthSignature())
        val padding = NotifyPayload(16394)
        val fragments = NotifyPayload(16395)
        val sa = ESPSAPayload(cryptoValues["espSPIr"]!!)
        val tsi = TSiPayload("02000000080000280000ffff00000000000000000000000000000000ffffffffffffffffffffffffffffffff070000100000ffff00000000ffffffff".fromHex())
        val tsr = TSrPayload("02000000080000280000ffff00000000000000000000000000000000ffffffffffffffffffffffffffffffff070000100000ffff00000000ffffffff".fromHex())
        val proxyNotify = NotifyPayload(50701, "fd7465726d6e7573000d6a146857bc23f516".fromHex())

        val msg = IKEMessage(exchangeType = 35, nextMsgId, initiatorSPI, responderSPI)
        val payloads = listOf(initialContact, idResp, auth, padding, fragments, sa, tsi, tsr, proxyNotify)
        msg.addPayloads(payloads)
        msg.encrypt(cryptoValues["skr"]!!, cryptoValues["skrSalt"]!!)
        nextMsgId += 1

        return msg.assemble()
    }

    private fun replyToHeartbeat(): ByteArray {



        val msg = IKEMessage(exchangeType = 37, nextMsgId, initiatorSPI, responderSPI)
        msg.encrypt(cryptoValues["skr"]!!, cryptoValues["skrSalt"]!!)
        nextMsgId += 1
        return msg.assemble()
    }

    private fun cryptoValuesUpdated() {
        // check if we have all required values to derive key material
        val requiredKeys = listOf("ni", "nr", "dhShared")
        if(requiredKeys.any { !cryptoValues.containsKey(it) })
            return

        val ci = initiatorSPI
        val cr = responderSPI
        val ni = cryptoValues["ni"]!!
        val nr = cryptoValues["nr"]!!
        val dh = cryptoValues["dhShared"]!!

        val skeyseed = Utils.HMAC(ni + nr, dh)

        // compute SK_d (used for derivation of ESP keys) as well as IKE communication keys (auth/enc for initiator and responder each)
        val keystream = Utils.PRFplus(skeyseed, ni + nr + ci + cr, 264)

        // SK_d is the size of the hash function output (i.e. 512 in our case)
        val skd = keystream.sliceArray(0 until 64)

        // chacha/poly is AEAD so no explicit authentication keys are necessary, every key consists of the actual key (256 bit) as well as a 32 bit salt
        val ski = keystream.sliceArray(64 until 96)
        val skiSalt = keystream.sliceArray(96 until 100)
        val skr = keystream.sliceArray(100 until 132)
        val skrSalt = keystream.sliceArray(132 until 136)
        val skpi = keystream.sliceArray(136 until 200)
        val skpr = keystream.sliceArray(200 until 264)

        cryptoValues["skd"] = skd
        cryptoValues["ski"] = ski
        cryptoValues["skr"] = skr
        cryptoValues["skiSalt"] = skiSalt
        cryptoValues["skrSalt"] = skrSalt
        cryptoValues["skpi"] = skpi
        cryptoValues["skpr"] = skpr

        // ESP keys
        val keymat = Utils.PRFplus(skd, ni + nr, 128)
        cryptoValues["espKeyI"] = keymat.sliceArray(0 until 32)
        cryptoValues["espSaltI"] = keymat.sliceArray(32 until 36)
        cryptoValues["espKeyR"] = keymat.sliceArray(36 until 68)
        cryptoValues["espSaltR"] = keymat.sliceArray(68 until 72)

        Logger.logIKE("derived key material", 3)

        sessionKeysReady = true

        createTunnelIfReady()
    }

    private fun createTunnelIfReady() {
        if(!sessionKeysReady || !cryptoValues.containsKey("espSPIi") || !cryptoValues.containsKey("espSPIr"))
            return

        transport.setupTunnel(
            cryptoValues["espSPIi"]!!,
            cryptoValues["espSPIr"]!!,
            cryptoValues["espKeyI"]!!,
            cryptoValues["espSaltI"]!!,
            cryptoValues["espKeyR"]!!,
            cryptoValues["espSaltR"]!!,
            dataProtectionClass == 3
        )
    }

    private fun generateAuthSignature(): ByteArray {
        val signedBytes = cryptoValues["ownSAinit"]!! + cryptoValues["ni"]!! + Utils.HMAC(
            cryptoValues["skpr"]!!,
            cryptoValues["IDr"]!!
        )

        val key = when(dataProtectionClass) {
            1 -> LongTermStorage.getEd25519LocalPrivateKey(LongTermStorage.PRIVATE_CLASS_A)
            3 -> LongTermStorage.getEd25519LocalPrivateKey(LongTermStorage.PRIVATE_CLASS_C)
            else -> LongTermStorage.getEd25519LocalPrivateKey(LongTermStorage.PRIVATE_CLASS_D)
        }

        val signer = Ed25519Signer()
        signer.init(true, key)
        signer.update(signedBytes, 0, signedBytes.size)

        return signer.generateSignature()
    }

    private fun verifyAuthSignature(sig: ByteArray) {
        val signedBytes = cryptoValues["peerSAinit"]!! + cryptoValues["nr"]!! + Utils.HMAC(
            cryptoValues["skpi"]!!,
            cryptoValues["IDi"]!!
        )

        val key = when(dataProtectionClass) {
            1 -> LongTermStorage.getEd25519RemotePublicKey(LongTermStorage.PUBLIC_CLASS_A)
            3 -> LongTermStorage.getEd25519RemotePublicKey(LongTermStorage.PUBLIC_CLASS_C)
            else -> LongTermStorage.getEd25519RemotePublicKey(LongTermStorage.PUBLIC_CLASS_D)
        }

        val verifier = Ed25519Signer()
        verifier.init(false, key)
        verifier.update(signedBytes, 0, signedBytes.size)
        val valid = verifier.verifySignature(sig)

        if(!valid) {
            Logger.logIKE("Auth signature verification failed", 0)
            throw Exception("Auth signature verification failed")
        }
    }
}

object IKEDefs {
    private val exchangeTypes = listOf("SA_INIT", "AUTH", "CHILD_SA", "INFO")
    private val payloadTypes = listOf("SA", "KEx", "IDinit", "IDresp", "CERT", "CERTREQ", "AUTH", "NONCE", "NOTIFY", "DELETE", "VENDOR", "TSi", "TSr", "ENC&AUTH", "CONFIG", "EAP")
    val notifyTypes = listOf("INITIAL_CONTACT", "SET_WINDOW_SIZE", "ADDITIONAL_TS_POSSIBLE", "IPCOMP_SUPPORTED", "NAT_DETECTION_SOURCE_IP", "NAT_DETECTION_DESTINATION_IP", "COOKIE", "USE_TRANSPORT_MODE", "HTTP_CERT_LOOKUP_SUPPORTED", "REKEY_SA", "ESP_TFC_PADDING_NOT_SUPPORTED", "NON_FIRST_FRAGMENTS_ALSO", "MOBIKE_SUPPORTED", "ADDITIONAL_IP4_ADDRESS", "ADDITIONAL_IP6_ADDRESS", "NO_ADDITIONAL_ADDRESSES", "UPDATE_SA_ADDRESSES", "COOKIE2", "NO_NATS_ALLOWED", "AUTH_LIFETIME", "MULTIPLE_AUTH_SUPPORTED", "ANOTHER_AUTH_FOLLOWS", "REDIRECT_SUPPORTED", "REDIRECT", "REDIRECTED_FROM", "TICKET_LT_OPAQUE", "TICKET_REQUEST", "TICKET_ACK", "TICKET_NACK", "TICKET_OPAQUE", "LINK_ID", "USE_WESP_MODE", "ROHC_SUPPORTED", "EAP_ONLY_AUTHENTICATION", "CHILDLESS_IKEV2_SUPPORTED", "QUICK_CRASH_DETECTION", "IKEV2_MESSAGE_ID_SYNC_SUPPORTED", "IPSEC_REPLAY_COUNTER_SYNC_SUPPORTED", "IKEV2_MESSAGE_ID_SYNC", "IPSEC_REPLAY_COUNTER_SYNC", "SECURE_PASSWORD_METHODS", "PSK_PERSIST", "PSK_CONFIRM", "ERX_SUPPORTED", "IFOM_CAPABILITY", "SENDER_REQUEST_ID", "IKEV2_FRAGMENTATION_SUPPORTED", "SIGNATURE_HASH_ALGORITHMS", "CLONE_IKE_SA_SUPPORTED", "CLONE_IKE_SA", "PUZZLE", "USE_PPK", "PPK_IDENTITY", "NO_PPK_AUTH", "INTERMEDIATE_EXCHANGE_SUPPORTED", "IP4_ALLOWED", "IP6_ALLOWED")
    val errorTypes = listOf("UNSUPPORTED_CRITICAL_PAYLOAD", "INVALID_IKE_SPI", "INVALID_MAJOR_VERSION", "INVALID_SYNTAX", "INVALID_MESSAGE_ID", "INVALID_SPI", "NO_PROPOSAL_CHOSEN", "INVALID_KE_PAYLOAD", "AUTHENTICATION_FAILED", "SINGLE_PAIR_REQUIRED", "NO_ADDITIONAL_SAS", "INTERNAL_ADDRESS_FAILURE", "FAILED_CP_REQUIRED", "TS_UNACCEPTABLE", "INVALID_SELECTORS", "TEMPORARY_FAILURE", "CHILD_SA_NOT_FOUND")
    val privateNotifyTypes = mapOf(
        48601 to "Encrypted prelude",
        48602 to "Remote terminus version",
        48603 to "Remote device name",
        48604 to "Remote build version",
        50701 to "ProxyNotifyPayload?",
        50702 to "LinkDirectorMessage",
        50801 to "InnerAddressInitiatorClassD",
        50802 to "InnerAddressResponderClassD",
        50811 to "InnerAddressInitiatorClassC",
        50812 to "InnerAddressResponderClassC",
        51401 to "Always-On WiFi support",
        51501 to "IsAltAccountDevice",
    )

    fun exchangeType(type: Byte): String {
        return exchangeTypes[type - 34]
    }

    fun nextPayload(type: Int): String {
        return if(type == 0x0)
            "None"
        else
            payloadTypes[type - 33]
    }
}