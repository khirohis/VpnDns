package net.hogelab.android.vpndns.data.repository

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import net.hogelab.android.vpndns.domain.model.BlacklistEntry
import net.hogelab.android.vpndns.domain.model.BlockType
import net.hogelab.android.vpndns.domain.model.DnsEntry
import net.hogelab.android.vpndns.domain.model.HistorySortConfig
import net.hogelab.android.vpndns.domain.model.SortField
import net.hogelab.android.vpndns.domain.model.SortOrder
import net.hogelab.android.vpndns.domain.repository.DnsRepository
import net.hogelab.android.vpndns.data.repository.util.DomainTrie
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * メモリ上で DNS 履歴とブラックリストを統合管理するリポジトリの実装
 */
class InMemoryDnsRepository : DnsRepository {

    private val repositoryScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // 履歴の「素」のデータ
    private val historyMap = ConcurrentHashMap<String, DnsEntry>()
    private val _rawHistory = MutableStateFlow<Map<String, DnsEntry>>(emptyMap())

    // ブラックリストのデータ
    private val blockedMap = ConcurrentHashMap<String, BlacklistEntry>()
    private val wildcardTrie = DomainTrie()
    private val _rawBlacklist = MutableStateFlow<List<BlacklistEntry>>(emptyList())

    private val _sortConfig = MutableStateFlow(HistorySortConfig())
    override val sortConfig: StateFlow<HistorySortConfig> = _sortConfig.asStateFlow()

    // ブラックリスト (StateFlow)
    override val blacklist: StateFlow<List<BlacklistEntry>> = _rawBlacklist.asStateFlow()

    // 履歴 (StateFlow): 履歴データ、ブラックリスト、ソート設定を結合して生成
    override val history: StateFlow<List<DnsEntry>> = combine(
        _rawHistory,
        blacklist,
        sortConfig
    ) { rawMap, blockedList, sort ->
        val blockedHosts = blockedList.map { it.hostName }.toSet()
        rawMap.values.asSequence().map { entry ->
            val isExact = blockedHosts.contains(entry.hostName)
            val isPattern = if (!isExact) wildcardTrie.matches(entry.hostName) else false
            
            entry.copy(
                blockType = when {
                    isExact -> BlockType.EXACT
                    isPattern -> BlockType.PATTERN_MATCHED
                    else -> BlockType.NONE
                }
            )
        }.filter { entry ->
            sort.showBlocked || entry.blockType == BlockType.NONE
        }.sortedWith { a, b ->
            val result = when (sort.field) {
                SortField.FIRST_SEEN -> a.firstSeen.compareTo(b.firstSeen)
                SortField.LAST_SEEN -> a.lastSeen.compareTo(b.lastSeen)
                SortField.REQUEST_COUNT -> a.requestCount.compareTo(b.requestCount)
                SortField.HOST_NAME -> a.hostName.compareTo(b.hostName, ignoreCase = true)
            }
            if (sort.order == SortOrder.ASCENDING) result else -result
        }.toList()
    }.stateIn(repositoryScope, SharingStarted.Eagerly, emptyList())

    override fun addHistory(host: String) {
        val now = System.currentTimeMillis()
        val existing = historyMap[host]
        
        if (existing != null) {
            historyMap[host] = existing.copy(
                lastSeen = now,
                requestCount = existing.requestCount + 1
            )
        } else {
            historyMap[host] = DnsEntry(
                hostName = host,
                firstSeen = now,
                lastSeen = now,
                requestCount = 1
            )
        }
        _rawHistory.value = historyMap.toMap()
    }

    override fun clearHistory() {
        historyMap.clear()
        _rawHistory.value = emptyMap()
    }

    override fun addToBlacklist(hostName: String) {
        if (blockedMap.containsKey(hostName)) return
        
        blockedMap[hostName] = BlacklistEntry(
            hostName = hostName,
            addedAt = System.currentTimeMillis()
        )
        
        if (hostName.contains("*")) {
            wildcardTrie.insert(hostName)
        }
        
        updateBlacklist()
    }

    override fun removeFromBlacklist(hostName: String) {
        if (blockedMap.remove(hostName) != null) {
            if (hostName.contains("*")) {
                wildcardTrie.remove(hostName)
            }
            updateBlacklist()
        }
    }

    override fun isBlocked(hostName: String): Boolean {
        // Stage 1: Exact Match
        if (blockedMap.containsKey(hostName)) return true
        
        // Stage 2: Suffix/Pattern Match via Trie
        return wildcardTrie.matches(hostName)
    }

    override fun clearBlacklist() {
        blockedMap.clear()
        wildcardTrie.clear()
        updateBlacklist()
    }

    override fun setSortConfig(config: HistorySortConfig) {
        _sortConfig.value = config
    }

    override fun saveBlacklist(context: Context) {
        try {
            val file = File(context.filesDir, "blacklist.txt")
            val content = _rawBlacklist.value.joinToString("\n") { 
                "${it.hostName},${it.addedAt}" 
            }
            file.writeText(content)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun loadBlacklist(context: Context) {
        try {
            val file = File(context.filesDir, "blacklist.txt")
            if (file.exists()) {
                val lines = file.readLines()
                blockedMap.clear()
                wildcardTrie.clear()
                lines.forEach { line ->
                    val parts = line.split(",")
                    if (parts.size == 2) {
                        val hostName = parts[0]
                        val addedAt = parts[1].toLongOrNull() ?: System.currentTimeMillis()
                        blockedMap[hostName] = BlacklistEntry(hostName, addedAt)
                        
                        if (hostName.contains("*")) {
                            wildcardTrie.insert(hostName)
                        }
                    }
                }
                updateBlacklist()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateBlacklist() {
        _rawBlacklist.value = blockedMap.values.toList().sortedByDescending { it.addedAt }
    }
}
