package net.hogelab.android.vpndns.ui.history

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow
import net.hogelab.android.vpndns.data.repository.RepositoryProvider
import net.hogelab.android.vpndns.domain.model.DnsEntry
import net.hogelab.android.vpndns.domain.model.HistorySortConfig
import net.hogelab.android.vpndns.domain.model.SortField
import net.hogelab.android.vpndns.domain.model.SortOrder
import net.hogelab.android.vpndns.domain.repository.BlacklistRepository
import net.hogelab.android.vpndns.domain.repository.DnsHistoryRepository

class HistoryViewModel(
    private val repository: DnsHistoryRepository = RepositoryProvider.dnsHistoryRepository,
    private val blacklistRepository: BlacklistRepository = RepositoryProvider.blacklistRepository
) : ViewModel() {
    val history: StateFlow<List<DnsEntry>> = repository.history
    val sortConfig: StateFlow<HistorySortConfig> = repository.sortConfig

    fun onSortFieldSelected(field: SortField) {
        val current = sortConfig.value
        val nextOrder = if (current.field == field) {
            if (current.order == SortOrder.ASCENDING) SortOrder.DESCENDING else SortOrder.ASCENDING
        } else {
            SortOrder.DESCENDING // デフォルトは降順
        }
        repository.setSortConfig(HistorySortConfig(field, nextOrder))
    }

    fun toggleBlock(entry: DnsEntry) {
        if (entry.isBlocked) {
            blacklistRepository.remove(entry.hostName)
        } else {
            blacklistRepository.add(entry.hostName)
        }
        // リポジトリ側の状態も更新されるはずだが、即時反映を促すために addHost を呼ぶか
        // あるいは BlacklistRepository の変更を DnsHistoryRepository が監視する仕組みが必要
        repository.addHost(entry.hostName)
    }

    fun clearHistory() {
        repository.clear()
    }
}
