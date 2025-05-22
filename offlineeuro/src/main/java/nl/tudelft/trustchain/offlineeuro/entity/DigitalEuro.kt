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
) : Serializable {
    fun toDigitalEuro(group: BilinearGroup): DigitalEuro {
        return DigitalEuro(
            serialNumberBytes.toString(Charsets.UTF_8),
            amountBytes.toString(Charsets.UTF_8).toDouble(),
            group.gElementFromBytes(firstTheta1Bytes),
            SchnorrSignatureSerializer.deserializeSchnorrSignatureBytes(signatureBytes)!!,
            GrothSahaiSerializer.deserializeProofListBytes(proofsBytes, group)
        )
    }
}

data class DigitalEuro(
    val serialNumber: String,
    val amount: Double,
    val firstTheta1: Element,
    val signature: SchnorrSignature,
    val proofs: ArrayList<GrothSahaiProof> = arrayListOf(),
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
            String.format("%.2f", amount).toByteArray(),
            firstTheta1.toBytes(),
            SchnorrSignatureSerializer.serializeSchnorrSignature(signature)!!,
            proofBytes ?: ByteArray(0)
        )
    }
}
