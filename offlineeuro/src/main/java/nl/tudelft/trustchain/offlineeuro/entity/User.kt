package nl.tudelft.trustchain.offlineeuro.entity

import android.content.Context

import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContentProviderCompat.requireContext
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
import nl.tudelft.trustchain.offlineeuro.enums.Role
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
    val onReqUserVerif: ((String, String) -> Unit)? = null,
    transactionId: String? = "",
) : Participant(communicationProtocol, name, onDataChangeCallback, Role.User) {
    val wallet: Wallet


    init {
        communicationProtocol.participant = this
        this.group = group

        if (runSetup) {
            setUp()
        } else {
            generateKeyPair()
        }
        if (walletManager == null) {
            walletManager = WalletManager(context, group)
        }

        wallet = Wallet(privateKey, publicKey, walletManager!!)
    }

    // Send a specific digital euro to a receiver
    fun sendSpecificDigitalEuroTo(digitalEuro: DigitalEuro, nameReceiver: String): String {
        val randomizationElements = communicationProtocol.requestTransactionRandomness(nameReceiver, group)
        var deposit = false
        if (nameReceiver.lowercase().contains("bank")) {
            deposit = true
        }
        val transactionDetails = wallet.spendSpecificEuro(digitalEuro, randomizationElements, group, crs, deposit)
            ?: throw Exception("Cannot spend this specific euro")

        Log.d("TransactionDetails", transactionDetails.toString())
        val result = communicationProtocol.sendTransactionDetails(nameReceiver, transactionDetails)
        onDataChangeCallback?.invoke(result)
        return result
    }
    // Double spend a specific digital euro to a receiver
    fun doubleSpendSpecificDigitalEuroTo(digitalEuro: DigitalEuro, nameReceiver: String): String {
        val randomizationElements = communicationProtocol.requestTransactionRandomness(nameReceiver, group)
        var deposit = false
        if (nameReceiver.lowercase().contains("bank")) {
            deposit = true
        }
        val transactionDetails = wallet.doubleSpendSpecificEuro(digitalEuro, randomizationElements, group, crs, deposit)
            ?: throw Exception("Cannot double spend this specific euro")
        val result = communicationProtocol.sendTransactionDetails(nameReceiver, transactionDetails)
        onDataChangeCallback?.invoke(result)
        return result
    }

    fun sendDigitalEuroTo(nameReceiver: String): String {
        val randomizationElements = communicationProtocol.requestTransactionRandomness(nameReceiver, group)
        var deposit = false
        if (nameReceiver.lowercase().contains("bank")) {
            deposit = true
        }
        val transactionDetails =
            wallet.spendEuro(randomizationElements, group, crs, deposit)
                ?: throw Exception("No euro to spend")

        val result = communicationProtocol.sendTransactionDetails(nameReceiver, transactionDetails)
        onDataChangeCallback?.invoke(result)
        return result
    }

    fun doubleSpendDigitalEuroTo(nameReceiver: String): String {
        val randomizationElements = communicationProtocol.requestTransactionRandomness(nameReceiver, group)
        var deposit = false
        if (nameReceiver.lowercase().contains("bank")) {
            deposit = true
        }
        val transactionDetails = wallet.doubleSpendEuro(randomizationElements, group, crs, deposit)
        val result = communicationProtocol.sendTransactionDetails(nameReceiver, transactionDetails!!)
        onDataChangeCallback?.invoke(result)
        return result
    }

    fun withdrawDigitalEuro(bank: String, amount: Long): DigitalEuro {
        if (amount <= 0.0) {
            throw IllegalArgumentException("Amount must be positive")
        }

        val serialNumber = UUID.randomUUID().toString()
        val firstT = group.getRandomZr()
        val tInv = firstT.mul(-1)
        val initialTheta = group.g.powZn(tInv).immutable

        val bytesToSign = serialNumber.toByteArray() + initialTheta.toBytes()


        val bankRandomness = communicationProtocol.getBlindSignatureRandomness(publicKey, bank, group)
        val bankPublicKey = communicationProtocol.getPublicKeyOf(bank, group)

        val blindedChallenge = Schnorr.createBlindedChallenge(bankRandomness, bytesToSign, bankPublicKey, group)

        val response = communicationProtocol.requestBlindSignature(
            publicKey,
            bank,
            blindedChallenge.blindedChallenge,
            amount,
            serialNumber
        )

        val signature = Schnorr.unblindSignature(blindedChallenge, response.signature)

        val digitalEuro = DigitalEuro(
            serialNumber,
            amount,
            initialTheta,
            signature,
            arrayListOf(),
            response.timestamp,
            response.hashSignature,
            group.gElementFromBytes(response.bankPublicKey),
            response.bankKeySignature,
            response.amountSignature
        )

        wallet.addToWallet(digitalEuro, firstT)
        onDataChangeCallback?.invoke("Withdrawn €${amount.toFloat()/100.0} successfully!")
        return digitalEuro
    }

    fun getBalance(): Long {
        return walletManager!!.getWalletEntriesToSpend().sumOf {
            it.digitalEuro.amount
        }
    }

    fun onReceivedRequestUserVerification(deeplink: String, transactionId: String) {
        Log.d("EUDI", "We are inside user class after having received user verification request")
        onReqUserVerif!!.invoke(deeplink, transactionId)
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
        val usedRandomness = lookUpRandomness(publicKeySender) ?: return "Randomness Not found!"
        removeRandomness(publicKeySender)
        val transactionResult = Transaction.validate(transactionDetails, publicKeyBank, group, crs, isDeposit = false)
        if (transactionResult.valid) {
            wallet.addToWallet(transactionDetails, usedRandomness)
            val amount = transactionDetails.digitalEuro.amount
            onDataChangeCallback?.invoke("Received ${amount.toFloat()/100.0} euro from $publicKeySender")
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
