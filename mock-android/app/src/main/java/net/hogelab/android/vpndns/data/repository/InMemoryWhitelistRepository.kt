package net.hogelab.android.vpndns.data.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.hogelab.android.vpndns.data.repository.util.DomainTrie
import net.hogelab.android.vpndns.domain.model.WhitelistEntity
import net.hogelab.android.vpndns.domain.repository.WhitelistRepository
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * メモリ上でホワイトリストを管理するリポジトリの実装
 */
class InMemoryWhitelistRepository : WhitelistRepository {

    private val whitelistMap = ConcurrentHashMap<String, WhitelistEntity>()
    private val wildcardTrie = DomainTrie()
    private val _rawWhitelist = MutableStateFlow<List<WhitelistEntity>>(emptyList())

    private val loadMutex = Mutex()
    private var isLoaded = false

    override val whitelist: StateFlow<List<WhitelistEntity>> = _rawWhitelist.asStateFlow()

    override fun addToWhitelist(hostName: String, description: String) {
        if (whitelistMap.containsKey(hostName)) return

        whitelistMap[hostName] = WhitelistEntity(
            hostName = hostName,
            editTime = System.currentTimeMillis(),
            description = description
        )
        
        if (hostName.contains("*")) {
            wildcardTrie.insert(hostName)
        }

        updateWhitelist()
    }

    override fun updateWhitelistEntry(oldHostName: String, newHostName: String, description: String) {
        if (oldHostName == newHostName) {
            val entry = whitelistMap[oldHostName] ?: return
            whitelistMap[oldHostName] = entry.copy(
                description = description,
                editTime = System.currentTimeMillis()
            )
        } else {
            whitelistMap.remove(oldHostName)
            if (oldHostName.contains("*")) {
                wildcardTrie.remove(oldHostName)
            }
            
            whitelistMap[newHostName] = WhitelistEntity(
                hostName = newHostName,
                editTime = System.currentTimeMillis(),
                description = description
            )
            if (newHostName.contains("*")) {
                wildcardTrie.insert(newHostName)
            }
        }
        updateWhitelist()
    }

    override fun removeFromWhitelist(hostName: String) {
        if (whitelistMap.remove(hostName) != null) {
            if (hostName.contains("*")) {
                wildcardTrie.remove(hostName)
            }
            updateWhitelist()
        }
    }

    override fun isWhitelisted(hostName: String): Boolean {
        return findMatchingEntry(hostName) != null
    }

    override fun findMatchingEntry(hostName: String): WhitelistEntity? {
        // 完全一致を優先
        val exactMatch = whitelistMap[hostName]
        if (exactMatch != null) return exactMatch
        
        // ワイルドカードマッチ
        val pattern = wildcardTrie.getMatchingPattern(hostName)
        return if (pattern != null) whitelistMap[pattern] else null
    }

    override suspend fun saveWhitelist(context: Context) = withContext(Dispatchers.IO) {
        try {
            val file = File(context.filesDir, "whitelist.txt")
            val content = _rawWhitelist.value.joinToString("\n") {
                "${it.hostName},${it.editTime},${it.description}"
            }
            file.writeText(content)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun loadWhitelist(context: Context) {
        if (isLoaded) return

        loadMutex.withLock {
            if (isLoaded) return

            withContext(Dispatchers.IO) {
                try {
                    val file = File(context.filesDir, "whitelist.txt")
                    if (file.exists()) {
                        val lines = file.readLines()
                        whitelistMap.clear()
                        wildcardTrie.clear()
                        lines.forEach { line ->
                            val parts = line.split(",")
                            if (parts.size >= 2) {
                                val hostName = parts[0]
                                val editTime = parts[1].toLongOrNull() ?: System.currentTimeMillis()
                                val description = if (parts.size >= 3) parts.drop(2).joinToString(",") else ""

                                val entity = WhitelistEntity(hostName, editTime, description)
                                whitelistMap[hostName] = entity
                                
                                if (hostName.contains("*")) {
                                    wildcardTrie.insert(hostName)
                                }
                            }
                        }
                        updateWhitelist()
                    }
                    isLoaded = true
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun updateWhitelist() {
        _rawWhitelist.value = whitelistMap.values.toList().sortedByDescending { it.editTime }
    }
}
