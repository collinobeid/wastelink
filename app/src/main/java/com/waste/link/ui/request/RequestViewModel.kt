package com.waste.link.ui.request

import android.content.Context
import android.location.Geocoder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.google.firebase.firestore.GeoPoint
import com.waste.link.data.models.WasteRequest
import com.waste.link.data.repository.AuthRepository
import com.waste.link.data.repository.WasteRequestRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.waste.link.util.ImageUtil
import com.waste.link.util.LocationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext

@HiltViewModel
class RequestViewModel @Inject constructor(
    private val wasteRequestRepository: WasteRequestRepository,
    private val authRepository: AuthRepository,
    private val locationHelper: LocationHelper,
    private val firestore: FirebaseFirestore,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val prefs = context.getSharedPreferences("waste_link_drafts", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(RequestUiState())
    val uiState: StateFlow<RequestUiState> = _uiState.asStateFlow()

    fun selectCategory(category: WasteCategory) {
        _uiState.update { it.copy(selectedCategory = category) }
        saveDraft()
    }

    fun selectVolume(volume: VolumeTier) {
        _uiState.update { it.copy(selectedVolume = volume) }
        saveDraft()
    }

    fun updateNotes(notes: String) {
        _uiState.update { it.copy(notes = notes) }
        saveDraft()
    }
    
    fun updateLocation(latLng: LocationCoords, context: Context) {
        _uiState.update { it.copy(location = latLng, address = "Detecting location...") }
        fetchAddressFromLatLng(latLng, context)
    }

    fun fetchCurrentLocation(context: Context) {
        viewModelScope.launch {
            val loc = locationHelper.getCurrentLocation(context)
            if (loc != null) {
                val latLng = LocationCoords(loc.latitude, loc.longitude)
                updateLocation(latLng, context)
            } else {
                _uiState.update { it.copy(address = "Location unavailable. Please select on map.") }
            }
        }
    }

    private fun fetchAddressFromLatLng(latLng: LocationCoords, context: Context) {
        viewModelScope.launch {
            val address = withContext(Dispatchers.IO) {
                try {
                    // Try Google Maps Geocoding API first
                    val apiKey = "AIzaSyBfHpvoWipBfhN6on4NBgDZUM1vOCab1hI"
                    val urlString = "https://maps.googleapis.com/maps/api/geocode/json?latlng=${latLng.lat},${latLng.lng}&key=$apiKey"
                    val url = URL(urlString)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000

                    if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                        val reader = BufferedReader(InputStreamReader(connection.inputStream))
                        val response = reader.readText()
                        reader.close()
                        
                        val jsonObject = JSONObject(response)
                        if (jsonObject.getString("status") == "OK") {
                            val results = jsonObject.getJSONArray("results")
                            if (results.length() > 0) {
                                return@withContext results.getJSONObject(0).getString("formatted_address")
                            }
                        }
                    }
                    
                    // Fallback to Android Geocoder if API fails
                    val geocoder = Geocoder(context, Locale.getDefault())
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        suspendCancellableCoroutine { continuation ->
                            geocoder.getFromLocation(latLng.lat, latLng.lng, 1) { addresses ->
                                if (addresses.isNotEmpty()) {
                                    val addr = addresses[0]
                                    val subLocality = addr.subLocality ?: addr.locality ?: addr.featureName
                                    val street = addr.thoroughfare ?: addr.adminArea
                                    continuation.resume("$subLocality, $street".trim(',', ' ', 'n', 'u', 'l'), null)
                                } else {
                                    continuation.resume("Unknown location", null)
                                }
                            }
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        val addresses = geocoder.getFromLocation(latLng.lat, latLng.lng, 1)
                        if (!addresses.isNullOrEmpty()) {
                            val addr = addresses[0]
                            val subLocality = addr.subLocality ?: addr.locality ?: addr.featureName
                            val street = addr.thoroughfare ?: addr.adminArea
                            "$subLocality, $street".trim(',', ' ', 'n', 'u', 'l')
                        } else {
                            "Unknown location"
                        }
                    }
                } catch (e: Exception) {
                    "Coordinates: ${latLng.lat}, ${latLng.lng}"
                }
            }
            _uiState.update { it.copy(address = address) }
            saveDraft()
        }
    }

    private fun saveDraft() {
        val state = _uiState.value
        prefs.edit()
            .putString("draft_category", state.selectedCategory?.name)
            .putString("draft_volume", state.selectedVolume?.name)
            .putString("draft_notes", state.notes)
            .apply()
    }

    fun loadDraft() {
        val catName = prefs.getString("draft_category", null)
        val volName = prefs.getString("draft_volume", null)
        val notes = prefs.getString("draft_notes", "")
        
        val cat = WasteCategory.values().find { it.name == catName }
        val vol = VolumeTier.values().find { it.name == volName }
        
        _uiState.update { it.copy(
            selectedCategory = cat,
            selectedVolume = vol,
            notes = notes ?: ""
        ) }
    }

    fun submitRequest(gatePhotoBitmap: android.graphics.Bitmap?, onSuccess: (String) -> Unit, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            val userId = authRepository.getCurrentUserId() ?: "unknown_user"
            val userRoleResult = authRepository.getUserRole(userId)
            val residentPhone = userRoleResult.getOrNull()?.phone ?: ""
            val refNumber = "REQ-${(1000..9999).random()}"
            val state = _uiState.value

            // Duplicate Prevention Check
            try {
                val hasDuplicates = !firestore.collection("waste_requests")
                    .whereEqualTo("requesterId", userId)
                    .whereIn("status", listOf("PENDING_DISPATCH", "ASSIGNED_TO_FLEET", "EN_ROUTE", "ARRIVED_AT_LOCATION"))
                    .get()
                    .await()
                    .isEmpty
                
                if (hasDuplicates) {
                    _uiState.update { it.copy(isLoading = false) }
                    onError("You already have an active request.")
                    return@launch
                }
            } catch (e: Exception) {
                // Ignore error and proceed
            }

            var gateUrl: String? = null
            if (gatePhotoBitmap != null) {
                val bytes = ImageUtil.compressBitmapToByteArray(gatePhotoBitmap)
                val path = "gate/${refNumber}_${System.currentTimeMillis()}.jpg"
                val result = wasteRequestRepository.uploadImageToStorage(bytes, path)
                gateUrl = result.getOrNull()
            }
            
            val request = WasteRequest(
                requestRef = refNumber,
                clientUid = userId,
                pickupAddress = state.address,
                lat = state.location?.lat ?: 0.0,
                lng = state.location?.lng ?: 0.0,
                category = state.selectedCategory?.name ?: "",
                volumeTier = state.selectedVolume?.name ?: "",
                notes = state.notes,
                gatePhotoUrl = gateUrl,
                residentPhoneNumber = residentPhone
            )
            
            val result = wasteRequestRepository.createRequest(request)
            if (result.isSuccess) {
                val documentId = result.getOrNull() ?: ""
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        reference = refNumber
                    ) 
                }
                // Clear draft upon successful submission
                prefs.edit().clear().apply()
                onSuccess(documentId)
            } else {
                // For MVP, handle failure later (e.g. show toast)
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun clear() {
        _uiState.value = RequestUiState()
    }

    private val _activeRequest = MutableStateFlow<WasteRequest?>(null)
    val activeRequest: StateFlow<WasteRequest?> = _activeRequest.asStateFlow()

    fun watchRequest(id: String) {
        viewModelScope.launch {
            wasteRequestRepository.getRequest(id).collect { request ->
                _activeRequest.value = request
                if (request?.assignedTruckId != null && request.status == "EN_ROUTE") {
                    watchCollectorLocation(request.assignedTruckId)
                }
            }
        }
    }

    suspend fun getCollectorPhone(collectorId: String): String? {
        val result = authRepository.getUserRole(collectorId)
        return result.getOrNull()?.phone
    }

    private val _collectorLocation = MutableStateFlow<com.waste.link.data.models.LiveLocation?>(null)
    val collectorLocation: StateFlow<com.waste.link.data.models.LiveLocation?> = _collectorLocation.asStateFlow()

    private fun watchCollectorLocation(collectorId: String) {
        viewModelScope.launch {
            wasteRequestRepository.watchCollectorLocation(collectorId).collect { location ->
                _collectorLocation.value = location
            }
        }
    }

    private val _activeCollectors = MutableStateFlow<List<com.waste.link.data.models.LiveLocation>>(emptyList())
    val activeCollectors: StateFlow<List<com.waste.link.data.models.LiveLocation>> = _activeCollectors.asStateFlow()

    fun watchActiveCollectors() {
        viewModelScope.launch {
            wasteRequestRepository.watchActiveCollectors().collect { collectors ->
                _activeCollectors.value = collectors
            }
        }
    }
}
