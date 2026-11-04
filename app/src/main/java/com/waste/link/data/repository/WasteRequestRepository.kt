package com.waste.link.data.repository

import com.waste.link.data.models.WasteRequest
import kotlinx.coroutines.flow.Flow

interface WasteRequestRepository {
    suspend fun createRequest(request: WasteRequest): Result<String>
    fun getUserRequests(uid: String): Flow<List<WasteRequest>>
    fun getAllActiveRequests(): Flow<List<WasteRequest>>
    fun getRequest(id: String): Flow<WasteRequest?>
    suspend fun updateRequestStatus(rid: String, status: String, adminUid: String?, truckId: String? = null, agreedPrice: Double? = null): Result<Unit>
    suspend fun uploadImageToStorage(imageBytes: ByteArray, path: String): Result<String>
    
    // Live Location Methods
    suspend fun updateCollectorLocation(collectorId: String, lat: Double, lng: Double): Result<Unit>
    fun watchCollectorLocation(collectorId: String): Flow<com.waste.link.data.models.LiveLocation?>
    fun watchActiveCollectors(): Flow<List<com.waste.link.data.models.LiveLocation>>
    
    fun getCollectorEarningsToday(collectorId: String): Flow<Double>
}
