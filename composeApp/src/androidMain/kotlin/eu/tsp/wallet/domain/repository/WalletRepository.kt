package eu.tsp.wallet.domain.repository

import eu.tsp.wallet.domain.model.Invitation
import eu.tsp.wallet.domain.model.MessageRecord
import eu.tsp.wallet.domain.model.MessageType
import org.hyperledger.ariesframework.connection.repository.ConnectionRecord

/**
 * Repository interface for wallet operations
 */
interface WalletRepository {
    /**
     * Initialize the wallet
     */
    suspend fun initialize()

    /**
     * Check if wallet is opened
     */
    fun isWalletOpened(): Boolean

    /**
     * Get the wallet key
     */
    fun getWalletKey(): String

    /**
     * Get the public DID
     */
    suspend fun getPublicDid(): String?

    /**
     * Create an invitation for others to connect
     */
    suspend fun createInvitation(): Invitation?

    /**
     * Connect to another user using their invitation URL
     */
    suspend fun connectToUser(invitationUrl: String): Boolean

    /**
     * Get all connections
     */
    suspend fun getConnections(): List<ConnectionRecord>

    /**
     * Send a message to a connection
     */
    suspend fun sendMessage(
        connectionId: String,
        messageType: MessageType,
        message: String?
    ): Boolean

    /**
     * Get all received messages
     */
    fun getReceivedMessages(): List<MessageRecord>
}
