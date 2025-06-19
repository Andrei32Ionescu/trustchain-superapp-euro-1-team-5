package nl.tudelft.trustchain.offlineeuro.ui

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import nl.tudelft.trustchain.offlineeuro.R
import nl.tudelft.trustchain.offlineeuro.communication.IPV8CommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.entity.Bank
import nl.tudelft.trustchain.offlineeuro.entity.TTP
import nl.tudelft.trustchain.offlineeuro.entity.User

object CallbackLibrary {
    fun bankCallback(
        context: Context,
        message: String?,
        view: View,
        bank: Bank
    ) {
        if (message != null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
        val table = view.findViewById<LinearLayout>(R.id.bank_home_deposited_list)
        TableHelpers.removeAllButFirstRow(table)
        TableHelpers.addDepositedEurosToTable(table, bank)
    }

    fun ttpCallback(
        context: Context,
        message: String?,
        view: View,
        ttp: TTP
    ) {
        if (message != null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
        updateUserList(view, ttp)
    }

    private fun updateUserList(
        view: View,
        ttp: TTP
    ) {
        val table = view.findViewById<LinearLayout>(R.id.tpp_home_registered_user_list) ?: return
        val users = ttp.getRegisteredUsers()
        TableHelpers.removeAllButFirstRow(table)
        TableHelpers.addRegisteredUsersToTable(table, users)
    }

    fun userCallback(
        context: Context,
        message: String?,
        view: View,
        communicationProtocol: IPV8CommunicationProtocol,
        user: User
    ) {
        if (message != null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
        val balanceField = view.findViewById<TextView>(R.id.user_home_balance)

        balanceField.text = buildString {
            append("€")
            append((user.getBalance().toFloat() / 100.0).toString())
        }
        // Update wallet tokens table
        val tokensList = view.findViewById<LinearLayout>(R.id.user_home_tokens_list)
        if (tokensList != null) {
            val walletEntries = user.wallet.getAllWalletEntriesToSpend() +
                user.wallet.getAllWalletEntriesToDoubleSpend()
            TableHelpers.addWalletTokensToTable(tokensList, walletEntries.distinctBy { it.digitalEuro.serialNumber }, user, context)
        }

        val addressList = view.findViewById<LinearLayout>(R.id.user_home_addresslist)
        val addresses = communicationProtocol.addressBookManager.getAllAddresses()
        TableHelpers.addAddressesToTable(addressList, addresses, user, context)
        view.refreshDrawableState()
    }
}
