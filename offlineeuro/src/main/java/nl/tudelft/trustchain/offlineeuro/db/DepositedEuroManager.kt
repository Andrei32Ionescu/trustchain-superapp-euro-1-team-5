package nl.tudelft.trustchain.offlineeuro.db

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import nl.tudelft.offlineeuro.sqldelight.Database
import nl.tudelft.offlineeuro.sqldelight.DepositedEurosQueries
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.entity.DigitalEuro

class DepositedEuroManager(
    context: Context?,
    group: BilinearGroup,
    driver: SqlDriver = AndroidSqliteDriver(Database.Schema, context!!, "deposited_euros.db"),
) : OfflineEuroManager(group, driver) {
    private val queries: DepositedEurosQueries = database.depositedEurosQueries
    private val digitalEuroMapper = {
            serialNumber: String,
            amount: Long,
            firstTheta: ByteArray,
            signature: ByteArray,
            previousProofs: ByteArray?,
            timestamp: Long,
            timestampSignature: ByteArray,
            bankPublicKey: ByteArray,
            bankKeySignature: ByteArray,
        ->
        DigitalEuro(
            serialNumber,
            amount,
            group.gElementFromBytes(firstTheta),
            deserializeSchnorr(signature)!!,
            deserializeGSP(previousProofs),
            timestamp,
            deserializeSchnorr(timestampSignature)!!,
            group.gElementFromBytes(bankPublicKey),
            deserializeSchnorr(bankKeySignature)!!,
        )
    }

    init {
        queries.createDepositedEurosTable()
        queries.clearDepositedEurosTable()
    }

    fun insertDigitalEuro(digitalEuro: DigitalEuro) {
        val (serialNumber, amount,firstTheta1, signature, proofs, timestamp, timestampSignature, bankPublicKey, bankKeySignature) = digitalEuro
        queries.insertDepositedEuro(
            serialNumber,
            amount,
            firstTheta1.toBytes(),
            serialize(signature)!!,
            serialize(proofs),
            timestamp,
            serialize(timestampSignature)!!,
            bankPublicKey.toBytes(),
            serialize(bankKeySignature)!!
        )
    }

    fun getDigitalEurosByDescriptor(digitalEuro: DigitalEuro): List<DigitalEuro> {
        val (serialNumber,amount, firstTheta1, signature, _, timestamp, timestampSignature, bankPublicKey, bankKeySignature) = digitalEuro
        return queries.getDepositedEuroByDescriptor(
            serialNumber,
            amount,
            firstTheta1.toBytes(),
            serialize(signature)!!,
            timestamp,
            serialize(timestampSignature)!!,
            bankPublicKey.toBytes(),
            serialize(bankKeySignature)!!,
            digitalEuroMapper
        ).executeAsList()
    }

    fun getAllDepositedEuros(): List<DigitalEuro> {
        return queries.getDepositedEuros(digitalEuroMapper).executeAsList()
    }

    fun clearDepositedEuros() {
        return queries.clearDepositedEurosTable()
    }
}
