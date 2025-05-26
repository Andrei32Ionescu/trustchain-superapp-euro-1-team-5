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
            originalAmount: Long,
            amount: Long,
            firstTheta: ByteArray,
            signature: ByteArray,
            previousProofs: ByteArray?,
        ->
        DigitalEuro(
            serialNumber,
            originalAmount,
            amount,
            group.gElementFromBytes(firstTheta),
            deserializeSchnorr(signature)!!,
            deserializeGSP(previousProofs)
        )
    }

    init {
        queries.createDepositedEurosTable()
        queries.clearDepositedEurosTable()
    }

    fun insertDigitalEuro(digitalEuro: DigitalEuro) {
        val (serialNumber, originalAmount, amount, firstTheta1, signature, proofs) = digitalEuro
        queries.insertDepositedEuro(
            serialNumber,
            originalAmount,
            amount,
            firstTheta1.toBytes(),
            serialize(signature)!!,
            serialize(proofs)
        )
    }

    fun getDigitalEurosByDescriptor(digitalEuro: DigitalEuro): List<DigitalEuro> {
        val (serialNumber, originalAmount, amount, firstTheta1, signature, _) = digitalEuro
        return queries.getDepositedEuroByDescriptor(
            serialNumber,
            originalAmount,     // Use original amount since it is signed
            firstTheta1.toBytes(),
            serialize(signature)!!,
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
