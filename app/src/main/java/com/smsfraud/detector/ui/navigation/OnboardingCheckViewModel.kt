package com.smsfraud.detector.ui.navigation

import androidx.lifecycle.ViewModel
import com.smsfraud.detector.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Thin ViewModel to expose SettingsRepository for onboarding check in NavHost.
 */
@HiltViewModel
class OnboardingCheckViewModel @Inject constructor(
    val settingsRepository: SettingsRepository
) : ViewModel()
