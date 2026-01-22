package eu.tsp.wallet.data.repository

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import eu.tsp.wallet.core.Constants
import eu.tsp.wallet.domain.model.Invitation
import eu.tsp.wallet.domain.model.MessageRecord
import eu.tsp.wallet.domain.model.MessageType
import eu.tsp.wallet.domain.repository.WalletRepository
import eu.tsp.wallet.data.handler.CustomBasicMessageHandler
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
import java.io.File
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

/**
 * Implementation of WalletRepository using Aries Framework
 */
class AriesWalletRepository(
    private val context: Context,
    private val deviceUserId: String
) : WalletRepository {

    private lateinit var agent: Agent
    private var walletOpened: Boolean = false
    private lateinit var credDefId: String
    private val _receivedMessages = mutableListOf<MessageRecord>()

    private val sharedPreferences by lazy {
        context.getSharedPreferences(Constants.PREFERENCE_NAME, Context.MODE_PRIVATE)
    }

    override fun isWalletOpened(): Boolean = walletOpened

    override fun getWalletKey(): String {
        return sharedPreferences.getString(Constants.PREF_KEY_WALLET_KEY, null) ?: ""
    }

    override suspend fun getPublicDid(): String? {
        return if (walletOpened) {
            agent.wallet.publicDid?.did
        } else {
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun initialize() {
        try {
            setupWalletKey()
            copyGenesisFile()
            initializeAgent()
            setupMessageHandlers()
            subscribeToEvents()
            credDefId = prepareForIssuance(Constants.DEFAULT_CREDENTIAL_ATTRIBUTES)
            walletOpened = true
            Log.i(TAG, "Wallet initialized successfully. CredDefId: $credDefId")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing wallet", e)
            throw e
        }
    }

    private fun setupWalletKey() {
        var key = sharedPreferences.getString(Constants.PREF_KEY_WALLET_KEY, null)
        if (key == null) {
            key = Agent.generateWalletKey()
            sharedPreferences.edit()
                .putString(Constants.PREF_KEY_WALLET_KEY, key)
                .apply()
        }
    }

    private fun copyGenesisFile() {
        val inputStream = context.assets.open(Constants.GENESIS_PATH)
        val file = File(context.filesDir.absolutePath, Constants.GENESIS_PATH)
        file.outputStream().use { inputStream.copyTo(it) }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun initializeAgent() {
        val config = AgentConfig(
            walletKey = getWalletKey(),
            genesisPath = File(context.filesDir.absolutePath, Constants.GENESIS_PATH).absolutePath,
            mediatorConnectionsInvite = Constants.MEDIATOR_INVITATION_URL,
            mediatorPickupStrategy = MediatorPickupStrategy.Implicit,
            label = deviceUserId,
            autoAcceptCredential = AutoAcceptCredential.Never,
            autoAcceptProof = AutoAcceptProof.Never,
            publicDidSeed = Constants.PUBLIC_DID_SEED
        )

        agent = Agent(context, config)
        agent.initialize()
    }

    private fun setupMessageHandlers() {
        agent.dispatcher.registerHandler(
            CustomBasicMessageHandler(agent, ::handleBasicMessage)
        )
    }

    private fun handleBasicMessage(connectionRecord: ConnectionRecord?, message: String) {
        if (connectionRecord != null) {
            val messageRecord = MessageRecord(
                senderConnectionId = connectionRecord.theirLabel ?: "Unknown",
                senderLabel = connectionRecord.theirLabel ?: "Unknown",
                content = message,
                type = MessageType.BasicMessage
            )
            _receivedMessages.add(messageRecord)
            Log.i(TAG, "Received basic message: $message from ${connectionRecord.theirLabel}")
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun subscribeToEvents() {
        // Subscribe to credential events
        agent.eventBus.subscribe<AgentEvents.CredentialEvent> {
            GlobalScope.launch(Dispatchers.IO) {
                handleCredentialEvent(it)
            }
        }

        // Subscribe to proof events
        agent.eventBus.subscribe<AgentEvents.ProofEvent> {
            GlobalScope.launch(Dispatchers.IO) {
                handleProofEvent(it)
            }
        }
    }

    private suspend fun handleCredentialEvent(event: AgentEvents.CredentialEvent) {
        val theirLabel = agent.connectionRepository
            .getById(event.record.connectionId)
            .theirLabel ?: "Unknown"

        when (event.record.state) {
            CredentialState.OfferReceived -> {
                _receivedMessages.add(
                    MessageRecord(
                        senderConnectionId = theirLabel,
                        senderLabel = theirLabel,
                        type = MessageType.CredentialOffer,
                        content = "Credential offer received. Attributes: ${event.record.getCredentialInfo()}"
                    )
                )
                acceptCredentialOffer(event.record.id)
            }
            CredentialState.Done -> {
                _receivedMessages.add(
                    MessageRecord(
                        senderConnectionId = theirLabel,
                        senderLabel = theirLabel,
                        type = MessageType.CredentialApproved,
                        content = "Credentials: ${event.record.credentialAttributes}"
                    )
                )
                Log.i(TAG, "Credential offer completed: ${event.record.id}")
            }
            else -> {
                // Handle other states if needed
            }
        }
    }

    private suspend fun handleProofEvent(event: AgentEvents.ProofEvent) {
        when (event.record.state) {
            ProofState.RequestReceived -> {
                sendProof(event.record.id)
            }
            ProofState.Done -> {
                Log.i(TAG, "Proof request completed: ${event.record.id}")
            }
            else -> {
                // Handle other states if needed
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun acceptCredentialOffer(id: String) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                agent.credentials.acceptOffer(
                    AcceptOfferOptions(
                        credentialRecordId = id,
                        autoAcceptCredential = AutoAcceptCredential.Always
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error accepting credential offer", e)
            }
        }
    }

    private suspend fun sendProof(id: String) {
        try {
            val retrievedCredentials = agent.proofs.getRequestedCredentialsForProofRequest(id)
            val requestedCredentials = agent.proofService
                .autoSelectCredentialsForProofRequest(retrievedCredentials)
            agent.proofs.acceptRequest(id, requestedCredentials)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending proof request", e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    override suspend fun createInvitation(): Invitation? {
        return try {
            val routing = agent.mediationRecipient.getRouting()
            val invitation = agent.connectionService.createInvitation(
                routing = routing,
                autoAcceptConnection = true,
                label = deviceUserId,
                multiUseInvitation = true,
            )

            Invitation(
                url = "https://${invitation.connection.invitation?.toUrl("public.mediator.indiciotech.io") ?: ""}",
                imageUrl = invitation.connection.invitation?.imageUrl ?: ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error creating invitation", e)
            null
        }
    }

    override suspend fun connectToUser(invitationUrl: String): Boolean {
        if (!walletOpened) return false

        return try {
            val connectionRecord = agent.connections.receiveInvitationFromUrl(invitationUrl)
            Log.i(TAG, "Received invitation: $connectionRecord")
            connectionRecord.state == ConnectionState.Requested
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to user", e)
            false
        }
    }

    override suspend fun getConnections(): List<ConnectionRecord> {
        if (!walletOpened) return emptyList()

        return try {
            agent.connectionRepository.getAll()
                .filter { it.theirDid != null }
                .sortedByDescending { it.createdAt }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting connections", e)
            emptyList()
        }
    }

    override suspend fun sendMessage(
        connectionId: String,
        messageType: MessageType,
        message: String?
    ): Boolean {
        if (!walletOpened) return false

        return try {
            val connection = agent.connectionRepository.getById(connectionId)

            if (connection.state != ConnectionState.Complete) {
                Log.e(TAG, "Connection not in Complete state: ${connection.state}")
                return false
            }

            when (messageType) {
                MessageType.CredentialOffer -> sendCredentialOffer(connection, message)
                MessageType.BasicMessage -> sendBasicMessage(connection, message)
                else -> throw IllegalArgumentException("Unsupported message type: $messageType")
            }

            Log.i(TAG, "Message sent successfully: $message")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message", e)
            false
        }
    }

    private suspend fun sendCredentialOffer(
        connection: ConnectionRecord,
        comment: String?
    ) {
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
            comment = comment ?: "Credential Request",
            autoAcceptCredential = AutoAcceptCredential.Always,
            credentialDefinitionId = credDefId,
            attributes = credentialPreview.attributes
        )

        agent.credentials.offerCredential(offerOptions)
    }

    private suspend fun sendBasicMessage(
        connection: ConnectionRecord,
        message: String?
    ) {
        val outboundMessage = OutboundMessage(
            BasicMessage(message ?: "N/A"),
            connection
        )
        agent.messageSender.send(outboundMessage)
    }

    override fun getReceivedMessages(): List<MessageRecord> {
        return _receivedMessages.toList()
    }

    private suspend fun prepareForIssuance(attributes: List<String>): String {
        Log.i(TAG, "Preparing for credential issuance with attributes: $attributes")

        val didInfo = agent.wallet.publicDid
            ?: throw Exception("Agent has no public DID.")

        val schemaId = agent.ledgerService.registerSchema(
            didInfo,
            SchemaTemplate("schema-${UUID.randomUUID()}", "1.0", attributes),
        )
        Log.i(TAG, "Schema registered with ID: $schemaId")

        delay(0.1.seconds)

        val (schema, seqNo) = agent.ledgerService.getSchema(schemaId)
        Log.i(TAG, "Schema retrieved: $schema with seqNo: $seqNo")

        return agent.ledgerService.registerCredentialDefinition(
            didInfo,
            CredentialDefinitionTemplate(schema, "default", false, seqNo),
        )
    }

    companion object {
        private const val TAG = "AriesWalletRepository"
    }
}
