# WASTELINK — DEVOPS & TECHNICAL LEAD HANDOFF REPORT

**To:** Lead DevOps / Technical Director  
**From:** Antigravity Mobile & Architecture Pair Programming Team  
**Date:** November 5, 2026  
**Subject:** Firebase Multi-Environment Resolution, Live Mobile Validation, and Module 1 Security Baseline Handoff  
**Branch:** `rebuild/modular-workspace` | **Commit:** `aacc84e`  
**Status:** **RESOLVED & VERIFIED LIVE ON PHYSICAL HARDWARE**

---

## 1. Executive Summary

This report documents the investigation, root-cause remediation, security audit, and live on-device verification of the Firebase authentication and Firestore infrastructure for WasteLink.

Following on-device testing on a physical Android handset (`Device ID: 099176437J003629`), the previous blocking defect:
> `Registration failed — An internal error has occurred. [API key not valid. Please pass a valid API key.]`

has been **completely resolved**. 

End-to-end functionality was verified directly by the user on the physical phone:
- **Resident Account Registration**: Succeeded without API key rejection.
- **Account Creation & Firestore Sync**: Resident profile successfully mapped and written to `/users/{uid}`.
- **Email Verification Flow**: Triggered and delivered as expected.
- **Password Reset / Forgot Password Flow**: Outbound password recovery email dispatched and received successfully.

The active codebase on `rebuild/modular-workspace` has passed all local automated unit tests and Firestore emulator security suites (32/32 tests passed), with zero regressions to the accepted Module 1 authentication foundation.

---

## 2. Root Cause Analysis (RCA)

| Dimension | Finding |
| :--- | :--- |
| **Defective State** | The committed `app/google-services.json` referenced project `wastelink-4791b` (Project # `1051515222019`) with API key `AIzaSy...7e4`. |
| **Root Cause** | Google Cloud / Firebase console credentials for project `wastelink-4791b` had been regenerated, revoked, or deleted, causing Google Identity Toolkit to return `HTTP 400 API_KEY_INVALID` during runtime client registration. |
| **Secondary Weakness** | A single `app/google-services.json` at the root of the app module acted as a global fallback, obscuring environment boundaries and conflating debug, staging, and production configurations. |
| **Resolution** | Full migration to active project `wastelink-27595` (`wastelinktz@gmail.com`), elimination of root `app/google-services.json`, introduction of strict flavor source sets, and runtime project-ID assertion in `WasteLinkApplication`. |

---

## 3. Environment Architecture & Source Set Topology

We have enforced a strict three-tier environment separation. The root fallback file `app/google-services.json` has been deleted to ensure that any variant without an explicit configuration fails at build time rather than falling back to an unverified configuration.

```
app/src/
├── debug/
│   └── google-services.json   --> wastelink-27595 (Project # 161365383144)
│                                  Local emulators (Auth 9099, Firestore 8080)
├── staging/
│   └── google-services.json --> wastelink-27595 (Project # 161365383144)
│                                  Live remote Staging endpoints
└── release/
    └── google-services.json --> wastelink-prod-2026 (Project # 992880418114)
                                   Production isolated; fail-closed gate
```

### Build Variant Configuration (`app/build.gradle.kts`)

```kotlin
buildTypes {
    debug {
        buildConfigField("String", "FIREBASE_ENV", "\"EMULATOR\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"wastelink-27595\"")
    }
    create("staging") {
        initWith(getByName("debug"))
        buildConfigField("String", "FIREBASE_ENV", "\"STAGING\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"wastelink-27595\"")
        matchingFallbacks += listOf("debug")
    }
    release {
        buildConfigField("String", "FIREBASE_ENV", "\"PRODUCTION\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"wastelink-prod-2026\"")
        isMinifyEnabled = false
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
}
```

### Runtime Environment Enforcement (`FirebaseEnvironmentManager.kt`)

At application boot (`WasteLinkApplication.kt`):
1. The runtime project ID extracted from `FirebaseOptions.projectId` is compared against `BuildConfig.FIREBASE_PROJECT_ID`. A mismatch immediately aborts startup with an `IllegalStateException`.
2. In `STAGING`, local emulators are bypassed, and live Google endpoints for `wastelink-27595` are engaged.
3. In `PRODUCTION`, `FirebaseEnvironmentManager` operates in a **fail-closed** mode: initialization throws an error unless explicit runtime activation (`setProductionActivated(true)`) has been invoked.

---

## 4. Firestore Security Rules & Remote Deployment

Security rules were verified against local emulators using the automated Mocha test suite before remote deployment:

- **Rule Tests**: 32 test cases across 9 suites in `firestore-rules-tests`.
- **Test Result**: **32 / 32 Passed (100%)**.
- **Remote Staging Deployment**:
  ```powershell
  npx firebase-tools deploy --only firestore:rules,firestore:indexes --project staging
  ```
- **Live Permission Gate Probe**:
  - Unauthenticated GET request to `https://firestore.googleapis.com/v1/projects/wastelink-27595/databases/(default)/documents/users/{uid}` returned:
    `HTTP 403 PERMISSION_DENIED`
  - Confirmed the database is secure and locked down against unauthenticated snooping.

---

## 5. Live Physical Mobile Validation Log

| Step | Action / Flow | Target / Component | Result | Status |
| :---: | :--- | :--- | :--- | :---: |
| 1 | APK Streaming & Install | `Device 099176437J003629` via ADB | `Performing Streamed Install -> Success` | **PASS** |
| 2 | Application Startup | `MainActivity` launch | `FirebaseEnvManager: Configuring Firebase for REMOTE_STAGING (remote endpoints active: wastelink-27595)` | **PASS** |
| 3 | Resident Registration | UI registration form submit | Firebase Auth account provisioned; Firestore `/users/{uid}` created | **PASS** |
| 4 | Email Verification | Automatic verification trigger | Verification email successfully dispatched | **PASS** |
| 5 | Password Reset | Forgot password flow | Recovery email successfully dispatched and received | **PASS** |

---

## 6. Git Baseline & Preservation Compliance

To maintain strict compliance with team governance:
- **Legacy Baseline**: `legacy/original-app` remains pinned at commit `188b2a1021eedd94c720bbfe1ca93a4ba77363c4` (unmodified).
- **Module 1 Foundation**: Accepted foundation on `rebuild/foundation` preserved.
- **Active Workspace**: `rebuild/modular-workspace` at commit `aacc84e`. All working tree changes have been committed and pushed to `origin/rebuild/modular-workspace`.
- **Legacy Project Untouched**: Stale project `wastelink-4791b` was not modified or deleted, preserving audit history.

---

## 7. Production Launch Checklist (DevOps Action Items)

The Production project has been registered as `wastelink-prod-2026` under `wastelinktz@gmail.com`. Because Google Cloud APIs require a one-click manual activation on first use for a new project, DevOps must complete the following steps prior to the production release:

1. **Enable Cloud Firestore API**:
   - Navigate to: [Google Cloud Console — Firestore API for wastelink-prod-2026](https://console.developers.google.com/apis/api/firestore.googleapis.com/overview?project=wastelink-prod-2026)
   - Click **Enable**.
2. **Create Default Firestore Database**:
   ```powershell
   npx firebase-tools firestore:databases:create "(default)" --location africa-south1 --project prod
   ```
3. **Deploy Production Rules and Indexes**:
   ```powershell
   npx firebase-tools deploy --only firestore:rules,firestore:indexes --project prod
   ```
4. **Enable Email/Password Authentication**:
   - Navigate to [Firebase Console > wastelink-prod-2026 > Authentication > Sign-in method](https://console.firebase.google.com/project/wastelink-prod-2026/authentication/providers).
   - Enable the **Email/Password** provider.

---

## 8. Handoff to Module 2

With the Firebase environment architecture stabilized, audited, and confirmed working on real mobile hardware, the authentication and infrastructure gate is formally closed. 

The development team is cleared to proceed with **Module 2: Resident Operations & Request Flow** (Pickup request creation, waste classification selection, GPS location tagging, and request lifecycle management).
