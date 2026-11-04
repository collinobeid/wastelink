package com.waste.link.ui.profile

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
import javax.inject.Inject
import com.waste.link.data.repository.AuthRepository

data class ProfileUiState(
    val isLoading: Boolean = true,
    val userProfile: AppUser? = null,
    
    // For Residents
    val wasteRequests: List<WasteRequest> = emptyList(),
    
    // For Collectors
    val totalPickups: Int = 0,
    val activeRoutes: Int = 0
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfileData()
    }

    fun loadProfileData() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            _uiState.value = _uiState.value.copy(isLoading = false)
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                // Fetch User Profile
                val userDoc = firestore.collection("users").document(currentUser.uid).get().await()
                val profile = userDoc.toObject(AppUser::class.java) ?: AppUser(
                    uid = currentUser.uid,
                    name = currentUser.displayName ?: "Unknown",
                    email = currentUser.email ?: "",
                    phone = currentUser.phoneNumber ?: "",
                    role = "resident", // Fallback
                    status = "UNVERIFIED"
                )

                // The profile role might be overridden to "admin" if the Auth repo did it, but let's trust the DB or fallback.
                // Actually, if Auth repo updates the DB, it should be in the DB.
                
                var requests = emptyList<WasteRequest>()
                var totalPickups = 0
                var activeRoutes = 0

                if (profile.role == "resident") {
                    // Fetch live Waste Requests
                    val requestsSnapshot = firestore.collection("waste_requests")
                        .whereEqualTo("clientUid", currentUser.uid)
                        .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                        .get()
                        .await()
                    
                    requests = requestsSnapshot.toObjects(WasteRequest::class.java)
                } else if (profile.role == "collector") {
                    // Fetch Collector Metrics
                    val pickupsSnapshot = firestore.collection("waste_requests")
                        .whereEqualTo("assignedTruckId", currentUser.uid)
                        .whereEqualTo("status", "COMPLETED")
                        .get()
                        .await()
                    totalPickups = pickupsSnapshot.size()

                    val activeSnapshot = firestore.collection("waste_requests")
                        .whereEqualTo("assignedTruckId", currentUser.uid)
                        .whereIn("status", listOf("EN_ROUTE", "ARRIVED_AT_LOCATION", "COLLECTION_VERIFIED", "IN_TRANSIT_TO_DUMP"))
                        .get()
                        .await()
                    activeRoutes = activeSnapshot.size()
                }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    userProfile = profile,
                    wasteRequests = requests,
                    totalPickups = totalPickups,
                    activeRoutes = activeRoutes
                )

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun updateProfile(name: String, phone: String) {
        val currentUser = auth.currentUser ?: return
        viewModelScope.launch {
            try {
                firestore.collection("users").document(currentUser.uid)
                    .update(
                        mapOf(
                            "name" to name,
                            "phone" to phone
                        )
                    ).await()
                loadProfileData() // Reload to reflect changes
            } catch (e: Exception) {
                // handle error
            }
        }
    }

    fun deleteAccount(onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            authRepository.deleteAccount().fold(
                onSuccess = { onResult(true, null) },
                onFailure = { e -> onResult(false, e.message) }
            )
        }
    }
}
