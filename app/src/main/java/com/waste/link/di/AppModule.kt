package com.waste.link.di

import android.content.Context
import androidx.room.Room
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.ktx.storage
import com.waste.link.data.local.WasteLinkDatabase
import com.waste.link.data.local.WasteRequestDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideWasteLinkDatabase(@ApplicationContext context: Context): WasteLinkDatabase {
        return Room.databaseBuilder(
            context,
            WasteLinkDatabase::class.java,
            WasteLinkDatabase.DATABASE_NAME
        ).build()
    }

    @Provides
    @Singleton
    fun provideWasteRequestDao(database: WasteLinkDatabase): WasteRequestDao {
        return database.wasteRequestDao
    }

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth {
        return Firebase.auth
    }

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore {
        return Firebase.firestore
    }

    @Provides
    @Singleton
    fun provideFirebaseStorage(): FirebaseStorage {
        return Firebase.storage
    }
}
