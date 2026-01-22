package eu.tsp.wallet.aries.view

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tsp.wallet.oid4vci.OID4VCICredential
import kotlinx.datetime.toJavaInstant
import org.hyperledger.ariesframework.anoncreds.storage.CredentialRecord
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun CredentialsList(
    ariesCredentials: List<CredentialRecord>,
    oid4vciCredentials: List<OID4VCICredential>,
    onDeleteAriesCredential: (String) -> Unit,
    onDeleteOID4VCICredential: (String) -> Unit
) {
    val totalCount = ariesCredentials.size + oid4vciCredentials.size

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = "My Credentials",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (totalCount == 0) {
            Text(
                text = "No credentials stored yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // OID4VCI Credentials
                if (oid4vciCredentials.isNotEmpty()) {
                    item {
                        Text(
                            text = "OpenID4VCI Credentials",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }
                    items(
                        items = oid4vciCredentials,
                        key = { "oid4vci-${it.id}" }
                    ) { credential ->
                        OID4VCICredentialItem(
                            credential = credential,
                            onDelete = { onDeleteOID4VCICredential(credential.id) }
                        )
                    }
                }

                // Aries Credentials
                if (ariesCredentials.isNotEmpty()) {
                    item {
                        Text(
                            text = "Aries Credentials",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }
                    items(
                        items = ariesCredentials,
                        key = { "aries-${it.id}" }
                    ) { credential ->
                        AriesCredentialItem(
                            credential = credential,
                            onDelete = { onDeleteAriesCredential(credential.credentialId) }
                        )
                    }
                }
            }

            // Credential count at the bottom
            Text(
                text = "$totalCount credential(s)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

// Keep backwards compatibility with old signature
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun CredentialsList(
    credentials: List<CredentialRecord>,
    onDeleteCredential: (String) -> Unit
) {
    CredentialsList(
        ariesCredentials = credentials,
        oid4vciCredentials = emptyList(),
        onDeleteAriesCredential = onDeleteCredential,
        onDeleteOID4VCICredential = {}
    )
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun AriesCredentialItem(
    credential: CredentialRecord,
    onDelete: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
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
            // Header row with credential name and actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = credential.schemaName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "v${credential.schemaVersion}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row {
                    IconButton(onClick = { isExpanded = !isExpanded }) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (isExpanded) "Show less" else "Show more"
                        )
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete credential",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Issuer info
            Text(
                text = "Issuer: ${credential.issuerId}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Issue date
            val formatter = DateTimeFormatter
                .ofPattern("MMM dd, yyyy HH:mm")
                .withZone(ZoneId.systemDefault())
            Text(
                text = "Issued: ${formatter.format(credential.createdAt.toJavaInstant())}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Expanded content with credential attributes
            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Credential Details",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Show schema ID
                DetailRow(label = "Schema ID", value = credential.schemaId)

                // Show credential definition ID
                DetailRow(label = "Cred Def ID", value = credential.credentialDefinitionId)

                // Show credential ID
                DetailRow(label = "Credential ID", value = credential.credentialId)

                // Show revocation info if available
                credential.revocationRegistryId?.let { regId ->
                    DetailRow(label = "Revocation Registry", value = regId)
                }
                credential.credentialRevocationId?.let { revId ->
                    DetailRow(label = "Revocation ID", value = revId)
                }

                // Show credential attributes from tags
                val tags = credential.getTags()
                val attrTags = tags.filterKeys { it.startsWith("attr::") && it.endsWith("::value") }
                if (attrTags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Attributes",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    attrTags.forEach { (key, value) ->
                        val attrName = key.removePrefix("attr::").removeSuffix("::value")
                        DetailRow(label = attrName, value = value)
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Credential") },
            text = {
                Text("Are you sure you want to delete the credential '${credential.schemaName}'? This action cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(100.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun OID4VCICredentialItem(
    credential: OID4VCICredential,
    onDelete: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
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
            // Header row with credential name and actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = credential.credentialConfigurationId,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Format: ${credential.format}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row {
                    IconButton(onClick = { isExpanded = !isExpanded }) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (isExpanded) "Show less" else "Show more"
                        )
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete credential",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Issuer info
            Text(
                text = "Issuer: ${credential.issuer}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Issue date
            val formatter = DateTimeFormatter
                .ofPattern("MMM dd, yyyy HH:mm")
                .withZone(ZoneId.systemDefault())
            Text(
                text = "Issued: ${formatter.format(Instant.ofEpochMilli(credential.issuedAt))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Expanded content with credential details
            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Credential Details",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Show credential ID
                DetailRow(label = "Credential ID", value = credential.id)

                // Show format
                DetailRow(label = "Format", value = credential.format)

                // Show configuration ID
                DetailRow(label = "Config ID", value = credential.credentialConfigurationId)

                // Show raw credential (truncated)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Raw Credential",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = credential.rawCredential.take(500) + if (credential.rawCredential.length > 500) "..." else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Credential") },
            text = {
                Text("Are you sure you want to delete this OpenID4VCI credential? This action cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
