package net.hogelab.android.vpndns.ui.blacklist

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.hogelab.android.vpndns.data.repository.RepositoryProvider
import net.hogelab.android.vpndns.domain.model.BlacklistEntity
import net.hogelab.android.vpndns.domain.repository.BlacklistRepository
import net.hogelab.android.vpndns.domain.util.DomainUtils

enum class BlacklistViewMode {
    GROUPED,        // ドメイン別（セクション区切り）
    CHRONOLOGICAL   // 登録順（最新順）
}

class BlacklistViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: BlacklistRepository = RepositoryProvider.blacklistRepository
) : AndroidViewModel(application) {
    val entries: StateFlow<List<BlacklistEntity>> = repository.blacklist

    private val _viewMode = MutableStateFlow(BlacklistViewMode.GROUPED)
    val viewMode: StateFlow<BlacklistViewMode> = _viewMode.asStateFlow()

    // セカンドレベルドメインごとにグルーピングされたリスト
    val groupedEntries: StateFlow<Map<String, List<BlacklistEntity>>> = repository.blacklist
        .map { list ->
            list.groupBy { DomainUtils.extractBaseDomain(it.hostName) }
                .mapValues { (_, entities) -> entities.sortedBy { it.hostName } }
                .toSortedMap()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // 登録順（最新順）のフラットリスト
    val chronologicalEntries: StateFlow<List<BlacklistEntity>> = repository.blacklist
        .map { list ->
            list.sortedByDescending { it.firstTime }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var inputHostName by mutableStateOf("")
        private set

    var redundantEntries by mutableStateOf<List<String>?>(null)
        private set

    fun onInputChange(value: String) {
        inputHostName = value
    }

    fun onViewModeChange(mode: BlacklistViewMode) {
        _viewMode.value = mode
    }

    fun onWildcardShortcutClick(baseDomain: String) {
        inputHostName = "*.$baseDomain"
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
        viewModelScope.launch {
            repository.saveBlacklist(getApplication()) // 永続化
        }
        inputHostName = ""
    }

    fun confirmAddWithCleanup() {
        val host = inputHostName.trim()
        viewModelScope.launch {
            redundantEntries?.forEach { 
                repository.removeFromBlacklist(it)
            }
            repository.addToBlacklist(host)
            repository.saveBlacklist(getApplication()) // 永続化
        }
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
        viewModelScope.launch {
            repository.saveBlacklist(getApplication()) // 永続化
        }
    }

    fun togglePending(hostName: String) {
        repository.togglePending(hostName)
        viewModelScope.launch {
            repository.saveBlacklist(getApplication()) // 永続化
        }
    }

    fun clearAll() {
        repository.clearBlacklist()
        viewModelScope.launch {
            repository.saveBlacklist(getApplication()) // 永続化
        }
    }
}
