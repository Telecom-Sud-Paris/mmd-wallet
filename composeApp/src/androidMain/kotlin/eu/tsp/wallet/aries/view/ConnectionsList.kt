package eu.tsp.wallet.aries.view

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.tsp.wallet.aries.model.MessageType
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import org.apache.commons.lang3.StringUtils
import org.hyperledger.ariesframework.connection.repository.ConnectionRecord
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
            // Set height constraint to enable scrolling
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(
                    items = connections,
                    key = { it.id }
                ) { connection ->
                    val isSelected = connection.id == selectedConnectionId
                    val isSelectable = connection.theirLabel != "Indicio Public Mediator"

                    // Determine card appearance based on selection state and selectability
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

                    androidx.compose.material3.Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .then(
                                if (isSelectable) {
                                    Modifier.clickable {
                                        // Toggle selection - deselect if already selected
                                        selectedConnectionId = if (isSelected) null else connection.id
                                    }
                                } else {
                                    Modifier
                                }
                            ),
                        colors = androidx.compose.material3.CardDefaults.cardColors(
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
                                text = "Their DID: ${
                                    StringUtils.abbreviate(
                                        connection.theirDid,
                                        20
                                    )
                                }",
                                style = MaterialTheme.typography.bodySmall,
                                color = secondaryTextColor
                            )
                            Text(
                                text = "State: ${connection.state}",
                                style = MaterialTheme.typography.bodySmall,
                                color = secondaryTextColor
                            )
                            Text(
                                text = "Created: ${formatTimestamp(connection.createdAt)}",
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
            }

            // Message input for selected connection - only shown when a connection is selected
            selectedConnectionId?.let { connectionId ->
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
                    Button(
                        onClick = {
                            onSendMessage(connectionId, MessageType.CredentialOffer, null)
                        }
                    ) {
                        Text("Request Access")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Send a text message:",
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
                        onValueChange = { messageText = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Message") }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (messageText.isNotBlank()) {
                                onSendMessage(connectionId, MessageType.BasicMessage, messageText)
                                messageText = ""
                            }
                        }
                    ) {
                        Text("Send")
                    }
                }
            }
        }
    }
}

// Helper function to format the timestamp
@RequiresApi(Build.VERSION_CODES.O)
private fun formatTimestamp(timestamp: Instant?): String {
    if (timestamp == null) return "N/A"

    return try {
        // Parse ISO-8601 date format
        val formatter = DateTimeFormatter
            .ofPattern("MMM d, yyyy HH:mm")
            .withZone(ZoneId.systemDefault())

        formatter.format(timestamp.toJavaInstant())
    } catch (e: Exception) {
        // Return raw timestamp if parsing fails
        timestamp.toEpochMilliseconds().toString()
    }
}