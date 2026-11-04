# WasteLink — Waste Collection Platform
## Software Requirements Specification (SRS) v1.0 & Modular Implementation Plan

**Document Version:** 1.0
**Date:** June 2026
**Classification:** Technical Specification — Internal
**Primary Audience:** Senior Engineers, Mobile Developers, Backend Architects, Product Owners
**Scope:** Android/iOS Mobile App (Residents) + Web Admin Dashboard (Dispatchers/Operators)

---

## TABLE OF CONTENTS

1. [System Overview](#1-system-overview)
2. [Recommended Tech Stack & Architecture Decisions](#2-recommended-tech-stack--architecture-decisions)
3. [Functional Requirements](#3-functional-requirements)
   - 3.1 User (Resident) Module
   - 3.2 Admin Dashboard Module
4. [UI/UX & Navigation Architecture](#4-uiux--navigation-architecture)
5. [Non-Functional Requirements](#5-non-functional-requirements)
6. [System Architecture & Data Flow](#6-system-architecture--data-flow)
7. [Database Schema](#7-database-schema)
8. [Modular Implementation Phase Plan](#8-modular-implementation-phase-plan)
9. [Appendix: Key Identifiers & Configuration Reference](#9-appendix-key-identifiers--configuration-reference)

---

## 1. SYSTEM OVERVIEW

**Product Name:** WasteLink *(Swahili working title: Usafi Connect)*
**Platform:** Mobile Application (Android-first, iOS secondary) + Web-based Admin Dashboard
**Target Region:** East Africa — primary locales English (en) and Swahili (sw)

### Problem Statement
Waste collection scheduling in urban East African markets is largely reactive, phone-driven, and has no real-time visibility for either residents or collection operators. Residents cannot signal demand, dispatchers cannot prioritize intelligently, and there is no auditable record of request lifecycles.

### Solution
A geo-tagged, state-tracked waste request platform that connects residents to municipal or private waste collection authorities. The mobile client handles demand-side submission with precise location capture. The admin dashboard manages supply-side dispatch, fleet assignment, and lifecycle visibility. All resident-to-operator communication is offloaded to WhatsApp to eliminate the cost and complexity of a custom chat infrastructure.

### User Roles

| Role | Access Surface | Core Capability |
|---|---|---|
| **Client (Resident)** | Mobile App (Android/iOS) | Submit geo-tagged requests, track status, reach support via WhatsApp |
| **Admin (Dispatcher/Operator)** | Web Dashboard + Mobile | View live map of requests, dispatch collection units, manage request states and user accounts, pull analytics |

---

## 2. RECOMMENDED TECH STACK & ARCHITECTURE DECISIONS

### 2.1 Mobile Application

| Concern | Decision | Rationale |
|---|---|---|
| Framework | **Flutter (Dart)** | Single codebase for Android and iOS. First-class `flutter_localizations` + `intl` support for EN/SW with compile-time ARB resolution. Native-quality 60fps performance on mid-range Android hardware. |
| State Management | **Riverpod** | Compile-time safety, no `BuildContext` dependency in business logic, scoped providers per feature module. Preferable to BLoC for this domain's complexity level. |
| Navigation | **GoRouter** | Declarative routing with deep-link support, shell route support for persistent Bottom Nav Bar, named routes for testability. |
| Maps | `google_maps_flutter` | Mature, well-supported, integrates directly with Google Geocoding API already in use server-side. |
| HTTP Client | `dio` | Interceptor support for auto-attaching JWT headers and silent token refresh on 401. |
| Local Persistence | `shared_preferences` (for locale/tokens) + `drift` (for offline request queue) | Lightweight for simple KV, typed SQL for structured offline data. |
| Push Notifications | `firebase_messaging` (FCM) | Cross-platform, reliable delivery, supports background/foreground and notification-data payloads. |
| Real-time Updates | `supabase_flutter` | WebSocket subscription to Postgres LISTEN/NOTIFY events; eliminates polling for request status changes on the client. |
| WhatsApp | `url_launcher` | Opens `wa.me` deep links externally — no API key required, no infrastructure overhead. |

### 2.2 Backend / API

| Concern | Decision | Rationale |
|---|---|---|
| Runtime | **Node.js v20 LTS + NestJS** | TypeScript-first, enforces modular architecture via dependency injection, decorators and guards align with REST security requirements. |
| Database | **PostgreSQL 16 + PostGIS extension** | Native geospatial query support (`ST_DWithin`, `ST_Distance`, `ST_AsGeoJSON`). `GEOGRAPHY(POINT, 4326)` column for pickup coordinates. |
| Real-time Layer | **Supabase Realtime** | Built on PostgreSQL LISTEN/NOTIFY; zero additional infrastructure. Admin dashboard and mobile client subscribe to filtered channels. |
| Auth | **JWT (Access + Refresh token pair)** with bcrypt hashing | Access token: 15-min expiry. Refresh token: 7-day expiry with rotation on each use. Separate admin token scope (`role: admin`). |
| File Storage | **Supabase Storage** or **Firebase Storage** | Waste photo uploads with public CDN-backed URLs stored in the `waste_requests.photo_urls` array column. |
| Geocoding | **Google Geocoding API (server-side)** | Reverse geocoding at submission time — coordinate → human-readable address string stored immutably with the request. |
| Remote Config | **Firebase Remote Config** | Store WhatsApp support number and other operator-configurable flags without a release cycle. |

### 2.3 Admin Dashboard

| Concern | Decision |
|---|---|
| Framework | React 18 + TypeScript + Vite |
| Map | Mapbox GL JS (or Google Maps JS API) |
| State / Data-fetching | React Query (TanStack) + Zustand |
| Charts | Recharts |
| Styling | Tailwind CSS |

---

## 3. FUNCTIONAL REQUIREMENTS

### 3.1 User (Resident) Module

---

#### FR-U-001 — Authentication & Account Management

- The system SHALL allow users to register with: full name, phone number (E.164 format, e.g., `+255712345678`), password, and preferred language (`en` or `sw`).
- Phone number SHALL be unique across the `users` table and serves as the primary login credential.
- The system SHALL implement JWT access token + refresh token authentication with silent background refresh.
- A "Forgot Password" flow SHALL deliver an OTP via SMS to the registered phone number, valid for 10 minutes.
- On first login after registration, users SHALL be prompted to confirm their preferred language if not set.
- The `fcm_token` SHALL be captured on every successful login and stored in `users.fcm_token` (updated, not duplicated) to ensure push delivery accuracy.

---

#### FR-U-002 — Waste Request Creation (Core Feature)

This is the highest-priority feature of the mobile app. Every sub-requirement here is critical-path.

**A. Geolocation Tagging**
- The app SHALL request `ACCESS_FINE_LOCATION` (Android) / `NSLocationWhenInUseUsageDescription` (iOS) on first request submission — not on app launch.
- GPS acquisition SHALL target ≤20 meters accuracy with a 10-second timeout. On timeout, the app SHALL fall back to network/cell-tower location and display a UI indicator that accuracy may be reduced.
- Upon capture, the app SHALL call the reverse-geocoding utility to resolve coordinates into a human-readable address string and display it to the user for confirmation prior to submission.
- The user SHALL be able to drag a map pin to correct an inaccurate GPS-derived location. On drag end, the address SHALL update via a second reverse-geocode call on the new coordinates.
- Coordinates SHALL be submitted to the API as `{ lat: number, lng: number }` and stored server-side as a PostGIS `GEOGRAPHY(POINT, 4326)` type — immutable after submission.

**B. Waste Classification**
- The user SHALL select a waste category from a fixed enum:

  | Enum Value | Display (EN) | Display (SW) |
  |---|---|---|
  | `HOUSEHOLD` | Household Waste | Taka za Nyumbani |
  | `CONSTRUCTION` | Construction Debris | Taka za Ujenzi |
  | `GARDEN` | Garden / Organic | Taka za Bustani |
  | `ELECTRONIC` | E-Waste / Electronics | Taka za Elektroniki |
  | `HAZARDOUS` | Hazardous Materials | Vifaa Hatari |
  | `BULK_ITEMS` | Bulk / Large Items | Vitu Vikubwa |

- The user SHALL select an estimated volume tier:

  | Enum Value | Display (EN) | Description |
  |---|---|---|
  | `SMALL_BAG` | Small Bag | < 20 liters |
  | `STANDARD_BIN` | Standard Bin | 20–80 liters |
  | `LARGE_LOAD` | Large Load | 80–300 liters |
  | `TRUCK_LOAD` | Truck Load | > 300 liters |

**C. Supporting Data**
- The user MAY upload 1–3 photos of the waste via camera or gallery. Photos are optional but displayed to the admin for context.
- The user MAY add a text note of up to 280 characters.

**D. Submission & Confirmation**
- On "Submit", the app SHALL call `POST /api/v1/requests` and receive a `{ requestId, requestRef, status, address }` response.
- Initial `status` SHALL always be `PENDING`.
- A confirmation screen SHALL display the `requestRef` (e.g., `REQ-000234`) and an estimated response time if provided by system config.
- If the network is unavailable, the request SHALL be queued locally in the `drift` database and retried with exponential backoff when connectivity is restored.

---

#### FR-U-003 — Request History & Real-Time Tracking

- The History tab SHALL display a paginated list (20 items per page) of all the authenticated user's requests, ordered `created_at DESC`.
- Each list tile SHALL show: short `requestRef`, waste category icon, localized status label with color-coded badge, and formatted date.
- Tapping a list item SHALL navigate to the Request Detail screen, which displays:
  - Embedded Google Map with the pickup pin at `pickup_location` coordinates.
  - Resolved `pickup_address` string.
  - Waste category and volume tier.
  - Photos (horizontal scroll gallery, opens fullscreen on tap).
  - Vertical status timeline stepper (shows all transitions: `PENDING → ASSIGNED → IN_TRANSIT → COLLECTED`).
  - Estimated collection window if set by admin.
  - "Contact Support via WhatsApp" CTA button (see FR-U-005).
- The app SHALL maintain a Supabase Realtime subscription on channel `user_{userId}_requests`, filtered by the authenticated user's ID. Any status update pushed by the backend SHALL update the UI immediately without requiring manual refresh.

---

#### FR-U-004 — Language Switcher (EN / SW Localization)

- The Profile/Settings tab SHALL contain a dedicated "Language" setting rendering two selectable options: `English` and `Kiswahili`.
- Selected locale SHALL be persisted to `SharedPreferences` under the key `app_locale` AND synced to the backend via `PATCH /api/v1/users/me` with `{ preferred_language: "sw" }`.
- On locale change, the app SHALL call `ref.read(localeProvider.notifier).setLocale(Locale('sw'))`, which triggers `MaterialApp` to rebuild with the new locale — the navigation stack SHALL NOT be reset or disrupted.
- All user-facing text — button labels, form hints, error messages, status labels, empty states, push notification payloads, and WhatsApp pre-filled message templates — SHALL be sourced from locale-keyed ARB resource files. No string SHALL be hardcoded in widget code.
- Date/time display SHALL use `intl` package's locale-aware formatters (e.g., `DateFormat.yMMMMd('sw').format(date)`).
- The language setting SHALL survive app restarts (read from `SharedPreferences` during app initialization).

---

#### FR-U-005 — WhatsApp Communication Integration

- There SHALL be no native chat interface, push-to-talk, or messaging inbox in the mobile app.
- A "Contact Support via WhatsApp" button SHALL appear on:
  1. The Request Detail screen — contextual to the open request.
  2. The Profile/Settings screen — general support contact.
- On tap, the app SHALL invoke the `WhatsAppService.buildAndLaunch()` method, which:
  1. Reads the support phone number from the remote config endpoint `GET /api/v1/config` (cached for 1 hour).
  2. Reads the localized message template from `AppLocalizations.waMessageTemplate(requestId, category, status)`.
  3. Constructs the deep link: `https://wa.me/{supportNumber}?text={Uri.encodeComponent(localizedMessage)}`.
  4. Calls `launchUrl(uri, mode: LaunchMode.externalApplication)`.
- If WhatsApp is not installed on the device, the app SHALL show a snackbar with the support number formatted for the user to copy or dial directly.
- The support number SHALL NEVER be hardcoded in the app binary. It MUST be fetched from server-side config, enabling operations to update it without a release.
- The pre-filled message template in English:
  > `"Hello, my waste collection request #REQ-000234 (Household Waste) is currently PENDING. Please assist."`
- The pre-filled message template in Swahili:
  > `"Habari, ombi langu la ukusanyaji wa taka #REQ-000234 (Taka za Nyumbani) lipo katika hali ya INASUBIRI. Tafadhali nisaidie."`

---

### 3.2 Admin Dashboard Module

---

#### FR-A-001 — Live Request Map & Queue View

- The admin dashboard home SHALL display a full-viewport Google Map / Mapbox GL map centered on the operational city.
- All requests with status in `{PENDING, ASSIGNED, IN_TRANSIT}` SHALL be plotted as markers. Terminal states (`COLLECTED`, `CLOSED`, `CANCELLED`) are excluded from the live map by default.
- Marker color coding by status:

  | Status | Marker Color |
  |---|---|
  | `PENDING` | Yellow `#F59E0B` |
  | `ASSIGNED` | Blue `#3B82F6` |
  | `IN_TRANSIT` | Orange `#F97316` |
  | `COLLECTED` | Green `#22C55E` |

- A resizable sidebar panel SHALL show the same requests as a list (sortable by `created_at`, `status`, `category`; filterable by status and date range).
- Both the map and the list SHALL update in real-time via Supabase Realtime subscription on the `admin_requests` channel — no manual refresh required.
- Clicking a map marker or list row SHALL open a request detail modal (see FR-A-002).

---

#### FR-A-002 — Dispatch & Assignment Logic

- The Request Detail Modal SHALL display: user full name, phone number, `pickup_address`, embedded mini-map with pin, waste category, volume tier, photos (gallery), notes, `requestRef`, `created_at`, and current status.
- The admin SHALL be able to assign the request to a collection unit via a dropdown populated from `collection_units` where `is_available = TRUE`.
- On assignment confirmation, the API (`PATCH /api/v1/admin/requests/:id/status`) SHALL:
  1. Validate the requesting token has `role: admin` scope.
  2. Validate the state transition (`PENDING → ASSIGNED`) via the server-side state guard.
  3. Within a single database transaction:
     - `UPDATE waste_requests SET status = 'ASSIGNED', assigned_unit_id = ?, assigned_at = NOW()`.
     - `INSERT INTO request_status_history (...) VALUES ('PENDING', 'ASSIGNED', NOW(), admin_id)`.
  4. Trigger FCM push notification to the user's `fcm_token`: "Your request has been assigned — a collection unit is on the way."
  5. Publish a Supabase Realtime event on `user_{userId}_requests` and `admin_requests` channels.
- The modal SHALL also contain a "WhatsApp User" button that constructs a `wa.me/{userPhone}` link (admin-side only — the admin has access to user phone numbers, which are not exposed to other residents).

---

#### FR-A-003 — Request Status Lifecycle Management

The status machine is strictly linear with one permitted branch:

```
PENDING → ASSIGNED → IN_TRANSIT → COLLECTED → CLOSED
       ↘                ↘
        CANCELLED (from PENDING by user OR admin)
        CANCELLED (from ASSIGNED by admin only)
```

- All status transitions SHALL be validated server-side using a `validateTransition(currentStatus, nextStatus, actorRole)` function. Invalid transitions SHALL return `HTTP 409 Conflict` with a descriptive error body.
- The user (resident) MAY cancel their own request only if `status === 'PENDING'`.
- The admin MAY cancel a request from `PENDING` or `ASSIGNED` state.
- Once a request reaches `COLLECTED` or `CANCELLED`, no further status transitions are permitted by any actor.
- Every status transition, regardless of actor, SHALL produce a row in `request_status_history`.

---

#### FR-A-004 — User Management

- The admin SHALL be able to view all registered users in a sortable, searchable table: full name, phone, `preferred_language`, `created_at`, total request count, and `is_active` status.
- The admin SHALL be able to deactivate a user account (`is_active = FALSE`). Deactivated users receive a `403 Forbidden` on API calls.
- The admin SHALL be able to reactivate a previously deactivated account.
- The admin SHALL be able to filter users by `preferred_language` to monitor localization adoption metrics.

---

#### FR-A-005 — Analytics & Reporting

- The analytics page SHALL display KPI summary cards:
  - Total requests today / this week / this month.
  - Count by current status (PENDING, ASSIGNED, IN_TRANSIT, COLLECTED, CLOSED, CANCELLED).
  - Average time from request creation (`created_at`) to collection (`collected_at`), segmented by day and week.
- A donut chart SHALL show the distribution of requests by `category`.
- A line chart SHALL show daily request volume over a selectable date range (7/30/90 days).
- A heatmap or ranked table SHALL show top pickup zones by request density.
- The admin SHALL be able to export a filtered dataset (by date range and status) as a CSV file. The export endpoint SHALL stream the response to avoid timeout on large datasets.

---

## 4. UI/UX & NAVIGATION ARCHITECTURE

### 4.1 Bottom Navigation Bar — Mobile App

The Bottom Navigation Bar is the primary navigation paradigm for the resident mobile app. It uses Flutter's `NavigationBar` widget (Material 3) with 3 tab destinations. The bar persists across all nested screens within each tab's own navigation stack, implemented via `GoRouter`'s `ShellRoute`.

```
╔══════════════════════════════════════════════════╗
║                                                  ║
║              APP CONTENT AREA                    ║
║        (Tab-specific Navigator Stack)            ║
║                                                  ║
╠══════════════════════════════════════════════════╣
║                                                  ║
║    🏠 Home         📋 History       👤 Profile   ║
║   (Ombi)         (Historia)        (Wasifu)      ║
║                                                  ║
╚══════════════════════════════════════════════════╝
```

**Tab 1 — Home (`/home`)**

| Screen | Route | Description |
|---|---|---|
| Dashboard | `/home` | Greeting, active request status card (if any), "Request Pickup" CTA button |
| New Request Form | `/home/request/new` | Category picker grid, volume tier selector, notes field, photo picker row |
| Location Picker | `/home/request/location` | Full-screen map, auto-centered on GPS, draggable pin, reverse-geocoded address confirmation |
| Confirmation | `/home/request/confirm` | Summary card of submission, requestRef display, "Done" → back to Dashboard |

Badge behavior: Show count badge on tab icon if user has ≥1 active request in `PENDING` or `ASSIGNED` state.

**Tab 2 — History (`/history`)**

| Screen | Route | Description |
|---|---|---|
| Request List | `/history` | Paginated list, infinite scroll, pull-to-refresh, status badge on each tile |
| Request Detail | `/history/request/:id` | Map pin, status timeline stepper, metadata, photo gallery, WhatsApp CTA |

**Tab 3 — Profile / Settings (`/profile`)**

| Screen | Route | Description |
|---|---|---|
| Profile Home | `/profile` | Avatar, full name (editable inline), phone number (read-only), logout |
| Settings | `/profile/settings` | Language toggle, notification preferences, app version, "Support via WhatsApp" |

---

### 4.2 Localization Architecture

**ARB File Structure:**

```
/assets/
  /l10n/
    app_en.arb    ←  English string catalog (source of truth)
    app_sw.arb    ←  Swahili translation catalog
```

**Sample ARB Content (abbreviated):**

```json
// app_en.arb
{
  "@@locale": "en",
  "navHome": "Home",
  "navHistory": "History",
  "navProfile": "Profile",
  "homeRequestCTA": "Request Pickup",
  "reqCategoryHousehold": "Household Waste",
  "reqCategoryConstruction": "Construction Debris",
  "reqCategoryElectronic": "E-Waste",
  "reqCategoryHazardous": "Hazardous Materials",
  "statusPending": "Pending",
  "statusAssigned": "Assigned",
  "statusInTransit": "On the Way",
  "statusCollected": "Collected",
  "statusClosed": "Closed",
  "statusCancelled": "Cancelled",
  "waMessageTemplate": "Hello, my waste request #{requestId} ({category}) is currently {status}. Please assist.",
  "@waMessageTemplate": {
    "placeholders": {
      "requestId": { "type": "String" },
      "category": { "type": "String" },
      "status": { "type": "String" }
    }
  },
  "langPickerTitle": "Select Language",
  "langEnglish": "English",
  "langSwahili": "Kiswahili"
}

// app_sw.arb
{
  "@@locale": "sw",
  "navHome": "Nyumbani",
  "navHistory": "Historia",
  "navProfile": "Wasifu",
  "homeRequestCTA": "Omba Ukusanyaji",
  "reqCategoryHousehold": "Taka za Nyumbani",
  "reqCategoryConstruction": "Taka za Ujenzi",
  "reqCategoryElectronic": "Taka za Elektroniki",
  "reqCategoryHazardous": "Vifaa Hatari",
  "statusPending": "Inasubiri",
  "statusAssigned": "Imepangiwa",
  "statusInTransit": "Inakuja",
  "statusCollected": "Imekusanywa",
  "statusClosed": "Imefungwa",
  "statusCancelled": "Imefutwa",
  "waMessageTemplate": "Habari, ombi langu la taka #{requestId} ({category}) lipo katika hali ya {status}. Tafadhali nisaidie.",
  "@waMessageTemplate": {
    "placeholders": {
      "requestId": { "type": "String" },
      "category": { "type": "String" },
      "status": { "type": "String" }
    }
  },
  "langPickerTitle": "Chagua Lugha",
  "langEnglish": "Kiingereza",
  "langSwahili": "Kiswahili"
}
```

**Runtime Switching Flow:**

1. User taps language option in Profile > Settings.
2. `LocaleNotifier` (Riverpod `StateNotifier<Locale>`) is called with the new locale.
3. `MaterialApp`'s `locale` parameter is bound to `ref.watch(localeProvider)` — rebuild fires automatically.
4. `SharedPreferences.setString('app_locale', 'sw')` persists the selection to device storage.
5. `PATCH /api/v1/users/me { preferred_language: "sw" }` syncs to backend (used for FCM notification localization).
6. The navigation stack is NOT reset. The user remains on the current screen, which rebuilds its text in-place in under 100ms.

**FCM Notification Localization:** The backend SHALL read `users.preferred_language` before constructing FCM notification payloads. FCM `data` payloads (not `notification` payloads) SHALL be used so the app handles display locally via `AppLocalizations`, ensuring correct language even when the background locale differs from the device OS language.

---

## 5. NON-FUNCTIONAL REQUIREMENTS

### NFR-001: Performance

- App cold start time MUST be < 3 seconds on a device with 2GB RAM and a Snapdragon 665-class CPU (representative low-end Android in the target market).
- GPS location acquisition SHALL complete within 5 seconds under normal outdoor conditions. Network location fallback activates after 10 seconds.
- WhatsApp deep-link launch SHALL require zero network calls at the moment of tap (the support number is pre-cached from the config endpoint). The `wa.me` URL SHALL be constructed entirely in memory.
- API response times SHALL meet: `GET /requests` (paginated, 20 items) < 500ms at P95 under 100 concurrent users; `POST /requests` < 800ms at P95 (includes server-side geocoding call).
- Admin map SHALL render up to 500 active request markers without perceptible frame drops (marker clustering SHALL be implemented for dense regions using Mapbox's built-in clustering or `supercluster`).

### NFR-002: Scalability

- The API server SHALL be stateless and horizontally scalable behind an NGINX or cloud load balancer (AWS ALB, GCP Cloud Load Balancing).
- All JWT validation SHALL occur in memory with no database calls per request (signing secret in env var, standard `jsonwebtoken.verify()`).
- PostgreSQL read replicas SHALL handle analytics and reporting queries (`/admin/analytics/*`) to eliminate I/O contention with transactional write traffic.
- The architecture SHALL support 10,000 daily active users (DAU) in Phase 1 on a single app server node + single primary DB. Scaling to 100,000 DAU SHALL require infrastructure changes only (additional nodes, read replicas, connection pooler like PgBouncer) — no application code refactoring.

### NFR-003: Reliability

- Core API uptime SLA target: **99.5% monthly** (≤ 3.6 hours downtime/month).
- All database writes that involve multiple tables (e.g., updating `waste_requests.status` AND inserting into `request_status_history`) SHALL be wrapped in explicit PostgreSQL transactions (`BEGIN / COMMIT / ROLLBACK`) to guarantee atomicity.
- Requests that fail to submit due to transient network errors on the mobile client SHALL be queued in the local `drift` database and retried using exponential backoff (initial delay 5s, max delay 5 minutes, max 5 retries).
- A daily automated PostgreSQL dump SHALL be written to cloud object storage (Supabase Storage or S3) with 14-day retention and alerting on failure.

### NFR-004: Localization Performance

- Language switching SHALL complete in **< 100ms** as measured from user tap to full UI text update.
- ARB files SHALL be compiled to Dart code at build time via `flutter gen-l10n`. This means all locale strings are static Dart getters — no runtime JSON parsing, no file I/O, no network calls.
- All date, time, and number formatting SHALL be locale-aware using the `intl` package (`DateFormat`, `NumberFormat`). Timestamps stored in UTC SHALL be converted to local timezone using `flutter_timezone` before display.
- The language preference SHALL survive app restarts, device reboots, and OS-level language changes (device language does NOT override the in-app preference — the app locale setting is authoritative).

### NFR-005: Security & Privacy

- User GPS coordinates and pickup addresses constitute **PII** (Personally Identifiable Information) and SHALL be treated accordingly: stored only in the `waste_requests` table, accessible only to the owning `user_id` and admins.
- The API SHALL enforce row-level ownership on all resident endpoints: `WHERE user_id = (decoded JWT).sub`. This SHALL be enforced server-side in the NestJS guard/interceptor layer — client-side filtering is not sufficient.
- Admin API endpoints SHALL require a separate JWT with a `role: admin` claim, issued only by the admin login endpoint. The mobile app SHALL never receive or store an admin-scoped token.
- API communication SHALL use **HTTPS / TLS 1.3** exclusively. The mobile app SHALL implement **certificate pinning** via `http_certificate_pinning` or equivalent to prevent man-in-the-middle attacks on device-local networks (critical for target market where public/shared WiFi is common).
- Phone numbers of residents SHALL NEVER be exposed to other residents. The WhatsApp button in the resident app goes to a generic support number, not a specific dispatcher's personal number.
- Photo uploads SHALL be stored in a private bucket with signed URL access. Admins access photos via short-lived pre-signed URLs (15-minute expiry) generated by the backend.
- All `password_hash` values SHALL use `bcrypt` with a minimum cost factor of 12.
- API rate limiting SHALL be applied on auth endpoints: max 5 failed login attempts per IP per 15-minute window (using `express-rate-limit` or NestJS `ThrottlerModule`).

---

## 6. SYSTEM ARCHITECTURE & DATA FLOW

### 6.1 System Component Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                        MOBILE APP (Flutter)                     │
│                                                                 │
│  ┌──────────┐  ┌─────────────┐  ┌───────────┐  ┌───────────┐  │
│  │  Auth    │  │   Request   │  │  History  │  │  Profile  │  │
│  │  Module  │  │   Module    │  │  Module   │  │  Module   │  │
│  └────┬─────┘  └──────┬──────┘  └─────┬─────┘  └─────┬─────┘  │
│       └───────────────┴────────────────┴──────────────┘        │
│                           │                                     │
│                   ┌───────┴───────┐                            │
│                   │  Dio Client   │  ◄─── JWT Interceptor       │
│                   │  + Supabase   │  ◄─── Realtime Subscription │
│                   └───────┬───────┘                            │
└───────────────────────────┼─────────────────────────────────────┘
                            │ HTTPS / TLS 1.3
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                    API SERVER (NestJS / Node)                   │
│                                                                 │
│  Auth Guard  │  Rate Limiter  │  Request Logger  │  CORS       │
│  ──────────────────────────────────────────────────────────   │
│  /auth/*     │  /api/v1/requests/*  │  /api/v1/admin/*         │
│  /api/v1/config                                                │
│                                                                 │
│  Services: AuthService, RequestService, GeocodingService,      │
│             FCMService, WhatsAppConfigService                   │
└──────────────────────────┬──────────────────────────────────────┘
                           │
          ┌────────────────┼─────────────────┐
          ▼                ▼                 ▼
   ┌────────────┐   ┌────────────┐   ┌────────────┐
   │ PostgreSQL │   │  Supabase  │   │  Firebase  │
   │  + PostGIS │   │  Realtime  │   │  FCM / RC  │
   │  (Primary) │   │  (WS Push) │   │  (Notif.)  │
   └────────────┘   └────────────┘   └────────────┘
          ▲
   ┌────────────┐   ┌────────────────────────────────┐
   │ PG Read    │   │  Google APIs                   │
   │ Replica    │   │  - Geocoding API (reverse)     │
   │ (Analytics)│   │  - Maps SDK (mobile + admin)   │
   └────────────┘   └────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                 ADMIN DASHBOARD (React + Mapbox)                │
│                                                                 │
│  Login  │  Map View  │  Queue List  │  Analytics  │  Users     │
│  ─────────────────────────────────────────────────────────    │
│  Supabase Realtime subscription (admin_requests channel)       │
└─────────────────────────────────────────────────────────────────┘
```

### 6.2 New Request Submission — Full Data Flow

```
[Mobile Client]
  User fills form (category, volume, photos, note)
  GPS acquired → reverse geocoded → address confirmed
  User taps "Submit"
        │
        │ POST /api/v1/requests
        │ Authorization: Bearer {access_token}
        │ Body: { lat, lng, category, volumeTier, note, photoUrls[] }
        ▼
[API Server — NestJS]
  1. JWT middleware: verify signature, extract userId from sub claim
  2. Zod schema validation: required fields, enum values, coordinate range
  3. GeocodingService.reverseGeocode(lat, lng) → addressString
  4. Generate requestRef: "REQ-" + zero-padded sequence number
  ─── BEGIN TRANSACTION ───────────────────────────────────────
  5. INSERT INTO waste_requests (userId, POINT(lng,lat), address,
     category, volumeTier, note, photoUrls, status='PENDING', requestRef)
  6. INSERT INTO request_status_history
     (requestId, previousStatus=NULL, newStatus='PENDING', changedByUserId)
  ─── COMMIT ──────────────────────────────────────────────────
  7. Supabase.publish('admin_requests', { event: 'NEW_REQUEST', requestId })
  8. Response 201 { requestId, requestRef, status: 'PENDING', address }
        │
        ▼
[Mobile Client]
  Navigate to Confirmation screen
  Display requestRef "REQ-000234"
  Supabase Realtime subscription active on user_{userId}_requests

[Admin Dashboard]
  Supabase Realtime fires on admin_requests channel
  New yellow marker appears on map at (lat, lng)
  Sidebar list updates
        │
        │ Admin clicks marker → reviews detail → selects unit → confirms
        │
        │ PATCH /api/v1/admin/requests/REQ-000234/status
        │ Authorization: Bearer {admin_token}  (role: admin scope)
        │ Body: { newStatus: "ASSIGNED", unitId: "UNIT-07" }
        ▼
[API Server]
  1. AdminGuard: verify role === 'admin' in JWT claims
  2. Fetch current request from DB: status === 'PENDING' ✓
  3. validateTransition('PENDING', 'ASSIGNED', 'admin') → valid
  ─── BEGIN TRANSACTION ───────────────────────────────────────
  4. UPDATE waste_requests SET
       status='ASSIGNED',
       assigned_unit_id='UNIT-07',
       assigned_at=NOW()
     WHERE id = requestId
  5. INSERT INTO request_status_history
     (requestId, 'PENDING', 'ASSIGNED', NOW(), changedByAdminId)
  ─── COMMIT ──────────────────────────────────────────────────
  6. FCMService.send(user.fcmToken, {
       title: localize('fcmAssignedTitle', user.preferred_language),
       body: localize('fcmAssignedBody', requestRef, user.preferred_language)
     })
  7. Supabase.publish('user_{userId}_requests', { requestId, newStatus: 'ASSIGNED' })
  8. Supabase.publish('admin_requests', { requestId, newStatus: 'ASSIGNED' })
  9. Response 200 { requestId, newStatus: 'ASSIGNED' }
        │
        ▼
[Mobile Client]
  Supabase Realtime subscription fires
  History list tile badge: PENDING → ASSIGNED (blue)
  Push notification received: "Your request has been assigned!"
  Request Detail status stepper updates
```

### 6.3 State Machine — Server-Side Enforcement

```
                    ┌─────────────────┐
                    │    PENDING      │  ← Initial state (created by user)
                    └────────┬────────┘
           User cancel ↙     │ Admin assigns unit
                            ▼
                    ┌─────────────────┐
                    │    ASSIGNED     │
                    └────────┬────────┘
           Admin cancel ↙    │ Unit departs
                            ▼
                    ┌─────────────────┐
                    │   IN_TRANSIT    │
                    └────────┬────────┘
                             │ Waste physically collected
                            ▼
                    ┌─────────────────┐
                    │   COLLECTED     │
                    └────────┬────────┘
                             │ Admin closes/archives
                            ▼
                    ┌─────────────────┐
                    │     CLOSED      │  ← Terminal state (no further transitions)
                    └─────────────────┘

  CANCELLED ← PENDING (by user) | PENDING or ASSIGNED (by admin only)
  CANCELLED is also a terminal state.

  Guard function signature:
  validateTransition(current: RequestStatus, next: RequestStatus, actor: 'user' | 'admin'): void
  Throws ConflictException (HTTP 409) on invalid transition.
```

---

## 7. DATABASE SCHEMA

### Core Tables — PostgreSQL 16 + PostGIS

```sql
-- =============================================
-- EXTENSIONS
-- =============================================
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS "pgcrypto";  -- gen_random_uuid()

-- =============================================
-- ENUMS
-- =============================================
CREATE TYPE waste_category_enum AS ENUM (
    'HOUSEHOLD', 'CONSTRUCTION', 'GARDEN', 'ELECTRONIC', 'HAZARDOUS', 'BULK_ITEMS'
);

CREATE TYPE volume_tier_enum AS ENUM (
    'SMALL_BAG', 'STANDARD_BIN', 'LARGE_LOAD', 'TRUCK_LOAD'
);

CREATE TYPE request_status_enum AS ENUM (
    'PENDING', 'ASSIGNED', 'IN_TRANSIT', 'COLLECTED', 'CLOSED', 'CANCELLED'
);

CREATE TYPE preferred_language_enum AS ENUM ('en', 'sw');
CREATE TYPE admin_role_enum AS ENUM ('super_admin', 'dispatcher', 'analyst');

-- =============================================
-- USERS (Residents)
-- =============================================
CREATE TABLE users (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name           VARCHAR(100) NOT NULL,
    phone_number        VARCHAR(20) UNIQUE NOT NULL,    -- E.164: +255712345678
    password_hash       VARCHAR(255) NOT NULL,           -- bcrypt, min cost 12
    preferred_language  preferred_language_enum NOT NULL DEFAULT 'en',
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    fcm_token           TEXT,                            -- Updated on each login
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_phone ON users (phone_number);
CREATE INDEX idx_users_language ON users (preferred_language);

-- =============================================
-- ADMINS
-- =============================================
CREATE TABLE admins (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name       VARCHAR(100) NOT NULL,
    email           VARCHAR(255) UNIQUE NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    role            admin_role_enum NOT NULL DEFAULT 'dispatcher',
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- =============================================
-- COLLECTION UNITS (Trucks / Teams)
-- =============================================
CREATE TABLE collection_units (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    unit_code       VARCHAR(20) UNIQUE NOT NULL,    -- e.g., "TRUCK-07"
    operator_name   VARCHAR(100),
    operator_phone  VARCHAR(20),
    is_available    BOOLEAN NOT NULL DEFAULT TRUE,
    current_zone    VARCHAR(100),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- =============================================
-- WASTE REQUESTS (Core Table)
-- =============================================
CREATE TABLE waste_requests (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_ref                 VARCHAR(12) UNIQUE NOT NULL,   -- REQ-000234

    -- Ownership
    user_id                     UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,

    -- Location (PostGIS GEOGRAPHY for accurate distance calculations)
    pickup_location             GEOGRAPHY(POINT, 4326) NOT NULL,
    pickup_address              TEXT NOT NULL,          -- Reverse-geocoded, stored immutably at submission
    pickup_zone                 VARCHAR(100),           -- Optional admin-assigned zone label

    -- Classification
    category                    waste_category_enum NOT NULL,
    volume_tier                 volume_tier_enum NOT NULL,
    notes                       VARCHAR(280),
    photo_urls                  TEXT[],                -- Array of 1-3 storage URLs

    -- Lifecycle
    status                      request_status_enum NOT NULL DEFAULT 'PENDING',
    assigned_unit_id            UUID REFERENCES collection_units(id),
    assigned_at                 TIMESTAMPTZ,
    collected_at                TIMESTAMPTZ,
    closed_at                   TIMESTAMPTZ,
    cancelled_at                TIMESTAMPTZ,
    estimated_collection_window TSTZRANGE,             -- Admin-set: e.g., [2025-06-29 14:00, 2025-06-29 16:00]

    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Spatial index for admin map queries (find requests within radius)
CREATE INDEX idx_waste_requests_location    ON waste_requests USING GIST (pickup_location);
-- Status filter (admin dashboard, analytics)
CREATE INDEX idx_waste_requests_status      ON waste_requests (status);
-- User ownership filter (resident history, FCM publishing)
CREATE INDEX idx_waste_requests_user_id     ON waste_requests (user_id);
-- Time-series ordering
CREATE INDEX idx_waste_requests_created_at  ON waste_requests (created_at DESC);
-- Compound: resident history list (user_id + status + created_at DESC)
CREATE INDEX idx_waste_requests_user_history ON waste_requests (user_id, status, created_at DESC);

-- =============================================
-- REQUEST STATUS HISTORY (Audit Log)
-- =============================================
CREATE TABLE request_status_history (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id          UUID NOT NULL REFERENCES waste_requests(id) ON DELETE CASCADE,
    previous_status     request_status_enum,            -- NULL on initial PENDING insertion
    new_status          request_status_enum NOT NULL,
    changed_by_user_id  UUID REFERENCES users(id),      -- Non-null if user triggered (e.g., cancel)
    changed_by_admin_id UUID REFERENCES admins(id),     -- Non-null if admin triggered
    note                TEXT,
    changed_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_status_history_request_id ON request_status_history (request_id, changed_at DESC);

-- =============================================
-- REFRESH TOKENS
-- =============================================
CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(255) UNIQUE NOT NULL,           -- SHA-256 hash of the token
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_hash ON refresh_tokens (token_hash);

-- =============================================
-- SYSTEM CONFIG (Remote/Operator Config)
-- =============================================
CREATE TABLE system_config (
    key         VARCHAR(100) PRIMARY KEY,
    value       TEXT NOT NULL,
    description TEXT,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Seed: WhatsApp support number (operators update via admin panel, no code release needed)
INSERT INTO system_config (key, value, description)
VALUES ('whatsapp_support_number', '+255700000000', 'E.164 number for resident WhatsApp support contact');
```

---

## 8. MODULAR IMPLEMENTATION PHASE PLAN

---

### PHASE 1 — Architecture, Localization Setup & Database Design

**Goal:** Establish the monorepo or multi-repo project structure, configure tooling, design the full database schema, run all migrations, and build out complete localization resource files for both EN and SW before any screen is built.

**Estimated Duration:** 1 – 1.5 weeks

---

#### Backend Setup
- [ ] Initialize NestJS project with TypeScript `strict: true` and module alias paths configured.
- [ ] Configure ESLint (Airbnb/NestJS rules) + Prettier + Husky pre-commit hooks (lint-staged).
- [ ] Provision PostgreSQL 16 instance via Docker locally: `docker-compose.yml` with `postgis/postgis:16-3.4` image.
- [ ] Configure remote environment: Supabase project (PostgreSQL + Realtime + Storage) for staging and production.
- [ ] Run all migration scripts in order: extensions → enums → tables → indexes → seed data.
- [ ] Validate PostGIS is active: `SELECT PostGIS_Full_Version();`.
- [ ] Configure environment variable management: `.env`, `.env.example`, `ConfigModule` (NestJS).
- [ ] Set up Firebase Admin SDK: import service account JSON, initialize `firebase-admin`.
- [ ] Configure Google Geocoding API: server-side key with IP restriction (not exposed to client).
- [ ] Create `GeocodingService` with `reverseGeocode(lat: number, lng: number): Promise<string>` method.
- [ ] Create `requestRef` generation utility: query current max sequence → format `REQ-{padStart(6, '0')}`.
- [ ] Configure Supabase Realtime: create publication on `waste_requests` table for `UPDATE` events.

#### Mobile App Setup
- [ ] Initialize Flutter project: `flutter create wastlink --org com.wastlink`.
- [ ] Configure `pubspec.yaml` with all required dependencies (see Section 2.1 Tech Stack).
- [ ] Configure `l10n.yaml`:
  ```yaml
  arb-dir: assets/l10n
  template-arb-file: app_en.arb
  output-localization-file: app_localizations.dart
  ```
- [ ] Configure Android `build.gradle`: `minSdkVersion 21`, `targetSdkVersion 34`.
- [ ] Set up project flavor profiles: `dev` / `staging` / `prod` with separate `google-services.json` per flavor.
- [ ] Configure certificate pinning: add server SSL certificate SHA-256 fingerprint to `network_security_config.xml` (Android) and `Info.plist` (iOS).
- [ ] Set up folder structure per feature module (see Section 2.1 Folder Structure).
- [ ] Configure `GoRouter` with all named routes for all screens across 3 tabs.
- [ ] Configure `ShellRoute` for persistent Bottom Navigation Bar.

#### Localization
- [ ] Create `/assets/l10n/app_en.arb` with a minimum of 80 string keys covering all modules.
- [ ] Create `/assets/l10n/app_sw.arb` with accurate Swahili translations for all 80+ keys. Engage a Swahili-native translator for review.
- [ ] Run `flutter gen-l10n` — verify `AppLocalizations` Dart file is generated without errors.
- [ ] Create `LocaleProvider` using Riverpod `StateNotifierProvider<LocaleNotifier, Locale>`.
- [ ] Initialize `LocaleProvider` from `SharedPreferences` value `app_locale` during app startup in `main.dart`.
- [ ] Bind `MaterialApp.locale` to `ref.watch(localeProvider)`.
- [ ] Set `MaterialApp.supportedLocales: [Locale('en'), Locale('sw')]`.
- [ ] Set `MaterialApp.localizationsDelegates` to include `AppLocalizations.delegate`, `GlobalMaterialLocalizations.delegate`, `GlobalWidgetsLocalizations.delegate`, `GlobalCupertinoLocalizations.delegate`.
- [ ] Write widget test: toggle locale from 'en' to 'sw' → assert `navHome` label changes from "Home" to "Nyumbani" without route reset.

---

### PHASE 2 — Core User UI & Bottom Navigation

**Goal:** Build all primary screens with accurate UI, the persistent Bottom Navigation Bar, and a working language toggle. No real API calls — use mock providers and hardcoded fixture data.

**Estimated Duration:** 1.5 – 2 weeks

---

#### Navigation Shell
- [ ] Implement `AppShell` stateful widget containing `NavigationBar` (Material 3) with destinations: Home, History, Profile.
- [ ] Wire `GoRouter` `ShellRoute` so Bottom Nav persists across all nested route pushes within each tab.
- [ ] Implement per-tab `Navigator` key to preserve each tab's scroll position and stack on tab switch.
- [ ] Style `NavigationBar`: apply brand color tokens, filled indicator on selected tab, icon + localized label.
- [ ] Implement active request badge on Home tab icon: read from mock `activeRequestCountProvider`.
- [ ] Implement route guard: if `authStateProvider` is unauthenticated, redirect all guarded routes to `/auth/login`.

#### Authentication Screens
- [ ] Build **Login Screen**: phone field (E.164 hint), password field, "Login" button, "Forgot Password" link, "Register" link, localized labels.
- [ ] Build **Registration Screen**: full name, phone, password, confirm password, language preference row (two option chips).
- [ ] Build **OTP Verification Screen** (if OTP flow is adopted): 6-digit input, 60s resend timer.
- [ ] Implement `FormState` validation: phone format via regex, min password length (8), confirm password match.
- [ ] Wire all auth screen labels to `AppLocalizations` — no hardcoded English strings.

#### Home Tab
- [ ] Build **Dashboard Screen**: localized greeting (`"Good morning, {name}"`), active request status card widget (shows most recent active request), "Request Pickup" primary CTA button.
- [ ] Build **New Request Form Screen**:
  - `WasteCategoryGrid` widget: 3×2 grid of tappable category cards with icon + localized label.
  - `VolumeTierSelector` widget: 4 radio option tiles with icon and description.
  - `NotesField`: multiline text input, 280-char counter.
  - `WastePhotoGrid` widget: horizontal scroll, placeholder tiles, "+" trigger for image picker, max 3.
- [ ] Build **Location Picker Screen**: full-screen `GoogleMap`, auto-center on user GPS, draggable `Marker`, localized address string display below map, "Confirm Location" button.
- [ ] Build **Request Confirmation Screen**: summary card (category, volume, address, photos count), "Submit" button, "Edit" back nav.

#### History Tab
- [ ] Build **Request History List Screen**: `ListView.builder` with `RequestListTile` widget, status badge, pull-to-refresh, infinite scroll trigger at bottom.
- [ ] Build **Request Detail Screen**: embedded map pin (non-interactive), vertical status timeline `Stepper`, metadata section, photo gallery row, WhatsApp CTA button (wired to mock).
- [ ] Build `StatusBadge` shared widget: takes `RequestStatus` enum, returns color-coded chip with localized label. Test all 6 states.

#### Profile Tab
- [ ] Build **Profile Screen**: avatar with first-name initial, `full_name` display (inline edit on tap), phone number (read-only), logout button with confirmation dialog.
- [ ] Build **Settings Section**: "Language" row → tapping opens modal with two radio options. On select, call `LocaleProvider` → verify all visible text updates immediately.
- [ ] Build notification preference toggle (UI only in Phase 2).
- [ ] Build "Support via WhatsApp" button (wired to mock `#` link in Phase 2, real in Phase 3).
- [ ] Display app version string from `package_info_plus`.

#### Shared UI System
- [ ] `AppButton` widget: primary (filled), secondary (outlined), destructive (red). All accept `isLoading` flag.
- [ ] `AppTextField` widget: standardized input with label, hint, error, suffix icon.
- [ ] `AppLoadingOverlay` widget: fullscreen semi-transparent spinner, blocks interaction.
- [ ] `AppErrorBanner` widget: dismissible top-of-screen error with icon.
- [ ] `RequestListTile` widget: left status color bar, category icon, ref + date, status badge.
- [ ] Define `AppTheme`: `ThemeData` with brand colors, `TextTheme` hierarchy, `NavigationBarTheme`, `CardTheme`, `InputDecorationTheme`.

---

### PHASE 3 — Request Workflow & WhatsApp Integration

**Goal:** Replace all mock data with live API connections. Implement GPS location capture, real-time status updates via Supabase, and functional WhatsApp deep-link generation in both languages.

**Estimated Duration:** 2 weeks

---

#### Backend API Endpoints
- [ ] `POST /api/v1/auth/register` — validate phone uniqueness, hash password with bcrypt (cost 12), create user, issue JWT pair, return `{ accessToken, refreshToken, user }`.
- [ ] `POST /api/v1/auth/login` — find by phone, compare password, update `fcm_token` if provided, issue JWT pair.
- [ ] `POST /api/v1/auth/refresh` — hash received token, find matching non-revoked `refresh_tokens` row, validate expiry, issue new access token, rotate refresh token (revoke old, insert new).
- [ ] `PATCH /api/v1/users/me` — update `full_name` and/or `preferred_language` for authenticated user.
- [ ] `POST /api/v1/requests` — validate JWT, validate body (Zod), call `GeocodingService.reverseGeocode()`, generate `requestRef`, insert within transaction, publish Supabase event, return 201.
- [ ] `GET /api/v1/requests?page=1&limit=20` — paginated, ordered `created_at DESC`, scoped to `user_id` from JWT.
- [ ] `GET /api/v1/requests/:id` — single request, ownership check, return full detail including `photoUrls`.
- [ ] `PATCH /api/v1/requests/:id/cancel` — user cancel: validate ownership + `status === 'PENDING'`, run state machine, update within transaction, send FCM.
- [ ] `GET /api/v1/config` — return `{ whatsappSupportNumber }` from `system_config` table. Cache response in-memory for 1 hour.
- [ ] Write unit tests for `validateTransition()`: assert every valid transition passes, assert every invalid transition throws `ConflictException`.

#### Mobile GPS & Location
- [ ] Install `geolocator`, `geocoding` packages. Configure Android permissions in `AndroidManifest.xml`, iOS `Info.plist`.
- [ ] Create `LocationService` class:
  - `requestPermission()` → returns bool, handles `denied`, `deniedForever` cases with bottom sheet guide to settings.
  - `getCurrentPosition()` → `geolocator.getCurrentPosition(desiredAccuracy: LocationAccuracy.high, timeLimit: Duration(seconds: 10))`.
  - On `TimeoutException`: retry with `LocationAccuracy.medium` (network-based) for 5 more seconds.
- [ ] Create `GeocodingService` (client-side, separate from server-side):
  - `reverseGeocode(lat, lng)` → calls Google Geocoding API via `Dio`, returns formatted address string.
  - Implement debounce (300ms) on map drag to avoid excessive API calls.
- [ ] On Location Picker Screen mount: call `requestPermission()` → `getCurrentPosition()` → animate map camera → `reverseGeocode()` → display address.
- [ ] Implement draggable marker: `GoogleMap(onCameraIdle: () => reverseGeocode(center.lat, center.lng))` — address updates after drag stops.
- [ ] Block submission if `_locationState.coordinates == null`.

#### API Integration (Mobile)
- [ ] Create `DioClient` singleton: configure `baseUrl`, `connectTimeout: 15s`, `receiveTimeout: 30s`.
- [ ] Implement JWT interceptor: attach `Authorization: Bearer {accessToken}` from `SecureStorage` on every request.
- [ ] Implement refresh token interceptor: on `401` response, call `POST /auth/refresh`, update tokens in `SecureStorage`, retry original request once. On refresh failure → logout.
- [ ] Implement `AuthRepository`: `login()`, `register()`, `refresh()`, `logout()` (revoke + clear storage).
- [ ] Implement `RequestRepository`: `createRequest()`, `getRequests(page)`, `getRequestById(id)`, `cancelRequest(id)`.
- [ ] Wire Riverpod `AsyncNotifierProvider` per feature: `authNotifier`, `requestListNotifier`, `requestDetailNotifier`.
- [ ] Initialize Supabase client in `main.dart`. On authenticated login, subscribe to `user_{userId}_requests` channel. On event received: call `ref.invalidate(requestListProvider)` to trigger re-fetch; show in-app toast.
- [ ] Register FCM token on successful login: `PATCH /api/v1/users/me { fcmToken }`.
- [ ] Handle FCM foreground messages: show local notification banner with request ref and status.

#### WhatsApp Integration
- [ ] Create `WhatsAppService` class with method `buildDeepLink({ requestId, category, status, locale }) → String`.
  - Fetch support number from `ref.read(configProvider)` (pre-cached).
  - Resolve localized category label: `AppLocalizations.of(context)!.catLabel(category)` — or pass pre-resolved strings.
  - Resolve localized status label: `AppLocalizations.of(context)!.statusLabel(status)`.
  - Apply localized `waMessageTemplate` with named placeholders.
  - `Uri.encodeFull(message)` → construct `https://wa.me/{number}?text={encoded}`.
- [ ] Create `WhatsAppLaunchService.launch(url: String)`:
  - `canLaunchUrl()` → if false: show `SnackBar` with phone number + "Copy" action.
  - `launchUrl(mode: LaunchMode.externalApplication)`.
- [ ] Wire to "Contact Support via WhatsApp" button on Request Detail screen.
- [ ] Wire to "Support" button on Profile/Settings screen (no requestId → generic message).
- [ ] Write widget test for `WhatsAppService.buildDeepLink()` in EN and SW:
  - Assert EN: message contains `"Hello, my waste request"` and `requestRef`.
  - Assert SW: message contains `"Habari, ombi langu la taka"` and `requestRef`.
- [ ] Manual test on physical device: button tap → WhatsApp opens → pre-filled text is correct in current app language.

---

### PHASE 4 — Admin Control Panel & Dispatch Logic

**Goal:** Build the full web admin dashboard with real-time map, request assignment, lifecycle management, user management, analytics, and CSV export.

**Estimated Duration:** 2 weeks

---

#### Backend Admin API
- [ ] `POST /api/v1/admin/auth/login` — verify admin credentials, issue JWT with `{ role: 'admin', adminId }` in payload.
- [ ] `GET /api/v1/admin/requests` — all requests, filterable by `status`, `category`, `dateFrom`, `dateTo`; paginated (20/page); includes user name and phone.
- [ ] `GET /api/v1/admin/requests/map` — returns all requests where `status IN ('PENDING','ASSIGNED','IN_TRANSIT')` with `{ id, requestRef, lat, lng, status, category }`. No pagination (used for map marker rendering). Cached with 10s TTL.
- [ ] `PATCH /api/v1/admin/requests/:id/status` — admin status transition. Validate `role === 'admin'` guard. Call `validateTransition()`. Run update + history insert in transaction. Publish Supabase events. Trigger FCM.
- [ ] `GET /api/v1/admin/collection-units` — list all units, optionally filtered by `is_available`.
- [ ] `GET /api/v1/admin/users` — paginated user list with `preferred_language` filter, total request count per user via LEFT JOIN.
- [ ] `PATCH /api/v1/admin/users/:id/status` — toggle `is_active`. Revoke all active refresh tokens for deactivated user.
- [ ] `GET /api/v1/admin/analytics/summary` — aggregate query: counts by status, average `(collected_at - created_at)` as `avgResolutionHours`, requests per day for last 30 days (daily buckets), count by category.
- [ ] `GET /api/v1/admin/analytics/export?dateFrom=&dateTo=&status=` — stream CSV using `fast-csv` piped to `res` (streaming avoids memory OOM on large exports).
- [ ] Apply `AdminGuard` (NestJS `CanActivate`) to ALL `/admin/*` routes — return 403 if `decoded.role !== 'admin'`.

#### Admin Dashboard Frontend
- [ ] Initialize React 18 + TypeScript + Vite project. Configure `tsconfig.json` with `strict: true`.
- [ ] Configure Tailwind CSS with brand color tokens in `tailwind.config.ts`.
- [ ] Configure React Query `QueryClient` with `staleTime: 30_000` (30s) as global default.
- [ ] Build **Login Page**: email + password form, POST to `/admin/auth/login`, store admin JWT in `sessionStorage`.
- [ ] Build **App Shell**: fixed left sidebar (Requests, Users, Analytics, Settings links), topbar with admin name + logout, main content `<Outlet>`.
- [ ] Build **Request Map View** (`/requests/map`):
  - Initialize Mapbox GL / Google Maps JS centered on city coordinates from config.
  - Call `GET /admin/requests/map` on mount.
  - Plot color-coded markers. Implement `supercluster` for marker clustering at low zoom levels.
  - Set up Supabase Realtime subscription (`admin_requests` channel): on `INSERT` → add marker; on `UPDATE` → update marker color; on marker removal → remove.
  - Click handler on marker → open Request Detail Modal.
- [ ] Build **Request Queue View** (`/requests/list`): sortable TanStack Table with columns: Ref, User, Category, Volume, Zone, Status, Created At, Actions. Status filter tab bar (All / Pending / Assigned / In Transit / Collected). Date range picker filter.
- [ ] Build **Request Detail Modal**:
  - Displays all request metadata (see FR-A-002 for complete list).
  - Mini map (read-only `GoogleMap` or Mapbox static image) with pickup pin.
  - Photo gallery (lightbox on click).
  - Status transition control: dropdown filtered to only valid next states based on `currentStatus`; "Update Status" button → POST → optimistic UI update.
  - Collection unit assignment dropdown (loaded from `/admin/collection-units`).
  - "WhatsApp User" button → `window.open('https://wa.me/{userPhone}?text=...', '_blank')`.
- [ ] Build **User Management View** (`/users`): searchable, sortable table. "Deactivate" / "Activate" toggle per row with confirmation dialog.
- [ ] Build **Analytics Dashboard** (`/analytics`):
  - KPI cards: Today's Total, This Week Total, Avg Resolution Time.
  - `PieChart` (Recharts): requests by category.
  - `LineChart` (Recharts): daily request volume over last 30 days.
  - Date range picker → re-fetches analytics summary.
  - "Export CSV" button → triggers `GET /admin/analytics/export` with `window.location.href` or `<a download>` streaming.
- [ ] Implement polling fallback (React Query `refetchInterval: 30_000`) for all active-data queries in case Supabase WebSocket connection drops.
- [ ] Build status transition optimistic UI: immediately update status badge in list, roll back on API error.

---

### PHASE 5 — Testing, Deployment & Optimization

**Goal:** Achieve production readiness through systematic testing, performance validation, security review, and cloud deployment.

**Estimated Duration:** 1.5 – 2 weeks

---

#### Backend Testing
- [ ] **Unit Tests (Jest):**
  - `validateTransition()`: test all valid transitions → pass; all invalid transitions → throw `ConflictException`.
  - `WhatsAppService.buildDeepLink()`: test EN and SW message formatting with mock data.
  - `requestRef` generator: test formatting and zero-padding.
  - `GeocodingService.reverseGeocode()`: mock Google API response, assert address string extraction.
- [ ] **Integration Tests (Supertest + in-memory PG or test schema):**
  - Auth flow: `register` → `login` → `refresh` → authenticated request → `logout`.
  - Request lifecycle: `createRequest` → assert `PENDING` → `ASSIGNED` → `IN_TRANSIT` → `COLLECTED` → `CLOSED`.
  - Security: user A cannot fetch user B's requests (assert 403).
  - Security: mobile client cannot reach `/admin/*` endpoints (assert 403).
  - Invalid transition: attempt `PENDING → COLLECTED` directly (assert 409).
- [ ] **Performance:**
  - Run `EXPLAIN ANALYZE` on `GET /requests?page=1` query with 100,000 rows seeded — assert index usage, no sequential scan.
  - Run `EXPLAIN ANALYZE` on `/admin/requests/map` spatial query — assert GiST index usage.
  - Load test with `k6` or `Artillery`: 100 concurrent virtual users, `POST /requests` for 2 minutes — assert P95 < 800ms, 0% error rate.

#### Mobile App Testing
- [ ] **Widget Tests:**
  - `StatusBadge`: render all 6 status values in EN and SW — assert correct label and color.
  - `WasteCategoryGrid`: assert all 6 categories render with localized labels in SW.
  - Language toggle: assert `LocaleProvider` state updates and `MaterialApp` rebuilds.
- [ ] **Integration Tests (flutter_test + mock repositories):**
  - New Request flow: fill form → select location (mock GPS) → submit → navigate to Confirmation screen → assert `requestRef` displayed.
  - WhatsApp deep-link: EN locale → assert link contains English text; SW locale → assert link contains Swahili text.
  - Cancel flow: mock `PENDING` request → tap Cancel → assert API called → status updates to `CANCELLED`.
- [ ] **Manual Device Testing:**
  - Test on physical low-end Android (2GB RAM) — verify cold start < 3s, GPS acquisition < 5s.
  - Test offline submission queue: disable WiFi after filling form → submit → re-enable WiFi → verify request appears on server after retry.
  - Test language switch: switch to SW mid-app → all visible text updates without restart.

#### Admin Dashboard Testing
- [ ] Unit tests (Vitest): `validateTransition()` frontend guard, WhatsApp URL builder.
- [ ] E2E tests (Playwright):
  - Login → map loads with markers → click `PENDING` marker → assign unit → status changes to `ASSIGNED` (marker turns blue, sidebar list updates).
  - Date-range filter on analytics → assert chart data changes.
  - CSV export → assert file download triggered.
- [ ] Cross-browser manual test: Chrome, Firefox, Safari (admin likely uses desktop browser).

#### Security Review
- [ ] Verify certificate pinning blocks traffic on device when intercepting proxy is active.
- [ ] Confirm admin JWT is rejected on all `/api/v1/requests/*` resident endpoints (wrong scope).
- [ ] Confirm user JWT is rejected on all `/api/v1/admin/*` endpoints.
- [ ] Confirm `pickup_location` and `pickup_address` are not returned in `/admin/requests/map` response (only coordinates needed for markers; full address loads in modal on demand).
- [ ] Audit photo URLs: confirm they are signed with 15-min expiry, not permanent public URLs.
- [ ] Run `npm audit` and `flutter pub audit` — remediate all high/critical CVEs.

#### Performance Optimization
- [ ] Add compound index `(user_id, status, created_at DESC)` for History list query.
- [ ] Cache `GET /api/v1/config` response in Redis or in-memory `node-cache` with 1-hour TTL.
- [ ] Cache `GET /admin/requests/map` with 10-second in-memory TTL to reduce map query frequency under high admin load.
- [ ] Enable GZIP compression via `compression` middleware on the Express/NestJS app.
- [ ] Build Flutter release APK with `--split-per-abi` flag: separates ARMv7, ARM64, x86_64 APKs — reduces download size by ~40%.
- [ ] Lazy-load admin analytics page: split into separate React lazy chunk to reduce initial bundle.

#### Deployment
- [ ] Write `Dockerfile` for NestJS API: multi-stage build (build → production), non-root user.
- [ ] Write `docker-compose.yml`: `api` + `postgres` + optional `redis` services with health checks.
- [ ] Write GitHub Actions CI pipeline:
  - On `push` to `main`: lint → test → build → build Docker image → push to registry → deploy to cloud.
  - Separate pipeline for Flutter: lint → test → build APK → upload artifact.
- [ ] Deploy API to cloud platform (Railway, Render, AWS ECS, or Google Cloud Run).
- [ ] Deploy admin dashboard to Vercel or Netlify with environment-based config injection.
- [ ] Configure custom domain + SSL (Let's Encrypt via cloud provider or Cloudflare).
- [ ] Configure PostgreSQL automated daily snapshots (Supabase built-in or `pg_dump` cron job) with 14-day retention.
- [ ] Configure uptime monitoring (Better Uptime / UptimeRobot) with SMS + email alert on API downtime.
- [ ] Configure structured logging with Winston/Pino — ship to Logtail, Datadog, or similar.
- [ ] Submit Flutter app to **Google Play Internal Testing** track for team UAT before public release.
- [ ] Conduct UAT with 5–10 real residents + 2 admin operators in the target operational area. Collect structured feedback, triage bugs, and run a rapid bug-fix cycle.

---

## 9. APPENDIX: KEY IDENTIFIERS & CONFIGURATION REFERENCE

| Config Item | Format / Value |
|---|---|
| Request Reference Format | `REQ-{6-digit zero-padded integer}` e.g., `REQ-000234` |
| Phone Number Format | E.164 international: `+255XXXXXXXXX` (Tanzania) |
| GPS Coordinate System | WGS84 / SRID 4326 |
| PostGIS Column Type | `GEOGRAPHY(POINT, 4326)` |
| API Version Prefix | `/api/v1/` |
| Admin API Version Prefix | `/api/v1/admin/` |
| JWT Access Token Expiry | 15 minutes |
| JWT Refresh Token Expiry | 7 days (rotated on each use) |
| bcrypt Cost Factor | 12 (min) |
| Supported Locales | `en` (English), `sw` (Swahili/Kiswahili) |
| Default Locale | `en` |
| GPS Accuracy Target | ≤ 20 meters (`LocationAccuracy.high`) |
| GPS Acquisition Timeout | 10 seconds (hard), 5s fallback to network |
| Max Waste Photos per Request | 3 |
| Note Max Length | 280 characters |
| History Page Size | 20 items |
| Map Active Statuses | `PENDING`, `ASSIGNED`, `IN_TRANSIT` |
| Admin Map Cache TTL | 10 seconds |
| Config Cache TTL | 1 hour |
| FCM Payload Type | `data` (not `notification`) for client-side locale control |
| WhatsApp Deep-Link Format | `https://wa.me/{E164number}?text={encodeURIComponent(message)}` |

---

*End of Document — WasteLink SRS v1.0 & Implementation Plan*
*Next revision trigger: completion of Phase 1 database review or any change to the core status machine.*
