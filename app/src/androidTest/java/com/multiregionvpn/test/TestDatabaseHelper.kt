package com.multiregionvpn.test

import android.content.Context
import androidx.room.Room
import com.multiregionvpn.data.database.AppDatabase

/**
 * Helper for creating AppDatabase instances in tests.
 * This replaces the old singleton pattern with direct database creation.
 */
object TestDatabaseHelper {
    /**
     * Creates an in-memory database for testing.
     * Each call creates a new instance.
     */
    fun createInMemoryDatabase(context: Context): AppDatabase {
        return Room.inMemoryDatabaseBuilder(
            context.applicationContext,
            AppDatabase::class.java
        )
            .addCallback(AppDatabase.PresetRuleCallback())
            .allowMainThreadQueries() // Only for testing
            .build()
    }
    
    /**
     * Creates a persistent database for testing.
     * Uses the same configuration as the app.
     */
    fun createDatabase(context: Context): AppDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "region_router_db"
        )
            .addCallback(AppDatabase.PresetRuleCallback())
            .build()
    }
}
