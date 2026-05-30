package net.hogelab.android.vpndns.ui.blacklist

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow
import net.hogelab.android.vpndns.data.repository.RepositoryProvider
import net.hogelab.android.vpndns.domain.model.BlacklistEntry
import net.hogelab.android.vpndns.domain.repository.DnsRepository

class BlacklistViewModel(
    private val repository: DnsRepository = RepositoryProvider.dnsRepository
) : ViewModel() {
    val entries: StateFlow<List<BlacklistEntry>> = repository.blacklist

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
        inputHostName = ""
    }

    fun confirmAddWithCleanup() {
        val host = inputHostName.trim()
        redundantEntries?.forEach { 
            repository.removeFromBlacklist(it)
        }
        repository.addToBlacklist(host)
        inputHostName = ""
        redundantEntries = null
    }

    fun cancelAddWithCleanup() {
        redundantEntries = null
    }

    private fun findRedundant(pattern: String): List<String> {
        val suffix = if (pattern.startsWith("*.")) pattern.substring(2) else pattern.replace("*", "")
        return entries.value.filter { entry ->
            val host = entry.hostName
            host != pattern && (host == suffix || host.endsWith(".$suffix"))
        }.map { it.hostName }
    }

    fun removeEntry(hostName: String) {
        repository.removeFromBlacklist(hostName)
    }

    fun clearAll() {
        repository.clearBlacklist()
    }
}
