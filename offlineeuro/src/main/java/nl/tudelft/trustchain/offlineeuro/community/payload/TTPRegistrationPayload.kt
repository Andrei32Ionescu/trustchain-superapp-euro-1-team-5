package nl.tudelft.trustchain.offlineeuro.community.payload

import nl.tudelft.ipv8.messaging.Deserializable
import nl.tudelft.ipv8.messaging.Serializable
import nl.tudelft.ipv8.messaging.deserializeVarLen
import nl.tudelft.ipv8.messaging.serializeVarLen
import nl.tudelft.trustchain.offlineeuro.enums.Role

class TTPRegistrationPayload(
    val userName: String,
    val publicKey: ByteArray,
    val overlayPublicKey: ByteArray,
    val role : Role,
) : Serializable {
    override fun serialize(): ByteArray {
        var payload = ByteArray(0)
        payload += serializeVarLen(userName.toByteArray())
        payload += serializeVarLen(publicKey)
        payload += serializeVarLen(byteArrayOf(role.ordinal.toByte()))
        payload += serializeVarLen(overlayPublicKey)
        return payload
    }

    companion object Deserializer : Deserializable<TTPRegistrationPayload> {
        override fun deserialize(
            buffer: ByteArray,
            offset: Int
        ): Pair<TTPRegistrationPayload, Int> {
            var localOffset = offset

            val (nameBytes, nameSize) = deserializeVarLen(buffer, localOffset)
            localOffset += nameSize
            val (publicKeyBytes, publicKeyBytesSize) = deserializeVarLen(buffer, localOffset)
            localOffset += publicKeyBytesSize
            val (roleBytes, roleBytesSize) = deserializeVarLen(buffer, localOffset)
            localOffset += roleBytesSize
            val (overlayPKBytes, overlayPKSize) = deserializeVarLen(buffer, localOffset)
            localOffset += overlayPKSize

            return Pair(
                TTPRegistrationPayload(
                    nameBytes.toString(Charsets.UTF_8),
                    publicKeyBytes,
                    overlayPKBytes,
                    Role.entries[roleBytes[0].toInt()]
                ),
                localOffset - offset
            )
        }
    }
}
