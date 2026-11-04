package com.waste.link.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.waste.link.data.models.AppUser
import com.waste.link.data.models.WasteRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject

data class AdminModerationUiState(
    val clients: List<AppUser> = emptyList(),
    val filteredClients: List<AppUser> = emptyList(),
    val searchQuery: String = "",
    val selectedClientIds: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    
    // Drawer State
    val selectedClient: AppUser? = null,
    val selectedClientRequests: List<WasteRequest> = emptyList(),
    val isDrawerLoading: Boolean = false
)

@HiltViewModel
class AdminModerationViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminModerationUiState())
    val uiState: StateFlow<AdminModerationUiState> = _uiState.asStateFlow()

    init {
        fetchClients()
    }

    fun fetchClients() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                // For a production app, we would paginate. For now, fetching all residents.
                val snapshot = firestore.collection("users")
                    .whereEqualTo("role", "resident")
                    .get()
                    .await()
                    
                val clients = snapshot.toObjects(AppUser::class.java).sortedBy { it.name }
                
                _uiState.value = _uiState.value.copy(
                    clients = clients,
                    filteredClients = filterClientsList(clients, _uiState.value.searchQuery),
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun updateSearchQuery(query: String) {
        val filtered = filterClientsList(_uiState.value.clients, query)
        _uiState.value = _uiState.value.copy(searchQuery = query, filteredClients = filtered)
    }

    private fun filterClientsList(clients: List<AppUser>, query: String): List<AppUser> {
        if (query.isBlank()) return clients
        val lower = query.lowercase()
        return clients.filter { 
            it.name.lowercase().contains(lower) || 
            it.phone.contains(lower) || 
            it.email.lowercase().contains(lower) 
        }
    }

    fun toggleClientSelection(clientId: String) {
        val current = _uiState.value.selectedClientIds.toMutableSet()
        if (current.contains(clientId)) {
            current.remove(clientId)
        } else {
            current.add(clientId)
        }
        _uiState.value = _uiState.value.copy(selectedClientIds = current)
    }

    fun selectAllClients() {
        val allIds = _uiState.value.filteredClients.map { it.uid }.toSet()
        _uiState.value = _uiState.value.copy(selectedClientIds = allIds)
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedClientIds = emptySet())
    }

    fun selectClientForDrawer(client: AppUser?) {
        _uiState.value = _uiState.value.copy(selectedClient = client)
        if (client != null) {
            fetchClientActivity(client.uid)
        } else {
            _uiState.value = _uiState.value.copy(selectedClientRequests = emptyList())
        }
    }

    private fun fetchClientActivity(clientId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDrawerLoading = true)
            try {
                val requestsSnapshot = firestore.collection("waste_requests")
                    .whereEqualTo("clientUid", clientId)
                    .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .get()
                    .await()
                    
                val requests = requestsSnapshot.toObjects(WasteRequest::class.java)
                _uiState.value = _uiState.value.copy(
                    selectedClientRequests = requests,
                    isDrawerLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isDrawerLoading = false)
            }
        }
    }

    fun banClient(clientId: String, reason: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val adminId = auth.currentUser?.uid ?: "UNKNOWN_ADMIN"
                
                // Update User Status
                firestore.collection("users").document(clientId)
                    .update(
                        "status", "BANNED",
                        "isActive", false,
                        "bannedUntil", null // Indefinite
                    ).await()
                    
                // Write Audit Log
                val logEntry = hashMapOf(
                    "actorId" to adminId,
                    "targetId" to clientId,
                    "action" to "BANNED_CLIENT",
                    "reason" to reason,
                    "timestampMs" to System.currentTimeMillis(),
                    "title" to "Client Banned",
                    "description" to "Banned for: \$reason",
                    "iconType" to "BANNED"
                )
                firestore.collection("admin_actions_log").add(logEntry).await()
                
                // Refresh list
                fetchClients()
                selectClientForDrawer(null) // Close drawer
                onSuccess()
            } catch (e: Exception) {
                // Handle error implicitly
            }
        }
    }

    fun bulkBanSelected(reason: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val adminId = auth.currentUser?.uid ?: "UNKNOWN_ADMIN"
                val idsToBan = _uiState.value.selectedClientIds.toList()
                if (idsToBan.isEmpty()) return@launch
                
                // Firestore batch allows up to 500 writes
                val batch = firestore.batch()
                
                idsToBan.forEach { clientId ->
                    val userRef = firestore.collection("users").document(clientId)
                    batch.update(userRef, "status", "BANNED")
                    batch.update(userRef, "isActive", false)
                    
                    val logRef = firestore.collection("admin_actions_log").document()
                    val logEntry = hashMapOf(
                        "actorId" to adminId,
                        "targetId" to clientId,
                        "action" to "BANNED_CLIENT_BULK",
                        "reason" to reason,
                        "timestampMs" to System.currentTimeMillis(),
                        "title" to "Client Banned (Bulk)",
                        "description" to "Banned in bulk for: \$reason",
                        "iconType" to "BANNED"
                    )
                    batch.set(logRef, logEntry)
                }
                
                batch.commit().await()
                
                clearSelection()
                fetchClients()
                onSuccess()
            } catch (e: Exception) {
                // Handle error implicitly
            }
        }
    }
}
