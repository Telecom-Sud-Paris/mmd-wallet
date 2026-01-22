package eu.tsp.wallet.aries

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import eu.tsp.wallet.aries.handlers.CustomBasicMessageHandler
import eu.tsp.wallet.aries.model.MessageRecord
import eu.tsp.wallet.aries.model.MessageType
import eu.tsp.wallet.oid4vci.OID4VCICredential
import eu.tsp.wallet.oid4vci.OID4VCICredentialRepository
import eu.tsp.wallet.oid4vci.OpenID4VCIService
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.hyperledger.ariesframework.OutboundMessage
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.AgentConfig
import org.hyperledger.ariesframework.agent.AgentEvents
import org.hyperledger.ariesframework.agent.MediatorPickupStrategy
import org.hyperledger.ariesframework.basicmessage.messages.BasicMessage
import org.hyperledger.ariesframework.connection.models.ConnectionState
import org.hyperledger.ariesframework.connection.repository.ConnectionRecord
import org.hyperledger.ariesframework.credentials.models.AcceptOfferOptions
import org.hyperledger.ariesframework.credentials.models.AutoAcceptCredential
import org.hyperledger.ariesframework.credentials.models.CreateOfferOptions
import org.hyperledger.ariesframework.credentials.models.CredentialPreview
import org.hyperledger.ariesframework.credentials.models.CredentialState
import org.hyperledger.ariesframework.ledger.CredentialDefinitionTemplate
import org.hyperledger.ariesframework.ledger.SchemaTemplate
import org.hyperledger.ariesframework.proofs.models.AutoAcceptProof
import org.hyperledger.ariesframework.proofs.models.ProofState
import org.hyperledger.ariesframework.anoncreds.storage.CredentialRecord
import java.io.File
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

const val PREFERENCE_NAME = "aries-framework-kotlin-sample"
const val genesisPath = "bcovrin-genesis.txn"

class WalletApp(private val context: Context, private val deviceUserId: String) {
    lateinit var agent: Agent
    var walletOpened: Boolean = false
    var initializationError: String? = null
    val walletKey: String
        get() {
            val pref = context.getSharedPreferences(PREFERENCE_NAME, 0)
            return pref.getString("walletKey", null) ?: ""
        }

    // OpenID4VCI support
    private val oid4vciService = OpenID4VCIService()
    private val oid4vciRepository = OID4VCICredentialRepository(context)

    var invitation: Invitation? = null

    private val _receivedMessages = mutableListOf<MessageRecord>()
    val receivedMessages: List<MessageRecord> get() = _receivedMessages.toList()

    private var credDefId: String = ""

    private fun copyResourceFile(resource: String) {
        val inputStream = context.assets.open(resource)
        val file = File(context.filesDir.absolutePath, resource)
        file.outputStream().use { inputStream.copyTo(it) }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun openWallet() {
        try {
            Log.i("WalletApp", "Step 1: Getting shared preferences")
            val pref = context.getSharedPreferences(PREFERENCE_NAME, 0)
            var key = pref.getString("walletKey", null)

            if (key == null) {
                Log.i("WalletApp", "Step 2: Generating wallet key")
                key = Agent.generateWalletKey()
                pref.edit().putString("walletKey", key).apply()
            }

            Log.i("WalletApp", "Step 3: Copying genesis file")
            copyResourceFile(genesisPath)

            val invitationUrl =
                "https://public.mediator.indiciotech.io?c_i=eyJAdHlwZSI6ICJkaWQ6c292OkJ6Q2JzTlloTXJqSGlxWkRUVUFTSGc7c3BlYy9jb25uZWN0aW9ucy8xLjAvaW52aXRhdGlvbiIsICJAaWQiOiAiMDVlYzM5NDItYTEyOS00YWE3LWEzZDQtYTJmNDgwYzNjZThhIiwgInNlcnZpY2VFbmRwb2ludCI6ICJodHRwczovL3B1YmxpYy5tZWRpYXRvci5pbmRpY2lvdGVjaC5pbyIsICJyZWNpcGllbnRLZXlzIjogWyJDc2dIQVpxSktuWlRmc3h0MmRIR3JjN3U2M3ljeFlEZ25RdEZMeFhpeDIzYiJdLCAibGFiZWwiOiAiSW5kaWNpbyBQdWJsaWMgTWVkaWF0b3IifQ==" // ktlint-disable max-line-length

            Log.i("WalletApp", "Step 4: Creating agent config")
            val config = AgentConfig(
                walletKey = key,
                genesisPath = File(context.filesDir.absolutePath, genesisPath).absolutePath,
                mediatorConnectionsInvite = invitationUrl,
                mediatorPickupStrategy = MediatorPickupStrategy.Implicit,
                label = deviceUserId,
                autoAcceptCredential = AutoAcceptCredential.Never,
                autoAcceptProof = AutoAcceptProof.Never,
                publicDidSeed = "00000000000000000000000AFKIssuer" //endorser DID
            )

            Log.i("WalletApp", "Step 5: Creating agent")
            agent = Agent(context, config)

            Log.i("WalletApp", "Step 6: Initializing agent")
            agent.initialize()

            Log.i("WalletApp", "Step 7: Registering message handler")
            agent.dispatcher.registerHandler(
                CustomBasicMessageHandler(
                    agent,
                    ::basicMessageHandler
                )
            )

            Log.i("WalletApp", "Step 8: Subscribing to events")
            subscribeEvents()

            Log.i("WalletApp", "Step 9: Creating invitation")
            this.invitation = createInvitation()

            Log.i("WalletApp", "Step 10: Setting wallet opened")
            walletOpened = true

            Log.i("WalletApp", "Step 11: Preparing for issuance (async - non-blocking)")
            // Run prepareForIssuance asynchronously so it doesn't block or crash the app
            val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
                Log.e("WalletApp", "Coroutine exception in prepareForIssuance: ${throwable.message}", throwable)
            }
            GlobalScope.launch(Dispatchers.IO + exceptionHandler) {
                try {
                    credDefId = prepareForIssuance(listOf("access_requester", "access_granter", "resource_owner"))
                    Log.i("WalletApp", "Credential definition created: $credDefId")
                } catch (e: Exception) {
                    Log.e("WalletApp", "Failed to prepare for issuance (non-fatal): ${e.message}", e)
                    // This is non-fatal - the wallet can still function without credential issuance
                }
            }

            Log.i("WalletApp", "Wallet initialization complete!")
        } catch (e: Exception) {
            Log.e("WalletApp", "Error opening wallet: ${e.message}", e)
            initializationError = e.message ?: "Unknown error during wallet initialization"
        }
    }

    private fun basicMessageHandler(connectionRecord: ConnectionRecord?, message: String) {
        if (connectionRecord != null) {
            val messageRecord = MessageRecord(
                senderConnectionId = connectionRecord.theirLabel ?: "Unknown",
                senderLabel = connectionRecord.theirLabel ?: "Unknown",
                content = message,
                type = MessageType.BasicMessage
            )
            _receivedMessages.add(messageRecord)
            Log.i(
                "WalletApp",
                "Received basic message: $message from ${connectionRecord.theirLabel}"
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @OptIn(DelicateCoroutinesApi::class)
    fun initialise() {
        GlobalScope.launch(Dispatchers.IO) {
            openWallet()
        }
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    @SuppressLint("MissingPermission")
    suspend fun createInvitation(): Invitation? {
        Log.i("WalletApp", "Creating invitation")
        return try {
            // Create a connection invitation
            val routing = agent.mediationRecipient.getRouting()
            val invitation = agent.connectionService.createInvitation(
                routing = routing,
                autoAcceptConnection = true,
                label = deviceUserId,
                multiUseInvitation = true,
            )
            invitation.connection.invitation?.toUrl("public.mediator.indiciotech.io") ?: ""
            Invitation(
                "https://${invitation.connection.invitation?.toUrl("public.mediator.indiciotech.io") ?: ""}",
                invitation.connection.invitation?.imageUrl ?: ""
            )
        } catch (e: Exception) {
            Log.e("WalletApp", "Error creating invitation", e)
            null
        }
    }

    suspend fun connectToUser(invitationUrl: String): Boolean {
        if (!walletOpened) return false

        try {
            val connectionRecord = agent.connections.receiveInvitationFromUrl(invitationUrl)
            Log.i("WalletApp", "Received invitation: $connectionRecord")

            return connectionRecord.state == ConnectionState.Requested
        } catch (e: Exception) {
            Log.e("WalletApp", "Error connecting to user", e)
            return false
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun subscribeEvents() {
        val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
            Log.e("WalletApp", "Coroutine exception in event handler: ${throwable.message}", throwable)
        }

        agent.eventBus.subscribe<AgentEvents.CredentialEvent> {
            GlobalScope.launch(Dispatchers.IO + exceptionHandler) {
                try {
                    val theirLabel =
                        agent.connectionRepository.getById(it.record.connectionId).theirLabel
                            ?: "Unknown"
                    if (it.record.state == CredentialState.OfferReceived) {
                        _receivedMessages.add(
                            MessageRecord(
                                senderConnectionId = theirLabel,
                                senderLabel = theirLabel,
                                type = MessageType.CredentialOffer,
                                content = "Credential offer received. Attributes: ${it.record.getCredentialInfo()}"
                            )
                        )
                        getCredential(it.record.id)
                    } else if (it.record.state == CredentialState.Done) {
                        _receivedMessages.add(
                            MessageRecord(
                                senderConnectionId = theirLabel,
                                senderLabel = theirLabel,
                                type = MessageType.CredentialApproved,
                                content = "Credentials: ${it.record.credentialAttributes}"
                            )
                        )
                        Log.i("WalletApp", "Credential offer completed: ${it.record.id}")
                    }
                } catch (e: Exception) {
                    Log.e("WalletApp", "Error handling credential event: ${e.message}", e)
                }
            }
        }
        agent.eventBus.subscribe<AgentEvents.ProofEvent> {
            GlobalScope.launch(Dispatchers.IO + exceptionHandler) {
                try {
                    if (it.record.state == ProofState.RequestReceived) {
                        sendProof(it.record.id)
                    } else if (it.record.state == ProofState.Done) {
                        Log.i("WalletApp", "Proof request completed: ${it.record.id}")
                    }
                } catch (e: Exception) {
                    Log.e("WalletApp", "Error handling proof event: ${e.message}", e)
                }
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun getCredential(id: String) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                agent.credentials.acceptOffer(
                    AcceptOfferOptions(
                        credentialRecordId = id,
                        autoAcceptCredential = AutoAcceptCredential.Always
                    ),
                )
            } catch (e: Exception) {
                Log.e("WalletApp", "Error accepting credential offer", e)
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private suspend fun sendProof(id: String) {
        try {
            val retrievedCredentials = agent.proofs.getRequestedCredentialsForProofRequest(id)
            val requestedCredentials =
                agent.proofService.autoSelectCredentialsForProofRequest(retrievedCredentials)
            agent.proofs.acceptRequest(id, requestedCredentials)
        } catch (e: Exception) {
            Log.e("WalletApp", "Error sending proof request", e)
        }
    }

    suspend fun sendMessageToConnection(
        connectionId: String,
        messageType: MessageType,
        message: String?
    ): Boolean {
        if (!walletOpened) return false

        return try {
            val connection = agent.connectionRepository.getById(connectionId)

            // Ensure connection is ready for messaging
            if (connection.state != ConnectionState.Complete) {
                Log.e("WalletApp", "Connection not in Complete state: ${connection.state}")
                return false
            }

            when (messageType) {
                MessageType.CredentialOffer -> {
                    val theirLabel = connection.theirLabel ?: "Unknown"
                    val credentialPreview = CredentialPreview.fromDictionary(
                        mapOf(
                            "access_requester" to deviceUserId,
                            "access_granter" to theirLabel,
                            "resource_owner" to theirLabel
                        )
                    )
                    val offerOptions = CreateOfferOptions(
                        connection = connection,
                        comment = message ?: "Credential Request",
                        autoAcceptCredential = AutoAcceptCredential.Always,
                        credentialDefinitionId = credDefId,
                        attributes = credentialPreview.attributes
                    )
                    agent.credentials.offerCredential(offerOptions)
                }

                MessageType.BasicMessage -> {
                    val outboundMessage =
                        OutboundMessage(BasicMessage(message ?: "N/A"), connection)
                    agent.messageSender.send(outboundMessage)
                }

                else -> throw IllegalArgumentException("Unsupported message type: $messageType")
            }
            Log.i("WalletApp", "Message sent successfully: $message")
            true
        } catch (e: Exception) {
            Log.e("WalletApp", "Error sending message", e)
            false
        }
    }

    private suspend fun prepareForIssuance(attributes: List<String>): String {
        Log.i("WalletApp", "Preparing for credential issuance with attributes: $attributes")
        try {
            val didInfo = agent.wallet.publicDid
            if (didInfo == null) {
                Log.w("WalletApp", "Agent has no public DID, skipping credential issuance setup")
                return ""
            }
            val schemaId = agent.ledgerService.registerSchema(
                didInfo,
                SchemaTemplate("schema-${UUID.randomUUID()}", "1.0", attributes),
            )
            Log.i("WalletApp", "Schema registered with ID: $schemaId")
            delay(0.1.seconds)
            val (schema, seqNo) = agent.ledgerService.getSchema(schemaId)
            Log.i("WalletApp", "Schema retrieved: $schema with seqNo: $seqNo")
            return agent.ledgerService.registerCredentialDefinition(
                didInfo,
                CredentialDefinitionTemplate(schema, "default", false, seqNo),
            )
        } catch (e: Exception) {
            Log.e("WalletApp", "Error preparing for issuance: ${e.message}", e)
            return ""
        }
    }

    // In WalletApp.kt
    suspend fun getConnections(): List<ConnectionRecord> {
        if (!walletOpened || !::agent.isInitialized) return emptyList()

        return try {
            agent.connectionRepository.getAll().filter { it.theirDid != null }
                .sortedByDescending { it.createdAt }
        } catch (e: Exception) {
            Log.e("WalletApp", "Error getting connections", e)
            emptyList()
        }
    }

    /**
     * Get all credentials stored in the wallet
     */
    suspend fun getCredentials(): List<CredentialRecord> {
        if (!walletOpened || !::agent.isInitialized) return emptyList()

        return try {
            agent.credentialRepository.getAll().sortedByDescending { it.createdAt }
        } catch (e: Exception) {
            Log.e("WalletApp", "Error getting credentials", e)
            emptyList()
        }
    }

    /**
     * Delete a credential from the wallet
     */
    suspend fun deleteCredential(credentialId: String): Boolean {
        if (!walletOpened || !::agent.isInitialized) return false

        return try {
            val credential = agent.credentialRepository.getByCredentialId(credentialId)
            agent.credentialRepository.delete(credential)
            Log.i("WalletApp", "Credential deleted: $credentialId")
            true
        } catch (e: Exception) {
            Log.e("WalletApp", "Error deleting credential", e)
            false
        }
    }

    /**
     * Receive a credential offer from an invitation URL.
     * Supports both OpenID4VCI (openid-credential-offer://) and Aries (DIDComm) URLs.
     */
    suspend fun receiveCredentialFromUrl(invitationUrl: String): CredentialReceiveResult {
        val trimmedUrl = invitationUrl.trim()

        // Check if it's an OpenID4VCI URL
        if (oid4vciService.isOpenID4VCIUrl(trimmedUrl)) {
            return try {
                Log.i("WalletApp", "Processing as OID4VCI credential offer")
                val credential = oid4vciService.receiveCredentialFromUrl(trimmedUrl)
                oid4vciRepository.saveCredential(credential)
                Log.i("WalletApp", "OID4VCI credential saved: ${credential.id}")
                CredentialReceiveResult.Success("OID4VCI credential received successfully", CredentialType.OID4VCI)
            } catch (e: Exception) {
                Log.e("WalletApp", "Error receiving OID4VCI credential", e)
                CredentialReceiveResult.Error("Failed to receive OID4VCI credential: ${e.message}")
            }
        }

        // Otherwise, process as Aries invitation
        if (!walletOpened) {
            return CredentialReceiveResult.Error("Wallet not opened")
        }

        try {
            // Try to receive the invitation via OOB
            val (oobRecord, connectionRecord) = agent.oob.receiveInvitationFromUrl(trimmedUrl)
            Log.i("WalletApp", "Received OOB invitation: ${oobRecord?.id}, connection: ${connectionRecord?.id}")
            return if (oobRecord != null || connectionRecord != null) {
                CredentialReceiveResult.Success("Aries invitation received. Waiting for credential offer...", CredentialType.ARIES)
            } else {
                CredentialReceiveResult.Error("No OOB or connection record created")
            }
        } catch (e: Exception) {
            Log.e("WalletApp", "Error receiving credential from URL via OOB", e)
            // Fall back to connection invitation if OOB fails
            try {
                val connectionRecord = agent.connections.receiveInvitationFromUrl(trimmedUrl)
                Log.i("WalletApp", "Received connection invitation: ${connectionRecord.id}")
                return if (connectionRecord.state == ConnectionState.Requested) {
                    CredentialReceiveResult.Success("Connection established. Waiting for credential offer...", CredentialType.ARIES)
                } else {
                    CredentialReceiveResult.Error("Connection not in expected state")
                }
            } catch (e2: Exception) {
                Log.e("WalletApp", "Error receiving connection invitation", e2)
                return CredentialReceiveResult.Error("Failed to process invitation: ${e2.message}")
            }
        }
    }

    /**
     * Get all OID4VCI credentials
     */
    fun getOID4VCICredentials(): List<OID4VCICredential> {
        return oid4vciRepository.getAllCredentials()
    }

    /**
     * Delete an OID4VCI credential
     */
    fun deleteOID4VCICredential(credentialId: String): Boolean {
        return oid4vciRepository.deleteCredential(credentialId)
    }
}

data class Invitation(val url: String, val imageUrl: String)

/**
 * Result of receiving a credential from a URL
 */
sealed class CredentialReceiveResult {
    data class Success(val message: String, val type: CredentialType) : CredentialReceiveResult()
    data class Error(val message: String) : CredentialReceiveResult()
}

enum class CredentialType {
    ARIES,
    OID4VCI
}
