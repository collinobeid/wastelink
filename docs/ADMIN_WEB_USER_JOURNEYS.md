# WASTELINK ADMIN WEB — ADMINISTRATIVE USER JOURNEYS & WORKFLOW SPECIFICATION
**Document Version:** 1.0.0-ENTERPRISE  
**Date:** November 5, 2026  
**Status:** CANONICAL USER JOURNEY SPECIFICATION  
**Target Application:** WasteLink Admin Web (`admin-web/`)  
**Shared Authority:** Cloud Firestore & Firebase Authentication (`rebuild/modular-workspace`)  

---

## 1. Principles of Flow-First Engineering

Every visible control in WasteLink Admin Web must have a deterministic lifecycle. Decorative fake forms, non-functioning buttons, and permanently mocked data masquerading as real platform capabilities are strictly forbidden. 

Where underlying backend infrastructure does not exist yet (e.g., dynamic pricing engine, automated lasso dispatch API), the workflow is documented here with explicit **Dependency Status: PENDING BACKEND IMPLEMENTATION**, and the UI surfaces honest informational states rather than simulated operations.

---

## 2. Core Administrative User Journeys

---

### Journey 1: Administrator Sign-In & Cryptographic Session Verification

* **Objective**: Authenticate an administrator and cryptographically verify their privileged custom claims before granting access to the operational console.
* **Who can perform**: Authorized municipal officers and platform administrators.
* **Information required**: Administrator email, password.
* **Where information comes from**: User input on `LoginView`.
* **Backend operation**:
  1. `signInWithEmailAndPassword(auth, email, password)`.
  2. `user.getIdTokenResult(true)` to inspect cryptographically signed token claims.
  3. `getDoc(/users/{uid})` to verify active profile standing.
* **Authorization required**:
  - Valid Firebase Auth user credentials.
  - Cryptographic token claim: `token.admin === true`.
  - Firestore profile `/users/{uid}.accountStatus === 'ACTIVE'`.
* **During loading**: Submit button displays an inline spinner; email and password inputs are disabled to prevent duplicate submissions.
* **After success**: Session state updates in `AuthContext`; user is redirected to the requested protected route or `/dashboard`.
* **After failure**:
  - *Bad credentials*: Generic error banner: "Invalid email or password."
  - *Token lacks `admin === true`*: Immediate forced sign-out, redirection to `/unauthorized` with message: "Access denied: Account lacks administrative custom claims. Contact your system administrator."
  - *Account suspended/banned*: Immediate sign-out with status explanation.
* **Idempotency & Replay**: Safe to repeat.
* **Audit record**: Writes to `/audit_logs/{logId}` (`action: 'ADMIN_SIGN_IN'`).
* **Tests proving workflow**:
  - `AuthService.test.ts`: Verify valid admin token succeeds; non-admin token triggers forced sign-out and error.
  - `ProtectedRoute.test.tsx`: Verify unauthenticated users redirect to `/login`; non-admin users redirect to `/unauthorized`.
* **Implementation Status**: **READY FOR CLIENT INTEGRATION** (Enforced by Firebase Auth + `firestore.rules`).

---

### Journey 2: Executive Dashboard Data Loading & Health Radar

* **Objective**: Render high-level municipal operational metrics, active requests, pending collector vetting, open disputes, and recent audit activity.
* **Who can perform**: Any authenticated administrator with verified `admin === true`.
* **Information required**: Date range filter (`TODAY`, `WEEK`, `MONTH`, `LIFETIME`).
* **Where information comes from**:
  - Total residents: Count query on `/users` where `role == 'resident'`.
  - Active collectors: Count query on `/users` where `role == 'collector'` and `collectorVerificationStatus == 'APPROVED'`.
  - Pending collector vetting: Count query on `/collector_submissions` where `status == 'PENDING_REVIEW'`.
  - Open requests: Count query on `/waste_requests` where `status == 'PENDING_DISPATCH'`.
  - Active fleet in field: Count query on `/waste_requests` where `status in ['ASSIGNED_TO_FLEET', 'EN_ROUTE', 'ARRIVED_AT_LOCATION', 'COLLECTION_VERIFIED', 'IN_TRANSIT_TO_DUMP']`.
  - Completed collections: Count query on `/waste_requests` where `status == 'COMPLETED'`.
  - Open grievances: Count query on `/disputes` (or `/service_disputes`) where `status in ['OPEN', 'INVESTIGATING', 'PENDING']`.
  - Recent audit entries: Paged query on `/admin_actions_log` (limit 10, ordered by `timestampMs` descending).
* **Backend operation**: Parallel read queries against Cloud Firestore collections.
* **Authorization required**: `request.auth.token.admin == true` (enforced by `firestore.rules` on all queried collections).
* **During loading**: Skeleton cards with shimmer loading states; no layout shift.
* **After success**: Metric cards render verified figures with percentage trend indicators; audit ledger renders timestamps and actor details.
* **After failure**: Non-blocking error cards with "Retry Loading" buttons; existing cached metrics retained if available.
* **Idempotency & Replay**: Safe read-only operation.
* **Audit record**: None for read-only retrieval.
* **Tests proving workflow**:
  - `DashboardView.test.tsx`: Verifies metrics render accurately from mock Firestore queries and handle partial network errors gracefully.
* **Implementation Status**: **READY FOR CLIENT INTEGRATION** (Direct Firestore queries supported by existing security rules).

---

### Journey 3: User Inspection, Account Moderation & Collector Vetting

* **Objective**: Inspect resident, collector, and administrator profiles; review submitted collector vetting documents; approve or reject collector applications; suspend or reinstate accounts with mandatory audit reasons.
* **Who can perform**: Authorized platform administrators.
* **Information required**:
  - Target user UID.
  - For moderation: `action` (`RESTRICTED`, `SUSPENDED`, `BANNED`, `REINSTATED`), `reason` (3–500 chars), optional `suspendedUntilEpochMs`.
  - For collector vetting: Decision (`APPROVED` or `REJECTED`), optional `rejectionReason` (mandatory if rejected).
* **Where information comes from**: Admin form input on `UserDetailModal`.
* **Backend operation**:
  - *Moderation*: Atomic batch write:
    1. Update `/users/{userId}`: `accountStatus`, `status`, `statusReason`, `suspendedUntilEpochMs`, `updatedAtEpochMs`.
    2. Create `/user_moderation/{userId}`: `action`, `reason`, `moderatedByUid: request.auth.uid`, `moderatedAtEpochMs`.
    3. Create `/admin_actions_log/{logId}`: `action`, `targetUserId`, `reason`.
  - *Collector Vetting*: Atomic batch write:
    1. Update `/collector_submissions/{userId}`: `status: 'APPROVED' | 'REJECTED'`, `reviewedByUid`, `reviewedAtEpochMs`, `rejectionReason`.
    2. Update `/users/{userId}`: `collectorVerificationStatus`, `isVerified: true | false`, `collectorRejectionReason`.
    3. Create `/collector_submissions/{userId}/history/{attemptId}`: `eventType: 'APPROVED' | 'REJECTED'`.
    4. Create `/admin_actions_log/{logId}`.
* **Authorization required**: `request.auth.token.admin == true`.
* **During loading**: Action buttons display inline spinner and lock modal dismiss to prevent double-writes.
* **After success**: Toast notification confirms state change; user table row updates immediately; modal closes.
* **After failure**: Error banner details rejection reason (e.g. rule assertion failed); state is rolled back.
* **Idempotency & Replay**: Actions enforce strict state preconditions (e.g. cannot approve an already approved collector).
* **Audit record**: Mandatory `/user_moderation` and `/admin_actions_log` entries created atomically.
* **Tests proving workflow**:
  - `UserService.test.ts`: Verifies atomic batch construction matches `firestore.rules` field whitelists.
  - `CollectorVettingModal.test.tsx`: Proves rejection requires non-empty reason; approval sets verified flags.
* **Implementation Status**: **READY FOR CLIENT INTEGRATION** (Fully permitted by `firestore.rules` Sections 2, 3, 3a, 4, 5).

---

### Journey 4: Waste Category Management & Special Handling Rules

* **Objective**: Create, update, activate, and deactivate municipal waste categories; configure hazardous material classifications and quotation requirements.
* **Who can perform**: Municipal waste administration supervisors.
* **Information required**: Category ID, display name, hazardous flag, special handling flag, base estimate tariff (in TZS), active status.
* **Where information comes from**: Admin form input on `CategoryManagementView`.
* **Backend operation**: Upsert into `/waste_categories/{categoryId}`.
* **Authorization required**: `request.auth.token.admin == true`.
* **Backend Dependency Status**: **PENDING BACKEND IMPLEMENTATION**.
  > [!IMPORTANT]
  > In the current `firestore.rules` on disk, `/waste_categories/{categoryId}` has NO explicit match block and falls into the default-deny `match /{document=**} { allow read, write: if false; }`.
  > Although `docs/MODULE_2_RESIDENT_REQUEST_ARCHITECTURE.md` specifies:
  > `match /waste_categories/{categoryId} { allow get, list: if isAuthenticated(); allow write: if isAdmin(); }`, this rule has not yet been merged into `firestore.rules`.
  > Therefore, the Admin Web must display category administration in **read-only / fixture mode** until this security rule or Cloud Function is formally deployed.
* **During loading**: Table shows loading skeleton.
* **After success**: UI list updates dynamically.
* **After failure**: Surfaces clear error stating the permission denial.
* **Audit record**: Will write to `/admin_actions_log/{logId}` upon backend activation.
* **Tests proving workflow**: `CategoryView.test.tsx` verifies UI rendering, validation rules, and honest disabled state for pending backend operations.

---

### Journey 5: Interactive Service Zone Management & Polygon Boundaries

* **Objective**: Define, inspect, activate, and deactivate municipal service zones; configure axis-aligned bounding boxes (AABB) and planar polygon vertices for geographic containment verification.
* **Who can perform**: Municipal GIS officers and platform administrators.
* **Information required**: Zone ID, zone name, bounding box coordinates (`minLat`, `maxLat`, `minLng`, `maxLng`), optional sequential polygon vertices (`List<GeoCoordinates>`, minimum 3), active status.
* **Where information comes from**: MapLibre/Leaflet interactive polygon drawing tool on `ServiceZoneView`.
* **Backend operation**: Upsert into `/service_zones/{zoneId}`.
* **Authorization required**: `request.auth.token.admin == true`.
* **Backend Dependency Status**: **PENDING BACKEND IMPLEMENTATION**.
  > [!IMPORTANT]
  > Like waste categories, `/service_zones/{zoneId}` currently has NO rule block in `firestore.rules` and falls under default-deny.
  > Furthermore, drawn polygons require geometric topological validation (no self-intersections, valid WGS 84 vertices completely enclosed by the bounding box).
  > The Admin Web must allow administrators to explore zones in visual preview mode, but clearly label live mutations as pending backend rule deployment.
* **During loading**: Map renders vector base tiles with loading overlay.
* **After success**: Zone polygon renders as an emerald or blue semi-transparent overlay on the map.
* **After failure**: Boundary validation errors highlighted with red vertices.
* **Idempotency & Replay**: Safe to re-save zone definitions.
* **Audit record**: Writes to `/admin_actions_log` upon backend activation.
* **Tests proving workflow**: `ServiceZoneValidator.test.ts` (verifying Jordan curve theorem, AABB bounds, vertex validation in TypeScript).

---

### Journey 6: Pricing & Tariff Configuration

* **Objective**: Configure municipal base tariffs, volume tier rates, and zone-specific price multipliers; inspect historical pricing changes.
* **Who can perform**: Authorized municipal tariff administrators.
* **Information required**: Waste category ID, volume tier (`SMALL_BAG`, `STANDARD_BIN`, `LARGE_LOAD`, `TRUCK_LOAD`), base price in TZS, effective date.
* **Where information comes from**: Tariff schedule form on `PricingManagementView`.
* **Backend operation**: Mutation against `/pricing_rules/{ruleId}` or Cloud Function `updatePricingTariff`.
* **Authorization required**: `request.auth.token.admin == true`.
* **Backend Dependency Status**: **PENDING BACKEND IMPLEMENTATION**.
  > [!IMPORTANT]
  > No pricing rule schema or security rules currently exist in `firestore.rules`.
  > Client-side pricing calculations must NEVER override authoritative backend pricing.
  > Pricing in the Admin Web is presented as a **Read-Only Reference / Pending Backend** view until the authoritative pricing microservice is approved.
* **Tests proving workflow**: `PricingView.test.tsx` tests validation and read-only transparency.

---

### Journey 7: Waste Request Lifecycle Inspection & Dispatch Allocation

* **Objective**: Search, filter, inspect, assign to fleet, flag for review, or cancel customer waste pickup requests across all 13 canonical lifecycle states.
* **Who can perform**: Municipal dispatch supervisors and administrators.
* **Information required**: Request ID, action (`ASSIGN_TO_FLEET`, `FLAG_FOR_REVIEW`, `RELEASE_FLAG`, `CANCEL`), assigned collector UID, assigned vehicle ID, administrative resolution note.
* **Where information comes from**: Request detail drawer on `WasteRequestListView`.
* **Backend operation**: Update on `/waste_requests/{requestId}`:
  - Allocation: `status: 'ASSIGNED_TO_FLEET'`, `assignedTruckId: collectorUid` (legacy alias) or `assignedCollectorUid`, `assignedVehicleId`, `assignedAt: request.time`.
  - Quarantine: `status: 'FLAGGED_FOR_REVIEW'`.
  - Admin Cancellation: `status: 'CANCELLED'`, `adminResolutionNote: note`, `updatedAt: request.time`.
* **Authorization required**: `request.auth.token.admin == true` (enforced by `firestore.rules` lines 705–723).
* **During loading**: Dispatch action button shows spinner; drawer interaction locked.
* **After success**: Drawer closes; request status badge updates immediately via real-time listener.
* **After failure**: Error toast surfaces specific validation reason (e.g. collector not verified or request already in route).
* **Idempotency & Replay**: Governed by `firestore.rules` state machine.
* **Audit record**: Writes to `/admin_actions_log/{logId}` atomically.
* **Tests proving workflow**: `RequestService.test.ts` tests state transition compliance with `RequestStateTransitionValidator`.
* **Implementation Status**: **READY FOR CLIENT INTEGRATION** (Permitted by `firestore.rules` Section 6).

---

### Journey 8: Dispute Investigation & Grievance Arbitration

* **Objective**: Review customer service disputes filed against failed or completed pickups; examine gate photos, failure reasons, and collector Proof of Collection; arbitrate outcome (`RESOLVED` or `DISMISSED`) with an auditable justification note.
* **Who can perform**: Customer service arbitrators and administrators.
* **Information required**: Dispute ID, target request ID, decision (`RESOLVED` or `DISMISSED`), resolution outcome note (2–500 chars).
* **Where information comes from**: Dispute review pane on `DisputeDetailView`.
* **Backend operation**:
  - Update `/disputes/{disputeId}` or `/service_disputes/{disputeId}`: `status: 'RESOLVED' | 'DISMISSED'`, `resolutionOutcome: note`, `resolvedByUid: request.auth.uid`.
  - Create note in `/disputes/{disputeId}/notes/{noteId}`: `text: note`, `adminId: request.auth.uid`, `timestampMs: currentTime`.
  - Create `/admin_actions_log/{logId}`.
* **Authorization required**: `request.auth.token.admin == true` (enforced by `firestore.rules` lines 885–912 & 934–950).
* **During loading**: Resolution buttons disabled with loading indicator.
* **After success**: Dispute status changes to terminal badge (`RESOLVED` or `DISMISSED`); resolution note added to timeline.
* **After failure**: Error banner rendered; dispute remains in review state.
* **Idempotency & Replay**: Terminal states cannot be re-arbitrated.
* **Audit record**: Atomic dispute note and admin log entry.
* **Tests proving workflow**: `DisputeService.test.ts` verifying `firestore.rules` dispute schema compliance.
* **Implementation Status**: **READY FOR CLIENT INTEGRATION** (Permitted by `firestore.rules` Section 8).

---

### Journey 9: Operational Reports, Analytics & Data Export

* **Objective**: Aggregate municipal metrics by date range, waste category, and service zone; generate verifiable CSV exports of collections and dispute resolution performance.
* **Who can perform**: Municipal platform directors and administrators.
* **Information required**: Start date, end date, filter attributes (zone, category, collector).
* **Where information comes from**: Firestore queries against `/waste_requests` and `/disputes`.
* **Backend operation**: Filtered queries with client-side CSV formatting.
* **Authorization required**: `request.auth.token.admin == true`.
* **Backend Dependency Status**: **PARTIALLY READY**. Basic queries on requests and disputes are supported by security rules; complex server-side aggregation pipelines (BigQuery export or Cloud Function summary rollups) are pending future backend development.
* **During loading**: Progress bar indicates query retrieval.
* **After success**: Native browser download of UTF-8 encoded CSV file with complete metadata.
* **After failure**: Notification alerting user to query timeout or size limit.
* **Tests proving workflow**: `ExportService.test.ts` verifying CSV formatting and escaping.

---

### Journey 10: Administrative Audit Log Review

* **Objective**: Search, filter, and inspect the immutable, append-only ledger of all privileged administrative decisions.
* **Who can perform**: Platform security officers and administrators.
* **Information required**: Page token, search keyword, date filter, actor filter.
* **Where information comes from**: `/admin_actions_log` and `/audit_logs`.
* **Backend operation**: Cursor-paged Firestore query ordered by `timestampMs` descending.
* **Authorization required**: `request.auth.token.admin == true` (`firestore.rules` lines 537 & 565).
* **During loading**: Table displays loading skeletons.
* **After success**: Table populates with timestamp, actor UID, action code, target ID, and reason.
* **After failure**: Retryable error state.
* **Idempotency & Replay**: Read-only query.
* **Tests proving workflow**: `AuditLogView.test.tsx` verifying paged list retrieval and filter behavior.
* **Implementation Status**: **READY FOR CLIENT INTEGRATION** (Permitted by `firestore.rules` Section 5).
