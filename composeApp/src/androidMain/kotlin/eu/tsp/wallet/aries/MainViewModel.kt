// MainViewModel.kt
package eu.tsp.wallet.aries

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.O)
class MainViewModel(private val application: Application) : AndroidViewModel(application) {
    private val _walletState = MutableStateFlow<WalletState>(WalletState.Initializing)
    val walletState: StateFlow<WalletState> = _walletState

    private val _connectionNotification = MutableStateFlow<String?>(null)
    val connectionNotification: StateFlow<String?> = _connectionNotification

    private val walletApp = WalletApp(application.applicationContext).apply {
        initialise()
    }

    init {
        initializeWallet()
    }


    private fun initializeWallet() {
        viewModelScope.launch {
            try {
                _walletState.value = WalletState.Initializing
                // Wait for wallet to be ready
                while (!walletApp.walletOpened) {
                    kotlinx.coroutines.delay(100)
                }
                kotlinx.coroutines.delay(500)
                val publicDid = walletApp.agent.wallet.publicDid?.did ?: "No DID created yet"
                _walletState.value = WalletState.Ready(publicDid, walletApp.walletKey, walletApp.invitation!!)
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
}