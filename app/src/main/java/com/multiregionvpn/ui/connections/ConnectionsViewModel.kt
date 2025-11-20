package com.multiregionvpn.ui.connections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.multiregionvpn.data.database.ConnectionEvent
import com.multiregionvpn.data.repository.ConnectionsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class ConnectionsViewModel @Inject constructor(
    private val connectionsRepository: ConnectionsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConnectionsUiState())
    val uiState: StateFlow<ConnectionsUiState> = _uiState.asStateFlow()

    init {
        loadConnections()
    }

    private fun loadConnections() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            connectionsRepository.getRecentEvents(100).collect { events ->
                val displayEvents = events.map { it.toDisplayModel() }
                _uiState.value = ConnectionsUiState(
                    connections = displayEvents,
                    isLoading = false
                )
            }
        }
    }

    fun refresh() {
        loadConnections()
    }

    fun clearAll() {
        viewModelScope.launch {
            connectionsRepository.clearAll()
        }
    }
    
    private fun ConnectionEvent.toDisplayModel(): ConnectionEventDisplay {
        val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val formattedTime = dateFormat.format(Date(timestamp))
        
        return ConnectionEventDisplay(
            timestamp = formattedTime,
            appName = appName,
            destination = "$destinationIp:$destinationPort",
            tunnelAlias = tunnelAlias ?: "Direct Internet"
        )
    }
}

data class ConnectionsUiState(
    val connections: List<ConnectionEventDisplay> = emptyList(),
    val isLoading: Boolean = false
)

data class ConnectionEventDisplay(
    val timestamp: String,
    val appName: String,
    val destination: String,
    val tunnelAlias: String
)
