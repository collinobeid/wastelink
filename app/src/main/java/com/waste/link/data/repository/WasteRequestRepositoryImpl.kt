package com.waste.link.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.firestore.snapshots
import com.waste.link.data.models.WasteRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class WasteRequestRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage
) : WasteRequestRepository {

    override suspend fun createRequest(request: WasteRequest): Result<String> {
        return try {
            // First check if the user is RESTRICTED (Shadow ban check)
            var finalStatus = request.status
            try {
                val userDoc = firestore.collection("users").document(request.clientUid).get().await()
                val status = userDoc.getString("status")
                if (status == "RESTRICTED") {
                    finalStatus = "FLAGGED_FOR_REVIEW" // Silently divert to Admin queue
                }
            } catch (e: Exception) {
                // Ignore failure to fetch user, proceed normally
            }

            val docRef = firestore.collection("wasteRequests").document()
            val requestWithId = request.copy(
                id = docRef.id,
                status = finalStatus
            )
            docRef.set(requestWithId).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getUserRequests(uid: String): Flow<List<WasteRequest>> {
        return firestore.collection("wasteRequests")
            .whereEqualTo("clientUid", uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(20)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(WasteRequest::class.java)
            }
    }

    override fun getAllActiveRequests(): Flow<List<WasteRequest>> {
        return firestore.collection("wasteRequests")
            .whereIn("status", listOf("PENDING_DISPATCH", "ASSIGNED_TO_FLEET", "EN_ROUTE", "ARRIVED_AT_LOCATION", "COLLECTION_VERIFIED", "IN_TRANSIT_TO_DUMP"))
            .limit(20)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(WasteRequest::class.java)
            }
    }

    override fun getRequest(id: String): Flow<WasteRequest?> {
        return firestore.collection("wasteRequests")
            .document(id)
            .snapshots()
            .map { snapshot ->
                snapshot.toObject(WasteRequest::class.java)
            }
    }

    override suspend fun updateRequestStatus(
        rid: String,
        status: String,
        adminUid: String?,
        truckId: String?,
        agreedPrice: Double?
    ): Result<Unit> {
        return try {
            val updates = mutableMapOf<String, Any>(
                "status" to status
            )
            adminUid?.let { updates["assignedTruckId"] = it } // using new assignedTruckId field
            agreedPrice?.let { updates["agreedPrice"] = it }
            
            firestore.collection("wasteRequests")
                .document(rid)
                .update(updates)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun uploadImageToStorage(imageBytes: ByteArray, path: String): Result<String> {
        return try {
            val storageRef = storage.reference.child(path)
            storageRef.putBytes(imageBytes).await()
            val downloadUrl = storageRef.downloadUrl.await()
            Result.success(downloadUrl.toString())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateCollectorLocation(collectorId: String, lat: Double, lng: Double): Result<Unit> {
        return try {
            val location = com.waste.link.data.models.LiveLocation(
                collectorId = collectorId,
                lat = lat,
                lng = lng,
                heading = 0f
            )
            firestore.collection("live_locations").document(collectorId).set(location).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun watchCollectorLocation(collectorId: String): Flow<com.waste.link.data.models.LiveLocation?> {
        return firestore.collection("live_locations")
            .document(collectorId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObject(com.waste.link.data.models.LiveLocation::class.java)
            }
    }

    override fun watchActiveCollectors(): Flow<List<com.waste.link.data.models.LiveLocation>> {
        return firestore.collection("live_locations")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(com.waste.link.data.models.LiveLocation::class.java)
            }
    }

    override fun getCollectorEarningsToday(collectorId: String): Flow<Double> {
        val startOfDay = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.time
        
        return firestore.collection("wasteRequests")
            .whereEqualTo("assignedTruckId", collectorId)
            .whereEqualTo("status", "COMPLETED")
            .snapshots()
            .map { snapshot ->
                val requests = snapshot.toObjects(WasteRequest::class.java)
                requests.filter { it.updatedAt != null && it.updatedAt.after(startOfDay) }
                    .sumOf { it.agreedPrice ?: 0.0 }
            }
    }
}
