package nl.tudelft.trustchain.offlineeuro.communication

import android.util.Log
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import it.unisa.dia.gas.jpbc.Element
import nl.tudelft.ipv8.Peer
import nl.tudelft.offlineeuro.sqldelight.Database
import nl.tudelft.trustchain.offlineeuro.community.OfflineEuroCommunity
import nl.tudelft.trustchain.offlineeuro.community.message.AddressMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BilinearGroupCRSReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BilinearGroupCRSRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BlindSignatureRandomnessReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BlindSignatureRandomnessRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BlindSignatureReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BlindSignatureRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.TransactionMessage
import nl.tudelft.trustchain.offlineeuro.community.message.TransactionRandomizationElementsReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.TransactionRandomizationElementsRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.TransactionResultMessage
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.CRSGenerator
import nl.tudelft.trustchain.offlineeuro.cryptography.GrothSahai
import nl.tudelft.trustchain.offlineeuro.cryptography.PairingTypes
import nl.tudelft.trustchain.offlineeuro.cryptography.Schnorr
import nl.tudelft.trustchain.offlineeuro.db.AddressBookManager
import nl.tudelft.trustchain.offlineeuro.entity.Address
import nl.tudelft.trustchain.offlineeuro.entity.Bank
import nl.tudelft.trustchain.offlineeuro.entity.TTP
import nl.tudelft.trustchain.offlineeuro.entity.TransactionDetails
import nl.tudelft.trustchain.offlineeuro.entity.TransactionDetailsBytes
import nl.tudelft.trustchain.offlineeuro.entity.User
import nl.tudelft.trustchain.offlineeuro.enums.Role
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.math.BigInteger
import java.util.UUID

class IPV8CommunicationProtocolTest {
    private val context = null
    private val driver =
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).apply {
            Database.Schema.create(this)
        }

    private val community: OfflineEuroCommunity = Mockito.mock(OfflineEuroCommunity::class.java)

    // Setup the TTP
    private val groupDescription = BilinearGroup(PairingTypes.FromFile)
    private val ttpPK = groupDescription.generateRandomElementOfG()
    private val ttpCRS = CRSGenerator.generateCRSMap(groupDescription, ttpPK)
    private val ttpAddress = Address("TTP", Role.Bank, ttpPK, "TTPPublicKey".toByteArray())

    // Set up bank
    private val bankPK = groupDescription.generateRandomElementOfG()
    private val bankPrivateKey = groupDescription.getRandomZr()
    private val bankAddress = Address("Bank", Role.Bank, bankPK, "BankPublicKey".toByteArray())
    private val blindSignatureRandomness = groupDescription.generateRandomElementOfG()

    // Create proper response
    private val ttpPrivateKey = groupDescription.getRandomZr()
    private val bankKeySignature = Schnorr.schnorrSignature(ttpPrivateKey, bankPK.toBytes(), groupDescription)
    private val amount = 200L
    private val serialNumber = UUID.randomUUID().toString()
    private val timestamp = System.currentTimeMillis()
    private val amountSignature = Schnorr.schnorrSignature(bankPrivateKey, amount.toString().toByteArray(), groupDescription)
    private val hashString = "$serialNumber | $amount | $timestamp"
    private val hashSignature = Schnorr.schnorrSignature(bankPrivateKey, hashString.hashCode().toString().toByteArray(), groupDescription)

    private val blindSignatureResponse = ICommunicationProtocol.BlindSignatureResponse(
        BigInteger("1236231234124321421"),
        timestamp,
        hashSignature,
        bankPK.toBytes(),
        bankKeySignature,
        amountSignature
    )

    // Set up receiver
    private val receiverPK = groupDescription.generateRandomElementOfG()
    private val receiverAddress = Address("Receiver", Role.User, receiverPK, "UserPublicKey".toByteArray())
    private val randomizationElements = GrothSahai.tToRandomizationElements(groupDescription.getRandomZr(), groupDescription, ttpCRS.first)
    private val transactionResult = "Successful Transaction!"

    private val initialBilinearGroup = BilinearGroup(PairingTypes.FromFile)
    private val addressBookManager = AddressBookManager(context, initialBilinearGroup, driver)

    private val userRandomness: HashMap<Element, Element> = hashMapOf()

    private val receivingPeer = Mockito.mock(Peer::class.java)

    private val iPV8CommunicationProtocol = IPV8CommunicationProtocol(addressBookManager, community)
    private lateinit var logMock: MockedStatic<Log>

    @Before
    fun setup() {
        `when`(community.messageList).thenReturn(iPV8CommunicationProtocol.messageList)
        val ttpAddressMessage = AddressMessage(ttpAddress.name, ttpAddress.type, ttpAddress.publicKey.toBytes(), ttpAddress.peerPublicKey!!)
        `when`(community.getGroupDescriptionAndCRS()).then {
            val message = BilinearGroupCRSReplyMessage(groupDescription.toGroupElementBytes(), ttpCRS.first.toCRSBytes(), ttpAddressMessage)
            community.messageList.add(message)
        }

        `when`(community.sendGroupDescriptionAndCRS(any(), any(), any(), any())).then { }

        `when`(community.registerAtTTP(any(), any(), any(), any())).then { }

        `when`(community.sendBlindSignatureRandomnessReply(any(), any())).then { }
        `when`(community.sendBlindSignature(any(), any())).then { }

        `when`(community.getBlindSignatureRandomness(any(), any())).then {
            val message = BlindSignatureRandomnessReplyMessage(blindSignatureRandomness.toBytes())
            community.messageList.add(message)
        }

        `when`(community.getBlindSignature(any(), any(), any(), any(), any())).then {
            val message = BlindSignatureReplyMessage(
                blindSignatureResponse.signature,
                blindSignatureResponse.timestamp,
                blindSignatureResponse.hashSignature,
                blindSignatureResponse.bankPublicKey,
                blindSignatureResponse.bankKeySignature,
                blindSignatureResponse.amountSignature
            )
            community.messageList.add(message)
        }

        `when`(community.getTransactionRandomizationElements(any(), any())).then {
            val message = TransactionRandomizationElementsReplyMessage(randomizationElements.toRandomizationElementsBytes())
            community.messageList.add(message)
        }

        `when`(community.sendTransactionDetails(any(), any(), any())).then {
            val message = TransactionResultMessage(transactionResult)
            community.messageList.add(message)
        }

        // Mock the Log class before each test
        logMock = mockStatic(Log::class.java)
    }

    @After
    fun tearDown() {
        // Close the mock after each test
        logMock.close()
    }

    @Test
    fun getGroupDescriptionAndCRSTest() {
        val ttp = Mockito.mock(TTP::class.java)
        iPV8CommunicationProtocol.participant = ttp
        `when`(ttp.group).thenReturn(groupDescription)
        `when`(ttp.crs).thenReturn(ttpCRS.first)

        iPV8CommunicationProtocol.getGroupDescriptionAndCRS()
        val groupDescription = ttp.group
        val crs = ttp.crs
        Assert.assertEquals(this.groupDescription.pairing, groupDescription.pairing)
        Assert.assertEquals(this.groupDescription.g, groupDescription.g)
        Assert.assertEquals(this.groupDescription.h, groupDescription.h)
        Assert.assertEquals(this.groupDescription.gt, groupDescription.gt)
        Assert.assertEquals("The crs should be correct", ttpCRS.first, crs)
    }

    @Test
    fun sendBilinearGroupAndCRSTest() {
        val message = BilinearGroupCRSRequestMessage(receivingPeer)

        val ttp = Mockito.mock(TTP::class.java)
        iPV8CommunicationProtocol.participant = ttp

        `when`(ttp.group).thenReturn(groupDescription)
        `when`(ttp.crs).thenReturn(ttpCRS.first)
        `when`(ttp.publicKey).thenReturn(ttpPK)

        val expectedGroupElements = groupDescription.toGroupElementBytes()
        val expectedCRSBytes = ttpCRS.first.toCRSBytes()

        community.messageList.add(message)
        verify(community, times(1))
            .sendGroupDescriptionAndCRS(expectedGroupElements, expectedCRSBytes, ttpAddress.publicKey.toBytes(), receivingPeer)
    }

    @Test
    fun registrationTest() {
        val publicKey = groupDescription.generateRandomElementOfG()
        val userName = "UserTryingToRegister"
        val participant = Mockito.mock(User::class.java)
        val txId = "mockId"
        `when`(participant.publicKey).thenReturn(publicKey)
        `when`(participant.name).thenReturn(userName)
        `when`(participant.group).thenReturn(groupDescription)
        `when`(participant.role).thenReturn(Role.User)

        iPV8CommunicationProtocol.participant = participant
        iPV8CommunicationProtocol.messageList.add(
            AddressMessage(ttpAddress.name, ttpAddress.type, ttpAddress.publicKey.toBytes(), ttpAddress.peerPublicKey!!)
        )
        iPV8CommunicationProtocol.register(userName, publicKey, ttpAddress.name, Role.User)
        // Assert that the registration request is sent correctly
        verify(community, times(1)).registerAtTTP(userName, publicKey.toBytes(), ttpAddress.peerPublicKey!!, Role.User)
    }

    @Test
    fun getBlindSignatureRandomnessTest() {
        addressBookManager.insertAddress(bankAddress)
        val publicKey = groupDescription.generateRandomElementOfG()
        val randomness = iPV8CommunicationProtocol.getBlindSignatureRandomness(publicKey, bankAddress.name, groupDescription)
        verify(community, times(1)).getBlindSignatureRandomness(publicKey.toBytes(), bankAddress.peerPublicKey!!)
        Assert.assertEquals("The randomness should be correct", blindSignatureRandomness, randomness)
    }

    @Test
    fun sendBlindSignatureRandomnessTest() {
        val bank = Mockito.mock(Bank::class.java)
        iPV8CommunicationProtocol.participant = bank

        val givenRandomness = groupDescription.generateRandomElementOfG()
        val publicKey = groupDescription.generateRandomElementOfG()
        `when`(bank.getBlindSignatureRandomness(any())).thenReturn(givenRandomness)
        `when`(bank.group).thenReturn(groupDescription)
        val requestMessage = BlindSignatureRandomnessRequestMessage(publicKey.toBytes(), receivingPeer)
        community.messageList.add(requestMessage)

        verify(bank, times(1)).getBlindSignatureRandomness(publicKey)
        verify(community, times(1)).sendBlindSignatureRandomnessReply(givenRandomness.toBytes(), receivingPeer)
    }

    @Test
    fun requestBlindSignatureTest() {
        val bank = Mockito.mock(Bank::class.java)
        iPV8CommunicationProtocol.participant = bank
        addressBookManager.insertAddress(bankAddress)
        val publicKey = groupDescription.generateRandomElementOfG()
        val challenge = BigInteger("12352132521521321521312")
        val amount = 200L
        val response = iPV8CommunicationProtocol.requestBlindSignature(publicKey, bankAddress.name, challenge, amount, serialNumber)
        verify(community, times(1)).getBlindSignature(challenge, publicKey.toBytes(), bankAddress.peerPublicKey!!, amount, serialNumber)
        Assert.assertEquals("The returned response should be correct", blindSignatureResponse, response)
    }

    @Test
    fun sendBlindSignatureTest() {
        val bank = Mockito.mock(Bank::class.java)
        iPV8CommunicationProtocol.participant = bank

        val challenge = BigInteger("321321521421097502142")
        val publicKey = groupDescription.generateRandomElementOfG()
        val serialNumber = UUID.randomUUID().toString()
        `when`(bank.createBlindSignature(challenge, publicKey, amount, serialNumber)).thenReturn(blindSignatureResponse)
        `when`(bank.group).thenReturn(groupDescription)
        val blindSignatureRequestMessage = BlindSignatureRequestMessage(challenge, publicKey.toBytes(), amount, serialNumber, receivingPeer)
        community.messageList.add(blindSignatureRequestMessage)

        verify(bank, times(1)).createBlindSignature(challenge, publicKey, amount, serialNumber)
        verify(community, times(1)).sendBlindSignature(blindSignatureResponse, receivingPeer)
    }

    @Test
    fun requestTransactionRandomnessTest() {
        val bank = Mockito.mock(Bank::class.java)
        iPV8CommunicationProtocol.participant = bank

        val bankPK = groupDescription.generateRandomElementOfG()
        `when`(bank.publicKey).thenReturn(bankPK)
        addressBookManager.insertAddress(receiverAddress)
        val transactionRandomness = iPV8CommunicationProtocol.requestTransactionRandomness(receiverAddress.name, groupDescription)
        verify(community, times(1)).getTransactionRandomizationElements(bankPK.toBytes(), receiverAddress.peerPublicKey!!)
        Assert.assertEquals("The transaction randomness should be correct", randomizationElements, transactionRandomness)
    }

    @Test
    fun sendTransactionRandomnessTest() {
        val user = Mockito.mock(User::class.java)
        iPV8CommunicationProtocol.participant = user

        val randomT = groupDescription.getRandomZr()
        val randomizationElements = GrothSahai.tToRandomizationElements(randomT, groupDescription, ttpCRS.first)
        val publicKey = groupDescription.generateRandomElementOfG()
        `when`(user.group).thenReturn(groupDescription)
        `when`(user.generateRandomizationElements(any())).thenReturn(randomizationElements)
        `when`(community.sendTransactionRandomizationElements(any(), any())).then { }

        val requestMessage = TransactionRandomizationElementsRequestMessage(publicKey.toBytes(), receivingPeer)
        community.messageList.add(requestMessage)

        verify(user, times(1)).generateRandomizationElements(publicKey)
        verify(
            community,
            times(1)
        ).sendTransactionRandomizationElements(randomizationElements.toRandomizationElementsBytes(), receivingPeer)
    }

    @Test
    fun sendTransactionDetailsTest() {
        val user = Mockito.mock(User::class.java)
        iPV8CommunicationProtocol.participant = user

        val userPK = groupDescription.generateRandomElementOfG()
        `when`(user.publicKey).thenReturn(userPK)

        addressBookManager.insertAddress(receiverAddress)
        val transactionDetails = Mockito.mock(TransactionDetails::class.java)
        val transactionDetailsBytes = Mockito.mock(TransactionDetailsBytes::class.java)
        `when`(transactionDetails.toTransactionDetailsBytes()).thenReturn(transactionDetailsBytes)

        val result = iPV8CommunicationProtocol.sendTransactionDetails(receiverAddress.name, transactionDetails)
        verify(
            community,
            times(1)
        ).sendTransactionDetails(userPK.toBytes(), receiverAddress.peerPublicKey!!, transactionDetails.toTransactionDetailsBytes())
        Assert.assertEquals("The returned result should be correct", transactionResult, result)
    }

    @Test
    fun onTransactionDetailsReceived() {
        val user = Mockito.mock(User::class.java)
        iPV8CommunicationProtocol.participant = user

        addressBookManager.insertAddress(bankAddress)
        val transactionDetails = Mockito.mock(TransactionDetails::class.java)
        val transactionDetailsBytes = Mockito.mock(TransactionDetailsBytes::class.java)
        val publicKeySender = groupDescription.generateRandomElementOfG()
        val result = "Transaction Mocking"

        `when`(transactionDetailsBytes.toTransactionDetails(groupDescription)).thenReturn(transactionDetails)
        `when`(user.group).thenReturn(groupDescription)
        `when`(user.onReceivedTransaction(transactionDetails, bankPK, publicKeySender)).thenReturn(result)
        `when`(community.sendTransactionResult(result, receivingPeer)).then { }

        val message = TransactionMessage(publicKeySender.toBytes(), transactionDetailsBytes, receivingPeer)
        community.messageList.add(message)

        verify(user, times(1)).onReceivedTransaction(transactionDetails, bankPK, publicKeySender)
        verify(community, times(1)).sendTransactionResult(result, receivingPeer)
    }
}
