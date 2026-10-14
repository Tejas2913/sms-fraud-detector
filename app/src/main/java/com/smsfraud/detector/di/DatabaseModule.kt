package com.smsfraud.detector.di

import android.content.Context
import androidx.room.Room
import com.smsfraud.detector.data.db.AppDatabase
import com.smsfraud.detector.data.db.dao.AuditLogDao
import com.smsfraud.detector.data.db.dao.MessageRefDao
import com.smsfraud.detector.data.db.dao.SenderHistoryDao
import com.smsfraud.detector.data.db.dao.SettingsDao
import com.smsfraud.detector.data.db.dao.UserFeedbackDao
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
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "sms_guard.db"
        )
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()
    }

    @Provides
    fun provideMessageRefDao(db: AppDatabase): MessageRefDao = db.messageRefDao()

    @Provides
    fun provideAuditLogDao(db: AppDatabase): AuditLogDao = db.auditLogDao()

    @Provides
    fun provideSenderHistoryDao(db: AppDatabase): SenderHistoryDao = db.senderHistoryDao()

    @Provides
    fun provideUserFeedbackDao(db: AppDatabase): UserFeedbackDao = db.userFeedbackDao()

    @Provides
    fun provideSettingsDao(db: AppDatabase): SettingsDao = db.settingsDao()
}
