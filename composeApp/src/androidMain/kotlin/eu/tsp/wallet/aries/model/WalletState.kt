// WalletState.kt
package eu.tsp.wallet.aries.model

import eu.tsp.wallet.aries.Invitation

sealed class WalletState {
    object Initializing : WalletState()
    data class Ready(val publicDid: String, val walletId: String, val invitation: Invitation) : WalletState()
    data class Error(val message: String) : WalletState()
}