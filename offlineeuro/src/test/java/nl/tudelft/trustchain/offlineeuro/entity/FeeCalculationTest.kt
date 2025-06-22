package nl.tudelft.trustchain.offlineeuro.entity

import android.util.Log
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.GrothSahaiProof
import nl.tudelft.trustchain.offlineeuro.cryptography.Schnorr
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito.mockStatic
import java.util.UUID


class FeeCalculationTest {
    private val group = BilinearGroup()
    private lateinit var logMock: MockedStatic<Log>

    @Before
    fun setUp() {
        // Mock the Log class before each test
        logMock = mockStatic(Log::class.java)
    }

    @After
    fun tearDown() {
        // Close the mock after each test
        logMock.close()
    }

    @Test
    fun testNoFeeForFirstThreeTransactions() {
        val digitalEuro = createDigitalEuro(200L, 0)
        val walletEntry = WalletEntry(digitalEuro, group.getRandomZr(), null)

        // First transaction - no fee
        Assert.assertEquals(0.0, walletEntry.calculateTransactionFee(), 0.001)
        Assert.assertEquals(199L, walletEntry.getValueAfterFee())

        // Add proofs to simulate transactions
        val euroWith1Proof = createDigitalEuro(200L, 1)
        val entry1 = WalletEntry(euroWith1Proof, group.getRandomZr(), null)
        Assert.assertEquals(0.0, entry1.calculateTransactionFee(), 0.001)
        Assert.assertEquals(199L, entry1.getValueAfterFee())

        val euroWith2Proofs = createDigitalEuro(200L, 2)
        val entry2 = WalletEntry(euroWith2Proofs, group.getRandomZr(), null)
        Assert.assertEquals(0.0, entry2.calculateTransactionFee(), 0.001)
        Assert.assertEquals(199L, entry2.getValueAfterFee())
    }

    @Test
    fun testFeeAfterThreeTransactions() {
        // Fourth transaction - 5% fee
        val euroWith3Proofs = createDigitalEuro(200L, 3)
        val entry3 = WalletEntry(euroWith3Proofs, group.getRandomZr(), null)
        Assert.assertEquals(0.05, entry3.calculateTransactionFee(), 0.001)
        Assert.assertEquals(189L, entry3.getValueAfterFee()) // 200 * 0.95

        // Fifth transaction - 10% fee
        val euroWith4Proofs = createDigitalEuro(200L, 4)
        val entry4 = WalletEntry(euroWith4Proofs, group.getRandomZr(), null)
        Assert.assertEquals(0.10, entry4.calculateTransactionFee(), 0.001)
        Assert.assertEquals(179L, entry4.getValueAfterFee()) // 200 * 0.90
    }

    @Test
    fun testTimeDegradation() {
        // Create a euro withdrawn 24 hours ago
        val oldTimestamp = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
        val digitalEuro = createDigitalEuro(200L, 0, oldTimestamp)
        val walletEntry = WalletEntry(digitalEuro, group.getRandomZr(), null)

        // Should have 5% fee for 24 hours
        val fee = walletEntry.calculateTransactionFee()
        Assert.assertTrue("Fee should be approximately 5%", fee >= 0.04 && fee <= 0.06)
        val value = walletEntry.getValueAfterFee()
        Assert.assertTrue("Value should be approximately 190", value >= 188L && value <= 192L)
    }

    @Test
    fun testCombinedFees() {
        // Create a euro withdrawn 48 hours ago with 4 transactions
        val oldTimestamp = System.currentTimeMillis() - (48 * 60 * 60 * 1000)
        val digitalEuro = createDigitalEuro(1000L, 4, oldTimestamp)
        val walletEntry = WalletEntry(digitalEuro, group.getRandomZr(), null)

        // Should have 10% time fee (2 days) + 10% transaction fee (2 transactions after first 3)
        val fee = walletEntry.calculateTransactionFee()
        Assert.assertTrue("Fee should be approximately 20%", fee >= 0.19 && fee <= 0.21)
        val value = walletEntry.getValueAfterFee()
        Assert.assertTrue("Value should be approximately 800", value >= 790L && value <= 810L)
    }

    @Test
    fun testTransactionValidateAmount() {
        val bankPrivateKey = group.getRandomZr()
        val bankPublicKey = group.g.powZn(bankPrivateKey)
        val ttpPrivateKey = group.getRandomZr()
        val ttpPublicKey = group.g.powZn(ttpPrivateKey)
        val crs = createMockCRS(ttpPublicKey)

        // Create a properly signed digital euro
        val amount = 500L
        val digitalEuro = createProperlySignedDigitalEuro(amount, bankPrivateKey, bankPublicKey, ttpPrivateKey, ttpPublicKey)

        // Test valid amount
        Assert.assertTrue("Amount validation should pass", Transaction.validateAmount(digitalEuro, crs, group))

        // Test with modified amount
        val tamperedEuro = digitalEuro.copy(amount = 1000L)
        Assert.assertFalse("Amount validation should fail for tampered amount", Transaction.validateAmount(tamperedEuro, crs, group))
    }

    @Test
    fun testTransactionValidateTimestampChain() {
        val bankPrivateKey = group.getRandomZr()
        val bankPublicKey = group.g.powZn(bankPrivateKey)
        val ttpPrivateKey = group.getRandomZr()
        val ttpPublicKey = group.g.powZn(ttpPrivateKey)
        val crs = createMockCRS(ttpPublicKey)

        val digitalEuro = createProperlySignedDigitalEuro(200L, bankPrivateKey, bankPublicKey, ttpPrivateKey, ttpPublicKey)

        // Test valid timestamp chain
        Assert.assertTrue("Timestamp validation should pass", Transaction.validateTimestampChain(digitalEuro, crs, group))

        // Test with tampered timestamp
        val tamperedEuro = digitalEuro.copy(withdrawalTimestamp = System.currentTimeMillis() + 1000000)
        Assert.assertFalse("Timestamp validation should fail for tampered timestamp", Transaction.validateTimestampChain(tamperedEuro, crs, group))
    }

    @Test
    fun testTransferCountCalculation() {
        val euro0 = createDigitalEuro(100L, 0)
        Assert.assertEquals(0, Transaction.calculateTransferCount(euro0))

        val euro5 = createDigitalEuro(100L, 5)
        Assert.assertEquals(5, Transaction.calculateTransferCount(euro5))

        val euro10 = createDigitalEuro(100L, 10)
        Assert.assertEquals(10, Transaction.calculateTransferCount(euro10))
    }

    private fun createDigitalEuro(amount: Long, proofCount: Int, timestamp: Long = System.currentTimeMillis()): DigitalEuro {
        val proofs = ArrayList<GrothSahaiProof>()
        for (i in 0 until proofCount) {
            proofs.add(createMockProof())
        }

        val privateKey = group.getRandomZr()
        val publicKey = group.g.powZn(privateKey)
        val signature = Schnorr.schnorrSignature(privateKey, amount.toString().toByteArray(), group)

        return DigitalEuro(
            UUID.randomUUID().toString(),
            amount,
            group.generateRandomElementOfG(),
            signature,
            proofs,
            timestamp,
            signature, // Mock hash signature
            publicKey,
            signature, // Mock bank key signature
            signature  // Mock amount signature
        )
    }

    private fun createProperlySignedDigitalEuro(
        amount: Long,
        bankPrivateKey: it.unisa.dia.gas.jpbc.Element,
        bankPublicKey: it.unisa.dia.gas.jpbc.Element,
        ttpPrivateKey: it.unisa.dia.gas.jpbc.Element,
        ttpPublicKey: it.unisa.dia.gas.jpbc.Element
    ): DigitalEuro {
        val serialNumber = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        val firstTheta1 = group.generateRandomElementOfG()

        // Sign serial number and theta
        val signature = Schnorr.schnorrSignature(
            bankPrivateKey,
            serialNumber.toByteArray() + firstTheta1.toBytes(),
            group
        )

        // Sign amount
        val amountSignature = Schnorr.schnorrSignature(
            bankPrivateKey,
            amount.toString().toByteArray(Charsets.UTF_8),
            group
        )

        // Sign hash
        val hashString = "$serialNumber | $amount | $timestamp"
        val hashSignature = Schnorr.schnorrSignature(
            bankPrivateKey,
            hashString.hashCode().toString().toByteArray(Charsets.UTF_8),
            group
        )

        // TTP signs bank's public key
        val bankKeySignature = Schnorr.schnorrSignature(
            ttpPrivateKey,
            bankPublicKey.toBytes(),
            group
        )

        return DigitalEuro(
            serialNumber,
            amount,
            firstTheta1,
            signature,
            arrayListOf(),
            timestamp,
            hashSignature,
            bankPublicKey,
            bankKeySignature,
            amountSignature
        )
    }

    private fun createMockCRS(ttpPublicKey: it.unisa.dia.gas.jpbc.Element): nl.tudelft.trustchain.offlineeuro.cryptography.CRS {
        return nl.tudelft.trustchain.offlineeuro.cryptography.CRS(
            group.generateRandomElementOfG(),
            group.generateRandomElementOfG(),
            group.generateRandomElementOfG(),
            group.generateRandomElementOfG(),
            group.generateRandomElementOfH(),
            group.generateRandomElementOfH(),
            group.generateRandomElementOfH(),
            group.generateRandomElementOfH(),
            ttpPublicKey
        )
    }

    private fun createMockProof(): GrothSahaiProof {
        return GrothSahaiProof(
            group.generateRandomElementOfG(),
            group.generateRandomElementOfG(),
            group.generateRandomElementOfH(),
            group.generateRandomElementOfH(),
            group.generateRandomElementOfG(),
            group.generateRandomElementOfG(),
            group.generateRandomElementOfH(),
            group.generateRandomElementOfH(),
            group.generateRandomElementOfGT()
        )
    }
}
