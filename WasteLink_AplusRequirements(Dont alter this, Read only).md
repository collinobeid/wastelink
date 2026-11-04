# WasteLink — A+ Premium Product Requirements Specification
### Lead System Architect & Principal UI/UX Design Document

---

## MODULE 1: AUTHENTICATION & IDENTITY

### REQ-001: Splash-to-Auth Cold Start Flow
**Core Logic:** On app launch, check Firebase Auth session token validity before rendering any UI. If a valid session exists, silently route to role-based home. If invalid/expired, route to Auth Gateway. Session check must complete within a defined timeout with fallback to login.
**Pro UI/UX Specification:** Branded splash with animated logo mark (scale + fade, 400ms ease-out). No blank white frame ever visible — splash persists until first frame of destination screen is composed. Use a skeleton shimmer for the destination screen's shell while data loads, never a spinner-only screen.

### REQ-002: Email/Password Registration
**Core Logic:** Validate email format and password strength server-side and client-side before Firebase Auth `createUserWithEmailAndPassword` call. On success, write `AppUser` doc atomically with default role `resident`.
**Pro UI/UX Specification:** Real-time inline validation under each field (green check / red cross transitions at 150ms). Password strength meter with 4-segment gradient bar (red→amber→yellow→green). Submit button disabled state uses reduced opacity + no-cursor affordance; enabled state has subtle elevation shadow.

### REQ-003: Social/Phone Auth Option
**Core Logic:** Support Google Sign-In and phone OTP as alternate Firebase Auth providers, merging to the same `AppUser` schema.
**Pro UI/UX Specification:** Provider buttons use official brand iconography at 24dp, equal-width row below primary form, separated by an "OR" divider with hairline rules on each side.

### REQ-004: Genesis Protocol Admin Elevation
**Core Logic:** On login, check email against a secured remote-config allowlist (not hardcoded in APK) and elevate role to `admin` server-side via Cloud Function, never client-side.
**Pro UI/UX Specification:** No visible UI difference at login; role reveal happens only after routing, avoiding any flash of incorrect role-based UI ("role flicker").

### REQ-005: Login Error Handling
**Core Logic:** Map Firebase Auth error codes (wrong-password, user-not-found, too-many-requests, network-error) to distinct UI states.
**Pro UI/UX Specification:** Error banner slides down from top of form (200ms spring), color-coded by severity, auto-dismiss on next keystroke. Rate-limit errors show a countdown timer chip.

### REQ-006: Session Persistence & Silent Refresh
**Core Logic:** Use Firebase token auto-refresh; detect revoked sessions (banned users) via custom claims listener and force logout.
**Pro UI/UX Specification:** Forced logout shows a modal explaining why ("Your account access has changed") rather than silently dumping the user to login — prevents confusion and support tickets.

### REQ-007: Password Reset Flow
**Core Logic:** Trigger Firebase `sendPasswordResetEmail`, log the request in an audit collection for abuse monitoring.
**Pro UI/UX Specification:** Confirmation state replaces the form entirely (not a toast) with an illustration and clear next-step copy ("Check your inbox"), plus a resend-with-cooldown button (60s countdown).

### REQ-008: Multi-Factor Authentication for Admins
**Core Logic:** Require TOTP or SMS second factor for any account holding `admin` custom claim before granting dashboard access.
**Pro UI/UX Specification:** 6-digit OTP input with auto-advance between boxes, auto-submit on completion, haptic tick per digit on supported devices.

### REQ-009: Account Deletion & GDPR Export
**Core Logic:** Cloud Function cascades deletion across `waste_requests`, `AppUser`, storage objects; export packages user data as downloadable JSON before deletion.
**Pro UI/UX Specification:** Two-step destructive confirmation (type "DELETE" to confirm), irreversible-action red button styling, progress indicator during cascade delete with per-collection status ticks.

### REQ-010: Biometric Re-Auth for Sensitive Actions
**Core Logic:** Gate account deletion, payment method changes, and admin moderation actions behind device biometric or PIN re-auth.
**Pro UI/UX Specification:** Native biometric sheet triggered contextually, with a graceful password fallback link if biometrics fail 2x.

---

## MODULE 2: DYNAMIC PROFILE HUB

### REQ-011: Role-Aware Profile Shell
**Core Logic:** ProfileViewModel resolves user role once and exposes a sealed state (`Resident`, `Collector`, `Admin`) driving composable selection — no role branching in the View layer.
**Pro UI/UX Specification:** Shared header (avatar, name, role badge) persists across role variants for visual continuity; only the content region below animates in/out via crossfade (250ms) when role data resolves.

### REQ-012: Resident Waste Request History
**Core Logic:** Paginated Firestore query (`limit` + `startAfter` cursor) on `waste_requests` filtered by `userId`, ordered by `createdAt desc`.
**Pro UI/UX Specification:** Infinite-scroll list with skeleton placeholder rows (3 shimmering cards) while loading next page. Each row shows status chip (color-coded: pending=amber, in-progress=blue, completed=green, cancelled=gray) with icon.

### REQ-013: Collector Live Metrics Cards
**Core Logic:** Aggregate query counts (Total Pickups, Active Routes) computed via Firestore aggregation queries or a maintained counter doc for scale.
**Pro UI/UX Specification:** Metric cards use large numeral typography (32sp bold) with label beneath (12sp uppercase, letter-spacing 0.5). Numbers animate count-up (ease-out, 600ms) on first load instead of appearing instantly.

### REQ-014: Admin God Mode Entry Portal
**Core Logic:** Single CTA routes to `AdminDashboard` graph; verify custom claim server-side on route guard, not just UI hide.
**Pro UI/UX Specification:** Distinct visual treatment (dark gradient card, subtle glow border) differentiating it from standard profile actions to signal elevated privilege.

### REQ-015: Avatar Upload & Crop
**Core Logic:** Image picked via system intent, client-side compressed to <500KB, uploaded to Firebase Storage under `avatars/{uid}`, URL written to `AppUser.photoUrl`.
**Pro UI/UX Specification:** Circular crop overlay with pinch-zoom and drag reposition; upload progress ring around avatar itself rather than a separate progress bar.

### REQ-016: Profile Edit Inline Mode
**Core Logic:** Toggle editable state for name/phone/address fields; PATCH only changed fields to Firestore to minimize writes.
**Pro UI/UX Specification:** Fields morph from static text to underlined input on edit-mode toggle (no jarring layout shift). Save button appears only when a diff exists (dirty-state detection).

### REQ-017: Empty State Design for New Users
**Core Logic:** Detect zero-history state distinctly from loading state.
**Pro UI/UX Specification:** Custom illustration + encouraging copy + primary CTA ("Request your first pickup") rather than a blank list — empty states must never look like a bug.

### REQ-018: Role Badge System
**Core Logic:** Render badge from role + verification status fields (`isVerifiedCollector`, `tenureLevel`).
**Pro UI/UX Specification:** Badge uses tiered iconography (bronze/silver/gold outline) for collector tenure, tooltip on tap explaining criteria.

### REQ-019: Logout Confirmation
**Core Logic:** Clear local Firebase session and any cached Room/SQLDelight data on logout.
**Pro UI/UX Specification:** Bottom sheet confirmation (not full dialog) with destructive-styled confirm button, cancel dismisses with swipe-down gesture support.

### REQ-020: Profile Loading Skeletons
**Core Logic:** Distinguish "no data yet" (skeleton) from "data confirmed empty" (empty state) using explicit loading enum states.
**Pro UI/UX Specification:** Skeleton shapes exactly match final content geometry (same card sizes, avatar circle) to prevent layout jump on data arrival.

---

## MODULE 3: ADMIN DASHBOARD — OVERVIEW

### REQ-021: 2x2 Metric Grid Live Binding
**Core Logic:** `AdminDashboardViewModel` runs parallel Firestore snapshot listeners for active clients, unverified fleets, disputes, revenue proxy metric; combine via `combine()` flow operator.
**Pro UI/UX Specification:** Grid uses equal-height cards with consistent 16dp gutters; live updates pulse the changed numeral briefly (background flash fade, 400ms) so admins notice real-time changes without a full re-render jolt.

### REQ-022: Activity Log Real-Time Feed
**Core Logic:** Stream from an `activity_log` collection ordered by timestamp desc, capped listener (last 50 entries) to control read costs.
**Pro UI/UX Specification:** New entries slide in from top with a subtle highlight background that fades over 2s, timestamps shown as relative ("2m ago") with tooltip on hover for absolute time.

### REQ-023: Metric Drill-Down Navigation
**Core Logic:** Tapping a metric card deep-links to the corresponding filtered management tab with query params pre-applied.
**Pro UI/UX Specification:** Shared-element transition where the tapped card visually expands into the destination screen's header (Material motion "container transform").

### REQ-024: Dashboard Date Range Filter
**Core Logic:** Metrics recompute against a selected date window via parameterized Firestore queries or pre-aggregated daily rollup docs.
**Pro UI/UX Specification:** Segmented control (Today/Week/Month/Custom) with custom range opening a calendar picker sheet; selected range persists in ViewModel state across navigation.

### REQ-025: Admin Dashboard Offline Resilience
**Core Logic:** Cache last-known metrics locally; display staleness indicator if Firestore listener disconnects.
**Pro UI/UX Specification:** Small "Live" green dot becomes gray "Updated 3m ago" label when connection drops, without blocking the UI.

### REQ-026: Export Dashboard Report
**Core Logic:** Server-side Cloud Function compiles CSV/PDF snapshot of current metrics and activity log.
**Pro UI/UX Specification:** Export button shows inline progress spinner replacing icon during generation, then triggers native share sheet automatically on completion — no extra tap required.

### REQ-027: Role-Based Widget Visibility
**Core Logic:** Dashboard widgets are driven by a remote-config permission matrix so sub-admin roles see restricted views.
**Pro UI/UX Specification:** Restricted widgets show a subtle lock icon overlay with "Contact super-admin" tooltip instead of being silently hidden, aiding discoverability of the permission model.

### REQ-028: Alert/Anomaly Banner
**Core Logic:** Cloud Function flags anomalies (e.g., dispute spike) and writes to an `alerts` collection surfaced at dashboard top.
**Pro UI/UX Specification:** Banner uses warning color system, dismissible per-session, with a "View details" inline link that doesn't require full navigation.

---

## MODULE 4: WASTE REQUEST CREATION FLOW

### REQ-029: Multi-Step Wizard State Machine
**Core Logic:** `RequestViewModel` holds a sealed step state (`Category`, `Location`, `Volume`, `Confirm`) with validated forward-only transitions and back navigation preserving prior input.
**Pro UI/UX Specification:** Top progress indicator (4-segment bar) fills incrementally; step transitions use horizontal slide (300ms ease-in-out) matching physical "moving forward" metaphor. Back gesture/button reverses the same animation.

### REQ-030: Category Selection Grid
**Core Logic:** Categories loaded from a Firestore-backed config collection (not hardcoded) to allow remote updates without app release.
**Pro UI/UX Specification:** Icon-tile grid (2 columns), selected state uses filled background + checkmark badge, unselected uses outline style; selection triggers haptic tap.

### REQ-031: Location Capture via Map + GPS
**Core Logic:** Use device FusedLocationProvider for current position; allow manual pin-drop override; reverse-geocode to human-readable address for storage.
**Pro UI/UX Specification:** Map centers with a bouncing drop-pin animation on load; draggable pin updates address label live below the map with debounced reverse-geocode calls (500ms) to avoid API spam.

### REQ-032: Volume/Quantity Selector
**Core Logic:** Store volume as an enum/tier (Small/Medium/Large/Bulk) mapped to pricing/logistics weight server-side.
**Pro UI/UX Specification:** Custom stepper with illustrated bin-size icons scaling visually per tier, selected tier highlighted with animated border pulse.

### REQ-033: Confirmation Summary Screen
**Core Logic:** Aggregate all wizard state into a read-only summary before final Firestore write; edit links jump back to specific steps preserving other steps' data.
**Pro UI/UX Specification:** Summary card list with right-aligned "Edit" chevron links per section; final CTA uses full-width prominent button with loading spinner replacing label text during write (not overlay, to avoid layout shift).

### REQ-034: Request Submission & Optimistic UI
**Core Logic:** Write to Firestore with a client-generated ID; show success state before server ack using optimistic concurrency, roll back gracefully on failure.
**Pro UI/UX Specification:** Success screen with checkmark draw-on animation (SVG path animation, 500ms), auto-navigates to home after 2s or on tap.

### REQ-035: Draft Auto-Save
**Core Logic:** Persist in-progress wizard state to local storage (DataStore/Room) so an interrupted flow (app kill, call interruption) can resume.
**Pro UI/UX Specification:** On relaunch, show a resume prompt ("Continue your pickup request?") rather than silently restoring — respects user intent.

### REQ-036: Field-Level Validation & Error Prevention
**Core Logic:** Disable "Next" until step-required fields pass validation; server also re-validates on write (never trust client only).
**Pro UI/UX Specification:** Disabled Next button uses reduced-contrast styling with an inline hint text explaining what's missing, appearing only after first interaction attempt (not preemptively, to avoid nagging).

### REQ-037: Request Type Presets/Templates
**Core Logic:** Allow saving a request configuration as a reusable template doc under the user's profile.
**Pro UI/UX Specification:** "Save as template" toggle on confirm screen; templates surface as quick-action chips on the request entry point for repeat users.

### REQ-038: Cancellation Window Logic
**Core Logic:** Allow cancellation only while status is `pending`, enforced via Firestore security rules checking status field.
**Pro UI/UX Specification:** Cancel button visibility conditionally rendered per status; disabled state shows tooltip explaining why cancellation is no longer available.

### REQ-039: Accessibility for Wizard Flow
**Core Logic:** Each step exposes semantic labels and focus order for screen readers; state changes announced via live regions.
**Pro UI/UX Specification:** TalkBack/VoiceOver announces step number ("Step 2 of 4: Location") on entry; all touch targets meet 48dp minimum.

### REQ-040: Duplicate Request Prevention
**Core Logic:** Server-side check for an existing `pending` request at the same location within a cooldown window before allowing new submission.
**Pro UI/UX Specification:** Non-blocking warning dialog ("You have a similar pending request") with options to view existing or proceed anyway.

---

## MODULE 5: IMAGE CAPTURE & UPLOAD

### REQ-041: Gate Photo Capture Integration
**Core Logic:** Wire Camera/Gallery intent to populate `gatePhotoBase64` (or preferably migrate to Storage URL reference instead of Base64 for performance), with client-side compression before upload.
**Pro UI/UX Specification:** Camera viewfinder overlay with a ghosted gate-outline guide to help users frame the shot consistently; captured thumbnail appears with retake/confirm buttons.

### REQ-042: Proof-of-Collection (POC) Photo
**Core Logic:** Collector-side capture on job completion, uploaded to Storage under `poc/{requestId}`, triggers status transition to `completed` only after successful upload confirmation.
**Pro UI/UX Specification:** Full-screen capture flow with a mandatory-photo gate (cannot mark complete without POC), progress bar tied to actual upload bytes transferred, not indeterminate.

### REQ-043: Migration from Base64 to Storage URLs
**Core Logic:** Refactor `WasteRequest` schema to store Storage download URLs instead of inline Base64 to reduce document size and Firestore read costs.
**Pro UI/UX Specification:** No user-facing change required, but enables faster image loading via CDN caching — surfaced as improved perceived performance (images load progressively with blur-up placeholder).

### REQ-044: Upload Retry & Offline Queue
**Core Logic:** Failed uploads queue locally (WorkManager/BGTaskScheduler) and retry with exponential backoff when connectivity restores.
**Pro UI/UX Specification:** Pending-upload indicator badge on the request card ("Photo syncing...") with manual retry tap affordance if auto-retry exhausts attempts.

### REQ-045: Image Compression Pipeline
**Core Logic:** Resize to max 1600px longest edge, JPEG quality 80%, strip EXIF GPS metadata for privacy unless explicitly needed for verification.
**Pro UI/UX Specification:** Compression happens with a brief "Optimizing photo..." micro-loading state so users understand the short delay before upload begins.

### REQ-046: Multi-Photo Gallery View
**Core Logic:** Support array of photo references per request rather than single fields, enabling before/after documentation.
**Pro UI/UX Specification:** Horizontal photo carousel with page dots, pinch-to-zoom full-screen viewer on tap, swipe-to-dismiss gesture.

### REQ-047: Photo Verification Badge
**Core Logic:** Flag requests with both gate and POC photos present as "Fully Documented" via a computed boolean.
**Pro UI/UX Specification:** Small camera-check icon badge on request cards signaling documentation completeness to admins reviewing disputes.

---

## MODULE 6: COLLECTOR ROUTE & MISSION SCREEN

### REQ-048: Live Route Queue Backend Wiring
**Core Logic:** Replace mock data with a real-time Firestore query filtering `waste_requests` by `assignedCollectorId` and status `in-progress`/`assigned`, ordered by an optimized route sequence.
**Pro UI/UX Specification:** Route list renders as a numbered stop sequence (1, 2, 3...) with connecting line visual between stops, current active stop highlighted with accent color and pulse animation.

### REQ-049: Live GPS Position Tracking
**Core Logic:** Foreground service streams collector location at intervals to a `live_locations/{collectorId}` doc via Firestore writes (throttled to control cost, e.g., every 10-15s or on 20m movement delta).
**Pro UI/UX Specification:** Truck icon on map interpolates smoothly between GPS pings (animated marker movement, not teleporting jumps) using linear interpolation over the update interval.

### REQ-50: Route Optimization Algorithm
**Core Logic:** Server-side Cloud Function computes optimal stop order via a routing API (e.g., distance-matrix + nearest-neighbor or TSP heuristic) when new requests are assigned.
**Pro UI/UX Specification:** "Optimize Route" button shows a route-recalculating animation (dashed line redrawing) rather than an abrupt list reorder, helping collectors track what changed.

### REQ-051: Turn-by-Turn Navigation Handoff
**Core Logic:** Deep-link to native Maps app (Google Maps/Apple Maps) with destination coordinates pre-filled via intent/URL scheme.
**Pro UI/UX Specification:** "Navigate" button uses the destination app's brand color accent subtly, with a bottom sheet confirming handoff before leaving WasteLink.

### REQ-052: Stop Completion Workflow
**Core Logic:** Tapping "Complete Stop" requires POC photo (REQ-042) and updates `waste_requests.status` transactionally, then advances the queue.
**Pro UI/UX Specification:** Swipe-to-complete gesture on the stop card (iOS-style) with a satisfying checkmark-fill animation and haptic success feedback on release.

### REQ-053: Real-Time ETA Calculation
**Core Logic:** Compute ETA to next stop using current GPS + routing API distance/duration estimate, refreshed periodically.
**Pro UI/UX Specification:** ETA chip updates with a subtle number-roll animation rather than instant text swap when the value changes.

### REQ-054: Map Clustering for Dense Stop Areas
**Core Logic:** Client-side marker clustering algorithm groups nearby pins at low zoom levels, expanding on zoom-in.
**Pro UI/UX Specification:** Cluster bubbles show count badge, tapping zooms map with animated camera transition to that cluster's bounds.

### REQ-055: Offline Route Caching
**Core Logic:** Cache the day's assigned route and map tiles locally so collectors in low-connectivity areas retain functionality.
**Pro UI/UX Specification:** Offline indicator banner ("Using cached route — updates will sync when online") rather than a broken/blank map.

### REQ-056: Collector Break/Pause State
**Core Logic:** Explicit status field (`onBreak: boolean`) pauses ETA calculations and hides collector from active dispatch pool.
**Pro UI/UX Specification:** Prominent toggle in mission screen header, visually distinct (amber background) when active, with auto-reminder after extended break duration.

### REQ-057: Multi-Stop Reordering (Manual Override)
**Core Logic:** Allow collector to manually drag-reorder stops when local knowledge beats the algorithm; persist override to Firestore.
**Pro UI/UX Specification:** Long-press-and-drag reordering with elevation shadow on the dragged card and auto-scroll when dragging near list edges.

---

## MODULE 7: LIVE MAP TRACKING (RESIDENT-FACING)

### REQ-058: Resident Real-Time Truck View
**Core Logic:** `ResidentTrackingScreen` subscribes to the assigned collector's `live_locations` doc via snapshot listener, scoped by security rules to only the resident with an active request from that collector.
**Pro UI/UX Specification:** Truck marker with subtle shadow and directional rotation matching heading; map auto-pans to keep truck+destination in frame without disorienting the user (debounced camera updates).

### REQ-058b: WebSocket/Firebase Streaming Architecture
**Core Logic:** Evaluate Firestore listener cost vs. a dedicated Realtime Database or WebSocket layer for high-frequency GPS streams; implement chosen architecture with connection-state handling.
**Pro UI/UX Specification:** Connection status indicator (small dot: green=live, amber=reconnecting, red=offline) unobtrusively placed in map corner.

### REQ-059: Arrival Notification Trigger
**Core Logic:** Geofence check (server-side distance calc) triggers push notification when collector is within a configurable radius of resident's location.
**Pro UI/UX Specification:** Push notification with rich preview showing mini-map thumbnail and "Truck arriving in ~5 min" copy.

### REQ-060: Tracking Screen Empty/Unassigned State
**Core Logic:** Distinguish "no active request," "request pending assignment," and "collector assigned but not yet en route" as separate explicit states.
**Pro UI/UX Specification:** Each state has tailored illustration and copy — never show a static/empty map with no explanation.

### REQ-061: ETA Countdown Display for Resident
**Core Logic:** Mirror collector-side ETA calc, exposed read-only to resident.
**Pro UI/UX Specification:** Large countdown chip at top of screen with color transition as time decreases (blue→green as "arriving now" approaches).

### REQ-062: Post-Pickup Rating Prompt
**Core Logic:** On status transition to `completed`, trigger a rating request write to `ratings` collection linked to `requestId` and `collectorId`.
**Pro UI/UX Specification:** Bottom sheet slides up automatically post-completion with 5-star tap targets (44dp+), optional comment field, skip option that doesn't feel punitive.

---

## MODULE 8: ADMIN MODERATION — CLIENTS

### REQ-063: Replace "Under Construction" with Client Management Table
**Core Logic:** Query all `AppUser` docs with role `resident`, paginated, with server-side search/filter via Firestore composite indexes or Algolia for full-text search.
**Pro UI/UX Specification:** Data table with sticky header row, sortable columns (click-to-sort with arrow indicator), zebra-striped rows for scanability, sticky search bar above.

### REQ-064: Client Detail Drawer
**Core Logic:** Tapping a row opens a side drawer fetching full user doc + related `waste_requests` subquery.
**Pro UI/UX Specification:** Drawer slides in from right (desktop) or bottom sheet (mobile), overlay dims background content, drawer content scrolls independently of the table behind it.

### REQ-065: Ban/Suspend Client Action
**Core Logic:** Cloud Function sets custom claim `banned: true` and disables Firebase Auth account (`updateUser({disabled: true})`), logs action to audit trail.
**Pro UI/UX Specification:** Destructive action behind confirmation modal requiring a typed reason (mandatory text field), button disabled until reason entered — enforces accountability.

### REQ-066: Bulk Client Actions
**Core Logic:** Multi-select checkboxes trigger batched Cloud Function calls (chunked to respect Firestore batch limits of 500).
**Pro UI/UX Specification:** Selection triggers a floating contextual action bar at bottom of table ("3 selected: Ban / Export / Message"), checkbox-all in header with indeterminate state styling.

### REQ-067: Client Search & Filter
**Core Logic:** Composite filter query on status, registration date range, request count thresholds.
**Pro UI/UX Specification:** Filter chips row above table, each removable via "x", "Clear all" link when 2+ active; filter changes debounce query execution (300ms).

### REQ-068: Client Activity Timeline
**Core Logic:** Aggregate all `waste_requests`, `ratings`, and `activity_log` entries for a client into a unified chronological feed.
**Pro UI/UX Specification:** Vertical timeline with connecting line, icon per event type, relative timestamps, "Load more" pagination rather than infinite scroll within the drawer.

### REQ-069: Audit Log for Admin Actions
**Core Logic:** Every moderation action writes an immutable audit doc (`actorId`, `action`, `targetId`, `timestamp`, `reason`) — never allow deletion of audit entries via security rules.
**Pro UI/UX Specification:** Audit trail visible in a dedicated tab, read-only styled (monospace timestamp, muted colors) to visually signal it's a system record, not editable content.

---

## MODULE 9: ADMIN MODERATION — FLEET & ROUTES

### REQ-070: Collector Verification Queue
**Core Logic:** Query `AppUser` where role=`collector` and `isVerified=false`, surface documents (license, vehicle registration) uploaded to Storage for review.
**Pro UI/UX Specification:** Card-based queue (not table) since document review benefits from larger visual real estate; document thumbnails open full-screen zoomable viewer.

### REQ-071: Approve/Reject Collector Action
**Core Logic:** Approval sets `isVerified: true` + writes welcome notification; rejection requires reason and triggers a re-submission allowance flag.
**Pro UI/UX Specification:** Side-by-side Approve (green) / Reject (outlined red) buttons of equal visual weight to avoid dark-pattern bias toward either action.

### REQ-072: Fleet Vehicle Registry
**Core Logic:** New `vehicles` subcollection under each collector doc storing plate number, capacity, inspection expiry date.
**Pro UI/UX Specification:** Expiry dates within 30 days highlighted amber, expired highlighted red with a small warning icon, sortable by expiry urgency.

### REQ-073: Route Assignment Interface
**Core Logic:** Admin manually or algorithmically assigns pending `waste_requests` to available collectors, writing `assignedCollectorId` transactionally to prevent double-assignment race conditions.
**Pro UI/UX Specification:** Drag-and-drop interface: unassigned requests in a left column, collector lanes on the right (Kanban-style), drop triggers assignment write with optimistic UI update and rollback on failure.

### REQ-074: Fleet Utilization Dashboard
**Core Logic:** Computed metrics per collector (jobs/day, average completion time, idle time) via scheduled Cloud Function aggregation into a `fleet_stats` collection.
**Pro UI/UX Specification:** Heat-map style grid (collectors x days) with color intensity representing utilization, hover/tap reveals exact numbers in tooltip.

### REQ-075: Geographic Coverage Map
**Core Logic:** Admin-facing map overlay showing all active collector positions + service area boundaries (polygon geofences stored as GeoJSON).
**Pro UI/UX Specification:** Toggle-able map layers panel (collectors / requests / boundaries) with checkbox controls, each layer has distinct marker styling.

---

## MODULE 10: ADMIN MODERATION — DISPUTES

### REQ-076: Dispute Case Management Table
**Core Logic:** Query `disputes` collection joined conceptually with related `waste_requests` and involved parties, status-filterable (open/investigating/resolved).
**Pro UI/UX Specification:** Kanban board view option alongside table view (toggle), status columns with drag-to-transition, card count badge per column.

### REQ-077: Dispute Detail & Evidence Viewer
**Core Logic:** Aggregate all related photos, messages, and timeline events for a dispute into a single review panel.
**Pro UI/UX Specification:** Split-pane layout: evidence gallery on left, resolution notes/actions on right, synchronized scroll not required but visually balanced.

### REQ-078: Resolution Action & Refund Trigger
**Core Logic:** Resolving a dispute with "refund" outcome triggers a Cloud Function call to payment processor (once REQ-089+ payments exist) and updates dispute status atomically.
**Pro UI/UX Specification:** Resolution form requires selecting an outcome category from a dropdown before free-text notes unlock — structures data for future analytics.

### REQ-079: SLA Countdown for Open Disputes
**Core Logic:** Each dispute has a `dueBy` timestamp computed from creation + SLA policy; scheduled function flags overdue cases.
**Pro UI/UX Specification:** Countdown badge on dispute cards transitions color as deadline approaches (green→amber→red), overdue cases auto-sort to top of default view.

### REQ-080: Internal Admin Notes Thread
**Core Logic:** `notes` subcollection per dispute, admin-only, separate from any resident/collector-facing communication.
**Pro UI/UX Specification:** Chat-style thread UI with admin avatars, clearly labeled "Internal only — not visible to users" banner to prevent accidental info leakage confusion.

---

## MODULE 11: COMMUNICATION & MESSAGING

### REQ-081: In-App Chat Replacing WhatsApp Redirect
**Core Logic:** Build native chat using Firestore subcollection `chats/{requestId}/messages` with real-time listeners, replacing hardcoded WhatsApp intent.
**Pro UI/UX Specification:** Standard messaging UI (bubbles, sender-right/receiver-left alignment), typing indicator via a `typing` presence doc, read receipts as double-check icon.

### REQ-082: Push Notification Infrastructure
**Core Logic:** FCM integration with topic subscriptions per role and per active request, Cloud Functions trigger sends on key status transitions.
**Pro UI/UX Specification:** In-app notification center (bell icon with unread count badge) mirrors push history for users who missed the push, grouped by date.

### REQ-083: Support Ticket System
**Core Logic:** Replace generic WhatsApp button with an in-app ticket form writing to a `support_tickets` collection, routed to admin queue.
**Pro UI/UX Specification:** Category-first triage form (dropdown: Billing/Pickup Issue/App Bug/Other) before free text, reduces support team sorting overhead and shown as smart routing to the user.

### REQ-084: Automated Status Update Messages
**Core Logic:** System-generated chat messages auto-post on status transitions (e.g., "Collector assigned", "Truck en route") sourced from a template collection.
**Pro UI/UX Specification:** System messages visually distinct from human messages (centered, muted gray pill style, no avatar) to avoid confusion about who "said" it.

### REQ-085: Rich Media in Chat
**Core Logic:** Support image attachments in chat messages, uploaded to Storage with message doc referencing URL.
**Pro UI/UX Specification:** Inline image thumbnails in bubbles with tap-to-expand, upload progress shown as a blurred placeholder that sharpens on completion.

---

## MODULE 12: LOCALIZATION & INTERNATIONALIZATION

### REQ-086: Full String Extraction to Resource Files
**Core Logic:** Audit and migrate all hardcoded UI strings into `strings.xml`/`Localizable.strings` equivalents referenced via `LocalizationManager`, eliminating remaining English-only hardcoding.
**Pro UI/UX Specification:** No UI change per se, but enables layout QA pass ensuring Swahili (typically longer strings) doesn't cause text truncation — all text containers use flexible/wrap sizing, not fixed-width.

### REQ-087: Language Switcher UI
**Core Logic:** `LanguageViewModel` persists selected locale to DataStore/UserDefaults and triggers app-wide recomposition/reload without requiring restart where feasible.
**Pro UI/UX Specification:** Language picker shows each option in its own native script (e.g., "Kiswahili" not "Swahili" when listing itself) with flag or text-based indicator, current selection checkmarked.

### REQ-088: RTL-Ready Layout Architecture
**Core Logic:** Even if not immediately needed, structure layouts using start/end (not left/right) constraints to future-proof for RTL languages.
**Pro UI/UX Specification:** Mirror-testable layouts; icons with directional meaning (arrows) use `mirrorForRtl` flags.

### REQ-089: Locale-Aware Formatting
**Core Logic:** Dates, currency, and numbers formatted via locale-aware system APIs (ICU) rather than manual string concatenation.
**Pro UI/UX Specification:** Currency displays with correct symbol placement and grouping per locale automatically, no visual regression when switching languages.

### REQ-090: Translation Completeness Indicator (Internal/Debug)
**Core Logic:** Build-time lint script flags missing translation keys per locale, failing CI if coverage drops below threshold.
**Pro UI/UX Specification:** (Dev-facing) Debug overlay in non-prod builds highlights untranslated strings in a visible color for QA sweep.

---

## MODULE 13: RESIDENT HOME SCREEN

### REQ-091: Live Recent Activity Feed
**Core Logic:** Replace hardcoded "Recent Activity" with a real Firestore query on the user's last N `waste_requests` + `activity_log` entries, real-time listener.
**Pro UI/UX Specification:** Card carousel or vertical list with subtle entrance stagger animation (each card fades+slides in 50ms after the previous) on first load only, not on every recomposition.

### REQ-92: Dynamic Personalized Greeting
**Core Logic:** Compute time-of-day greeting server-timezone-aware combined with user's display name, localized via REQ-089.
**Pro UI/UX Specification:** Greeting text uses variable font weight emphasis on the name portion; avoid re-render flicker by memoizing the greeting computation.

### REQ-093: Quick Action Shortcuts
**Core Logic:** Configurable shortcut tiles (Request Pickup, Track Truck, View History) driven by a remote-config-defined layout for A/B testing flexibility.
**Pro UI/UX Specification:** Horizontally scrollable chip row with icon+label, active/pressed state uses scale-down (0.96) micro-interaction on tap.

### REQ-094: Home Screen Pull-to-Refresh
**Core Logic:** Manual refresh re-triggers all home screen data sources (activity feed, metrics) with debounce to prevent spam-refresh abuse.
**Pro UI/UX Specification:** Custom branded pull-to-refresh spinner (not default system spinner) matching app's visual identity, satisfying elastic overscroll physics.

### REQ-095: Contextual Home Banners
**Core Logic:** Server-driven promotional/informational banner system via a `home_banners` collection with targeting rules (role, region, date range).
**Pro UI/UX Specification:** Dismissible banner cards with swipe-to-dismiss, dismissal persisted locally so it doesn't reappear same session.

### REQ-096: Weather-Aware Pickup Advisory (Optional Enhancement)
**Core Logic:** Integrate a weather API to warn residents of potential pickup delays during severe weather, cached to limit API calls.
**Pro UI/UX Specification:** Small advisory chip with weather icon appears only when relevant (rain/storm), auto-hides when conditions clear.

---

## MODULE 14: PAYMENTS & BILLING

### REQ-097: Mobile Money Integration
**Core Logic:** Integrate a regional mobile money API (e.g., M-Pesa/Tigo Pesa equivalent) via server-side Cloud Function handling STK push and callback webhook verification — never process payment client-side only.
**Pro UI/UX Specification:** Payment sheet with provider-branded input mask for phone number, real-time STK-push status polling shown as a progress state machine (Initiated → Awaiting confirmation → Success/Failed) with appropriate iconography per state.

### REQ-098: Pricing Engine & Fee Transparency
**Core Logic:** Server-computed pricing based on category/volume/distance, exposed via a Cloud Function callable so pricing logic isn't reverse-engineerable from client code.
**Pro UI/UX Specification:** Itemized fee breakdown (base fee, distance surcharge, service fee) shown before confirmation — no surprise totals, matches App Store/Play Store transparency expectations.

### REQ-099: Payment History & Receipts
**Core Logic:** Each successful payment writes an immutable `receipts` doc; generate PDF receipt via Cloud Function for download.
**Pro UI/UX Specification:** Receipt list styled like a bank statement (date, amount, status), tap opens PDF preview in-app rather than forcing external app switch.

### REQ-100: Failed Payment Recovery Flow
**Core Logic:** Failed/timeout payment states trigger a retry-eligible flag; webhook idempotency keys prevent double-charging on retry.
**Pro UI/UX Specification:** Clear failure reason messaging (insufficient funds vs. network timeout vs. cancelled) each with tailored recovery CTA, not a generic "Payment failed" dead-end.

### REQ-101: Subscription/Recurring Pickup Billing
**Core Logic:** Support recurring billing schedules stored as `subscriptions` docs, scheduled Cloud Function triggers charge + request creation on cadence.
**Pro UI/UX Specification:** Subscription management screen showing next billing date, cadence editor (weekly/biweekly/monthly segmented control), pause/cancel with clear effective-date messaging.

### REQ-102: Admin Revenue Reporting
**Core Logic:** Aggregated revenue rollups computed via scheduled function into `revenue_daily` docs to avoid expensive live aggregation queries on the dashboard.
**Pro UI/UX Specification:** Revenue chart (line/bar toggle) with hover tooltips showing exact daily figures, export-to-CSV action in chart header.

### REQ-103: Refund Processing UI
**Core Logic:** Admin-initiated refunds call the payment provider's refund API server-side, logged to audit trail, linked to originating dispute if applicable.
**Pro UI/UX Specification:** Refund confirmation modal shows original transaction details for verification before the admin commits, preventing wrong-transaction refunds.

### REQ-104: PCI/Compliance-Conscious Data Handling
**Core Logic:** Never store raw payment credentials in Firestore; only store provider transaction references and tokens per compliance requirements.
**Pro UI/UX Specification:** N/A user-facing, but surfaces as trust signals: displaying "Payments secured by [Provider]" badge near payment entry points.

---

## MODULE 15: NOTIFICATIONS SYSTEM

### REQ-105: Notification Preference Center
**Core Logic:** Per-category opt-in/out flags (`pushEnabled`, `emailEnabled`, categories: reminders, promotions, status updates) stored on `AppUser`, respected by Cloud Function send logic.
**Pro UI/UX Specification:** Settings screen with grouped toggle switches, each with a one-line description; master toggle at top with indeterminate state if sub-toggles are mixed.

### REQ-106: Notification Center Inbox
**Core Logic:** Persistent notification list backed by a `notifications/{uid}` subcollection, mirrors push payloads for in-app review, mark-as-read state tracked per doc.
**Pro UI/UX Specification:** Unread items have a colored dot indicator and bold text weight; swipe-to-dismiss individual items, "Mark all read" action in header.

### REQ-107: Scheduled Reminder Notifications
**Core Logic:** Cloud Scheduler triggers reminders for recurring pickups or pending actions (e.g., "Complete your profile") based on rule-based conditions.
**Pro UI/UX Specification:** Reminder notifications include a direct deep-link action button within the push payload (e.g., "Complete Now") skipping intermediate navigation.

### REQ-108: Do Not Disturb Windows
**Core Logic:** Respect user-defined quiet hours by queuing non-urgent notifications for delivery outside the DND window server-side.
**Pro UI/UX Specification:** Time-range picker (start/end) with a visual 24-hour clock dial component rather than two plain dropdowns.

---

## MODULE 16: SETTINGS & PREFERENCES

### REQ-109: Master Settings Hub
**Core Logic:** Centralized settings screen aggregating account, notifications, privacy, and app preferences into grouped sections backed by a unified settings ViewModel.
**Pro UI/UX Specification:** Grouped list sections with subtle header labels (uppercase, muted), consistent chevron-right affordance for drill-in items, toggle switches right-aligned for binary settings.

### REQ-110: Dark Mode / Theme Support
**Core Logic:** Theme preference stored locally and optionally synced to profile; respects system setting by default with manual override option.
**Pro UI/UX Specification:** Full design token system (not just color inversion) — dark mode uses elevated surface colors per Material 3 / HIG guidelines, not pure black, with smooth cross-fade transition on toggle (not instant flash).

### REQ-111: Accessibility Settings Panel
**Core Logic:** Expose text-scale override, reduce-motion toggle, and high-contrast mode flags consumed globally by the design system.
**Pro UI/UX Specification:** Live preview sample text/card in the settings screen itself updates in real-time as sliders/toggles are adjusted, so users see the effect immediately.

### REQ-112: Data Usage / Offline Mode Settings
**Core Logic:** Toggle to restrict image loading/map tile fetching to Wi-Fi only, respected by network layer conditional logic.
**Pro UI/UX Specification:** Setting includes a small explanatory subtext estimating data savings, building user trust in the control's purpose.

### REQ-113: Privacy Controls & Consent Management
**Core Logic:** Granular consent flags (location tracking, analytics, marketing) stored with timestamps for compliance audit trail.
**Pro UI/UX Specification:** Each consent toggle links to a plain-language explanation modal (not legalese dump) accessible via an info icon.

---

## MODULE 17: ONBOARDING & FIRST-RUN EXPERIENCE

### REQ-114: Role Selection Onboarding Step
**Core Logic:** First-time users post-registration select intended role context (resident vs. wanting to become a collector), writing an initial preference that shapes onboarding path (collector path triggers verification doc upload flow).
**Pro UI/UX Specification:** Two large illustrated choice cards, tap triggers scale+color transition confirming selection before auto-advancing after a brief pause (400ms) so the choice registers visually.

### REQ-115: Permission Priming Screens
**Core Logic:** Before triggering native OS permission dialogs (location, camera, notifications), show a custom pre-permission explainer screen to improve grant rates.
**Pro UI/UX Specification:** Each primer uses a simple icon + one-sentence benefit statement ("We use your location to find nearby trucks") before the system dialog fires.

### REQ-116: Interactive Feature Walkthrough
**Core Logic:** Coach-mark overlay system triggered once per feature (tracked via local flags), highlighting key UI elements on first encounter.
**Pro UI/UX Specification:** Spotlight/cutout overlay dimming the rest of the screen, tooltip pointing to the highlighted element, "Got it" dismiss and "Skip all" option.

### REQ-117: Address/Location Setup Wizard
**Core Logic:** Guided first-address entry integrated with map picker (reuses REQ-031 component) required before allowing first request creation.
**Pro UI/UX Specification:** Progress-friendly framing ("Almost done — just need your pickup address") rather than presenting as a blocking gate.

### REQ-118: Collector Onboarding Document Upload Flow
**Core Logic:** Dedicated multi-step flow for prospective collectors to upload license/vehicle docs, writing to a pending-verification state consumed by REQ-070.
**Pro UI/UX Specification:** Checklist UI showing document upload status per required item (License ✓, Vehicle Reg ○, Insurance ○) so users know exactly what's left.

---

## MODULE 18: ERROR HANDLING & RESILIENCE (APP-WIDE)

### REQ-119: Global Network Error Boundary
**Core Logic:** Centralized error interceptor for Firestore/network calls classifying errors (offline, permission-denied, timeout, unknown) into a shared UI state model.
**Pro UI/UX Specification:** Consistent error card component reused app-wide (icon + message + retry button) rather than ad-hoc error handling per screen — ensures visual consistency.

### REQ-120: Offline Mode Banner
**Core Logic:** Connectivity monitor (NetworkCallback/NWPathMonitor) drives a global app-bar banner state.
**Pro UI/UX Specification:** Slim banner slides down from status bar area ("No internet connection"), auto-dismisses with slide-up animation on reconnect, non-blocking of underlying content.

### REQ-121: Graceful Degradation for Partial Data Failures
**Core Logic:** If one data source in a combined screen fails (e.g., metrics load but activity feed fails), render successful sections and show inline error only for the failed section.
**Pro UI/UX Specification:** Failed section shows a small "Couldn't load this — Retry" inline card rather than failing the entire screen render.

### REQ-122: Crash Reporting & Session Replay Integration
**Core Logic:** Integrate Crashlytics (or equivalent) with breadcrumb logging for key user actions to aid debugging.
**Pro UI/UX Specification:** N/A user-facing directly, but enables faster bug-fix turnaround which indirectly improves perceived polish over time.

### REQ-123: Empty vs. Error vs. Loading State Discipline
**Core Logic:** Enforce a strict tri-state (or quad-state including "stale") pattern across all ViewModels using sealed classes, preventing ambiguous UI states.
**Pro UI/UX Specification:** Each state has a dedicated, deliberately designed composable — no screen ever silently shows a blank frame during any of these states.

### REQ-124: Form Submission Idempotency Protection
**Core Logic:** Disable submit buttons immediately on tap and use idempotency keys server-side to prevent duplicate writes from double-taps or network retries.
**Pro UI/UX Specification:** Button shows inline loading spinner replacing text (fixed-width button to prevent layout shift), re-enables only on definitive success/failure response.

---

## MODULE 19: PERFORMANCE & TECHNICAL POLISH

### REQ-125: Firestore Query Cost Optimization Audit
**Core Logic:** Review all listeners for over-fetching; apply `limit()`, composite indexes, and denormalized counter docs where aggregation queries are expensive.
**Pro UI/UX Specification:** Indirect UX benefit: faster load times translate to shorter/no skeleton-state duration, which should be measured and targeted (e.g., sub-500ms perceived load).

### REQ-126: Image Caching Strategy
**Core Logic:** Implement Coil/SDWebImage-equivalent disk+memory caching with cache-busting only on explicit content updates.
**Pro UI/UX Specification:** Images that were previously viewed load instantly from cache with no flash-of-blank, using blur-hash placeholders for genuinely new images.

### REQ-127: List Virtualization for Long Lists
**Core Logic:** Use LazyColumn/UICollectionView virtualization for all history/table lists to prevent memory bloat on large datasets.
**Pro UI/UX Specification:** Scroll performance must sustain 60fps even on 500+ item lists; scrollbar thumb sizing reflects actual list proportion for orientation.

### REQ-128: Cold Start Time Budget
**Core Logic:** Set and enforce a cold-start performance budget (e.g., <2s to interactive) via profiling, deferring non-critical initialization (analytics SDKs) to post-first-frame.
**Pro UI/UX Specification:** Splash-to-content transition must feel instantaneous relative to this budget; any unavoidable delay is masked by the skeleton pattern established in REQ-001.

### REQ-129: Firestore Security Rules Hardening
**Core Logic:** Comprehensive rules audit ensuring role-based read/write restrictions match every UI-exposed action (residents can't read others' requests, collectors can't modify payment records, etc.).
**Pro UI/UX Specification:** N/A directly visible, but permission-denied errors that do surface should route through REQ-119's consistent error component with clear, non-technical messaging.

### REQ-130: Automated Visual Regression Testing
**Core Logic:** Snapshot testing pipeline (Paparazzi/iOS snapshot testing) covering all key screen states (loading/empty/error/populated) across light/dark themes.
**Pro UI/UX Specification:** Ensures the polished states specified throughout this document don't silently regress across future commits — a process requirement protecting design quality long-term.

---

## MODULE 20: DESIGN SYSTEM FOUNDATION

### REQ-131: Unified Design Token Library
**Core Logic:** Centralize color, spacing, typography, radius, and elevation values into a single design-token source (Compose theme / SwiftUI environment values) consumed everywhere — eliminate any remaining inline hex codes or magic numbers in the codebase.
**Pro UI/UX Specification:** Typography scale follows a clear hierarchy (Display/Headline/Title/Body/Label, each with defined size/weight/line-height); 8pt spacing grid enforced across all layouts for visual rhythm consistency.

### REQ-132: Component Library Documentation
**Core Logic:** Build a living style-guide screen (dev-only route) rendering every reusable component variant for QA and design review.
**Pro UI/UX Specification:** Organized by component category with interactive state toggles (default/pressed/disabled/loading) visible side-by-side for comparison.

### REQ-133: Motion Design Guidelines
**Core Logic:** Standardize animation curves/durations into shared constants (e.g., `fastOutSlowIn`, 150/250/400ms tiers) rather than ad-hoc per-screen values.
**Pro UI/UX Specification:** All transitions across the app feel like they belong to the same physical system — consistent easing prevents the "stitched together" feeling common in less polished apps.

### REQ-134: Iconography Consistency Pass
**Core Logic:** Audit and replace any mismatched icon sets (mixing filled/outline styles inconsistently) with a single icon family throughout.
**Pro UI/UX Specification:** Icons maintain consistent optical weight and corner radius language matching the overall brand geometry (rounded vs. sharp).

### REQ-135: Elevation & Shadow System
**Core Logic:** Define a fixed elevation scale (0/1/2/3/4dp equivalents) mapped to consistent shadow tokens rather than arbitrary per-component shadow values.
**Pro UI/UX Specification:** Cards, sheets, and dialogs use elevation purely to communicate hierarchy — higher elevation always means "more foreground/interactive," never used decoratively.

---

## Closing Note

This specification covers 135 top-level requirement groups spanning authentication, core resident/collector/admin workflows, payments, communication, localization, notifications, onboarding, resilience, performance, and the underlying design system — with several requirements (e.g., REQ-048–057, REQ-063–080) internally covering multiple discrete sub-behaviors. This is the structural backbone for elevating WasteLink from its current "read/write foundation with UI mockups" state to a fully wired, production-grade, A+ product. Recommend treating this as a living backlog: prioritize Module 6–8 (live GPS, admin CRUD) and Module 14 (payments) first, since those represent the largest gaps between current mockup state and real functionality per the source capabilities audit.
