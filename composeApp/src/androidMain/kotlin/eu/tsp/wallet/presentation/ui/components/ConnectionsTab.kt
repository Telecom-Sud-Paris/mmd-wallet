package eu.tsp.wallet.presentation.ui.components

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.tsp.wallet.core.Constants
import eu.tsp.wallet.domain.model.MessageType
import eu.tsp.wallet.presentation.viewmodel.WalletViewModel
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import org.apache.commons.lang3.StringUtils
import org.hyperledger.ariesframework.connection.repository.ConnectionRecord
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Tab showing active connections
 */
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun ConnectionsTab(viewModel: WalletViewModel) {
    val connections by viewModel.connections.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        ConnectionsList(
            connections = connections,
            onSendMessage = { connectionId, messageType, message ->
                viewModel.sendMessage(connectionId, messageType, message)
            }
        )
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun ConnectionsList(
    connections: List<ConnectionRecord>,
    onSendMessage: (String, MessageType, String?) -> Unit
) {
    var selectedConnectionId by remember { mutableStateOf<String?>(null) }
    var messageText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        Text(
            text = "Connections",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (connections.isEmpty()) {
            Text("No connections yet", style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(
                    items = connections,
                    key = { it.id }
                ) { connection ->
                    ConnectionItem(
                        connection = connection,
                        isSelected = connection.id == selectedConnectionId,
                        onClick = {
                            selectedConnectionId = if (selectedConnectionId == connection.id) {
                                null
                            } else {
                                connection.id
                            }
                        }
                    )
                }
            }

            // Message input for selected connection
            selectedConnectionId?.let { connectionId ->
                MessageInputSection(
                    messageText = messageText,
                    onMessageTextChange = { messageText = it },
                    onRequestAccess = {
                        onSendMessage(connectionId, MessageType.CredentialOffer, null)
                    },
                    onSendMessage = {
                        if (messageText.isNotBlank()) {
                            onSendMessage(connectionId, MessageType.BasicMessage, messageText)
                            messageText = ""
                        }
                    }
                )
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun ConnectionItem(
    connection: ConnectionRecord,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val isSelectable = connection.theirLabel != Constants.MEDIATOR_LABEL

    val cardColor = when {
        !isSelectable -> MaterialTheme.colorScheme.surfaceVariant
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }

    val textColor = when {
        !isSelectable -> MaterialTheme.colorScheme.onSurfaceVariant
        isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    val secondaryTextColor = when {
        !isSelectable -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .then(
                if (isSelectable) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = cardColor
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = connection.theirLabel ?: "Unknown",
                style = MaterialTheme.typography.titleSmall,
                color = textColor
            )
            Text(
                text = "Their DID: ${StringUtils.abbreviate(connection.theirDid, 20)}",
                style = MaterialTheme.typography.bodySmall,
                color = secondaryTextColor
            )
            Text(
                text = "State: ${connection.state}",
                style = MaterialTheme.typography.bodySmall,
                color = secondaryTextColor
            )
            Text(
                text = "Created: ${formatConnectionTimestamp(connection.createdAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = secondaryTextColor
            )

            if (!isSelectable) {
                Text(
                    text = "(Not selectable)",
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryTextColor
                )
            }
        }
    }
}

@Composable
private fun MessageInputSection(
    messageText: String,
    onMessageTextChange: (String) -> Unit,
    onRequestAccess: () -> Unit,
    onSendMessage: () -> Unit
) {
    Column {
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Request Channel Access:",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onRequestAccess) {
                Text("Request Access")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "Send a text message:",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 2.dp)
        )

        Spacer(modifier = Modifier.height(2.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = messageText,
                onValueChange = onMessageTextChange,
                modifier = Modifier.weight(1f),
                label = { Text("Message") }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onSendMessage) {
                Text("Send")
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
private fun formatConnectionTimestamp(timestamp: Instant): String {
    val formatter = DateTimeFormatter
        .ofPattern("MMM dd, HH:mm")
        .withZone(ZoneId.systemDefault())
    return formatter.format(timestamp.toJavaInstant())
}
