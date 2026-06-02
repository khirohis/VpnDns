package net.hogelab.android.vpndns.data.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.hogelab.android.vpndns.domain.model.WhitelistEntity
import net.hogelab.android.vpndns.domain.repository.WhitelistRepository
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * メモリ上でホワイトリストを管理するリポジトリの実装
 */
class InMemoryWhitelistRepository : WhitelistRepository {

    private val whitelistMap = ConcurrentHashMap<String, WhitelistEntity>()
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

        updateWhitelist()
    }

    override fun updateDescription(hostName: String, description: String) {
        val entry = whitelistMap[hostName] ?: return
        whitelistMap[hostName] = entry.copy(
            description = description,
            editTime = System.currentTimeMillis()
        )
        updateWhitelist()
    }

    override fun removeFromWhitelist(hostName: String) {
        if (whitelistMap.remove(hostName) != null) {
            updateWhitelist()
        }
    }

    override fun isWhitelisted(hostName: String): Boolean {
        return whitelistMap.containsKey(hostName)
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
                        lines.forEach { line ->
                            val parts = line.split(",")
                            if (parts.size >= 2) {
                                val hostName = parts[0]
                                val editTime = parts[1].toLongOrNull() ?: System.currentTimeMillis()
                                val description = if (parts.size >= 3) parts.drop(2).joinToString(",") else ""

                                val entity = WhitelistEntity(hostName, editTime, description)
                                whitelistMap[hostName] = entity
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
