package de.carcophan.snorerec.data.repository

import de.carcophan.snorerec.data.db.SnoreDatabase
import de.carcophan.snorerec.data.model.NightSession
import de.carcophan.snorerec.data.model.SnoreEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class SnoreRepository(
    private val database: SnoreDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val _sessions = MutableStateFlow<List<NightSession>>(emptyList())
    val sessions: StateFlow<List<NightSession>> = _sessions.asStateFlow()

    private val _selectedSessionEvents = MutableStateFlow<List<SnoreEvent>>(emptyList())
    val selectedSessionEvents: StateFlow<List<SnoreEvent>> = _selectedSessionEvents.asStateFlow()

    private val _selectedSession = MutableStateFlow<NightSession?>(null)
    val selectedSession: StateFlow<NightSession?> = _selectedSession.asStateFlow()

    suspend fun refreshSessions() = withContext(ioDispatcher) {
        val list = database.getAllSessions()
        _sessions.value = list

        val currentSelectedId = _selectedSession.value?.id
        if (currentSelectedId != null) {
            _selectedSession.value = database.getSession(currentSelectedId)
            _selectedSessionEvents.value = database.getEventsForSession(currentSelectedId)
        } else if (list.isNotEmpty()) {
            selectSession(list.first().id)
        }
    }

    suspend fun selectSession(sessionId: Long) = withContext(ioDispatcher) {
        val session = database.getSession(sessionId)
        val events = database.getEventsForSession(sessionId)
        _selectedSession.value = session
        _selectedSessionEvents.value = events
    }

    suspend fun startNewSession(startTime: Long = System.currentTimeMillis()): Long = withContext(ioDispatcher) {
        val id = database.createSession(startTime)
        refreshSessions()
        selectSession(id)
        id
    }

    suspend fun finishSession(sessionId: Long, endTime: Long = System.currentTimeMillis()) = withContext(ioDispatcher) {
        database.endSession(sessionId, endTime)
        refreshSessions()
    }

    suspend fun recordSnoreEvent(event: SnoreEvent): Long = withContext(ioDispatcher) {
        val id = database.insertSnoreEvent(event)
        // If current session is active/selected, update state
        if (_selectedSession.value?.id == event.sessionId) {
            _selectedSession.value = database.getSession(event.sessionId)
            _selectedSessionEvents.value = database.getEventsForSession(event.sessionId)
        }
        val list = database.getAllSessions()
        _sessions.value = list
        id
    }

    suspend fun deleteSession(sessionId: Long) = withContext(ioDispatcher) {
        database.deleteSession(sessionId)
        if (_selectedSession.value?.id == sessionId) {
            _selectedSession.value = null
            _selectedSessionEvents.value = emptyList()
        }
        refreshSessions()
    }

    suspend fun cleanupOldAudioFiles(olderThanDays: Int = 14): Int = withContext(ioDispatcher) {
        val count = database.deleteOldAudioFiles(olderThanDays)
        if (count > 0) {
            val currentSelectedId = _selectedSession.value?.id
            if (currentSelectedId != null) {
                _selectedSessionEvents.value = database.getEventsForSession(currentSelectedId)
            }
        }
        count
    }

    suspend fun getSessionById(sessionId: Long): NightSession? = withContext(ioDispatcher) {
        database.getSession(sessionId)
    }

    suspend fun getEventsForSession(sessionId: Long): List<SnoreEvent> = withContext(ioDispatcher) {
        database.getEventsForSession(sessionId)
    }
}
