package com.smsfraud.detector

import android.app.Application
import com.smsfraud.detector.service.DetectionNotificationManager
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class for SMS Guard.
 * Annotated with @HiltAndroidApp to trigger Hilt code generation
 * and serve as the application-level dependency container.
 *
 * Milestone 3:
 *   - Registers Android notification channels on startup (idempotent; must
 *     happen before any notification can be posted by SmsReceiver).
 *   - MlEngineImpl lazily initialises its ONNX session on the first predict()
 *     call from the dispatcher thread — no explicit warm-up required here.
 */
@HiltAndroidApp
class SmsGuardApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Register notification channels before any BroadcastReceiver can fire
        DetectionNotificationManager.createChannels(this)
    }
}

