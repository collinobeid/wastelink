package com.waste.link.ui.history

import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.waste.link.data.models.WasteRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HistoryViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _requests = MutableStateFlow<List<WasteRequest>>(emptyList())
    val requests: StateFlow<List<WasteRequest>> = _requests.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadRequests()
    }

    private fun loadRequests() {
        val userId = auth.currentUser?.uid ?: return
        
        db.collection("wasteRequests")
            .whereEqualTo("clientUid", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _isLoading.value = false
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val requestsList = snapshot.toObjects(WasteRequest::class.java)
                    _requests.value = requestsList
                }
                _isLoading.value = false
            }
    }
}
