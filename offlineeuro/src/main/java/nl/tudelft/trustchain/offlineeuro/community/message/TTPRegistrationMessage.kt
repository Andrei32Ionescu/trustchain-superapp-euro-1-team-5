package nl.tudelft.trustchain.offlineeuro.community.message

import nl.tudelft.ipv8.Peer
import nl.tudelft.trustchain.offlineeuro.enums.Role

class TTPRegistrationMessage(
    val userName: String,
    val userPKBytes: ByteArray,
    val overlayPK: ByteArray,
    val role: Role,
    val requestingPeer: Peer,
) : ICommunityMessage {
    override val messageType = CommunityMessageType.TTPRegistrationMessage
}
