package com.waste.link.utils

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

object DatabaseWipeUtility {
    
    suspend fun wipeDatabase(db: FirebaseFirestore) {
        val collectionsToWipe = listOf(
            "users", 
            "operational_units", 
            "service_disputes", 
            "wasteRequests",
            "admin_actions_log"
        )
        
        for (collectionName in collectionsToWipe) {
            try {
                val snapshot = db.collection(collectionName).get().await()
                for (document in snapshot.documents) {
                    db.collection(collectionName).document(document.id).delete().await()
                }
            } catch (e: Exception) {
                // Ignore if it fails due to permissions or empty
            }
        }
    }
}
