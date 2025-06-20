package nl.tudelft.trustchain.offlineeuro.community.message

class UserVerificationSubmitMessage (
    val transactionId: String,
): ICommunityMessage {
    override val messageType = CommunityMessageType.UserVerificationSubmitMessage
}
