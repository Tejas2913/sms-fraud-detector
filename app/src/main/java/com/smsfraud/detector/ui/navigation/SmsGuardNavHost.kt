package com.smsfraud.detector.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.smsfraud.detector.MainActivity
import com.smsfraud.detector.data.repository.SettingsRepository
import com.smsfraud.detector.ui.auditlog.AuditLogScreen
import com.smsfraud.detector.ui.dashboard.DashboardScreen
import com.smsfraud.detector.ui.developermode.DeveloperModeScreen
import com.smsfraud.detector.ui.fraudinbox.FraudInboxScreen
import com.smsfraud.detector.ui.messagedetail.MessageDetailScreen
import com.smsfraud.detector.ui.onboarding.OnboardingScreen
import com.smsfraud.detector.ui.safeinbox.SafeInboxScreen
import com.smsfraud.detector.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

/**
 * Navigation routes for the app.
 */
object Routes {
    const val ONBOARDING = "onboarding"
    const val DASHBOARD = "dashboard"
    const val FRAUD_INBOX = "fraud_inbox"
    const val SETTINGS = "settings"
    const val SAFE_INBOX = "safe_inbox"
    const val MESSAGE_DETAIL = "message_detail/{messageRefId}"
    const val AUDIT_LOG = "audit_log"
    const val DEVELOPER_MODE = "developer_mode"

    fun messageDetail(messageRefId: String) = "message_detail/$messageRefId"
}

/**
 * Bottom navigation tab definitions.
 * Product Spec v4 §4: Dashboard, Fraud Inbox, Settings.
 */
private data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val bottomNavItems = listOf(
    BottomNavItem(Routes.DASHBOARD, "Dashboard", Icons.Filled.Home),
    BottomNavItem(Routes.FRAUD_INBOX, "Fraud Inbox", Icons.Filled.Warning),
    BottomNavItem(Routes.SETTINGS, "Settings", Icons.Filled.Settings)
)

/** Top-level routes that show the bottom navigation bar. */
private val topLevelRoutes = bottomNavItems.map { it.route }.toSet()

@Composable
fun SmsGuardNavHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Check onboarding state
    val settingsRepository: SettingsRepository = hiltViewModel<OnboardingCheckViewModel>().settingsRepository
    var startDestination by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            val onboardingComplete = settingsRepository.isOnboardingComplete()
            startDestination = if (onboardingComplete) Routes.DASHBOARD else Routes.ONBOARDING
        }
    }

    if (startDestination == null) return // Wait for onboarding check

    // --- Notification deep-link: route to MessageDetail if activity has a pending messageRefId ---
    val activity = LocalContext.current as? MainActivity
    LaunchedEffect(startDestination, activity?.pendingMessageRefId) {
        val pendingId = activity?.pendingMessageRefId
        if (startDestination != null && !pendingId.isNullOrBlank()) {
            navController.navigate(Routes.messageDetail(pendingId)) {
                launchSingleTop = true
            }
            // Consume the pending ID so back-navigation / recomposition doesn't replay
            activity?.consumePendingMessageRefId()
        }
    }

    Scaffold(
        bottomBar = {
            if (currentRoute in topLevelRoutes) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            selected = currentRoute == item.route,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination!!,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    onComplete = { granted ->
                        scope.launch {
                            settingsRepository.setOnboardingComplete()
                            if (granted) {
                                settingsRepository.set(
                                    SettingsRepository.KEY_SMS_PROTECTION_ENABLED,
                                    "true"
                                )
                            }
                        }
                        navController.navigate(Routes.DASHBOARD) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    }
                )
            }

            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    onNavigateToFraudInbox = {
                        navController.navigate(Routes.FRAUD_INBOX) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToSafeInbox = {
                        navController.navigate(Routes.SAFE_INBOX)
                    }
                )
            }

            composable(Routes.FRAUD_INBOX) {
                FraudInboxScreen(
                    onNavigateToDetail = { messageRefId ->
                        navController.navigate(Routes.messageDetail(messageRefId))
                    }
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onNavigateToAuditLog = {
                        navController.navigate(Routes.AUDIT_LOG)
                    },
                    onNavigateToDeveloperMode = {
                        navController.navigate(Routes.DEVELOPER_MODE)
                    }
                )
            }

            composable(Routes.SAFE_INBOX) {
                SafeInboxScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToDetail = { messageRefId ->
                        navController.navigate(Routes.messageDetail(messageRefId))
                    }
                )
            }

            composable(
                route = Routes.MESSAGE_DETAIL,
                arguments = listOf(navArgument("messageRefId") { type = NavType.StringType })
            ) {
                MessageDetailScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Routes.AUDIT_LOG) {
                AuditLogScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Routes.DEVELOPER_MODE) {
                DeveloperModeScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
