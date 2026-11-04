package com.waste.link

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Security Baseline Test Suite.
 * Validates that critical security backdoors, unauthenticated wipe mechanisms,
 * and privilege escalation vulnerabilities remain permanently removed.
 */
class SecurityBaselineTest {

    @Test
    fun databaseWipeUtility_doesNotExistOnClasspath() {
        val classExists = try {
            Class.forName("com.waste.link.utils.DatabaseWipeUtility")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
        assertFalse("DatabaseWipeUtility must NOT exist in the codebase", classExists)
    }

    @Test
    fun authViewModel_doesNotExposeWipeDatabaseMethod() {
        val authViewModelClass = Class.forName("com.waste.link.ui.auth.AuthViewModel")
        val hasWipeMethod = authViewModelClass.methods.any { it.name.contains("wipe", ignoreCase = true) }
        assertFalse("AuthViewModel must NOT expose any database wipe methods", hasWipeMethod)
    }

    @Test
    fun authRepositoryImpl_doesNotContainHardcodedAdminEmails() {
        val repoClass = Class.forName("com.waste.link.data.repository.AuthRepositoryImpl")
        val methods = repoClass.declaredMethods
        val hasHardcodedEmailCheck = methods.any { method ->
            method.name == "isAdminEmail"
        }
        assertFalse("AuthRepositoryImpl must not have hardcoded admin email checks", hasHardcodedEmailCheck)
    }
}
