package nl.tudelft.trustchain.offlineeuro.community.payload

import org.junit.Assert
import org.junit.Test
import java.math.BigInteger

class BlindSignatureRequestPayloadTest {
    @Test
    fun serializeAndDeserializeTest() {
        val challenge = BigInteger("1232521321452132178521213215252321523125213")
        val publicKeyBytes = "NotAPublicKeyButJustSomeBytes".toByteArray()
        val amount = 200L

        val serializedPayload = BlindSignatureRequestPayload(challenge, publicKeyBytes, amount).serialize()
        val deserializedPayload = BlindSignatureRequestPayload.deserialize(serializedPayload).first
        val deserializedChallenge = deserializedPayload.challenge
        val deserializedPublicKey = deserializedPayload.publicKeyBytes

        Assert.assertEquals("The challenge should be equal", challenge, deserializedChallenge)
        Assert.assertArrayEquals("The public key bytes should be equal", publicKeyBytes, deserializedPublicKey)
    }
}
