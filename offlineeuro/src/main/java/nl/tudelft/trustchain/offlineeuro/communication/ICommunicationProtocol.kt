package nl.tudelft.trustchain.offlineeuro.communication

import it.unisa.dia.gas.jpbc.Element
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.GrothSahaiProof
import nl.tudelft.trustchain.offlineeuro.cryptography.RandomizationElements
import nl.tudelft.trustchain.offlineeuro.cryptography.SchnorrSignature
import nl.tudelft.trustchain.offlineeuro.entity.Participant
import nl.tudelft.trustchain.offlineeuro.entity.TransactionDetails
import nl.tudelft.trustchain.offlineeuro.enums.Role
import java.math.BigInteger

interface ICommunicationProtocol {
    var participant: Participant
    data class BlindSignatureResponse(
        val signature: BigInteger,
        val timestamp: Long,
        val timestampSignature: SchnorrSignature,
        val bankPublicKey: ByteArray,
        val bankKeySignature: SchnorrSignature,
        val amountSignature: SchnorrSignature
    )

    fun getGroupDescriptionAndCRS()

    fun register(
        userName: String,
        publicKey: Element,
        nameTTP: String,
        role: Role
    ): SchnorrSignature?

    fun getBlindSignatureRandomness(
        publicKey: Element,
        bankName: String,
        group: BilinearGroup
    ): Element

    fun requestBlindSignature(
        publicKey: Element,
        bankName: String,
        challenge: BigInteger,
        amount: Long
    ): BlindSignatureResponse

    fun requestTransactionRandomness(
        userNameReceiver: String,
        group: BilinearGroup
    ): RandomizationElements

    fun sendTransactionDetails(
        userNameReceiver: String,
        transactionDetails: TransactionDetails
    ): String

    fun requestFraudControl(
        firstProof: GrothSahaiProof,
        secondProof: GrothSahaiProof,
        nameTTP: String
    ): String

    fun getPublicKeyOf(
        name: String,
        group: BilinearGroup
    ): Element
}
