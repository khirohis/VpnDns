package net.hogelab.android.vpndns.ui.history

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow
import net.hogelab.android.vpndns.data.repository.RepositoryProvider
import net.hogelab.android.vpndns.domain.model.DnsEntry
import net.hogelab.android.vpndns.domain.model.HistorySortConfig
import net.hogelab.android.vpndns.domain.model.SortField
import net.hogelab.android.vpndns.domain.model.SortOrder
import net.hogelab.android.vpndns.domain.repository.DnsRepository

class HistoryViewModel(
    private val repository: DnsRepository = RepositoryProvider.dnsRepository
) : ViewModel() {
    val history: StateFlow<List<DnsEntry>> = repository.history
    val sortConfig: StateFlow<HistorySortConfig> = repository.sortConfig

    fun onSortFieldSelected(field: SortField) {
        val current = sortConfig.value
        val nextOrder = if (current.field == field) {
            if (current.order == SortOrder.ASCENDING) SortOrder.DESCENDING else SortOrder.ASCENDING
        } else {
            SortOrder.DESCENDING
        }
        repository.setSortConfig(HistorySortConfig(field, nextOrder))
    }

    fun toggleBlock(entry: DnsEntry) {
        if (entry.isBlocked) {
            repository.removeFromBlacklist(entry.hostName)
        } else {
            repository.addToBlacklist(entry.hostName)
        }
    }

    fun clearHistory() {
        repository.clearHistory()
    }
}
