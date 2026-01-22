package eu.tsp.wallet.oid4vci

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Repository for storing OID4VCI credentials locally
 */
class OID4VCICredentialRepository(context: Context) {

    companion object {
        private const val PREFS_NAME = "oid4vci_credentials"
        private const val KEY_CREDENTIALS = "credentials"

        private val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Save a new credential
     */
    fun saveCredential(credential: OID4VCICredential) {
        val credentials = getAllCredentials().toMutableList()
        credentials.add(credential)
        saveCredentials(credentials)
    }

    /**
     * Get all stored credentials
     */
    fun getAllCredentials(): List<OID4VCICredential> {
        val credentialsJson = prefs.getString(KEY_CREDENTIALS, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<StoredCredential>>(credentialsJson)
                .map { it.toOID4VCICredential() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Delete a credential by ID
     */
    fun deleteCredential(credentialId: String): Boolean {
        val credentials = getAllCredentials().toMutableList()
        val removed = credentials.removeAll { it.id == credentialId }
        if (removed) {
            saveCredentials(credentials)
        }
        return removed
    }

    /**
     * Get a credential by ID
     */
    fun getCredentialById(credentialId: String): OID4VCICredential? {
        return getAllCredentials().find { it.id == credentialId }
    }

    private fun saveCredentials(credentials: List<OID4VCICredential>) {
        val storedCredentials = credentials.map { StoredCredential.fromOID4VCICredential(it) }
        val credentialsJson = json.encodeToString(storedCredentials)
        prefs.edit().putString(KEY_CREDENTIALS, credentialsJson).apply()
    }
}

/**
 * Serializable version of OID4VCICredential for storage
 */
@Serializable
private data class StoredCredential(
    val id: String,
    val format: String,
    val credentialConfigurationId: String,
    val issuer: String,
    val rawCredential: String,
    val issuedAt: Long
) {
    fun toOID4VCICredential(): OID4VCICredential {
        return OID4VCICredential(
            id = id,
            format = format,
            credentialConfigurationId = credentialConfigurationId,
            issuer = issuer,
            rawCredential = rawCredential,
            issuedAt = issuedAt
        )
    }

    companion object {
        fun fromOID4VCICredential(credential: OID4VCICredential): StoredCredential {
            return StoredCredential(
                id = credential.id,
                format = credential.format,
                credentialConfigurationId = credential.credentialConfigurationId,
                issuer = credential.issuer,
                rawCredential = credential.rawCredential,
                issuedAt = credential.issuedAt
            )
        }
    }
}
