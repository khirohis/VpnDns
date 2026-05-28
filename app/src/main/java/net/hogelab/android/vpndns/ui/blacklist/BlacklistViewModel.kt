package net.hogelab.android.vpndns.ui.blacklist

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow
import net.hogelab.android.vpndns.data.repository.RepositoryProvider
import net.hogelab.android.vpndns.domain.model.BlacklistEntry
import net.hogelab.android.vpndns.domain.repository.BlacklistRepository

class BlacklistViewModel(
    private val repository: BlacklistRepository = RepositoryProvider.blacklistRepository
) : ViewModel() {
    val entries: StateFlow<List<BlacklistEntry>> = repository.entries

    fun removeEntry(hostName: String) {
        repository.remove(hostName)
    }

    fun clearAll() {
        repository.clear()
    }
}
