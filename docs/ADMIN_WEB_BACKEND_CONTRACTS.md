# WASTELINK ADMIN WEB — BACKEND CONTRACT INVENTORY & DATA INTERFACES
**Document Version:** 1.0.0-ENTERPRISE  
**Date:** November 5, 2026  
**Status:** CANONICAL CONTRACT INVENTORY  
**Scope:** Client-to-Backend Firestore Operations, Data Schemas & Cloud Service Contracts  
**Governance:** Strict alignment with `firestore.rules` and pure domain models in `:core:domain`  

---

## 1. Overview & Data Schema Governance

To prevent conflicting contracts or schema drifts between Android and Web:
1. **Canonical Collection Names**:
   - Primary user profiles: `/users/{userId}`
   - Collector vetting applications: `/collector_submissions/{userId}`
   - Collector vetting history: `/collector_submissions/{userId}/history/{attemptId}`
   - User moderation ledger: `/user_moderation/{userId}`
   - Administrative audit log: `/admin_actions_log/{logId}`
   - General system audit log: `/audit_logs/{logId}`
   - Waste collection requests: `/waste_requests/{requestId}` (with legacy alias `/wasteRequests/{requestId}`)
   - Collector live telemetry: `/live_locations/{collectorId}`
   - Dispute arbitration: `/disputes/{disputeId}` (and `/service_disputes/{disputeId}`)
   - Operational fleet units: `/operational_units/{unitId}`
   - Configurable waste categories: `/waste_categories/{categoryId}` *(Pending rule deployment)*
   - Configurable service zones: `/service_zones/{zoneId}` *(Pending rule deployment)*
   - Pricing tariffs: `/pricing_rules/{ruleId}` *(Pending rule deployment)*

2. **Field Consistency & Legacy Aliases**:
   - `phoneNumber` (canonical E.164) is mirrored to legacy `phone` on write.
   - `fullName` is mirrored to legacy `name` on write.
   - `accountStatus` is mirrored to legacy `status` on write.
   - All client-generated document IDs for new requests use RFC 4122 v4 UUIDs (`id == clientGeneratedId`).

---

## 2. Operation Inventory

---

### Contract OP-01: Admin Identity Verification & Session Introspection
* **Operation Name**: `verifyAdminSession`
* **Purpose**: Verifies that the signed-in Firebase Auth user possesses the cryptographically valid custom claim `admin: true` and has an active account standing.
* **Protocol**: Firebase Auth SDK client call + Firestore Document Read.
* **Input Schema**:
  ```typescript
  interface VerifyAdminSessionInput {
    forceRefresh: boolean; // Must be true on login and periodically to catch claim revocations
  }
  ```
* **Output Schema**:
  ```typescript
  interface VerifyAdminSessionOutput {
    uid: string;
    email: string;
    emailVerified: boolean;
    isAdminClaimPresent: boolean;
    tokenClaims: {
      admin?: boolean;
      role?: string;
      [key: string]: unknown;
    };
    profile: {
      id: string;
      fullName: string;
      email: string;
      role: 'admin';
      accountStatus: 'ACTIVE' | 'RESTRICTED' | 'SUSPENDED' | 'BANNED';
      preferredLanguage: string;
    };
  }
  ```
* **Required Permission**: Valid Firebase Auth user with `claims.admin === true`.
* **Validation Rules**: `profile.accountStatus` must equal `'ACTIVE'`.
* **Failure Conditions**:
  - `auth/user-token-expired`: Token expired, must re-authenticate.
  - `auth/claims-missing`: Token does not contain `admin: true`.
  - `auth/account-locked`: Firestore `/users/{uid}` has `accountStatus != 'ACTIVE'`.
* **Audit Requirements**: Non-mutating; login event logged via OP-02.
* **Implementation Status**: **IMPLEMENTED & AUTHORIZED** (`firestore.rules` line 27).

---

### Contract OP-02: Record Administrative Audit Entry
* **Operation Name**: `createAdminAuditLog`
* **Purpose**: Records an immutable, tamper-proof record of any privileged administrative mutation.
* **Protocol**: Cloud Firestore `addDoc` or `setDoc` to `/admin_actions_log/{logId}`.
* **Input Schema**:
  ```typescript
  interface CreateAdminAuditLogInput {
    action: string;             // Min 2, max 100 chars (e.g. 'USER_SUSPENDED', 'DISPUTE_RESOLVED')
    reason: string;             // Min 2, max 1000 chars justification
    targetId?: string;          // Target entity ID (User UID, Request ID, Dispute ID)
    targetUserId?: string;      // Specific target user UID if applicable
    title: string;              // Human-readable summary title
    description: string;        // Human-readable narrative description
    iconType?: 'ADMIN' | 'USER' | 'FLEET' | 'DISPUTE' | 'CONFIG';
  }
  ```
* **Output Schema**:
  ```typescript
  interface CreateAdminAuditLogOutput {
    logId: string;
    timestampMs: number;
  }
  ```
* **Required Permission**: `request.auth.token.admin == true`.
* **Validation Rules (`firestore.rules` lines 539–558)**:
  - Document keys strictly limited to `['id', 'adminId', 'actorId', 'targetUserId', 'targetId', 'action', 'reason', 'timestampMs', 'title', 'description', 'iconType', 'createdAt']`.
  - Anti-impersonation: `actorId` or `adminId` MUST match `request.auth.uid`.
  - Immutability: Update and delete are permanently rejected (`allow update, delete: if false`).
* **Failure Conditions**: Missing actor UID, oversized strings, missing reason.
* **Audit Requirements**: Self-auditing append-only ledger.
* **Implementation Status**: **IMPLEMENTED & AUTHORIZED**.

---

### Contract OP-03: User Account Standing Moderation
* **Operation Name**: `moderateUserStanding`
* **Purpose**: Restricts, suspends, bans, or reinstates a user account with mandatory reason and moderation record.
* **Protocol**: Atomic Firestore `WriteBatch`.
* **Input Schema**:
  ```typescript
  interface ModerateUserStandingInput {
    targetUserId: string;
    action: 'RESTRICTED' | 'SUSPENDED' | 'BANNED' | 'REINSTATED';
    accountStatus: 'ACTIVE' | 'RESTRICTED' | 'SUSPENDED' | 'BANNED';
    reason: string;                     // Min 3, max 500 chars
    adminNotes?: string;                // Max 2000 chars internal notes
    suspendedUntilEpochMs?: number;     // Mandatory if accountStatus == 'SUSPENDED'
  }
  ```
* **Output Schema**:
  ```typescript
  interface ModerateUserStandingOutput {
    targetUserId: string;
    newAccountStatus: 'ACTIVE' | 'RESTRICTED' | 'SUSPENDED' | 'BANNED';
    updatedAtEpochMs: number;
  }
  ```
* **Required Permission**: `request.auth.token.admin == true`.
* **Validation Rules (`firestore.rules` lines 259–295 & 512–528)**:
  - Atomic batch must mutate `/users/{targetUserId}` and create `/user_moderation/{targetUserId}` simultaneously.
  - Admin cannot mutate `id`, `uid`, `email`, `fullName`, `phoneNumber`, `role`, `createdAtEpochMs`.
  - If `accountStatus == 'SUSPENDED'`, `suspendedUntilEpochMs` must be a positive integer.
  - Reason must be non-empty (3–500 chars).
* **Failure Conditions**: Partial write without `/user_moderation`, missing suspension timestamp.
* **Audit Requirements**: Creates `/user_moderation/{targetUserId}` and `/admin_actions_log/{logId}`.
* **Implementation Status**: **IMPLEMENTED & AUTHORIZED**.

---

### Contract OP-04: Collector Vetting Decision (Approve / Reject)
* **Operation Name**: `reviewCollectorSubmission`
* **Purpose**: Evaluates a collector's vehicle and credential submission (`PENDING_REVIEW`) and transitions it to `APPROVED` or `REJECTED`.
* **Protocol**: Atomic Firestore `WriteBatch`.
* **Input Schema**:
  ```typescript
  interface ReviewCollectorSubmissionInput {
    targetCollectorUid: string;
    decision: 'APPROVED' | 'REJECTED';
    rejectionReason?: string;           // Mandatory if decision == 'REJECTED' (3–500 chars)
    adminNotes?: string;                // Optional reviewer notes
  }
  ```
* **Output Schema**:
  ```typescript
  interface ReviewCollectorSubmissionOutput {
    targetCollectorUid: string;
    decision: 'APPROVED' | 'REJECTED';
    reviewedAtEpochMs: number;
  }
  ```
* **Required Permission**: `request.auth.token.admin == true`.
* **Validation Rules (`firestore.rules` lines 405–449 & 302–318)**:
  - Submission must currently be in `PENDING_REVIEW` (or `APPROVED` transitioning to `REJECTED`).
  - Admin cannot modify collector-submitted `vehicleType`, `vehiclePlateNumber`, `licenseNumber`, `serviceZoneId`, `submissionAttempt`.
  - If `decision == 'APPROVED'`:
    - `/users/{uid}.collectorVerificationStatus` becomes `'APPROVED'`.
    - `/users/{uid}.isVerified` becomes `true`.
    - `/collector_submissions/{uid}/history/attempt_{n}_approved` must be created.
  - If `decision == 'REJECTED'`:
    - Rejection reason is mandatory (3–500 chars).
    - `/users/{uid}.collectorVerificationStatus` becomes `'REJECTED'`.
    - `/users/{uid}.isVerified` becomes `false`.
    - `/collector_submissions/{uid}/history/attempt_{n}_rejected` must be created.
* **Failure Conditions**: Missing rejection reason on rejection, missing history record, non-atomic write.
* **Audit Requirements**: Recorded in submission history and `/admin_actions_log`.
* **Implementation Status**: **IMPLEMENTED & AUTHORIZED**.

---

### Contract OP-05: Waste Request Fleet Allocation & Dispatch
* **Operation Name**: `allocateRequestToFleet`
* **Purpose**: Assigns an unassigned pickup request in `PENDING_DISPATCH` or `FLAGGED_FOR_REVIEW` to a specific collector and vehicle.
* **Protocol**: Cloud Firestore `updateDoc` on `/waste_requests/{requestId}`.
* **Input Schema**:
  ```typescript
  interface AllocateRequestToFleetInput {
    requestId: string;
    assignedCollectorUid: string;       // Verified collector UID
    assignedVehicleId?: string;         // Vehicle identification
    agreedPrice?: number;               // Optional tariff agreement
  }
  ```
* **Output Schema**:
  ```typescript
  interface AllocateRequestToFleetOutput {
    requestId: string;
    status: 'ASSIGNED_TO_FLEET';
    assignedAt: number;
  }
  ```
* **Required Permission**: `request.auth.token.admin == true`.
* **Validation Rules (`firestore.rules` lines 705–723)**:
  - Request cannot alter `id`, `requestRef`, `clientUid`, `createdAt`.
  - Target status must be `ASSIGNED_TO_FLEET`.
  - `assignedTruckId` (or `assignedCollectorUid`) must be a non-empty string (1–128 chars).
* **Failure Conditions**: Request has already progressed to `EN_ROUTE` or beyond; collector UID is blank.
* **Audit Requirements**: Writes to `/admin_actions_log/{logId}`.
* **Implementation Status**: **IMPLEMENTED & AUTHORIZED**.

---

### Contract OP-06: Waste Request Administrative Cancellation & Quarantine
* **Operation Name**: `adminCancelOrQuarantineRequest`
* **Purpose**: Quarantines a suspicious request (`FLAGGED_FOR_REVIEW`) or cancels an uncollected request (`CANCELLED`) with administrative justification.
* **Protocol**: Cloud Firestore `updateDoc` on `/waste_requests/{requestId}`.
* **Input Schema**:
  ```typescript
  interface AdminCancelOrQuarantineInput {
    requestId: string;
    targetStatus: 'FLAGGED_FOR_REVIEW' | 'PENDING_DISPATCH' | 'CANCELLED';
    adminResolutionNote: string;        // Mandatory justification note
  }
  ```
* **Output Schema**:
  ```typescript
  interface AdminCancelOrQuarantineOutput {
    requestId: string;
    newStatus: string;
    updatedAt: number;
  }
  ```
* **Required Permission**: `request.auth.token.admin == true`.
* **Validation Rules (`firestore.rules` lines 705–723)**:
  - Permitted transitions for Admin:
    - `PENDING_DISPATCH` -> `FLAGGED_FOR_REVIEW`
    - `FLAGGED_FOR_REVIEW` -> `PENDING_DISPATCH`
    - `PENDING_DISPATCH` / `FLAGGED_FOR_REVIEW` / `ASSIGNED_TO_FLEET` -> `CANCELLED`
  - Cannot cancel requests that have already completed or verified collection (`COLLECTION_VERIFIED`, `IN_TRANSIT_TO_DUMP`, `COMPLETED`).
* **Audit Requirements**: Writes to `/admin_actions_log/{logId}`.
* **Implementation Status**: **IMPLEMENTED & AUTHORIZED**.

---

### Contract OP-07: Dispute Investigation & Grievance Arbitration
* **Operation Name**: `arbitrateDispute`
* **Purpose**: Adjudicates an open resident service grievance, transitioning dispute to `RESOLVED` or `DISMISSED` with an audit note.
* **Protocol**: Atomic Firestore `WriteBatch`.
* **Input Schema**:
  ```typescript
  interface ArbitrateDisputeInput {
    disputeId: string;
    outcome: 'RESOLVED' | 'DISMISSED';
    resolutionNote: string;             // Min 2, max 500 chars justification
  }
  ```
* **Output Schema**:
  ```typescript
  interface ArbitrateDisputeOutput {
    disputeId: string;
    status: 'RESOLVED' | 'DISMISSED';
    resolvedByUid: string;
    resolvedAtEpochMs: number;
  }
  ```
* **Required Permission**: `request.auth.token.admin == true`.
* **Validation Rules (`firestore.rules` lines 885–912 & 934–950)**:
  - Dispute must be in `OPEN` or `INVESTIGATING`.
  - Cannot modify immutable fields: `id`, `requestId`, `residentId`, `collectorId`, `reason`, `createdAt`.
  - Must supply non-empty `resolutionOutcome` (2–500 chars).
  - Creates append-only note under `/disputes/{disputeId}/notes/{noteId}`.
* **Audit Requirements**: Creates dispute note and `/admin_actions_log` entry.
* **Implementation Status**: **IMPLEMENTED & AUTHORIZED**.

---

### Contract OP-08: Waste Category Administration
* **Operation Name**: `saveWasteCategory`
* **Purpose**: Creates or updates a dynamic municipal waste category.
* **Protocol**: Cloud Firestore `setDoc` or `updateDoc` on `/waste_categories/{categoryId}`.
* **Input Schema**:
  ```typescript
  interface SaveWasteCategoryInput {
    id: string;                         // Canonical category slug (e.g. 'household', 'electronic')
    displayName: string;                // Min 2, max 80 chars
    isHazardous: boolean;
    requiresSpecialHandling: boolean;
    baseEstimateTzs?: number | null;    // Non-negative integer
    isActive: boolean;
    version: number;
  }
  ```
* **Validation Invariant**: If `isHazardous == true`, `requiresSpecialHandling` MUST be `true`.
* **Required Permission**: `request.auth.token.admin == true`.
* **Backend Dependency Status**: **PENDING BACKEND IMPLEMENTATION** (No rule match in current `firestore.rules`; UI remains in read-only / preview mode until rule is deployed).

---

### Contract OP-09: Municipal Service Zone Administration
* **Operation Name**: `saveServiceZone`
* **Purpose**: Creates or updates a municipal service zone boundary with bounding box and polygon vertices.
* **Protocol**: Cloud Firestore `setDoc` on `/service_zones/{zoneId}`.
* **Input Schema**:
  ```typescript
  interface SaveServiceZoneInput {
    zoneId: string;
    name: string;
    minLat: number;                     // -90 <= minLat <= maxLat <= 90
    maxLat: number;
    minLng: number;                     // -180 <= minLng <= maxLng <= 180
    maxLng: number;
    polygonVertices: Array<{
      latitude: number;
      longitude: number;
    }>;                                 // Minimum 3 vertices if provided; all vertices must fall inside bounding box
    isActive: boolean;
  }
  ```
* **Validation Invariant**:
  - `minLat <= maxLat` and `minLng <= maxLng`.
  - Every vertex in `polygonVertices` must satisfy `latitude in [minLat, maxLat]` and `longitude in [minLng, maxLng]`.
  - Polygon cannot self-intersect.
* **Required Permission**: `request.auth.token.admin == true`.
* **Backend Dependency Status**: **PENDING BACKEND IMPLEMENTATION** (No rule match in current `firestore.rules`; UI operates in preview / drawing inspection mode).

---

### Contract OP-10: Municipal Tariff & Pricing Rule Configuration
* **Operation Name**: `configurePricingTariff`
* **Purpose**: Sets authoritative pricing rules by category, volume tier, and optional zone multiplier.
* **Protocol**: Future Cloud Function or Firestore collection `/pricing_rules/{ruleId}`.
* **Input Schema**:
  ```typescript
  interface ConfigurePricingTariffInput {
    ruleId: string;
    categoryId: string;
    volumeTier: 'small_bag' | 'standard_bin' | 'large_load' | 'truck_load';
    basePriceTzs: number;               // Strictly positive integer
    effectiveDateEpochMs: number;
    zoneId?: string | null;
  }
  ```
* **Validation Invariant**: `basePriceTzs > 0`.
* **Required Permission**: `request.auth.token.admin == true`.
* **Backend Dependency Status**: **PENDING BACKEND IMPLEMENTATION** (Read-only / reference view in UI).
