package com.waste.link.data.auth

import com.waste.link.domain.model.SessionState
import kotlinx.coroutines.flow.StateFlow

/**
 * Supplies the current authoritative [SessionState] to `:core:data` synchronization
 * components without coupling the data layer directly to the auth feature module.
 */
interface SessionStateProvider {
    val sessionState: StateFlow<SessionState>
}
