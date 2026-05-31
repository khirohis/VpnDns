package net.hogelab.android.vpndns.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.hogelab.android.vpndns.data.repository.RepositoryProvider
import net.hogelab.android.vpndns.domain.model.BlockType
import net.hogelab.android.vpndns.domain.model.DnsHistoryEntity
import net.hogelab.android.vpndns.domain.model.HistorySortConfig
import net.hogelab.android.vpndns.domain.model.SortField
import net.hogelab.android.vpndns.domain.model.SortOrder
import net.hogelab.android.vpndns.domain.repository.BlacklistRepository
import net.hogelab.android.vpndns.domain.repository.DnsHistoryRepository

/**
 * UI 表示用の履歴アイテム
 */
data class DnsHistoryUiItem(
    val entity: DnsHistoryEntity,
    val blockType: BlockType
)

class HistoryViewModel(
    private val historyRepository: DnsHistoryRepository = RepositoryProvider.dnsRepository,
    private val blacklistRepository: BlacklistRepository = RepositoryProvider.blacklistRepository
) : ViewModel() {

    val sortConfig: StateFlow<HistorySortConfig> = historyRepository.sortConfig

    // 履歴データとブラックリストの状態を結合して UI 用のリストを作成する
    val history: StateFlow<List<DnsHistoryUiItem>> = combine(
        historyRepository.history,
        blacklistRepository.blacklist,
        sortConfig
    ) { rawHistory, blacklist, sort ->
        val blockedHosts = blacklist.map { it.hostName }.toSet()
        
        rawHistory.asSequence().map { entity ->
            // ブロック状態を都度判定
            val isExact = blockedHosts.contains(entity.hostName)
            // パターンマッチ判定（BlacklistRepository に委譲）
            // 注意: ここで全件に対して Trie 判定を行うため、BlacklistRepository.isBlocked が高速である必要がある
            val isPattern = if (!isExact) blacklistRepository.isBlocked(entity.hostName) else false
            
            val blockType = when {
                isExact -> BlockType.EXACT
                isPattern -> BlockType.PATTERN_MATCHED
                else -> BlockType.NONE
            }
            DnsHistoryUiItem(entity, blockType)
        }.filter { item ->
            sort.showBlocked || item.blockType == BlockType.NONE
        }.toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun clearHistory() {
        historyRepository.clearHistory()
    }

    fun onSortFieldSelected(field: SortField) {
        val current = sortConfig.value
        val newOrder = if (current.field == field) {
            if (current.order == SortOrder.ASCENDING) SortOrder.DESCENDING else SortOrder.ASCENDING
        } else {
            if (field == SortField.HOST_NAME) SortOrder.ASCENDING else SortOrder.DESCENDING
        }
        historyRepository.setSortConfig(current.copy(field = field, order = newOrder))
    }

    fun toggleShowBlocked() {
        val current = sortConfig.value
        historyRepository.setSortConfig(current.copy(showBlocked = !current.showBlocked))
    }

    fun toggleBlock(item: DnsHistoryUiItem) {
        viewModelScope.launch {
            if (item.blockType == BlockType.NONE) {
                blacklistRepository.addToBlacklist(item.entity.hostName)
            } else if (item.blockType == BlockType.EXACT) {
                blacklistRepository.removeFromBlacklist(item.entity.hostName)
            }
        }
    }
}
