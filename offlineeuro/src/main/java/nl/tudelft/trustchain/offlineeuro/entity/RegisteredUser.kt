package nl.tudelft.trustchain.offlineeuro.entity

import it.unisa.dia.gas.jpbc.Element

data class RegisteredUser(
    val id: Long,
    val name: String,
    val publicKey: Element,
    val legalName: String
)
