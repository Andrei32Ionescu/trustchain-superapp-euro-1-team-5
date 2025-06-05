package nl.tudelft.trustchain.offlineeuro.community.message

import nl.tudelft.ipv8.Peer

class TTPRegistrationMessage(
    val userName: String,
    val userPKBytes: ByteArray,
    val transactionId: String,
    val requestingPeer: Peer
) : ICommunityMessage {
    override val messageType = CommunityMessageType.TTPRegistrationMessage
}
