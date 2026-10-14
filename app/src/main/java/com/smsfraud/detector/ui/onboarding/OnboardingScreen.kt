package com.smsfraud.detector.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Four-screen onboarding flow per Product Spec v4 §1.1, Table 1.
 * Shown only on first install. Writes onboarding_complete flag on completion.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onComplete: (smsPermissionGranted: Boolean) -> Unit
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { 4 })
    val scope = rememberCoroutineScope()
    var smsGrantedState by remember { mutableStateOf(false) }

    // Notification permission launcher — fires AFTER SMS permission callback completes on Android 13+.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        onComplete(smsGrantedState)
    }

    // SMS permission launcher — requests notification permission in callback on Android 13+
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.RECEIVE_SMS] == true &&
                permissions[Manifest.permission.READ_SMS] == true
        smsGrantedState = granted

        // Chain notification permission AFTER SMS result on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasNotificationPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasNotificationPermission) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return@rememberLauncherForActivityResult
            }
        }

        onComplete(granted)
    }


    Scaffold { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            userScrollEnabled = false
        ) { page ->
            when (page) {
                0 -> OnboardingPage(
                    icon = Icons.Filled.Shield,
                    title = "SMS Guard",
                    subtitle = "Protect your inbox from scam messages",
                    buttonText = "Next"
                ) {
                    scope.launch { pagerState.animateScrollToPage(1) }
                }
                1 -> OnboardingPage(
                    icon = Icons.Filled.Security,
                    title = "What it does",
                    subtitle = "This app checks incoming SMS for common fraud patterns. " +
                            "It runs entirely on your phone.\n\n" +
                            "• No messages are sent anywhere\n" +
                            "• No personal data leaves your device",
                    buttonText = "Next"
                ) {
                    scope.launch { pagerState.animateScrollToPage(2) }
                }
                2 -> OnboardingPage(
                    icon = Icons.Filled.Sms,
                    title = "Why SMS access",
                    subtitle = "To check messages, the app needs to read incoming SMS. " +
                            "Here's why that's safe:\n\n" +
                            "• The app never sends messages\n" +
                            "• No text is stored — only safety scores and labels",
                    buttonText = "Next"
                ) {
                    scope.launch { pagerState.animateScrollToPage(3) }
                }
                3 -> OnboardingPage(
                    icon = Icons.Filled.VerifiedUser,
                    title = "Grant access",
                    subtitle = "Allow SMS Guard to read incoming messages so it can protect you.\n\n" +
                            "🔒 100% On-Device Analysis — No text is ever uploaded.",
                    buttonText = "Grant Permission"
                ) {
                    smsPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.RECEIVE_SMS,
                            Manifest.permission.READ_SMS,
                            Manifest.permission.READ_CONTACTS
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingPage(
    icon: ImageVector,
    title: String,
    subtitle: String,
    buttonText: String,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = onAction,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(buttonText)
        }
    }
}
