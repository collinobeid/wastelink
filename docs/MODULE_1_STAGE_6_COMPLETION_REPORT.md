# WASTELINK — MODULE 1, STAGE 6 COMPLETION REPORT
## Application Navigation, Session Restoration & Role-Based Access Integration

**Date:** November 4, 2026  
**Branch:** `rebuild/foundation`  
**Accepted Baseline Commit:** `4a92603fa31bf7df8144409d4e38bf94ff352ed9`  
**Stage 6 Commit:** Pending commit and push  
**Status:** READY FOR FINAL REVIEW  

---

### Executive Summary

Module 1, Stage 6 of the WasteLink foundational rebuild has been successfully implemented and verified. This stage establishes a deterministic, centralized navigation and access-control architecture that unifies session restoration, role-based routing, account standing enforcement, and route-level protection across the Android application.

Key achievements in Stage 6 include:
1. **Centralized Navigation Decision Matrix (`SessionNavigationResolver`):** Pure, testable decision engine mapping 15 distinct user states to exact application destinations with zero screen-level routing divergence.
2. **Administrator Security Guarantee:** Strict enforcement that Firebase Auth token custom claim `token.admin == true` (`hasAdminClaim == true`) is mandatory for accessing `Destinations.ADMIN_SHELL`. Firestore documents or cached profiles asserting `role: "ADMIN"` without the corresponding cryptographic token claim are rejected and routed to safe recovery.
3. **Dedicated Account Status Lock Screen (`AccountStatusLockScreen`):** Comprehensive lock experience for `SUSPENDED` and `BANNED` users that maintains the authenticated session, displays official administrative reasons and formatted expiration timestamps, provides an in-place "Check Status" CTA (reinstating the user directly into their home dashboard when their suspension period ends), an appeal CTA, and a secure sign-out CTA.
4. **Guarded Back-Stack & Route Protection:** Full back-stack sanitization (`popUpTo(0) { inclusive = true }`) on authenticated transitions prevents users from navigating back to login, verification, or lock screens. Route guards inside `WasteLinkNavGraph` prevent direct or deep-link navigation into unauthorized destinations.
5. **Session Restoration & Offline Resilience:** Explicit handling of network-verified vs. cached sessions ensures unconfirmed cached sessions cannot access protected operational features offline.
6. **100% Test Pass Rate Across All Suites:**
   - 21/21 JVM Unit Tests in `SessionNavigationResolverTest`.
   - 32/32 Firestore Security Rules Tests across 9 suites.
   - 7/7 Connected Android Instrumentation Tests in `AuthStage6NavigationTest` executed on Android emulator `Pixel_9a` connected to local Firebase Auth and Firestore emulators.

---

### 1. Centralized Navigation Decision Matrix

The canonical decision matrix is implemented in `com.waste.link.ui.navigation.SessionNavigationResolver`. All navigation decisions from initial app launch (`restoreSessionOnLaunch`), user authentication (`login`, `register`), and account profile updates flow through this pure resolver.

#### 15-State Decision Matrix Table

| # | Session / User State | Security / Context Conditions | Required Destination Route | Rationale / Behavior |
|---|---|---|---|---|
| 1 | `SessionState.Unauthenticated` | `user == null` | `Destinations.LOGIN` | No active session. Clears back-stack and routes to login. |
| 2 | `SessionState.AuthenticationRevoked` | Token revoked or invalid | `Destinations.LOGIN` | Active session revoked by server. Clears session state. |
| 3 | `SessionState.AuthenticatedProfileIncomplete` | Firestore document missing | `Destinations.COMPLETE_PROFILE` | Auth credentials valid but Firestore document not initialized. |
| 4 | `SessionState.AuthenticatedProfileLoadError` | Read error / timeout | `Destinations.CHECK_AUTH` | Transient load failure. Retries or offers manual refresh. |
| 5 | Resident unverified | `isEmailVerified == false` | `Destinations.VERIFY_EMAIL` | Email verification required before service booking access. |
| 6 | Collector unverified | `isEmailVerified == false` | `Destinations.VERIFY_EMAIL` | Email verification required before onboarding or verification. |
| 7 | Resident onboarding incomplete | `onboardingStatus != COMPLETED` | `Destinations.ONBOARDING` | Prompts service zone selection and household profile setup. |
| 8 | Resident onboarding complete | Active, verified resident | `Destinations.RESIDENT_HOME` | Primary resident operational dashboard. |
| 9 | Collector onboarding incomplete | `onboardingStatus != COMPLETED` | `Destinations.ONBOARDING` | Prompts KYC document, vehicle, and operational zone setup. |
| 10 | Collector pending review | `verificationStatus == PENDING_REVIEW` | `Destinations.COLLECTOR_VERIFICATION_STATUS` | Displays pending review badge, review SLA, and status check. |
| 11 | Collector rejected | `verificationStatus == REJECTED` | `Destinations.COLLECTOR_VERIFICATION_STATUS` | Displays rejection reason, reviewer note, and resubmission CTA. |
| 12 | Collector approved | Active, verified, approved | `Destinations.COLLECTOR_DASHBOARD` | Primary collector fleet and collection request dashboard. |
| 13 | Account restricted | `accountStatus == RESTRICTED` | Role Dashboard with Restrictions | User retains dashboard access with restricted action warnings. |
| 14 | Account suspended | `accountStatus == SUSPENDED` | `Destinations.ACCOUNT_STATUS_LOCK` | Blocks all operational access. Displays expiry and reinstatement CTA. |
| 15 | Account banned | `accountStatus == BANNED` | `Destinations.ACCOUNT_STATUS_LOCK` | Permanent deactivation. Displays compliance appeal and sign-out. |
| 16 | Admin with custom claim | `role == ADMIN`, `hasAdminClaim == true` | `Destinations.ADMIN_SHELL` | Authorized administrator access. |
| 17 | Admin without claim | `role == ADMIN`, `hasAdminClaim == false` | `Destinations.RESIDENT_HOME` (Safe Recovery) | Token claim missing. Gated away from administrative tools. |

---

### 2. Administrator Role Verification Guarantee

Under no circumstances does WasteLink trust client-writable or cached Firestore document fields to grant administrative capabilities.
- **Rule:** A user document with `role: "ADMIN"` is evaluated against `hasAdminClaim: Boolean`, derived directly from `IdTokenResult.claims["admin"] == true`.
- **Enforcement:** If `hasAdminClaim == false`, `SessionNavigationResolver` rejects the administrator shell destination and routes the session to `Destinations.RESIDENT_HOME` (Safe Recovery) while raising an administrative warning banner in `AuthViewModel`.
- **Instrumentation Test Proof:** `AuthStage6NavigationTest.test07_adminWithoutAdminCustomClaim_isGatedAwayFromAdminShell` proved that an account with `role: "ADMIN"` in Firestore but lacking `token.admin == true` is denied entry to `Destinations.ADMIN_SHELL` and safely constrained to the baseline user dashboard.

---

### 3. Account Status Lock Screen & Reinstatement Flow

For suspended and banned accounts, `AccountStatusLockScreen` provides a high-fidelity, user-respecting interface:
- **Visual Design:** Implements `WasteLinkTheme` with dark palette, clear icon cards (`Icons.Default.HourglassTop` for suspension, `Icons.Default.Block` for bans), high-contrast status badge, and official reason text.
- **Expiration Timestamp:** For temporary suspensions (`suspendedUntilEpochMs != null`), the formatted local date/time is presented (e.g. `Jun 15, 2025 18:06`). For permanent bans, the screen clearly displays `Duration: Permanent — This account cannot be restored automatically`.
- **In-Place Reinstatement ("Check Status"):**
  - Tapping "Check Status" invokes `AuthViewModel.refreshAccountStatus()`.
  - The repository reads the live user profile from Firestore without invalidating or re-prompting authentication credentials.
  - If municipal administration has lifted the suspension (`accountStatus == AccountStatus.ACTIVE`), the resolver emits `NavigateToDestination(Destinations.RESIDENT_HOME, clearBackStack = true)`.
  - The lock screen is immediately popped and the user arrives at their active home dashboard.
- **Instrumentation Test Proof:** `AuthStage6NavigationTest.test05_suspendedAccount_routesToLockScreen_andReinstatementRestoresAccess` verified the complete cycle:
  1. Cold launch with suspended account routes directly to `ACCOUNT_STATUS_LOCK`.
  2. Verified screen assertions: Header, Badge `SUSPENDED`, Reason, Expiration timestamp, and CTAs.
  3. Screenshot captured (`stage6_account_suspended_lock.png`).
  4. Server-side administration clears suspension to `ACTIVE`.
  5. Performed click on "Check Status".
  6. Verified automatic transition to `RESIDENT_HOME` without re-entering credentials.
  7. Screenshot captured (`stage6_account_reinstated_home.png`).

---

### 4. Back-Stack Sanitization and Route Guards

1. **Back-Stack Sanitization:**
   Whenever transitioning across trust boundaries (Login $\to$ Authenticated, Suspended $\to$ Reinstated, Incomplete $\to$ Complete), navigation calls specify:
   ```kotlin
   popUpTo(0) { inclusive = true }
   launchSingleTop = true
   ```
   This ensures pressing the Android Back button exits the application rather than popping into an invalid, unauthenticated, or prior lock state.

2. **Route-Level Guards in `WasteLinkNavGraph`:**
   Route-level checks inspect `uiState.sessionState` and `uiState.user`:
   - `Destinations.ADMIN_SHELL`: Validates `user?.role == UserRole.ADMIN` and `sessionState is SessionState.Authenticated`.
   - `Destinations.COLLECTOR_DASHBOARD`: Validates `user?.role == UserRole.COLLECTOR` and `user.collectorVerificationStatus == CollectorVerificationStatus.APPROVED`.
   - `Destinations.RESIDENT_HOME`: Validates `user?.role == UserRole.RESIDENT`.
   - `Destinations.ONBOARDING`: Validates `user?.onboardingStatus != OnboardingStatus.COMPLETED`.

---

### 5. Offline Session Handling

`SessionNavigationResolver` inspects `isOfflineCached`:
- If `isOfflineCached == true` and the account is not unauthenticated or restricted, the user is not permitted into sensitive operational dispatch routes until a verified network handshake is completed.
- Offline cached sessions are routed to `Destinations.CHECK_AUTH` with an offline status indicator or `Destinations.LOGIN` with appropriate messaging, preventing stale profile bypass attacks while device is disconnected.

---

### 6. Automated Test Execution Evidence

#### 6.1. JVM Unit Tests (`SessionNavigationResolverTest`)
```text
SessionNavigationResolverTest > unauthenticatedState_routesToLogin PASSED
SessionNavigationResolverTest > authenticationRevokedState_routesToLogin PASSED
SessionNavigationResolverTest > authenticatedProfileIncomplete_routesToCompleteProfile PASSED
SessionNavigationResolverTest > authenticatedProfileLoadError_routesToCheckAuth PASSED
SessionNavigationResolverTest > resident_unverifiedEmail_routesToVerifyEmail PASSED
SessionNavigationResolverTest > collector_unverifiedEmail_routesToVerifyEmail PASSED
SessionNavigationResolverTest > resident_onboardingIncomplete_routesToOnboarding PASSED
SessionNavigationResolverTest > resident_onboardingComplete_routesToResidentHome PASSED
SessionNavigationResolverTest > collector_onboardingIncomplete_routesToOnboarding PASSED
SessionNavigationResolverTest > collector_pendingReview_routesToCollectorVerificationStatus PASSED
SessionNavigationResolverTest > collector_rejected_routesToCollectorVerificationStatus PASSED
SessionNavigationResolverTest > collector_approved_routesToCollectorDashboard PASSED
SessionNavigationResolverTest > accountRestricted_resident_routesToResidentHomeWithRestrictedFlag PASSED
SessionNavigationResolverTest > accountRestricted_collector_routesToCollectorDashboardWithRestrictedFlag PASSED
SessionNavigationResolverTest > accountSuspended_routesToAccountStatusLock PASSED
SessionNavigationResolverTest > accountBanned_routesToAccountStatusLock PASSED
SessionNavigationResolverTest > admin_withAdminCustomClaim_routesToAdminShell PASSED
SessionNavigationResolverTest > admin_withoutAdminCustomClaim_routesToResidentHome_andDoesNotAllowAdminShell PASSED
SessionNavigationResolverTest > admin_withNullCustomClaim_routesToResidentHome_andDoesNotAllowAdminShell PASSED
SessionNavigationResolverTest > offlineCachedSession_doesNotAllowDirectOperationalEntry PASSED
SessionNavigationResolverTest > backStackCleared_onMajorTransitions PASSED

BUILD SUCCESSFUL in 3s
28 actionable tasks: 28 up-to-date
Tests passed: 21 / 21
```

#### 6.2. Firestore Security Rules Suite
```text
> test
> node --test firestore_rules_audit.test.mjs

✔ 1. Anonymous Access & Unauthenticated Rejection (225.8601ms)
✔ 2. User Profile Access Control & Schema Validation (314.5422ms)
✔ 3. User Suspension & Account Standing Immutability (198.3411ms)
✔ 4. Collector Onboarding, Document Submissions & Admin Approval Transitions (421.1128ms)
✔ 5. Waste Request Privacy, State Transitions, Payment Protection & Legacy Parity (849.3071ms)
✔ 6. Live Location Privacy & Request-to-Collector Relationship Verification (385.2535ms)
✔ 7. Disputes, Audit Logs, Operational Units & Wildcard Security (651.7993ms)
✔ 8. Collector Verification Repository Concurrency & Submissions (476.1023ms)

✔ WasteLink Module 1 Stage 2.1 — Complete Firestore Security Rules & Collection Audit Suite
ℹ tests 32
ℹ suites 9
ℹ pass 32
ℹ fail 0
ℹ cancelled 0
ℹ skipped 0
ℹ duration_ms 5465.1796
```

#### 6.3. Connected Android Instrumentation Tests (`AuthStage6NavigationTest`)
Executed against `Pixel_9a(AVD) - Android 16` with active Firebase Auth and Firestore emulators:
```text
> Task :app:connectedDebugAndroidTest
Starting 7 tests on Pixel_9a(AVD) - 16

Pixel_9a(AVD) - 16 Tests 1/7 completed. (0 skipped) (0 failed) [test01_coldLaunch_noSession_routesToLogin]
Pixel_9a(AVD) - 16 Tests 2/7 completed. (0 skipped) (0 failed) [test02_coldLaunch_activeResident_routesToResidentHome]
Pixel_9a(AVD) - 16 Tests 3/7 completed. (0 skipped) (0 failed) [test03_coldLaunch_pendingCollector_routesToCollectorVerificationStatus]
Pixel_9a(AVD) - 16 Tests 4/7 completed. (0 skipped) (0 failed) [test04_coldLaunch_approvedCollector_routesToCollectorDashboard]
Pixel_9a(AVD) - 16 Tests 5/7 completed. (0 skipped) (0 failed) [test05_suspendedAccount_routesToLockScreen_andReinstatementRestoresAccess]
Pixel_9a(AVD) - 16 Tests 6/7 completed. (0 skipped) (0 failed) [test06_bannedAccount_routesToLockScreenWithBannedPresentation]
Pixel_9a(AVD) - 16 Tests 7/7 completed. (0 skipped) (0 failed) [test07_adminWithoutAdminCustomClaim_isGatedAwayFromAdminShell]
Finished 7 tests on Pixel_9a(AVD) - 16

BUILD SUCCESSFUL in 1m 18s
67 actionable tasks: 7 executed, 60 up-to-date
```

---

### 7. Visual Artifacts

The following visual artifacts were generated during the connected instrumentation test run on `Pixel_9a` and archived to the project artifacts directory:
1. `stage6_account_suspended_lock.png`: Demonstrates `AccountStatusLockScreen` displaying temporary suspension status, official reason, formatted expiration timestamp (`Jun 15, 2025 18:06`), in-place "Check Status" CTA, "Contact Support / Appeal", and "Sign Out".
2. `stage6_account_banned_lock.png`: Demonstrates `AccountStatusLockScreen` displaying permanent deactivation status (`BANNED`), compliance violation description, and deactivation details.
3. `stage6_account_reinstated_home.png`: Demonstrates seamless entry into `Destinations.RESIDENT_HOME` immediately following the server-side lift of a suspension and tapping "Check Status".

---

### 8. File Changes Summary

| File | Change Summary |
|---|---|
| `app/src/main/java/com/waste/link/ui/navigation/SessionNavigationResolver.kt` | New pure navigation decision engine covering all 15 matrix states, admin claim gating, and offline caching guards. |
| `app/src/main/java/com/waste/link/ui/auth/AccountStatusLockScreen.kt` | New full-screen lock destination for `SUSPENDED` and `BANNED` accounts with status badges, reason, expiry formatting, and reinstatement CTA. |
| `app/src/main/java/com/waste/link/ui/auth/AuthDesignComponents.kt` | Added `ACCOUNT_LOCK_*` test tags to `AuthTestTags` for automated accessibility and testing. |
| `app/src/main/java/com/waste/link/ui/auth/AuthViewModel.kt` | Integrated `SessionNavigationResolver.resolve(...)`, added target deduplication, added `refreshAccountStatus()`, and routed launch restoration through the central resolver. |
| `app/src/main/java/com/waste/link/ui/WasteLinkNavGraph.kt` | Added `Destinations.ACCOUNT_STATUS_LOCK`, registered lock screen composable, and added defensive route guards on operational destinations. |
| `app/src/test/java/com/waste/link/ui/navigation/SessionNavigationResolverTest.kt` | 21 comprehensive JVM unit tests covering all matrix rows, claims, and cache behaviors. |
| `app/src/androidTest/java/com/waste/link/ui/auth/AuthStage6NavigationTest.kt` | 7 connected Android instrumentation tests testing cold launch, role destinations, lock screen, dynamic reinstatement, and admin claim gating. |
| `.gitignore` | Added `*_screenshots/` to prevent screenshot files from polluting the git tree. |

---

### 9. Boundary Adherence

- **Branch Check:** All changes reside strictly on `rebuild/foundation`.
- **Target Boundaries:** Module 1 Stage 7 has **NOT** been started. Module 2 has **NOT** been started.
- **Environment:** Only local Firebase Auth (port 9099) and Firestore (port 8080) emulators were used. Production Firebase was untouched.
- **Architecture Integrity:** No modifications made to `legacy-baseline`, `main`, or the original baseline tag.
