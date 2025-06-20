package nl.tudelft.trustchain.offlineeuro.community.message

class RequestUserVerificationMessage (
    val transactionId: String,
    val deeplink: String
): ICommunityMessage {
    override val messageType = CommunityMessageType.RequestUserVerificationMessage
}
