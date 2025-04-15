// WalletState.kt
package eu.tsp.wallet.aries

sealed class WalletState {
    object Initializing : WalletState()
    data class Ready(val publicDid: String, val walletId: String, val invitation: Invitation) : WalletState()
    data class Error(val message: String) : WalletState()
}