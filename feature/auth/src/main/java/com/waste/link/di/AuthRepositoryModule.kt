package com.waste.link.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.waste.link.data.remote.AuthRemoteDataSource
import com.waste.link.data.repository.AuthRepository
import com.waste.link.data.repository.AuthRepositoryImpl
import com.waste.link.data.repository.CollectorVerificationRepository
import com.waste.link.data.repository.CollectorVerificationRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthRepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        authRepositoryImpl: AuthRepositoryImpl
    ): AuthRepository

    @Binds
    @Singleton
    abstract fun bindSessionStateProvider(
        authRepositoryImpl: AuthRepositoryImpl
    ): com.waste.link.data.auth.SessionStateProvider

    @Binds
    @Singleton
    abstract fun bindCollectorVerificationRepository(
        collectorVerificationRepositoryImpl: CollectorVerificationRepositoryImpl
    ): CollectorVerificationRepository
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AuthRepositoryEntryPoint {
    fun authRemoteDataSource(): AuthRemoteDataSource
    fun authRepository(): AuthRepository
    fun authRepositoryImpl(): AuthRepositoryImpl
    fun collectorVerificationRepository(): CollectorVerificationRepository
    fun collectorVerificationRepositoryImpl(): CollectorVerificationRepositoryImpl
    fun firebaseAuth(): FirebaseAuth
    fun firebaseFirestore(): FirebaseFirestore
}
