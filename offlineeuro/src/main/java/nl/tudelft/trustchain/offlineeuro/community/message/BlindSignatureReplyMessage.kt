package nl.tudelft.trustchain.offlineeuro.community.message

import it.unisa.dia.gas.jpbc.Element
import nl.tudelft.trustchain.offlineeuro.cryptography.SchnorrSignature
import java.math.BigInteger

class BlindSignatureReplyMessage(
    val signature: BigInteger,
    val timestamp: Long,
    val hashSignature: SchnorrSignature,
    val bankPublicKey: ByteArray,
    val bankKeySignature: SchnorrSignature,
    val amountSignature: SchnorrSignature
) : ICommunityMessage {
    override val messageType = CommunityMessageType.BlindSignatureReplyMessage
}
