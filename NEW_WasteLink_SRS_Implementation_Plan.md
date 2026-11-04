# WasteLink Enterprise Platform
## Software Requirements Specification (SRS) & Modular Implementation Plan
### Version 2.0 | Classification: Internal Engineering

---

> **Document Control**
> | Field | Value |
> |---|---|
> | Project | WasteLink — Urban Waste Collection Platform |
> | Stack | Android (Jetpack Compose + Material 3), Firebase (Firestore, Auth, Functions) |
> | Target Roles | Resident (User), Dispatcher (Admin) |
> | Localization | English (EN) / Swahili (SW) |
> | Communication Layer | WhatsApp Deep-Link Integration |
> | Revision | 2.0 — Post-MVP Enterprise Transition |

---

## TABLE OF CONTENTS

1. [Introduction & Scope](#1-introduction--scope)
2. [System Stakeholders & User Classes](#2-system-stakeholders--user-classes)
3. [Functional Requirements — User Module](#3-functional-requirements--user-module)
4. [Functional Requirements — Admin Module](#4-functional-requirements--admin-module)
5. [UI/UX & Navigation Architecture](#5-uiux--navigation-architecture)
6. [Localization Architecture](#6-localization-architecture)
7. [Non-Functional Requirements](#7-non-functional-requirements)
8. [System Architecture & Data Flow](#8-system-architecture--data-flow)
9. [Database Schema](#9-database-schema)
10. [WhatsApp Integration Specification](#10-whatsapp-integration-specification)
11. [Implementation Plan — Phase Checklists](#11-implementation-plan--phase-checklists)

---

## 1. Introduction & Scope

### 1.1 Purpose

This Software Requirements Specification defines the complete functional, non-functional, architectural, and implementation requirements for the WasteLink platform — a mobile-first waste collection management system connecting urban residents with waste management authorities. This document governs engineering decisions from schema design to deployment pipeline and serves as the single source of truth for all feature development.

### 1.2 Product Scope

WasteLink operates as a two-sided marketplace platform:

- **Supply Side:** Waste collection authorities (Admins/Dispatchers) managing fleets, routing trucks, and updating collection statuses.
- **Demand Side:** Residents (Users) submitting geotagged, categorized waste pickup requests and tracking their lifecycle in real-time.

The platform is designed for urban municipality deployments in East Africa, with localization targeting English and Swahili as co-equal primary languages.

### 1.3 Definitions & Acronyms

| Term | Definition |
|---|---|
| WR | Waste Request — a single resident-initiated pickup request |
| OCC | Operations Control Center — the Admin dispatch interface |
| BNB | Bottom Navigation Bar — primary mobile navigation component |
| EN | English locale string catalog |
| SW | Swahili locale string catalog |
| GPS | Global Positioning System — hardware location provider |
| GC | Geocoding — translation of GPS coordinates to human-readable address |
| FCM | Firebase Cloud Messaging — push notification service |
| WA | WhatsApp — third-party communication channel |
| SM | State Machine — lifecycle enforcer for WR transitions |
| UID | Unique User Identifier assigned by Firebase Authentication |
| RID | Request Identifier — auto-generated Firestore document ID |

### 1.4 System Boundaries

**In Scope:**
- Native Android application (Resident-facing)
- Firebase-backed Admin dashboard (Web or Android)
- Firestore real-time data layer
- Firebase Authentication (Anonymous → Phone Auth migration)
- Google Maps Platform (Geocoding + Maps display)
- WhatsApp deep-link communication triggers
- English/Swahili runtime localization
- Request lifecycle state machine (PENDING → ASSIGNED → IN_TRANSIT → COMPLETED → CANCELLED)
- Firebase Cloud Functions for automated triggers
- FCM push notification delivery

**Out of Scope:**
- Custom in-app chat/messaging system
- iOS client (Phase 2 roadmap item)
- Payment gateway integration
- Driver/truck mobile app (separate module, future)
- Route optimization engine (Cloud Functions Phase 2)

---

## 2. System Stakeholders & User Classes

### 2.1 Resident (User Class: CLIENT)

**Description:** Urban household or business requiring waste removal. Interacts exclusively through the Android mobile application.

**Technical Profile:**
- Android device (API 26+, target API 34)
- Variable connectivity (2G/3G/4G/WiFi)
- Moderate to low digital literacy — UI must be intuitive
- Location services must be enabled for core functionality

**Primary Goals:**
- Submit a pickup request with minimal friction
- Track request status in real-time
- Contact support or dispatch via WhatsApp
- Switch UI language between English and Swahili

### 2.2 Dispatcher / Admin (User Class: ADMIN)

**Description:** Municipal waste authority operator managing incoming requests, assigning trucks, and monitoring fleet state. Accesses the OCC Dashboard.

**Technical Profile:**
- Desktop or Android tablet preferred
- Stable internet connection assumed
- High digital literacy — power-user interface acceptable

**Primary Goals:**
- Monitor live geospatial view of all active WRs
- Assign requests to specific trucks/operators
- Update WR state through the lifecycle
- View analytics on demand patterns
- Manage user accounts and reported abuse

### 2.3 System Administrator (User Class: SYSADMIN)

**Description:** Technical operator managing Firebase console, Cloud Functions, and platform health. Does not interact with the resident app.

---

## 3. Functional Requirements — User Module

### 3.1 Authentication & Identity

| Req ID | Requirement | Priority |
|---|---|---|
| UR-AUTH-001 | The system SHALL create an Anonymous Firebase session on first app launch without requiring user input. | MUST |
| UR-AUTH-002 | The system SHALL persist the anonymous session across app restarts using SharedPreferences/DataStore. | MUST |
| UR-AUTH-003 | The system SHALL present a Phone Number Verification screen when the user attempts to submit their first WR. | MUST |
| UR-AUTH-004 | The system SHALL send an OTP via Firebase Phone Auth SMS to the entered phone number. | MUST |
| UR-AUTH-005 | The system SHALL validate the OTP within a 60-second countdown window. | MUST |
| UR-AUTH-006 | The system SHALL display a resend OTP button after the countdown expires. | MUST |
| UR-AUTH-007 | The system SHALL migrate the anonymous UID to the verified phone-linked UID via `linkWithCredential()`. | MUST |
| UR-AUTH-008 | The system SHALL store the verified user profile in Firestore under `users/{uid}` upon successful Phone Auth. | MUST |
| UR-AUTH-009 | The system SHALL display an error message (localized EN/SW) for invalid phone number formats before OTP dispatch. | MUST |
| UR-AUTH-010 | The system SHALL support the E.164 phone number format and display a country code picker defaulting to +255 (Tanzania). | MUST |
| UR-AUTH-011 | The system SHALL block submission of a WR until phone verification is completed. | MUST |
| UR-AUTH-012 | The system SHALL store the user's preferred language (EN/SW) in the Firestore user profile. | MUST |
| UR-AUTH-013 | The system SHALL allow a verified user to view their phone number on the Profile screen. | SHOULD |
| UR-AUTH-014 | The system SHALL not expose the raw UID in any user-facing interface. | MUST |
| UR-AUTH-015 | The system SHALL implement Firebase App Check to prevent unauthorized API access. | MUST |
| UR-AUTH-016 | The system SHALL log authentication events (login, OTP sent, OTP verified, failure) to Firestore audit collection. | SHOULD |
| UR-AUTH-017 | The system SHALL handle Firebase Auth token expiry by silently refreshing the token in the background. | MUST |
| UR-AUTH-018 | The system SHALL display a sign-out option in the Profile/Settings screen. | MUST |
| UR-AUTH-019 | On sign-out, the system SHALL clear local DataStore user cache and navigate to the anonymous entry state. | MUST |
| UR-AUTH-020 | The system SHALL prevent duplicate accounts for the same phone number by enforcing Firebase's one-account-per-phone policy. | MUST |

### 3.2 Home Screen & Request Initiation

| Req ID | Requirement | Priority |
|---|---|---|
| UR-HOME-001 | The Home screen SHALL be the default landing screen after login and SHALL display as the first tab in the BNB. | MUST |
| UR-HOME-002 | The Home screen SHALL display the user's current active WR status if one exists. | MUST |
| UR-HOME-003 | The Home screen SHALL display a prominent CTA button labeled "Request Pickup" (EN) / "Omba Ukusanyaji" (SW). | MUST |
| UR-HOME-004 | The Home screen SHALL display a greeting with the user's registered first name if available. | SHOULD |
| UR-HOME-005 | The Home screen SHALL display a summary card showing pending WR count if none is active. | SHOULD |
| UR-HOME-006 | The Request Pickup flow SHALL open a bottom sheet dialog or new screen — not a system dialog. | MUST |
| UR-HOME-007 | The request creation screen SHALL present a Waste Category picker with the following options: Household, Construction, Hazardous, E-Waste, Organic, Recyclable. | MUST |
| UR-HOME-008 | Each waste category SHALL display a localized label and a distinct icon. | MUST |
| UR-HOME-009 | The request creation screen SHALL present a Volume Estimate picker with options: Small Bag (<20L), Large Bag (20–60L), Single Bin (60–120L), Multiple Bins (120L+). | MUST |
| UR-HOME-010 | Each volume option SHALL display a localized label and descriptive sub-label. | MUST |
| UR-HOME-011 | The request creation screen SHALL include an optional free-text Notes field (max 280 characters) for additional context. | SHOULD |
| UR-HOME-012 | The system SHALL automatically populate the location field using the device's GPS coordinates on screen open. | MUST |
| UR-HOME-013 | The GPS location SHALL be reverse-geocoded to a human-readable street address string using the Google Maps Geocoding API. | MUST |
| UR-HOME-014 | The resolved address SHALL be displayed to the user in a non-editable field labeled "Pickup Location" for visual confirmation. | MUST |
| UR-HOME-015 | The system SHALL display a map preview thumbnail of the detected location within the request form. | SHOULD |
| UR-HOME-016 | The system SHALL provide a "Adjust Location" button that opens an interactive map pin-drop interface if GPS auto-detection is inaccurate. | MUST |
| UR-HOME-017 | On the interactive map, the system SHALL display a draggable pin centered on the current GPS location. | MUST |
| UR-HOME-018 | As the user drags the pin, the address field SHALL update in real-time via reverse geocoding. | SHOULD |
| UR-HOME-019 | The geocoding call SHALL be debounced by 500ms to prevent excessive API calls during pin drag. | MUST |
| UR-HOME-020 | The system SHALL require GPS Location permission (`ACCESS_FINE_LOCATION`) before proceeding with request creation. | MUST |
| UR-HOME-021 | If location permission is denied, the system SHALL display a localized explanation dialog and redirect to app settings. | MUST |
| UR-HOME-022 | If GPS signal is unavailable, the system SHALL fallback to network-based location and indicate lower accuracy to the user. | MUST |
| UR-HOME-023 | The "Submit Request" button SHALL remain disabled until Category, Volume, and a valid Location are selected. | MUST |
| UR-HOME-024 | On submission, the system SHALL write the WR document to Firestore via a Kotlin Coroutine with structured error handling. | MUST |
| UR-HOME-025 | On successful submission, the system SHALL display a localized success Snackbar and navigate to the History tab. | MUST |
| UR-HOME-026 | On submission failure (network error), the system SHALL display a retry dialog without data loss. | MUST |
| UR-HOME-027 | The system SHALL prevent submitting a new WR while a PENDING or ASSIGNED WR already exists for the user. | MUST |
| UR-HOME-028 | The system SHALL display a localized error message if the user attempts to create a duplicate active request. | MUST |
| UR-HOME-029 | The newly submitted WR SHALL appear in the History screen immediately after creation via real-time listener. | MUST |
| UR-HOME-030 | The system SHALL record the submission timestamp as a Firestore server timestamp (not device clock). | MUST |

### 3.3 History Screen

| Req ID | Requirement | Priority |
|---|---|---|
| UR-HIST-001 | The History screen SHALL display a chronological list of all WRs submitted by the authenticated user. | MUST |
| UR-HIST-002 | The list SHALL be ordered by `createdAt` descending (most recent first). | MUST |
| UR-HIST-003 | Each WR list item SHALL display: Waste Category, Volume, Submission Date, Current Status badge, and a truncated Address. | MUST |
| UR-HIST-004 | Status badges SHALL be color-coded: PENDING (amber), ASSIGNED (blue), IN_TRANSIT (purple), COMPLETED (green), CANCELLED (grey). | MUST |
| UR-HIST-005 | Each status badge label SHALL be localized in EN/SW. | MUST |
| UR-HIST-006 | The History list SHALL update in real-time via a Firestore `snapshots()` listener — no manual refresh required. | MUST |
| UR-HIST-007 | Tapping a WR list item SHALL navigate to a WR Detail screen. | MUST |
| UR-HIST-008 | The WR Detail screen SHALL display all fields: Category, Volume, Notes, Address, GPS Coordinates, Status timeline, Assigned truck (if applicable), and Timestamps. | MUST |
| UR-HIST-009 | The WR Detail screen SHALL display a status timeline visualization showing completed and pending lifecycle steps. | SHOULD |
| UR-HIST-010 | The WR Detail screen SHALL include a "Contact via WhatsApp" button that fires the pre-filled WA deep link for that specific request. | MUST |
| UR-HIST-011 | The History screen SHALL display an empty-state illustration and localized message when no WRs exist. | MUST |
| UR-HIST-012 | The History list SHALL support infinite scroll pagination using Firestore cursor-based queries (`.startAfter()`). | SHOULD |
| UR-HIST-013 | The first page SHALL load the 20 most recent WRs; subsequent pages SHALL load 10 at a time. | SHOULD |
| UR-HIST-014 | The system SHALL display a loading skeleton animation while the initial History list loads. | SHOULD |
| UR-HIST-015 | COMPLETED WRs SHALL display a "Rate Collection" option (1–5 stars) if not yet rated. | COULD |
| UR-HIST-016 | The system SHALL write the rating to the WR document in Firestore for Admin analytics. | COULD |
| UR-HIST-017 | PENDING WRs SHALL display a "Cancel Request" button on the Detail screen. | MUST |
| UR-HIST-018 | On Cancel, the system SHALL set the WR status to CANCELLED and record the cancellation timestamp. | MUST |
| UR-HIST-019 | ASSIGNED or IN_TRANSIT WRs SHALL display the "Cancel Request" button as disabled with a tooltip explaining it cannot be cancelled once assigned. | MUST |
| UR-HIST-020 | The History screen header SHALL display a count of active (non-completed, non-cancelled) requests. | SHOULD |

### 3.4 Profile & Settings Screen

| Req ID | Requirement | Priority |
|---|---|---|
| UR-PROF-001 | The Profile screen SHALL display the user's phone number and registration date. | MUST |
| UR-PROF-002 | The Profile screen SHALL display an editable name field (first name, last name). | SHOULD |
| UR-PROF-003 | Name changes SHALL be persisted to `users/{uid}` in Firestore. | SHOULD |
| UR-PROF-004 | The Profile screen SHALL contain a Language Toggle switch clearly labeled "Language / Lugha" with EN and SW options. | MUST |
| UR-PROF-005 | Switching the language toggle SHALL immediately re-render all UI strings in the selected language without app restart. | MUST |
| UR-PROF-006 | The selected language SHALL be persisted to the Firestore user profile (`preferredLanguage` field). | MUST |
| UR-PROF-007 | The selected language SHALL also be saved to local DataStore for offline access on next launch. | MUST |
| UR-PROF-008 | The Profile screen SHALL display a "Sign Out" button at the bottom. | MUST |
| UR-PROF-009 | Sign Out SHALL prompt a localized confirmation dialog before executing. | MUST |
| UR-PROF-010 | The Profile screen SHALL include a "Contact Support via WhatsApp" button that fires the support WA deep link. | MUST |
| UR-PROF-011 | The Profile screen SHALL include links to "Privacy Policy" and "Terms of Service" (opening an in-app WebView). | MUST |
| UR-PROF-012 | The Profile screen SHALL display the current app version number (pulled from `BuildConfig.VERSION_NAME`). | SHOULD |
| UR-PROF-013 | The Profile screen SHALL include a notification preferences toggle for FCM push alerts. | SHOULD |
| UR-PROF-014 | FCM opt-out preference SHALL be stored in Firestore and respected by Cloud Functions. | SHOULD |

### 3.5 Notifications

| Req ID | Requirement | Priority |
|---|---|---|
| UR-NOTIF-001 | The system SHALL send an FCM push notification when a WR status changes from PENDING to ASSIGNED. | MUST |
| UR-NOTIF-002 | The system SHALL send an FCM push notification when a WR status changes to IN_TRANSIT. | MUST |
| UR-NOTIF-003 | The system SHALL send an FCM push notification when a WR status changes to COMPLETED. | MUST |
| UR-NOTIF-004 | All push notification content (title, body) SHALL be localized based on the user's `preferredLanguage` profile field. | MUST |
| UR-NOTIF-005 | Tapping a push notification SHALL deep-link directly to the relevant WR Detail screen. | MUST |
| UR-NOTIF-006 | The system SHALL request notification permission at first launch using the Android 13+ permission model. | MUST |
| UR-NOTIF-007 | The FCM token SHALL be registered in the user's Firestore document under `fcmTokens` (array, supports multi-device). | MUST |
| UR-NOTIF-008 | Stale FCM tokens SHALL be pruned by a Cloud Function on token refresh events. | SHOULD |

---

## 4. Functional Requirements — Admin Module

### 4.1 Admin Authentication

| Req ID | Requirement | Priority |
|---|---|---|
| AR-AUTH-001 | Admin accounts SHALL be created exclusively by a SysAdmin via Firebase Console — no self-registration. | MUST |
| AR-AUTH-002 | Admin login SHALL use Firebase Email/Password authentication. | MUST |
| AR-AUTH-003 | Admin role SHALL be defined by a Custom Claim (`admin: true`) set via Firebase Admin SDK or Cloud Function. | MUST |
| AR-AUTH-004 | Firestore Security Rules SHALL enforce that admin-only collections are inaccessible to users without the `admin` custom claim. | MUST |
| AR-AUTH-005 | Admin session SHALL expire after 8 hours of inactivity. | SHOULD |
| AR-AUTH-006 | Failed login attempts SHALL be rate-limited by Firebase Authentication's built-in abuse prevention. | MUST |
| AR-AUTH-007 | Admin credentials SHALL never be embedded in client-side code. | MUST |

### 4.2 Operations Control Center — Live Map View

| Req ID | Requirement | Priority |
|---|---|---|
| AR-MAP-001 | The Admin Dashboard SHALL feature a full-screen interactive map as a primary view. | MUST |
| AR-MAP-002 | The map SHALL render using the Google Maps Android SDK or Maps JavaScript SDK (for web). | MUST |
| AR-MAP-003 | Each active PENDING WR SHALL be displayed as a map pin/marker with a distinct amber icon. | MUST |
| AR-MAP-004 | Each ASSIGNED WR marker SHALL display a distinct blue icon. | MUST |
| AR-MAP-005 | Each IN_TRANSIT WR marker SHALL display a distinct purple icon. | MUST |
| AR-MAP-006 | Map markers SHALL update in real-time as Firestore data changes — no manual refresh. | MUST |
| AR-MAP-007 | Tapping a map marker SHALL open an info window or bottom sheet showing the WR summary. | MUST |
| AR-MAP-008 | The info window SHALL contain: Waste Category, Volume, Address, Submission Time, and action buttons. | MUST |
| AR-MAP-009 | The info window SHALL include an "Assign" button for PENDING WRs. | MUST |
| AR-MAP-010 | The map SHALL support clustering of markers when many WRs are spatially proximate. | SHOULD |
| AR-MAP-011 | The map SHALL default to a zoom level that shows the configured operational zone. | MUST |
| AR-MAP-012 | The Admin SHALL be able to filter visible markers by status (All / PENDING / ASSIGNED / IN_TRANSIT). | MUST |
| AR-MAP-013 | The Admin SHALL be able to filter markers by waste category. | SHOULD |
| AR-MAP-014 | The map legend SHALL be persistently displayed indicating marker color meanings. | MUST |

### 4.3 Active Request Queue

| Req ID | Requirement | Priority |
|---|---|---|
| AR-QUEUE-001 | The OCC SHALL provide a scrollable list view of all active WRs as an alternative to the map view. | MUST |
| AR-QUEUE-002 | The list SHALL be ordered by `createdAt` ascending (oldest first) to enforce FIFO dispatch fairness. | MUST |
| AR-QUEUE-003 | Each queue item SHALL display: WR ID, Category, Volume, Address, Time Since Submission, and Status. | MUST |
| AR-QUEUE-004 | The queue SHALL update in real-time via Firestore snapshots. | MUST |
| AR-QUEUE-005 | The queue SHALL support filtering by status, category, and date range. | MUST |
| AR-QUEUE-006 | The queue SHALL support keyword search by address string. | SHOULD |
| AR-QUEUE-007 | PENDING items older than a configurable threshold (default 2 hours) SHALL be highlighted in red. | SHOULD |
| AR-QUEUE-008 | Tapping a queue item SHALL navigate to the WR Management screen. | MUST |

### 4.4 Dispatch & State Machine

| Req ID | Requirement | Priority |
|---|---|---|
| AR-DISP-001 | The system SHALL enforce the following WR state machine: `PENDING → ASSIGNED → IN_TRANSIT → COMPLETED`. | MUST |
| AR-DISP-002 | The system SHALL allow cancellation from `PENDING` or `ASSIGNED` states only (by Admin or User respectively). | MUST |
| AR-DISP-003 | State transitions SHALL be validated by Firestore Security Rules — invalid transitions SHALL be rejected. | MUST |
| AR-DISP-004 | Assigning a WR SHALL require the Admin to select a Truck/Operator ID from a dropdown. | MUST |
| AR-DISP-005 | The Truck/Operator list SHALL be populated from the `trucks` Firestore collection. | MUST |
| AR-DISP-006 | On assignment, the system SHALL write `assignedTo`, `assignedAt`, `assignedTruckId` to the WR document. | MUST |
| AR-DISP-007 | On assignment, the WR status SHALL transition to `ASSIGNED`. | MUST |
| AR-DISP-008 | The Admin SHALL be able to mark a WR as `IN_TRANSIT` from the OCC. | MUST |
| AR-DISP-009 | The Admin SHALL be able to mark a WR as `COMPLETED` from the OCC. | MUST |
| AR-DISP-010 | On `COMPLETED`, the system SHALL record `completedAt` server timestamp. | MUST |
| AR-DISP-011 | Each state transition SHALL trigger the corresponding FCM notification to the resident. | MUST |
| AR-DISP-012 | FCM dispatch SHALL be executed via Firebase Cloud Function triggered by a Firestore `onUpdate` event. | MUST |
| AR-DISP-013 | The Admin SHALL be able to un-assign a WR from `ASSIGNED` back to `PENDING` (re-queue). | SHOULD |
| AR-DISP-014 | State transition history SHALL be logged in a sub-collection `wasteRequests/{rid}/transitions`. | SHOULD |
| AR-DISP-015 | The dispatch UI SHALL surface a warning if a truck already has more than a configurable max active assignments. | SHOULD |

### 4.5 User Management

| Req ID | Requirement | Priority |
|---|---|---|
| AR-USER-001 | The Admin SHALL access a Users list screen showing all registered residents. | MUST |
| AR-USER-002 | Each user row SHALL display: Phone Number, Name, Registration Date, and WR count. | MUST |
| AR-USER-003 | The Admin SHALL be able to view all WRs submitted by a specific user. | MUST |
| AR-USER-004 | The Admin SHALL be able to flag/block a user account (`blocked: true` in Firestore). | MUST |
| AR-USER-005 | Blocked users SHALL not be able to submit new WRs (enforced by Firestore Security Rules). | MUST |
| AR-USER-006 | The Admin SHALL be able to initiate a WhatsApp message to a resident directly from their profile. | SHOULD |
| AR-USER-007 | Bulk user export SHALL be available as a CSV download via a Cloud Function endpoint. | COULD |

### 4.6 Analytics Dashboard

| Req ID | Requirement | Priority |
|---|---|---|
| AR-ANLY-001 | The Admin analytics view SHALL display: Total WRs (all-time), WRs by Status (today/week/month), Average resolution time, Top waste categories by volume, and WRs by geographic zone. | MUST |
| AR-ANLY-002 | Analytics counters SHALL be derived from live Firestore aggregation queries (`count()`, `sum()`). | MUST |
| AR-ANLY-003 | A Cloud Function SHALL run nightly to aggregate daily statistics into a `dailyStats/{date}` collection for historical charting. | SHOULD |
| AR-ANLY-004 | The dashboard SHALL display a bar chart of daily WR counts for the last 30 days. | SHOULD |
| AR-ANLY-005 | The dashboard SHALL display a pie chart of WR distribution by waste category. | SHOULD |
| AR-ANLY-006 | The Analytics screen SHALL support date range filtering. | SHOULD |
| AR-ANLY-007 | Average collection time (PENDING → COMPLETED) SHALL be calculated and displayed. | SHOULD |
| AR-ANLY-008 | Peak request hours SHALL be visualized as a heat map or bar chart. | COULD |

### 4.7 Truck & Fleet Management

| Req ID | Requirement | Priority |
|---|---|---|
| AR-FLEET-001 | The Admin SHALL be able to add, edit, and deactivate truck records in the `trucks` collection. | MUST |
| AR-FLEET-002 | Each truck record SHALL contain: Truck ID, License Plate, Type (Compactor/Hazmat/Standard), Capacity (L), Operator Name, Status (Active/Inactive). | MUST |
| AR-FLEET-003 | Only Active trucks SHALL appear in the assignment dropdown. | MUST |
| AR-FLEET-004 | The fleet management screen SHALL display the current WR load per truck. | SHOULD |

---

## 5. UI/UX & Navigation Architecture

### 5.1 Bottom Navigation Bar (BNB) Specification

The BNB is the primary navigation affordance for the resident mobile app. It must be persistent across all non-modal screens and accessible with a single thumb reach.

```
┌─────────────────────────────────────────────────────────┐
│                                                         │
│                    [SCREEN CONTENT]                     │
│                                                         │
│                                                         │
├──────────┬──────────┬──────────┬──────────┬────────────┤
│   🏠      │   📋     │    🗺    │   🔔     │    👤      │
│  Home     │ History  │   Map    │  Alerts  │  Profile   │
│  Nyumbani │  Historia│  Ramani  │  Arifa   │  Wasifu    │
└──────────┴──────────┴──────────┴──────────┴────────────┘
```

| Tab Index | EN Label | SW Label | Icon | Route |
|---|---|---|---|---|
| 0 | Home | Nyumbani | `home_outlined` / `home_filled` | `/home` |
| 1 | History | Historia | `history` | `/history` |
| 2 | Map | Ramani | `map_outlined` / `map_filled` | `/map` |
| 3 | Alerts | Arifa | `notifications_outlined` / `notifications_filled` | `/notifications` |
| 4 | Profile | Wasifu | `person_outlined` / `person_filled` | `/profile` |

**BNB Behavioral Requirements:**

| Req ID | Requirement |
|---|---|
| UI-BNB-001 | The BNB SHALL use Material 3 `NavigationBar` and `NavigationBarItem` composables. |
| UI-BNB-002 | The selected tab SHALL use `selectedContentColor` = Material 3 Primary; unselected SHALL use `unselectedContentColor` = `onSurfaceVariant`. |
| UI-BNB-003 | The active tab icon SHALL switch from outlined to filled variant on selection. |
| UI-BNB-004 | The Alerts tab SHALL display a badge count when unread notifications exist. |
| UI-BNB-005 | The BNB SHALL be hidden when navigating to modal screens (e.g., request form, pin-drop map). |
| UI-BNB-006 | BNB transitions SHALL use slide-in animation for tab switches. |
| UI-BNB-007 | Tab label text SHALL switch language in real-time on locale change. |
| UI-BNB-008 | The BNB SHALL render above the system navigation bar with appropriate WindowInsets padding. |

### 5.2 Screen Hierarchy

```
App Entry
├── Auth Gate (ViewModel observes auth state)
│   ├── [Unauthenticated] → Phone Verification Screen
│   └── [Authenticated] → Main Scaffold
│       └── Main Scaffold (Persistent BNB)
│           ├── Tab 0: Home Screen
│           │   └── → Request Creation BottomSheet/Screen
│           │       └── → Pin-Drop Map Screen (modal)
│           ├── Tab 1: History Screen
│           │   └── → WR Detail Screen
│           ├── Tab 2: Live Map Screen (resident view)
│           ├── Tab 3: Notifications Screen
│           └── Tab 4: Profile & Settings Screen
│               └── → Privacy Policy WebView
│               └── → Terms of Service WebView
└── Admin Dashboard (separate Activity or NavGraph)
    ├── Map View Screen
    ├── Request Queue Screen
    │   └── → WR Management Screen
    ├── Analytics Screen
    ├── User Management Screen
    └── Fleet Management Screen
```

### 5.3 Material 3 Design Tokens

| Token | Value | Usage |
|---|---|---|
| `colorPrimary` | `#1B8A4E` (Green) | Primary actions, active BNB |
| `colorSecondary` | `#F4A025` (Amber) | PENDING status badges |
| `colorTertiary` | `#2563EB` (Blue) | ASSIGNED status badges |
| `colorError` | `#DC2626` (Red) | Error states, overdue WRs |
| `colorSurface` | `#FFFFFF` | Card backgrounds |
| `colorBackground` | `#F1F5F1` | Screen backgrounds |
| `typographyDisplayLarge` | Roboto / Noto Sans 57sp | Screen titles |
| `typographyHeadlineMedium` | Roboto / Noto Sans 28sp | Section headers |
| `typographyBodyLarge` | Roboto / Noto Sans 16sp | Body text |
| `typographyLabelSmall` | Roboto / Noto Sans 11sp | Status badges |

**Note:** The Noto Sans font family SHALL be used as it provides full Unicode coverage for Swahili characters.

### 5.4 Interaction Patterns

| Req ID | Requirement |
|---|---|
| UI-INTERACT-001 | Loading states SHALL use Material 3 `LinearProgressIndicator` at the top of the active screen. |
| UI-INTERACT-002 | Error states SHALL display a full-screen error composable with a retry CTA button. |
| UI-INTERACT-003 | Destructive actions (Cancel WR, Sign Out) SHALL require a confirmation `AlertDialog` before execution. |
| UI-INTERACT-004 | Success feedback SHALL use `SnackbarHostState` with a brief, localized message. |
| UI-INTERACT-005 | All tappable elements SHALL have a minimum touch target of 48x48dp per Material accessibility guidelines. |
| UI-INTERACT-006 | The app SHALL support TalkBack screen reader (content descriptions on all icons and images). |
| UI-INTERACT-007 | The app SHALL respect the system dark mode preference and provide a dark color scheme. |
| UI-INTERACT-008 | All input fields SHALL display validation errors inline below the field in `errorText`. |

---

## 6. Localization Architecture

### 6.1 String Resource Structure

The localization system SHALL follow Android's standard `strings.xml` resource pattern with a custom `LocalizationManager` wrapping it for runtime switching.

**File Structure:**
```
app/src/main/res/
├── values/
│   └── strings.xml          # English (default)
└── values-sw/
    └── strings.xml          # Swahili
```

**Runtime Override Architecture (for dynamic switching without restart):**
```kotlin
// LocalizationManager.kt
object LocalizationManager {
    fun getLocalizedContext(context: Context, languageCode: String): Context {
        val locale = Locale(languageCode)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}

// CompositionLocal provider in MainActivity
val LocalAppStrings = staticCompositionLocalOf { EnglishStrings }
```

### 6.2 Required String Keys (Sample Catalog)

| Key | English Value | Swahili Value |
|---|---|---|
| `nav_home` | Home | Nyumbani |
| `nav_history` | History | Historia |
| `nav_map` | Map | Ramani |
| `nav_alerts` | Alerts | Arifa |
| `nav_profile` | Profile | Wasifu |
| `btn_request_pickup` | Request Pickup | Omba Ukusanyaji |
| `btn_submit` | Submit | Wasilisha |
| `btn_cancel` | Cancel | Ghairi |
| `btn_contact_whatsapp` | Contact via WhatsApp | Wasiliana kupitia WhatsApp |
| `btn_sign_out` | Sign Out | Toka |
| `label_waste_category` | Waste Category | Aina ya Taka |
| `label_volume` | Volume Estimate | Kadirio la Kiasi |
| `label_pickup_location` | Pickup Location | Mahali pa Kukusanyia |
| `label_notes` | Additional Notes | Maelezo ya Ziada |
| `status_pending` | Pending | Inasubiri |
| `status_assigned` | Assigned | Imepewa |
| `status_in_transit` | In Transit | Inasafirishwa |
| `status_completed` | Completed | Imekamilika |
| `status_cancelled` | Cancelled | Imeghairiwa |
| `category_household` | Household | Nyumbani |
| `category_construction` | Construction | Ujenzi |
| `category_hazardous` | Hazardous | Hatari |
| `category_ewaste` | E-Waste | Taka za Kielektroniki |
| `category_organic` | Organic | Kikaboni |
| `category_recyclable` | Recyclable | Inayoweza Kuchakatwa |
| `volume_small` | Small Bag (<20L) | Mfuko Mdogo (<20L) |
| `volume_large` | Large Bag (20–60L) | Mfuko Mkubwa (20–60L) |
| `volume_bin` | Single Bin (60–120L) | Pipa Moja (60–120L) |
| `volume_multi` | Multiple Bins (120L+) | Mapipa Mengi (120L+) |
| `msg_success_request` | Request submitted successfully! | Ombi limewasilishwa! |
| `msg_location_denied` | Location permission required to submit a request. | Ruhusa ya mahali inahitajika kuwasilisha ombi. |
| `msg_no_history` | No requests yet. Tap "Request Pickup" to get started. | Bado hakuna maombi. Bonyeza "Omba Ukusanyaji" kuanza. |
| `lang_toggle_label` | Language / Lugha | Lugha / Language |
| `confirm_cancel_title` | Cancel Request? | Ghairi Ombi? |
| `confirm_cancel_body` | This action cannot be undone. | Hatua hii haiwezi kutenduliwa. |
| `confirm_signout_title` | Sign Out? | Toka? |

### 6.3 Localization Requirements

| Req ID | Requirement |
|---|---|
| LOC-001 | All user-facing strings SHALL be externalized to `strings.xml` — no hardcoded strings in Composables. |
| LOC-002 | The app SHALL support runtime locale switching without Activity restart. |
| LOC-003 | Locale preference SHALL be persisted in DataStore Preferences under key `user_language`. |
| LOC-004 | On app cold start, the system SHALL restore the persisted locale before rendering any UI frame. |
| LOC-005 | The Swahili translation SHALL be reviewed by a native speaker before production release. |
| LOC-006 | Date and time formatting SHALL use `java.time` with locale-aware formatters. |
| LOC-007 | The language switch SHALL take effect within a single composition recomposition cycle (<16ms). |
| LOC-008 | Plural string forms SHALL use `plurals.xml` resources for count-based strings (e.g., "1 request" vs "3 requests"). |
| LOC-009 | Right-to-left layout mirroring is NOT required for EN/SW but the architecture SHALL accommodate it for future Arabic support. |

---

## 7. Non-Functional Requirements

### 7.1 Performance

| Req ID | Requirement | Metric |
|---|---|---|
| NFR-PERF-001 | App cold start time SHALL not exceed 3 seconds on a mid-range device (≥2GB RAM, Snapdragon 665). | <3000ms |
| NFR-PERF-002 | GPS location acquisition SHALL complete within 5 seconds under clear sky conditions. | <5000ms |
| NFR-PERF-003 | Reverse geocoding API call SHALL complete within 2 seconds on 4G connectivity. | <2000ms |
| NFR-PERF-004 | Firestore WR write operation SHALL complete within 3 seconds on 3G connectivity. | <3000ms |
| NFR-PERF-005 | WhatsApp deep-link SHALL launch the WhatsApp app within 500ms of button tap. | <500ms |
| NFR-PERF-006 | History list initial load (20 items) SHALL complete within 2 seconds. | <2000ms |
| NFR-PERF-007 | Language switch re-render SHALL complete within one composition frame. | <16ms |
| NFR-PERF-008 | Admin map SHALL render up to 500 concurrent markers without frame drops below 30fps. | ≥30fps @ 500 markers |
| NFR-PERF-009 | FCM notification delivery SHALL target P99 latency under 10 seconds. | <10s P99 |
| NFR-PERF-010 | Geocoding debounce SHALL be 500ms during pin-drag to cap API calls. | 500ms |

### 7.2 Scalability

| Req ID | Requirement |
|---|---|
| NFR-SCALE-001 | The Firestore data model SHALL support horizontal scaling to 10 million WR documents without schema migration. |
| NFR-SCALE-002 | Firestore Security Rules SHALL be structured to avoid collection-level scans. |
| NFR-SCALE-003 | All Firestore queries SHALL use indexed fields — composite indexes defined in `firestore.indexes.json`. |
| NFR-SCALE-004 | Firebase Cloud Functions SHALL be deployed in the `us-central1` or `europe-west1` region nearest to the operational zone. |
| NFR-SCALE-005 | FCM batch dispatch for multi-device users SHALL use FCM multicast API. |
| NFR-SCALE-006 | The system SHALL support 10,000 concurrent Firestore listeners without degradation. |
| NFR-SCALE-007 | Cloud Functions SHALL implement exponential backoff retry logic for Firestore writes. |

### 7.3 Reliability & Availability

| Req ID | Requirement |
|---|---|
| NFR-REL-001 | The system SHALL target 99.9% uptime (Firebase SLA baseline). |
| NFR-REL-002 | The app SHALL handle offline mode gracefully — Firestore offline persistence SHALL be enabled. |
| NFR-REL-003 | WR documents written while offline SHALL sync to Firestore automatically on reconnection. |
| NFR-REL-004 | The app SHALL display a connectivity status banner when offline. |
| NFR-REL-005 | Cloud Functions SHALL have a maximum retry count of 3 for Pub/Sub triggered functions. |
| NFR-REL-006 | All Cloud Functions SHALL implement structured error logging to Cloud Logging. |

### 7.4 Security

| Req ID | Requirement |
|---|---|
| NFR-SEC-001 | Firestore Security Rules SHALL ensure users can only read/write their own WR documents. |
| NFR-SEC-002 | Admin-only Firestore operations SHALL verify the `admin: true` custom claim in Security Rules. |
| NFR-SEC-003 | GPS coordinates in WR documents SHALL only be readable by the document owner and admin users. |
| NFR-SEC-004 | The app SHALL use HTTPS exclusively — no HTTP network calls permitted. |
| NFR-SEC-005 | Firebase App Check (Play Integrity) SHALL be enforced to block non-genuine app clients. |
| NFR-SEC-006 | The app SHALL not log user PII (phone number, address) to Android Logcat in production builds. |
| NFR-SEC-007 | ProGuard/R8 obfuscation SHALL be enabled for release builds. |
| NFR-SEC-008 | API keys (Google Maps, Firebase) SHALL be stored in `secrets.properties` and injected via Gradle — never committed to version control. |
| NFR-SEC-009 | Firebase Admin SDK credentials SHALL only exist in Cloud Function environments — never client-side. |
| NFR-SEC-010 | User location data SHALL not be shared with third parties beyond Google Maps Platform for geocoding. |
| NFR-SEC-011 | WR documents SHALL not be publicly readable — no public Firestore rules. |
| NFR-SEC-012 | Input validation SHALL be performed both client-side (UI) and server-side (Security Rules / Cloud Functions). |

### 7.5 Maintainability

| Req ID | Requirement |
|---|---|
| NFR-MAINT-001 | The codebase SHALL follow MVVM + Repository pattern strictly. |
| NFR-MAINT-002 | Dependency injection SHALL be implemented using Hilt. |
| NFR-MAINT-003 | All network/Firestore operations SHALL return `Result<T>` sealed classes for consistent error handling. |
| NFR-MAINT-004 | Unit tests SHALL cover all ViewModel logic with ≥80% coverage. |
| NFR-MAINT-005 | UI tests SHALL use Compose testing APIs for all critical user flows. |
| NFR-MAINT-006 | Firestore data access SHALL be encapsulated in Repository interfaces — ViewModels SHALL NOT call Firestore directly. |

---

## 8. System Architecture & Data Flow

### 8.1 High-Level Architecture Diagram

```
┌──────────────────────────────────────────────────────────────────────┐
│                         RESIDENT DEVICE                              │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │          Android App (Jetpack Compose + Material 3)          │   │
│  │                                                              │   │
│  │  ┌─────────────┐   ┌─────────────┐   ┌────────────────┐   │   │
│  │  │    UI       │──▶│  ViewModel  │──▶│  Repository    │   │   │
│  │  │  (Composable│   │  (StateFlow)│   │  (Interface)   │   │   │
│  │  │   Screens)  │◀──│             │◀──│                │   │   │
│  │  └─────────────┘   └─────────────┘   └───────┬────────┘   │   │
│  │                                               │            │   │
│  │  ┌──────────────────────────────────────┐    │            │   │
│  │  │         Local DataStore              │    │            │   │
│  │  │  (Language Pref, User Cache)         │    │            │   │
│  │  └──────────────────────────────────────┘    │            │   │
│  └─────────────────────────────────────────────────────────────┘   │
└────────────────────────────────────────────────────────────────────┘
                                  │ HTTPS / Firebase SDK
                                  ▼
┌──────────────────────────────────────────────────────────────────────┐
│                     FIREBASE / GCP BACKEND                           │
│                                                                      │
│  ┌────────────────┐  ┌─────────────────┐  ┌──────────────────────┐ │
│  │ Firebase Auth  │  │    Firestore    │  │  Cloud Functions     │ │
│  │                │  │                 │  │                      │ │
│  │ ・Phone Auth   │  │ ・users/        │  │ ・onWRStatusUpdate   │ │
│  │ ・Anonymous   │  │ ・wasteRequests/│  │   (FCM trigger)      │ │
│  │ ・Custom Claims│  │ ・trucks/       │  │ ・dailyAggregation   │ │
│  │                │  │ ・dailyStats/   │  │ ・fcmTokenRefresh    │ │
│  └────────────────┘  └─────────────────┘  └──────────────────────┘ │
│                                                                      │
│  ┌────────────────┐  ┌─────────────────┐                           │
│  │   Firebase FCM │  │  Google Maps    │                           │
│  │   (Push Notif) │  │  Geocoding API  │                           │
│  └────────────────┘  └─────────────────┘                           │
└──────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌──────────────────────────────────────────────────────────────────────┐
│                        ADMIN / OCC                                   │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │    Admin Dashboard (Android Tablet or Web)                     │ │
│  │    Real-time Firestore listener → Map + Queue views            │ │
│  │    State mutation → Firestore write → Cloud Function trigger   │ │
│  └────────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────────┘
```

### 8.2 Waste Request — End-to-End Data Flow

```
Step 1: RESIDENT SUBMITS REQUEST
────────────────────────────────────────────────────────────────────
1. User opens Home screen → taps "Request Pickup"
2. App calls GPS LocationManager / FusedLocationProviderClient
3. GPS coordinates returned (lat, lng)
4. App calls Google Maps Geocoding API:
   GET https://maps.googleapis.com/maps/api/geocode/json?latlng={lat},{lng}&key={API_KEY}
5. Geocoding returns formatted address string
6. User selects Category + Volume, optionally adds Notes
7. User taps "Submit"
8. ViewModel calls WasteRequestRepository.createRequest(WasteRequest)
9. Repository writes to Firestore:
   Collection: wasteRequests
   Document ID: auto-generated (RID)
   Fields: {uid, category, volume, notes, address, geopoint, status: "PENDING",
            createdAt: serverTimestamp(), fcmToken}
10. Firestore confirms write (Result.Success)
11. ViewModel updates UI state → Success Snackbar displayed
12. Real-time listener on History screen picks up new document

Step 2: ADMIN OBSERVES REQUEST
────────────────────────────────────────────────────────────────────
13. Admin OCC maintains Firestore listener:
    db.collection("wasteRequests")
      .whereIn("status", ["PENDING","ASSIGNED","IN_TRANSIT"])
      .snapshots()
14. New PENDING WR document appears in Admin queue/map in real-time
15. Admin taps the WR marker / queue item
16. WR info sheet displays: Category, Volume, Address, Geo, Notes

Step 3: ADMIN DISPATCHES
────────────────────────────────────────────────────────────────────
17. Admin selects truck from dropdown and taps "Assign"
18. Repository writes state transition to Firestore:
    db.collection("wasteRequests").document(rid).update({
        status: "ASSIGNED",
        assignedTo: adminUid,
        assignedTruckId: truckId,
        assignedAt: serverTimestamp()
    })
19. Firestore Security Rules validate: admin custom claim present, 
    current status == "PENDING", new status == "ASSIGNED"
20. Write accepted → document updated

Step 4: CLOUD FUNCTION FIRES FCM NOTIFICATION
────────────────────────────────────────────────────────────────────
21. Cloud Function `onWRStatusUpdate` triggers on document update
22. Function reads previous status (PENDING) vs new status (ASSIGNED)
23. Function fetches user's FCM token(s) from users/{uid}/fcmTokens
24. Function reads user's preferredLanguage from users/{uid}
25. Function composes localized FCM payload:
    EN: { title: "Request Assigned", body: "Your waste request has been assigned to a truck." }
    SW: { title: "Ombi Limepewa", body: "Ombi lako la taka limepewa gari." }
26. Function dispatches FCM message(s) via Admin SDK

Step 5: RESIDENT RECEIVES NOTIFICATION
────────────────────────────────────────────────────────────────────
27. Android device receives FCM push notification
28. Notification displayed in system tray (localized)
29. User taps notification → deep-link opens WR Detail screen for that RID
30. Real-time Firestore listener on History screen already reflects new status
31. Status badge updated from PENDING (amber) → ASSIGNED (blue)

Step 6: COLLECTION & COMPLETION
────────────────────────────────────────────────────────────────────
32. Admin updates status: ASSIGNED → IN_TRANSIT → COMPLETED
33. Each transition: Firestore write → Cloud Function → FCM notification
34. On COMPLETED: completedAt server timestamp recorded
35. Resident's History shows COMPLETED (green badge)
36. Optional: Rating prompt displayed to resident
```

---

## 9. Database Schema

### 9.1 Firestore Collection: `users`

```
users/{uid}
├── uid: String                    # Firebase Auth UID
├── phoneNumber: String            # E.164 format (+255...)
├── firstName: String              # Optional
├── lastName: String               # Optional
├── preferredLanguage: String      # "en" | "sw"
├── fcmTokens: Array<String>       # Multi-device FCM tokens
├── blocked: Boolean               # Default: false
├── role: String                   # "user" | "admin"
├── createdAt: Timestamp           # Server timestamp
└── lastActiveAt: Timestamp        # Updated on session
```

### 9.2 Firestore Collection: `wasteRequests`

```
wasteRequests/{rid}
├── rid: String                    # Auto-generated document ID
├── uid: String                    # Submitting user UID (FK → users)
├── category: String               # "HOUSEHOLD"|"CONSTRUCTION"|"HAZARDOUS"|"EWASTE"|"ORGANIC"|"RECYCLABLE"
├── volume: String                 # "SMALL"|"LARGE"|"BIN"|"MULTI_BIN"
├── notes: String                  # Optional, max 280 chars
├── address: String                # Reverse-geocoded address string
├── geopoint: GeoPoint             # Firestore GeoPoint(lat, lng)
├── status: String                 # "PENDING"|"ASSIGNED"|"IN_TRANSIT"|"COMPLETED"|"CANCELLED"
├── createdAt: Timestamp           # Server timestamp on creation
├── assignedTo: String             # Admin UID (set on ASSIGNED)
├── assignedTruckId: String        # Truck ID (set on ASSIGNED)
├── assignedAt: Timestamp          # Set on ASSIGNED transition
├── inTransitAt: Timestamp         # Set on IN_TRANSIT transition
├── completedAt: Timestamp         # Set on COMPLETED transition
├── cancelledAt: Timestamp         # Set on CANCELLED transition
├── cancelledBy: String            # "user"|"admin"
└── rating: Number                 # 1–5, optional, set post-completion

wasteRequests/{rid}/transitions/{auto}
├── fromStatus: String
├── toStatus: String
├── performedBy: String            # UID
├── performedByRole: String        # "user"|"admin"
└── timestamp: Timestamp
```

### 9.3 Firestore Collection: `trucks`

```
trucks/{truckId}
├── truckId: String
├── licensePlate: String
├── type: String                   # "COMPACTOR"|"HAZMAT"|"STANDARD"|"FLATBED"
├── capacityLiters: Number
├── operatorName: String
├── operatorPhone: String
├── status: String                 # "ACTIVE"|"INACTIVE"|"MAINTENANCE"
└── createdAt: Timestamp
```

### 9.4 Firestore Collection: `dailyStats`

```
dailyStats/{YYYY-MM-DD}
├── date: String
├── totalRequests: Number
├── pendingCount: Number
├── assignedCount: Number
├── completedCount: Number
├── cancelledCount: Number
├── avgResolutionTimeMs: Number
├── byCategory: Map<String, Number>
└── generatedAt: Timestamp
```

### 9.5 Firestore Security Rules (Key Excerpts)

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    // Users: Only owner reads/writes their own document
    match /users/{uid} {
      allow read, write: if request.auth != null && request.auth.uid == uid;
      allow read: if request.auth.token.admin == true;
    }

    // Waste Requests: Owner reads own docs; Admin reads/writes all
    match /wasteRequests/{rid} {
      allow create: if request.auth != null
                   && request.resource.data.uid == request.auth.uid
                   && request.resource.data.status == 'PENDING'
                   && !exists(/databases/$(database)/documents/users/$(request.auth.uid))
                   || get(/databases/$(database)/documents/users/$(request.auth.uid)).data.blocked != true;

      allow read: if request.auth != null
                 && (resource.data.uid == request.auth.uid
                 || request.auth.token.admin == true);

      allow update: if request.auth.token.admin == true
                   && isValidStatusTransition(resource.data.status, request.resource.data.status);

      allow update: if request.auth != null
                   && request.auth.uid == resource.data.uid
                   && resource.data.status == 'PENDING'
                   && request.resource.data.status == 'CANCELLED';
    }

    // Admin-only collections
    match /trucks/{tid} {
      allow read, write: if request.auth.token.admin == true;
    }

    match /dailyStats/{date} {
      allow read: if request.auth.token.admin == true;
    }

    // Helper function
    function isValidStatusTransition(from, to) {
      return (from == 'PENDING' && to in ['ASSIGNED', 'CANCELLED'])
          || (from == 'ASSIGNED' && to in ['IN_TRANSIT', 'PENDING', 'CANCELLED'])
          || (from == 'IN_TRANSIT' && to == 'COMPLETED');
    }
  }
}
```

---

## 10. WhatsApp Integration Specification

### 10.1 Deep-Link Format

WhatsApp deep-links follow the format:
```
https://wa.me/{PHONE_NUMBER}?text={URL_ENCODED_MESSAGE}
```

The system SHALL use Tanzanian waste authority WhatsApp number format: `+255XXXXXXXXX`

### 10.2 Pre-filled Message Templates

**Support Contact (from Profile screen):**

```kotlin
// EN
val enSupportMsg = """
Hello WasteLink Support,
I need assistance with my account.
My registered phone: ${user.phoneNumber}
""".trimIndent()

// SW
val swSupportMsg = """
Habari WasteLink Support,
Ninahitaji msaada na akaunti yangu.
Simu yangu iliyosajiliwa: ${user.phoneNumber}
""".trimIndent()
```

**Request Follow-up (from WR Detail screen):**

```kotlin
// EN
fun buildWRFollowupMsg(wr: WasteRequest, lang: String): String {
    return if (lang == "sw") """
Habari,
Ninataka ufuatiliaji wa ombi langu la taka.
Nambari ya Ombi: ${wr.rid}
Aina ya Taka: ${wr.category.localizedSw}
Mahali: ${wr.address}
Hali ya Sasa: ${wr.status.localizedSw}
Tuma tarehe: ${wr.createdAt.toFormattedString("sw")}
    """.trimIndent() else """
Hello,
I would like to follow up on my waste collection request.
Request ID: ${wr.rid}
Waste Type: ${wr.category.localizedEn}
Location: ${wr.address}
Current Status: ${wr.status.localizedEn}
Submitted: ${wr.createdAt.toFormattedString("en")}
    """.trimIndent()
}
```

### 10.3 Deep-Link Launch Function

```kotlin
fun launchWhatsApp(context: Context, phone: String, message: String) {
    val encodedMessage = Uri.encode(message)
    val url = "https://wa.me/$phone?text=$encodedMessage"
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    } else {
        // WhatsApp not installed — open in browser
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(browserIntent)
    }
}
```

### 10.4 WhatsApp Requirements

| Req ID | Requirement |
|---|---|
| WA-001 | The WhatsApp deep-link SHALL resolve within 500ms of button tap. |
| WA-002 | The system SHALL NOT require WhatsApp to be installed — SHALL fallback to browser `wa.me` link. |
| WA-003 | Message templates SHALL be fully localized in EN and SW based on user preference. |
| WA-004 | The WR ID SHALL always be included in the request follow-up message for dispatcher reference. |
| WA-005 | Phone numbers used in deep-links SHALL be stored in a remote config value (Firebase Remote Config), not hardcoded. |
| WA-006 | The "Contact via WhatsApp" button SHALL be visible on the WR Detail screen for all statuses. |
| WA-007 | The "Contact Support via WhatsApp" button SHALL be in the Profile screen. |

---

## 11. Implementation Plan — Phase Checklists

---

### PHASE 1: Architecture, Localization Setup & Database Design

**Objective:** Establish the project scaffolding, Firestore schema, localization infrastructure, and CI/CD pipeline before any feature code is written.

#### 1.1 Project & Build Setup
- [ ] Create Android project in Android Studio Hedgehog+ with minimum SDK 26, target SDK 34
- [ ] Configure Kotlin 1.9+ and Kotlin Symbol Processing (KSP)
- [ ] Add all required Gradle dependencies: Compose BOM, Material 3, Firebase BoM, Hilt, Navigation Compose, DataStore, Coil, Google Maps Compose, Play Services Location
- [ ] Configure `secrets.properties` and `local.properties` for API key injection via Gradle `BuildConfig`
- [ ] Set up `google-services.json` from Firebase Console for `debug` and `release` build variants
- [ ] Enable ProGuard/R8 for `release` build with Firebase keep rules
- [ ] Configure `firestore.rules` file and `firestore.indexes.json` in project root
- [ ] Set up Firebase CLI and project linking (`firebase use --alias production`)

#### 1.2 Firebase Backend Setup
- [ ] Create Firebase project with Firestore in Native mode (multi-region if budget allows)
- [ ] Enable Firebase Authentication → Anonymous provider
- [ ] Enable Firebase Authentication → Phone provider
- [ ] Enable Firebase Cloud Messaging
- [ ] Enable Firebase App Check with Play Integrity provider
- [ ] Set up Firestore Security Rules from Section 9.5 above
- [ ] Deploy initial Security Rules via Firebase CLI
- [ ] Create composite Firestore indexes: `wasteRequests` by `(uid, createdAt desc)`, `(status, createdAt asc)`
- [ ] Create admin service account for Cloud Functions SDK
- [ ] Initialize Cloud Functions project with TypeScript template
- [ ] Configure Firebase Emulator Suite for local development

#### 1.3 Database Schema Implementation
- [ ] Define Kotlin data classes: `User`, `WasteRequest`, `Truck`, `DailyStats`, `StatusTransition`
- [ ] Annotate data classes with `@Keep` for ProGuard
- [ ] Define `WasteCategory` enum with EN and SW label fields
- [ ] Define `VolumeEstimate` enum with EN and SW label fields
- [ ] Define `RequestStatus` enum with `validNextStates()` function for state machine enforcement
- [ ] Implement Firestore serialization using `@DocumentId` and `@ServerTimestamp` annotations
- [ ] Write seed data script for `trucks` collection via Admin SDK

#### 1.4 Localization Infrastructure
- [ ] Create `res/values/strings.xml` with all keys from Section 6.2
- [ ] Create `res/values-sw/strings.xml` with all Swahili translations
- [ ] Create `res/values/plurals.xml` for count-based strings
- [ ] Create `res/values-sw/plurals.xml`
- [ ] Implement `LocalizationManager` singleton with `getLocalizedContext()`
- [ ] Implement `AppLocale` sealed class: `English`, `Swahili`
- [ ] Implement `LanguageViewModel` with `StateFlow<AppLocale>` backed by DataStore
- [ ] Set up `CompositionLocalProvider` in `MainActivity` providing current locale
- [ ] Verify all Composable strings use `stringResource()` — zero hardcoded strings
- [ ] Create Espresso test verifying language switch updates BNB labels

#### 1.5 Architecture Setup
- [ ] Set up Hilt `@HiltAndroidApp` in `Application` class
- [ ] Define Repository interfaces: `AuthRepository`, `WasteRequestRepository`, `UserRepository`, `TruckRepository`
- [ ] Implement Firebase-backed concrete Repository classes
- [ ] Define `Result<T>` sealed class with `Success`, `Error`, `Loading` states
- [ ] Set up Hilt modules: `FirebaseModule`, `RepositoryModule`, `CoroutineModule`
- [ ] Configure Navigation Compose `NavHost` with all route definitions
- [ ] Implement `sealed class Screen(val route: String)` for all navigation routes
- [ ] Define `AppState` top-level StateFlow observable by `MainActivity`

---

### PHASE 2: Core User UI & Bottom Navigation

**Objective:** Build all resident-facing screens with complete localization, navigation, and theming — without backend integration.

#### 2.1 Material 3 Theme & Design System
- [ ] Define `WasteLinkColorScheme` with all color tokens from Section 5.3
- [ ] Define `WasteLinkTypography` using Noto Sans font family
- [ ] Create `WasteLinkTheme` composable with light and dark scheme support
- [ ] Add Noto Sans font files to `res/font/` and reference in `FontFamily`
- [ ] Define status color mapping: `RequestStatus → Color` for badge rendering
- [ ] Create reusable `StatusBadge(status: RequestStatus)` composable
- [ ] Create reusable `WasteCategoryIcon(category: WasteCategory)` composable
- [ ] Create reusable `PrimaryButton`, `OutlinedButton`, `DangerButton` composables
- [ ] Create reusable `LoadingScreen`, `ErrorScreen(onRetry: () -> Unit)` composables
- [ ] Create reusable `EmptyStateScreen(messageRes: Int, illustrationRes: Int)` composable

#### 2.2 Bottom Navigation Bar
- [ ] Implement `WasteLinkBNB` composable using Material 3 `NavigationBar`
- [ ] Define `BnbTab` data class with route, EN/SW labels, outlined and filled icon resources
- [ ] Implement active/inactive icon swap logic on tab selection
- [ ] Implement notification badge overlay on Alerts tab using `BadgedBox`
- [ ] Observe unread notification count from `NotificationViewModel` for badge
- [ ] Implement BNB visibility toggle: hidden on modal screens via `backstackEntry` observation
- [ ] Apply `WindowInsets.navigationBars` padding to BNB container
- [ ] Write Compose UI test: all 5 tabs navigable, correct active icon state

#### 2.3 Main Scaffold
- [ ] Implement `MainScaffold` composable with `Scaffold`, `NavigationBar`, and `NavHost`
- [ ] Pass `navController` as single source of truth to all composables
- [ ] Implement scroll-to-top on re-tapping the active BNB tab
- [ ] Handle system back press to pop backstack correctly

#### 2.4 Home Screen
- [ ] Implement `HomeScreen` composable with greeting, active WR card, and CTA button
- [ ] Implement `ActiveRequestCard` composable showing current WR status with color-coded border
- [ ] Implement "Request Pickup" FAB / CTA button navigating to request creation
- [ ] Implement `RequestCreationSheet` as a `ModalBottomSheet` or full-screen composable
- [ ] Implement `WasteCategoryGrid` — 6-item grid with icon + localized label per category
- [ ] Implement `VolumePickerRow` — horizontal scrollable chip row with 4 options
- [ ] Implement `NotesTextField` with 280-char counter
- [ ] Implement `LocationDisplayCard` with address string, accuracy indicator, and "Adjust" button
- [ ] Implement shimmer loading placeholder for location detection

#### 2.5 History Screen
- [ ] Implement `HistoryScreen` composable with `LazyColumn` of `WRListItem`
- [ ] Implement `WRListItem` composable: category icon, address, date, status badge
- [ ] Implement `EmptyHistoryState` with illustration
- [ ] Implement `WRDetailScreen` composable with all fields from UR-HIST-008
- [ ] Implement `StatusTimeline` composable showing lifecycle steps
- [ ] Implement "Cancel Request" button with `AlertDialog` confirmation

#### 2.6 Profile & Settings Screen
- [ ] Implement `ProfileScreen` composable with user info section
- [ ] Implement `LanguageToggle` composable using `Switch` or `SegmentedButton`
- [ ] Wire toggle to `LanguageViewModel` → DataStore persistence
- [ ] Implement "Sign Out" button with confirmation dialog
- [ ] Implement "Contact Support via WhatsApp" button
- [ ] Implement "Privacy Policy" and "Terms of Service" `WebViewScreen`

#### 2.7 Live Map Screen (Resident)
- [ ] Implement `ResidentMapScreen` composable with Google Maps Compose
- [ ] Display user's current location on the map
- [ ] Display their active WR marker on the map if one exists
- [ ] Implement `PinDropMapScreen` — full-screen interactive map for location adjustment
- [ ] Implement draggable pin with real-time address update (debounced 500ms)

#### 2.8 Notifications Screen
- [ ] Implement `NotificationsScreen` with `LazyColumn` of notification items
- [ ] Each item shows: icon, localized message, timestamp, read/unread indicator
- [ ] Mark-as-read on tap; clear-all button in top app bar

---

### PHASE 3: Request Workflow, Location & WhatsApp Integration

**Objective:** Connect all UI to live Firebase backend, implement GPS + geocoding pipeline, state transitions, and WhatsApp deep-links.

#### 3.1 Authentication Flow
- [ ] Implement `AuthViewModel` with `StateFlow<AuthState>` (Unauthenticated / Verifying / Authenticated)
- [ ] Implement `AuthGate` composable that routes based on `AuthState`
- [ ] Implement `PhoneVerificationScreen` with country code picker and phone input
- [ ] Implement OTP input screen with 6-digit code field and 60s countdown
- [ ] Wire `resendOTP` button to `AuthViewModel.resendCode()`
- [ ] Implement `linkWithCredential()` flow to merge anonymous + phone accounts
- [ ] Handle all Firebase Auth error codes with localized error messages
- [ ] Write integration test: full phone auth flow against Firebase Emulator

#### 3.2 Location Pipeline
- [ ] Add `ACCESS_FINE_LOCATION` permission to `AndroidManifest.xml`
- [ ] Implement `LocationRepository` wrapping `FusedLocationProviderClient`
- [ ] Implement Compose permission flow using `rememberPermissionState()` (Accompanist or compose-permissions)
- [ ] Implement permission rationale dialog and settings redirect
- [ ] Implement `getLastKnownLocation()` with fallback to `requestSingleUpdate()`
- [ ] Implement network-location fallback with accuracy indicator
- [ ] Implement `GeocodingRepository` calling Google Maps Geocoding REST API
- [ ] Parse geocoding JSON response and extract `formatted_address`
- [ ] Handle geocoding API errors: NO_RESULTS, REQUEST_DENIED, OVER_DAILY_LIMIT
- [ ] Implement 500ms debounce on pin-drag geocoding calls using `FlowDebounce`
- [ ] Wire location + geocoding pipeline to `RequestCreationViewModel`
- [ ] Display map thumbnail using Compose Google Maps with static camera position

#### 3.3 Waste Request CRUD
- [ ] Implement `WasteRequestRepository` with: `createRequest()`, `getUserRequests()`, `getRequestById()`, `cancelRequest()`
- [ ] Implement `RequestCreationViewModel` with form state validation
- [ ] Implement submit button enabled/disabled logic (all required fields populated)
- [ ] Implement Firestore write with server timestamp
- [ ] Implement duplicate-active-request guard before submission
- [ ] Implement Firestore real-time listener: `getUserRequests()` returns `Flow<List<WasteRequest>>`
- [ ] Wire History `LazyColumn` to the real-time Flow
- [ ] Implement `cancelRequest()` with optimistic UI update
- [ ] Implement offline write support (Firestore offline persistence already enabled)
- [ ] Display offline indicator banner when network unavailable

#### 3.4 FCM Push Notification Setup
- [ ] Implement `FirebaseMessagingService` override for token refresh and message handling
- [ ] Register FCM token in Firestore `users/{uid}/fcmTokens` on first login and token refresh
- [ ] Implement `NotificationHelper` to build and display `NotificationCompat.Builder` notifications
- [ ] Implement deep-link `PendingIntent` routing to the correct WR Detail screen
- [ ] Request `POST_NOTIFICATIONS` permission on Android 13+
- [ ] Handle app-in-foreground FCM delivery via `onMessageReceived()`

#### 3.5 Cloud Functions — FCM Dispatch
- [ ] Implement `onWRStatusUpdate` Cloud Function with `onDocumentUpdated` trigger
- [ ] Extract `uid`, `status` (before/after), `rid` from function event
- [ ] Fetch user document to get `fcmTokens` and `preferredLanguage`
- [ ] Build localized FCM payload based on `preferredLanguage`
- [ ] Dispatch FCM multicast message to all user tokens
- [ ] Handle token-not-registered errors by removing stale tokens from Firestore
- [ ] Write unit tests for Cloud Function with mocked Firestore Admin SDK
- [ ] Deploy Cloud Function to Firebase and run end-to-end test

#### 3.6 WhatsApp Integration
- [ ] Implement `WhatsAppLauncher` utility object with `launchWhatsApp(context, phone, message)` from Section 10.3
- [ ] Implement `buildSupportMessage(user, lang)` and `buildWRFollowupMessage(wr, lang)` template functions
- [ ] Load support WhatsApp number from Firebase Remote Config
- [ ] Wire "Contact via WhatsApp" buttons on WR Detail and Profile screens
- [ ] Write Espresso UI test: button tap launches Intent with correct `wa.me` URI

---

### PHASE 4: Admin Control Panel & Dispatch Logic

**Objective:** Build the Operations Control Center with live map, queue, dispatch workflow, and analytics.

#### 4.1 Admin Authentication
- [ ] Implement `AdminAuthViewModel` with email/password Firebase Auth
- [ ] Implement Admin login screen with email and password fields
- [ ] Validate `admin` custom claim on login; reject if claim absent
- [ ] Implement session expiry check (8 hours) with auto sign-out
- [ ] Add Admin `NavHost` separate from resident `NavHost`
- [ ] Implement Admin `HomeScreen` with navigation to Map, Queue, Analytics, Users, Fleet

#### 4.2 Admin Live Map
- [ ] Implement `AdminMapScreen` composable with Google Maps Compose full-screen
- [ ] Implement real-time Firestore listener on `wasteRequests` filtered by active statuses
- [ ] Map `RequestStatus → MarkerIcon` (amber/blue/purple custom marker bitmaps)
- [ ] Implement `MarkerInfoSheet` composable appearing as bottom sheet on marker tap
- [ ] Implement map marker clustering using Maps SDK clustering utility
- [ ] Implement status filter chip row above map
- [ ] Implement category filter bottom sheet

#### 4.3 Request Queue & WR Management
- [ ] Implement `AdminQueueScreen` with real-time Firestore `LazyColumn`
- [ ] Implement overdue item highlighting (>2 hours PENDING)
- [ ] Implement filter bar: status chips + date range picker
- [ ] Implement `WRManagementScreen` for dispatchers:
  - [ ] Display all WR fields
  - [ ] Implement truck assignment dropdown populated from Firestore `trucks` collection
  - [ ] Implement "Assign" button calling `WasteRequestRepository.assignRequest(rid, truckId, adminUid)`
  - [ ] Implement "Mark In Transit" button
  - [ ] Implement "Mark Completed" button
  - [ ] Each transition writes to Firestore + logs to `transitions` sub-collection

#### 4.4 State Machine Enforcement
- [ ] Implement `RequestStateMachine` class with `getAvailableTransitions(currentStatus: RequestStatus): List<RequestStatus>`
- [ ] Show only valid next-state action buttons in `WRManagementScreen`
- [ ] Implement Firestore transaction for state transition to prevent race conditions
- [ ] Test: concurrent admin updates do not result in invalid state
- [ ] Implement re-queue (ASSIGNED → PENDING) with reason field

#### 4.5 User Management Screen
- [ ] Implement `AdminUsersScreen` with paginated Firestore user list
- [ ] Implement search by phone number
- [ ] Implement `UserDetailScreen` with user info and their WR history
- [ ] Implement block/unblock user toggle writing `blocked` field to Firestore
- [ ] Implement WhatsApp deep-link to contact resident from Admin context

#### 4.6 Fleet Management Screen
- [ ] Implement `FleetScreen` with truck list
- [ ] Implement Add/Edit truck form (TruckId, plate, type, capacity, operator, status)
- [ ] Implement deactivate truck button
- [ ] Display active WR count per truck

#### 4.7 Analytics Dashboard
- [ ] Implement `AnalyticsScreen` with stat cards and charts
- [ ] Implement Firestore aggregation query for live counts by status
- [ ] Implement `dailyStats` collection reader for 30-day bar chart
- [ ] Integrate charting library (MPAndroidChart or Vico)
- [ ] Implement date range picker for analytics filtering
- [ ] Implement `dailyAggregation` Cloud Function on scheduled trigger (nightly)

---

### PHASE 5: Testing, QA, Optimization & Deployment

**Objective:** Validate all functionality, harden security, optimize performance, and deploy to production.

#### 5.1 Unit Testing
- [ ] Write unit tests for all ViewModels using `kotlinx-coroutines-test` and `turbine`
- [ ] Write unit tests for all Repository implementations against Firebase Emulator
- [ ] Write unit tests for `RequestStateMachine.getAvailableTransitions()`
- [ ] Write unit tests for `WhatsAppLauncher.buildSupportMessage()` and `buildWRFollowupMessage()`
- [ ] Write unit tests for `LocalizationManager`
- [ ] Write unit tests for all Cloud Functions (TypeScript Jest)
- [ ] Achieve ≥80% code coverage across ViewModel and Repository layers

#### 5.2 UI / Integration Testing
- [ ] Write Compose UI tests for: BNB navigation, language toggle, request form validation
- [ ] Write UI test: submit WR flow end-to-end against Firebase Emulator
- [ ] Write UI test: cancel WR from History screen
- [ ] Write UI test: WhatsApp button fires correct Intent
- [ ] Write UI test: empty state displays on History with no WRs
- [ ] Write UI test: status badge color correctness for each status
- [ ] Write Espresso test: GPS permission denial flow and retry behavior

#### 5.3 Security Hardening
- [ ] Audit Firestore Security Rules against all attack vectors:
  - [ ] Verify users cannot read other users' WRs
  - [ ] Verify users cannot perform admin state transitions
  - [ ] Verify blocked users cannot create WRs
  - [ ] Verify invalid status transitions are rejected by rules
- [ ] Enable Firebase App Check enforcement in Firebase Console
- [ ] Verify `secrets.properties` not committed to Git (`.gitignore` audit)
- [ ] Run `gcloud` security scanner on Cloud Functions
- [ ] Ensure all Logcat PII logging is removed from release build via `BuildConfig.DEBUG` guards

#### 5.4 Performance Optimization
- [ ] Profile app with Android Studio Profiler — fix any jank on History scroll
- [ ] Verify composition recomposition scope is minimal (use `derivedStateOf` where appropriate)
- [ ] Implement Coil image caching for map thumbnails
- [ ] Verify geocoding debounce prevents API overrun on pin-drag
- [ ] Run Firestore query explain on all active queries — confirm index usage
- [ ] Profile Admin map performance with 500 synthetic markers

#### 5.5 Localization QA
- [ ] Native Swahili speaker review of all SW strings
- [ ] Test all screens in Swahili locale for text overflow / truncation
- [ ] Test runtime language switch on all 5 BNB screens
- [ ] Verify Swahili strings persist correctly across app restarts
- [ ] Verify FCM notifications arrive in correct language per user preference

#### 5.6 Device & Compatibility Testing
- [ ] Test on Android 8.0 (API 26) — minimum SDK
- [ ] Test on Android 13 (API 33) — runtime permission model
- [ ] Test on Android 14 (API 34) — target SDK
- [ ] Test on low-end device (2GB RAM, Snapdragon 430)
- [ ] Test offline mode: submit WR offline, verify sync on reconnect
- [ ] Test with airplane mode mid-submission

#### 5.7 CI/CD Pipeline
- [ ] Set up GitHub Actions workflow for:
  - [ ] Run unit tests on every PR
  - [ ] Run lint checks on every PR
  - [ ] Build release APK on merge to `main`
  - [ ] Deploy Cloud Functions on merge to `main` (`firebase deploy --only functions`)
  - [ ] Deploy Firestore rules on merge to `main` (`firebase deploy --only firestore:rules`)
- [ ] Configure Firebase App Distribution for internal beta builds
- [ ] Configure release signing with keystore stored in GitHub Secrets

#### 5.8 Production Deployment
- [ ] Set Firebase Firestore to production mode (rules locked down)
- [ ] Enable Firebase App Check enforcement (remove debug mode)
- [ ] Configure Firebase Remote Config production values (WhatsApp numbers, feature flags)
- [ ] Set up Firebase Crashlytics for production crash reporting
- [ ] Set up Firebase Performance Monitoring
- [ ] Configure Firebase Alerts for error rate thresholds
- [ ] Create Google Play Store developer account
- [ ] Prepare Play Store listing: screenshots (EN and SW), description, privacy policy URL
- [ ] Submit release APK to Play Store internal testing track
- [ ] Promote to production after 48-hour internal test window

#### 5.9 Post-Launch Monitoring
- [ ] Set up Firebase Dashboard monitoring: DAU, crash-free sessions, WR throughput
- [ ] Set up BigQuery export for long-term analytics
- [ ] Schedule weekly review of `dailyStats` analytics
- [ ] Establish incident response runbook for Firestore outage
- [ ] Define KPIs: average PENDING → COMPLETED time target ≤4 hours

---

## Appendix A: Cloud Functions Manifest

| Function Name | Trigger | Description |
|---|---|---|
| `onWRStatusUpdate` | `onDocumentUpdated("wasteRequests/{rid}")` | Dispatches localized FCM push when WR status changes |
| `onUserCreate` | `onDocumentCreated("users/{uid}")` | Initializes default user fields; sends welcome notification |
| `dailyStatsAggregation` | `onSchedule("every 24 hours")` | Aggregates Firestore WR data into `dailyStats/{date}` |
| `pruneStaleFCMTokens` | `onSchedule("every 7 days")` | Removes expired FCM tokens from user documents |
| `onWRCancelled` | `onDocumentUpdated("wasteRequests/{rid}")` | Sends cancellation confirmation notification to user |
| `bigQueryExport` | `onSchedule("every day 02:00")` | Exports daily WR data to BigQuery for long-term analytics |

---

## Appendix B: Google Maps Platform Usage

| API | Usage | Billing Note |
|---|---|---|
| Maps SDK for Android | Resident location display, pin-drop, Admin OCC map | Per session |
| Geocoding API | GPS → address string conversion | Per request (debounced) |
| Places API | Optional: address autocomplete on manual input | Per session |
| Maps JavaScript API | Admin web dashboard (if web-based) | Per load |

---

## Appendix C: Dependency Reference

```kotlin
// build.gradle.kts (app)
dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    
    val firebaseBom = platform("com.google.firebase:firebase-bom:33.1.0")
    implementation(firebaseBom)
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    implementation("com.google.firebase:firebase-crashlytics-ktx")
    implementation("com.google.firebase:firebase-perf-ktx")
    
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.maps.android:maps-compose:6.1.0")
    
    implementation("com.google.dagger:hilt-android:2.51")
    ksp("com.google.dagger:hilt-compiler:2.51")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("app.cash.turbine:turbine:1.1.0")
    testImplementation("io.mockk:mockk:1.13.11")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("com.google.firebase:firebase-emulator-suite:20.4.0")
}
```

---

*Document End — WasteLink SRS v2.0*
*Prepared for Engineering Team — All sections actionable for immediate development sprint planning.*
