package nl.tudelft.trustchain.offlineeuro.entity

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
import it.unisa.dia.gas.jpbc.Element
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.tudelft.trustchain.offlineeuro.communication.ICommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.Schnorr
import nl.tudelft.trustchain.offlineeuro.db.WalletManager
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.UUID

import kotlinx.coroutines.*
import okhttp3.*

class User(
    name: String,
    group: BilinearGroup,
    context: Context?,
    private var walletManager: WalletManager? = null,
    communicationProtocol: ICommunicationProtocol,
    runSetup: Boolean = true,
    onDataChangeCallback: ((String?) -> Unit)? = null,
    val onRegister: (() -> Unit)? = null,
    transactionId: String? = "",
) : Participant(communicationProtocol, name, onDataChangeCallback) {
    val wallet: Wallet


    init {
        communicationProtocol.participant = this
        this.group = group

        if (runSetup) {
            setUp(transactionId!!)
        } else {
            generateKeyPair()
        }
        if (walletManager == null) {
            walletManager = WalletManager(context, group)
        }

        wallet = Wallet(privateKey, publicKey, walletManager!!)
    }

    fun sendDigitalEuroTo(nameReceiver: String): String {
        val randomizationElements = communicationProtocol.requestTransactionRandomness(nameReceiver, group)
        val transactionDetails =
            wallet.spendEuro(randomizationElements, group, crs)
                ?: throw Exception("No euro to spend")

        val result = communicationProtocol.sendTransactionDetails(nameReceiver, transactionDetails)
        onDataChangeCallback?.invoke(result)
        return result
    }

    fun doubleSpendDigitalEuroTo(nameReceiver: String): String {
        val randomizationElements = communicationProtocol.requestTransactionRandomness(nameReceiver, group)
        val transactionDetails = wallet.doubleSpendEuro(randomizationElements, group, crs)
        val result = communicationProtocol.sendTransactionDetails(nameReceiver, transactionDetails!!)
        onDataChangeCallback?.invoke(result)
        return result
    }

    fun withdrawDigitalEuro(bank: String): DigitalEuro {
        val serialNumber = UUID.randomUUID().toString()
        val firstT = group.getRandomZr()
        val tInv = firstT.mul(-1)
        val initialTheta = group.g.powZn(tInv).immutable

        val bytesToSign = serialNumber.toByteArray() + initialTheta.toBytes()

        val bankRandomness = communicationProtocol.getBlindSignatureRandomness(publicKey, bank, group)
        val bankPublicKey = communicationProtocol.getPublicKeyOf(bank, group)

        val blindedChallenge = Schnorr.createBlindedChallenge(bankRandomness, bytesToSign, bankPublicKey, group)
        val blindSignature = communicationProtocol.requestBlindSignature(publicKey, bank, blindedChallenge.blindedChallenge)
        val signature = Schnorr.unblindSignature(blindedChallenge, blindSignature)
        val digitalEuro = DigitalEuro(serialNumber, initialTheta, signature, arrayListOf())
        wallet.addToWallet(digitalEuro, firstT)
        onDataChangeCallback?.invoke("Withdrawn ${digitalEuro.serialNumber} successfully!")
        return digitalEuro
    }

    fun getBalance(): Int {
        return walletManager!!.getWalletEntriesToSpend().count()
    }

    fun onReceivedTTPRegisterReply() {
        Log.d("EUDI", "hello")
        onRegister!!.invoke()
    }

    override fun onReceivedTransaction(
        transactionDetails: TransactionDetails,
        publicKeyBank: Element,
        publicKeySender: Element
    ): String {
        Log.d("EUDI","got transaction")
        val usedRandomness = lookUpRandomness(publicKeySender) ?: return "Randomness Not found!"
        removeRandomness(publicKeySender)
        val transactionResult = Transaction.validate(transactionDetails, publicKeyBank, group, crs)

        if (transactionResult.valid) {
            wallet.addToWallet(transactionDetails, usedRandomness)
            onDataChangeCallback?.invoke("Received an euro from $publicKeySender")
            return transactionResult.description
        }
        onDataChangeCallback?.invoke(transactionResult.description)
        return transactionResult.description
    }

    override fun reset() {
        randomizationElementMap.clear()
        walletManager!!.clearWalletEntries()
        setUp()
    }
}
