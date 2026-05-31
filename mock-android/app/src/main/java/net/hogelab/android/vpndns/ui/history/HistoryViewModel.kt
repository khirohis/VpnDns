package net.hogelab.android.vpndns.ui.history

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow
import net.hogelab.android.vpndns.data.repository.RepositoryProvider
import net.hogelab.android.vpndns.domain.model.BlockType
import net.hogelab.android.vpndns.domain.model.DnsEntity
import net.hogelab.android.vpndns.domain.model.HistorySortConfig
import net.hogelab.android.vpndns.domain.model.SortField
import net.hogelab.android.vpndns.domain.model.SortOrder
import net.hogelab.android.vpndns.domain.repository.DnsRepository

class HistoryViewModel(
    private val repository: DnsRepository = RepositoryProvider.dnsRepository
) : ViewModel() {

    val history: StateFlow<List<DnsEntity>> = repository.history
    val sortConfig: StateFlow<HistorySortConfig> = repository.sortConfig

    fun clearHistory() {
        repository.clearHistory()
    }

    fun onSortFieldSelected(field: SortField) {
        val current = sortConfig.value
        val newOrder = if (current.field == field) {
            if (current.order == SortOrder.ASCENDING) SortOrder.DESCENDING else SortOrder.ASCENDING
        } else {
            // フィールドが変わった場合は、時間系なら降順、名前なら昇順をデフォルトにするなどの工夫も可能
            if (field == SortField.HOST_NAME) SortOrder.ASCENDING else SortOrder.DESCENDING
        }
        repository.setSortConfig(current.copy(field = field, order = newOrder))
    }

    fun toggleShowBlocked() {
        val current = sortConfig.value
        repository.setSortConfig(current.copy(showBlocked = !current.showBlocked))
    }

    fun toggleBlock(entity: DnsEntity) {
        if (entity.blockType == BlockType.NONE) {
            repository.addToBlacklist(entity.hostName)
        } else if (entity.blockType == BlockType.EXACT) {
            repository.removeFromBlacklist(entity.hostName)
        }
    }
}
