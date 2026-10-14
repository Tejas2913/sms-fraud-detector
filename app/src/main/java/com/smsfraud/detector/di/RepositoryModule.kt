package com.smsfraud.detector.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Repository Hilt module.
 *
 * All repositories use @Inject constructor with @Singleton annotation,
 * so Hilt discovers and provides them automatically.
 * This module is kept as a placeholder for future manual bindings
 * (e.g., interface-to-implementation bindings in later milestones).
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule
