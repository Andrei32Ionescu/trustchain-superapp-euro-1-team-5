package nl.tudelft.trustchain.offlineeuro.community.payload

import nl.tudelft.ipv8.messaging.Serializable
import nl.tudelft.ipv8.messaging.Deserializable
import nl.tudelft.ipv8.messaging.deserializeVarLen
import nl.tudelft.ipv8.messaging.serializeVarLen

class RequestUserVerificationPayload (
    val transactionId: String,
    val deeplink: String
) : Serializable {
    override fun serialize(): ByteArray {
        var payload = ByteArray(0)
        payload += serializeVarLen(transactionId.toByteArray())
        payload += serializeVarLen(deeplink.toByteArray())
        return payload
    }

    companion object Deserializer : Deserializable<RequestUserVerificationPayload> {
        override fun deserialize(
            buffer: ByteArray,
            offset: Int
        ): Pair<RequestUserVerificationPayload, Int> {
            var localOffset = offset

            val (transactionIdBytes, transactionISize) = deserializeVarLen(buffer, localOffset)
            localOffset += transactionISize

            val (deeplinkBytes, deeplinkSize) = deserializeVarLen(buffer, localOffset)
            localOffset += deeplinkSize

            return Pair(
                RequestUserVerificationPayload(
                    transactionIdBytes.toString(Charsets.UTF_8),
                    deeplinkBytes.toString(Charsets.UTF_8)
                ),
                localOffset - offset
            )
        }
    }
}
