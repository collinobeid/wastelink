package com.waste.link.data.local.model

/**
 * Data-layer-only synchronization state for local request persistence.
 *
 * Strict Architectural Invariants:
 * 1. This enum exists strictly in `:core:data` and must NEVER be added to the pure
 *    domain `WasteRequest` model or conflated with `RequestLifecycleStatus`.
 * 2. `LOCAL_DRAFT`: In-progress draft wizard being edited locally.
 * 3. `QUEUED_OFFLINE`: Validated request persisted durably on local device awaiting sync.
 *    Does NOT imply server acceptance or fleet dispatch.
 * 4. `UPLOADING`: Currently in transit during remote network write.
 * 5. `SYNCED`: Remote confirmation received from trusted backend authority. Must never be
 *    assigned merely because a local database insert succeeded.
 * 6. `SYNC_FAILED`: Non-retryable error or exhausted retry budget.
 */
enum class LocalSyncStatus {
    LOCAL_DRAFT,
    QUEUED_OFFLINE,
    UPLOADING,
    SYNCED,
    SYNC_FAILED
}
