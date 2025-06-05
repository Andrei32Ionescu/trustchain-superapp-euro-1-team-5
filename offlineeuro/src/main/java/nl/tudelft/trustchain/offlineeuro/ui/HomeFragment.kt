package nl.tudelft.trustchain.offlineeuro.ui

import android.app.AlertDialog
import android.content.Intent
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
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.Dispatchers
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
    private val client = OkHttpClient()
    private val presentationURL = "https://verifier-backend.eudiw.dev/ui/presentations"
    private val valdiationURL = "https://verifier-backend.eudiw.dev/utilities/validations/msoMdoc/deviceResponse"

    private var transactionId = ""
    private var userName = ""
    private var eudiFinished = false
    private var shouldNavigateOnResume = false

    private lateinit var user: User
    private lateinit var community: OfflineEuroCommunity
    private lateinit var communicationProtocol: IPV8CommunicationProtocol

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
            showEUDIWindow()
        }

        // Set negative button
        alertDialogBuilder.setNegativeButton("Cancel") { dialog, which ->
            dialog.cancel()
        }

        // Create and show the AlertDialog
        val alertDialog = alertDialogBuilder.create()
        alertDialog.show()
    }

    private fun showEUDIWindow() {
        val alertDialogBuilder = AlertDialog.Builder(requireContext())

        alertDialogBuilder.setTitle("Verify Identity")

        // Set positive button
        alertDialogBuilder.setPositiveButton("Verify") { dialog, which ->
            lifecycleScope.launch {
                try {
                    transactionId = getEUDI()!!
                    Log.d("EUDI", "got transaction id $transactionId")
//                    createUser()
                } catch (e: Exception) {
                    Log.e("Error", "EUDI authentication failed", e)
                }
            }
        }

        // Set negative button
        alertDialogBuilder.setNegativeButton("Cancel") { dialog, which ->
            dialog.cancel()
        }

        // Create and show the AlertDialog
        val alertDialog = alertDialogBuilder.create()
        alertDialog.show()
    }

    suspend fun getEUDI(): String? {
        val body = """
                {
                  "type": "vp_token",
                  "presentation_definition": {
                    "id": "2d4cf775-3ee3-4f56-9f4b-03cdbcbcca8b",
                    "input_descriptors": [
                      {
                        "id": "eu.europa.ec.eudi.pid.1",
                        "format": {
                          "mso_mdoc": {
                            "alg": [
                              "ES256",
                              "ES384",
                              "ES512",
                              "EdDSA"
                            ]
                          }
                        },
                        "constraints": {
                          "limit_disclosure": "required",
                          "fields": [
                            {
                              "path": [
                                "${'$'}['eu.europa.ec.eudi.pid.1']['family_name']"
                              ],
                              "intent_to_retain": false
                            }
                          ]
                        }
                      }
                    ]
                  },
                  "nonce": "29546f2b-d085-4588-b2bc-5c3523b0c79d",
                  "request_uri_method": "get"
                }
            """.trimIndent()


        // Start verifier transaction
        val result = makeAPIRequest(presentationURL, body, "application/json; charset=utf-8".toMediaType()) ?: return null

        // Parse JSON
        val jsonObject = JSONObject(result)
        val transactionId = jsonObject.optString("transaction_id")
        val clientId = jsonObject.optString("client_id")
        val requestURI = jsonObject.optString("request_uri")
        val requestURIMethod = "get"

        // Send authorization request to the wallet to handle it
        val walletRequestURL = "eudi-openid4vp://?client_id=$clientId&request_uri=$requestURI&request_uri_method=$requestURIMethod"

        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = walletRequestURL.toUri()
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        activityResultLauncher.launch(intent)

        return transactionId
    }



    private suspend fun makeAPIRequest(url: String, body: String, mediaType: MediaType): String? {
        Log.d("EUDI", "Calling api endpoint: $url")

        return withContext(Dispatchers.IO) {
            try {
                val request = if (body != "") {
                    val requestBody = body.toRequestBody(mediaType)

                    Request.Builder()
                        .url(url)
                        .post(requestBody)
                        .build()
                } else {
                    Request.Builder()
                        .url(url)
                        .get()
                        .build()
                }

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e("EUDI", "Unsuccessful response: ${response.code}")
                        Log.e("EUDI", "Unsuccessful response: ${response.body?.string()}")
                        return@withContext null
                    }

                    val responseBodyString = response.body?.string()
                    if (responseBodyString.isNullOrEmpty()) {
                        Log.e("EUDI", "Empty response body")
                        return@withContext null
                    }

                    Log.d("EUDI", "Response: $responseBodyString")

                    return@withContext responseBodyString
                }
            } catch (e: IOException) {
                Log.e("EUDI", "IOException: ${e.message}", e)
                return@withContext null
            } catch (e: JSONException) {
                Log.e("EUDI", "JSON parsing error: ${e.message}", e)
                return@withContext null
            } catch (e: Exception) {
                Log.e("EUDI", "Exception: ${e.message}", e)
                return@withContext null
            }
        }
    }

    private val activityResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        shouldNavigateOnResume = true
    }

    override fun onResume() {
        super.onResume()

        if (shouldNavigateOnResume) {
            createUser()
        }


        if (shouldNavigateOnResume && eudiFinished) {
            shouldNavigateOnResume = false
            moveToUserHome()
        }
    }

    private val onRegister: () -> Unit = {
        eudiFinished = true;
    }

    private fun createUser() {
        val community = getIpv8().getOverlay<OfflineEuroCommunity>()!!

        val group = BilinearGroup(PairingTypes.FromFile, context = context)
        val addressBookManager = AddressBookManager(context, group)
        communicationProtocol = IPV8CommunicationProtocol(addressBookManager, community)
        try {
            user = User(userName, group, context, null, communicationProtocol, onDataChangeCallback = onUserDataChangeCallBack, onRegister=onRegister, transactionId=transactionId)
            communicationProtocol.scopePeers()
            val addresses = communicationProtocol.addressBookManager.getAllAddresses()
            Log.d("EUDI", "$addresses")
        } catch (e: Exception) {
            Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
        }
        Log.d("EUDI", "Created user")

    }

    private fun moveToUserHome() {
        if (isAdded && !requireActivity().isFinishing) {
            try {
                ParticipantHolder.user = user
                findNavController().navigate(R.id.nav_home_userhome, null)
            } catch (e: Exception) {
                Log.e("Navigation", "Failed to navigate", e)
            }
        }
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
