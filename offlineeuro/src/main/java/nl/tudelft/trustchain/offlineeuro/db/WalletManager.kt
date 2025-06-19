package nl.tudelft.trustchain.offlineeuro.db

import android.content.Context
import android.util.Log
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import nl.tudelft.offlineeuro.sqldelight.Database
import nl.tudelft.offlineeuro.sqldelight.WalletQueries
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.entity.DigitalEuro
import nl.tudelft.trustchain.offlineeuro.entity.RegisteredUser
import nl.tudelft.trustchain.offlineeuro.entity.WalletEntry

class WalletManager(
    context: Context?,
    private val group: BilinearGroup,
    driver: SqlDriver = AndroidSqliteDriver(Database.Schema, context!!, "wallet.db"),
) : OfflineEuroManager(group, driver) {
    private val queries: WalletQueries = database.walletQueries
    private val walletEntryMapper = {
            serialNumber: String,
            amount: Long,
            firstTheta: ByteArray,
            signature: ByteArray,
            previousProofs: ByteArray?,
            secretT: ByteArray,
            transactionSignature: ByteArray?,
            timesSpent: Long,
            timestamp: Long,
            hashSignature: ByteArray,
            bankPublicKey: ByteArray,
            bankKeySignature: ByteArray,
            amountSignature: ByteArray
        ->
        WalletEntry(
            DigitalEuro(
                serialNumber,
                amount,
                group.gElementFromBytes(firstTheta),
                deserializeSchnorr(signature)!!,
                deserializeGSP(previousProofs),
                timestamp,
                deserializeSchnorr(hashSignature)!!,
                group.gElementFromBytes(bankPublicKey),
                deserializeSchnorr(bankKeySignature)!!,
                deserializeSchnorr(amountSignature)!!
            ),
            group.zrElementFromBytes(secretT),
            deserializeSchnorr(transactionSignature),
            timesSpent,
            timestamp
            )
    }

    /**
     * Creates the RegisteredUser table if it not yet exists.
     */
    init {
        queries.createWalletTable()
        queries.clearWalletTable()
    }

    /**
     * Tries to add a new [RegisteredUser] to the table.
     *
     * @param user the user that should be registered. Its id will be omitted.
     * @return true iff registering the user is successful.
     */
    fun insertWalletEntry(walletEntry: WalletEntry): Boolean {
        val digitalEuro = walletEntry.digitalEuro

        queries.insertWalletEntry(
            digitalEuro.serialNumber,
            digitalEuro.amount,
            digitalEuro.firstTheta1.toBytes(),
            serialize(digitalEuro.signature)!!,
            serialize(digitalEuro.proofs),
            walletEntry.t.toBytes(),
            serialize(walletEntry.transactionSignature),
            digitalEuro.withdrawalTimestamp,
            serialize(digitalEuro.hashSignature)!!,
            digitalEuro.bankPublicKey.toBytes(),
            serialize(digitalEuro.bankKeySignature)!!,
            serialize(digitalEuro.amountSignature)!!
        )
        return true
    }

    fun getAllDigitalWalletEntries(): List<WalletEntry> {
        return queries.getAllWalletEntries(walletEntryMapper).executeAsList()
    }

    fun getWalletEntriesToSpend(): List<WalletEntry> {
        return queries.getWalletEntriesToSpend(walletEntryMapper).executeAsList()
    }

    fun getNumberOfWalletEntriesToSpend(number: Int): List<WalletEntry> {
        return queries.getNumberOfWalletEntriesToSpend(number.toLong(), walletEntryMapper).executeAsList()
    }

    fun getNumberOfWalletEntriesToDoubleSpend(number: Int): List<WalletEntry> {
        return queries.getNumberOfWalletEntriesToDoubleSpend(number.toLong(), walletEntryMapper).executeAsList()
    }

    fun getWalletEntriesToDoubleSpend(): List<WalletEntry> {
        return queries.getWalletEntriesToDoubleSpend(walletEntryMapper).executeAsList()
    }

    fun incrementTimesSpent(digitalEuro: DigitalEuro) {
        queries.incrementTimesSpent(
            digitalEuro.serialNumber,
            digitalEuro.firstTheta1.toBytes(),
            serialize(digitalEuro.signature)!!,
            serialize(digitalEuro.proofs)
        )
    }

    // A user stores every token they ever receive, which is used to implement double spending functionality to see if the bank can detect it.
    // It is possibly also utilized on the user's device for them to detect double spending at deposit time.
    // If it occurs that a user receives the same token they sent out previously, and its value is unchanged given a different fee function,
    // Then we must query the token with the last received timestamp, as otherwise the DB would return multiple tokens.
    fun getWalletEntryByDigitalEuro(digitalEuro: DigitalEuro): WalletEntry? {
        return queries.getWalletEntryByDescriptor(
            digitalEuro.serialNumber,
            digitalEuro.firstTheta1.toBytes(),
            serialize(digitalEuro.signature)!!,
            digitalEuro.amount,
            walletEntryMapper
        ).executeAsList().sortedByDescending { entry -> entry.receivedTimestamp }.firstOrNull()
    }

    fun clearWalletEntries() {
        queries.clearWalletTable()
    }

}
