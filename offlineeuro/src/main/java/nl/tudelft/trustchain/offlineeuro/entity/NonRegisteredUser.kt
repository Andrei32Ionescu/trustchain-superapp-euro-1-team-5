package nl.tudelft.trustchain.offlineeuro.entity

import it.unisa.dia.gas.jpbc.Element

data class NonRegisteredUser(
    val id: Long,
    val name: String,
    val publicKey: Element,
    val transactionId: String,
)
