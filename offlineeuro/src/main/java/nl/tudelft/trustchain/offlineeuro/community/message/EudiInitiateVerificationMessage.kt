package nl.tudelft.trustchain.offlineeuro.community.message

class EudiInitiateVerificationMessage (
    val userName: String,
    val publicKey: ByteArray
): ICommunityMessage {
    override val messageType = CommunityMessageType.EudiInitiateVerificationMessage
}
