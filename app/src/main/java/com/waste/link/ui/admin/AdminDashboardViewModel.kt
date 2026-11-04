package com.waste.link.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class AdminDashboardState(
    val activeClients: Int = 0,
    val pendingFleets: Int = 0,
    val activeDisputes: Int = 0,
    val systemHealth: String = "100%",
    val recentLogs: List<ActivityLogItem> = emptyList(),
    val isLoading: Boolean = true,
    val selectedDateRange: String = "LIFETIME", // TODAY, WEEK, MONTH, LIFETIME
    val hasAnomaly: Boolean = false
)

data class ActivityLogItem(
    val id: String,
    val title: String,
    val description: String,
    val timestampMs: Long,
    val iconType: String = "ADMIN"
)

@HiltViewModel
class AdminDashboardViewModel @Inject constructor(
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminDashboardState())
    val uiState: StateFlow<AdminDashboardState> = _uiState.asStateFlow()

    init {
        fetchDashboardData()
    }

    fun setDateRange(range: String) {
        _uiState.value = _uiState.value.copy(selectedDateRange = range)
        fetchDashboardData() // Re-fetch with new range if we were doing real date filtering.
    }

    fun fetchDashboardData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                // Fetch Active Clients
                val clientsSnapshot = firestore.collection("users")
                    .whereEqualTo("role", "resident")
                    .get()
                    .await()
                val activeClients = clientsSnapshot.size()

                // Fetch Pending Fleets/Companies
                val fleetsSnapshot = firestore.collection("users")
                    .whereEqualTo("role", "collector")
                    .whereEqualTo("status", "UNVERIFIED")
                    .get()
                    .await()
                val pendingFleets = fleetsSnapshot.size()

                // Fetch Active Disputes
                val disputesSnapshot = firestore.collection("service_disputes")
                    .whereIn("status", listOf("OPEN", "ACTIVE", "PENDING"))
                    .get()
                    .await()
                val activeDisputes = disputesSnapshot.size()

                // Anomaly Logic (Example: > 5 active disputes triggers an anomaly)
                val hasAnomaly = activeDisputes > 5

                // Fetch Recent Activity Logs
                val logsSnapshot = firestore.collection("admin_actions_log")
                    .orderBy("timestampMs", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(10)
                    .get()
                    .await()
                
                val recentLogs = logsSnapshot.documents.mapNotNull { doc ->
                    try {
                        ActivityLogItem(
                            id = doc.id,
                            title = doc.getString("title") ?: "Action",
                            description = doc.getString("description") ?: "",
                            timestampMs = doc.getLong("timestampMs") ?: System.currentTimeMillis(),
                            iconType = doc.getString("iconType") ?: "ADMIN"
                        )
                    } catch (e: Exception) {
                        null
                    }
                }

                _uiState.value = _uiState.value.copy(
                    activeClients = activeClients,
                    pendingFleets = pendingFleets,
                    activeDisputes = activeDisputes,
                    recentLogs = recentLogs,
                    isLoading = false,
                    hasAnomaly = hasAnomaly
                )
            } catch (e: Exception) {
                // Fallback on error
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
}
