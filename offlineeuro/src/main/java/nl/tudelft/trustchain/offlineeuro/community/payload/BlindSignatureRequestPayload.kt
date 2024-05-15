package nl.tudelft.trustchain.offlineeuro.community.payload

import nl.tudelft.ipv8.messaging.Deserializable
import nl.tudelft.ipv8.messaging.Serializable
import nl.tudelft.ipv8.messaging.deserializeVarLen
import nl.tudelft.ipv8.messaging.serializeVarLen
import java.math.BigInteger

class BlindSignatureRequestPayload(
    val challenge: BigInteger,
    val publicKeyBytes: ByteArray
) : Serializable {
    override fun serialize(): ByteArray {
        var payload = ByteArray(0)
        payload += serializeVarLen(challenge.toByteArray())
        payload += serializeVarLen(publicKeyBytes)
        return payload
    }

    companion object Deserializer : Deserializable<BlindSignatureRequestPayload> {
        override fun deserialize(
            buffer: ByteArray,
            offset: Int
        ): Pair<BlindSignatureRequestPayload, Int> {
            var localOffset = offset

            val (challengeBytes, challengeSize) = deserializeVarLen(buffer, offset)
            localOffset += challengeSize

            val (publicKeyBytes, publicKeySize) = deserializeVarLen(buffer, localOffset)
            localOffset += publicKeySize

            return Pair(
                BlindSignatureRequestPayload(BigInteger(challengeBytes), publicKeyBytes),
                localOffset - offset
            )
        }
    }
}
