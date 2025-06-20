package nl.tudelft.trustchain.offlineeuro.community

import nl.tudelft.ipv8.Overlay
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCommunity
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCrawler
import nl.tudelft.ipv8.attestation.trustchain.TrustChainSettings
import nl.tudelft.ipv8.attestation.trustchain.store.TrustChainStore
import nl.tudelft.ipv8.messaging.Packet
import nl.tudelft.trustchain.offlineeuro.community.message.AddressMessage
import nl.tudelft.trustchain.offlineeuro.community.message.AddressRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BilinearGroupCRSReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BilinearGroupCRSRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BlindSignatureRandomnessReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BlindSignatureRandomnessRequestMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BlindSignatureReplyMessage
import nl.tudelft.trustchain.offlineeuro.community.message.BlindSignatureRequestMessage
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
import nl.tudelft.trustchain.offlineeuro.community.payload.AddressPayload
import nl.tudelft.trustchain.offlineeuro.community.payload.BilinearGroupCRSPayload
import nl.tudelft.trustchain.offlineeuro.community.payload.BlindSignatureRequestPayload
import nl.tudelft.trustchain.offlineeuro.community.payload.ByteArrayPayload
import nl.tudelft.trustchain.offlineeuro.community.payload.FraudControlRequestPayload
import nl.tudelft.trustchain.offlineeuro.community.payload.RequestUserVerificationPayload
import nl.tudelft.trustchain.offlineeuro.community.payload.TTPRegistrationPayload
import nl.tudelft.trustchain.offlineeuro.community.payload.TTPRegistrationReplyPayload
import nl.tudelft.trustchain.offlineeuro.community.payload.TransactionDetailsPayload
import nl.tudelft.trustchain.offlineeuro.community.payload.TransactionRandomizationElementsPayload
import nl.tudelft.trustchain.offlineeuro.community.payload.UserVerificationSubmitPayload
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroupElementsBytes
import nl.tudelft.trustchain.offlineeuro.cryptography.CRSBytes
import nl.tudelft.trustchain.offlineeuro.cryptography.RandomizationElementsBytes
import nl.tudelft.trustchain.offlineeuro.entity.TransactionDetailsBytes
import nl.tudelft.trustchain.offlineeuro.enums.Role
import java.math.BigInteger
import android.util.Log
import it.unisa.dia.gas.jpbc.Element
import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.bilinearGroup
import nl.tudelft.trustchain.offlineeuro.communication.ICommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.community.message.BankRegistrationReplyMessage
import nl.tudelft.trustchain.offlineeuro.cryptography.SchnorrSignature
import nl.tudelft.trustchain.offlineeuro.entity.DigitalEuro
import java.nio.ByteBuffer
import java.nio.ByteOrder

object MessageID {
    const val GET_GROUP_DESCRIPTION_CRS = 9
    const val GET_GROUP_DESCRIPTION_CRS_REPLY = 10
    const val REGISTER_AT_TTP = 11
    // TODO THIS NUMBER WAS CHANGED
    const val REGISTER_AT_TTP_REPLY = 26
    const val REQUEST_USER_VERIFICATION = 25
    const val USER_VERIFICATION_SUBMIT = 27

    const val GET_BLIND_SIGNATURE_RANDOMNESS = 12
    const val GET_BLIND_SIGNATURE_RANDOMNESS_REPLY = 13
    const val GET_BLIND_SIGNATURE = 14
    const val GET_BLIND_SIGNATURE_REPLY = 15
    const val BANK_REGISTRATION_REPLY = 24

    const val GET_TRANSACTION_RANDOMIZATION_ELEMENTS = 16
    const val GET_TRANSACTION_RANDOMIZATION_ELEMENTS_REPLY = 17

    const val TRANSACTION = 18
    const val TRANSACTION_RESULT = 19
    const val SCOPE_PEERS = 20
    const val ADDRESS_RESPONSE = 21

    const val FRAUD_CONTROL_REQUEST = 22
    const val FRAUD_CONTROL_REPLY = 23
}

class OfflineEuroCommunity(
    settings: TrustChainSettings,
    database: TrustChainStore,
    crawler: TrustChainCrawler = TrustChainCrawler()
) : TrustChainCommunity(settings, database, crawler) {
    override val serviceId = "ffffd716494b474ea9f614a16a4da0aed6899aec"

    lateinit var messageList: MessageList<ICommunityMessage>

    init {

        messageHandlers[MessageID.GET_GROUP_DESCRIPTION_CRS] = ::onGetGroupDescriptionAndCRSPacket
        messageHandlers[MessageID.GET_GROUP_DESCRIPTION_CRS_REPLY] = ::onGetGroupDescriptionAndCRSReplyPacket

        messageHandlers[MessageID.REGISTER_AT_TTP] = ::onGetRegisterAtTTPPacket
        messageHandlers[MessageID.REGISTER_AT_TTP_REPLY] = ::onGetRegisterAtTTPReplyPacket
        messageHandlers[MessageID.REQUEST_USER_VERIFICATION] = ::onGetRequestUserVerificationPacket
        messageHandlers[MessageID.USER_VERIFICATION_SUBMIT] = ::onGetUserVerificationSubmitPacket

        messageHandlers[MessageID.GET_BLIND_SIGNATURE_RANDOMNESS] = ::onGetBlindSignatureRandomnessPacket
        messageHandlers[MessageID.GET_BLIND_SIGNATURE_RANDOMNESS_REPLY] = ::onGetBlindSignatureRandomnessReplyPacket

        messageHandlers[MessageID.GET_BLIND_SIGNATURE] = ::onGetBlindSignaturePacket
        messageHandlers[MessageID.GET_BLIND_SIGNATURE_REPLY] = ::onGetBlindSignatureReplyPacket
        messageHandlers[MessageID.BANK_REGISTRATION_REPLY] = ::onBankRegistrationReplyPacket

        messageHandlers[MessageID.GET_TRANSACTION_RANDOMIZATION_ELEMENTS] = ::onGetTransactionRandomizationElementsRequestPacket
        messageHandlers[MessageID.GET_TRANSACTION_RANDOMIZATION_ELEMENTS_REPLY] = ::onGetTransactionRandomizationElementsReplyPacket

        messageHandlers[MessageID.TRANSACTION] = ::onTransactionPacket
        messageHandlers[MessageID.TRANSACTION_RESULT] = ::onTransactionResultPacket

        messageHandlers[MessageID.SCOPE_PEERS] = ::onScopePeersPacket
        messageHandlers[MessageID.ADDRESS_RESPONSE] = ::onAddressReplyPacket

        messageHandlers[MessageID.FRAUD_CONTROL_REQUEST] = ::onFraudControlRequestPacket
        messageHandlers[MessageID.FRAUD_CONTROL_REPLY] = ::onFraudControlReplyPacket
    }

    private fun onGetRegisterAtTTPReplyPacket(packet: Packet) {
        Log.d("EUDI", "Got reply packet")
        val (_, payload) = packet.getAuthPayload(TTPRegistrationReplyPayload)
        val message = TTPRegistrationReplyMessage(payload.status)
        addMessage(message)
    }

    fun sendRegisterAtTTPReplyMessage(
        status: String,
        userPK: ByteArray
    ) {
        Log.d("EUDI", "Sending Register at ttp reply $status $userPK")
        Log.d("EUDI", userPK.toString())

        val peers = getPeers()
        Log.d("EUDI", "$peers")
        val peer = getPeerByPublicKeyBytes(userPK) ?: throw Exception("Completed Verification: User not found (this error shouldn't happen i think)")
        val eudiVerificationCompletedPacket =
            serializePacket(
                MessageID.REGISTER_AT_TTP_REPLY,
                TTPRegistrationReplyPayload(status)
            )

        send(peer, eudiVerificationCompletedPacket)
    }

    private fun onGetRequestUserVerificationPacket(packet: Packet) {
        Log.d("EUDI", "Got request user verification packet")
        val (_, payload) = packet.getAuthPayload(RequestUserVerificationPayload)
        Log.d("EUDI", "The packet is: ${payload.deeplink}, ${payload.transactionId}")
        val message = RequestUserVerificationMessage(payload.transactionId, payload.deeplink)
        addMessage(message)
    }

    fun sendRequestUserVerificationMessage(
        transactionId: String,
        deeplink: String,
        userPK: ByteArray,
    ) {
//        community
        Log.d("EUDI", "Sending Request User Verification at ttp reply")
        val peer = getPeerByPublicKeyBytes(userPK) ?: throw Exception("Send request error: user not found $userPK")
        val requestUserVerificationPacket =
            serializePacket(
                MessageID.REQUEST_USER_VERIFICATION,
                RequestUserVerificationPayload(transactionId, deeplink)
            )

        send(peer, requestUserVerificationPacket)
    }

    private fun onGetUserVerificationSubmitPacket(packet: Packet) {
        Log.d("EUDI", "Got user submit verification packet")
        val (_, payload) = packet.getAuthPayload(UserVerificationSubmitPayload)
        val message = UserVerificationSubmitMessage(payload.transactionId)
        addMessage(message)
    }

    fun sendUserVerificationSubmitMessage(
        transactionId: String,
        ttpPublicKeyBytes: ByteArray
    ) {
        Log.d("EUDI", "Sending user submit verification message to TTP")
        val peer = getPeerByPublicKeyBytes(ttpPublicKeyBytes) ?: throw Exception("Send user verification submit error: ttp not found $ttpPublicKeyBytes")
        val userVerificationSubmitPacket =
            serializePacket(
                MessageID.USER_VERIFICATION_SUBMIT,
                UserVerificationSubmitPayload(transactionId)
            )

        send(peer, userVerificationSubmitPacket)
    }

    fun getGroupDescriptionAndCRS() {
        val packet =
            serializePacket(
                MessageID.GET_GROUP_DESCRIPTION_CRS,
                ByteArrayPayload(myPeer.publicKey.keyToBin())
            )

        for (peer: Peer in getPeers()) {
            send(peer, packet)
        }
    }

    private fun onGetGroupDescriptionAndCRSPacket(packet: Packet) {
        val (requestingPeer, payload) = packet.getAuthPayload(ByteArrayPayload)
        onGetGroupDescriptionAndCRS(requestingPeer)
    }

    fun onGetGroupDescriptionAndCRS(requestingPeer: Peer) {
        val message = BilinearGroupCRSRequestMessage(requestingPeer)
        messageList.add(message)
    }

    fun sendGroupDescriptionAndCRS(
        groupBytes: BilinearGroupElementsBytes,
        crsBytes: CRSBytes,
        ttpPublicKeyBytes: ByteArray,
        requestingPeer: Peer
    ) {
        val groupAndCrsPacket =
            serializePacket(
                MessageID.GET_GROUP_DESCRIPTION_CRS_REPLY,
                BilinearGroupCRSPayload(groupBytes, crsBytes, ttpPublicKeyBytes)
            )

        send(requestingPeer, groupAndCrsPacket)
    }

    private fun onGetGroupDescriptionAndCRSReplyPacket(packet: Packet) {
        val (peer, payload) = packet.getAuthPayload(BilinearGroupCRSPayload)
        onGetGroupDescriptionAndCRSReply(payload, peer)
    }

    fun onGetGroupDescriptionAndCRSReply(
        payload: BilinearGroupCRSPayload,
        peer: Peer
    ) {
        val groupElements = payload.bilinearGroupElements
        val crs = payload.crs

        val ttpAddressMessage = AddressMessage("TTP", Role.TTP, payload.ttpPublicKey, peer.publicKey.keyToBin())

        val message = BilinearGroupCRSReplyMessage(groupElements, crs, ttpAddressMessage)
        addMessage(message)
    }

    fun registerAtTTP(
        name: String,
        myPublicKeyBytes: ByteArray,
        publicKeyTTP: ByteArray,
        role: Role
    ) {
        val ttpPeer = getPeerByPublicKeyBytes(publicKeyTTP) ?: throw Exception("TTP not found")
        Log.d("EUDI", "Registering at ttp")
        val registerPacket =
            serializePacket(
                MessageID.REGISTER_AT_TTP,
                TTPRegistrationPayload(
                    name,
                    myPublicKeyBytes,
                    myPeer.publicKey.keyToBin(),
                    role
                )
            )

        send(ttpPeer, registerPacket)
    }

    fun onGetRegisterAtTTPPacket(packet: Packet) {
        Log.d("EUDI", "Got register at ttp packet")
        val (peer, payload) = packet.getAuthPayload(TTPRegistrationPayload)
        onGetRegisterAtTTP(peer, payload)
    }

    fun onGetRegisterAtTTP(
        peer: Peer,
        payload: TTPRegistrationPayload
    ) {
        val senderPKBytes = peer.publicKey.keyToBin()
        val userName = payload.userName
        val userPKBytes = payload.publicKey
        val role = payload.role

        val message =
            TTPRegistrationMessage(
                userName,
                userPKBytes,
                senderPKBytes,
                role,
                peer
            )

        addMessage(message)
    }

    fun getBlindSignatureRandomness(
        userPublicKeyBytes: ByteArray,
        publicKeyBank: ByteArray
    ) {
        val bankPeer = getPeerByPublicKeyBytes(publicKeyBank)

        bankPeer ?: throw Exception("Bank not found")

        val packet =
            serializePacket(
                MessageID.GET_BLIND_SIGNATURE_RANDOMNESS,
                ByteArrayPayload(
                    userPublicKeyBytes
                )
            )

        send(bankPeer, packet)
    }

    fun onGetBlindSignatureRandomnessPacket(packet: Packet) {
        val (requestingPeer, payload) = packet.getAuthPayload(ByteArrayPayload)
        onGetBlindSignatureRandomness(requestingPeer, payload)
    }

    fun onGetBlindSignatureRandomness(
        requestingPeer: Peer,
        payload: ByteArrayPayload
    ) {
        val publicKey = payload.bytes
        val message =
            BlindSignatureRandomnessRequestMessage(
                publicKey,
                requestingPeer
            )
        addMessage(message)
    }

    fun sendBlindSignatureRandomnessReply(
        randomnessBytes: ByteArray,
        peer: Peer
    ) {
        val packet = serializePacket(MessageID.GET_BLIND_SIGNATURE_RANDOMNESS_REPLY, ByteArrayPayload(randomnessBytes))
        send(peer, packet)
    }

    fun onGetBlindSignatureRandomnessReplyPacket(packet: Packet) {
        val (peer, payload) = packet.getAuthPayload(ByteArrayPayload)
        onGetBlindSignatureRandomnessReply(payload)
    }

    fun onGetBlindSignatureRandomnessReply(payload: ByteArrayPayload) {
        val message = BlindSignatureRandomnessReplyMessage(payload.bytes)
        addMessage(message)
    }

    fun getBlindSignature(
        challenge: BigInteger,
        publicKeyBytes: ByteArray,
        bankPublicKeyBytes: ByteArray,
        amount: Long,
        serialNumber: String
    ) {
        val bankPeer = getPeerByPublicKeyBytes(bankPublicKeyBytes)

        bankPeer ?: throw Exception("Bank not found")
        val packet =
            serializePacket(
                MessageID.GET_BLIND_SIGNATURE,
                BlindSignatureRequestPayload(
                    challenge,
                    publicKeyBytes,
                    amount,
                    serialNumber
                )
            )

        send(bankPeer, packet)
    }

    fun onGetBlindSignaturePacket(packet: Packet) {
        val (requestingPeer, payload) = packet.getAuthPayload(BlindSignatureRequestPayload)
        onGetBlindSignature(requestingPeer, payload)
    }

    fun onGetBlindSignature(
        requestingPeer: Peer,
        payload: BlindSignatureRequestPayload
    ) {
        val message =
            BlindSignatureRequestMessage(
                payload.challenge,
                payload.publicKeyBytes,
                payload.amount,
                payload.serialNumber,
                requestingPeer
            )
        addMessage(message)
    }

    fun sendBlindSignature(
        signature: ICommunicationProtocol.BlindSignatureResponse,
        peer: Peer
    ) {
        val sigBytes          = signature.signature.toByteArray()
        val tsBytes           = ByteBuffer.allocate(Long.SIZE_BYTES)
            .putLong(signature.timestamp).array()
        val sigHashBytes      = signature.hashSignature.toBytes()
        val sigPkBytes        = signature.bankKeySignature.toBytes()
        val bankPkBytes       = signature.bankPublicKey
        val sigAmtBytes       = signature.amountSignature.toBytes()

        val totalSize =
            4 + sigBytes.size +                       // sig length + data
                tsBytes.size +                            // timestamp (8 B)
                4 + sigHashBytes.size +                   // Schnorr hash-sig
                4 + sigPkBytes.size +                     // Schnorr pk-sig
                4 + bankPkBytes.size +                    // bank PK
                4 + sigAmtBytes.size                      // Schnorr amt-sig

        val buf = ByteBuffer.allocate(totalSize)
            .order(ByteOrder.BIG_ENDIAN)

        buf.putInt(sigBytes.size) ; buf.put(sigBytes)
        buf.put(tsBytes)
        buf.putInt(sigHashBytes.size) ; buf.put(sigHashBytes)
        buf.putInt(sigPkBytes.size)   ; buf.put(sigPkBytes)
        buf.putInt(bankPkBytes.size)  ; buf.put(bankPkBytes)
        buf.putInt(sigAmtBytes.size)  ; buf.put(sigAmtBytes)

        val payload = ByteArrayPayload(buf.array())
        val packet  = serializePacket(
            MessageID.GET_BLIND_SIGNATURE_REPLY,
            payload
        )
        send(peer, packet)
    }

    fun onGetBlindSignatureReplyPacket(packet: Packet) {
        val (requestingPeer, payload) = packet.getAuthPayload(ByteArrayPayload)
        onGetBlindSignatureReply(requestingPeer, payload)
    }

    fun onGetBlindSignatureReply(
        requestingPeer: Peer,
        payload: ByteArrayPayload
    ) {
        val buf = ByteBuffer.wrap(payload.bytes).order(ByteOrder.BIG_ENDIAN)

        val sigLen   = buf.int
        val sigBytes = ByteArray(sigLen).also { buf.get(it) }
        val signature = BigInteger(sigBytes)

        val timestamp = buf.long

        val sigHashLen   = buf.int
        val sigHashBytes = ByteArray(sigHashLen).also { buf.get(it) }
        val hashSignature = SchnorrSignature.fromBytes(sigHashBytes)

        val sigPkLen   = buf.int
        val sigPkBytes = ByteArray(sigPkLen).also { buf.get(it) }
        val bankKeySignature = SchnorrSignature.fromBytes(sigPkBytes)

        val bankPkLen   = buf.int
        val bankPkBytes = ByteArray(bankPkLen).also { buf.get(it) }

        val sigAmtLen   = buf.int
        val sigAmtBytes = ByteArray(sigAmtLen).also { buf.get(it) }
        val amountSignature = SchnorrSignature.fromBytes(sigAmtBytes)

        val msg = BlindSignatureReplyMessage(
            signature,
            timestamp,
            hashSignature,
            bankPkBytes,
            bankKeySignature,
            amountSignature
        )
        addMessage(msg)
    }

    fun getTransactionRandomizationElements(
        publicKey: ByteArray,
        publicKeyReceiver: ByteArray
    ) {
        val peer = getPeerByPublicKeyBytes(publicKeyReceiver)

        peer ?: throw Exception("User not found")

        val packet =
            serializePacket(
                MessageID.GET_TRANSACTION_RANDOMIZATION_ELEMENTS,
                ByteArrayPayload(
                    publicKey
                )
            )

        send(peer, packet)
    }

    fun onGetTransactionRandomizationElementsRequestPacket(packet: Packet) {
        val (requestingPeer, payload) = packet.getAuthPayload(ByteArrayPayload)
        onGetTransactionRandomizationElementsRequest(requestingPeer, payload)
    }

    fun onGetTransactionRandomizationElementsRequest(
        requestingPeer: Peer,
        payload: ByteArrayPayload
    ) {
        val message = TransactionRandomizationElementsRequestMessage(payload.bytes, requestingPeer)
        addMessage(message)
    }

    fun sendTransactionRandomizationElements(
        randomizationElementsBytes: RandomizationElementsBytes,
        requestingPeer: Peer
    ) {
        val packet =
            serializePacket(
                MessageID.GET_TRANSACTION_RANDOMIZATION_ELEMENTS_REPLY,
                TransactionRandomizationElementsPayload(randomizationElementsBytes)
            )
        send(requestingPeer, packet)
    }

    fun onGetTransactionRandomizationElementsReplyPacket(packet: Packet) {
        val (_, payload) = packet.getAuthPayload(TransactionRandomizationElementsPayload)
        onGetTransactionRandomizationElements(payload)
    }

    fun onGetTransactionRandomizationElements(payload: TransactionRandomizationElementsPayload) {
        val message = TransactionRandomizationElementsReplyMessage(payload.transactionRandomizationElementsBytes)
        addMessage(message)
    }

    fun sendTransactionDetails(
        publicKeyBytes: ByteArray,
        publicKeyReceiver: ByteArray,
        transactionDetails: TransactionDetailsBytes
    ) {
        val peer = getPeerByPublicKeyBytes(publicKeyReceiver)

        peer ?: throw Exception("User not found")

        val packet =
            serializePacket(
                MessageID.TRANSACTION,
                TransactionDetailsPayload(
                    publicKeyBytes,
                    transactionDetails
                )
            )

        send(peer, packet)
    }

    fun onTransactionPacket(packet: Packet) {
        Log.d("OfflineEuroCommunity", "On transaction packet!")
        val (peer, payload) = packet.getAuthPayload(TransactionDetailsPayload)
        onTransaction(peer, payload)
    }

    fun onTransaction(
        peer: Peer,
        payload: TransactionDetailsPayload
    ) {
        Log.d("OfflineEuroCommunity", "On transaction message!")
        val publicKey = payload.publicKey
        val transactionDetailsBytes = payload.transactionDetailsBytes
        val message = TransactionMessage(publicKey, transactionDetailsBytes, peer)
        messageList.add(message)
    }

    fun sendTransactionResult(
        transactionResult: String,
        requestingPeer: Peer
    ) {
        val packet =
            serializePacket(
                MessageID.TRANSACTION_RESULT,
                ByteArrayPayload(transactionResult.toByteArray())
            )
        send(requestingPeer, packet)
    }

    fun onTransactionResultPacket(packet: Packet) {
        val (_, payload) = packet.getAuthPayload(ByteArrayPayload)
        onTransactionResult(payload)
    }

    fun onTransactionResult(byteArrayPayload: ByteArrayPayload) {
        val message = TransactionResultMessage(byteArrayPayload.bytes.toString(Charsets.UTF_8))
        messageList.add(message)
    }

    fun sendAddressReply(
        name: String,
        role: Role,
        publicKeyBytes: ByteArray,
        requestingPeer: Peer
    ) {
        val addressPacket =
            serializePacket(
                MessageID.ADDRESS_RESPONSE,
                AddressPayload(
                    name,
                    publicKeyBytes,
                    role
                )
            )

        send(requestingPeer, addressPacket)
    }

    private fun onAddressReplyPacket(packet: Packet) {
        val (peer, payload) = packet.getAuthPayload(AddressPayload)
        onAddressReply(payload, peer)
    }

    private fun onAddressReply(
        payload: AddressPayload,
        peer: Peer
    ) {
        val message = AddressMessage(payload.userName, payload.role, payload.publicKey, peer.publicKey.keyToBin())
        addMessage(message)
    }

    fun sendFraudControlRequest(
        firstProofBytes: ByteArray,
        secondProofBytes: ByteArray,
        ttpPublicKeyBytes: ByteArray
    ) {
        val peer = getPeerByPublicKeyBytes(ttpPublicKeyBytes)

        peer ?: throw Exception("TTP not found")

        val packet =
            serializePacket(
                MessageID.FRAUD_CONTROL_REQUEST,
                FraudControlRequestPayload(
                    firstProofBytes,
                    secondProofBytes
                )
            )

        send(peer, packet)
    }

    fun sendBankRegistrationReply(
        signatureBytes: ByteArray,
        requestingPeer: Peer
    ) {
        val packet = serializePacket(
            MessageID.BANK_REGISTRATION_REPLY,
            ByteArrayPayload(signatureBytes)
        )
        send(requestingPeer, packet)
    }

    private fun onBankRegistrationReplyPacket(packet: Packet) {
        val (_, payload) = packet.getAuthPayload(ByteArrayPayload)
        onBankRegistrationReply(payload)
    }

    private fun onBankRegistrationReply(payload: ByteArrayPayload) {
        val message = BankRegistrationReplyMessage(payload.bytes)
        addMessage(message)
    }

    fun onFraudControlRequestPacket(packet: Packet) {
        val (peer, payload) = packet.getAuthPayload(FraudControlRequestPayload)
        onFraudControlRequest(peer, payload)
    }

    private fun onFraudControlRequest(
        peer: Peer,
        payload: FraudControlRequestPayload
    ) {
        val message = FraudControlRequestMessage(payload.firstProofBytes, payload.secondProofBytes, peer)
        addMessage(message)
    }

    fun sendFraudControlReply(
        result: String,
        peer: Peer
    ) {
        val packet =
            serializePacket(
                MessageID.FRAUD_CONTROL_REPLY,
                ByteArrayPayload(
                    result.toByteArray()
                )
            )
        send(peer, packet)
    }

    fun onFraudControlReplyPacket(packet: Packet) {
        val (_, payload) = packet.getAuthPayload(ByteArrayPayload)
        onFraudControlReply(payload)
    }

    fun onFraudControlReply(payload: ByteArrayPayload) {
        val fraudControlResult = payload.bytes.toString(Charsets.UTF_8)
        addMessage(FraudControlReplyMessage(fraudControlResult))
    }

    fun scopePeers(
        name: String,
        role: Role,
        publicKeyBytes: ByteArray
    ) {
        val addressPacket =
            serializePacket(
                MessageID.SCOPE_PEERS,
                AddressPayload(
                    name,
                    publicKeyBytes,
                    role
                )
            )

        for (peer in getPeers()) {
            send(peer, addressPacket)
        }
    }

    private fun onScopePeersPacket(packet: Packet) {
        val (requestingPeer, payload) = packet.getAuthPayload(AddressPayload)
        onScopePeers(payload, requestingPeer)
    }

    private fun onScopePeers(
        payload: AddressPayload,
        requestingPeer: Peer
    ) {
        val addressMessage = AddressMessage(payload.userName, payload.role, payload.publicKey, requestingPeer.publicKey.keyToBin())
        addMessage(addressMessage)

        val addressRequestMessage = AddressRequestMessage(requestingPeer)
        addMessage(addressRequestMessage)
    }

    private fun getPeerByPublicKeyBytes(publicKey: ByteArray): Peer? {
        return getPeers().find {
            it.publicKey.keyToBin().contentEquals(publicKey)
        }
    }

    private fun getRandomPeer(publicKeyBank: ByteArray): Peer? {
        return getPeers().shuffled().find {
            !it.publicKey.keyToBin().contentEquals(publicKeyBank)
        }
    }

    fun addMessage(message: ICommunityMessage) {
        if (this::messageList.isInitialized) {
            messageList.add(message)
        }
    }

    class Factory(
        private val settings: TrustChainSettings,
        private val database: TrustChainStore,
        private val crawler: TrustChainCrawler = TrustChainCrawler()
    ) : Overlay.Factory<OfflineEuroCommunity>(OfflineEuroCommunity::class.java) {
        override fun create(): OfflineEuroCommunity {
            return OfflineEuroCommunity(settings, database, crawler)
        }
    }
}
