package com.waste.link.data.di

import android.content.Context
import androidx.room.Room
import com.waste.link.data.local.WasteLinkDatabase
import com.waste.link.data.local.WasteLinkMigrations
import com.waste.link.data.local.dao.LocalWasteRequestDao
import com.waste.link.data.local.dao.ResidentDraftDao
import com.waste.link.data.repository.LocalWasteRequestRepository
import com.waste.link.data.repository.LocalWasteRequestRepositoryImpl
import com.waste.link.data.repository.WasteRequestRepositoryImpl
import com.waste.link.domain.repository.WasteRequestRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DatabaseBindingModule {

    @Binds
    @Singleton
    abstract fun bindLocalWasteRequestRepository(
        impl: LocalWasteRequestRepositoryImpl
    ): LocalWasteRequestRepository

    @Binds
    @Singleton
    abstract fun bindWasteRequestRepository(
        impl: WasteRequestRepositoryImpl
    ): WasteRequestRepository
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideWasteLinkDatabase(
        @ApplicationContext context: Context
    ): WasteLinkDatabase {
        return Room.databaseBuilder(
            context,
            WasteLinkDatabase::class.java,
            WasteLinkDatabase.DATABASE_NAME
        ).addMigrations(*WasteLinkMigrations.ALL_MIGRATIONS)
            .build()
    }

    @Provides
    @Singleton
    fun provideLocalWasteRequestDao(
        database: WasteLinkDatabase
    ): LocalWasteRequestDao {
        return database.localWasteRequestDao()
    }

    @Provides
    @Singleton
    fun provideResidentDraftDao(
        database: WasteLinkDatabase
    ): ResidentDraftDao {
        return database.residentDraftDao()
    }
}
