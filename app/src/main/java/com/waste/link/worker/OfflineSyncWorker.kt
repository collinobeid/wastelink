package com.waste.link.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore
import com.waste.link.data.local.WasteRequestDao
import com.waste.link.data.models.WasteRequest
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.tasks.await

@HiltWorker
class OfflineSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val wasteRequestDao: WasteRequestDao,
    private val firestore: FirebaseFirestore
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val offlineRequests = wasteRequestDao.getOfflineRequestsSync()
            
            if (offlineRequests.isEmpty()) {
                return Result.success()
            }

            for (entity in offlineRequests) {
                val wasteRequest = WasteRequest(
                    requestRef = entity.requestRef,
                    clientUid = entity.clientUid,
                    geohash = entity.geohash,
                    lat = entity.lat,
                    lng = entity.lng,
                    pickupAddress = entity.pickupAddress,
                    category = entity.category,
                    volumeTier = entity.volumeTier,
                    notes = entity.notes,
                    gatePhotoBase64 = entity.gatePhotoBase64,
                    pocPhotoBase64 = entity.pocPhotoBase64,
                    status = entity.status
                )
                
                // Upload to Firestore
                firestore.collection("wasteRequests")
                    .add(wasteRequest)
                    .await()
                    
                // Once uploaded, delete from offline cache
                wasteRequestDao.deleteRequest(entity.requestRef)
            }
            
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
