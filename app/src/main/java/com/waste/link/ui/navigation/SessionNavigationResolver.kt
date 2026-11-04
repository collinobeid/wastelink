package com.waste.link.ui.navigation

import com.waste.link.domain.model.AccountStatus
import com.waste.link.domain.model.CollectorVerificationStatus
import com.waste.link.domain.model.OnboardingStatus
import com.waste.link.domain.model.ProfileIncompleteReason
import com.waste.link.domain.model.SessionState
import com.waste.link.domain.model.UserRole
import com.waste.link.ui.Destinations

/**
 * Structured, immutable navigation decision resolved from the authoritative [SessionState].
 */
sealed interface NavigationDestinationDecision {
    val route: String
    val clearBackStack: Boolean get() = true

    data class Login(
        val prefillEmail: String? = null,
        val loadError: String? = null
    ) : NavigationDestinationDecision {
        override val route: String = Destinations.LOGIN
    }

    data class VerifyEmail(
        val email: String,
        val initialEmailSent: Boolean = true
    ) : NavigationDestinationDecision {
        override val route: String = Destinations.VERIFY_EMAIL
    }

    data class CompleteProfile(
        val uid: String,
        val email: String,
        val reason: ProfileIncompleteReason
    ) : NavigationDestinationDecision {
        override val route: String = Destinations.COMPLETE_PROFILE
    }

    data class ResidentOnboarding(
        val role: UserRole = UserRole.RESIDENT
    ) : NavigationDestinationDecision {
        override val route: String = Destinations.ONBOARDING
    }

    data class CollectorOnboarding(
        val role: UserRole = UserRole.COLLECTOR
    ) : NavigationDestinationDecision {
        override val route: String = Destinations.ONBOARDING
    }

    data class CollectorStatus(
        val status: CollectorVerificationStatus,
        val rejectionReason: String? = null
    ) : NavigationDestinationDecision {
        override val route: String = Destinations.COLLECTOR_VERIFICATION_STATUS
    }

    data class ResidentHome(
        val isRestricted: Boolean = false,
        val isOffline: Boolean = false
    ) : NavigationDestinationDecision {
        override val route: String = Destinations.RESIDENT_HOME
    }

    data class CollectorDashboard(
        val isRestricted: Boolean = false,
        val isOffline: Boolean = false
    ) : NavigationDestinationDecision {
        override val route: String = Destinations.COLLECTOR_DASHBOARD
    }

    data object AdminConsole : NavigationDestinationDecision {
        override val route: String = Destinations.ADMIN_SHELL
    }

    data class AccountStatusLock(
        val status: AccountStatus,
        val reason: String?,
        val suspendedUntilEpochMs: Long?
    ) : NavigationDestinationDecision {
        override val route: String = Destinations.ACCOUNT_STATUS_LOCK
    }

    data class SafeRecovery(
        val reason: String
    ) : NavigationDestinationDecision {
        override val route: String = Destinations.LOGIN
    }
}

/**
 * Authoritative Centralized Navigation Decision Resolver for WasteLink.
 *
 * Implements the single, deterministic decision matrix across all application entry points,
 * cold launch restorations, authentication state changes, and role/standing transitions.
 *
 * Security Invariants:
 * - Unverified emails are gated to [NavigationDestinationDecision.VerifyEmail].
 * - Incomplete profiles are gated to [NavigationDestinationDecision.CompleteProfile].
 * - Suspended/Banned accounts are routed to [NavigationDestinationDecision.AccountStatusLock] without automatic sign-out.
 * - Admin Console strictly requires `hasAdminClaim == true` (Firebase Auth token claim `admin == true`).
 *   Firestore role fields alone can NEVER grant admin privileges.
 * - Unapproved collectors cannot reach [NavigationDestinationDecision.CollectorDashboard].
 * - Offline cached profiles never grant administrator or privileged operations.
 */
object SessionNavigationResolver {

    fun resolve(session: SessionState): NavigationDestinationDecision {
        return when (session) {
            is SessionState.Unauthenticated,
            is SessionState.AuthenticationRevoked -> {
                NavigationDestinationDecision.Login()
            }

            is SessionState.AuthenticatedProfileIncomplete -> {
                NavigationDestinationDecision.CompleteProfile(
                    uid = session.uid,
                    email = session.email,
                    reason = session.reason
                )
            }

            is SessionState.AuthenticatedProfileLoadError -> {
                NavigationDestinationDecision.Login(
                    prefillEmail = session.email,
                    loadError = session.error.message
                )
            }

            is SessionState.AuthenticatedRestricted -> {
                resolveRestrictedSession(session)
            }

            is SessionState.Authenticated -> {
                resolveAuthenticatedSession(session)
            }

            is SessionState.AuthenticatedCachedOffline -> {
                resolveCachedOfflineSession(session)
            }
        }
    }

    private fun resolveRestrictedSession(
        session: SessionState.AuthenticatedRestricted
    ): NavigationDestinationDecision {
        val user = session.user
        return when (session.accountStatus) {
            AccountStatus.SUSPENDED -> {
                NavigationDestinationDecision.AccountStatusLock(
                    status = AccountStatus.SUSPENDED,
                    reason = session.statusReason,
                    suspendedUntilEpochMs = session.suspendedUntilEpochMs
                )
            }
            AccountStatus.BANNED -> {
                NavigationDestinationDecision.AccountStatusLock(
                    status = AccountStatus.BANNED,
                    reason = session.statusReason,
                    suspendedUntilEpochMs = null
                )
            }
            AccountStatus.RESTRICTED -> {
                if (!session.isEmailVerified) {
                    return NavigationDestinationDecision.VerifyEmail(user.email)
                }
                when (user.role) {
                    UserRole.RESIDENT -> {
                        if (user.onboardingStatus != OnboardingStatus.COMPLETED) {
                            NavigationDestinationDecision.ResidentOnboarding()
                        } else {
                            NavigationDestinationDecision.ResidentHome(isRestricted = true)
                        }
                    }
                    UserRole.COLLECTOR -> {
                        if (user.onboardingStatus != OnboardingStatus.COMPLETED ||
                            user.collectorVerificationStatus == CollectorVerificationStatus.NOT_SUBMITTED
                        ) {
                            NavigationDestinationDecision.CollectorOnboarding()
                        } else if (user.collectorVerificationStatus == CollectorVerificationStatus.APPROVED) {
                            NavigationDestinationDecision.CollectorDashboard(isRestricted = true)
                        } else {
                            NavigationDestinationDecision.CollectorStatus(
                                status = user.collectorVerificationStatus,
                                rejectionReason = user.collectorRejectionReason
                            )
                        }
                    }
                    UserRole.ADMIN -> {
                        NavigationDestinationDecision.SafeRecovery("Restricted administrator accounts require manual clearance.")
                    }
                }
            }
            AccountStatus.ACTIVE -> {
                // If accountStatus is ACTIVE, resolve standard authenticated path
                resolveAuthenticatedSession(
                    SessionState.Authenticated(
                        user = user,
                        isEmailVerified = session.isEmailVerified,
                        hasAdminClaim = false
                    )
                )
            }
        }
    }

    private fun resolveAuthenticatedSession(
        session: SessionState.Authenticated
    ): NavigationDestinationDecision {
        val user = session.user

        // 1. Account Standing Guard
        if (user.accountStatus == AccountStatus.SUSPENDED) {
            return NavigationDestinationDecision.AccountStatusLock(
                status = AccountStatus.SUSPENDED,
                reason = user.statusReason,
                suspendedUntilEpochMs = user.suspendedUntilEpochMs
            )
        }
        if (user.accountStatus == AccountStatus.BANNED) {
            return NavigationDestinationDecision.AccountStatusLock(
                status = AccountStatus.BANNED,
                reason = user.statusReason,
                suspendedUntilEpochMs = null
            )
        }
        if (user.accountStatus == AccountStatus.RESTRICTED) {
            return resolveRestrictedSession(
                SessionState.AuthenticatedRestricted(
                    user = user,
                    isEmailVerified = session.isEmailVerified,
                    accountStatus = AccountStatus.RESTRICTED,
                    statusReason = user.statusReason,
                    suspendedUntilEpochMs = user.suspendedUntilEpochMs
                )
            )
        }

        // 2. Email Verification Guard (Applies to all non-admin roles)
        if (user.role != UserRole.ADMIN && !session.isEmailVerified) {
            return NavigationDestinationDecision.VerifyEmail(user.email)
        }

        // 3. Role-Based Navigation Routing
        return when (user.role) {
            UserRole.ADMIN -> {
                // Critical Administrator Security Rule:
                // Server custom claim token.admin == true is mandatory.
                if (session.hasAdminClaim) {
                    NavigationDestinationDecision.AdminConsole
                } else {
                    NavigationDestinationDecision.SafeRecovery(
                        "Administrator profile lacks verified server-issued admin claim."
                    )
                }
            }

            UserRole.COLLECTOR -> {
                if (user.onboardingStatus != OnboardingStatus.COMPLETED ||
                    user.collectorVerificationStatus == CollectorVerificationStatus.NOT_SUBMITTED
                ) {
                    NavigationDestinationDecision.CollectorOnboarding()
                } else {
                    when (user.collectorVerificationStatus) {
                        CollectorVerificationStatus.PENDING_REVIEW -> {
                            NavigationDestinationDecision.CollectorStatus(
                                status = CollectorVerificationStatus.PENDING_REVIEW
                            )
                        }
                        CollectorVerificationStatus.REJECTED -> {
                            NavigationDestinationDecision.CollectorStatus(
                                status = CollectorVerificationStatus.REJECTED,
                                rejectionReason = user.collectorRejectionReason
                            )
                        }
                        CollectorVerificationStatus.APPROVED -> {
                            NavigationDestinationDecision.CollectorDashboard(isRestricted = false)
                        }
                        CollectorVerificationStatus.NOT_SUBMITTED,
                        CollectorVerificationStatus.NOT_APPLICABLE -> {
                            NavigationDestinationDecision.CollectorOnboarding()
                        }
                    }
                }
            }

            UserRole.RESIDENT -> {
                if (user.onboardingStatus != OnboardingStatus.COMPLETED) {
                    NavigationDestinationDecision.ResidentOnboarding()
                } else {
                    NavigationDestinationDecision.ResidentHome(isRestricted = false)
                }
            }
        }
    }

    private fun resolveCachedOfflineSession(
        session: SessionState.AuthenticatedCachedOffline
    ): NavigationDestinationDecision {
        val user = session.cachedUser

        // Offline standing guards
        if (user.accountStatus == AccountStatus.SUSPENDED) {
            return NavigationDestinationDecision.AccountStatusLock(
                status = AccountStatus.SUSPENDED,
                reason = user.statusReason,
                suspendedUntilEpochMs = user.suspendedUntilEpochMs
            )
        }
        if (user.accountStatus == AccountStatus.BANNED) {
            return NavigationDestinationDecision.AccountStatusLock(
                status = AccountStatus.BANNED,
                reason = user.statusReason,
                suspendedUntilEpochMs = null
            )
        }

        // Email verification
        if (!session.isEmailVerified) {
            return NavigationDestinationDecision.VerifyEmail(user.email)
        }

        // Offline role routing: Cached admins NEVER get AdminConsole!
        return when (user.role) {
            UserRole.ADMIN -> {
                NavigationDestinationDecision.SafeRecovery(
                    "Administrator access is disabled while operating in offline cached mode."
                )
            }
            UserRole.COLLECTOR -> {
                if (user.collectorVerificationStatus == CollectorVerificationStatus.APPROVED) {
                    NavigationDestinationDecision.CollectorDashboard(
                        isRestricted = user.accountStatus == AccountStatus.RESTRICTED,
                        isOffline = true
                    )
                } else if (user.collectorVerificationStatus in listOf(
                        CollectorVerificationStatus.PENDING_REVIEW,
                        CollectorVerificationStatus.REJECTED
                    )
                ) {
                    NavigationDestinationDecision.CollectorStatus(
                        status = user.collectorVerificationStatus,
                        rejectionReason = user.collectorRejectionReason
                    )
                } else {
                    NavigationDestinationDecision.CollectorOnboarding()
                }
            }
            UserRole.RESIDENT -> {
                if (user.onboardingStatus == OnboardingStatus.COMPLETED) {
                    NavigationDestinationDecision.ResidentHome(
                        isRestricted = user.accountStatus == AccountStatus.RESTRICTED,
                        isOffline = true
                    )
                } else {
                    NavigationDestinationDecision.ResidentOnboarding()
                }
            }
        }
    }
}
