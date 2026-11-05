package com.waste.link.di

import com.waste.link.data.remote.AuthRemoteDataSource
import com.waste.link.data.remote.FirebaseAuthDataSource
import com.waste.link.data.remote.FirestoreWasteRequestRemoteDataSource
import com.waste.link.data.remote.WasteRequestRemoteDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FirebaseDataModule {

    @Binds
    @Singleton
    abstract fun bindAuthRemoteDataSource(
        firebaseAuthDataSource: FirebaseAuthDataSource
    ): AuthRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindWasteRequestRemoteDataSource(
        firestoreWasteRequestRemoteDataSource: FirestoreWasteRequestRemoteDataSource
    ): WasteRequestRemoteDataSource
}
