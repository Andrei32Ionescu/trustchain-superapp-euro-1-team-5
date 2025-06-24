package nl.tudelft.trustchain.offlineeuro.entity

import android.content.Context
import it.unisa.dia.gas.jpbc.Element
import nl.tudelft.trustchain.offlineeuro.communication.ICommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.Schnorr
import nl.tudelft.trustchain.offlineeuro.cryptography.SchnorrSignature
import nl.tudelft.trustchain.offlineeuro.db.DepositedEuroManager
import nl.tudelft.trustchain.offlineeuro.enums.Role
import java.math.BigInteger
import kotlin.math.min

class Bank(
    name: String,
    group: BilinearGroup,
    communicationProtocol: ICommunicationProtocol,
    context: Context?,
    private val depositedEuroManager: DepositedEuroManager = DepositedEuroManager(context, group),
    runSetup: Boolean = true,
    onDataChangeCallback: ((String?) -> Unit)? = null,
) : Participant(communicationProtocol, name, onDataChangeCallback, Role.Bank) {
    private val depositedEuros: ArrayList<DigitalEuro> = arrayListOf()
    val withdrawUserRandomness: HashMap<Element, Element> = hashMapOf()
    val depositedEuroLogger: ArrayList<Pair<String, Boolean>> = arrayListOf()
    var ttpSignatureOnPublicKey: SchnorrSignature? = null

    init {
        communicationProtocol.participant = this
        this.group = group
        if (runSetup) {
            setUp()
        } else {
            generateKeyPair()
        }
    }

    override fun registerAtTTP() {
        val signature = communicationProtocol.register(name, publicKey, "TTP", role)
        if (signature != null) {
            ttpSignatureOnPublicKey = signature
            onDataChangeCallback?.invoke("Registered at TTP with signed public key")
        }
    }

    fun getBlindSignatureRandomness(userPublicKey: Element): Element {
        if (withdrawUserRandomness.containsKey(userPublicKey)) {
            val randomness = withdrawUserRandomness[userPublicKey]!!
            return group.g.powZn(randomness)
        }
        val randomness = group.getRandomZr()
        withdrawUserRandomness[userPublicKey] = randomness
        return group.g.powZn(randomness)
    }

    fun createBlindSignature(
        challenge: BigInteger,
        userPublicKey: Element,
        amount: Long,
        serialNumber: String
    ): ICommunicationProtocol.BlindSignatureResponse {  // Return signature and metadata
        val k = lookUp(userPublicKey) ?: return ICommunicationProtocol.BlindSignatureResponse(BigInteger.ZERO,3,SchnorrSignature(BigInteger.ZERO, BigInteger.ZERO, ByteArray(0)), userPublicKey.toBytes(), SchnorrSignature(BigInteger.ZERO, BigInteger.ZERO, ByteArray(0)),SchnorrSignature(BigInteger.ZERO, BigInteger.ZERO, ByteArray(0)))
        remove(userPublicKey)

        // Create timestamp
        val timestamp = System.currentTimeMillis()

        // Sign the initial amount with bank's private key
        val amountSignature = Schnorr.schnorrSignature(
            privateKey,
            amount.toString().toByteArray(Charsets.UTF_8),
            group
        )

        // Sign the hash with bank's private key
        val hashString = "$serialNumber | $amount | $timestamp"
        val hashSignature = Schnorr.schnorrSignature(
            privateKey,
            hashString.hashCode().toString().toByteArray(Charsets.UTF_8),
            group
        )

        val bankPk = group.gElementFromBytes(publicKey.toBytes())

        val blindSignature = Schnorr.signBlindedChallenge(k, challenge, privateKey)

        onDataChangeCallback?.invoke("A token of €${amount.toFloat()/100.0} was withdrawn by $userPublicKey")

        return ICommunicationProtocol.BlindSignatureResponse(blindSignature, timestamp, hashSignature, bankPk.toBytes(), ttpSignatureOnPublicKey!!, amountSignature)
    }

    fun getWithdrawalMetadata(): Pair<Element, SchnorrSignature> {
        if (ttpSignatureOnPublicKey == null) {
            throw Exception("Bank not registered with TTP")
        }
        return Pair(publicKey, ttpSignatureOnPublicKey!!)
    }

    private fun lookUp(userPublicKey: Element): Element? {
        for (element in withdrawUserRandomness.entries) {
            val key = element.key

            if (key == userPublicKey) {
                return element.value
            }
        }

        return null
    }

    private fun remove(userPublicKey: Element): Element? {
        for (element in withdrawUserRandomness.entries) {
            val key = element.key

            if (key == userPublicKey) {
                return withdrawUserRandomness.remove(key)
            }
        }

        return null
    }

    private fun depositEuro(
        euro: DigitalEuro,
        publicKeyUser: Element
    ): String {
        val duplicateEuros = depositedEuroManager.getDigitalEurosByDescriptor(euro)

        if (duplicateEuros.isEmpty()) {
            depositedEuroLogger.add(Pair(euro.serialNumber, false))
            depositedEuroManager.insertDigitalEuro(euro)
            val amount = euro.amount.toFloat()/100.0
            onDataChangeCallback?.invoke("$amount euro was deposited successfully by $publicKeyUser")
            return "Deposit was successful!"
        }

        var maxFirstDifferenceIndex = -1
        var doubleSpendEuro: DigitalEuro? = null
        for (duplicateEuro in duplicateEuros) {
            // Loop over the proofs to find the double spending
            val euroProofs = euro.proofs
            val duplicateEuroProofs = duplicateEuro.proofs

            for (i in 0 until min(euroProofs.size, duplicateEuroProofs.size)) {
                if (euroProofs[i] == duplicateEuroProofs[i]) {
                    continue
                } else if (i > maxFirstDifferenceIndex) {
                    maxFirstDifferenceIndex = i
                    doubleSpendEuro = duplicateEuro
                    break
                }
            }
        }

        if (doubleSpendEuro != null) {
            val euroProof = euro.proofs[maxFirstDifferenceIndex]
            val depositProof = doubleSpendEuro.proofs[maxFirstDifferenceIndex]
            try {
                val dsResult =
                    communicationProtocol.requestFraudControl(euroProof, depositProof, "TTP")

                if (dsResult != "") {
                    depositedEuroLogger.add(Pair(euro.serialNumber, true))
                    // <Increase user balance here and penalize the fraudulent User>
                    depositedEuroManager.insertDigitalEuro(euro)
                    onDataChangeCallback?.invoke(dsResult)
                    return dsResult
                }
            } catch (e: Exception) {
                depositedEuroLogger.add(Pair(euro.serialNumber, true))
                depositedEuroManager.insertDigitalEuro(euro)
                onDataChangeCallback?.invoke("Noticed double spending but could not reach TTP")
                return "Found double spending proofs, but TTP is unreachable"
            }
        }
        depositedEuroLogger.add(Pair(euro.serialNumber, true))
        // <Increase user balance here>
        depositedEuroManager.insertDigitalEuro(euro)
        onDataChangeCallback?.invoke("Noticed double spending but could not find a proof")
        return "Detected double spending but could not blame anyone"
    }

    fun getDepositedTokens(): List<DigitalEuro> {
        return depositedEuros
    }

    override fun onReceivedTransaction(
        transactionDetails: TransactionDetails,
        publicKeyBank: Element,
        publicKeySender: Element
    ): String {
        val transactionResult = Transaction.validate(transactionDetails, publicKeyBank, group, crs, isDeposit = true)
        if (transactionResult.valid) {
            val digitalEuro = transactionDetails.digitalEuro
            digitalEuro.proofs.add(transactionDetails.currentTransactionProof.grothSahaiProof)
            return depositEuro(transactionDetails.digitalEuro, publicKeySender)
        }

        return transactionResult.description
    }

    override fun reset() {
        randomizationElementMap.clear()
        withdrawUserRandomness.clear()
        depositedEuroManager.clearDepositedEuros()
        setUp()
    }
}
