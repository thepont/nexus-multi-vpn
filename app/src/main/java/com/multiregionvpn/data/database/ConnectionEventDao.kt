package com.multiregionvpn.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ConnectionEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: ConnectionEvent)
    
    @Query("SELECT * FROM connection_events ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentEvents(limit: Int = 100): Flow<List<ConnectionEvent>>
    
    @Query("SELECT * FROM connection_events ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentEventsList(limit: Int = 100): List<ConnectionEvent>
    
    @Query("DELETE FROM connection_events")
    suspend fun clearAll()
    
    @Query("DELETE FROM connection_events WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOlderThan(beforeTimestamp: Long)
    
    @Query("SELECT COUNT(*) FROM connection_events")
    suspend fun getCount(): Int
}
