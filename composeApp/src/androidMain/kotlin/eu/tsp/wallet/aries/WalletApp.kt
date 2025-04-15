package eu.tsp.wallet.aries

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.AgentConfig
import org.hyperledger.ariesframework.agent.MediatorPickupStrategy
import org.hyperledger.ariesframework.credentials.models.AutoAcceptCredential
import org.hyperledger.ariesframework.proofs.models.AutoAcceptProof
import java.io.File
import java.time.Instant

const val PREFERENCE_NAME = "aries-framework-kotlin-sample"
const val genesisPath = "bcovrin-genesis.txn"

class WalletApp(private val context: Context) {
    lateinit var agent: Agent
    var walletOpened: Boolean = false
    val walletKey: String get() {
            val pref = context.getSharedPreferences(PREFERENCE_NAME, 0)
            return pref.getString("walletKey", null) ?: ""
        }

    var invitation: Invitation? = null

    private fun copyResourceFile(resource: String) {
        val inputStream = context.assets.open(resource)
        val file = File(context.filesDir.absolutePath, resource)
        file.outputStream().use { inputStream.copyTo(it) }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun openWallet() {
        try {
            val pref = context.getSharedPreferences(PREFERENCE_NAME, 0)
            var key = pref.getString("walletKey", null)

            if (key == null) {
                key = Agent.generateWalletKey()
                pref.edit().putString("walletKey", key).apply()
            }
            copyResourceFile(genesisPath)

            val invitationUrl =
                "https://public.mediator.indiciotech.io?c_i=eyJAdHlwZSI6ICJkaWQ6c292OkJ6Q2JzTlloTXJqSGlxWkRUVUFTSGc7c3BlYy9jb25uZWN0aW9ucy8xLjAvaW52aXRhdGlvbiIsICJAaWQiOiAiMDVlYzM5NDItYTEyOS00YWE3LWEzZDQtYTJmNDgwYzNjZThhIiwgInNlcnZpY2VFbmRwb2ludCI6ICJodHRwczovL3B1YmxpYy5tZWRpYXRvci5pbmRpY2lvdGVjaC5pbyIsICJyZWNpcGllbnRLZXlzIjogWyJDc2dIQVpxSktuWlRmc3h0MmRIR3JjN3U2M3ljeFlEZ25RdEZMeFhpeDIzYiJdLCAibGFiZWwiOiAiSW5kaWNpbyBQdWJsaWMgTWVkaWF0b3IifQ==" // ktlint-disable max-line-length
            // val invitationUrl = URL("http://10.0.2.2:3001/invitation").readText() // This uses local AFJ mediator and needs MediatorPickupStrategy.PickUpV1
            val config = AgentConfig(
                walletKey = key,
                genesisPath = File(context.filesDir.absolutePath, genesisPath).absolutePath,
                mediatorConnectionsInvite = invitationUrl,
                mediatorPickupStrategy = MediatorPickupStrategy.Implicit,
                label = "MoreMedDietAriesAgent",
                autoAcceptCredential = AutoAcceptCredential.Never,
                autoAcceptProof = AutoAcceptProof.Never,
                publicDidSeed = "MichalKit${Instant.now().toEpochMilli()}".padEnd(32, '0')
            )
            agent = Agent(context, config)
            agent.initialize()
            this.invitation = createInvitation()
            Log.i("demo", "Invitation URL: $invitationUrl")
            walletOpened = true
        } catch (e: Exception) {
            Log.e("WalletApp", "Error opening wallet", e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @OptIn(DelicateCoroutinesApi::class)
    fun initialise() {
        GlobalScope.launch(Dispatchers.IO) {
            openWallet()
        }
    }

    suspend fun createInvitation(): Invitation? {
        Log.i("WalletApp", "Creating invitation")
        return try {
            // Create a connection invitation
            val routing = agent.mediationRecipient.getRouting()
            val invitation = agent.connectionService.createInvitation(
                routing = routing,
                autoAcceptConnection = true,
                label = "MoreMedDietAriesInvite",
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
            // Create a connection invitation or use Out-of-Band protocol
            val connectionRecord = agent.connections.receiveInvitationFromUrl(invitationUrl)
            Log.i("WalletApp", "Received invitation: $connectionRecord")
            return connectionRecord != null
        } catch (e: Exception) {
            Log.e("WalletApp", "Error connecting to user", e)
            return false
        }
    }
}

data class Invitation(val url: String, val imageUrl: String)