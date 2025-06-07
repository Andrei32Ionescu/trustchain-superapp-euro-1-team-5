package nl.tudelft.trustchain.offlineeuro.entity

import android.content.Context
import android.util.Log
import it.unisa.dia.gas.jpbc.Element
import kotlinx.coroutines.delay
import nl.tudelft.trustchain.offlineeuro.communication.ICommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.CRSGenerator
import nl.tudelft.trustchain.offlineeuro.cryptography.GrothSahaiProof
import nl.tudelft.trustchain.offlineeuro.db.RegisteredUserManager
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class TTP(
    name: String = "TTP",
    group: BilinearGroup,
    communicationProtocol: ICommunicationProtocol,
    context: Context?,
    private val registeredUserManager: RegisteredUserManager = RegisteredUserManager(context, group),
    onDataChangeCallback: ((String?) -> Unit)? = null
) : Participant(communicationProtocol, name, onDataChangeCallback) {
    val crsMap: Map<Element, Element>
    private val presentationURL = "https://verifier-backend.eudiw.dev/ui/presentations"

    init {
        communicationProtocol.participant = this
        this.group = group
        val generatedCRS = CRSGenerator.generateCRSMap(group)
        this.crs = generatedCRS.first
        this.crsMap = generatedCRS.second
        generateKeyPair()
    }

    fun getEUDI(): Map<String, String> {
        val client = OkHttpClient()
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
//        val result = makeAPIRequest(presentationURL, body, "application/json; charset=utf-8".toMediaType()) ?: return null
//        val jsonBody = JSONObject(body).
        val request = Request.Builder().url(presentationURL).post(body.toRequestBody("application/json".toMediaType())).build()

        // Parse JSON
        val jsonObject = postRequest(request, client)

        // val walletRequestURL = "eudi-openid4vp://?client_id=$clientId&request_uri=$requestURI&request_uri_method=$requestURIMethod"
        val map = HashMap<String, String>()
        map["transaction_id"] = jsonObject.optString("transaction_id")
        map["client_id"] = jsonObject.optString("client_id")
        map["request_uri"] = jsonObject.optString("request_uri")
        map["request_uri_method"] = "get"
        return map
    }

    fun postRequest(request: Request, client: OkHttpClient): JSONObject {
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful)
                throw IllegalStateException("HTTP ${resp.code} – ${resp.message}")
            val obj = JSONObject(resp.body!!.string())
            return obj
        }
    }

    fun getVPToken(transactionId: String): String? {
        var currentAttempt = 0
        val maxRetries = 1000
        val delayBetweenRetries = 200L
        val client = OkHttpClient()

        while (currentAttempt < maxRetries) {
            try {
                Log.d("EUDI", "Attempting to get VP token, attempt ${currentAttempt + 1}/$maxRetries")
                val request = Request.Builder().url("$presentationURL/$transactionId").get().build()
                val vpToken = requestVPToken(request, client)
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
//                delay(delayBetweenRetries)
            }
        }

        Log.e("EUDI", "Failed to get VP token after $maxRetries attempts")
        return null
    }

    private fun requestVPToken(request: Request, client: OkHttpClient): String? {
        Log.d("EUDI", "Requesting vp token...")
        val stringResp = postVerification(request, client)
        // Parse JSON
        if (stringResp == null)
            return null

        val jsonObject = JSONObject(stringResp)
        val vpToken = jsonObject.optJSONArray("vp_token")?.optString(0, null)
        Log.d("EUDI", "VP token: $vpToken")

        return vpToken
    }

    fun postVerification(request: Request, client: OkHttpClient): String? {
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful)
                return null
            val obj = resp.body
            if (obj == null)
                return null
            return resp.body!!.string()
        }
    }

    fun registerUser(
        name: String,
        publicKey: Element,
//        legalName: String
    ): Boolean {
        val legalName = name // currently just the username
        val result = registeredUserManager.addRegisteredUser(name, publicKey, legalName)
        onDataChangeCallback?.invoke("Registered $name")
        return result
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
}
