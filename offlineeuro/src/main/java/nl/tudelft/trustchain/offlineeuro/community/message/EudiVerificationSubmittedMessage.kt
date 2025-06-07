package nl.tudelft.trustchain.offlineeuro.community.message

class EudiVerificationSubmittedMessage (
    val transactionId: String,
    val clientId: String, // maybe not needed?
    val userName: String,
    val publicKey: ByteArray
): ICommunityMessage {
    override val messageType = CommunityMessageType.EudiVerificationSubmittedMessage
}
