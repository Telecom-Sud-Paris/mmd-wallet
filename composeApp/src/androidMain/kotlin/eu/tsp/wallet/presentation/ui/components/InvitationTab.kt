package eu.tsp.wallet.presentation.ui.components

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import eu.tsp.wallet.R
import eu.tsp.wallet.domain.model.WalletState
import eu.tsp.wallet.presentation.viewmodel.WalletViewModel
import org.apache.commons.lang3.StringUtils

/**
 * Tab showing wallet invitation and connection functionality
 */
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun InvitationTab(viewModel: WalletViewModel) {
    val walletState by viewModel.walletState.collectAsState()
    val connectionNotification by viewModel.connectionNotification.collectAsState()
    var invitationUrl by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Logo
        Image(
            painter = painterResource(id = R.drawable.mmd_logo),
            contentDescription = "Wallet Logo",
            modifier = Modifier
                .padding(vertical = 10.dp)
                .height(100.dp)
        )

        // Wallet Status
        WalletStatusSection(walletState = walletState, context = context)

        Spacer(modifier = Modifier.height(16.dp))

        // Connection Section
        ConnectionSection(
            invitationUrl = invitationUrl,
            onInvitationUrlChange = { invitationUrl = it },
            onPasteClick = { invitationUrl = pasteFromClipboard(context) },
            onConnectClick = { viewModel.connectToUser(invitationUrl) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Notification
        connectionNotification?.let { notification ->
            NotificationText(notification)
        }
    }
}

@Composable
private fun WalletStatusSection(
    walletState: WalletState,
    context: android.content.Context
) {
    when (val state = walletState) {
        is WalletState.Initializing -> {
            CircularProgressIndicator()
            Text("Initializing wallet...")
        }

        is WalletState.Ready -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Wallet Ready!", style = MaterialTheme.typography.titleLarge)
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
                QrCode(
                    content = state.invitation.url,
                    size = 180.dp
                )
                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        copyToClipboard(
                            context = context,
                            label = "Invitation URL",
                            text = state.invitation.url
                        )
                    }
                ) {
                    Text("Copy Invite URL")
                }
            }
        }

        is WalletState.Error -> {
            Text(
                "Error: ${state.message}",
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun ConnectionSection(
    invitationUrl: String,
    onInvitationUrlChange: (String) -> Unit,
    onPasteClick: () -> Unit,
    onConnectClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Connect to Others",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        TextField(
            value = invitationUrl,
            onValueChange = onInvitationUrlChange,
            label = { Text("Invitation URL") },
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Button(onClick = onPasteClick) {
                Icon(
                    imageVector = Icons.Filled.ContentPaste,
                    contentDescription = "Paste from clipboard"
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(onClick = onConnectClick) {
                Icon(Icons.Filled.Done, contentDescription = "Accept Invite")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Accept Invite")
            }
        }
    }
}

@Composable
private fun NotificationText(notification: String) {
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

private fun copyToClipboard(
    context: android.content.Context,
    label: String,
    text: String
) {
    val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
            as android.content.ClipboardManager
    val clipData = android.content.ClipData.newPlainText(label, text)
    clipboardManager.setPrimaryClip(clipData)
    android.widget.Toast.makeText(
        context,
        "$label copied to clipboard",
        android.widget.Toast.LENGTH_SHORT
    ).show()
}

private fun pasteFromClipboard(context: android.content.Context): String {
    val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
            as android.content.ClipboardManager

    if (clipboardManager.hasPrimaryClip()) {
        val item = clipboardManager.primaryClip?.getItemAt(0)
        val pasteData = item?.text?.toString()
        if (pasteData != null) {
            android.widget.Toast.makeText(
                context,
                "Pasted from clipboard",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return pasteData
        }
    }
    return ""
}
