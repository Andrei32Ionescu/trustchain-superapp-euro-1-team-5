package nl.tudelft.trustchain.offlineeuro.communication

import it.unisa.dia.gas.jpbc.Element
import nl.tudelft.trustchain.offlineeuro.community.OfflineEuroCommunity
import nl.tudelft.trustchain.offlineeuro.community.message.AddressMessage
import nl.tudelft.trustchain.offlineeuro.community.message.AddressRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BilinearGroupCRSReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BilinearGroupCRSRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BlindSignatureRandomnessReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BlindSignatureRandomnessRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BlindSignatureReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BlindSignatureRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.CommunityMessageType
import nl.tudelft.trustchain.offlineeuro.community.message.FraudControlReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.FraudControlRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.ICommunityMessage
import nl.tudelft.trustchain.offlineeuro.community.message.MessageList
import nl.tudelft.trustchain.offlineeuro.community.message.RequestUserVerificationMessage
import nl.tudelft.trustchain.offlineeuro.community.message.TTPRegistrationMessage
import nl.tudelft.trustchain.offlineeuro.community.message.TTPRegistrationReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.TransactionMessage
import nl.tudelft.trustchain.offlineeuro.community.message.TransactionRandomizationElementsReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.TransactionRandomizationElementsRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.TransactionResultMessage
import nl.tudelft.trustchain.offlineeuro.community.message.UserVerificationSubmitMessage
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.GrothSahaiProof
import nl.tudelft.trustchain.offlineeuro.cryptography.RandomizationElements
import nl.tudelft.trustchain.offlineeuro.db.AddressBookManager
import nl.tudelft.trustchain.offlineeuro.entity.Address
import nl.tudelft.trustchain.offlineeuro.entity.Bank
import nl.tudelft.trustchain.offlineeuro.entity.Participant
import nl.tudelft.trustchain.offlineeuro.entity.TTP
import nl.tudelft.trustchain.offlineeuro.entity.TransactionDetails
import nl.tudelft.trustchain.offlineeuro.entity.User
import nl.tudelft.trustchain.offlineeuro.enums.Role
import nl.tudelft.trustchain.offlineeuro.libraries.GrothSahaiSerializer
import java.math.BigInteger
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import nl.tudelft.trustchain.offlineeuro.community.message.BankRegistrationReplyMessage
import nl.tudelft.trustchain.offlineeuro.cryptography.SchnorrSignature
import nl.tudelft.trustchain.offlineeuro.libraries.SchnorrSignatureSerializer
import org.bitcoinj.core.Peer

class IPV8CommunicationProtocol(
    val addressBookManager: AddressBookManager,
    val community: OfflineEuroCommunity,
) : ICommunicationProtocol {
    val messageList = MessageList(this::handleRequestMessage)

    init {
        community.messageList = messageList
    }

    private val sleepDuration: Long = 100
    private val timeOutInMS = 10000
    override lateinit var participant: Participant
    private var bankRegistrationRequestingPeer: Peer? = null

    override fun getGroupDescriptionAndCRS() {
        community.getGroupDescriptionAndCRS()
        val message =
            waitForMessage(CommunityMessageType.GroupDescriptionCRSReplyMessage) as BilinearGroupCRSReplyMessage

        participant.group.updateGroupElements(message.groupDescription)
        val crs = message.crs.toCRS(participant.group)
        participant.crs = crs
        messageList.add(message.addressMessage)
    }

    override fun register(
        userName: String,
        publicKey: Element,
        nameTTP: String,
        role: Role
    ): SchnorrSignature? {
        Log.d("EUDI", "Registering")
        // TODO OOOOOOOOOOOOOOOOOOOOOOOOOOOOOO
        val ttpAddress = addressBookManager.getAddressByName(nameTTP)
        community.registerAtTTP(userName, publicKey.toBytes(), ttpAddress.peerPublicKey!!, role)

        if (role == Role.Bank) {
            // Wait for registration response with signature
            val message = waitForMessage(CommunityMessageType.BankRegistrationReplyMessage) as BankRegistrationReplyMessage
            return SchnorrSignatureSerializer.deserializeSchnorrSignatureBytes(message.signatureBytes)
        }
        return null
    }

    override fun getBlindSignatureRandomness(
        publicKey: Element,
        bankName: String,
        group: BilinearGroup
    ): Element {
        val bankAddress = addressBookManager.getAddressByName(bankName)
        community.getBlindSignatureRandomness(publicKey.toBytes(), bankAddress.peerPublicKey!!)

        val replyMessage =
            waitForMessage(CommunityMessageType.BlindSignatureRandomnessReplyMessage) as BlindSignatureRandomnessReplyMessage
        return group.gElementFromBytes(replyMessage.randomnessBytes)
    }

    override fun requestBlindSignature(
        publicKey: Element,
        bankName: String,
        challenge: BigInteger,
        amount: Long,
        serialNumber: String
    ): ICommunicationProtocol.BlindSignatureResponse {
        val bankAddress = addressBookManager.getAddressByName(bankName)
        community.getBlindSignature(challenge, publicKey.toBytes(), bankAddress.peerPublicKey!!, amount, serialNumber)

        val replyMessage = waitForMessage(CommunityMessageType.BlindSignatureReplyMessage) as BlindSignatureReplyMessage
        val newResponse = ICommunicationProtocol.BlindSignatureResponse(replyMessage.signature, replyMessage.timestamp, replyMessage.hashSignature, replyMessage.bankPublicKey, replyMessage.bankKeySignature, replyMessage.amountSignature)
        return newResponse
    }

    override fun requestTransactionRandomness(
        userNameReceiver: String,
        group: BilinearGroup
    ): RandomizationElements {
        val peerAddress = addressBookManager.getAddressByName(userNameReceiver)
        community.getTransactionRandomizationElements(participant.publicKey.toBytes(), peerAddress.peerPublicKey!!)
        val message = waitForMessage(CommunityMessageType.TransactionRandomnessReplyMessage) as TransactionRandomizationElementsReplyMessage
        return message.randomizationElementsBytes.toRandomizationElements(group)
    }

    override fun sendTransactionDetails(
        userNameReceiver: String,
        transactionDetails: TransactionDetails
    ): String {
        val peerAddress = addressBookManager.getAddressByName(userNameReceiver)
        community.sendTransactionDetails(
            participant.publicKey.toBytes(),
            peerAddress.peerPublicKey!!,
            transactionDetails.toTransactionDetailsBytes()
        )
        Log.d("IPV8Protocol", "Sent transaction details to $userNameReceiver")

        val message = waitForMessage(CommunityMessageType.TransactionResultMessage) as TransactionResultMessage
        return message.result
    }

    override fun requestFraudControl(
        firstProof: GrothSahaiProof,
        secondProof: GrothSahaiProof,
        nameTTP: String
    ): String {
        val ttpAddress = addressBookManager.getAddressByName(nameTTP)
        community.sendFraudControlRequest(
            GrothSahaiSerializer.serializeGrothSahaiProof(firstProof),
            GrothSahaiSerializer.serializeGrothSahaiProof(secondProof),
            ttpAddress.peerPublicKey!!
        )
        val message = waitForMessage(CommunityMessageType.FraudControlReplyMessage) as FraudControlReplyMessage
        return message.result
    }

    fun scopePeers() {
        community.scopePeers(participant.name, getParticipantRole(), participant.publicKey.toBytes())
    }

    override fun getPublicKeyOf(
        name: String,
        group: BilinearGroup
    ): Element {
        return addressBookManager.getAddressByName(name).publicKey
    }

    private fun waitForMessage(messageType: CommunityMessageType, specificTimeoutInMs: Int = timeOutInMS): ICommunityMessage {
        var loops = 0

        while (!community.messageList.any { it.messageType == messageType }) {
            if (loops * sleepDuration >= specificTimeoutInMs) {
                throw Exception("TimeOut")
            }
            Thread.sleep(sleepDuration)
            loops++
        }

        val message =
            community.messageList.first { it.messageType == messageType }
        community.messageList.remove(message)

        return message
    }

    private fun handleAddressMessage(message: AddressMessage) {
        val publicKey = participant.group.gElementFromBytes(message.publicKeyBytes)
        val address = Address(message.name, message.role, publicKey, message.peerPublicKey)
        addressBookManager.insertAddress(address)
        participant.onDataChangeCallback?.invoke(null)
    }

    private fun handleGetBilinearGroupAndCRSRequest(message: BilinearGroupCRSRequestMessage) {
        if (participant !is TTP) {
            return
        } else {
            val groupBytes = participant.group.toGroupElementBytes()
            val crsBytes = participant.crs.toCRSBytes()
            val peer = message.requestingPeer
            community.sendGroupDescriptionAndCRS(
                groupBytes,
                crsBytes,
                participant.publicKey.toBytes(),
                peer
            )
        }
    }

    private fun handleBlindSignatureRandomnessRequest(message: BlindSignatureRandomnessRequestMessage) {
        if (participant !is Bank) {
            throw Exception("Participant is not a bank")
        }
        val bank = (participant as Bank)
        val publicKey = bank.group.gElementFromBytes(message.publicKeyBytes)
        val randomness = bank.getBlindSignatureRandomness(publicKey)
        val requestingPeer = message.peer
        community.sendBlindSignatureRandomnessReply(randomness.toBytes(), requestingPeer)
    }

    private fun handleBlindSignatureRequestMessage(message: BlindSignatureRequestMessage) {
        if (participant !is Bank) {
            throw Exception("Participant is not a bank")
        }
        val bank = (participant as Bank)

        val publicKey = bank.group.gElementFromBytes(message.publicKeyBytes)
        val challenge = message.challenge
        val amount = message.amount
        val serialNumber = message.serialNumber

        val blindSignatureResponse = bank.createBlindSignature(challenge, publicKey, amount, serialNumber)

        val requestingPeer = message.peer
        community.sendBlindSignature(blindSignatureResponse, requestingPeer)
    }

    private fun handleTransactionRandomizationElementsRequest(message: TransactionRandomizationElementsRequestMessage) {
        val group = participant.group
        val publicKey = group.gElementFromBytes(message.publicKey)
        val requestingPeer = message.requestingPeer

        val randomizationElements = participant.generateRandomizationElements(publicKey)
        val randomizationElementBytes = randomizationElements.toRandomizationElementsBytes()
        community.sendTransactionRandomizationElements(randomizationElementBytes, requestingPeer)
    }

    private fun handleTransactionMessage(message: TransactionMessage) {
        val bankPublicKey =
            if (participant is Bank) {
                participant.publicKey
            } else {
                addressBookManager.getAddressByName("Bank").publicKey
            }

        Log.d("ss", "TEEEEEEEEEEEEEEEEEEEEEEEEESSSSSSSSSSSSSSSSSSSSSSSSSSSTTTTTTTTTTTTTTTTTTTTTTTTTTT")

        val group = participant.group
        val publicKey = group.gElementFromBytes(message.publicKeyBytes)
        val transactionDetailsBytes = message.transactionDetailsBytes
        val transactionDetails = transactionDetailsBytes.toTransactionDetails(group)
        val transactionResult = participant.onReceivedTransaction(transactionDetails, bankPublicKey, publicKey)
        val requestingPeer = message.requestingPeer
        community.sendTransactionResult(transactionResult, requestingPeer)
    }

    private fun handleRegistrationMessage(message: TTPRegistrationMessage) {
        if (participant !is TTP) {
            return
        }
        Log.d("EUDI","Handle registration message, ${message.userName}, ${message.messageType}, ${message.userPKBytes}")
        val ttp = participant as TTP
        val publicKey = ttp.group.gElementFromBytes(message.userPKBytes)

        // TODO OOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOO
        if (message.role == Role.Bank) {
            val (success, signature) = ttp.registerBank(publicKey, message.userName, message.role)
            // If it's a bank registration, send back the signature
            if (success) {
                val signatureBytes = SchnorrSignatureSerializer.serializeSchnorrSignature(signature)
                community.sendBankRegistrationReply(signatureBytes, message.requestingPeer)
            }
        }
        else if (message.role == Role.User) {
            ttp.sendUserRequestData(message.userName, publicKey, message.overlayPK)
        }
    }

    private fun handleAddressRequestMessage(message: AddressRequestMessage) {
        val role = getParticipantRole()

        community.sendAddressReply(participant.name, role, participant.publicKey.toBytes(), message.requestingPeer)
    }

    private fun handleFraudControlRequestMessage(message: FraudControlRequestMessage) {
        if (getParticipantRole() != Role.TTP) {
            return
        }
        val ttp = participant as TTP
        val firstProof = GrothSahaiSerializer.deserializeProofBytes(message.firstProofBytes, participant.group)
        val secondProof = GrothSahaiSerializer.deserializeProofBytes(message.secondProofBytes, participant.group)
        val result = ttp.getUserFromProofs(firstProof, secondProof)
        community.sendFraudControlReply(result, message.requestingPeer)
    }

    private fun handleRequestMessage(message: ICommunityMessage) {
        when (message) {
            is AddressMessage -> handleAddressMessage(message)
            is AddressRequestMessage -> handleAddressRequestMessage(message)
            is BilinearGroupCRSRequestMessage -> handleGetBilinearGroupAndCRSRequest(message)
            is BlindSignatureRandomnessRequestMessage -> handleBlindSignatureRandomnessRequest(message)
            is BlindSignatureRequestMessage -> handleBlindSignatureRequestMessage(message)
            is TransactionRandomizationElementsRequestMessage -> handleTransactionRandomizationElementsRequest(message)
            is TransactionMessage -> handleTransactionMessage(message)
            is TTPRegistrationMessage -> handleRegistrationMessage(message)
            is TTPRegistrationReplyMessage -> handleRegistrationReplyMessage(message)
            is RequestUserVerificationMessage -> handleRequestUserVerificationMessage(message)
            is UserVerificationSubmitMessage -> handleUserVerificationSubmitMessage(message)
            is FraudControlRequestMessage -> handleFraudControlRequestMessage(message)
            else -> throw Exception("Unsupported message type")
        }
        return
    }

    // received by user
    private fun handleRegistrationReplyMessage(message: TTPRegistrationReplyMessage) {
        Log.d("EUDI", "handle registration")
        if (participant !is User) {
            return
        }
        (participant as User).onReceivedTTPRegisterReply()

//        val message = waitForMessage(CommunityMessageType.TTPRegistrationReplyMessage) as TTPRegistrationReplyMessage
//        return message.result
        // do user stuff based on status
    }

    private fun handleRequestUserVerificationMessage(message: RequestUserVerificationMessage) {
        Log.d("EUDI", "handle request user verification")
        if (participant !is User) {
            return
        }
        (participant as User).onReceivedRequestUserVerification(message.deeplink, message.transactionId)
    }

    private fun handleUserVerificationSubmitMessage(message: UserVerificationSubmitMessage) {
        if (participant !is TTP) {
            return
        }
        Log.d("EUDI", "Handle user verification submit message")
        val ttp = participant as TTP
        ttp.registerUser(message.transactionId)
//        CoroutineScope(Dispatchers.Default).launch {
//            val result = ttp.registerUser("name", message.transactionId, Role.User).await() // TODO ADD ROLE AND USERNAME
//        }

        //        val (success, signature) = ttp.registerUser(message.userName, publicKey, message.role)
//
//        // If it's a bank registration, send back the signature
//        if (message.role == Role.Bank && signature != null && success) {
//            val signatureBytes = SchnorrSignatureSerializer.serializeSchnorrSignature(signature)
//            community.sendBankRegistrationReply(signatureBytes, message.requestingPeer)
//        }
//        val submitVerifMessage = waitForMessage(CommunityMessageType.UserVerificationSubmitMessage) as UserVerificationSubmitMessage
    }

    private fun getParticipantRole(): Role {
        return when (participant) {
            is User -> Role.User
            is TTP -> Role.TTP
            is Bank -> Role.Bank
            else -> throw Exception("Unknown role")
        }
    }

    override fun sendRegisterAtTTPReplyMessage(
        status: String,
        publicKey: ByteArray
    ) {
        community.sendRegisterAtTTPReplyMessage(
            status,
            publicKey,
        )
    }

    override fun sendRequestUserVerificationMessage(
        transactionId: String,
        deeplink: String,
        publicKey: ByteArray,
    ) {
        community.sendRequestUserVerificationMessage(
            transactionId,
            deeplink,
            publicKey
        )
    }

    override fun sendUserSubmitVerificationMessage(transactionId: String, publicKey: ByteArray) {
        community.sendUserVerificationSubmitMessage(
            transactionId,
            publicKey
        )
    }
}
