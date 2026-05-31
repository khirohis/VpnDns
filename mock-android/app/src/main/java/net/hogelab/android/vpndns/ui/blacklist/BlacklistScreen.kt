package net.hogelab.android.vpndns.ui.blacklist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BlacklistScreen(
    viewModel: BlacklistViewModel = viewModel()
) {
    val groupedEntries by viewModel.groupedEntries.collectAsState()
    val redundantEntries = viewModel.redundantEntries

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Blacklist Domains",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { viewModel.clearAll() }) {
                Icon(Icons.Default.Delete, contentDescription = "Clear All")
            }
        }

        // Add Domain Area
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = viewModel.inputHostName,
                onValueChange = { viewModel.onInputChange(it) },
                label = { Text("Add Domain (e.g. *.google.com)") },
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

        HorizontalDivider()

        if (groupedEntries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No domains blocked",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                groupedEntries.forEach { (baseDomain, entities) ->
                    stickyHeader {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = baseDomain,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    items(entities) { entry ->
                        ListItem(
                            headlineContent = { 
                                Text(
                                    text = entry.hostName,
                                    fontFamily = if (entry.hostName.contains("*")) FontFamily.Monospace else FontFamily.Default,
                                    color = if (entry.hostName.contains("*")) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            supportingContent = {
                                Text("Added at: ${dateFormat.format(Date(entry.firstTime))}")
                            },
                            trailingContent = {
                                IconButton(onClick = { viewModel.removeEntry(entry.hostName) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove")
                                }
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    // Redundancy Alert Dialog
    if (redundantEntries != null) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelAddWithCleanup() },
            title = { Text("Cleanup Suggested") },
            text = {
                Column {
                    Text("The pattern '${viewModel.inputHostName}' covers the following existing entries:")
                    Spacer(modifier = Modifier.height(8.dp))
                    redundantEntries.forEach { 
                        Text("- $it", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Would you like to remove these and add the new pattern?")
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.confirmAddWithCleanup() }) {
                    Text("Yes, Cleanup & Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onAddClick() /* Skip logic? No, let's just use regular add */
                    // Actually, let's make it simpler.
                    viewModel.cancelAddWithCleanup()
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}
