package net.hogelab.android.vpndns.ui.whitelist

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

    var inputHostName by mutableStateOf("")
        private set

    var inputDescription by mutableStateOf("")
        private set

    fun onHostNameChange(value: String) {
        inputHostName = value
    }

    fun onDescriptionChange(value: String) {
        inputDescription = value
    }

    fun onAddClick() {
        val host = inputHostName.trim()
        if (host.isEmpty()) return

        repository.addToWhitelist(host, inputDescription.trim())
        viewModelScope.launch {
            repository.saveWhitelist(getApplication())
        }
        inputHostName = ""
        inputDescription = ""
    }

    fun updateDescription(hostName: String, description: String) {
        repository.updateDescription(hostName, description)
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
