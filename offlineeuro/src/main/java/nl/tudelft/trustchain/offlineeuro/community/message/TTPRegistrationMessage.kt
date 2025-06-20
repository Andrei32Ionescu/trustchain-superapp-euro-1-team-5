package nl.tudelft.trustchain.offlineeuro.community.message

import nl.tudelft.ipv8.Peer
import nl.tudelft.trustchain.offlineeuro.enums.Role

class TTPRegistrationMessage(
    val userName: String,
    val userPKBytes: ByteArray,
    val peerPublicKeyBytes: ByteArray,
    val role: Role,
    val requestingPeer: Peer,
    val overlayPK: ByteArray
) : ICommunityMessage {
    override val messageType = CommunityMessageType.TTPRegistrationMessage
}
