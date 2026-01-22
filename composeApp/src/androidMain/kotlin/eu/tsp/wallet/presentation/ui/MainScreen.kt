package eu.tsp.wallet.presentation.ui

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.tsp.wallet.presentation.ui.components.ConnectionsTab
import eu.tsp.wallet.presentation.ui.components.InvitationTab
import eu.tsp.wallet.presentation.ui.components.MessagesTab
import eu.tsp.wallet.presentation.viewmodel.WalletViewModel

/**
 * Main screen with tabs for Invitation, Connections, and Messages
 */
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun MainScreen(viewModel: WalletViewModel, deviceUserId: String) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Invitation", "Connections", "Messages")

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Tab Row
            TabRow(
                tabs = tabs,
                selectedTabIndex = selectedTabIndex,
                onTabSelected = { selectedTabIndex = it }
            )

            Spacer(modifier = Modifier.height(2.dp))

            // User ID Header
            Text(
                text = deviceUserId,
                style = MaterialTheme.typography.headlineMedium,
            )

            // Tab Content
            when (selectedTabIndex) {
                0 -> InvitationTab(viewModel)
                1 -> ConnectionsTab(viewModel)
                2 -> MessagesTab(viewModel)
            }
        }
    }
}

@Composable
private fun TabRow(
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp)
    ) {
        tabs.forEachIndexed { index, title ->
            TabButton(
                title = title,
                selected = selectedTabIndex == index,
                onClick = { onTabSelected(index) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TabButton(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = modifier
            .padding(horizontal = 4.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = backgroundColor,
        contentColor = contentColor
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .padding(vertical = 12.dp, horizontal = 16.dp)
        )
    }
}
