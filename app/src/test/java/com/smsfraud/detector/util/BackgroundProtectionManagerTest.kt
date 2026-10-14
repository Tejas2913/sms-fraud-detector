package com.smsfraud.detector.util

import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for BackgroundProtectionManager utility and BackgroundProtectionStatus.
 *
 * Verifies:
 *   1. Device manufacturer detection and OEM autostart verifiability.
 *   2. Status computation for unverifiable OEM devices vs stock Android.
 *   3. Settings-opened state handling.
 *   4. Guidance text & warnings.
 *   5. Safe intent launch handling without crashes.
 */
class BackgroundProtectionManagerTest {

    @Test
    fun `getManufacturer returns non-empty string`() {
        val manufacturer = BackgroundProtectionManager.getManufacturer()
        assertNotNull(manufacturer)
    }

    @Test
    fun `canVerifyProgrammatically returns false for OEM devices`() {
        // Since canVerifyProgrammatically checks OEM flags, verify behavior
        val canVerify = BackgroundProtectionManager.canVerifyProgrammatically()
        if (BackgroundProtectionManager.isXiaomi() ||
            BackgroundProtectionManager.isHuawei() ||
            BackgroundProtectionManager.isOppo() ||
            BackgroundProtectionManager.isVivo()
        ) {
            assertFalse("OEM autostart cannot be programmatically read", canVerify)
        } else {
            assertTrue("Stock Android allows battery optimization verification", canVerify)
        }
    }

    @Test
    fun `getStatus returns appropriate BackgroundProtectionStatus`() {
        val testContext = TestContext(canLaunch = false)
        val initialStatus = BackgroundProtectionManager.getStatus(testContext, hasOpenedSettings = false)
        val openedStatus = BackgroundProtectionManager.getStatus(testContext, hasOpenedSettings = true)

        if (!BackgroundProtectionManager.canVerifyProgrammatically()) {
            assertEquals(BackgroundProtectionStatus.UNVERIFIABLE, initialStatus)
            assertEquals(BackgroundProtectionStatus.SETTINGS_OPENED_UNVERIFIABLE, openedStatus)
        } else {
            assertEquals(BackgroundProtectionStatus.VERIFIED_RESTRICTED, initialStatus)
        }
    }

    @Test
    fun `FORCE_STOP_WARNING and UNVERIFIABLE_NOTE contain required user messaging`() {
        val warning = BackgroundProtectionManager.FORCE_STOP_WARNING
        assertTrue(warning.contains("Force Stopped"))
        assertTrue(warning.contains("prevents all background broadcast delivery"))

        val note = BackgroundProtectionManager.UNVERIFIABLE_NOTE
        assertEquals("Status cannot be verified automatically on this device.", note)
    }

    @Test
    fun `getGuidanceText returns valid non-empty guidance text`() {
        val testContext = TestContext(canLaunch = true)
        val text = BackgroundProtectionManager.getGuidanceText(testContext)
        assertNotNull(text)
        assertTrue(text.isNotEmpty())
    }

    @Test
    fun `openBackgroundSettings gracefully returns false when no system settings activity is present`() {
        val testContext = TestContext(canLaunch = false)
        val opened = BackgroundProtectionManager.openBackgroundSettings(testContext)
        assertFalse("When system activity is unavailable, openBackgroundSettings safely returns false", opened)
    }

    @Test
    fun `openBackgroundSettings returns true when settings intent launches successfully`() {
        val testContext = TestContext(canLaunch = true)
        val opened = BackgroundProtectionManager.openBackgroundSettings(testContext)
        assertTrue("When intent succeeds, openBackgroundSettings returns true", opened)
    }

    // Pure Kotlin test double context
    private class TestContext(private val canLaunch: Boolean) : ContextWrapper(null) {
        override fun getPackageName(): String = "com.smsfraud.detector"
        override fun startActivity(intent: Intent?) {
            if (!canLaunch) {
                throw ActivityNotFoundException("No system activity available in unit test environment")
            }
        }
    }
}
