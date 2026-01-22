package eu.tsp.wallet.aries.view

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import eu.tsp.wallet.R
import eu.tsp.wallet.aries.model.MessageRecord
import eu.tsp.wallet.aries.model.MainViewModel
import eu.tsp.wallet.aries.model.WalletState
import org.apache.commons.lang3.StringUtils
import java.time.Instant

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun MainScreen(viewModel: MainViewModel, deviceUserId: String) {
    var selectedTabIndex by remember { mutableStateOf(0) }

    // Tab data with icons and labels
    data class TabItem(val icon: ImageVector, val label: String)
    val tabs = listOf(
        TabItem(Icons.Filled.QrCode2, "Invite"),
        TabItem(Icons.Filled.People, "Connections"),
        TabItem(Icons.Filled.Badge, "Credentials"),
        TabItem(Icons.Filled.Email, "Messages")
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            // Tab Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                tabs.forEachIndexed { index, tab ->
                    IconTabButton(
                        icon = tab.icon,
                        label = tab.label,
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = deviceUserId,
                style = MaterialTheme.typography.headlineMedium,
            )

            // Tab Content
            when (selectedTabIndex) {
                0 -> InvitationTab(viewModel)
                1 -> ConnectionsTab(viewModel)
                2 -> CredentialsTab(viewModel)
                3 -> MessagesTab(viewModel)
            }
        }
    }
}

@Composable
fun IconTabButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (selected) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surface
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurface

    Surface(
        modifier = modifier
            .padding(horizontal = 2.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = backgroundColor,
        contentColor = contentColor
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.height(24.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun InvitationTab(viewModel: MainViewModel) {
    val walletState by viewModel.walletState.collectAsState()
    val connectionNotification by viewModel.connectionNotification.collectAsState()
    var invitationUrl by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(id = R.drawable.mmd_logo),
            contentDescription = "Wallet Logo",
            modifier = Modifier
                .padding(vertical = 10.dp)
                .height(100.dp)
        )

        when (val state = walletState) {
            is WalletState.Initializing -> {
                CircularProgressIndicator()
                Text("Initializing wallet...")
            }

            is WalletState.Ready -> {
                Text("Wallet Ready!")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Public DID: ${state.publicDid}")
                Spacer(modifier = Modifier.height(8.dp))
                Text(StringUtils.abbreviate("Wallet ID: ${state.walletId}", 40))
                Spacer(modifier = Modifier.height(16.dp))

                // QR Code for invitation
                Text(
                    text = "Scan to Connect",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                eu.tsp.wallet.presentation.ui.components.QrCode(
                    content = state.invitation.url,
                    size = 180.dp
                )
                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        val clipboardManager =
                            context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clipData = android.content.ClipData.newPlainText(
                            "Invitation URL",
                            state.invitation.url
                        )
                        clipboardManager.setPrimaryClip(clipData)
                        android.widget.Toast.makeText(
                            context,
                            "Invitation copied to clipboard",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                ) {
                    Text("Copy Invite URL")
                }
            }

            is WalletState.Error -> {
                Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Connect to Others",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        TextField(
            value = invitationUrl,
            onValueChange = { invitationUrl = it },
            label = { Text("Invitation URL") },
            singleLine = true
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            Button(
                onClick = {
                    val clipboardManager =
                        context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    if (clipboardManager.hasPrimaryClip()) {
                        val item = clipboardManager.primaryClip?.getItemAt(0)
                        val pasteData = item?.text?.toString()
                        if (pasteData != null) {
                            invitationUrl = pasteData
                            android.widget.Toast.makeText(
                                context,
                                "Pasted from clipboard",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentPaste,
                    contentDescription = "Paste from clipboard"
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { viewModel.connectToUser(invitationUrl) },
                content = {
                    Icon(Icons.Filled.Done, contentDescription = "Accept Invite")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Accept Invite")
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        connectionNotification?.let { notification ->
            Text(
                text = notification,
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    notification.startsWith("Error") -> MaterialTheme.colorScheme.error
                    notification.startsWith("Connection established") -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun ConnectionsTab(viewModel: MainViewModel) {
    val connections by viewModel.connections.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
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
fun CredentialsTab(viewModel: MainViewModel) {
    val credentials by viewModel.credentials.collectAsState()
    val oid4vciCredentials by viewModel.oid4vciCredentials.collectAsState()
    val credentialNotification by viewModel.credentialNotification.collectAsState()
    var invitationUrl by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Add Credential Section
        Text(
            text = "Add Credential",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "Supports Aries (DIDComm) and OpenID4VCI URLs",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        TextField(
            value = invitationUrl,
            onValueChange = { invitationUrl = it },
            label = { Text("Credential Invitation URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    val clipboardManager =
                        context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    if (clipboardManager.hasPrimaryClip()) {
                        val item = clipboardManager.primaryClip?.getItemAt(0)
                        val pasteData = item?.text?.toString()
                        if (pasteData != null) {
                            invitationUrl = pasteData
                            android.widget.Toast.makeText(
                                context,
                                "Pasted from clipboard",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentPaste,
                    contentDescription = "Paste from clipboard"
                )
            }

            Button(
                onClick = {
                    if (invitationUrl.isNotBlank()) {
                        viewModel.receiveCredentialFromUrl(invitationUrl)
                        invitationUrl = ""
                    }
                },
                enabled = invitationUrl.isNotBlank()
            ) {
                Icon(Icons.Filled.Done, contentDescription = "Add Credential")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add")
            }
        }

        // Notification
        credentialNotification?.let { notification ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = notification,
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    notification.startsWith("Error") -> MaterialTheme.colorScheme.error
                    notification.contains("success", ignoreCase = true) -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Divider()
        Spacer(modifier = Modifier.height(16.dp))

        // Credentials List
        CredentialsList(
            ariesCredentials = credentials,
            oid4vciCredentials = oid4vciCredentials,
            onDeleteAriesCredential = { credentialId ->
                viewModel.deleteCredential(credentialId)
            },
            onDeleteOID4VCICredential = { credentialId ->
                viewModel.deleteOID4VCICredential(credentialId)
            }
        )
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun MessagesTab(viewModel: MainViewModel) {
    val messages by viewModel.messages.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "Received Messages",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (messages.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No messages received yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // Use weight to ensure LazyColumn takes available space
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f) // This ensures the list takes available space and is scrollable
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = messages,
                    key = { "${it.senderConnectionId}-${it.receivedAt}" } // Use a stable key for better performance
                ) { message ->
                    MessageItem(message)
                    Divider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }

            // Message count at the bottom
            Text(
                text = "${messages.size} message(s)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun MessageItem(message: MessageRecord) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "From: ${message.senderLabel}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = formatTimestamp(message.receivedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Type: ${message.type.name}",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
fun formatTimestamp(timestamp: Long): String {
    val instant = Instant.ofEpochMilli(timestamp)
    val formatter = java.time.format.DateTimeFormatter
        .ofPattern("MMM dd, yyyy HH:mm")
        .withZone(java.time.ZoneId.systemDefault())
    return formatter.format(instant)
}