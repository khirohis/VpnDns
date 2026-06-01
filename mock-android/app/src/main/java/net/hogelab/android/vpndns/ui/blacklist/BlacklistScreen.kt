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
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import net.hogelab.android.vpndns.domain.model.BlacklistEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BlacklistScreen(
    viewModel: BlacklistViewModel = viewModel()
) {
    val viewMode by viewModel.viewMode.collectAsState()
    val groupedEntries by viewModel.groupedEntries.collectAsState()
    val chronologicalEntries by viewModel.chronologicalEntries.collectAsState()
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

        // 表示モード切り替え
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            SegmentedButton(
                selected = viewMode == BlacklistViewMode.GROUPED,
                onClick = { viewModel.onViewModeChange(BlacklistViewMode.GROUPED) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) {
                Text("Domain Group")
            }
            SegmentedButton(
                selected = viewMode == BlacklistViewMode.CHRONOLOGICAL,
                onClick = { viewModel.onViewModeChange(BlacklistViewMode.CHRONOLOGICAL) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) {
                Text("Latest First")
            }
        }

        HorizontalDivider()

        if (chronologicalEntries.isEmpty()) {
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
                if (viewMode == BlacklistViewMode.GROUPED) {
                    groupedEntries.forEach { (baseDomain, entities) ->
                        stickyHeader(key = "header_$baseDomain") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = baseDomain,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { viewModel.onWildcardShortcutClick(baseDomain) },
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoFixHigh,
                                        contentDescription = "Wildcard shortcut",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.height(20.dp)
                                    )
                                }
                            }
                        }

                        items(entities, key = { "item_${it.hostName}" }) { entry ->
                            BlacklistEntryItem(entry, viewModel, dateFormat)
                        }
                    }
                } else {
                    items(chronologicalEntries, key = { "item_${it.hostName}" }) { entry ->
                        BlacklistEntryItem(entry, viewModel, dateFormat)
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

@Composable
fun BlacklistEntryItem(
    entry: BlacklistEntity,
    viewModel: BlacklistViewModel,
    dateFormat: SimpleDateFormat
) {
    val isPending = entry.isPending
    val textColor = if (isPending) {
        MaterialTheme.colorScheme.outline
    } else if (entry.hostName.contains("*")) {
        MaterialTheme.colorScheme.secondary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    ListItem(
        headlineContent = {
            Text(
                text = entry.hostName,
                fontFamily = if (entry.hostName.contains("*")) FontFamily.Monospace else FontFamily.Default,
                color = textColor,
                style = if (isPending) MaterialTheme.typography.bodyMedium.copy(
                    textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough
                ) else MaterialTheme.typography.bodyLarge
            )
        },
        supportingContent = {
            Text(
                text = "Added at: ${dateFormat.format(Date(entry.firstTime))}${if (isPending) " (Pending)" else ""}",
                color = MaterialTheme.colorScheme.outline,
                style = MaterialTheme.typography.labelSmall
            )
        },
        trailingContent = {
            Row {
                IconButton(onClick = { viewModel.togglePending(entry.hostName) }) {
                    Icon(
                        imageVector = if (isPending) Icons.Default.PlayCircle else Icons.Default.PauseCircle,
                        contentDescription = if (isPending) "Resume" else "Pause",
                        tint = if (isPending) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                }
                IconButton(onClick = { viewModel.removeEntry(entry.hostName) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove")
                }
            }
        }
    )
    HorizontalDivider()
}
