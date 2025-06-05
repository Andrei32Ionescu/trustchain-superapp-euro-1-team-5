package nl.tudelft.trustchain.offlineeuro.entity

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
import it.unisa.dia.gas.jpbc.Element
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.tudelft.trustchain.offlineeuro.communication.ICommunicationProtocol
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.Schnorr
import nl.tudelft.trustchain.offlineeuro.db.WalletManager
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.UUID

import kotlinx.coroutines.*
import okhttp3.*

class User(
    name: String,
    group: BilinearGroup,
    context: Context?,
    private var walletManager: WalletManager? = null,
    communicationProtocol: ICommunicationProtocol,
    runSetup: Boolean = true,
    onDataChangeCallback: ((String?) -> Unit)? = null,
    private val coroutineScope: CoroutineScope? = null,
) : Participant(communicationProtocol, name, onDataChangeCallback) {
    val wallet: Wallet

    private val client = OkHttpClient()
    private val presentationURL = "https://verifier-backend.eudiw.dev/ui/presentations"
    private val valdiationURL = "https://verifier-backend.eudiw.dev/utilities/validations/msoMdoc/deviceResponse"
    private val context: Context?

    init {
        communicationProtocol.participant = this
        this.group = group
        this.context = context

        if (runSetup) {
            userSetUp() // TODO: Add eudi logic
        } else {
            generateKeyPair()
        }
        if (walletManager == null) {
            walletManager = WalletManager(context, group)
        }

        wallet = Wallet(privateKey, publicKey, walletManager!!)
    }

    fun userSetUp() {
        coroutineScope?.launch {
            try {
                val legalName = getEUDI()
                // Use result here - this runs on main thread
                handleUserSetUp(legalName)
            } catch (e: Exception) {
                // Handle errors
                Log.e("Error", "Suspended function failed", e)
            }
        }

    }

    fun handleUserSetUp(legalName: String?) {
        Log.d("EUDI", "SUCESSS Family name: $legalName")
        setUp(legalName!!) // TODO Maybe deal with !!
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

        context?.startActivity(intent)

        val vpToken = getVPToken(transactionId)  ?: return null

        val attributes = validateVPToken(vpToken) ?: return null

        val familyName = attributes.optString("family_name")

        Log.d("EUDI", "SUCESSS Family name: $familyName")

        return familyName
    }

    private suspend fun getVPToken(transactionId: String): String? {
        var currentAttempt = 0
        val maxRetries = 1000
        val delayBetweenRetries = 200L

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

    fun sendDigitalEuroTo(nameReceiver: String): String {
        val randomizationElements = communicationProtocol.requestTransactionRandomness(nameReceiver, group)
        val transactionDetails =
            wallet.spendEuro(randomizationElements, group, crs)
                ?: throw Exception("No euro to spend")

        val result = communicationProtocol.sendTransactionDetails(nameReceiver, transactionDetails)
        onDataChangeCallback?.invoke(result)
        return result
    }

    fun doubleSpendDigitalEuroTo(nameReceiver: String): String {
        val randomizationElements = communicationProtocol.requestTransactionRandomness(nameReceiver, group)
        val transactionDetails = wallet.doubleSpendEuro(randomizationElements, group, crs)
        val result = communicationProtocol.sendTransactionDetails(nameReceiver, transactionDetails!!)
        onDataChangeCallback?.invoke(result)
        return result
    }

    fun withdrawDigitalEuro(bank: String): DigitalEuro {
        val serialNumber = UUID.randomUUID().toString()
        val firstT = group.getRandomZr()
        val tInv = firstT.mul(-1)
        val initialTheta = group.g.powZn(tInv).immutable

        val bytesToSign = serialNumber.toByteArray() + initialTheta.toBytes()

        val bankRandomness = communicationProtocol.getBlindSignatureRandomness(publicKey, bank, group)
        val bankPublicKey = communicationProtocol.getPublicKeyOf(bank, group)

        val blindedChallenge = Schnorr.createBlindedChallenge(bankRandomness, bytesToSign, bankPublicKey, group)
        val blindSignature = communicationProtocol.requestBlindSignature(publicKey, bank, blindedChallenge.blindedChallenge)
        val signature = Schnorr.unblindSignature(blindedChallenge, blindSignature)
        val digitalEuro = DigitalEuro(serialNumber, initialTheta, signature, arrayListOf())
        wallet.addToWallet(digitalEuro, firstT)
        onDataChangeCallback?.invoke("Withdrawn ${digitalEuro.serialNumber} successfully!")
        return digitalEuro
    }

    fun getBalance(): Int {
        return walletManager!!.getWalletEntriesToSpend().count()
    }

    override fun onReceivedTransaction(
        transactionDetails: TransactionDetails,
        publicKeyBank: Element,
        publicKeySender: Element
    ): String {
        val usedRandomness = lookUpRandomness(publicKeySender) ?: return "Randomness Not found!"
        removeRandomness(publicKeySender)
        val transactionResult = Transaction.validate(transactionDetails, publicKeyBank, group, crs)

        if (transactionResult.valid) {
            wallet.addToWallet(transactionDetails, usedRandomness)
            onDataChangeCallback?.invoke("Received an euro from $publicKeySender")
            return transactionResult.description
        }
        onDataChangeCallback?.invoke(transactionResult.description)
        return transactionResult.description
    }

    override fun reset() {
        randomizationElementMap.clear()
        walletManager!!.clearWalletEntries()
        setUp()   // TODO: Add eudi logic
    }
}
