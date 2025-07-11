package nl.tudelft.trustchain.offlineeuro.entity

import android.util.Log
import it.unisa.dia.gas.jpbc.Element
import nl.tudelft.trustchain.offlineeuro.communication.ICommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.CRS
import nl.tudelft.trustchain.offlineeuro.cryptography.GrothSahai
import nl.tudelft.trustchain.offlineeuro.cryptography.RandomizationElements
import nl.tudelft.trustchain.offlineeuro.enums.Role

abstract class Participant(
    val communicationProtocol: ICommunicationProtocol,
    val name: String,
    var onDataChangeCallback: ((String?) -> Unit)? = null,
    val role: Role
) {
    public lateinit var privateKey: Element
    lateinit var publicKey: Element
    lateinit var group: BilinearGroup
    val randomizationElementMap: HashMap<Element, Element> = hashMapOf()
    lateinit var crs: CRS

    fun setUp() {
        Log.d("EUDI", "User setp 1")
        getGroupDescriptionAndCRS()
        Log.d("EUDI", "User setp 2")
        generateKeyPair()
        Log.d("EUDI", "User setp 3")
        registerAtTTP()
    }

    fun getGroupDescriptionAndCRS() {
        communicationProtocol.getGroupDescriptionAndCRS()
    }

    fun generateKeyPair() {
        privateKey = group.getRandomZr()
        publicKey = group.g.powZn(privateKey)
    }

    open fun registerAtTTP() {
        // TODO NAME OF TTP
        communicationProtocol.register(name, publicKey, "TTP", role)
    }

    fun generateRandomizationElements(receiverPublicKey: Element): RandomizationElements {
        val randomT = group.getRandomZr()
        randomizationElementMap[receiverPublicKey] = randomT
        return GrothSahai.tToRandomizationElements(randomT, group, crs)
    }

    fun lookUpRandomness(publicKey: Element): Element? {
        for (element in randomizationElementMap.entries) {
            val key = element.key

            if (key == publicKey) {
                return element.value
            }
        }

        return null
    }

    fun removeRandomness(publicKey: Element) {
        for (element in randomizationElementMap.entries) {
            val key = element.key

            if (key == publicKey) {
                randomizationElementMap.remove(key)
            }
        }
    }

    abstract fun onReceivedTransaction(
        transactionDetails: TransactionDetails,
        publicKeyBank: Element,
        publicKeySender: Element
    ): String

    abstract fun reset()
}
