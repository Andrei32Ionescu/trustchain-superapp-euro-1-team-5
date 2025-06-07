package nl.tudelft.trustchain.offlineeuro.community.message

class EudiInitiateVerificationReplyMessage (
    val transactionId: String,
    val requestURI: String,
    val requestURIMethod: String,
    val clientId: String
): ICommunityMessage {
    override val messageType = CommunityMessageType.EudiInitiateVerificationReplyMessage
}
