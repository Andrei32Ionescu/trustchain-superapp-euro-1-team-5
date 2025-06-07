package nl.tudelft.trustchain.offlineeuro.community.payload

import nl.tudelft.ipv8.messaging.Serializable
import nl.tudelft.ipv8.messaging.Deserializable
import nl.tudelft.ipv8.messaging.deserializeVarLen
import nl.tudelft.ipv8.messaging.serializeVarLen

class EudiInitiateVerificationPayload (
    val userName: String,
    val publicKey: ByteArray
) : Serializable {
    override fun serialize(): ByteArray {
        var payload = ByteArray(0)
        payload += serializeVarLen(userName.toByteArray())
        payload += serializeVarLen(publicKey)
        return payload
    }

    companion object Deserializer : Deserializable<EudiInitiateVerificationPayload> {
        override fun deserialize(
            buffer: ByteArray,
            offset: Int
        ): Pair<EudiInitiateVerificationPayload, Int> {
            var localOffset = offset

            val (userNameBytes, userNameSize) = deserializeVarLen(buffer, localOffset)
            localOffset += userNameSize

            val (publicKeyBytes, publicKeySize) = deserializeVarLen(buffer, localOffset)
            localOffset += publicKeySize


            return Pair(
                EudiInitiateVerificationPayload(
                    userNameBytes.toString(Charsets.UTF_8),
                    publicKeyBytes,
                ),
                localOffset - offset
            )
        }
    }
}
