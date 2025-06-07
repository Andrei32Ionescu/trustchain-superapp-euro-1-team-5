package nl.tudelft.trustchain.offlineeuro.community.message

class EudiVerificationCompletedMessage (
    status: String
) : ICommunityMessage {
    override val messageType = CommunityMessageType.EudiVerificationCompletedMessage
}

