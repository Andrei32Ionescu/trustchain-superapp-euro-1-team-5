package nl.tudelft.trustchain.offlineeuro.community.payload

import org.junit.Assert.*
import org.junit.Test

class TTPRegistrationReplyPayloadTest {
    @Test
    fun serializeAndDeserializeTest() {
        val originalStatus = "SUCCESS"
        val payload = TTPRegistrationReplyPayload(originalStatus)

        val serializedPayload = payload.serialize()

        val (deserializedPayload, _) = TTPRegistrationReplyPayload.deserialize(serializedPayload)

        assertEquals("The status should match after deserialization", originalStatus, deserializedPayload.status)
    }
}
