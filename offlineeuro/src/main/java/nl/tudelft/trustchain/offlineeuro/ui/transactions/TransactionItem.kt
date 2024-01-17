package nl.tudelft.trustchain.offlineeuro.ui.transactions

import com.mattskala.itemadapter.Item
import nl.tudelft.trustchain.common.eurotoken.Transaction

data class TransactionItem(
    val transaction: Transaction
) : Item() {
    override fun areItemsTheSame(other: Item): Boolean {
        return other is TransactionItem && transaction == other.transaction
    }
}
