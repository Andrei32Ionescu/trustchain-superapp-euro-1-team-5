package nl.tudelft.trustchain.offlineeuro.community.payload

import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.GrothSahaiProof
import nl.tudelft.trustchain.offlineeuro.cryptography.Schnorr
import nl.tudelft.trustchain.offlineeuro.cryptography.SchnorrSignature
import nl.tudelft.trustchain.offlineeuro.cryptography.TransactionProof
import nl.tudelft.trustchain.offlineeuro.entity.DigitalEuro
import nl.tudelft.trustchain.offlineeuro.entity.TransactionDetails
import org.junit.Assert
import org.junit.Test
import java.math.BigInteger
import java.util.UUID

class TransactionDetailsPayloadTest {
    private val group = BilinearGroup()

    @Test
    fun serializeAndDeserializeInitialEuro() {
        val publicKey = group.generateRandomElementOfG().toBytes()
        val transactionDetails = generateTransactionDetails(0)
        val transactionDetailsBytes = transactionDetails.toTransactionDetailsBytes()
        val serializedBytes = TransactionDetailsPayload(publicKey, transactionDetailsBytes).serialize()
        val deserializedBytes = TransactionDetailsPayload.deserialize(serializedBytes).first
        val deserializedPublicKey = deserializedBytes.publicKey
        val deserializedTransactionDetails = deserializedBytes.transactionDetailsBytes
        val fromBytes = deserializedTransactionDetails.toTransactionDetails(group)
        Assert.assertArrayEquals(publicKey, deserializedPublicKey)
        Assert.assertEquals(transactionDetails, fromBytes)
    }

    @Test
    fun serializeAndDeserializeEuroOneProof() {
        val publicKey = group.generateRandomElementOfG().toBytes()
        val transactionDetails = generateTransactionDetails(1)
        val transactionDetailsBytes = transactionDetails.toTransactionDetailsBytes()
        val serializedBytes = TransactionDetailsPayload(publicKey, transactionDetailsBytes).serialize()
        val deserializedBytes = TransactionDetailsPayload.deserialize(serializedBytes).first.transactionDetailsBytes
        val fromBytes = deserializedBytes.toTransactionDetails(group)
        Assert.assertEquals(transactionDetails, fromBytes)
    }

    @Test
    fun serializeAndDeserializeEuroFiveProofs() {
        val publicKey = group.generateRandomElementOfG().toBytes()
        val transactionDetails = generateTransactionDetails(5)
        val transactionDetailsBytes = transactionDetails.toTransactionDetailsBytes()
        val serializedBytes = TransactionDetailsPayload(publicKey, transactionDetailsBytes).serialize()
        val deserializedBytes = TransactionDetailsPayload.deserialize(serializedBytes).first.transactionDetailsBytes
        val fromBytes = deserializedBytes.toTransactionDetails(group)
        Assert.assertEquals(transactionDetails, fromBytes)
    }

    fun generateTransactionDetails(numberOfProofs: Int): TransactionDetails {
        val digitalEuro = generateDigitalEuro(numberOfProofs)
        val transactionProof = generateTranactionProof()

        val previousSignature =
            if (numberOfProofs > 0) {
                generateSignature()
            } else {
                null
            }

        return TransactionDetails(
            digitalEuro,
            transactionProof,
            previousSignature,
            generateSignature(),
            group.generateRandomElementOfG()
        )
    }

    fun generateTranactionProof(): TransactionProof {
        return TransactionProof(
            generateGrothSahaiProof(),
            group.generateRandomElementOfH(),
            group.generateRandomElementOfH()
        )
    }

    fun generateDigitalEuro(numberOfProofs: Int): DigitalEuro {
        val proofs = arrayListOf<GrothSahaiProof>()
        for (i: Int in 0 until numberOfProofs) {
            proofs.add(generateGrothSahaiProof())
        }

        // Generate proper signatures for the new fields
        val bankPrivateKey = group.getRandomZr()
        val bankPublicKey = group.g.powZn(bankPrivateKey)
        val amount = 200L
        val timestamp = System.currentTimeMillis()
        val serialNumber = UUID.randomUUID().toString()

        // Sign amount
        val amountSignature = Schnorr.schnorrSignature(
            bankPrivateKey,
            amount.toString().toByteArray(Charsets.UTF_8),
            group
        )

        // Sign hash
        val hashString = "$serialNumber | $amount | $timestamp"
        val hashSignature = Schnorr.schnorrSignature(
            bankPrivateKey,
            hashString.hashCode().toString().toByteArray(Charsets.UTF_8),
            group
        )

        // Mock TTP signature on bank key
        val ttpPrivateKey = group.getRandomZr()
        val bankKeySignature = Schnorr.schnorrSignature(
            ttpPrivateKey,
            bankPublicKey.toBytes(),
            group
        )

        return DigitalEuro(
            serialNumber,
            amount,
            group.generateRandomElementOfG(),
            generateSignature(),
            proofs,
            timestamp,
            hashSignature,
            bankPublicKey,
            bankKeySignature,
            amountSignature
        )
    }

    fun generateSignature(): SchnorrSignature {
        return SchnorrSignature(
            BigInteger("32930721097129037029137"),
            BigInteger("24829734219852198372193"),
            "SchnorrSignatureTest".toByteArray()
        )
    }

    fun generateGrothSahaiProof(): GrothSahaiProof {
        return GrothSahaiProof(
            group.generateRandomElementOfG(),
            group.generateRandomElementOfG(),
            group.generateRandomElementOfH(),
            group.generateRandomElementOfH(),
            group.generateRandomElementOfG(),
            group.generateRandomElementOfG(),
            group.generateRandomElementOfH(),
            group.generateRandomElementOfH(),
            group.generateRandomElementOfGT()
        )
    }
}
