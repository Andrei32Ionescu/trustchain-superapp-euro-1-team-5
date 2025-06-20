package nl.tudelft.trustchain.offlineeuro.community.payload

import nl.tudelft.ipv8.messaging.Serializable
import nl.tudelft.ipv8.messaging.Deserializable
import nl.tudelft.ipv8.messaging.deserializeVarLen
import nl.tudelft.ipv8.messaging.serializeVarLen

class UserVerificationSubmitPayload (
    val transactionId: String,
) : Serializable {
    override fun serialize(): ByteArray {
        var payload = ByteArray(0)
        payload += serializeVarLen(transactionId.toByteArray())
        return payload
    }

    companion object Deserializer : Deserializable<UserVerificationSubmitPayload> {
        override fun deserialize(
            buffer: ByteArray,
            offset: Int
        ): Pair<UserVerificationSubmitPayload, Int> {
            var localOffset = offset

            val (transactionIdBytes, transactionISize) = deserializeVarLen(buffer, localOffset)
            localOffset += transactionISize

            return Pair(
                UserVerificationSubmitPayload(
                    transactionIdBytes.toString(Charsets.UTF_8)
                ),
                localOffset - offset
            )
        }
    }
}
