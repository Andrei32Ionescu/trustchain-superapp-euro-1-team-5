package nl.tudelft.trustchain.offlineeuro.ui

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import nl.tudelft.trustchain.offlineeuro.R
import nl.tudelft.trustchain.offlineeuro.communication.IPV8CommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.community.OfflineEuroCommunity
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.PairingTypes
import nl.tudelft.trustchain.offlineeuro.db.AddressBookManager
import nl.tudelft.trustchain.offlineeuro.entity.User

class UserHomeFragment : OfflineEuroBaseFragment(R.layout.fragment_user_home) {
    private lateinit var user: User
    private lateinit var community: OfflineEuroCommunity
    private lateinit var communicationProtocol: IPV8CommunicationProtocol

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)
        if (ParticipantHolder.user != null) {
            user = ParticipantHolder.user!!
            communicationProtocol = user.communicationProtocol as IPV8CommunicationProtocol
            val userName: String = user.name
            val welcomeTextView = view.findViewById<TextView>(R.id.user_home_welcome_text)
            welcomeTextView.text = welcomeTextView.text.toString().replace("_name_", userName)
        } else {
            activity?.title = "User"
            val userName: String? = arguments?.getString("userName")
            val legalName: String? = arguments?.getString("legalName")
            val welcomeTextView = view.findViewById<TextView>(R.id.user_home_welcome_text)
            welcomeTextView.text = welcomeTextView.text.toString().replace("_name_", userName!!)
            community = getIpv8().getOverlay<OfflineEuroCommunity>()!!

            val group = BilinearGroup(PairingTypes.FromFile, context = context)
            val addressBookManager = AddressBookManager(context, group)
            communicationProtocol = IPV8CommunicationProtocol(addressBookManager, community)
            try {
                user = User(userName, group, context, null, communicationProtocol, onDataChangeCallback = onUserDataChangeCallBack, legalName=legalName)
                communicationProtocol.scopePeers()
            } catch (e: Exception) {
                Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
            }
        }

        view.findViewById<Button>(R.id.user_home_reset_button).setOnClickListener {
            communicationProtocol.addressBookManager.clear()
            user.reset()
            val addressList = view.findViewById<LinearLayout>(R.id.user_home_addresslist)
            val addresses = communicationProtocol.addressBookManager.getAllAddresses()
            TableHelpers.addAddressesToTable(addressList, addresses, user, requireContext())
        }
        view.findViewById<Button>(R.id.user_home_sync_addresses).setOnClickListener {
            communicationProtocol.scopePeers()
        }
        val addressList = view.findViewById<LinearLayout>(R.id.user_home_addresslist)
        val addresses = communicationProtocol.addressBookManager.getAllAddresses()
        TableHelpers.addAddressesToTable(addressList, addresses, user, requireContext())
        onUserDataChangeCallBack(null)
    }

    private val onUserDataChangeCallBack: (String?) -> Unit = { message ->
        requireActivity().runOnUiThread {
            val context = requireContext()
            if (this::user.isInitialized) {
                CallbackLibrary.userCallback(
                    context,
                    message,
                    requireView(),
                    communicationProtocol,
                    user
                )
            }
        }
    }
}
