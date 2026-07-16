package com.rhuta.kask.di

import android.content.Context
import androidx.room.Room
import com.rhuta.kask.data.db.KaskDatabase
import com.rhuta.kask.data.db.dao.HistoryDao
import com.rhuta.kask.data.db.dao.LibraryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): KaskDatabase =
        Room.databaseBuilder(context, KaskDatabase::class.java, "kask.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideHistoryDao(db: KaskDatabase): HistoryDao = db.historyDao()

    @Provides
    fun provideLibraryDao(db: KaskDatabase): LibraryDao = db.libraryDao()
}
