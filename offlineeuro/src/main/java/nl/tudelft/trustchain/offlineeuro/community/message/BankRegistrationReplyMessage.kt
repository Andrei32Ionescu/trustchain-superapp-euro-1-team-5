package nl.tudelft.trustchain.offlineeuro.community.message

class BankRegistrationReplyMessage(
    val signatureBytes: ByteArray
) : ICommunityMessage {
    override val messageType = CommunityMessageType.BankRegistrationReplyMessage
}
