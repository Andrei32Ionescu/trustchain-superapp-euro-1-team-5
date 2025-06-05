package nl.tudelft.trustchain.offlineeuro.community.payload

import nl.tudelft.ipv8.messaging.Deserializable
import nl.tudelft.ipv8.messaging.Serializable
import nl.tudelft.ipv8.messaging.deserializeVarLen
import nl.tudelft.ipv8.messaging.serializeVarLen
import nl.tudelft.trustchain.offlineeuro.cryptography.TransactionProofBytes
import nl.tudelft.trustchain.offlineeuro.entity.DigitalEuroBytes
import nl.tudelft.trustchain.offlineeuro.entity.TransactionDetailsBytes

class TransactionDetailsPayload(
    val publicKey: ByteArray,
    val transactionDetailsBytes: TransactionDetailsBytes
) : Serializable {
    override fun serialize(): ByteArray {
        var payload = ByteArray(0)

        payload += serializeVarLen(publicKey)
        // Add the Digital Euro Part
        val digitalEuroBytes = transactionDetailsBytes.digitalEuroBytes
        payload += serializeVarLen(digitalEuroBytes.serialNumberBytes)
        payload += serializeVarLen(digitalEuroBytes.amountBytes)
        payload += serializeVarLen(digitalEuroBytes.firstTheta1Bytes)
        payload += serializeVarLen(digitalEuroBytes.signatureBytes)
        payload += serializeVarLen(digitalEuroBytes.proofsBytes)
        payload += serializeVarLen(digitalEuroBytes.withdrawalTimestampBytes)
        payload += serializeVarLen(digitalEuroBytes.timestampSignatureBytes)
        payload += serializeVarLen(digitalEuroBytes.bankPublicKeyBytes)
        payload += serializeVarLen(digitalEuroBytes.bankKeySignatureBytes)

        // Add the current transaction parts
        val currentTransactionBytes = transactionDetailsBytes.currentTransactionProofBytes
        payload += serializeVarLen(currentTransactionBytes.grothSahaiProofBytes)
        payload += serializeVarLen(currentTransactionBytes.usedYBytes)
        payload += serializeVarLen(currentTransactionBytes.usedVSBytes)

        // Add the remainders
        payload += serializeVarLen(transactionDetailsBytes.previousThetaSignatureBytes)
        payload += serializeVarLen(transactionDetailsBytes.theta1SignatureBytes)
        payload += serializeVarLen(transactionDetailsBytes.spenderPublicKeyBytes)

        return payload
    }

    companion object Deserializer : Deserializable<TransactionDetailsPayload> {
        override fun deserialize(
            buffer: ByteArray,
            offset: Int
        ): Pair<TransactionDetailsPayload, Int> {
            var localOffset = offset

            val (publicKeyBytes, publicKeyBytesSize) = deserializeVarLen(buffer, localOffset)
            localOffset += publicKeyBytesSize

            // Digital Euro Parts
            val (serialNumberBytes, serialNumberSize) = deserializeVarLen(buffer, localOffset)
            localOffset += serialNumberSize

            val (amountBytes, amountSize) = deserializeVarLen(buffer, localOffset)
            localOffset += amountSize

            val (firstTheta1Bytes, firstTheta1Size) = deserializeVarLen(buffer, localOffset)
            localOffset += firstTheta1Size

            val (signatureBytes, signatureSize) = deserializeVarLen(buffer, localOffset)
            localOffset += signatureSize

            val (proofBytes, proofBytesSize) = deserializeVarLen(buffer, localOffset)
            localOffset += proofBytesSize

            val (withdrawalTimestampBytes, withdrawalTimestampSize) = deserializeVarLen(buffer, localOffset)
            localOffset += withdrawalTimestampSize

            val (timestampSignatureBytes, timestampSignatureSize) = deserializeVarLen(buffer, localOffset)
            localOffset += timestampSignatureSize

            val (bankPublicKeyBytes, bankPublicKeySize) = deserializeVarLen(buffer, localOffset)
            localOffset += bankPublicKeySize

            val (bankKeySignatureBytes, bankKeySignatureSize) = deserializeVarLen(buffer, localOffset)
            localOffset += bankKeySignatureSize

            // Current Transaction Parts
            val (grothSahaiProofBytes, grothSahaiProofSize) = deserializeVarLen(buffer, localOffset)
            localOffset += grothSahaiProofSize

            val (usedYBytes, usedYSize) = deserializeVarLen(buffer, localOffset)
            localOffset += usedYSize

            val (usedVSBytes, usedVSSize) = deserializeVarLen(buffer, localOffset)
            localOffset += usedVSSize

            val (previousThetaSignatureBytes, previousThetaSignatureSize) =
                deserializeVarLen(
                    buffer,
                    localOffset
                )
            localOffset += previousThetaSignatureSize

            val (theta1SignatureBytes, theta1SignatureSize) = deserializeVarLen(buffer, localOffset)
            localOffset += theta1SignatureSize

            val (spenderPublicKeyBytes, spenderPublicKeySize) =
                deserializeVarLen(
                    buffer,
                    localOffset
                )
            localOffset += spenderPublicKeySize

            val digitalEuroBytes =
                DigitalEuroBytes(serialNumberBytes, amountBytes,firstTheta1Bytes, signatureBytes, proofBytes, withdrawalTimestampBytes, timestampSignatureBytes, bankPublicKeyBytes, bankKeySignatureBytes)
            val transactionProofBytes =
                TransactionProofBytes(grothSahaiProofBytes, usedYBytes, usedVSBytes)

            val transactionDetailsBytes =
                TransactionDetailsBytes(
                    digitalEuroBytes,
                    transactionProofBytes,
                    previousThetaSignatureBytes,
                    theta1SignatureBytes,
                    spenderPublicKeyBytes
                )

            return Pair(
                TransactionDetailsPayload(publicKeyBytes, transactionDetailsBytes),
                localOffset - offset
            )
        }
    }
}
