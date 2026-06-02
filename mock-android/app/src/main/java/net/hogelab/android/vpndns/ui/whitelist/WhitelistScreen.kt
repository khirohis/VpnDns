package net.hogelab.android.vpndns.ui.whitelist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import net.hogelab.android.vpndns.domain.model.WhitelistEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WhitelistScreen(
    viewModel: WhitelistViewModel = viewModel()
) {
    val whitelist by viewModel.whitelist.collectAsState()
    var selectedEntity by remember { mutableStateOf<WhitelistEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Whitelist Domains",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
        }

        // Add Domain Area
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = viewModel.inputHostName,
                    onValueChange = { viewModel.onHostNameChange(it) },
                    label = { Text("Domain Name") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                IconButton(
                    onClick = { viewModel.onAddClick() },
                    enabled = viewModel.inputHostName.isNotBlank(),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = viewModel.inputDescription,
                onValueChange = { viewModel.onDescriptionChange(it) },
                label = { Text("Description (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        HorizontalDivider()

        if (whitelist.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No domains in whitelist",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(whitelist, key = { it.hostName }) { entry ->
                    WhitelistEntryItem(
                        entry = entry,
                        dateFormat = dateFormat,
                        onItemClick = { selectedEntity = entry },
                        onDeleteClick = { viewModel.removeFromWhitelist(entry.hostName) }
                    )
                }
            }
        }
    }

    // Edit/View Popup
    selectedEntity?.let { entity ->
        WhitelistDetailDialog(
            entity = entity,
            onDismiss = { selectedEntity = null },
            onSave = { _, description ->
                viewModel.updateDescription(entity.hostName, description)
                selectedEntity = null
            },
            onDelete = {
                viewModel.removeFromWhitelist(entity.hostName)
                selectedEntity = null
            }
        )
    }
}

@Composable
fun WhitelistEntryItem(
    entry: WhitelistEntity,
    dateFormat: SimpleDateFormat,
    onItemClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable { onItemClick() },
        headlineContent = {
            Text(text = entry.hostName)
        },
        supportingContent = {
            Column {
                if (entry.description.isNotBlank()) {
                    Text(
                        text = entry.description,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                }
                Text(
                    text = "Last edit: ${dateFormat.format(Date(entry.editTime))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        },
        trailingContent = {
            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    )
    HorizontalDivider()
}

@Composable
fun WhitelistDetailDialog(
    entity: WhitelistEntity,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onDelete: () -> Unit
) {
    var description by remember { mutableStateOf(entity.description) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = entity.hostName) },
        text = {
            Column {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(entity.hostName, description) }) {
                Text("Save")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete, modifier = Modifier.padding(end = 8.dp)) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}
