package eu.tsp.wallet.oid4vci

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

/**
 * OpenID4VCI (OpenID for Verifiable Credential Issuance) Service
 * Implements the credential offer flow for receiving credentials via OID4VCI protocol.
 */
class OpenID4VCIService {

    companion object {
        private const val TAG = "OpenID4VCIService"
        private const val SCHEME_CREDENTIAL_OFFER = "openid-credential-offer://"

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Check if the URL is an OpenID4VCI credential offer URL
     */
    fun isOpenID4VCIUrl(url: String): Boolean {
        return url.trim().startsWith(SCHEME_CREDENTIAL_OFFER) ||
               url.contains("credential_offer=") ||
               url.contains("credential_offer_uri=")
    }

    /**
     * Parse and process an OpenID4VCI credential offer URL
     * @param url The credential offer URL (openid-credential-offer://...)
     * @return The received credential or throws an exception
     */
    suspend fun receiveCredentialFromUrl(url: String): OID4VCICredential {
        return withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "Processing OID4VCI URL: $url")

                // Parse the credential offer
                val credentialOffer = parseCredentialOffer(url)
                Log.i(TAG, "Parsed credential offer: issuer=${credentialOffer.credentialIssuer}")

                // Fetch issuer metadata
                val issuerMetadata = fetchIssuerMetadata(credentialOffer.credentialIssuer)
                Log.i(TAG, "Fetched issuer metadata: ${issuerMetadata.credentialIssuer}")

                // Get access token using pre-authorized code if available
                val tokenResponse = if (credentialOffer.grants?.preAuthorizedCode != null) {
                    getAccessTokenWithPreAuth(
                        issuerMetadata.tokenEndpoint ?: "${credentialOffer.credentialIssuer}/token",
                        credentialOffer.grants.preAuthorizedCode
                    )
                } else {
                    throw OID4VCIException("Only pre-authorized code flow is currently supported")
                }
                Log.i(TAG, "Obtained access token")

                // Request the credential
                val credentialConfigId = credentialOffer.credentialConfigurationIds.firstOrNull()
                    ?: throw OID4VCIException("No credential configuration ID in offer")

                val credentialEndpoint = issuerMetadata.credentialEndpoint
                    ?: "${credentialOffer.credentialIssuer}/credential"

                val credential = requestCredential(
                    credentialEndpoint,
                    tokenResponse.accessToken,
                    credentialConfigId,
                    issuerMetadata
                )
                Log.i(TAG, "Successfully received credential")

                credential
            } catch (e: OID4VCIException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error processing OID4VCI URL", e)
                throw OID4VCIException("Failed to process credential offer: ${e.message}", e)
            }
        }
    }

    /**
     * Parse the credential offer from the URL
     */
    private fun parseCredentialOffer(url: String): CredentialOffer {
        val normalizedUrl = if (url.startsWith(SCHEME_CREDENTIAL_OFFER)) {
            url.removePrefix(SCHEME_CREDENTIAL_OFFER)
        } else {
            url
        }

        // Parse query parameters
        val queryStart = normalizedUrl.indexOf('?')
        val queryString = if (queryStart >= 0) {
            normalizedUrl.substring(queryStart + 1)
        } else {
            normalizedUrl
        }

        val params = queryString.split("&").associate { param ->
            val parts = param.split("=", limit = 2)
            if (parts.size == 2) {
                parts[0] to URLDecoder.decode(parts[1], "UTF-8")
            } else {
                parts[0] to ""
            }
        }

        // Check for credential_offer (inline JSON) or credential_offer_uri (reference)
        val credentialOfferJson = params["credential_offer"]
            ?: throw OID4VCIException("credential_offer parameter not found. credential_offer_uri not yet supported")

        return json.decodeFromString<CredentialOffer>(credentialOfferJson)
    }

    /**
     * Fetch the issuer's OpenID4VCI metadata
     */
    private suspend fun fetchIssuerMetadata(issuerUrl: String): IssuerMetadata {
        val metadataUrl = "${issuerUrl.trimEnd('/')}/.well-known/openid-credential-issuer"

        val request = Request.Builder()
            .url(metadataUrl)
            .get()
            .build()

        val response = httpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            throw OID4VCIException("Failed to fetch issuer metadata: ${response.code}")
        }

        val body = response.body?.string()
            ?: throw OID4VCIException("Empty response from issuer metadata endpoint")

        return json.decodeFromString<IssuerMetadata>(body)
    }

    /**
     * Get access token using pre-authorized code
     */
    private suspend fun getAccessTokenWithPreAuth(
        tokenEndpoint: String,
        preAuthCode: PreAuthorizedCodeGrant
    ): TokenResponse {
        val formBody = FormBody.Builder()
            .add("grant_type", "urn:ietf:params:oauth:grant-type:pre-authorized_code")
            .add("pre-authorized_code", preAuthCode.preAuthorizedCode)

        preAuthCode.txCode?.let { txCode ->
            // If tx_code is required, we need to prompt user - for now just note it
            Log.w(TAG, "Transaction code may be required: input_mode=${txCode.inputMode}")
        }

        val request = Request.Builder()
            .url(tokenEndpoint)
            .post(formBody.build())
            .build()

        val response = httpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            throw OID4VCIException("Failed to get access token: ${response.code} - $errorBody")
        }

        val body = response.body?.string()
            ?: throw OID4VCIException("Empty response from token endpoint")

        return json.decodeFromString<TokenResponse>(body)
    }

    /**
     * Request the credential from the issuer
     */
    private suspend fun requestCredential(
        credentialEndpoint: String,
        accessToken: String,
        credentialConfigId: String,
        issuerMetadata: IssuerMetadata
    ): OID4VCICredential {
        val credentialConfig = issuerMetadata.credentialConfigurationsSupported?.get(credentialConfigId)

        val requestBody = buildString {
            append("{")
            append("\"credential_identifier\":\"$credentialConfigId\"")
            if (credentialConfig?.format != null) {
                append(",\"format\":\"${credentialConfig.format}\"")
            }
            append("}")
        }

        val request = Request.Builder()
            .url(credentialEndpoint)
            .header("Authorization", "Bearer $accessToken")
            .header("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        val response = httpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            throw OID4VCIException("Failed to get credential: ${response.code} - $errorBody")
        }

        val body = response.body?.string()
            ?: throw OID4VCIException("Empty response from credential endpoint")

        val credentialResponse = json.decodeFromString<CredentialResponse>(body)

        // Parse the credential
        val credentialData = credentialResponse.credential
            ?: throw OID4VCIException("No credential in response")

        return OID4VCICredential(
            id = java.util.UUID.randomUUID().toString(),
            format = credentialConfig?.format ?: "unknown",
            credentialConfigurationId = credentialConfigId,
            issuer = issuerMetadata.credentialIssuer,
            rawCredential = credentialData.toString(),
            issuedAt = System.currentTimeMillis()
        )
    }
}

// Data classes for OID4VCI protocol

@Serializable
data class CredentialOffer(
    @SerialName("credential_issuer")
    val credentialIssuer: String,
    @SerialName("credential_configuration_ids")
    val credentialConfigurationIds: List<String>,
    val grants: Grants? = null
)

@Serializable
data class Grants(
    @SerialName("urn:ietf:params:oauth:grant-type:pre-authorized_code")
    val preAuthorizedCode: PreAuthorizedCodeGrant? = null,
    @SerialName("authorization_code")
    val authorizationCode: AuthorizationCodeGrant? = null
)

@Serializable
data class PreAuthorizedCodeGrant(
    @SerialName("pre-authorized_code")
    val preAuthorizedCode: String,
    @SerialName("tx_code")
    val txCode: TxCode? = null
)

@Serializable
data class TxCode(
    @SerialName("input_mode")
    val inputMode: String? = null,
    val length: Int? = null,
    val description: String? = null
)

@Serializable
data class AuthorizationCodeGrant(
    @SerialName("issuer_state")
    val issuerState: String? = null
)

@Serializable
data class IssuerMetadata(
    @SerialName("credential_issuer")
    val credentialIssuer: String,
    @SerialName("credential_endpoint")
    val credentialEndpoint: String? = null,
    @SerialName("token_endpoint")
    val tokenEndpoint: String? = null,
    @SerialName("authorization_server")
    val authorizationServer: String? = null,
    @SerialName("credential_configurations_supported")
    val credentialConfigurationsSupported: Map<String, CredentialConfiguration>? = null
)

@Serializable
data class CredentialConfiguration(
    val format: String? = null,
    val scope: String? = null,
    @SerialName("credential_definition")
    val credentialDefinition: JsonElement? = null,
    val display: List<CredentialDisplay>? = null
)

@Serializable
data class CredentialDisplay(
    val name: String? = null,
    val locale: String? = null,
    val logo: LogoInfo? = null,
    @SerialName("background_color")
    val backgroundColor: String? = null,
    @SerialName("text_color")
    val textColor: String? = null
)

@Serializable
data class LogoInfo(
    val uri: String? = null,
    @SerialName("alt_text")
    val altText: String? = null
)

@Serializable
data class TokenResponse(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("token_type")
    val tokenType: String? = null,
    @SerialName("expires_in")
    val expiresIn: Int? = null,
    @SerialName("c_nonce")
    val cNonce: String? = null,
    @SerialName("c_nonce_expires_in")
    val cNonceExpiresIn: Int? = null
)

@Serializable
data class CredentialResponse(
    val credential: JsonElement? = null,
    val format: String? = null,
    @SerialName("c_nonce")
    val cNonce: String? = null,
    @SerialName("c_nonce_expires_in")
    val cNonceExpiresIn: Int? = null
)

/**
 * Represents a credential received via OID4VCI
 */
data class OID4VCICredential(
    val id: String,
    val format: String,
    val credentialConfigurationId: String,
    val issuer: String,
    val rawCredential: String,
    val issuedAt: Long
)

/**
 * Exception for OID4VCI errors
 */
class OID4VCIException(message: String, cause: Throwable? = null) : Exception(message, cause)
