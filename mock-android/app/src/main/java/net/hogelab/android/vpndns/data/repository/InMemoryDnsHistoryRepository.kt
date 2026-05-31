package net.hogelab.android.vpndns.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import net.hogelab.android.vpndns.domain.model.DnsHistoryEntity
import net.hogelab.android.vpndns.domain.model.HistorySortConfig
import net.hogelab.android.vpndns.domain.model.SortField
import net.hogelab.android.vpndns.domain.model.SortOrder
import net.hogelab.android.vpndns.domain.repository.DnsHistoryRepository
import java.util.concurrent.ConcurrentHashMap

/**
 * メモリ上で DNS 履歴を管理するリポジトリの実装
 */
class InMemoryDnsHistoryRepository : DnsHistoryRepository {

    private val repositoryScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // 履歴の「素」のデータ
    private val historyMap = ConcurrentHashMap<String, DnsHistoryEntity>()
    private val _rawHistory = MutableStateFlow<Map<String, DnsHistoryEntity>>(emptyMap())

    private val _sortConfig = MutableStateFlow(HistorySortConfig())
    override val sortConfig: StateFlow<HistorySortConfig> = _sortConfig.asStateFlow()

    // DNS リクエストイベント
    private val _dnsRequestEvents = MutableSharedFlow<String>()
    override val dnsRequestEvents: SharedFlow<String> = _dnsRequestEvents.asSharedFlow()

    override suspend fun notifyDnsRequest(hostName: String) {
        _dnsRequestEvents.emit(hostName)
    }

    override val history: StateFlow<List<DnsHistoryEntity>> = combine(
        _rawHistory,
        sortConfig
    ) { rawMap, sort ->
        rawMap.values.asSequence()
            .sortedWith { a, b ->
                val result = when (sort.field) {
                    SortField.FIRST_SEEN -> a.firstTime.compareTo(b.firstTime)
                    SortField.LAST_SEEN -> a.accessTime.compareTo(b.accessTime)
                    SortField.REQUEST_COUNT -> a.requestCount.compareTo(b.requestCount)
                    SortField.HOST_NAME -> a.hostName.compareTo(b.hostName, ignoreCase = true)
                }
                if (sort.order == SortOrder.ASCENDING) result else -result
            }.toList()
    }.stateIn(repositoryScope, SharingStarted.WhileSubscribed(5000), emptyList())

    override suspend fun addHistory(host: String) = withContext(Dispatchers.Default) {
        val now = System.currentTimeMillis()
        val existing = historyMap[host]
        
        if (existing != null) {
            historyMap[host] = existing.copy(
                accessTime = now,
                requestCount = existing.requestCount + 1
            )
        } else {
            // ルール 4: History の上限は 500 件まで。超えた場合は accessTime が古い順に削除
            if (historyMap.size >= 500) {
                val oldestHost = historyMap.values
                    .minByOrNull { it.accessTime }
                    ?.hostName
                if (oldestHost != null) {
                    historyMap.remove(oldestHost)
                }
            }

            historyMap[host] = DnsHistoryEntity(
                hostName = host,
                firstTime = now,
                accessTime = now,
                requestCount = 1
            )
        }
        _rawHistory.value = historyMap.toMap()
    }

    override fun clearHistory() {
        historyMap.clear()
        _rawHistory.value = emptyMap()
    }

    override fun setSortConfig(config: HistorySortConfig) {
        _sortConfig.value = config
    }
}
