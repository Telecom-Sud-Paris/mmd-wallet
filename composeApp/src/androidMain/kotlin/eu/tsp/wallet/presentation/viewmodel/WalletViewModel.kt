package eu.tsp.wallet.presentation.viewmodel

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import eu.tsp.wallet.core.Constants
import eu.tsp.wallet.data.repository.AriesWalletRepository
import eu.tsp.wallet.domain.model.MessageRecord
import eu.tsp.wallet.domain.model.MessageType
import eu.tsp.wallet.domain.model.WalletState
import eu.tsp.wallet.domain.repository.WalletRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.hyperledger.ariesframework.connection.repository.ConnectionRecord

/**
 * ViewModel for managing wallet state and operations
 */
@RequiresApi(Build.VERSION_CODES.O)
class WalletViewModel(
    application: Application,
    private val deviceUserId: String,
    private val walletRepository: WalletRepository = AriesWalletRepository(
        application.applicationContext,
        deviceUserId
    )
) : AndroidViewModel(application) {

    private val _walletState = MutableStateFlow<WalletState>(WalletState.Initializing)
    val walletState: StateFlow<WalletState> = _walletState.asStateFlow()

    private val _connectionNotification = MutableStateFlow<String?>(null)
    val connectionNotification: StateFlow<String?> = _connectionNotification.asStateFlow()

    private val _connections = MutableStateFlow<List<ConnectionRecord>>(emptyList())
    val connections: StateFlow<List<ConnectionRecord>> = _connections.asStateFlow()

    private val _messages = MutableStateFlow<List<MessageRecord>>(emptyList())
    val messages: StateFlow<List<MessageRecord>> = _messages.asStateFlow()

    init {
        initializeWallet()
        startConnectionsMonitoring()
        startMessagesMonitoring()
    }

    private fun initializeWallet() {
        viewModelScope.launch {
            try {
                _walletState.value = WalletState.Initializing

                // Initialize wallet in background
                launch { walletRepository.initialize() }

                // Wait for wallet to be ready
                while (!walletRepository.isWalletOpened()) {
                    delay(100)
                }

                delay(500) // Give a bit more time for initialization

                val publicDid = walletRepository.getPublicDid() ?: "No DID created yet"
                val invitation = walletRepository.createInvitation()

                if (invitation != null) {
                    _walletState.value = WalletState.Ready(
                        publicDid = publicDid,
                        walletId = walletRepository.getWalletKey(),
                        invitation = invitation
                    )
                } else {
                    _walletState.value = WalletState.Error("Failed to create invitation")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Wallet initialization error", e)
                _walletState.value = WalletState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun startConnectionsMonitoring() {
        viewModelScope.launch {
            while (true) {
                if (walletRepository.isWalletOpened()) {
                    try {
                        _connections.value = walletRepository.getConnections()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error fetching connections", e)
                    }
                }
                delay(Constants.CONNECTIONS_REFRESH_INTERVAL_MS)
            }
        }
    }

    private fun startMessagesMonitoring() {
        viewModelScope.launch {
            while (true) {
                updateMessages()
                delay(Constants.MESSAGES_REFRESH_INTERVAL_MS)
            }
        }
    }

    private fun updateMessages() {
        _messages.value = walletRepository.getReceivedMessages()
            .sortedByDescending { it.receivedAt }
    }

    fun connectToUser(invitationUrl: String) {
        val currentState = walletState.value
        if (currentState !is WalletState.Ready) return

        viewModelScope.launch {
            try {
                if (currentState.invitation.url == invitationUrl) {
                    showNotification("That is my own invitation URL.")
                    return@launch
                }

                showNotification("Connecting to user...")
                val success = walletRepository.connectToUser(invitationUrl)

                showNotification(
                    if (success) "Connection established successfully!"
                    else "Failed to establish connection",
                    duration = Constants.NOTIFICATION_DISPLAY_DURATION_MS
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting to user", e)
                showNotification("Error: ${e.message}")
            }
        }
    }

    fun sendMessage(connectionId: String, messageType: MessageType, message: String?) {
        if (walletState.value !is WalletState.Ready) return

        viewModelScope.launch {
            try {
                showNotification("Sending message...")
                val success = walletRepository.sendMessage(connectionId, messageType, message)

                showNotification(
                    if (success) "Message sent successfully!"
                    else "Failed to send message",
                    duration = Constants.MESSAGE_SENT_NOTIFICATION_DURATION_MS
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error sending message", e)
                showNotification("Error: ${e.message}")
            }
        }
    }

    private suspend fun showNotification(message: String, duration: Long = 5000) {
        _connectionNotification.value = message
        delay(duration)
        _connectionNotification.value = null
    }

    companion object {
        private const val TAG = "WalletViewModel"
    }
}
