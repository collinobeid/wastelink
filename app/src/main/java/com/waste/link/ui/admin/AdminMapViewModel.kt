package com.waste.link.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waste.link.data.models.WasteRequest
import com.waste.link.data.repository.WasteRequestRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject

@HiltViewModel
class AdminMapViewModel @Inject constructor(
    private val repository: WasteRequestRepository
) : ViewModel() {

    val requests: StateFlow<List<WasteRequest>> = repository.getAllActiveRequests()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _liveLocations = MutableStateFlow<List<com.waste.link.data.models.LiveLocation>>(emptyList())
    val liveLocations: StateFlow<List<com.waste.link.data.models.LiveLocation>> = _liveLocations.asStateFlow()

    init {
        watchLiveLocations()
    }

    private fun watchLiveLocations() {
        FirebaseFirestore.getInstance().collection("live_locations")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && !snapshot.isEmpty) {
                    val locations = snapshot.documents.mapNotNull {
                        it.toObject(com.waste.link.data.models.LiveLocation::class.java)
                    }
                    _liveLocations.value = locations
                }
            }
    }

    fun updateRequestStatus(requestId: String, newStatus: String) {
        viewModelScope.launch {
            // For now, no specific adminUid or truckId assigned during this transition
            repository.updateRequestStatus(
                rid = requestId,
                status = newStatus,
                adminUid = null,
                truckId = null
            )
        }
    }
}
