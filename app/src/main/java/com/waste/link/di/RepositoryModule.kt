package com.waste.link.di

import com.waste.link.data.repository.AuthRepository
import com.waste.link.data.repository.AuthRepositoryImpl
import com.waste.link.data.repository.WasteRequestRepository
import com.waste.link.data.repository.WasteRequestRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

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
