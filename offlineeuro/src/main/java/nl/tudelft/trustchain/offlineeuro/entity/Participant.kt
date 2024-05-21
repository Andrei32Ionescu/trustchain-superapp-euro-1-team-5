package nl.tudelft.trustchain.offlineeuro.entity

import it.unisa.dia.gas.jpbc.Element
import nl.tudelft.trustchain.offlineeuro.communication.ICommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.CRS
import nl.tudelft.trustchain.offlineeuro.cryptography.GrothSahai
import nl.tudelft.trustchain.offlineeuro.cryptography.RandomizationElements

abstract class Participant(
    val communicationProtocol: ICommunicationProtocol,
    val name: String
) {
    protected lateinit var privateKey: Element
    lateinit var publicKey: Element
    lateinit var group: BilinearGroup
    val randomizationElementMap: HashMap<Element, Element> = hashMapOf()
    lateinit var crs: CRS

    fun setUp() {
        getGroupDescriptionAndCRS()
        generateKeyPair()
        registerAtTTP()
    }

    fun getGroupDescriptionAndCRS() {
        val groupAndCRS = communicationProtocol.getGroupDescriptionAndCRS()
        group = groupAndCRS.first
        crs = groupAndCRS.second
    }

    fun generateKeyPair() {
        privateKey = group.getRandomZr()
        publicKey = group.g.powZn(privateKey)
    }

    fun registerAtTTP() {
        // TODO NAME OF TTP
        communicationProtocol.register(name, publicKey, "TTP")
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

    abstract fun onReceivedTransaction(
        transactionDetails: TransactionDetails,
        publicKeyBank: Element,
        publicKeySender: Element
    ): String
}
