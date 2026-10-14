package com.smsfraud.detector

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.smsfraud.detector.ui.navigation.SmsGuardNavHost
import com.smsfraud.detector.ui.theme.SmsGuardTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Single activity hosting the entire Compose UI.
 * Product Spec v4 §4: three-tab bottom navigation is the spine.
 *
 * Milestone 3: launchMode="singleTop" + onNewIntent() allows notification
 * PendingIntents to navigate directly to the MessageDetail screen without
 * creating a new Activity instance.
 *
 * The [pendingMessageRefId] is read by [SmsGuardNavHost] on each composition
 * and consumed once navigation has occurred.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * messageRefId delivered from a notification PendingIntent tap.
     * Null if the activity was launched normally (no notification tap).
     * Consumed by SmsGuardNavHost on first read.
     */
    var pendingMessageRefId: String? = null
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Capture messageRefId from notification tap on cold start
        pendingMessageRefId = intent.getStringExtra("messageRefId")
        handleBenchmarkIntent(intent)
        enableEdgeToEdge()
        setContent {
            SmsGuardTheme {
                SmsGuardNavHost()
            }
        }
    }

    /**
     * Called when a notification PendingIntent is delivered while the activity
     * is already running (singleTop). Stores the messageRefId so the nav host
     * can route to MessageDetail on the next recomposition.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingMessageRefId = intent.getStringExtra("messageRefId")
        handleBenchmarkIntent(intent)
    }

    private fun handleBenchmarkIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra("run_benchmark", false) || intent.action == "com.smsfraud.detector.RUN_BENCHMARK") {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                com.smsfraud.detector.benchmark.LatencyBenchmarkRunner.runBenchmark(applicationContext)
            }
        }
    }

    /**
     * Consumed by [SmsGuardNavHost] after navigating to MessageDetail.
     * Prevents replaying the same navigation on recomposition or back-press.
     */
    fun consumePendingMessageRefId() {
        pendingMessageRefId = null
    }
}
