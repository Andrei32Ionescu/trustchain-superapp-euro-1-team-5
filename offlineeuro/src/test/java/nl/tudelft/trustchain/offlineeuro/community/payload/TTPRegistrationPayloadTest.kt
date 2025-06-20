package nl.tudelft.trustchain.offlineeuro.community.payload

import org.junit.Assert
import org.junit.Test

class TTPRegistrationPayloadTest {
    @Test
    fun serializeAndDeserializeTest() {
        val name = "NameForUserThatIsTryingToRegister"
        val publicKeyBytes = "NotAPublicKeyButJustSomeBytes".toByteArray()
        val txId = "mockId"

        val serializedPayload = TTPRegistrationPayload(name, publicKeyBytes, txId.toByteArray()).serialize()
        val deserializedPayload = TTPRegistrationPayload.deserialize(serializedPayload).first
        val deserializedName = deserializedPayload.userName
        val deserializedPublicKey = deserializedPayload.publicKey

        Assert.assertEquals("The name should be equal", name, deserializedName)
        Assert.assertArrayEquals("The public key bytes should be equal", publicKeyBytes, deserializedPublicKey)
    }
}
