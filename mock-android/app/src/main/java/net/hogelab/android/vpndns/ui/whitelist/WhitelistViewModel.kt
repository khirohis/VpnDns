package net.hogelab.android.vpndns.ui.whitelist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import net.hogelab.android.vpndns.data.repository.RepositoryProvider
import net.hogelab.android.vpndns.domain.model.WhitelistEntity
import net.hogelab.android.vpndns.domain.repository.WhitelistRepository

class WhitelistViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: WhitelistRepository = RepositoryProvider.whitelistRepository
) : AndroidViewModel(application) {

    val whitelist: StateFlow<List<WhitelistEntity>> = repository.whitelist

    fun updateWhitelistEntry(oldHostName: String, newHostName: String, description: String) {
        repository.updateWhitelistEntry(oldHostName, newHostName, description)
        viewModelScope.launch {
            repository.saveWhitelist(getApplication())
        }
    }

    fun removeFromWhitelist(hostName: String) {
        repository.removeFromWhitelist(hostName)
        viewModelScope.launch {
            repository.saveWhitelist(getApplication())
        }
    }
}
