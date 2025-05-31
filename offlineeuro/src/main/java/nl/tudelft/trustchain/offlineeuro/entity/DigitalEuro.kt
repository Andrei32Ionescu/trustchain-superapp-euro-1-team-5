package nl.tudelft.trustchain.offlineeuro.entity

import it.unisa.dia.gas.jpbc.Element
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.GrothSahaiProof
import nl.tudelft.trustchain.offlineeuro.cryptography.Schnorr
import nl.tudelft.trustchain.offlineeuro.cryptography.SchnorrSignature
import nl.tudelft.trustchain.offlineeuro.libraries.GrothSahaiSerializer
import nl.tudelft.trustchain.offlineeuro.libraries.SchnorrSignatureSerializer
import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.lang.Byte
import kotlin.Any
import kotlin.Boolean
import kotlin.ByteArray
import kotlin.Int
import kotlin.String

data class DigitalEuroBytes(
    val serialNumberBytes: ByteArray,
    val amountBytes: ByteArray,
    val firstTheta1Bytes: ByteArray,
    val signatureBytes: ByteArray,
    val proofsBytes: ByteArray,
    val withdrawalTimestampBytes: ByteArray? = null,
    val timestampSignatureBytes: ByteArray? = null,
    val bankPublicKeyBytes: ByteArray? = null,
    val bankKeySignatureBytes: ByteArray? = null
) : Serializable {
    fun toDigitalEuro(group: BilinearGroup): DigitalEuro {
        return DigitalEuro(
            serialNumberBytes.toString(Charsets.UTF_8),
            amountBytes.toString(Charsets.UTF_8).toLong(),
            group.gElementFromBytes(firstTheta1Bytes),
            SchnorrSignatureSerializer.deserializeSchnorrSignatureBytes(signatureBytes)!!,
            GrothSahaiSerializer.deserializeProofListBytes(proofsBytes, group),
            withdrawalTimestampBytes?.toString(Charsets.UTF_8)!!.toLong(),
            timestampSignatureBytes.let { SchnorrSignatureSerializer.deserializeSchnorrSignatureBytes(it)!! },
            bankPublicKeyBytes.let { group.gElementFromBytes(it!!) },
            bankKeySignatureBytes.let { SchnorrSignatureSerializer.deserializeSchnorrSignatureBytes(it)!! }
        )
    }
}

data class DigitalEuro(
    val serialNumber: String,
    val amount: Long,
    val firstTheta1: Element,
    val signature: SchnorrSignature,
    val proofs: ArrayList<GrothSahaiProof> = arrayListOf(),
    val withdrawalTimestamp: Long,
    val timestampSignature: SchnorrSignature,
    val bankPublicKey: Element,
    val bankKeySignature: SchnorrSignature
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DigitalEuro) return false

        return descriptorEquals(other) &&
            this.proofs == other.proofs
    }

    fun descriptorEquals(other: DigitalEuro): Boolean {
        return this.serialNumber == other.serialNumber &&
            this.firstTheta1 == other.firstTheta1 &&
            this.signature == other.signature
    }

    fun verifySignature(
        publicKeySigner: Element,
        group: BilinearGroup
    ): Boolean {
        return Schnorr.verifySchnorrSignature(signature, publicKeySigner, group)
    }

    fun serialize(): ByteArray {
        val byteArrayOutputStream = ByteArrayOutputStream()
        val objectOutputStream = ObjectOutputStream(byteArrayOutputStream)
        objectOutputStream.writeObject(toDigitalEuroBytes())
        objectOutputStream.close()
        return byteArrayOutputStream.toByteArray()
    }

    fun sizeInBytes(): Int {
        val serialNumberBytes = serialNumber.toByteArray()
        val firstTheta1Bytes = firstTheta1.toBytes()
        val test = Byte.valueOf("0")
        val thetaByteSize = firstTheta1Bytes.filter { it -> it != test }.size
        val signatureBytes = SchnorrSignatureSerializer.serializeSchnorrSignature(signature)
        val proofBytes = GrothSahaiSerializer.serializeGrothSahaiProofs(proofs)
        val signatureByteSize = signatureBytes?.size ?: 0
        val proofByteSize = proofBytes?.size ?: 0
        val test2 = serialize().size
        return serialNumberBytes.size + firstTheta1Bytes.size + signatureByteSize + proofByteSize
    }

    fun toDigitalEuroBytes(): DigitalEuroBytes {
        val proofBytes = GrothSahaiSerializer.serializeGrothSahaiProofs(proofs)
        return DigitalEuroBytes(
            serialNumber.toByteArray(),
            amount.toString().toByteArray(),
            firstTheta1.toBytes(),
            SchnorrSignatureSerializer.serializeSchnorrSignature(signature)!!,
            proofBytes ?: ByteArray(0),
            withdrawalTimestamp?.toString()?.toByteArray(),
            timestampSignature?.let { SchnorrSignatureSerializer.serializeSchnorrSignature(it) },
            bankPublicKey?.toBytes(),
            bankKeySignature?.let { SchnorrSignatureSerializer.serializeSchnorrSignature(it) }
        )
    }
}
