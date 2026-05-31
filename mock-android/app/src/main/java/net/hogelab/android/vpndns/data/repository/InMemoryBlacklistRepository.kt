package net.hogelab.android.vpndns.data.repository

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    override val blacklist: StateFlow<List<BlacklistEntity>> = _rawBlacklist.asStateFlow()

    override fun addToBlacklist(hostName: String) {
        if (blockedMap.containsKey(hostName)) return
        
        blockedMap[hostName] = BlacklistEntity(
            hostName = hostName,
            firstTime = System.currentTimeMillis()
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
        if (blockedMap.containsKey(hostName)) return true
        return wildcardTrie.matches(hostName)
    }

    override fun clearBlacklist() {
        blockedMap.clear()
        wildcardTrie.clear()
        updateBlacklist()
    }

    override fun saveBlacklist(context: Context) {
        try {
            val file = File(context.filesDir, "blacklist.txt")
            val content = _rawBlacklist.value.joinToString("\n") { 
                "${it.hostName},${it.firstTime}" 
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
                        val firstTime = parts[1].toLongOrNull() ?: System.currentTimeMillis()
                        blockedMap[hostName] = BlacklistEntity(hostName, firstTime)
                        
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
        _rawBlacklist.value = blockedMap.values.toList().sortedByDescending { it.firstTime }
    }
}
