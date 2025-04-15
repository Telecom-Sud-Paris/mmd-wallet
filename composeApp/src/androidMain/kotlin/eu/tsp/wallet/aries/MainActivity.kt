// MainActivity.kt
package eu.tsp.wallet.aries

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.material.icons.filled.Search

@RequiresApi(Build.VERSION_CODES.O)
class MainActivity : ComponentActivity() {
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = MainViewModel(application)
        setContent {
            WalletDemoScreen(viewModel)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun WalletDemoScreen(viewModel: MainViewModel) {
    val walletState by viewModel.walletState.collectAsState()
    val connectionNotification by viewModel.connectionNotification.collectAsState()
    var invitationUrl by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
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
                    Text("Wallet ID: ${state.walletId}")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Invite: ${state.invitation.url}")
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clipData = android.content.ClipData.newPlainText("Invitation URL", state.invitation.url)
                            clipboardManager.setPrimaryClip(clipData)
                            android.widget.Toast.makeText(context, "Invitation copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("Copy Invite")
                    }
                }

                is WalletState.Error -> {
                    Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Find Users",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Spacer(modifier = Modifier.height(30.dp))
            TextField(
                value = invitationUrl,
                onValueChange = { invitationUrl = it },
                label = { Text("Invitation URL") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { viewModel.connectToUser(invitationUrl) },
                content = {
                    Icon(Icons.Filled.Done, contentDescription = "Accept Invite")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Accept Invite")
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
            connectionNotification?.let { notification ->
                Text(
                    text = notification,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (notification.startsWith("Error"))
                        MaterialTheme.colorScheme.error
                    else if (notification.startsWith("Connection established"))
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}