package nl.tudelft.trustchain.offlineeuro.community.payload

import nl.tudelft.ipv8.messaging.Serializable
import nl.tudelft.ipv8.messaging.Deserializable
import nl.tudelft.ipv8.messaging.deserializeVarLen
import nl.tudelft.ipv8.messaging.serializeVarLen

class EudiInitiateVerificationReplyPayload (
    val transactionId: String,
    val requestURI: String,
    val requestURIMethod: String,
    val clientId: String
) : Serializable {
    override fun serialize(): ByteArray {
        var payload = ByteArray(0)
        payload += serializeVarLen(transactionId.toByteArray())
        payload += serializeVarLen(requestURI.toByteArray())
        payload += serializeVarLen(requestURIMethod.toByteArray())
        payload += serializeVarLen(clientId.toByteArray())
        return payload
    }

    companion object Deserializer : Deserializable<EudiInitiateVerificationReplyPayload> {
        override fun deserialize(
            buffer: ByteArray,
            offset: Int
        ): Pair<EudiInitiateVerificationReplyPayload, Int> {
            var localOffset = offset

            val (transactionIdBytes, transactionISize) = deserializeVarLen(buffer, localOffset)
            localOffset += transactionISize

            val (requestURIBytes, requestURISize) = deserializeVarLen(buffer, localOffset)
            localOffset += requestURISize

            val (requestURIMethodBytes, requestURIMethodSize) = deserializeVarLen(buffer, localOffset)
            localOffset += requestURIMethodSize

            val (clientIdBytes, clientIdSize) = deserializeVarLen(buffer, localOffset)
            localOffset += clientIdSize

            return Pair(
                EudiInitiateVerificationReplyPayload(
                    transactionIdBytes.toString(Charsets.UTF_8),
                    requestURIBytes.toString(Charsets.UTF_8),
                    requestURIMethodBytes.toString(Charsets.UTF_8),
                    clientIdBytes.toString(Charsets.UTF_8)
                ),
                localOffset - offset
            )
        }
    }
}
