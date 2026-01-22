// MainViewModel.kt
package eu.tsp.wallet.aries.model

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import eu.tsp.wallet.aries.CredentialReceiveResult
import eu.tsp.wallet.aries.WalletApp
import eu.tsp.wallet.oid4vci.OID4VCICredential
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.hyperledger.ariesframework.anoncreds.storage.CredentialRecord
import org.hyperledger.ariesframework.connection.repository.ConnectionRecord

@RequiresApi(Build.VERSION_CODES.O)
class MainViewModel(application: Application, deviceUserId: String) : AndroidViewModel(application) {
    private val _walletState = MutableStateFlow<WalletState>(WalletState.Initializing)
    val walletState: StateFlow<WalletState> = _walletState

    private val _connectionNotification = MutableStateFlow<String?>(null)
    val connectionNotification: StateFlow<String?> = _connectionNotification

    private val _connections = MutableStateFlow<List<ConnectionRecord>>(emptyList())
    val connections: StateFlow<List<ConnectionRecord>> = _connections

    private val _messages = MutableStateFlow<List<MessageRecord>>(emptyList())
    val messages: StateFlow<List<MessageRecord>> = _messages

    private val _credentials = MutableStateFlow<List<CredentialRecord>>(emptyList())
    val credentials: StateFlow<List<CredentialRecord>> = _credentials

    private val _oid4vciCredentials = MutableStateFlow<List<OID4VCICredential>>(emptyList())
    val oid4vciCredentials: StateFlow<List<OID4VCICredential>> = _oid4vciCredentials

    private val _credentialNotification = MutableStateFlow<String?>(null)
    val credentialNotification: StateFlow<String?> = _credentialNotification

    private val walletApp = WalletApp(application.applicationContext, deviceUserId).apply {
        initialise()
    }

    init {
        initializeWallet()

        // Start monitoring connections - only after wallet is opened
        viewModelScope.launch {
            while (true) {
                try {
                    if (walletApp.walletOpened && walletApp.initializationError == null) {
                        val currentConnections = walletApp.getConnections()
                        _connections.value = currentConnections
                    }
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Error fetching connections", e)
                }
                delay(3000) // Refresh every 3 seconds
            }
        }
        viewModelScope.launch {
            while (true) {
                try {
                    if (walletApp.walletOpened) {
                        updateMessages()
                    }
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Error updating messages", e)
                }
                delay(2000) // Update every 2 seconds
            }
        }
        // Start monitoring credentials
        viewModelScope.launch {
            while (true) {
                try {
                    if (walletApp.walletOpened && walletApp.initializationError == null) {
                        val currentCredentials = walletApp.getCredentials()
                        _credentials.value = currentCredentials
                    }
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Error fetching credentials", e)
                }
                delay(3000) // Refresh every 3 seconds
            }
        }
        // Start monitoring OID4VCI credentials
        viewModelScope.launch {
            while (true) {
                try {
                    _oid4vciCredentials.value = walletApp.getOID4VCICredentials()
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Error fetching OID4VCI credentials", e)
                }
                delay(3000) // Refresh every 3 seconds
            }
        }
    }

    fun updateMessages() {
        viewModelScope.launch {
            _messages.value = walletApp.receivedMessages.sortedByDescending { it.receivedAt }
        }
    }


    private fun initializeWallet() {
        viewModelScope.launch {
            try {
                _walletState.value = WalletState.Initializing
                // Wait for wallet to be ready with a timeout
                var waitTime = 0
                val maxWaitTime = 30000 // 30 seconds timeout
                while (!walletApp.walletOpened && walletApp.initializationError == null && waitTime < maxWaitTime) {
                    kotlinx.coroutines.delay(100)
                    waitTime += 100
                }

                // Check for initialization error
                walletApp.initializationError?.let { error ->
                    _walletState.value = WalletState.Error(error)
                    return@launch
                }

                if (!walletApp.walletOpened) {
                    _walletState.value = WalletState.Error("Wallet initialization timed out")
                    return@launch
                }

                kotlinx.coroutines.delay(500)
                val publicDid = walletApp.agent.wallet.publicDid?.did ?: "No DID created yet"
                val invitation = walletApp.invitation

                if (invitation == null) {
                    _walletState.value = WalletState.Error("Failed to create invitation")
                    return@launch
                }

                _walletState.value = WalletState.Ready(publicDid, walletApp.walletKey, invitation)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Wallet initialization error", e)
                _walletState.value = WalletState.Error(e.message ?: "Unknown error")
            }
        }
    }


    fun connectToUser(invitationUrl: String) {
        if (walletState.value !is WalletState.Ready) return

        viewModelScope.launch {
            try {
                if ((walletState.value as WalletState.Ready).invitation.url == invitationUrl) {
                    _connectionNotification.value = "That is my own invitation URL."
                    return@launch
                }
                _connectionNotification.value = "Connecting to user..."
                val success = walletApp.connectToUser(invitationUrl)
                _connectionNotification.value = if (success) {
                    "Connection established successfully!"
                } else {
                    "Failed to establish connection"
                }

                // Clear notification after 5 seconds
                kotlinx.coroutines.delay(5000)
                _connectionNotification.value = null
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error connecting to user", e)
                _connectionNotification.value = "Error: ${e.message}"
            }
        }
    }

    fun sendMessage(connectionId: String, messageType: MessageType, message: String?) {
        if (walletState.value !is WalletState.Ready) return

        viewModelScope.launch {
            try {
                _connectionNotification.value = "Sending message..."
                val success = walletApp.sendMessageToConnection(connectionId, messageType, message)
                _connectionNotification.value = if (success) {
                    "Message sent successfully!"
                } else {
                    "Failed to send message"
                }

                // Clear notification after 3 seconds
                kotlinx.coroutines.delay(3000)
                _connectionNotification.value = null
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error sending message", e)
                _connectionNotification.value = "Error: ${e.message}"
            }
        }
    }

    fun deleteCredential(credentialId: String) {
        if (walletState.value !is WalletState.Ready) return

        viewModelScope.launch {
            try {
                _credentialNotification.value = "Deleting credential..."
                val success = walletApp.deleteCredential(credentialId)
                _credentialNotification.value = if (success) {
                    "Credential deleted successfully!"
                } else {
                    "Failed to delete credential"
                }

                // Refresh credentials list
                if (success) {
                    _credentials.value = walletApp.getCredentials()
                }

                // Clear notification after 3 seconds
                delay(3000)
                _credentialNotification.value = null
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error deleting credential", e)
                _credentialNotification.value = "Error: ${e.message}"
            }
        }
    }

    fun deleteOID4VCICredential(credentialId: String) {
        viewModelScope.launch {
            try {
                _credentialNotification.value = "Deleting credential..."
                val success = walletApp.deleteOID4VCICredential(credentialId)
                _credentialNotification.value = if (success) {
                    "Credential deleted successfully!"
                } else {
                    "Failed to delete credential"
                }

                // Refresh OID4VCI credentials list
                if (success) {
                    _oid4vciCredentials.value = walletApp.getOID4VCICredentials()
                }

                // Clear notification after 3 seconds
                delay(3000)
                _credentialNotification.value = null
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error deleting OID4VCI credential", e)
                _credentialNotification.value = "Error: ${e.message}"
            }
        }
    }

    fun receiveCredentialFromUrl(invitationUrl: String) {
        viewModelScope.launch {
            try {
                _credentialNotification.value = "Processing invitation..."
                val result = walletApp.receiveCredentialFromUrl(invitationUrl)

                _credentialNotification.value = when (result) {
                    is CredentialReceiveResult.Success -> result.message
                    is CredentialReceiveResult.Error -> result.message
                }

                // Refresh credentials lists
                if (result is CredentialReceiveResult.Success) {
                    _credentials.value = walletApp.getCredentials()
                    _oid4vciCredentials.value = walletApp.getOID4VCICredentials()
                }

                // Clear notification after 5 seconds
                delay(5000)
                _credentialNotification.value = null
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error receiving credential", e)
                _credentialNotification.value = "Error: ${e.message}"
            }
        }
    }
}