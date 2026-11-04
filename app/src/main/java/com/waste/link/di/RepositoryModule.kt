package com.waste.link.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.waste.link.data.remote.AuthRemoteDataSource
import com.waste.link.data.remote.FirebaseAuthDataSource
import com.waste.link.data.repository.AuthRepository
import com.waste.link.data.repository.AuthRepositoryImpl
import com.waste.link.data.repository.WasteRequestRepository
import com.waste.link.data.repository.WasteRequestRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRemoteDataSource(
        firebaseAuthDataSource: FirebaseAuthDataSource
    ): AuthRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        authRepositoryImpl: AuthRepositoryImpl
    ): AuthRepository

    @Binds
    @Singleton
    abstract fun bindWasteRequestRepository(
        wasteRequestRepositoryImpl: WasteRequestRepositoryImpl
    ): WasteRequestRepository
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AuthRepositoryEntryPoint {
    fun authRemoteDataSource(): AuthRemoteDataSource
    fun authRepository(): AuthRepository
    fun authRepositoryImpl(): AuthRepositoryImpl
    fun firebaseAuth(): FirebaseAuth
    fun firebaseFirestore(): FirebaseFirestore
}


