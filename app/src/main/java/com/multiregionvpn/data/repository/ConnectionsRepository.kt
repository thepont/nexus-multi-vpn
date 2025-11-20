package com.multiregionvpn.data.repository

import com.multiregionvpn.data.database.ConnectionEvent
import com.multiregionvpn.data.database.ConnectionEventDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectionsRepository @Inject constructor(
    private val connectionEventDao: ConnectionEventDao
) {
    /**
     * Get recent connection events as a Flow for reactive updates
     */
    fun getRecentEvents(limit: Int = 100): Flow<List<ConnectionEvent>> {
        return connectionEventDao.getRecentEvents(limit)
    }
    
    /**
     * Insert a new connection event
     */
    suspend fun logConnection(event: ConnectionEvent) {
        connectionEventDao.insert(event)
    }
    
    /**
     * Clear all connection events
     */
    suspend fun clearAll() {
        connectionEventDao.clearAll()
    }
    
    /**
     * Delete events older than the specified timestamp
     */
    suspend fun deleteOlderThan(timestamp: Long) {
        connectionEventDao.deleteOlderThan(timestamp)
    }
    
    /**
     * Get total count of events
     */
    suspend fun getCount(): Int {
        return connectionEventDao.getCount()
    }
}
