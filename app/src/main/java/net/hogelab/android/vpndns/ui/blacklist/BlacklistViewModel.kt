package net.hogelab.android.vpndns.ui.blacklist

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow
import net.hogelab.android.vpndns.data.repository.RepositoryProvider
import net.hogelab.android.vpndns.domain.model.BlacklistEntry
import net.hogelab.android.vpndns.domain.repository.DnsRepository

class BlacklistViewModel(
    private val repository: DnsRepository = RepositoryProvider.dnsRepository
) : ViewModel() {
    val entries: StateFlow<List<BlacklistEntry>> = repository.blacklist

    fun removeEntry(hostName: String) {
        repository.removeFromBlacklist(hostName)
    }

    fun clearAll() {
        repository.clearBlacklist()
    }
}
