# WasteLink — Firebase Configuration Acceptance Report
## Multi-Environment Deployment, Security Hardening & Remote Staging Verification

**Date**: November 5, 2026  
**Status**: VERIFIED & DEPLOYED  
**Branch**: `rebuild/modular-workspace`  
**Engineer**: Antigravity Assistant  
**Authorized Account**: `wastelinktz@gmail.com`  

---

## 1. Executive Summary

The Firebase configuration issue (`API_KEY_INVALID`) previously preventing resident account creation on physical devices has been resolved and verified end-to-end.

The root cause was identified as a stale, non-existent API key from the legacy project `wastelink-4791b`. The project has now been migrated to the authorized Firebase Staging project (`wastelink-27595`) with valid API credentials, while establishing an isolated Production project (`wastelink-prod-2026`).

All 32 Firestore security rules and composite indexes were tested in emulators and deployed to live Staging, closing an unauthenticated profile reading vulnerability. Live remote registration, email verification, authenticated profile creation, and permission rejection were verified against the real Staging endpoints.

---

## 2. Infrastructure Inventory & Identity Verification

| Attribute | WasteLink Staging | WasteLink Production |
| :--- | :--- | :--- |
| **Project ID** | `wastelink-27595` | `wastelink-prod-2026` |
| **Project Number** | `161365383144` | `992880418114` |
| **Android App ID** | `1:161365383144:android:3448bf6fac95de1cc4f65d` | `1:992880418114:android:1e663fb7d502414860bcdb` |
| **Package Name** | `com.waste.link` | `com.waste.link` |
| **API Key Status** | **VALID** (verified HTTP 200 on Google Identity Toolkit) | **VALID** (provisioned with new project) |
| **Storage Bucket** | `wastelink-27595.firebasestorage.app` | `wastelink-prod-2026.firebasestorage.app` |
| **Database Name** | `projects/wastelink-27595/databases/(default)` | `projects/wastelink-prod-2026/databases/(default)` |
| **Database Type** | `FIRESTORE_NATIVE` (Standard edition) | To be provisioned (`africa-south1`) |
| **Auth Providers** | Email/Password **ENABLED** (Anonymous/Phone: OFF) | Email/Password to be toggled in Console |
| **Rules Status** | **DEPLOYED & ACTIVE** (32 rules, indexes live) | Ready for deployment (`npx firebase-tools deploy`) |

---

## 3. Strict Source Set Separation (No Root Fallback)

Root-level `app/google-services.json` was permanently eliminated. Configurations are now strictly resolved per Gradle build variant:

```
app/src/
├── debug/
│   └── google-services.json   --> Project wastelink-27595 (Routes to Local Emulator)
├── staging/
│   └── google-services.json   --> Project wastelink-27595 (Remote Staging Backend)
└── release/
    └── google-services.json   --> Project wastelink-prod-2026 (Production Backend)
```

Generated Google Services resources verified:
- **`processDebugGoogleServices`**: `project_id: wastelink-27595`, App ID: `1:161365383144:android:3448bf6fac95de1cc4f65d`
- **`processStagingGoogleServices`**: `project_id: wastelink-27595`, App ID: `1:161365383144:android:3448bf6fac95de1cc4f65d`
- **`processReleaseGoogleServices`**: `project_id: wastelink-prod-2026`, App ID: `1:992880418114:android:1e663fb7d502414860bcdb`

---

## 4. Runtime Startup & Environment Gate Hardening

1. **`WasteLinkApplication.onCreate()`**:
   - Injects `FirebaseAuth` and `FirebaseFirestore`.
   - Validates that `actualProjectId == BuildConfig.FIREBASE_PROJECT_ID`. Throws `IllegalStateException` on mismatch.
   - Dispatches environment configuration to `FirebaseEnvironmentManager.configureFirebase(...)`.
2. **`FirebaseEnvironmentManager`**:
   - `LOCAL_EMULATOR`: Connects to `10.0.2.2:9099` (Auth) and `10.0.2.2:8080` (Firestore).
   - `REMOTE_STAGING`: Enforces that local emulator is NOT connected; connects to remote `wastelink-27595`.
   - `PRODUCTION`: Enforces that local emulator is NOT connected; fails closed unless `isProductionActivated()` is true.
3. **Automated Test Coverage**:
   - `FirebaseEnvironmentManagerTest` added in `:core:firebase` verifying default environment, gate transitions, and strict project validation.

---

## 5. Security Rules Verification & Staging Deployment

### 5.1. Automated Rules Suite (`npm test` in `firestore-rules-tests`)
- **Execution**: Run with `@firebase/rules-unit-testing` against local emulator.
- **Suites**: 9 test suites
- **Result**: **32 / 32 PASSED (100%)**
- **Coverage**:
  - User profile creation & immutable role protection (only `RESIDENT` / `COLLECTOR`).
  - Active suspension write denial.
  - Collector KYC atomic write batch validation.
  - Collector mission transitions & proof-of-collection photo enforcement.
  - Administrator dispatch operations.
  - Live location privacy & active request verification.
  - Disputes & append-only admin notes.
  - Audit log actor impersonation prevention.
  - Catch-all default-deny on unrecognized collections.

### 5.2. Live Staging Deployment
Executed command:
```powershell
npx firebase-tools deploy --only firestore:rules,firestore:indexes --project staging
```
Output:
```
=== Deploying to 'wastelink-27595'...
i  cloud.firestore: checking firestore.rules for compilation errors...
+  cloud.firestore: rules file firestore.rules compiled successfully
i  firestore: uploading rules firestore.rules...
i  firestore: deploying indexes...
+  firestore: deployed indexes in firestore.indexes.json successfully for (default) database
+  firestore: released rules firestore.rules to cloud.firestore
+  Deploy complete!
```

---

## 6. Live Staging Registration Verification

A dedicated verification run was conducted against the live remote `wastelink-27595` project using a controlled test account (`staging.verification.*@wastelink.co.tz`):

```
[STAGING VERIFY] Target Project: wastelink-27595
[STAGING VERIFY] API Key: AIzaSy...ucA8

1. Creating test resident in Firebase Auth
   ✓ Firebase Auth account created. UID: fOHzNQZlsqPtoK7sxU35CBuD5CI3

2. Requesting email verification
   ✓ Verification email request accepted by Firebase Identity Toolkit.

3. Verifying unauthenticated Firestore write is BLOCKED
   ✓ Unauthenticated Firestore write correctly rejected (HTTP 403 PERMISSION_DENIED).

4. Creating Firestore Resident profile as authenticated owner
   ✓ Resident profile created in Firestore: /users/fOHzNQZlsqPtoK7sxU35CBuD5CI3

5. Verifying authenticated owner can read profile
   ✓ Profile retrieved successfully. Role: RESIDENT, Status: ACTIVE

6. Verifying unauthenticated read is BLOCKED
   ✓ Unauthenticated profile read correctly denied (HTTP 403 PERMISSION_DENIED).

7. Testing Password Recovery request
   ✓ Password reset request accepted.

8. Testing Sign-In with credentials
   ✓ Sign-in succeeded. Token received for UID: fOHzNQZlsqPtoK7sxU35CBuD5CI3

9. Cleaning up test auth account
   ✓ Test Auth account deleted cleanly.

========================================
🎉 ALL STAGING LIVE VERIFICATION CHECKS PASSED!
========================================
```

---

## 7. Build Verification

| Task | Scope | Duration | Result | Output Artifact |
| :--- | :--- | :--- | :--- | :--- |
| `testDebugUnitTest` | Full multi-module unit test suite | 16s | **BUILD SUCCESSFUL (100% PASSED)** | All 165 tasks completed |
| `:app:assembleDebug` | Debug build variant | 3m 14s | **BUILD SUCCESSFUL** | `app-debug.apk` (24.68 MB) |
| `:app:assembleStaging`| Staging build variant | 3m 14s | **BUILD SUCCESSFUL** | `app-staging.apk` (24.45 MB) |
| `:app:assembleRelease`| Release build variant | 3m 14s | **BUILD SUCCESSFUL** | `app-release-unsigned.apk` (16.5 MB)|

---

## 8. Status of Remaining Integrations

1. **Firebase Cloud Messaging (FCM)**:
   - Implemented `WasteLinkMessagingService` extending `FirebaseMessagingService`.
   - Token refresh persists to `/users/{uid}/fcmToken`.
   - Notification channel `WasteLink_Notifications` configured with default system alerts.
2. **Firebase Storage**:
   - Client dependency and Hilt binding `FirebaseStorage` configured.
   - Storage bucket `wastelink-27595.firebasestorage.app` remains on the free tier until photo upload stages begin. No billing account attached.
3. **Cloud Functions**:
   - Confirmed: no `functions/` implementation exists in the repository. Privileged roles and KYC status transitions are enforced strictly through Firestore security rules.
4. **App Check**:
   - Ready for integration with Play Integrity once the app is published to internal testing on Google Play.

---

## 9. Action Items for Project Owner

To prepare the empty **Production** project (`wastelink-prod-2026`) when launch readiness is required:

1. **Enable Cloud Firestore API**:
   Open [Google Cloud Console for wastelink-prod-2026](https://console.developers.google.com/apis/api/firestore.googleapis.com/overview?project=wastelink-prod-2026) and click **Enable**.
2. **Create Production Database**:
   In [Firebase Console](https://console.firebase.google.com/project/wastelink-prod-2026/firestore), create the `(default)` database in region `africa-south1` (or run `npx firebase-tools firestore:databases:create "(default)" --location africa-south1 --project prod`).
3. **Deploy Security Rules to Production**:
   Run:
   ```powershell
   npx firebase-tools deploy --only firestore:rules,firestore:indexes --project prod
   ```
4. **Enable Email/Password Provider**:
   In [Firebase Console > Authentication](https://console.firebase.google.com/project/wastelink-prod-2026/authentication/providers), enable "Email/Password".

---

## 10. Conclusion

The Staging environment is operational, secure, and ready for physical device testing with `app-staging.apk`. All changes have been verified and documented.
