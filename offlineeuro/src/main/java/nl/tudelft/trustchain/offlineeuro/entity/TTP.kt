package nl.tudelft.trustchain.offlineeuro.entity

import android.content.Context
import android.util.Log
import it.unisa.dia.gas.jpbc.Element
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.tudelft.trustchain.offlineeuro.communication.ICommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.CRSGenerator
import nl.tudelft.trustchain.offlineeuro.cryptography.GrothSahaiProof
import nl.tudelft.trustchain.offlineeuro.cryptography.Schnorr
import nl.tudelft.trustchain.offlineeuro.cryptography.SchnorrSignature
import nl.tudelft.trustchain.offlineeuro.db.NonRegisteredUserManager
import nl.tudelft.trustchain.offlineeuro.db.RegisteredUserManager
import nl.tudelft.trustchain.offlineeuro.enums.Role
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

class TTP(
    name: String = "TTP",
    group: BilinearGroup,
    communicationProtocol: ICommunicationProtocol,
    context: Context?,
    internal val registeredUserManager: RegisteredUserManager = RegisteredUserManager(context, group),
    private val nonRegisteredUserManager: NonRegisteredUserManager = NonRegisteredUserManager(context, group),
    onDataChangeCallback: ((String?) -> Unit)? = null,
    private val signedBankKeys: HashMap<Element, SchnorrSignature> = HashMap<Element, SchnorrSignature>()
) : Participant(communicationProtocol, name, onDataChangeCallback, Role.TTP) {
    val crsMap: Map<Element, Element>

    private val client = OkHttpClient()
    private val presentationURL = "https://verifier-backend.eudiw.dev/ui/presentations"
    private val valdiationURL = "https://verifier-backend.eudiw.dev/utilities/validations/msoMdoc/deviceResponse"

    init {
        communicationProtocol.participant = this
        this.group = group
        generateKeyPair()
        val generatedCRS = CRSGenerator.generateCRSMap(group, publicKey)  // Pass TTP's public key
        this.crs = generatedCRS.first
        this.crsMap = generatedCRS.second
    }

    private val localScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun registerUser(
//        name: String,
        transactionId: String,
//        role: Role
    ): Boolean {
        // TODO HANDLE USER REGISTRATION
        Log.d("EUDI", "register user")
        localScope.launch {
            try {
                val vpToken = getVPToken(transactionId)  ?: return@launch

                val attributes = validateVPToken(vpToken) ?: return@launch

                val legalName = attributes.optString("family_name")
                Log.d("EUDI", "got name")
                val user = nonRegisteredUserManager.getNonRegisteredUserByTransactionId(transactionId)
                // delete user from non-registered database
                nonRegisteredUserManager.deleteNonRegisteredUserByTransactionId(transactionId)
                val userName = user?.name
                val publicKey = user?.publicKey
                val overlayPK = user?.overlayPK // The peer's address over the ipv8 network
                val result = registeredUserManager.addRegisteredUser(userName!!, publicKey!!, legalName)
                onDataChangeCallback?.invoke("Registered $name")
                communicationProtocol.sendRegisterAtTTPReplyMessage(result.toString(), overlayPK!!)

                Log.d("EUDI", "SUCESSS Family name: $legalName")

            } catch (e: Exception) {
                Log.e("Error", "EUDI authentication failed", e)
                val user = nonRegisteredUserManager.getNonRegisteredUserByTransactionId(transactionId)
                val peerPublicKeyBytes = user?.publicKey!!.toBytes()
                communicationProtocol.sendRegisterAtTTPReplyMessage("false", peerPublicKeyBytes)
            }
        }

//        val result = registeredUserManager.addRegisteredUser(name, publicKey)
//        onDataChangeCallback?.invoke("Registered $name")
//
//        var bankKeySignature: SchnorrSignature? = null
//        if (role == Role.Bank) {
//            // Sign the bank's public key
//            bankKeySignature = Schnorr.schnorrSignature(
//                privateKey,
//                publicKey.toBytes(),
//                group
//            )
//            signedBankKeys[publicKey] = bankKeySignature
//        }
//        return Pair(result, bankKeySignature)

        return true;
    }

    fun registerBank(
        publicKey: Element,
        name: String,
        role: Role
    ): Pair<Boolean, SchnorrSignature> {
        val result = registeredUserManager.addRegisteredUser(name, publicKey, "")
        onDataChangeCallback?.invoke("Registered bank")

        // Sign the bank's public key
        val bankKeySignature = Schnorr.schnorrSignature(
            privateKey,
            publicKey.toBytes(),
            group
        )
        signedBankKeys[publicKey] = bankKeySignature
        return Pair(result, bankKeySignature)
    }

//    fun registerUser(
//        name: String,
//        transactionId: String,
//        role: Role
//    ): Deferred<Pair<Boolean, SchnorrSignature?>> =
//        // TODO HANDLE USER REGISTRATION
//        CoroutineScope(Dispatchers.Default).async {
//            Log.d("EUDI", "register user")
//            var bankKeySignature: SchnorrSignature? = null
//            val ok: Boolean = try {
//                val vpToken = getVPToken(transactionId)  ?: return@async Pair(false, null)
//
//                val attributes = validateVPToken(vpToken) ?: return@async Pair(false, null)
//
//                val legalName = attributes.optString("family_name")
//                Log.d("EUDI", "got name")
//                val user = nonRegisteredUserManager.getNonRegisteredUserByTransactionId(transactionId)
//                // delete user from non-registered database
//                nonRegisteredUserManager.deleteNonRegisteredUserByTransactionId(transactionId)
//                val userName = user?.name
//                val publicKey = user?.publicKey
//                val overlayPK = user?.overlayPK // The peer's address over the ipv8 network
//                val result = registeredUserManager.addRegisteredUser(userName!!, publicKey!!, legalName)
//                onDataChangeCallback?.invoke("Registered $name")
//                communicationProtocol.sendRegisterAtTTPReplyMessage(result.toString(), overlayPK!!)
////                onDataChangeCallback?.invoke("Registered $name")
//
////                var bankKeySignature: SchnorrSignature? = null
//                if (role == Role.Bank) {
//                    // Sign the bank's public key
//                    bankKeySignature = Schnorr.schnorrSignature(
//                        privateKey,
//                        publicKey.toBytes(),
//                        group
//                    )
//                    signedBankKeys[publicKey] = bankKeySignature
//                }
//                Log.d("EUDI", "SUCESSS Family name: $legalName")
//                result
//            } catch (e: Exception) {
//                Log.e("Error", "EUDI authentication failed", e)
//                val user = nonRegisteredUserManager.getNonRegisteredUserByTransactionId(transactionId)
//                val peerPublicKeyBytes = user?.publicKey!!.toBytes()
//                communicationProtocol.sendRegisterAtTTPReplyMessage("false", peerPublicKeyBytes)
//                false
//            }
//            Pair(ok, bankKeySignature)
//        }

    fun sendUserRequestData(
//        data: Pair<String, String>,
        name: String,
        publicKey: Element,
        overlayPK: ByteArray
    ) {
        Log.d("EUDI", "send user request data")
        localScope.launch {
            try {
                val (deeplink, transactionId) = getEUDI()!!
                val result = nonRegisteredUserManager.addNonRegisteredUser(name, publicKey, transactionId, overlayPK)
                communicationProtocol.sendRequestUserVerificationMessage(transactionId, deeplink, overlayPK)
            } catch (e: Exception) {
                Log.e("Error", "Requesting EUDI data failed", e)
                throw e
            }
        }
    }

    suspend fun getEUDI(): Pair<String, String>? {
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

//        val intent = Intent(Intent.ACTION_VIEW).apply {
//            data = walletRequestURL.toUri()
//            flags = Intent.FLAG_ACTIVITY_NEW_TASK
//        }

//        activityResultLauncher.launch(intent)

        return Pair(walletRequestURL, transactionId)
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

    private suspend fun getVPToken(transactionId: String): String? {
        var currentAttempt = 0
        val maxRetries = 150
        val delayBetweenRetries = 200L

        Log.d("EUDI", "Get VP TOken")

        while (currentAttempt < maxRetries) {
            try {
                Log.d("EUDI", "Attempting to get VP token, attempt ${currentAttempt + 1}/$maxRetries")

                val vpToken = requestVPToken(transactionId)
                if (!vpToken.isNullOrEmpty()) {
                    // Success! Process the token
                    Log.d("EUDI", "VP token received successfully")
                    return vpToken
                }
            } catch (e: Exception) {
                Log.w("EUDI", "Attempt ${currentAttempt + 1} failed: ${e.message}")
            }

            currentAttempt++

            if (currentAttempt < maxRetries) {
                Log.d("EUDI", "Waiting ${delayBetweenRetries}ms before next attempt...")
                delay(delayBetweenRetries)
            }
        }

        Log.e("EUDI", "Failed to get VP token after $maxRetries attempts")
        return null
    }

    private suspend fun requestVPToken(transactionId: String): String? {
        Log.d("EUDI", "Requesting vp token...")
        val result = makeAPIRequest("$presentationURL/$transactionId", "", "application/json; charset=utf-8".toMediaType()) ?: return null

        // Parse JSON
        val jsonObject = JSONObject(result)
        val vpToken = jsonObject.optJSONArray("vp_token")?.optString(0, null)

        Log.d("EUDI", "VP token: $vpToken")

        return vpToken
    }

    private suspend fun validateVPToken(vpToken: String): JSONObject? {
        Log.d("EUDI", "Validation vp token...")
        val body = "device_response=$vpToken"
        val result = makeAPIRequest(valdiationURL, body, "application/x-www-form-urlencoded".toMediaType()) ?: return null

        // Parse JSON
        val jsonObject = JSONArray(result).optJSONObject(0)
        val docType = jsonObject?.optString("docType")
        val attributes = jsonObject?.optJSONObject("attributes")?.optJSONObject(docType)

        Log.d("EUDI", "Data: $attributes")

        return attributes
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
}
