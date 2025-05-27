package nl.tudelft.trustchain.offlineeuro.entity

import android.content.Context
import it.unisa.dia.gas.jpbc.Element
import nl.tudelft.trustchain.offlineeuro.communication.ICommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.CRSGenerator
import nl.tudelft.trustchain.offlineeuro.cryptography.GrothSahaiProof
import nl.tudelft.trustchain.offlineeuro.db.RegisteredUserManager
import nl.tudelft.trustchain.offlineeuro.enums.Role

class TTP(
    name: String = "TTP",
    group: BilinearGroup,
    communicationProtocol: ICommunicationProtocol,
    context: Context?,
    private val registeredUserManager: RegisteredUserManager = RegisteredUserManager(context, group),
    onDataChangeCallback: ((String?) -> Unit)? = null
) : Participant(communicationProtocol, name, onDataChangeCallback, Role.TTP) {
    val crsMap: Map<Element, Element>

    init {
        communicationProtocol.participant = this
        this.group = group
        val testList = ArrayList<String>();
        testList.add("test");
        testList.add("test2");
        testList.add("test3");
        val generatedCRS = CRSGenerator.generateCRSMap(group, testList)
        this.crs = generatedCRS.first
        this.crsMap = generatedCRS.second
        generateKeyPair()
    }

    fun registerUser(
        name: String,
        publicKey: Element,
        role: Role
    ): Boolean {
        val result = registeredUserManager.addRegisteredUser(name, publicKey)
        onDataChangeCallback?.invoke("Registered $name")
        if(role == Role.Bank){
            print("Bank registered")
            crs.setBankPKs(crs.getBankPKs() + publicKey.toString())
        }
        return result
    }

    fun getRegisteredUsers(): List<RegisteredUser> {
        return registeredUserManager.getAllRegisteredUsers()
    }

    override fun onReceivedTransaction(
        transactionDetails: TransactionDetails,
        publicKeyBank: Element,
        publicKeySender: Element
    ): String {
        TODO("Not yet implemented")
    }

    fun getUserFromProof(grothSahaiProof: GrothSahaiProof): RegisteredUser? {
        val crsExponent = crsMap[crs.u]
        val test = group.g.powZn(crsExponent)
        val publicKey =
            grothSahaiProof.c1.powZn(crsExponent!!.mul(-1)).mul(grothSahaiProof.c2).immutable

        return registeredUserManager.getRegisteredUserByPublicKey(publicKey)
    }

    fun getUserFromProofs(
        firstProof: GrothSahaiProof,
        secondProof: GrothSahaiProof
    ): String {
        val firstPK = getUserFromProof(firstProof)
        val secondPK = getUserFromProof(secondProof)

        return if (firstPK != null && firstPK == secondPK) {
            onDataChangeCallback?.invoke("Found proof that  ${firstPK.name} committed fraud!")
            "Double spending detected. Double spender is ${firstPK.name} with PK: ${firstPK.publicKey}"
        } else {
            onDataChangeCallback?.invoke("Invalid fraud request received!")
            "No double spending detected"
        }
    }

    override fun reset() {
        registeredUserManager.clearAllRegisteredUsers()
    }
}
