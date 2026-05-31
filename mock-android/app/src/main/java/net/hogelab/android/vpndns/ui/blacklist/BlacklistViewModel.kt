package net.hogelab.android.vpndns.ui.blacklist

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow
import net.hogelab.android.vpndns.data.repository.RepositoryProvider
import net.hogelab.android.vpndns.domain.model.BlacklistEntity
import net.hogelab.android.vpndns.domain.repository.BlacklistRepository

class BlacklistViewModel(
    application: Application,
    private val repository: BlacklistRepository = RepositoryProvider.blacklistRepository
) : AndroidViewModel(application) {
    val entries: StateFlow<List<BlacklistEntity>> = repository.blacklist

    var inputHostName by mutableStateOf("")
        private set

    var redundantEntries by mutableStateOf<List<String>?>(null)
        private set

    fun onInputChange(value: String) {
        inputHostName = value
    }

    fun onAddClick() {
        val host = inputHostName.trim()
        if (host.isEmpty()) return

        if (host.contains("*")) {
            val redundant = findRedundant(host)
            if (redundant.isNotEmpty()) {
                redundantEntries = redundant
                return
            }
        }
        
        repository.addToBlacklist(host)
        repository.saveBlacklist(getApplication()) // 永続化
        inputHostName = ""
    }

    fun confirmAddWithCleanup() {
        val host = inputHostName.trim()
        redundantEntries?.forEach { 
            repository.removeFromBlacklist(it)
        }
        repository.addToBlacklist(host)
        repository.saveBlacklist(getApplication()) // 永続化
        inputHostName = ""
        redundantEntries = null
    }

    fun cancelAddWithCleanup() {
        redundantEntries = null
    }

    private fun findRedundant(pattern: String): List<String> {
        val suffix = if (pattern.startsWith("*.")) pattern.substring(2) else pattern.replace("*", "")
        return entries.value.filter { entity ->
            val host = entity.hostName
            host != pattern && (host == suffix || host.endsWith(".$suffix"))
        }.map { it.hostName }
    }

    fun removeEntry(hostName: String) {
        repository.removeFromBlacklist(hostName)
        repository.saveBlacklist(getApplication()) // 永続化
    }

    fun clearAll() {
        repository.clearBlacklist()
        repository.saveBlacklist(getApplication()) // 永続化
    }
}
