package com.smsfraud.detector.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * App-level Hilt module for cross-cutting concerns.
 * DecisionEventBus, SmsContentResolver, CryptoUtil will be added in later milestones.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule
