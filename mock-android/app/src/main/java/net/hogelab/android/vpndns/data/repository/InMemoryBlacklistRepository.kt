package net.hogelab.android.vpndns.data.repository

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.hogelab.android.vpndns.domain.model.BlacklistEntity
import net.hogelab.android.vpndns.domain.repository.BlacklistRepository
import net.hogelab.android.vpndns.data.repository.util.DomainTrie
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * メモリ上でブラックリストを管理するリポジトリの実装
 */
class InMemoryBlacklistRepository : BlacklistRepository {

    private val blockedMap = ConcurrentHashMap<String, BlacklistEntity>()
    private val wildcardTrie = DomainTrie()
    private val _rawBlacklist = MutableStateFlow<List<BlacklistEntity>>(emptyList())
    
    private val loadMutex = Mutex()
    private var isLoaded = false

    override val blacklist: StateFlow<List<BlacklistEntity>> = _rawBlacklist.asStateFlow()

    override fun addToBlacklist(hostName: String) {
        if (blockedMap.containsKey(hostName)) return
        
        blockedMap[hostName] = BlacklistEntity(
            hostName = hostName,
            firstTime = System.currentTimeMillis(),
            isPending = false
        )
        
        if (hostName.contains("*")) {
            wildcardTrie.insert(hostName, isPending = false)
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
        val exactMatch = blockedMap[hostName]
        if (exactMatch != null) {
            return !exactMatch.isPending
        }
        return wildcardTrie.matches(hostName)
    }

    override fun togglePending(hostName: String) {
        val entry = blockedMap[hostName] ?: return
        val updated = entry.copy(isPending = !entry.isPending)
        blockedMap[hostName] = updated
        
        if (hostName.contains("*")) {
            // Trie 木の状態も更新（一旦削除して再挿入）
            wildcardTrie.remove(hostName)
            wildcardTrie.insert(hostName, isPending = updated.isPending)
        }
        
        updateBlacklist()
    }

    override fun clearBlacklist() {
        blockedMap.clear()
        wildcardTrie.clear()
        updateBlacklist()
    }

    override suspend fun saveBlacklist(context: Context) = withContext(Dispatchers.IO) {
        try {
            val file = File(context.filesDir, "blacklist.txt")
            val content = _rawBlacklist.value.joinToString("\n") { 
                "${it.hostName},${it.firstTime},${it.isPending}" 
            }
            file.writeText(content)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun loadBlacklist(context: Context) {
        if (isLoaded) return
        
        loadMutex.withLock {
            if (isLoaded) return
            
            withContext(Dispatchers.IO) {
                try {
                    val file = File(context.filesDir, "blacklist.txt")
                    if (file.exists()) {
                        val lines = file.readLines()
                        blockedMap.clear()
                        wildcardTrie.clear()
                        lines.forEach { line ->
                            val parts = line.split(",")
                            if (parts.size >= 2) {
                                val hostName = parts[0]
                                val firstTime = parts[1].toLongOrNull() ?: System.currentTimeMillis()
                                val isPending = if (parts.size >= 3) parts[2].toBoolean() else false
                                
                                val entity = BlacklistEntity(hostName, firstTime, isPending)
                                blockedMap[hostName] = entity
                                
                                if (hostName.contains("*")) {
                                    wildcardTrie.insert(hostName, isPending = isPending)
                                }
                            }
                        }
                        updateBlacklist()
                    }
                    isLoaded = true
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun updateBlacklist() {
        _rawBlacklist.value = blockedMap.values.toList().sortedByDescending { it.firstTime }
    }
}
