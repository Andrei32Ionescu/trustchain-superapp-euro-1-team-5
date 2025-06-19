package nl.tudelft.trustchain.offlineeuro.entity

import android.util.Log
import it.unisa.dia.gas.jpbc.Element
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.CRS
import nl.tudelft.trustchain.offlineeuro.cryptography.RandomizationElements
import nl.tudelft.trustchain.offlineeuro.cryptography.SchnorrSignature
import nl.tudelft.trustchain.offlineeuro.db.WalletManager
import java.io.Console

data class WalletEntry(
    val digitalEuro: DigitalEuro,
    val t: Element,
    val transactionSignature: SchnorrSignature?,
    val timesSpent: Long = 0,
    val receivedTimestamp: Long = System.currentTimeMillis(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as WalletEntry
        return this.digitalEuro == other.digitalEuro &&
            this.t == other.t &&
            this.transactionSignature == other.transactionSignature &&
            this.timesSpent == other.timesSpent &&
            this.receivedTimestamp == other.receivedTimestamp
    }

    fun calculateTransactionFee(): Double {
        val currentTime = System.currentTimeMillis()
        val referenceTimestamp = digitalEuro.withdrawalTimestamp
        val timePassed = (currentTime - referenceTimestamp) / (1000 * 60 * 60)

        val currentTransferCount = calculateTransferCount()

        var fee = (timePassed / 24) * 0.005

        // No fee for the first 3 transactions
        if (currentTransferCount > 2) {
            fee += (currentTransferCount - 2) * 0.05
        }

        return fee
    }

    fun getValueAfterFee(): Long {
        val fee = calculateTransactionFee()
        val left = (String(digitalEuro.amountSignature.signedMessage, Charsets.UTF_8).toFloat() * (1.00-fee)).toLong()

        return left
    }

    fun calculateTransferCount(): Int {
        return this.digitalEuro.proofs.size
    }
}

class Wallet(
    private val privateKey: Element,
    val publicKey: Element,
    private val walletManager: WalletManager
) {
    fun addToWallet(
        transactionDetails: TransactionDetails,
        t: Element
    ) {
        val digitalEuro = transactionDetails.digitalEuro
        digitalEuro.proofs.add(transactionDetails.currentTransactionProof.grothSahaiProof)

        val transactionSignature = transactionDetails.theta1Signature
        val walletEntry = WalletEntry(digitalEuro, t, transactionSignature)
        walletManager.insertWalletEntry(walletEntry)
    }

    fun addToWallet(
        digitalEuro: DigitalEuro,
        t: Element
    ) {
        walletManager.insertWalletEntry(WalletEntry(digitalEuro, t, null))
    }

    fun getWalletEntryToSpend(): WalletEntry? {
        return walletManager.getNumberOfWalletEntriesToSpend(1).firstOrNull()
    }

    fun getAllWalletEntriesToSpend(): List<WalletEntry> {
        return walletManager.getWalletEntriesToSpend()
    }
    fun getAllWalletEntriesToDoubleSpend(): List<WalletEntry>{
        return walletManager.getWalletEntriesToDoubleSpend()
    }
    // Spend a specific wallet entry by its digital euro
    fun spendSpecificEuro(
        digitalEuro: DigitalEuro,
        randomizationElements: RandomizationElements,
        bilinearGroup: BilinearGroup,
        crs: CRS,
        deposit: Boolean
    ): TransactionDetails? {
        val walletEntry = walletManager.getWalletEntryByDigitalEuro(digitalEuro) ?: return null

        // Check if this token is spendable (not already spent)
        if (walletEntry.timesSpent > 0) return null

        Log.d("Wallet", "Spending euro with times spent: ${walletEntry.timesSpent}")
        println("Spending euro with times spent: ${walletEntry.timesSpent}")
        walletManager.incrementTimesSpent(digitalEuro)

        if (deposit == true) {
            return Transaction.createTransaction(privateKey, publicKey, walletEntry, randomizationElements, bilinearGroup, crs)
        }

        // Calculate the fee and update the value
        val valueAfterFee = walletEntry.getValueAfterFee()

        // Create a new digital euro with the updated value
        val updatedEuro = digitalEuro.copy(amount = valueAfterFee)

        val updatedEntry = walletEntry.copy(digitalEuro = updatedEuro)

        return Transaction.createTransaction(privateKey, publicKey, updatedEntry, randomizationElements, bilinearGroup, crs)
    }

    // Double spend a specific wallet entry by its digital euro
    fun doubleSpendSpecificEuro(
        digitalEuro: DigitalEuro,
        randomizationElements: RandomizationElements,
        bilinearGroup: BilinearGroup,
        crs: CRS,
        deposit: Boolean
    ): TransactionDetails? {
        val walletEntry = walletManager.getWalletEntryByDigitalEuro(digitalEuro) ?: return null
        // Check if this token can be double spent (spent exactly once)
        if (walletEntry.timesSpent != 1L) return null

        walletManager.incrementTimesSpent(digitalEuro)

        if (deposit == true) {
            return Transaction.createTransaction(privateKey, publicKey, walletEntry, randomizationElements, bilinearGroup, crs)
        }

        // Calculate the fee and update the value
        val valueAfterFee = walletEntry.getValueAfterFee()

        // Create a new digital euro with the updated value
        val updatedEuro = digitalEuro.copy(amount = valueAfterFee)
        val updatedEntry = walletEntry.copy( digitalEuro = updatedEuro )

        return Transaction.createTransaction(privateKey, publicKey, updatedEntry, randomizationElements, bilinearGroup, crs)
    }

    fun spendEuro(
        randomizationElements: RandomizationElements,
        bilinearGroup: BilinearGroup,
        crs: CRS,
        deposit: Boolean
    ): TransactionDetails? {
        val walletEntry = walletManager.getNumberOfWalletEntriesToSpend(1).firstOrNull() ?: return null
        val euro = walletEntry.digitalEuro
        walletManager.incrementTimesSpent(euro)

        if (deposit == true) {
            return Transaction.createTransaction(privateKey, publicKey, walletEntry, randomizationElements, bilinearGroup, crs)
        }

        // Calculate the fee and update the value
        val valueAfterFee = walletEntry.getValueAfterFee()

        // Create a new digital euro with the updated value
        val updatedEuro = euro.copy(amount = valueAfterFee)
        val updatedEntry = walletEntry.copy( digitalEuro = updatedEuro )

        return Transaction.createTransaction(privateKey, publicKey, updatedEntry, randomizationElements, bilinearGroup, crs)
    }

    fun doubleSpendEuro(
        randomizationElements: RandomizationElements,
        bilinearGroup: BilinearGroup,
        crs: CRS,
        deposit: Boolean
    ): TransactionDetails? {
        val walletEntry = walletManager.getNumberOfWalletEntriesToDoubleSpend(1).firstOrNull() ?: return null
        val euro = walletEntry.digitalEuro
        walletManager.incrementTimesSpent(euro)

        if (deposit == true) {
            return Transaction.createTransaction(privateKey, publicKey, walletEntry, randomizationElements, bilinearGroup, crs)
        }

        val valueAfterFee = walletEntry.getValueAfterFee()

        // Create a new digital euro with the updated value
        val updatedEuro = euro.copy(amount = valueAfterFee)
        val updatedEntry = walletEntry.copy( digitalEuro = updatedEuro )

        return Transaction.createTransaction(privateKey, publicKey, updatedEntry, randomizationElements, bilinearGroup, crs)
    }
}
