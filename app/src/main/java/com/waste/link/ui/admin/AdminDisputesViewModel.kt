package com.waste.link.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.waste.link.data.models.AppUser
import com.waste.link.data.models.Dispute
import com.waste.link.data.models.WasteRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class DisputeNote(
    val id: String = "",
    val text: String = "",
    val adminId: String = "",
    val timestampMs: Long = 0L
)

data class AdminDisputesUiState(
    val disputes: List<Dispute> = emptyList(),
    val isLoading: Boolean = true,
    
    // Drawer State
    val selectedDispute: Dispute? = null,
    val selectedRequest: WasteRequest? = null,
    val resident: AppUser? = null,
    val collector: AppUser? = null,
    val notes: List<DisputeNote> = emptyList(),
    val isDrawerLoading: Boolean = false
)

@HiltViewModel
class AdminDisputesViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminDisputesUiState())
    val uiState: StateFlow<AdminDisputesUiState> = _uiState.asStateFlow()

    init {
        fetchDisputes()
    }

    private fun fetchDisputes() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                // Fetch open disputes first
                val snapshot = firestore.collection("disputes")
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .get()
                    .await()
                
                val disputes = snapshot.toObjects(Dispute::class.java).sortedBy { it.status == "RESOLVED" }
                
                _uiState.value = _uiState.value.copy(
                    disputes = disputes,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun selectDispute(dispute: Dispute?) {
        _uiState.value = _uiState.value.copy(selectedDispute = dispute)
        if (dispute != null) {
            fetchDisputeDetails(dispute)
        } else {
            _uiState.value = _uiState.value.copy(
                selectedRequest = null, resident = null, collector = null, notes = emptyList()
            )
        }
    }

    private fun fetchDisputeDetails(dispute: Dispute) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDrawerLoading = true)
            try {
                // Fetch Request
                val requestSnap = firestore.collection("waste_requests").document(dispute.requestId).get().await()
                val request = requestSnap.toObject(WasteRequest::class.java)

                // Fetch Users
                val resSnap = firestore.collection("users").document(dispute.residentId).get().await()
                val resident = resSnap.toObject(AppUser::class.java)
                
                var collector: AppUser? = null
                if (dispute.collectorId.isNotEmpty()) {
                    val colSnap = firestore.collection("users").document(dispute.collectorId).get().await()
                    collector = colSnap.toObject(AppUser::class.java)
                }

                // Fetch Notes
                fetchNotes(dispute.id)

                _uiState.value = _uiState.value.copy(
                    selectedRequest = request,
                    resident = resident,
                    collector = collector,
                    isDrawerLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isDrawerLoading = false)
            }
        }
    }

    private suspend fun fetchNotes(disputeId: String) {
        val notesSnap = firestore.collection("disputes").document(disputeId).collection("notes")
            .orderBy("timestampMs", Query.Direction.ASCENDING)
            .get()
            .await()
        val notes = notesSnap.toObjects(DisputeNote::class.java)
        _uiState.value = _uiState.value.copy(notes = notes)
    }

    fun addNote(disputeId: String, text: String) {
        viewModelScope.launch {
            try {
                val adminId = auth.currentUser?.uid ?: "UNKNOWN_ADMIN"
                val note = hashMapOf(
                    "text" to text,
                    "adminId" to adminId,
                    "timestampMs" to System.currentTimeMillis()
                )
                firestore.collection("disputes").document(disputeId).collection("notes").add(note).await()
                fetchNotes(disputeId) // refresh notes
            } catch (e: Exception) {}
        }
    }

    fun resolveDispute(disputeId: String, outcome: String, notes: String) {
        viewModelScope.launch {
            try {
                // Add final note if provided
                if (notes.isNotBlank()) {
                    addNote(disputeId, "Resolution Note: \$notes")
                }

                firestore.collection("disputes").document(disputeId)
                    .update(
                        "status", "RESOLVED",
                        "resolutionOutcome", outcome
                    ).await()
                
                fetchDisputes()
                selectDispute(null)
            } catch (e: Exception) {}
        }
    }
}
