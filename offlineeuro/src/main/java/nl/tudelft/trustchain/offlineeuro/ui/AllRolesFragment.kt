package nl.tudelft.trustchain.offlineeuro.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import nl.tudelft.trustchain.offlineeuro.R
import nl.tudelft.trustchain.offlineeuro.communication.IPV8CommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.community.OfflineEuroCommunity
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.PairingTypes
import nl.tudelft.trustchain.offlineeuro.db.AddressBookManager
import nl.tudelft.trustchain.offlineeuro.entity.Address
import nl.tudelft.trustchain.offlineeuro.entity.Bank
import nl.tudelft.trustchain.offlineeuro.entity.TTP
import nl.tudelft.trustchain.offlineeuro.entity.User
import nl.tudelft.trustchain.offlineeuro.enums.Role

import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import androidx.core.net.toUri

class AllRolesFragment : OfflineEuroBaseFragment(R.layout.fragment_all_roles_home) {
    private lateinit var iPV8CommunicationProtocol: IPV8CommunicationProtocol
    private lateinit var community: OfflineEuroCommunity

    private lateinit var ttp: TTP
    private lateinit var bank: Bank
    private lateinit var user: User

    private val mainScope = CoroutineScope(Dispatchers.Main + Job())
    private val client = OkHttpClient()



    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)
        activity?.title = "Flexible role"
        community = getIpv8().getOverlay<OfflineEuroCommunity>()!!
        val group = BilinearGroup(PairingTypes.FromFile, context = context)
        val addressBookManager = AddressBookManager(context, group)
        iPV8CommunicationProtocol = IPV8CommunicationProtocol(addressBookManager, community)
        ttp = TTP("TTP", group, iPV8CommunicationProtocol, context, onDataChangeCallback = onTTPDataChangeCallback)

        bank = Bank("Bank", group, iPV8CommunicationProtocol, context, runSetup = false, onDataChangeCallback = onBankDataChangeCallBack)
        user =
            User(
                "TestUser",
                group,
                context,
                null,
                iPV8CommunicationProtocol,
                runSetup = false,
                onDataChangeCallback = onUserDataChangeCallBack
            )

        bank.group = ttp.group
        bank.crs = ttp.crs
        bank.generateKeyPair()

        user.group = ttp.group
        user.crs = ttp.crs

        ParticipantHolder.ttp = ttp
        ParticipantHolder.bank = bank
        ParticipantHolder.user = user

        iPV8CommunicationProtocol.participant = ttp

        ttp.registerUser(user.name, user.publicKey)
        ttp.registerUser(bank.name, bank.publicKey)

        iPV8CommunicationProtocol.addressBookManager.insertAddress(Address(bank.name, Role.Bank, bank.publicKey, null))
        iPV8CommunicationProtocol.addressBookManager.insertAddress(Address(user.name, Role.User, user.publicKey, null))
        iPV8CommunicationProtocol.addressBookManager.insertAddress(Address(ttp.name, Role.TTP, ttp.publicKey, null))

        prepareButtons(view)
        setTTPAsChild()
        updateUserList(view)
    }

    private fun prepareButtons(view: View) {
        val ttpButton = view.findViewById<Button>(R.id.all_roles_set_ttp)
        val bankButton = view.findViewById<Button>(R.id.all_roles_set_bank)
        val userButton = view.findViewById<Button>(R.id.all_roles_set_user)
        val eudiButton = view.findViewById<Button>(R.id.all_roles_eudi_button)

        ttpButton.setOnClickListener {
            ttpButton.isEnabled = false
            bankButton.isEnabled = true
            userButton.isEnabled = true
            setTTPAsChild()
        }

        bankButton.setOnClickListener {
            ttpButton.isEnabled = true
            bankButton.isEnabled = false
            userButton.isEnabled = true
            setBankAsChild()
        }

        userButton.setOnClickListener {
            ttpButton.isEnabled = true
            bankButton.isEnabled = true
            userButton.isEnabled = false
            setUserAsChild()
        }

        eudiButton.setOnClickListener {

            val presentationURL = "https://verifier-backend.eudiw.dev/ui/presentations"
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

            mainScope.launch {
                // Start verifier transaction
                val result = makeAPIRequest(presentationURL, body)

                if (result == "") {
                    return@launch
                }

                // Parse JSON
                val jsonObject = JSONObject(result)
                val transactionId = jsonObject.optString("transaction_id", null)
                val clientId = jsonObject.optString("client_id", null)
                val requestURI = jsonObject.optString("request_uri", null)
                val requestURIMethod = "get"
                Log.d("EUDI", "Transaction ID: $transactionId")
                Log.d("EUDI", "Transaction ID: $requestURI")

                // Send authorization request to the wallet to handle it
                val walletRequestURL = "eudi-openid4vp://?client_id=$clientId&request_uri=$requestURI&request_uri_method=$requestURIMethod"

                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = walletRequestURL.toUri()
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }

                startActivity(intent)
            }
        }
    }


    private suspend fun makeAPIRequest(url: String, jsonBody: String): String {
        Log.d("EUDI", "Calling api endpoint: $url")

        return withContext(Dispatchers.IO) {
            try {
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val requestBody = jsonBody.toRequestBody(mediaType)

                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e("API_CALL", "Unsuccessful response: ${response.code}")
                        return@withContext ""
                    }

                    val responseBodyString = response.body?.string()
                    if (responseBodyString.isNullOrEmpty()) {
                        Log.e("EUDI", "Empty response body")
                        return@withContext ""
                    }

                    Log.d("EUDI", "Response: $responseBodyString")

                    return@withContext responseBodyString
                }
            } catch (e: IOException) {
                Log.e("EUDI", "IOException: ${e.message}", e)
                return@withContext ""
            } catch (e: JSONException) {
                Log.e("EUDI", "JSON parsing error: ${e.message}", e)
                return@withContext ""
            } catch (e: Exception) {
                Log.e("EUDI", "Exception: ${e.message}", e)
                return@withContext ""
            }
        }
    }
    private fun setTTPAsChild() {
        iPV8CommunicationProtocol.participant = ttp
        val ttpFragment = TTPHomeFragment()
        childFragmentManager.beginTransaction()
            .replace(R.id.parent_fragment_container, ttpFragment)
            .commit()
        Toast.makeText(context, "Switched to TTP", Toast.LENGTH_SHORT).show()
    }

    private fun setBankAsChild() {
        iPV8CommunicationProtocol.participant = bank
        val bankFragment = BankHomeFragment()
        childFragmentManager.beginTransaction()
            .replace(R.id.parent_fragment_container, bankFragment)
            .commit()
        Toast.makeText(context, "Switched to Bank", Toast.LENGTH_SHORT).show()
    }

    private fun setUserAsChild() {
        iPV8CommunicationProtocol.participant = user
        val userFragment = UserHomeFragment()
        childFragmentManager.beginTransaction()
            .replace(R.id.parent_fragment_container, userFragment)
            .commit()
        Toast.makeText(context, "Switched to User", Toast.LENGTH_SHORT).show()
    }

    private val onBankDataChangeCallBack: (String?) -> Unit = { message ->
        if (this::bank.isInitialized) {
            requireActivity().runOnUiThread {
                CallbackLibrary.bankCallback(requireContext(), message, requireView(), bank)
            }
        }
    }

    private val onUserDataChangeCallBack: (String?) -> Unit = { message ->
        if (this::user.isInitialized) {
            requireActivity().runOnUiThread {
                val context = requireContext()
                CallbackLibrary.userCallback(
                    context,
                    message,
                    requireView(),
                    iPV8CommunicationProtocol,
                    user
                )
            }
        }
    }

    private val onTTPDataChangeCallback: (String?) -> Unit = { message ->
        if (this::ttp.isInitialized) {
            requireActivity().runOnUiThread {
                val context = requireContext()
                CallbackLibrary.ttpCallback(context, message, requireView(), ttp)
            }
        }
    }

    private fun updateUserList(view: View) {
        val table = view.findViewById<LinearLayout>(R.id.tpp_home_registered_user_list) ?: return
        val users = ttp.getRegisteredUsers()
        TableHelpers.removeAllButFirstRow(table)
        TableHelpers.addRegisteredUsersToTable(table, users)
    }
}
