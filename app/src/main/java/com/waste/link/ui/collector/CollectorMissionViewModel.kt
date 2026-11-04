package com.waste.link.ui.collector

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.waste.link.data.models.WasteRequest
import com.waste.link.data.repository.WasteRequestRepository
import com.waste.link.util.ImageUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import kotlin.random.Random

data class CollectorMissionUiState(
    val activeRequests: List<WasteRequest> = emptyList(),
    val isLoading: Boolean = true,
    val isSimulatingGps: Boolean = false
)

@HiltViewModel
class CollectorMissionViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val repository: WasteRequestRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CollectorMissionUiState())
    val uiState: StateFlow<CollectorMissionUiState> = _uiState.asStateFlow()

    private var gpsSimulationJob: Job? = null
    private val manualOrder = mutableListOf<String>()

    init {
        fetchAssignedRequests()
    }

    private fun fetchAssignedRequests() {
        val collectorId = auth.currentUser?.uid ?: return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                firestore.collection("waste_requests")
                    .whereEqualTo("assignedTruckId", collectorId)
                    .whereNotEqualTo("status", "COMPLETED")
                    .addSnapshotListener { snapshot, _ ->
                        if (snapshot != null) {
                            val requests = snapshot.toObjects(WasteRequest::class.java)
                            // Maintain manual order if specified
                            val sorted = requests.sortedWith(compareByDescending<WasteRequest> { it.status }.thenBy { manualOrder.indexOf(it.id).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE })
                            
                            _uiState.value = _uiState.value.copy(
                                activeRequests = sorted,
                                isLoading = false
                            )
                            
                            // Check if any request is EN_ROUTE to start/stop simulator
                            val isEnRoute = requests.any { it.status == "EN_ROUTE" }
                            if (isEnRoute && gpsSimulationJob == null) {
                                startGpsSimulator(collectorId)
                            } else if (!isEnRoute && gpsSimulationJob != null) {
                                stopGpsSimulator()
                            }
                        }
                    }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun moveToTop(requestId: String) {
        manualOrder.remove(requestId)
        manualOrder.add(0, requestId)
        // Refresh sort
        val current = _uiState.value.activeRequests
        val sorted = current.sortedWith(compareByDescending<WasteRequest> { it.status }.thenBy { manualOrder.indexOf(it.id).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE })
        _uiState.value = _uiState.value.copy(activeRequests = sorted)
    }

    fun optimizeRoute() {
        val current = _uiState.value.activeRequests
        // Dummy optimization: sort by random/distance
        val sorted = current.sortedBy { it.lat + it.lng } // Mock distance sort
        _uiState.value = _uiState.value.copy(activeRequests = sorted)
    }

    fun updateRequestStatus(requestId: String, newStatus: String, pocPhotoBitmap: android.graphics.Bitmap? = null) {
        viewModelScope.launch {
            try {
                var pocUrl: String? = null
                if (pocPhotoBitmap != null) {
                    _uiState.value = _uiState.value.copy(isLoading = true)
                    val bytes = ImageUtil.compressBitmapToByteArray(pocPhotoBitmap)
                    val path = "poc/${requestId}_${System.currentTimeMillis()}.jpg"
                    val result = repository.uploadImageToStorage(bytes, path)
                    pocUrl = result.getOrNull()
                }

                val updates = mutableMapOf<String, Any>(
                    "status" to newStatus,
                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
                
                when (newStatus) {
                    "EN_ROUTE" -> updates["enRouteAt"] = Date()
                    "ARRIVED_AT_LOCATION" -> updates["arrivedAt"] = Date()
                    "COMPLETED" -> {
                        updates["completedAt"] = Date()
                        if (pocUrl != null) {
                            updates["pocPhotoUrl"] = pocUrl
                        }
                    }
                }
                
                firestore.collection("waste_requests").document(requestId)
                    .update(updates).await()
                    
                _uiState.value = _uiState.value.copy(isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    // --- GPS Simulation logic ---
    // In a real app, this would be a Foreground Service reading from LocationManager.
    private fun startGpsSimulator(collectorId: String) {
        _uiState.value = _uiState.value.copy(isSimulatingGps = true)
        gpsSimulationJob = viewModelScope.launch {
            var lat = -6.162 // Dar es Salaam approximate starting point
            var lng = 39.191
            
            while (true) {
                // Simulate moving slightly
                lat += (Random.nextDouble() - 0.2) * 0.001
                lng += (Random.nextDouble() - 0.2) * 0.001
                
                val locationMap = hashMapOf(
                    "collectorId" to collectorId,
                    "lat" to lat,
                    "lng" to lng,
                    "heading" to Random.nextFloat() * 360f,
                    "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
                
                try {
                    firestore.collection("live_locations").document(collectorId)
                        .set(locationMap).await()
                } catch (e: Exception) {}
                
                delay(3000) // update every 3 seconds for smooth testing
            }
        }
    }

    private fun stopGpsSimulator() {
        gpsSimulationJob?.cancel()
        gpsSimulationJob = null
        _uiState.value = _uiState.value.copy(isSimulatingGps = false)
    }
}
