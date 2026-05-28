package net.hogelab.android.vpndns.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.hogelab.android.vpndns.domain.model.DnsEntry
import net.hogelab.android.vpndns.domain.model.HistorySortConfig
import net.hogelab.android.vpndns.domain.model.SortField
import net.hogelab.android.vpndns.domain.model.SortOrder
import net.hogelab.android.vpndns.domain.repository.BlacklistRepository
import net.hogelab.android.vpndns.domain.repository.DnsHistoryRepository

/**
 * メモリ上で DNS 履歴を保持するリポジトリの実装
 */
class InMemoryDnsHistoryRepository(
    private val blacklistRepository: BlacklistRepository = RepositoryProvider.blacklistRepository
) : DnsHistoryRepository {

    private val entriesMap = mutableMapOf<String, DnsEntry>()

    private val _sortConfig = MutableStateFlow(HistorySortConfig())
    override val sortConfig: StateFlow<HistorySortConfig> = _sortConfig.asStateFlow()

    private val _history = MutableStateFlow<List<DnsEntry>>(emptyList())
    override val history: StateFlow<List<DnsEntry>> = _history.asStateFlow()

    override fun addHost(host: String) {
        val now = System.currentTimeMillis()
        val existingEntry = entriesMap[host]
        
        if (existingEntry != null) {
            entriesMap[host] = existingEntry.copy(
                lastSeen = now,
                requestCount = existingEntry.requestCount + 1,
                isBlocked = blacklistRepository.isBlocked(host)
            )
        } else {
            entriesMap[host] = DnsEntry(
                hostName = host,
                firstSeen = now,
                lastSeen = now,
                requestCount = 1,
                isBlocked = blacklistRepository.isBlocked(host)
            )
        }
        updateHistory()
    }

    override fun setSortConfig(config: HistorySortConfig) {
        _sortConfig.value = config
        updateHistory()
    }

    override fun clear() {
        entriesMap.clear()
        updateHistory()
    }

    private fun updateHistory() {
        val config = _sortConfig.value
        val sortedList = entriesMap.values.toList().sortedWith { a, b ->
            val result = when (config.field) {
                SortField.FIRST_SEEN -> a.firstSeen.compareTo(b.firstSeen)
                SortField.LAST_SEEN -> a.lastSeen.compareTo(b.lastSeen)
                SortField.REQUEST_COUNT -> a.requestCount.compareTo(b.requestCount)
                SortField.HOST_NAME -> a.hostName.compareTo(b.hostName, ignoreCase = true)
            }
            if (config.order == SortOrder.ASCENDING) result else -result
        }
        _history.value = sortedList
    }
}
