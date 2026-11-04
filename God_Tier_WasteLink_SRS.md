# GOD-TIER WASTELINK ENTERPRISE SRS

## MODULE 1: AUTH & IDENTITY MANAGEMENT (AUTH)

REQ-AUTH-001: The system MUST utilize Firebase Phone Authentication for all actors (Client, Driver, Admin fallback) bypassing the need for email/password registration.
REQ-AUTH-002: (Dim A) The UI MUST present a loading overlay blocking all interaction during the OTP request phase to prevent duplicate SMS dispatches.
REQ-AUTH-003: (Dim B) The application MUST NOT request `READ_SMS` or `RECEIVE_SMS` permissions; it MUST utilize the Google Play Services SMS Retriever API for seamless background OTP extraction.
REQ-AUTH-004: (Dim C) Upon successful OTP verification, a cloud function `onUserCreated` MUST initialize a `/users/{uid}` document with default state: `role: "CLIENT"`, `createdAt: ServerValue.timestamp()`, `accountStatus: "ACTIVE"`.
REQ-AUTH-005: (Dim D) If the SMS Retriever API fails or times out after 60 seconds, the UI MUST seamlessly fallback to a manual 6-digit OTP entry field with a localized error string: "SMS Auto-read failed. Please enter the code."
REQ-AUTH-006: The Driver and Admin roles MUST be strictly enforced via Firebase Custom Claims (`admin: true`, `driver: true`).
REQ-AUTH-007: (Dim C) Firestore Security Rules MUST restrict writes to the `role` field on the user document; only the `sysadmin` custom claim can mutate user roles.
REQ-AUTH-008: (Dim A) On app foregrounding, the `AuthViewModel` MUST verify token validity; if expired, a Shimmer loading state MUST be presented while `getIdToken(true)` is executed.
REQ-AUTH-009: (Dim D) If a user attempts to authenticate while entirely offline, the app MUST surface a cached "Offline Profile" state but explicitly block mutation actions like "Request Pickup".
REQ-AUTH-010: The system MUST enforce a maximum of 3 concurrent sessions per UID. 
REQ-AUTH-011: (Dim C) A `sessionTracker` subcollection under `/users/{uid}` MUST log Device ID, OS Version, and Last Active Timestamp.

## MODULE 2: RESIDENT NODE (RES)

REQ-RES-001: The Resident app MUST allow a request creation in under 10 seconds.
REQ-RES-002: (Dim A) The Request Pickup screen MUST display a 3D animated visualizer for Waste Categorization (Construction, E-Waste, Organic, Recyclable) using Lottie animations.
REQ-RES-003: (Dim B) The app MUST request `ACCESS_FINE_LOCATION` with high accuracy. If denied, the user MUST be prompted with an educational rationale dialog before invoking the system permission dialog again.
REQ-RES-004: (Dim C) A new request MUST be written to `/wasteRequests/{requestId}`. The document MUST contain: `status: "PENDING_DISPATCH"`, `clientUid: {uid}`, `geohash: {string}`, `lat: {double}`, `lng: {double}`.
REQ-RES-005: (Dim D) If the user creates a request while offline, the payload MUST be cached locally via Room Database (Offline Request Queuing). A WorkManager `OneTimeWorkRequest` MUST be enqueued to sync to Firestore upon `NetworkType.CONNECTED`.
REQ-RES-006: "Find My Gate" Feature: The user MUST be able to attach a high-compression WebP photo (max 50KB) of their gate/landmark, or provide a textual Landmark Note.
REQ-RES-007: (Dim A) Due to zero-budget constraints avoiding Firebase Storage, the photo upload UI MUST compress the image aggressively and encode it to a Base64 string for direct storage within the Firestore `wasteRequests` document, displaying an indeterminate circular progress indicator during compression.
REQ-RES-008: Uber-Style Live Tracking: When a request state is `EN_ROUTE`, the MapView MUST subscribe to the assigned truck's telemetry document (`/truckTelemetry/{truckId}`).
REQ-RES-009: (Dim A) The truck icon MUST be smoothly interpolated across coordinates on the Google Map using a `ValueAnimator` to prevent jumping/teleporting pins.
REQ-RES-010: Smart Scheduling: Users MUST be able to define CRON-based recurring pickups (e.g., "Every Tuesday 08:00"). 
REQ-RES-011: (Dim C) Recurring requests MUST be stored in `/subscriptions/{subId}`. A Firebase Scheduled Function MUST query this collection daily at 00:01 EAT and spawn `wasteRequests` documents automatically.
REQ-RES-012: Eco-Gamification: A `greenPoints` integer counter on the user profile MUST be incremented by 10 for every request successfully marked as `Recyclable` and completed.

## MODULE 3: COLLECTOR NODE (COL)

REQ-COL-001: The Driver app MUST enforce a Dark Mode / High Contrast UI theme explicitly designed for high-glare environments.
REQ-COL-002: (Dim B) The app MUST acquire a Foreground Service with `FOREGROUND_SERVICE_LOCATION` type to ensure the Android OS does not kill the telemetry broadcaster via Doze Mode.
REQ-COL-003: (Dim C) The telemetry service MUST write to `/truckTelemetry/{truckId}` every 5 seconds. The payload: `lat`, `lng`, `heading`, `speed`, `updatedAt`.
REQ-COL-004: (Dim D) If network connectivity drops, the telemetry service MUST buffer the last 50 coordinates locally and batch-flush them to Firestore when the network is restored to ensure accurate historical route replay.
REQ-COL-005: Turn-by-Turn Route Optimization: Upon receiving a batch of ASSIGNED requests, the app MUST query the Google Maps Directions API (Waypoint Optimization) to sort the queue by minimal drive time.
REQ-COL-006: Mandatory Proof of Collection (PoC): To transition from `ARRIVED_AT_LOCATION` to `COLLECTION_VERIFIED`, the driver MUST invoke the camera intent.
REQ-COL-007: (Dim A) The PoC camera UI MUST overlay the current date, time, and GPS coordinates directly onto the Viewfinder before capture.
REQ-COL-008: (Dim C) The captured image MUST be heavily compressed and encoded to a Base64 string to be stored directly inside the Firestore `wasteRequests` document (bypassing Firebase Storage per budget constraints). The document MUST NOT be updated to `COLLECTION_VERIFIED` unless the Base64 image payload is successfully attached.
REQ-COL-009: Geofenced Auto-Arrival: The `LocationListener` MUST calculate the Haversine distance between the truck's current location and the active request's coordinates.
REQ-COL-010: (Dim B) If the distance is < 50 meters, the app MUST automatically mutate the request state to `ARRIVED_AT_LOCATION` and trigger a vibration burst (`VibrationEffect.createWaveform`).
REQ-COL-011: Capacity Meter: The UI MUST contain a persistent Slider (0-100%).
REQ-COL-012: (Dim C) Changing the slider MUST update `currentCapacity` on `/trucks/{truckId}`. If capacity >= 100, the system MUST automatically change the truck's operational state to `IN_TRANSIT_TO_DUMP`.
REQ-COL-013: 1-Tap "I'm Outside" SOS: The UI MUST feature a massive action button that invokes a WhatsApp deep link.

## MODULE 4: OPERATIONS CENTER (OCC)

REQ-OCC-001: Live Radar (The God Map): The Web/Tablet dashboard MUST render a full-screen Google Maps JS/Android View.
REQ-OCC-002: (Dim A) Requests in `PENDING_DISPATCH` MUST pulse red using a custom WebGL/Canvas marker animation.
REQ-OCC-003: (Dim C) The map MUST use Firestore `onSnapshot` listeners scoped by Geohash boundaries (GeoFirestore) to prevent downloading the entire city's data simultaneously.
REQ-OCC-004: Geospatial Clustering: Using the Google Maps Utility Library, nearby markers MUST cluster into a single node displaying the aggregate count.
REQ-OCC-005: (Dim A) The Admin MUST be able to activate a "Lasso Tool" (polygon draw on map). All markers within the polygon coordinate boundaries MUST be selected.
REQ-OCC-006: (Dim C) A bulk-dispatch action on lassoed markers MUST execute a Firestore `WriteBatch`. If the batch exceeds 500 documents, the client MUST chunk the payload into multiple batches.
REQ-OCC-007: Smart Auto-Dispatch Suggestion: When an admin clicks a `PENDING` request, the UI MUST calculate driving distances (Distance Matrix API) to all `ACTIVE` trucks and highlight the top 3 with capacity < 100%.
REQ-OCC-008: Live Audit Trail & Replay: The system MUST maintain a `/truckHistory/{date}/routes/{truckId}` collection.
REQ-OCC-009: (Dim A) The Replay UI MUST feature a scrubber timeline. Dragging the scrubber MUST move a ghost truck icon along a polyline representing its historical path.
REQ-OCC-010: Dumping Site Verification: The Admin dashboard MUST allow defining Geofence polygons for official dump sites.
REQ-OCC-011: (Dim C) A Cloud Function `onTruckStateUpdate` MUST verify if a truck transitioning to `DUMP_COMPLETED` actually has coordinates intersecting the official dump site polygon. If not, it MUST flag the truck for `ILLEGAL_DUMPING_REVIEW`.

## MODULE 5: WHATSAPP ROUTING ENGINE (WA)

REQ-WA-001: The system MUST completely bypass custom in-app messaging logic, leveraging the `intent://send?phone=` scheme for Android.
REQ-WA-002: (Dim A) Before launching the WA intent, the UI MUST display a brief loading dialog: "Opening WhatsApp..." to prevent double-taps.
REQ-WA-003: (Dim B) The app MUST query `PackageManager` to verify if the WhatsApp package (`com.whatsapp` or `com.whatsapp.w4b`) is installed.
REQ-WA-004: (Dim D) If WhatsApp is not installed, the app MUST display an error Snackbar and automatically open the Google Play Store market link for WhatsApp.
REQ-WA-005: Deep Link Encoding: All message payloads MUST be URL-encoded (`URLEncoder.encode(msg, "UTF-8")`).
REQ-WA-006: Dynamic Context Injection: A message from Resident to Admin MUST inject the Request ID and localized status. Example: "Habari, I have an issue with Request #WL-4922. Status: EN_ROUTE."
REQ-WA-007: Bulk Broadcasting: The Admin dashboard MUST generate an array of `wa.me/{phone}?text={encoded}` links for selected users.
REQ-WA-008: (Dim D) For bulk broadcasting, if the Admin is on a mobile device, the system MUST iterate and spawn intents sequentially, requiring the admin to press "Send" in WhatsApp for each, then automatically returning to the app.

## MODULE 6: LOCALIZATION ARCHITECTURE (LOC)

REQ-LOC-001: The system MUST guarantee 100% string parity between English (`values/strings.xml`) and Swahili (`values-sw/strings.xml`).
REQ-LOC-002: (Dim A) Switching the language via the Profile screen toggle MUST trigger an immediate Composable recomposition tree invalidation.
REQ-LOC-003: (Dim B) The localization engine MUST NOT require an `Activity.recreate()`. It MUST utilize a `CompositionLocal` provider (`LocalAppStrings`) holding the current locale state.
REQ-LOC-004: (Dim C) The chosen locale code (`en` or `sw`) MUST be persisted to the `DataStore` preferences and synced to the Firestore user profile.
REQ-LOC-005: (Dim D) If the Android system language is changed globally by the user in OS settings, the app MUST override it with the explicitly chosen in-app `DataStore` preference upon `MainActivity` launch.
REQ-LOC-006: Automated Message Formatting: WhatsApp intents MUST resolve strings in the target language. If a Swahili user contacts an English admin, the WA intent payload MUST use English keys for the Admin's readability.

## MODULE 7: STATE MACHINE ENFORCEMENT (STATE)

REQ-STATE-001: The system MUST strictly enforce the following unidirectional state flow: DRAFT -> PENDING_DISPATCH -> ASSIGNED_TO_FLEET -> EN_ROUTE -> ARRIVED_AT_LOCATION -> COLLECTION_VERIFIED -> IN_TRANSIT_TO_DUMP -> COMPLETED.
REQ-STATE-002: (Dim C) Firestore Security Rules (`request.resource.data.status`) MUST statically map allowed transitions. E.g., transitioning from `PENDING_DISPATCH` directly to `COMPLETED` MUST be denied with a `PermissionDeniedException`.
REQ-STATE-003: Exception State: `FAILED_NO_SHOW`. A driver can only trigger this from `ARRIVED_AT_LOCATION`. 
REQ-STATE-004: (Dim C) Triggering `FAILED_NO_SHOW` MUST require the driver to attach a photo URL (proof of blocked access or missing trash).
REQ-STATE-005: (Dim A) If a request enters `FAILED_NO_SHOW`, the Resident UI MUST display a full-screen red alert dialog upon next app open, providing the WA button to contact dispatch.
