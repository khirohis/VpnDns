package net.hogelab.android.vpndns.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import net.hogelab.android.vpndns.domain.model.BlockType
import net.hogelab.android.vpndns.domain.model.HistorySortConfig
import net.hogelab.android.vpndns.domain.model.SortField
import net.hogelab.android.vpndns.domain.model.SortOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = viewModel()
) {
    val history by viewModel.history.collectAsState()
    val sortConfig by viewModel.sortConfig.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "DNS Query History",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.align(Alignment.CenterStart)
            )
            IconButton(
                onClick = { viewModel.clearHistory() },
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Clear History")
            }
        }

        SortHeader(
            config = sortConfig,
            onFieldClick = { viewModel.onSortFieldSelected(it) },
            onToggleFilter = { viewModel.toggleShowBlocked() }
        )

        HorizontalDivider()

        if (history.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No history available",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            val dateFormat = SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault())
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(history) { entry ->
                    val color = when (entry.blockType) {
                        BlockType.EXACT -> MaterialTheme.colorScheme.error
                        BlockType.PATTERN_MATCHED -> MaterialTheme.colorScheme.tertiary
                        BlockType.PENDING -> MaterialTheme.colorScheme.outline
                        BlockType.NONE -> MaterialTheme.colorScheme.onSurface
                    }

                    ListItem(
                        headlineContent = { 
                            Text(
                                text = entry.entity.hostName,
                                color = color,
                                fontWeight = if (entry.blockType != BlockType.NONE) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        supportingContent = {
                            Column {
                                Text(
                                    "First: ${dateFormat.format(Date(entry.entity.firstTime))}\n" +
                                    "Last: ${dateFormat.format(Date(entry.entity.accessTime))}"
                                )
                                if (entry.blockType == BlockType.PATTERN_MATCHED) {
                                    Text(
                                        text = "Blocked by pattern",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = color
                                    )
                                } else if (entry.blockType == BlockType.PENDING) {
                                    Text(
                                        text = "Blocking paused",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = color
                                    )
                                }
                            }
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 8.dp)) {
                                    Text(
                                        text = entry.entity.requestCount.toString(),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "requests",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.toggleBlock(entry) },
                                    enabled = entry.blockType != BlockType.PATTERN_MATCHED
                                ) {
                                    Icon(
                                        imageVector = if (entry.blockType == BlockType.PATTERN_MATCHED) Icons.Default.FilterAlt else Icons.Default.Block,
                                        contentDescription = when (entry.blockType) {
                                            BlockType.EXACT -> "Unblock"
                                            BlockType.PATTERN_MATCHED -> "Pattern Blocked"
                                            else -> "Block"
                                        },
                                        tint = if (entry.blockType != BlockType.NONE) color else MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun SortHeader(
    config: HistorySortConfig,
    onFieldClick: (SortField) -> Unit,
    onToggleFilter: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (config.showBlocked) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp),
                tint = if (config.showBlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
            Text(
                text = if (config.showBlocked) "Showing Blocked" else "Hidden Blocked",
                style = MaterialTheme.typography.labelMedium,
                color = if (config.showBlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.weight(1f))
            androidx.compose.material3.Switch(
                checked = config.showBlocked,
                onCheckedChange = { onToggleFilter() },
                modifier = Modifier.scale(0.7f)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Sort, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text(text = "Sort:", style = MaterialTheme.typography.labelMedium)
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                SortChip(field = SortField.HOST_NAME, label = "Name", config = config, onClick = onFieldClick)
                SortChip(field = SortField.FIRST_SEEN, label = "First", config = config, onClick = onFieldClick)
                SortChip(field = SortField.LAST_SEEN, label = "Last", config = config, onClick = onFieldClick)
                SortChip(field = SortField.REQUEST_COUNT, label = "Count", config = config, onClick = onFieldClick)
            }
        }
    }
}

@Composable
fun SortChip(
    field: SortField,
    label: String,
    config: HistorySortConfig,
    onClick: (SortField) -> Unit
) {
    val isSelected = config.field == field
    Row(
        modifier = Modifier
            .clickable { onClick(field) }
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
        if (isSelected) {
            Icon(
                imageVector = if (config.order == SortOrder.ASCENDING) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                contentDescription = null,
                modifier = Modifier.padding(start = 2.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
