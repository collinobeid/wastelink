package com.waste.link.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.waste.link.data.models.AppUser
import com.waste.link.data.models.OperationalUnit
import com.waste.link.data.models.ServiceDispute
import com.waste.link.data.models.AdminActionLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class HydratedServiceDispute(
    val dispute: ServiceDispute,
    val reporterName: String,
    val targetEntityName: String
)

@HiltViewModel
class AdminGodPanelViewModel @Inject constructor(
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _users = MutableStateFlow<List<AppUser>>(emptyList())
    val users: StateFlow<List<AppUser>> = _users.asStateFlow()

    private val _operationalUnits = MutableStateFlow<List<OperationalUnit>>(emptyList())
    val operationalUnits: StateFlow<List<OperationalUnit>> = _operationalUnits.asStateFlow()

    private val _disputes = MutableStateFlow<List<HydratedServiceDispute>>(emptyList())
    val disputes: StateFlow<List<HydratedServiceDispute>> = _disputes.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            try {
                // In a real app we'd use snapshot listeners or pagination, but for now fetch all
                val usersSnapshot = firestore.collection("users").get().await()
                val usersList = usersSnapshot.toObjects(AppUser::class.java)
                _users.value = usersList

                val unitsSnapshot = firestore.collection("operational_units").get().await()
                val unitsList = unitsSnapshot.toObjects(OperationalUnit::class.java)
                _operationalUnits.value = unitsList

                val disputesSnapshot = firestore.collection("service_disputes").get().await()
                val rawDisputes = disputesSnapshot.toObjects(ServiceDispute::class.java)
                
                // Hydration
                val hydrated = rawDisputes.map { dispute ->
                    val reporter = usersList.find { it.uid == dispute.reporterId }?.name ?: "Unknown Reporter"
                    val targetName = if (dispute.disputedEntityType == "OPERATIONAL_UNIT") {
                        unitsList.find { it.id == dispute.disputedEntityId }?.name ?: "Unknown Truck"
                    } else {
                        usersList.find { it.uid == dispute.disputedEntityId }?.name ?: "Unknown Entity"
                    }
                    
                    HydratedServiceDispute(dispute, reporter, targetName)
                }
                
                _disputes.value = hydrated
            } catch (e: Exception) {
                // Error handling
            }
        }
    }

    fun updateUserStatus(userId: String, newStatus: String, bannedUntil: Long? = null) {
        viewModelScope.launch {
            try {
                val updates = mutableMapOf<String, Any>("status" to newStatus)
                if (bannedUntil != null) {
                    updates["bannedUntil"] = bannedUntil
                } else if (newStatus == "ACTIVE") {
                    updates["bannedUntil"] = com.google.firebase.firestore.FieldValue.delete()
                }

                firestore.collection("users").document(userId).update(updates).await()
                
                // Log the action
                val log = AdminActionLog(
                    adminId = "current_admin", // Usually fetched from auth
                    targetUserId = userId,
                    action = newStatus,
                    reason = "God Mode Override"
                )
                firestore.collection("admin_actions_log").add(log).await()
                
                // Refresh data
                loadData()
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun updateFleetStatus(unitId: String, newStatus: String) {
        viewModelScope.launch {
            try {
                firestore.collection("operational_units").document(unitId).update("status", newStatus).await()
                loadData()
            } catch (e: Exception) {
                // Error handling
            }
        }
    }
}
