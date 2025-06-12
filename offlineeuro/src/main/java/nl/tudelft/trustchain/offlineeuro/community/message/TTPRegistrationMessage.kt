package nl.tudelft.trustchain.offlineeuro.community.message

class TTPRegistrationMessage(
    val userName: String,
    val userPKBytes: ByteArray,
    val overlayPK: ByteArray
) : ICommunityMessage {
    override val messageType = CommunityMessageType.TTPRegistrationMessage
}
