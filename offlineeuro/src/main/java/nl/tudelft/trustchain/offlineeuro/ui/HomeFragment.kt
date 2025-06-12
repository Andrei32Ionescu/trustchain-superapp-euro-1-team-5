package nl.tudelft.trustchain.offlineeuro.ui

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.tudelft.trustchain.offlineeuro.R
import nl.tudelft.trustchain.offlineeuro.communication.IPV8CommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.community.OfflineEuroCommunity
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.PairingTypes
import nl.tudelft.trustchain.offlineeuro.db.AddressBookManager
import nl.tudelft.trustchain.offlineeuro.entity.User
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

class HomeFragment : OfflineEuroBaseFragment(R.layout.fragment_home) {
    private var transactionId = ""
    private var userName = ""
    private var eudiFinished = false
    private var shouldNavigateOnResume = false

    private lateinit var user: User
    private lateinit var community: OfflineEuroCommunity
    private lateinit var communicationProtocol: IPV8CommunicationProtocol

    private val walletReturned      = CompletableDeferred<Unit>()
    private val registrationReplied = CompletableDeferred<Unit>()

    private var pendingTxId: String? = null
//    private val WALLET_PACKAGE = "eu.europa.ec.euidi"

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)
        activity?.title = "Home"

        view.findViewById<Button>(R.id.JoinAsTTP).setOnClickListener {
            findNavController().navigate(R.id.nav_home_ttphome)
        }

        view.findViewById<Button>(R.id.JoinAsBankButton).setOnClickListener {
            findNavController().navigate(R.id.nav_home_bankhome)
        }

        view.findViewById<Button>(R.id.JoinAsUserButton).setOnClickListener {
            showAlertDialog()
        }
        view.findViewById<Button>(R.id.JoinAsAllRolesButton).setOnClickListener {
            findNavController().navigate(R.id.nav_home_all_roles_home)
        }
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        try {
            val euroTokenCommunity = getIpv8().getOverlay<OfflineEuroCommunity>()
            if (euroTokenCommunity == null) {
                Toast.makeText(requireContext(), "Could not find community", Toast.LENGTH_LONG)
                    .show()
            }
            if (euroTokenCommunity != null) {
                Toast.makeText(requireContext(), "Found community", Toast.LENGTH_LONG)
                    .show()
            }
        } catch (e: Exception) {
            logger.error { e }
            Toast.makeText(
                requireContext(),
                "Failed to send transactions",
                Toast.LENGTH_LONG
            )
                .show()
        }
        return
    }

    private fun showAlertDialog() {
        val alertDialogBuilder = AlertDialog.Builder(requireContext())

        val editText = EditText(requireContext())
        alertDialogBuilder.setView(editText)
        alertDialogBuilder.setTitle("Pick an username")
        alertDialogBuilder.setMessage("")
        // Set positive button
        alertDialogBuilder.setPositiveButton("Join!") { dialog, which ->
            userName = editText.text.toString()
            createUser()
        }

        // Set negative button
        alertDialogBuilder.setNegativeButton("Cancel") { dialog, which ->
            dialog.cancel()
        }

        // Create and show the AlertDialog
        val alertDialog = alertDialogBuilder.create()
        alertDialog.show()
    }

    // weird warning. application fully functions
    @SuppressLint("RepeatOnLifecycleWrongUsage")
    private suspend fun navigateToUserHomeSafely() = withContext(Dispatchers.Main) {
        repeatOnLifecycle(Lifecycle.State.RESUMED) {
            // run once, then cancel this inner coroutine
            ParticipantHolder.user = user
            findNavController().navigate(R.id.nav_home_userhome)
            this.cancel()
        }
    }

    fun onReqUserVerif(walletRequestURL: String, txId: String): Unit {
        val alertDialogBuilder = AlertDialog.Builder(requireContext())
        pendingTxId = txId
        requireActivity().runOnUiThread {
            AlertDialog.Builder(requireContext())
                .setTitle("Verify identity")
                .setPositiveButton("Verify") { _, _ ->
                    openWallet(walletRequestURL)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun openWallet(deeplink: String) {
        val walletIntent = Intent(Intent.ACTION_VIEW, Uri.parse(deeplink)).apply {
//            setPackage(WALLET_PACKAGE)
            addCategory(Intent.CATEGORY_BROWSABLE)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(walletIntent)
        pendingTxId?.let { tx ->
            val ttpPk = communicationProtocol.addressBookManager
                .getAddressByName("TTP").peerPublicKey!!
            communicationProtocol.sendUserSubmitVerificationMessage(tx, ttpPk)
            pendingTxId = null
        }
        walletReturned.complete(Unit)
    }

    override fun onResume() {
        super.onResume()

        if (shouldNavigateOnResume) {
            createUser()
        }


        if (shouldNavigateOnResume && eudiFinished) {
            shouldNavigateOnResume = false
            Log.d("EUDI", "moving to user home")
            moveToUserHome()
        }
    }

    private val onRegister: () -> Unit = {
        Log.d("EUDI","on register")
        eudiFinished = true;
        registrationReplied.complete(Unit)
    }

    private fun createUser() {
        val community = getIpv8().getOverlay<OfflineEuroCommunity>()!!

        val group = BilinearGroup(PairingTypes.FromFile, context = context)
        val addressBookManager = AddressBookManager(context, group)
        communicationProtocol = IPV8CommunicationProtocol(addressBookManager, community)
        try {
            user = User(userName, group, context, null, communicationProtocol, onRegister=onRegister, transactionId=transactionId,
                onReqUserVerif={ link:String, txId:String -> onReqUserVerif(link, txId) })
            communicationProtocol.scopePeers()
            val addresses = communicationProtocol.addressBookManager.getAllAddresses()
            Log.d("EUDI", "$addresses")
        } catch (e: Exception) {
            Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
        }
        Log.d("EUDI", "Created user")

        lifecycleScope.launch {
            walletReturned.await() // returned from app
            registrationReplied.await() // got response from TTP

            navigateToUserHomeSafely()
        }
    }

    private fun moveToUserHome() {
        if (isAdded && !requireActivity().isFinishing) {
            Log.d("EUDI", "moving...")
            try {
                ParticipantHolder.user = user
                findNavController().navigate(R.id.nav_home_userhome, null)
            } catch (e: Exception) {
                Log.e("Navigation", "Failed to navigate", e)
            }
        }
    }

//    private val activityResultLauncher = registerForActivityResult(
//        ActivityResultContracts.StartActivityForResult()
//    ) { result ->
////        shouldNavigateOnResume = true
//        val tx = pendingTxId ?: return@registerForActivityResult
//
//        val ttpPkBytes = communicationProtocol
//            .addressBookManager
//            .getAddressByName("TTP")
//            .peerPublicKey
//
//        communicationProtocol
//            .sendUserSubmitVerificationMessage(tx, ttpPkBytes!!)
//
//        pendingTxId = null
//        walletReturned.complete(Unit)
//    }
}
