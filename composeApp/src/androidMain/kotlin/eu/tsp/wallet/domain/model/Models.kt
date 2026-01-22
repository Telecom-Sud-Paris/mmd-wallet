package eu.tsp.wallet.domain.model

/**
 * Represents an Aries connection invitation
 */
data class Invitation(
    val url: String,
    val imageUrl: String
)

/**
 * Sealed class representing different wallet states
 */
sealed class WalletState {
    data object Initializing : WalletState()
    data class Ready(
        val publicDid: String,
        val walletId: String,
        val invitation: Invitation
    ) : WalletState()
    data class Error(val message: String) : WalletState()
}

/**
 * Represents a message received in the wallet
 */
data class MessageRecord(
    val senderConnectionId: String,
    val senderLabel: String?,
    val type: MessageType,
    val content: String,
    val receivedAt: Long = System.currentTimeMillis()
)

/**
 * Types of messages that can be sent/received
 */
enum class MessageType {
    BasicMessage,
    CredentialOffer,
    CredentialApproved,
    ProofRequest,
    ProofResponse,
    Unknown
}
