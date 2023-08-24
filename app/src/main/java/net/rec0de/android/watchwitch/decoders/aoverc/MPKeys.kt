package net.rec0de.android.watchwitch.decoders.aoverc

import net.rec0de.android.watchwitch.ParseCompanion
import net.rec0de.android.watchwitch.fromBytesBig
import net.rec0de.android.watchwitch.fromIndex
import net.rec0de.android.watchwitch.hex
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.ECPointUtil
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jce.spec.ECNamedCurveSpec
import java.lang.Exception
import java.math.BigInteger
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECPoint
import java.security.spec.ECPrivateKeySpec
import java.security.spec.ECPublicKeySpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec

data class MPKeys(
    val ecdsaRemotePublic: ByteArray,
    val rsaLocalPrivate: ByteArray,
    val ecdsaLocalPrivate: ByteArray,
    val rsaRemotePublic: ByteArray
    ) {

    companion object : ParseCompanion() {
        fun fromSentKeys(publicDER: ByteArray, ecdsaPrivate: ByteArray, rsaPrivatePKCS1: ByteArray) : MPKeys {
            val public = dissectDER(publicDER)

            // values have a two byte length prefix that we'll strip
            val ecdsaRemotePublic = public.first.fromIndex(2)
            val rsaRemotePublic = public.second.fromIndex(2)

            return MPKeys(ecdsaRemotePublic, rsaPrivatePKCS1, ecdsaPrivate, rsaRemotePublic)
        }

        private fun dissectDER(der: ByteArray): Pair<ByteArray, ByteArray> {
            if(der[0].toInt() != 0x30)
                throw Exception("Expected DER type SEQUENCE (0x30) but got 0x${der[0].toString(16)} in ${der.hex()}")
            parseOffset = 1
            val totalLen = readDerLengthField(der)

            if(totalLen != der.size - parseOffset)
                throw Exception("Expected DER length $totalLen but got ${der.size - parseOffset} in ${der.hex()}")

            // first element
            val firstType = readInt(der, 1)
            val firstLen = readDerLengthField(der)
            val firstPayload = readBytes(der, firstLen)
            println("First payload type 0x${firstType.toString(16)}: ${firstPayload.hex()}")

            // second element
            val secondType = readInt(der, 1)
            val secondLen = readDerLengthField(der)
            val secondPayload = readBytes(der, secondLen)

            println("Second payload type 0x${secondType.toString(16)}: ${secondPayload.hex()}")

            return Pair(firstPayload, secondPayload)
        }

        private fun readDerLengthField(der: ByteArray): Int {
            // multi-byte length
            val len = if(der[parseOffset].toInt() and 0x80 != 0) {
                val lengthByteCount = der[parseOffset].toInt() and 0x7F
                val start = parseOffset+1
                parseOffset += 1+lengthByteCount
                UInt.fromBytesBig(der.sliceArray(start until start+lengthByteCount)).toInt()
            }
            // single byte length
            else {
                parseOffset += 1
                der[parseOffset-1].toInt()
            }

            return len
        }
    }

    fun friendlyRsaPublicKey(): PublicKey {
        // it just so happens that we can re-use dissectDER here, which is a little cursed but works
        val keyComponents = dissectDER(rsaRemotePublic)
        // pitfall: bigint uses first bit as sign by default, we specifically force to positive here
        val spec = RSAPublicKeySpec(BigInteger(1, keyComponents.first), BigInteger(1, keyComponents.second))
        return KeyFactory.getInstance("RSA").generatePublic(spec)
    }

    fun friendlyRsaPrivateKey(): PrivateKey {
        // technically the key bytes we get are (should be?) PKCS#1 encoded, but apparently the PKCS#8 decoder reads them just fine
        return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(rsaLocalPrivate))
    }

    fun friendlyEcdsaPublicKey(): ECPublicKey {
        val spec = ECNamedCurveTable.getParameterSpec("secp256r1")
        val kf = KeyFactory.getInstance("ECDSA", BouncyCastleProvider())
        val params = ECNamedCurveSpec("secp256r1", spec.curve, spec.g, spec.n)

        val point: ECPoint = ECPointUtil.decodePoint(params.curve, ecdsaRemotePublic)
        val pubKeySpec = ECPublicKeySpec(point, params)
        return  kf.generatePublic(pubKeySpec) as ECPublicKey
    }

    fun friendlyEcdsaPrivateKey(): ECPrivateKey {
        val spec = ECNamedCurveTable.getParameterSpec("secp256r1")
        val kf = KeyFactory.getInstance("ECDSA", BouncyCastleProvider())
        val params = ECNamedCurveSpec("secp256r1", spec.curve, spec.g, spec.n)

        val bigInt = BigInteger(1, ecdsaLocalPrivate)
        val privKeySpec = ECPrivateKeySpec(bigInt, params)
        return kf.generatePrivate(privKeySpec) as ECPrivateKey
    }
}