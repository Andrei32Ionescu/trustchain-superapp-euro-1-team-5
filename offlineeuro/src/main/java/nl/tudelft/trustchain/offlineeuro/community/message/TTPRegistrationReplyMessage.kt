package nl.tudelft.trustchain.offlineeuro.community.message

class TTPRegistrationReplyMessage (
    val status: String,
): ICommunityMessage {
    override val messageType = CommunityMessageType.TTPRegistrationReplyMessage
}
