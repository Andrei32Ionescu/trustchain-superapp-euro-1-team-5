package nl.tudelft.trustchain.offlineeuro.community.message

class TTPRegistrationMessage(
    val userName: String,
    val userPKBytes: ByteArray,
    val legalName: String,
    val peerPublicKeyBytes: ByteArray,
) : ICommunityMessage {
    override val messageType = CommunityMessageType.TTPRegistrationMessage
}
