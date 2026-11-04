package com.waste.link.ui.collector

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waste.link.data.models.WasteRequest
import com.waste.link.data.repository.AuthRepository
import com.waste.link.data.repository.WasteRequestRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CollectorUiState(
    val isOnline: Boolean = false,
    val isOnBreak: Boolean = false,
    val activeRequests: List<WasteRequest> = emptyList(),
    val currentMission: WasteRequest? = null,
    val todayEarnings: Double = 0.0,
    val currentResidentName: String = "Resident"
)

@HiltViewModel
class CollectorViewModel @Inject constructor(
    private val requestRepository: WasteRequestRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CollectorUiState())
    val uiState: StateFlow<CollectorUiState> = _uiState.asStateFlow()

    init {
        // Observe all active requests
        viewModelScope.launch {
            requestRepository.getAllActiveRequests().collect { requests ->
                _uiState.update { it.copy(activeRequests = requests) }
                
                // Automatically update current mission if its status changed
                val current = _uiState.value.currentMission
                if (current != null) {
                    val updated = requests.find { it.id == current.id }
                    if (updated != null) {
                        _uiState.update { it.copy(currentMission = updated) }
                    }
                }
            }
        }
        
        // Observe today's earnings
        viewModelScope.launch {
            val truckId = authRepository.getCurrentUserId() ?: return@launch
            requestRepository.getCollectorEarningsToday(truckId).collect { earnings ->
                _uiState.update { it.copy(todayEarnings = earnings) }
            }
        }
    }

    fun toggleOnlineStatus() {
        _uiState.update { it.copy(isOnline = !it.isOnline, isOnBreak = false) }
    }

    fun toggleBreakStatus() {
        if (_uiState.value.isOnline) {
            _uiState.update { it.copy(isOnBreak = !it.isOnBreak) }
        }
    }

    fun acceptMission(request: WasteRequest, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val truckId = authRepository.getCurrentUserId() ?: return@launch
            val result = requestRepository.updateRequestStatus(
                rid = request.id,
                status = "EN_ROUTE",
                adminUid = truckId, // Admin/Collector UID
                truckId = truckId
            )
            if (result.isSuccess) {
                _uiState.update { it.copy(currentMission = request) }
                // Fetch resident name
                val userResult = authRepository.getUserRole(request.clientUid)
                val residentName = userResult.getOrNull()?.name ?: "Resident"
                _uiState.update { it.copy(currentResidentName = residentName) }
                
                onSuccess()
            }
        }
    }
    
    fun updateMissionStatus(status: String, agreedPrice: Double? = null, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val current = _uiState.value.currentMission ?: return@launch
            val truckId = authRepository.getCurrentUserId() ?: return@launch
            val result = requestRepository.updateRequestStatus(
                rid = current.id,
                status = status,
                adminUid = truckId,
                truckId = truckId,
                agreedPrice = agreedPrice
            )
            if (result.isSuccess) {
                if (status == "COMPLETED" || status == "IN_TRANSIT_TO_DUMP") {
                    _uiState.update { it.copy(currentMission = null) }
                }
                onSuccess()
            }
        }
    }

    fun updateLocation(lat: Double, lng: Double) {
        viewModelScope.launch {
            if (!_uiState.value.isOnline) return@launch
            val collectorId = authRepository.getCurrentUserId() ?: return@launch
            
            requestRepository.updateCollectorLocation(collectorId, lat, lng)
        }
    }
}
