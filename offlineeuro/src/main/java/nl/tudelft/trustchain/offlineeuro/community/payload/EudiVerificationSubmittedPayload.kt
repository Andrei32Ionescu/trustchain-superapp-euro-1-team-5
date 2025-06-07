package nl.tudelft.trustchain.offlineeuro.community.payload

import nl.tudelft.ipv8.messaging.Deserializable
import nl.tudelft.ipv8.messaging.Serializable
import nl.tudelft.ipv8.messaging.deserializeVarLen
import nl.tudelft.ipv8.messaging.serializeVarLen

class EudiVerificationSubmittedPayload(
    val transactionId: String,
    val clientId: String, // maybe not needed?
    val userName: String,
    val publicKey: ByteArray
) : Serializable {
    override fun serialize(): ByteArray {
        var payload = ByteArray(0)
        payload += serializeVarLen(transactionId.toByteArray())
        payload += serializeVarLen(clientId.toByteArray())
        payload += serializeVarLen(userName.toByteArray())
        payload += serializeVarLen(publicKey)
        return payload
    }

    companion object Deserializer : Deserializable<EudiVerificationSubmittedPayload> {
        override fun deserialize(
            buffer: ByteArray,
            offset: Int
        ): Pair<EudiVerificationSubmittedPayload, Int> {
            var localOffset = offset

            val (txIdBytes, txIdSize) = deserializeVarLen(buffer, localOffset)
            localOffset += txIdSize
            val (clientIdBytes, clientIdSize) = deserializeVarLen(buffer, localOffset)
            localOffset += clientIdSize
            val (userNameBytes, userNameSize) = deserializeVarLen(buffer, localOffset)
            localOffset += userNameSize
            val (publicKeyBytes, publicKeyBytesSize) = deserializeVarLen(buffer, localOffset)
            localOffset += publicKeyBytesSize

            return Pair(
                EudiVerificationSubmittedPayload(
                    txIdBytes.toString(Charsets.UTF_8),
                    clientIdBytes.toString(Charsets.UTF_8),
                    userNameBytes.toString(Charsets.UTF_8),
                    publicKeyBytes
                ),
                localOffset - offset
            )
        }
    }
}
