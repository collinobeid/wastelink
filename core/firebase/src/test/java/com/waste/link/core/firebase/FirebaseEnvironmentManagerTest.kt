package com.waste.link.core.firebase

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class FirebaseEnvironmentManagerTest {

    @Before
    fun setUp() {
        FirebaseEnvironmentManager.resetForTesting()
    }

    @After
    fun tearDown() {
        FirebaseEnvironmentManager.resetForTesting()
    }

    @Test
    fun defaultEnvironment_isLocalEmulator() {
        assertEquals(FirebaseEnvironment.LOCAL_EMULATOR, FirebaseEnvironmentManager.getEnvironment())
        assertFalse(FirebaseEnvironmentManager.isProductionActivated())
    }

    @Test
    fun setEnvironment_updatesCorrectly() {
        FirebaseEnvironmentManager.setEnvironment(FirebaseEnvironment.REMOTE_STAGING)
        assertEquals(FirebaseEnvironment.REMOTE_STAGING, FirebaseEnvironmentManager.getEnvironment())

        FirebaseEnvironmentManager.setEnvironment(FirebaseEnvironment.PRODUCTION)
        assertEquals(FirebaseEnvironment.PRODUCTION, FirebaseEnvironmentManager.getEnvironment())
    }

    @Test
    fun setProductionActivated_controlsGate() {
        assertFalse(FirebaseEnvironmentManager.isProductionActivated())
        FirebaseEnvironmentManager.setProductionActivated(true)
        assertTrue(FirebaseEnvironmentManager.isProductionActivated())
    }

    @Test
    fun validateProjectConfiguration_matchingIds_succeeds() {
        // Exact match
        FirebaseEnvironmentManager.validateProjectConfiguration("wastelink-27595", "wastelink-27595")
        // Case-insensitive match
        FirebaseEnvironmentManager.validateProjectConfiguration("wastelink-prod-2026", "WASTELINK-PROD-2026")
    }

    @Test
    fun validateProjectConfiguration_mismatchedIds_throwsIllegalStateException() {
        try {
            FirebaseEnvironmentManager.validateProjectConfiguration("wastelink-prod-2026", "wastelink-27595")
            fail("Expected IllegalStateException on project ID mismatch")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("Firebase project mismatch"))
            assertTrue(e.message!!.contains("wastelink-prod-2026"))
            assertTrue(e.message!!.contains("wastelink-27595"))
        }
    }

    @Test
    fun validateProjectConfiguration_emptyExpected_ignoresNull() {
        FirebaseEnvironmentManager.validateProjectConfiguration("", null)
        FirebaseEnvironmentManager.validateProjectConfiguration("", "any-project")
    }
}
