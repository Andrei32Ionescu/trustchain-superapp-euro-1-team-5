package nl.tudelft.trustchain.offlineeuro.community.message

import nl.tudelft.ipv8.Peer
import java.math.BigInteger
import java.time.temporal.TemporalAmount

class BlindSignatureRequestMessage(
    val challenge: BigInteger,
    val publicKeyBytes: ByteArray,
    val amount: Double,
    val peer: Peer
) : ICommunityMessage {
    override val messageType = CommunityMessageType.BlindSignatureRequestMessage
}
