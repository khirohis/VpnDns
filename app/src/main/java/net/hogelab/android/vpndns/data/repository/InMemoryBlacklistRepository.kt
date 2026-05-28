package net.hogelab.android.vpndns.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.hogelab.android.vpndns.domain.model.BlacklistEntry
import net.hogelab.android.vpndns.domain.repository.BlacklistRepository
import java.util.concurrent.ConcurrentHashMap

/**
 * メモリ上でブラックリストを保持するリポジトリの実装
 * 検索パフォーマンスを最大化するため、内部で ConcurrentHashMap (HashSet 的な利用) を保持する
 */
class InMemoryBlacklistRepository : BlacklistRepository {

    // 高速検索用のマップ (Key: hostName)
    private val blockedMap = ConcurrentHashMap<String, BlacklistEntry>()

    private val _entries = MutableStateFlow<List<BlacklistEntry>>(emptyList())
    override val entries: StateFlow<List<BlacklistEntry>> = _entries.asStateFlow()

    override fun isBlocked(hostName: String): Boolean {
        return blockedMap.containsKey(hostName)
    }

    override fun add(hostName: String) {
        if (blockedMap.containsKey(hostName)) return
        
        val entry = BlacklistEntry(
            hostName = hostName,
            addedAt = System.currentTimeMillis()
        )
        blockedMap[hostName] = entry
        updateList()
    }

    override fun remove(hostName: String) {
        if (blockedMap.remove(hostName) != null) {
            updateList()
        }
    }

    override fun clear() {
        blockedMap.clear()
        updateList()
    }

    private fun updateList() {
        // UI 表示用にリストを更新（追加順の降順でソート）
        _entries.value = blockedMap.values.toList().sortedByDescending { it.addedAt }
    }
}
