package eu.tsp.wallet.aries.model

data class MessageRecord(
    val senderConnectionId: String,
    val senderLabel: String?,
    val type: MessageType,
    val content: String,
    val receivedAt: Long = System.currentTimeMillis()
)

enum class MessageType {
    BasicMessage,
    CredentialOffer,
    CredentialApproved,
    ProofRequest,
    ProofResponse,
    Unknown
}