package com.windroidpro.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.windroidpro.data.AppDatabase
import com.windroidpro.data.ContainerDao
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
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "windroid_pro.db"
        )
        .addMigrations(object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE containers ADD COLUMN runtimeId INTEGER NOT NULL DEFAULT 0")
            }
        })
        .build()
    }

    @Provides
    @Singleton
    fun provideContainerDao(database: AppDatabase): ContainerDao {
        return database.containerDao()
    }
}
