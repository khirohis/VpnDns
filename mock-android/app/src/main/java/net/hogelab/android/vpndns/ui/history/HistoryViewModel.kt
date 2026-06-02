package net.hogelab.android.vpndns.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
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
import net.hogelab.android.vpndns.domain.model.WhitelistEntity
import net.hogelab.android.vpndns.domain.repository.BlacklistRepository
import net.hogelab.android.vpndns.domain.repository.DnsHistoryRepository
import net.hogelab.android.vpndns.domain.repository.WhitelistRepository

/**
 * UI 表示用の履歴アイテム
 */
data class DnsHistoryUiItem(
    val entity: DnsHistoryEntity,
    val blockType: BlockType,
    val whitelistedEntry: WhitelistEntity? = null
)

class HistoryViewModel @JvmOverloads constructor(
    application: Application,
    private val historyRepository: DnsHistoryRepository = RepositoryProvider.dnsRepository,
    private val blacklistRepository: BlacklistRepository = RepositoryProvider.blacklistRepository,
    private val whitelistRepository: WhitelistRepository = RepositoryProvider.whitelistRepository
) : AndroidViewModel(application) {

    val sortConfig: StateFlow<HistorySortConfig> = historyRepository.sortConfig

    // 履歴データとブラックリスト・ホワイトリストの状態を結合して UI 用のリストを作成する
    val history: StateFlow<List<DnsHistoryUiItem>> = combine(
        historyRepository.history,
        blacklistRepository.blacklist,
        whitelistRepository.whitelist,
        sortConfig
    ) { rawHistory, blacklist, _, sort ->
        val blockedHostsMap = blacklist.associateBy { it.hostName }
        
        rawHistory.asSequence().map { entity ->
            val blacklistEntry = blockedHostsMap[entity.hostName]
            val whitelistEntry = whitelistRepository.findMatchingEntry(entity.hostName)
            
            val blockType = when {
                whitelistEntry != null -> {
                    BlockType.WHITELISTED
                }
                blacklistEntry != null -> {
                    if (blacklistEntry.isPending) BlockType.PENDING else BlockType.EXACT
                }
                blacklistRepository.isBlocked(entity.hostName) -> {
                    BlockType.PATTERN_MATCHED
                }
                else -> {
                    BlockType.NONE
                }
            }
            DnsHistoryUiItem(entity, blockType, whitelistEntry)
        }.filter { item ->
            val blockedVisible = sort.showBlocked || (item.blockType != BlockType.EXACT && item.blockType != BlockType.PATTERN_MATCHED)
            val whitelistedVisible = sort.showWhitelisted || item.blockType != BlockType.WHITELISTED
            blockedVisible && whitelistedVisible
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

    fun toggleShowWhitelisted() {
        val current = sortConfig.value
        historyRepository.setSortConfig(current.copy(showWhitelisted = !current.showWhitelisted))
    }

    fun toggleBlock(item: DnsHistoryUiItem) {
        if (item.blockType == BlockType.WHITELISTED) return // ホワイトリスト入りはブロック不可

        viewModelScope.launch {
            if (item.blockType == BlockType.NONE) {
                blacklistRepository.addToBlacklist(item.entity.hostName)
            } else if (item.blockType == BlockType.EXACT) {
                blacklistRepository.removeFromBlacklist(item.entity.hostName)
            }
        }
    }

    fun addToWhitelist(hostName: String, description: String) {
        viewModelScope.launch {
            whitelistRepository.addToWhitelist(hostName, description)
            whitelistRepository.saveWhitelist(getApplication())
        }
    }

    fun updateWhitelistEntry(oldHostName: String, newHostName: String, description: String) {
        viewModelScope.launch {
            whitelistRepository.updateWhitelistEntry(oldHostName, newHostName, description)
            whitelistRepository.saveWhitelist(getApplication())
        }
    }

    fun removeFromWhitelist(hostName: String) {
        viewModelScope.launch {
            whitelistRepository.removeFromWhitelist(hostName)
            whitelistRepository.saveWhitelist(getApplication())
        }
    }
}
