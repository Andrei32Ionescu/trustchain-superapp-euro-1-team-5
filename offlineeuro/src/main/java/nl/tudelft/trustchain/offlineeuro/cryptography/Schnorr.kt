package nl.tudelft.trustchain.offlineeuro.cryptography

import it.unisa.dia.gas.jpbc.Element
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

data class BlindedChallenge(val challenge: BigInteger, val blindedChallenge: BigInteger, val alpha: BigInteger, val message: ByteArray)

data class SchnorrSignature(val signature: BigInteger, val encryption: BigInteger, val signedMessage: ByteArray) {
    /**
     * Converts the [SchnorrSignature] to a [ByteArray] such that the first target element
     * can be computed.
     * @return The [SchnorrSignature] converted a [ByteArray]
     */
    fun toBytes(): ByteArray {
        val sigBytes = signature.toByteArray()
        val encBytes = encryption.toByteArray()
        val msgBytes = signedMessage

        val buf = ByteBuffer.allocate(
            4 + sigBytes.size +
                4 + encBytes.size +
                4 + msgBytes.size
        ).order(ByteOrder.BIG_ENDIAN)

        buf.putInt(sigBytes.size); buf.put(sigBytes)
        buf.putInt(encBytes.size); buf.put(encBytes)
        buf.putInt(msgBytes.size); buf.put(msgBytes)

        return buf.array()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SchnorrSignature) return false
        return this.signature == other.signature &&
            this.encryption == other.encryption &&
            this.signedMessage.contentEquals(other.signedMessage)
    }

    companion object {
        /**
         * Rebuild a [SchnorrSignature] from the byte format produced by [toBytes].
         *
         * @param bytes      the raw payload
         * @param byteOrder  normally [ByteOrder.BIG_ENDIAN] (must match writer)
         */
        fun fromBytes(bytes: ByteArray, byteOrder: ByteOrder = ByteOrder.BIG_ENDIAN): SchnorrSignature {
            val buf = ByteBuffer.wrap(bytes).order(byteOrder)

            /* -- signature -- */
            val sigLen   = buf.int
            val sigBytes = ByteArray(sigLen).also { buf.get(it) }
            val signature = BigInteger(sigBytes)

            /* -- encryption -- */
            val encLen   = buf.int
            val encBytes = ByteArray(encLen).also { buf.get(it) }
            val encryption = BigInteger(encBytes)

            /* -- signed message -- */
            val msgLen   = buf.int
            val msgBytes = ByteArray(msgLen).also { buf.get(it) }

            return SchnorrSignature(signature, encryption, msgBytes)
        }
    }

}

/**
 * Object that contains methods to create and verify Schnorr signatures.
 *
 * Current supported signature schemes are regular Schnorr signatures and blinded Schnorr signatures
 */
object Schnorr {
    /**
     * Creates a Schnorr signature of the [message]. It takes a [BilinearGroup] as input to determine
     * which group to use and to determine the order of that group. The group that is used is [BilinearGroup.g].
     *
     * @param privateKey The private key used to sign the message.
     * @param message The message to be signed.
     * @param group The [BilinearGroup] used to create the signatures.
     * @return The [SchnorrSignature] on [message] signed with [privateKey].
     */
    fun schnorrSignature(
        privateKey: Element,
        message: ByteArray,
        group: BilinearGroup
    ): SchnorrSignature {
        val g = group.g
        val order = group.getZrOrder()

        val k = group.getRandomZr()
        val r = g.powZn(k)

        val e = hash(r, message, order)
        val s = k.sub(privateKey.mul(e))

        return SchnorrSignature(s.toBigInteger(), e, message)
    }

    /**
     * Creates a blinded challenge to be signed by the other party. It takes a [BilinearGroup] as input to determine
     * which group to use and to determine the order of that group. The group that is used is [BilinearGroup.g].
     *
     * @param r The randomness parameter supplied by the party that will sign the challenge.
     * @param message The message to be signed.
     * @param signerPublicKey The public key of the party that will sign the challenge.
     * @param group The [BilinearGroup] used to create the signatures.
     *
     * @return A random [BlindedChallenge] based on [r] and [message]. [BlindedChallenge.blindedChallenge]
     * should be send to the signing party.
     */
    fun createBlindedChallenge(
        r: Element,
        message: ByteArray,
        signerPublicKey: Element,
        group: BilinearGroup
    ): BlindedChallenge {
        val g = group.g

        // alpha and beta to blind
        val alpha = group.getRandomZr()
        val beta = group.getRandomZr()

        // Compute r' = r * g^(-alpha) * y^(-beta)
        val rPrime =
            r
                .mul(g.powZn(alpha.mul(-1)))
                .mul(signerPublicKey.powZn(beta.mul(-1)))

        val challenge = hash(rPrime, message, group.getZrOrder())
        val blindedChallenge = (challenge + beta.toBigInteger())

        return BlindedChallenge(challenge, blindedChallenge, alpha.toBigInteger(), message)
    }

    /**
     * Creates a blinded signature of the received challenge.
     *
     * @param k The randomness parameter sent to the requesting party.
     * @param challenge The blinded challenge supplied by the requesting party.
     * @param privateKey The private key to sign the challenge with.
     *
     * @return The signature on [challenge], signed with [privateKey].
     */
    fun signBlindedChallenge(
        k: Element,
        challenge: BigInteger,
        privateKey: Element
    ): BigInteger {
        // Calculate the signature value
        return k.toBigInteger() + challenge * privateKey.mul(-1).toBigInteger()
    }

    /**
     * Unblinds the blind signature received by the other party.
     *
     * @param blindedChallenge The [BlindedChallenge] created in [createBlindedChallenge].
     * @param blindSignature The signature received from the other party.
     * @return The blinded [SchnorrSignature] on [BlindedChallenge.message]
     */
    fun unblindSignature(
        blindedChallenge: BlindedChallenge,
        blindSignature: BigInteger
    ): SchnorrSignature {
        val (challenge, _, alpha, message) = blindedChallenge
        val signature = blindSignature - alpha
        return SchnorrSignature(signature, challenge, message)
    }

    /**
     * Verifies if the given [SchnorrSignature] is a valid signature of the authority of [publicKey].
     * It takes a [BilinearGroup] as input to determine which group to use and
     * to determine the order of that group. The group that is used is [BilinearGroup.g].
     *
     * @param schnorrSignature The [SchnorrSignature] to verify.
     * @param publicKey The public key of the signing authority.
     * @param group The [BilinearGroup] used to create the signatures.
     * @return True, if and only if, [SchnorrSignature.signature] is a valid
     * signature on [SchnorrSignature.signedMessage] signed by [publicKey].
     */
    fun verifySchnorrSignature(
        schnorrSignature: SchnorrSignature,
        publicKey: Element,
        group: BilinearGroup
    ): Boolean {
        val (signature, encryption, signedMessage) = schnorrSignature
        val g = group.g
        val order = group.getZrOrder()

        val rv = g.pow(signature).mul(publicKey.pow(encryption))
        val ev = hash(rv, signedMessage, order)
        return ev == encryption
    }

    /**
     * Calculates the hash-value of [randomness] concatenated with [message] in mod [modulo].
     *
     * @param randomness The randomness to use.
     * @param message The message to be hashed.
     * @param modulo The modulo to be used.
     * @return The hash-value as a [BigInteger]
     */
    private fun hash(
        randomness: Element,
        message: ByteArray,
        modulo: BigInteger
    ): BigInteger {
        val data = randomness.toBytes() + message
        return calculateHash(data).mod(modulo)
    }

    /**
     * Calculates the hash-value of [data].
     *
     * @param data The data to hash
     * @return The hash-value as a [BigInteger]
     */
    private fun calculateHash(data: ByteArray): BigInteger {
        val sha256Digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = sha256Digest.digest(data)
        val hexString = hashBytes.joinToString("") { "%02x".format(it) }
        return BigInteger(hexString, 16)
    }
}
