package nl.tudelft.trustchain.offlineeuro.community.payload

import nl.tudelft.ipv8.messaging.Deserializable
import nl.tudelft.ipv8.messaging.Serializable
import nl.tudelft.ipv8.messaging.deserializeVarLen
import nl.tudelft.ipv8.messaging.serializeVarLen
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroupElementsBytes
import nl.tudelft.trustchain.offlineeuro.cryptography.CRSBytes

class BilinearGroupCRSPayload(
    val bilinearGroupElements: BilinearGroupElementsBytes,
    val crs: CRSBytes,
    val ttpPublicKey: ByteArray
) : Serializable {
    override fun serialize(): ByteArray {
        var payload = ByteArray(0)
        payload += serializeVarLen(bilinearGroupElements.g)
        payload += serializeVarLen(bilinearGroupElements.h)
        payload += serializeVarLen(bilinearGroupElements.gt)

        payload += serializeVarLen(crs.g)
        payload += serializeVarLen(crs.u)
        payload += serializeVarLen(crs.gPrime)
        payload += serializeVarLen(crs.uPrime)

        payload += serializeVarLen(crs.h)
        payload += serializeVarLen(crs.v)
        payload += serializeVarLen(crs.hPrime)
        payload += serializeVarLen(crs.vPrime)

        payload += serializeVarLen(ttpPublicKey)

        return payload
    }

    companion object Deserializer : Deserializable<BilinearGroupCRSPayload> {
        override fun deserialize(
            buffer: ByteArray,
            offset: Int
        ): Pair<BilinearGroupCRSPayload, Int> {
            var localOffset = offset

            val (groupG, groupGSize) = deserializeVarLen(buffer, localOffset)
            localOffset += groupGSize

            val (groupH, groupHSize) = deserializeVarLen(buffer, localOffset)
            localOffset += groupHSize

            val (groupGt, groupGtSize) = deserializeVarLen(buffer, localOffset)
            localOffset += groupGtSize

            val (crsG, crsGSize) = deserializeVarLen(buffer, localOffset)
            localOffset += crsGSize

            val (crsU, crsUSize) = deserializeVarLen(buffer, localOffset)
            localOffset += crsUSize

            val (crsGPrime, crsGPrimeSize) = deserializeVarLen(buffer, localOffset)
            localOffset += crsGPrimeSize

            val (crsUPrime, crsUPrimeSize) = deserializeVarLen(buffer, localOffset)
            localOffset += crsUPrimeSize

            val (crsH, crsHSize) = deserializeVarLen(buffer, localOffset)
            localOffset += crsHSize

            val (crsV, crsVSize) = deserializeVarLen(buffer, localOffset)
            localOffset += crsVSize

            val (crsHPrime, crsHPrimeSize) = deserializeVarLen(buffer, localOffset)
            localOffset += crsHPrimeSize

            val (crsVPrime, crsVPrimeSize) = deserializeVarLen(buffer, localOffset)
            localOffset += crsVPrimeSize

            val (ttpPubKeyBytes, ttpPubKeySize) = deserializeVarLen(buffer, localOffset)
            localOffset += ttpPubKeySize

            val groupElementsBytes = BilinearGroupElementsBytes(groupG, groupH, groupGt)
            val crsBytes =
                CRSBytes(
                    crsG,
                    crsU,
                    crsGPrime,
                    crsUPrime,
                    crsH,
                    crsV,
                    crsHPrime,
                    crsVPrime,
                    ttpPubKeyBytes
                )

            return Pair(
                BilinearGroupCRSPayload(groupElementsBytes, crsBytes, ttpPubKeyBytes),
                localOffset - offset
            )
        }
    }
}
