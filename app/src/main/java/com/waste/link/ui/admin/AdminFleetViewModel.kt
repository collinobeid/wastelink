package com.waste.link.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.waste.link.data.models.AppUser
import com.waste.link.data.models.WasteRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class LiveLocation(
    val collectorId: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val heading: Float = 0f
)

data class AdminFleetUiState(
    val unverifiedCollectors: List<AppUser> = emptyList(),
    val activeCollectors: List<AppUser> = emptyList(),
    val pendingRequests: List<WasteRequest> = emptyList(),
    val liveLocations: List<LiveLocation> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class AdminFleetViewModel @Inject constructor(
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminFleetUiState())
    val uiState: StateFlow<AdminFleetUiState> = _uiState.asStateFlow()

    init {
        fetchData()
    }

    private fun fetchData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            // Listen for live locations
            firestore.collection("live_locations").addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val locations = snapshot.toObjects(LiveLocation::class.java)
                    _uiState.value = _uiState.value.copy(liveLocations = locations)
                }
            }

            try {
                // Fetch unverified collectors
                val unverifiedSnapshot = firestore.collection("users")
                    .whereEqualTo("role", "collector")
                    .whereEqualTo("isVerified", false)
                    .get()
                    .await()
                val unverified = unverifiedSnapshot.toObjects(AppUser::class.java)

                // Fetch active collectors
                val activeSnapshot = firestore.collection("users")
                    .whereEqualTo("role", "collector")
                    .whereEqualTo("isVerified", true)
                    .get()
                    .await()
                val active = activeSnapshot.toObjects(AppUser::class.java)

                // Fetch pending requests
                val pendingSnapshot = firestore.collection("waste_requests")
                    .whereEqualTo("status", "PENDING_DISPATCH")
                    .get()
                    .await()
                val pending = pendingSnapshot.toObjects(WasteRequest::class.java)

                _uiState.value = _uiState.value.copy(
                    unverifiedCollectors = unverified,
                    activeCollectors = active,
                    pendingRequests = pending,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun approveCollector(collectorId: String) {
        viewModelScope.launch {
            try {
                firestore.collection("users").document(collectorId)
                    .update("isVerified", true, "status", "ACTIVE")
                    .await()
                fetchData() // Refresh
            } catch (e: Exception) {
                // Error handling
            }
        }
    }

    fun rejectCollector(collectorId: String) {
        viewModelScope.launch {
            try {
                firestore.collection("users").document(collectorId)
                    .update("status", "REJECTED")
                    .await()
                fetchData() // Refresh
            } catch (e: Exception) {
                // Error handling
            }
        }
    }

    fun assignRequest(requestId: String, collectorId: String) {
        viewModelScope.launch {
            try {
                firestore.collection("waste_requests").document(requestId)
                    .update(
                        "assignedTruckId", collectorId,
                        "status", "ASSIGNED_TO_FLEET"
                    ).await()
                fetchData() // Refresh
            } catch (e: Exception) {
                // Error handling
            }
        }
    }
}
