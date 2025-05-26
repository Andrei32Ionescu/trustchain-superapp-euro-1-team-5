package nl.tudelft.trustchain.offlineeuro.ui

import android.app.AlertDialog
import android.content.Context
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import nl.tudelft.trustchain.offlineeuro.R
import nl.tudelft.trustchain.offlineeuro.communication.IPV8CommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.entity.Address
import nl.tudelft.trustchain.offlineeuro.entity.Bank
import nl.tudelft.trustchain.offlineeuro.entity.RegisteredUser
import nl.tudelft.trustchain.offlineeuro.entity.User
import nl.tudelft.trustchain.offlineeuro.entity.WalletEntry
import nl.tudelft.trustchain.offlineeuro.enums.Role

object TableHelpers {
    fun removeAllButFirstRow(table: LinearLayout) {
        val childrenCount = table.childCount
        val childrenToBeRemoved = childrenCount - 1

        for (i in childrenToBeRemoved downTo 1) {
            val row = table.getChildAt(i)
            table.removeView(row)
        }
    }

    fun addRegisteredUsersToTable(
        table: LinearLayout,
        users: List<RegisteredUser>
    ) {
        val context = table.context
        for (user in users) {
            table.addView(registeredUserToTableRow(user, context))
        }
    }

    private fun registeredUserToTableRow(
        user: RegisteredUser,
        context: Context,
    ): LinearLayout {
        val layout =
            LinearLayout(context).apply {
                layoutParams = rowParams()
                orientation = LinearLayout.HORIZONTAL
            }

        val idField =
            TextView(context).apply {
                text = user.id.toString()
                layoutParams = layoutParams(0.2f)
                gravity = Gravity.CENTER_HORIZONTAL
            }

        val nameField =
            TextView(context).apply {
                text = user.name
                layoutParams = layoutParams(0.2f)
                gravity = Gravity.CENTER_HORIZONTAL
            }

        val publicKeyField =
            TextView(context).apply {
                text = user.publicKey.toString()
                layoutParams = layoutParams(0.7f)
            }

        layout.addView(idField)
        layout.addView(nameField)
        layout.addView(publicKeyField)
        return layout
    }

    fun addDepositedEurosToTable(
        table: LinearLayout,
        bank: Bank
    ) {
        val context = table.context
        for (depositedEuro in bank.depositedEuroLogger) {
            table.addView(depositedEuroToTableRow(depositedEuro, context))
        }
    }

    private fun depositedEuroToTableRow(
        depositedEuro: Pair<String, Boolean>,
        context: Context
    ): LinearLayout {
        val layout =
            LinearLayout(context).apply {
                layoutParams = rowParams()
                orientation = LinearLayout.HORIZONTAL
            }

        val numberField =
            TextView(context).apply {
                text = depositedEuro.first
                layoutParams = layoutParams(0.7f)
                gravity = Gravity.CENTER_HORIZONTAL
            }

        val doubleSpendingField =
            TextView(context).apply {
                text = depositedEuro.second.toString()
                layoutParams = layoutParams(0.4f)
                gravity = Gravity.CENTER_HORIZONTAL
            }

        layout.addView(numberField)
        layout.addView(doubleSpendingField)
        return layout
    }

    fun addWalletTokensToTable(
        table: LinearLayout,
        walletEntries: List<WalletEntry>,
        user: User,
        context:Context
    ) {
        removeAllButFirstRow(table)
        for (walletEntry in  walletEntries){
            table.addView(walletEntryToTableRow(walletEntry,context,user))
        }
    }

    // Convert wallet entry to table row
    private fun walletEntryToTableRow(
        walletEntry: WalletEntry,
        context: Context,
        user: User
    ): LinearLayout{
        val tableRow = LinearLayout(context)
        tableRow.layoutParams = rowParams()
        tableRow.orientation = LinearLayout.HORIZONTAL

        val styledContext = ContextThemeWrapper(context, R.style.TableCell)

        //Token amount field
        val amountField = TextView (styledContext).apply{
            layoutParams= layoutParams(0.2f)
            text= "€${walletEntry.digitalEuro.amount}"
        }
        // Token status field
        val statusField = TextView(styledContext).apply {
            layoutParams = layoutParams(0.2f)
            text = when (walletEntry.timesSpent) {
                0L -> "Unspent"
                1L -> "Spent"
                else -> "Double-spent"
            }
        }
        // Serial number field (truncated)
        val serialField = TextView(styledContext).apply {
            layoutParams = layoutParams(0.3f)
            text = walletEntry.digitalEuro.serialNumber.take(8) + "..."
        }
        tableRow.addView(amountField)
        tableRow.addView(statusField)
        tableRow.addView(serialField)

        // Action buttons
        val buttonWrapper = LinearLayout(context)
        val params = layoutParams(0.3f)
        buttonWrapper.gravity = Gravity.CENTER_HORIZONTAL
        buttonWrapper.orientation = LinearLayout.HORIZONTAL
        buttonWrapper.layoutParams = params

        val sendButton = Button(context)
        val depositButton = Button(context)

        applyButtonStyling(sendButton, context)
        applyButtonStyling(depositButton, context)

        setTokenActionButtons(sendButton, depositButton, walletEntry, user, context)

        buttonWrapper.addView(sendButton)
        buttonWrapper.addView(depositButton)
        tableRow.addView(buttonWrapper)

        return tableRow
    }

    //Set action buttons for tokens
    private fun setTokenActionButtons(
        sendButton: Button,
        depositButton: Button,
        walletEntry: WalletEntry,
        user: User,
        context: Context
    ){
        val digitalEuro = walletEntry.digitalEuro
        if( walletEntry.timesSpent == 0L){
            //Unspent token- can send or deposit
            sendButton.text="Send"
            sendButton.setOnClickListener {
                showUserSelectionDialog(context, user) { selectedUser ->
                    try {
                        val result = user.sendSpecificDigitalEuroTo(digitalEuro, selectedUser)
                        Toast.makeText(context, "Sent successfully: $result", Toast.LENGTH_SHORT)
                            .show()
                    } catch (e: Exception) {
                        Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            //DEPOSIT
            depositButton.text = "Deposit"
            depositButton.setOnClickListener{
                showBankSelectionDialog(context, user) { selectedBank ->
                    try {
                        val result = user.sendSpecificDigitalEuroTo(digitalEuro, selectedBank)
                        Toast.makeText(context, "Deposited successfully: $result", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else if (walletEntry.timesSpent==1L) {
            // Spent once - can double spend
            sendButton.text = "Double Spend"
            sendButton.setOnClickListener {
                showUserSelectionDialog(context, user) { selectedUser ->
                    try {
                        val result = user.doubleSpendSpecificDigitalEuroTo(digitalEuro, selectedUser)
                        Toast.makeText(context, "Double spent: $result", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }

            depositButton.text = "-"
            depositButton.isEnabled = false
        } else {
            // Already double spent - no actions available
            sendButton.text = "-"
            sendButton.isEnabled = false
            depositButton.text = "-"
            depositButton.isEnabled = false
        }
    }
    // NEW: Show user selection dialog
    private fun showUserSelectionDialog(
        context: Context,
        user: User,
        onUserSelected: (String) -> Unit
    ) {
        val communicationProtocol = user.communicationProtocol as IPV8CommunicationProtocol
        val addresses = communicationProtocol.addressBookManager.getAllAddresses()
        val users = addresses.filter { it.type == Role.User }.map { it.name }

        if (users.isEmpty()) {
            Toast.makeText(context, "No users available", Toast.LENGTH_SHORT).show()
            return
        }

        val builder = AlertDialog.Builder(context)
        builder.setTitle("Select User")
        builder.setItems(users.toTypedArray()) { _, which ->
            onUserSelected(users[which])
        }
        builder.show()
    }
    // Show bank selection dialog
    private fun showBankSelectionDialog(
        context: Context,
        user: User,
        onBankSelected: (String) -> Unit
    ) {
        val communicationProtocol = user.communicationProtocol as IPV8CommunicationProtocol
        val addresses = communicationProtocol.addressBookManager.getAllAddresses()
        val banks = addresses.filter { it.type == Role.Bank }.map { it.name }

        if (banks.isEmpty()) {
            Toast.makeText(context, "No banks available", Toast.LENGTH_SHORT).show()
            return
        }

        val builder = AlertDialog.Builder(context)
        builder.setTitle("Select Bank")
        builder.setItems(banks.toTypedArray()) { _, which ->
            onBankSelected(banks[which])
        }
        builder.show()
    }

    fun addAddressesToTable(
        table: LinearLayout,
        addresses: List<Address>,
        user: User,
        context: Context
    ) {
        removeAllButFirstRow(table)
        for (address in addresses) {
            table.addView(addressToTableRow(address, context, user))
        }
    }

    private fun addressToTableRow(
        address: Address,
        context: Context,
        user: User
    ): LinearLayout {
        val tableRow = LinearLayout(context)
        tableRow.layoutParams = rowParams()
        tableRow.orientation = LinearLayout.HORIZONTAL

        val styledContext = ContextThemeWrapper(context, R.style.TableCell)
        val nameField =
            TextView(styledContext).apply {
                layoutParams = layoutParams(0.5f)
                text = address.name
            }

        val roleField =
            TextView(styledContext).apply {
                layoutParams = layoutParams(0.2f)
                text = address.type.toString()
            }
        tableRow.addView(nameField)
        tableRow.addView(roleField)

        if (address.type == Role.TTP) {
            val actionField =
                TextView(context).apply {
                    layoutParams = layoutParams(0.8f)
                }
            tableRow.addView(actionField)
        } else {
            val buttonWrapper = LinearLayout(context)
            val params = layoutParams(0.8f)
            buttonWrapper.gravity = Gravity.CENTER_HORIZONTAL
            buttonWrapper.orientation = LinearLayout.HORIZONTAL
            buttonWrapper.layoutParams = params

            val mainActionButton = Button(context)
            val secondaryButton = Button(context)

            applyButtonStyling(mainActionButton, context)
            applyButtonStyling(secondaryButton, context)

            buttonWrapper.addView(mainActionButton)
            buttonWrapper.addView(secondaryButton)

            when (address.type) {
                Role.Bank -> {
                    setBankActionButtons(mainActionButton, secondaryButton, address.name, user, context)
                }
                Role.User -> {
                    setUserActionButtons(mainActionButton, secondaryButton, address.name, user, context)
                }

                else -> {}
            }

            tableRow.addView(buttonWrapper)
        }

        return tableRow
    }

    fun setBankActionButtons(
        mainButton: Button,
        secondaryButton: Button,
        bankName: String,
        user: User,
        context: Context
    ) {
        mainButton.text = "Withdraw"
        mainButton.setOnClickListener {
            try {
                val amount = 200L  // This should come from user input
                val digitalEuro = user.withdrawDigitalEuro(bankName, amount)
                Toast.makeText(context, "Successfully withdrawn €${digitalEuro.amount.toFloat()/100.0}", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
            }
        }

        secondaryButton.text = "Deposit"
        secondaryButton.setOnClickListener {
            try {
                val depositResult = user.sendDigitalEuroTo(bankName)

                Toast.makeText(context, depositResult, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun setUserActionButtons(
        mainButton: Button,
        secondaryButton: Button,
        userName: String,
        user: User,
        context: Context
    ) {
        mainButton.text = "Send Euro"
        mainButton.setOnClickListener {
            try {
                val result = user.sendDigitalEuroTo(userName)
            } catch (e: Exception) {
                Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
            }
        }

        secondaryButton.text = "Double Spend"
        secondaryButton.setOnClickListener {
            try {
                val result = user.doubleSpendDigitalEuroTo(userName)
            } catch (e: Exception) {
                Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun layoutParams(weight: Float): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.MATCH_PARENT,
            weight
        )
    }

    fun rowParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    fun applyButtonStyling(
        button: Button,
        context: Context
    ) {
        button.setTextColor(context.getColor(R.color.white))
        button.background.setTint(context.resources.getColor(R.color.colorPrimary))
        button.isAllCaps = false
        button.textSize = 12f
        button.setPadding(14, 14, 14, 14)
        button.letterSpacing = 0f
    }
}
