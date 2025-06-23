package nl.tudelft.trustchain.offlineeuro.entity

import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic
import nl.tudelft.trustchain.offlineeuro.communication.ICommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.communication.IPV8CommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.community.OfflineEuroCommunity
import nl.tudelft.trustchain.offlineeuro.community.message.AddressMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BilinearGroupCRSReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BankRegistrationReplyMessage
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.CRSGenerator
import nl.tudelft.trustchain.offlineeuro.cryptography.PairingTypes
import nl.tudelft.trustchain.offlineeuro.cryptography.Schnorr
import nl.tudelft.trustchain.offlineeuro.cryptography.SchnorrSignature
import nl.tudelft.trustchain.offlineeuro.db.AddressBookManager
import nl.tudelft.trustchain.offlineeuro.db.DepositedEuroManager
import nl.tudelft.trustchain.offlineeuro.enums.Role
import nl.tudelft.trustchain.offlineeuro.libraries.SchnorrSignatureSerializer
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigInteger

class BankTest {
    private val ttpGroup = BilinearGroup(PairingTypes.FromFile)
    private val ttpPK = ttpGroup.generateRandomElementOfG()
    private val crs = CRSGenerator.generateCRSMap(ttpGroup, ttpPK).first
    private val depositedEuroManager = Mockito.mock(DepositedEuroManager::class.java)

    @Test
    fun initWithSetupTest() {

        val addressBookManager = Mockito.mock(AddressBookManager::class.java)
        val community = Mockito.mock(OfflineEuroCommunity::class.java)
        val communicationProtocol = IPV8CommunicationProtocol(addressBookManager, community)
        mockkStatic(Log::class)
        every { Log.d(any(),any()) } returns 0
        whenever(community.messageList).thenReturn(communicationProtocol.messageList)

        // Mock TTP signature for the bank
        val ttpPrivateKey = ttpGroup.getRandomZr()
        val bankSignature = Schnorr.schnorrSignature(
            ttpPrivateKey,
            "BankPublicKey".toByteArray(),
            ttpGroup
        )

        whenever(community.getGroupDescriptionAndCRS()).then {
            communicationProtocol.messageList.add(
                BilinearGroupCRSReplyMessage(
                    ttpGroup.toGroupElementBytes(),
                    crs.toCRSBytes(),
                    AddressMessage("TTP", Role.TTP, "SomeBytes".toByteArray(), "More Bytes".toByteArray())
                )
            )
        }
        val ttpAddress = Address("TTP", Role.TTP, ttpGroup.generateRandomElementOfG(), "More Bytes".toByteArray())

        val publicKeyCaptor = argumentCaptor<ByteArray>()

        whenever(addressBookManager.getAddressByName("TTP")).thenReturn(ttpAddress)
        whenever(community.registerAtTTP(any(), publicKeyCaptor.capture(), any(), any())).then {
            // Simulate receiving bank registration reply with signature
            communicationProtocol.messageList.add(
                BankRegistrationReplyMessage(
                    SchnorrSignatureSerializer.serializeSchnorrSignature(bankSignature)
                )
            )
        }

        val bankName = "SomeBank"
        val bank = Bank(bankName, BilinearGroup(PairingTypes.FromFile), communicationProtocol, null, depositedEuroManager)

        val capturedPKBytes = publicKeyCaptor.firstValue
        val capturedPK = ttpGroup.gElementFromBytes(capturedPKBytes)

        Assert.assertEquals(bankName, bank.name)
        Assert.assertEquals(ttpGroup, bank.group)
        Assert.assertEquals(crs, bank.crs)
        Assert.assertEquals(bank.publicKey, capturedPK)
        Assert.assertEquals(bank, communicationProtocol.participant)
        Assert.assertNotNull(bank.ttpSignatureOnPublicKey)
    }

    @Test
    fun initWithoutSetUpTest() {
        val addressBookManager = Mockito.mock(AddressBookManager::class.java)
        val community = Mockito.mock(OfflineEuroCommunity::class.java)
        val communicationProtocol = IPV8CommunicationProtocol(addressBookManager, community)

        val bankName = "SomeOtherBank"
        val group = BilinearGroup(PairingTypes.FromFile)
        val bank = Bank(bankName, group, communicationProtocol, null, depositedEuroManager, false)

        verify(community, never()).getGroupDescriptionAndCRS()
        verify(community, never()).registerAtTTP(any(), any(), any(), any())
        Assert.assertEquals(group, bank.group)

        Assert.assertThrows(UninitializedPropertyAccessException::class.java) {
            bank.crs.toCRSBytes()
        }
    }

    @Test
    fun getBlindSignatureRandomnessTest() {
        val bank = getBank()
        val publicKey = ttpGroup.generateRandomElementOfG()

        val firstRandomness = bank.getBlindSignatureRandomness(publicKey)
        val secondRandomness = bank.getBlindSignatureRandomness(publicKey)

        Assert.assertEquals("The same randomness should be returned", firstRandomness, secondRandomness)

        val newPublicKey = ttpGroup.generateRandomElementOfG()
        val thirdRandomness = bank.getBlindSignatureRandomness(newPublicKey)

        Assert.assertNotEquals(firstRandomness, thirdRandomness)
    }

    @Test
    fun getBlindSignatureTest() {
        val bank = getBank()
        val publicKey = ttpGroup.generateRandomElementOfG()
        val firstRandomness = bank.getBlindSignatureRandomness(publicKey)
        val elementToSign = ttpGroup.generateRandomElementOfG().immutable.toBytes()
        val serialNumber = "TestSerialNumber"
        val bytesToSign = serialNumber.toByteArray() + elementToSign

        val blindedChallenge = Schnorr.createBlindedChallenge(firstRandomness, bytesToSign, bank.publicKey, ttpGroup)
        val amount = 200L
        val response = bank.createBlindSignature(blindedChallenge.blindedChallenge, publicKey, amount, serialNumber)

        Assert.assertNotEquals("Should return valid response", BigInteger.ZERO, response.signature)
        Assert.assertTrue("Timestamp should be set", response.timestamp > 0)
        Assert.assertNotNull("Hash signature should be set", response.hashSignature)
        Assert.assertNotNull("Bank public key should be set", response.bankPublicKey)
        Assert.assertNotNull("Bank key signature should be set", response.bankKeySignature)
        Assert.assertNotNull("Amount signature should be set", response.amountSignature)

        // Verify the blind signature
        val blindSchnorrSignature = Schnorr.unblindSignature(blindedChallenge, response.signature)
        Assert.assertTrue(Schnorr.verifySchnorrSignature(blindSchnorrSignature, bank.publicKey, ttpGroup))

        // Test with no randomness requested
        val noRandomnessRequestedKey = ttpGroup.generateRandomElementOfG()
        val noRandomResponse = bank.createBlindSignature(blindedChallenge.blindedChallenge, noRandomnessRequestedKey, amount, serialNumber)
        Assert.assertEquals("There should be no randomness found", BigInteger.ZERO, noRandomResponse.signature)
    }

    fun getBank(): Bank {
        val addressBookManager = Mockito.mock(AddressBookManager::class.java)
        val community = Mockito.mock(OfflineEuroCommunity::class.java)
        val communicationProtocol = IPV8CommunicationProtocol(addressBookManager, community)

        val bankName = "Bank"
        val group = ttpGroup
        val bank = Bank(bankName, group, communicationProtocol, null, depositedEuroManager, false)
        bank.crs = crs
        bank.generateKeyPair()

        // Mock TTP signature
        bank.ttpSignatureOnPublicKey = Schnorr.schnorrSignature(
            group.getRandomZr(),
            bank.publicKey.toBytes(),
            group
        )

        return bank
    }
}
