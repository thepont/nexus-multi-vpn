# Database Migration Guide

## Overview

This document provides guidance for managing database schema changes in the Multi-Region VPN Router application. Proper migration strategies are critical to prevent user data loss during app updates.

## Critical Rules

### ❌ NEVER Do This in Production

```kotlin
Room.databaseBuilder(...)
    .fallbackToDestructiveMigration()  // ⛔ NEVER USE THIS IN PRODUCTION!
    .build()
```

**Why?** `fallbackToDestructiveMigration()` will **delete all user data** if a migration is missing. This means users will lose:
- All configured VPN servers
- All app routing rules  
- All saved credentials
- All custom settings

This creates a terrible user experience and should only be used in test databases or during development.

### ✅ Always Do This Instead

```kotlin
Room.databaseBuilder(...)
    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, ...)  // ✅ Proper migrations
    .build()
```

## Migration Process

### 1. Plan the Schema Change

Before making any changes:
- Document what needs to change and why
- Consider backwards compatibility
- Plan the migration SQL statements
- Identify edge cases and data transformations needed

### 2. Update the Database Version

In `AppDatabase.kt`:

```kotlin
@Database(
    entities = [...],
    version = 2,  // ← Increment this
    exportSchema = true
)
```

### 3. Create the Migration

In `Migrations.kt`:

```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add new column with default value
        database.execSQL(
            "ALTER TABLE vpn_configs ADD COLUMN dns_servers TEXT DEFAULT NULL"
        )
    }
}
```

### 4. Register the Migration

In `AppDatabase.kt` in the `getDatabase()` method:

```kotlin
val instance = Room.databaseBuilder(...)
    .addMigrations(Migrations.MIGRATION_1_2)  // ← Add here
    .build()
```

### 5. Write Migration Tests

In `MigrationTest.kt`:

```kotlin
@Test
fun testMigration1To2() {
    // Create version 1 database with test data
    val db = helper.createDatabase(TEST_DB_NAME, 1)
    db.execSQL("INSERT INTO vpn_configs (...) VALUES (...)")
    db.close()
    
    // Run migration
    val migratedDb = helper.runMigrationsAndValidate(
        TEST_DB_NAME, 2, true, Migrations.MIGRATION_1_2
    )
    
    // Verify data survived and new schema is correct
    val cursor = migratedDb.query("SELECT * FROM vpn_configs")
    assertThat(cursor.moveToFirst()).isTrue()
    // Verify old data is intact
    // Verify new columns have correct defaults
    cursor.close()
    migratedDb.close()
}
```

### 6. Test Thoroughly

Test these scenarios:
- ✅ Fresh install (new users)
- ✅ Upgrade from each previous version
- ✅ Data integrity after migration
- ✅ Default values for new columns
- ✅ Complex data transformations
- ✅ Edge cases (null values, empty tables, etc.)

### 7. Export and Review Schema

After building, Room will generate schema JSON files in `app/schemas/`. 

- Review the schema changes in the JSON diff
- Commit the schema files to version control
- Include schema review in code review process

## Common Migration Patterns

### Adding a Column

```kotlin
val MIGRATION_X_Y = object : Migration(X, Y) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE table_name ADD COLUMN new_column TEXT DEFAULT 'default_value'"
        )
    }
}
```

### Renaming a Column

SQLite doesn't support RENAME COLUMN directly (before SQLite 3.25.0), so:

```kotlin
val MIGRATION_X_Y = object : Migration(X, Y) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Create new table with new column name
        database.execSQL(
            """
            CREATE TABLE table_name_new (
                id TEXT PRIMARY KEY NOT NULL,
                new_column_name TEXT,
                other_column TEXT
            )
            """.trimIndent()
        )
        
        // Copy data from old table
        database.execSQL(
            """
            INSERT INTO table_name_new (id, new_column_name, other_column)
            SELECT id, old_column_name, other_column FROM table_name
            """.trimIndent()
        )
        
        // Drop old table
        database.execSQL("DROP TABLE table_name")
        
        // Rename new table
        database.execSQL("ALTER TABLE table_name_new RENAME TO table_name")
    }
}
```

### Adding a Table

```kotlin
val MIGRATION_X_Y = object : Migration(X, Y) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE new_table (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                value INTEGER DEFAULT 0
            )
            """.trimIndent()
        )
    }
}
```

### Deleting a Column

```kotlin
val MIGRATION_X_Y = object : Migration(X, Y) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Create new table without the column
        database.execSQL(
            """
            CREATE TABLE table_name_new (
                id TEXT PRIMARY KEY NOT NULL,
                kept_column TEXT
            )
            """.trimIndent()
        )
        
        // Copy data (excluding deleted column)
        database.execSQL(
            """
            INSERT INTO table_name_new (id, kept_column)
            SELECT id, kept_column FROM table_name
            """.trimIndent()
        )
        
        database.execSQL("DROP TABLE table_name")
        database.execSQL("ALTER TABLE table_name_new RENAME TO table_name")
    }
}
```

### Complex Data Transformation

```kotlin
val MIGRATION_X_Y = object : Migration(X, Y) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add new column
        database.execSQL("ALTER TABLE table_name ADD COLUMN new_column TEXT")
        
        // Transform existing data
        database.execSQL(
            """
            UPDATE table_name 
            SET new_column = CASE 
                WHEN old_column = 'value1' THEN 'new_value1'
                WHEN old_column = 'value2' THEN 'new_value2'
                ELSE 'default_value'
            END
            """.trimIndent()
        )
    }
}
```

## Multiple Version Migrations

Users might skip versions (e.g., upgrade from v1 to v3, skipping v2). Room handles this automatically:

```kotlin
// In AppDatabase
.addMigrations(
    Migrations.MIGRATION_1_2,  // v1 → v2
    Migrations.MIGRATION_2_3   // v2 → v3
)
// Room automatically chains: v1 → v2 → v3
```

If you need a direct migration path for performance:

```kotlin
val MIGRATION_1_3 = object : Migration(1, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Direct migration from 1 to 3
        // Often just combines the SQL from MIGRATION_1_2 and MIGRATION_2_3
    }
}
```

## Debugging Migration Issues

### Enable Logging

```kotlin
// In debug builds
Room.databaseBuilder(...)
    .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
    .build()
```

### Inspect Database

```bash
# Pull database from device
adb pull /data/data/com.multiregionvpn/databases/region_router_db

# Inspect with sqlite3
sqlite3 region_router_db
.schema
.tables
SELECT * FROM table_name;
```

### Common Issues

**Issue**: Migration test fails with "no such column"
- **Fix**: Verify the column name matches exactly (case-sensitive)
- **Fix**: Check that you're using the correct table name

**Issue**: Data is lost after migration
- **Fix**: Review your INSERT INTO ... SELECT statement
- **Fix**: Ensure you're copying all required columns
- **Fix**: Add migration tests with actual data

**Issue**: Migration not running
- **Fix**: Verify the migration is added to `.addMigrations()`
- **Fix**: Check that version numbers are correct
- **Fix**: Ensure the database version is incremented

## Schema Export

Schema files are automatically generated in `app/schemas/` when:
1. `exportSchema = true` is set in `@Database`
2. `ksp { arg("room.schemaLocation", "$projectDir/schemas") }` is configured
3. You build the project

These files should be:
- ✅ Committed to version control
- ✅ Reviewed during code review
- ✅ Used for migration testing

## Production Checklist

Before releasing a version with database changes:

- [ ] Database version incremented
- [ ] Migration created in Migrations.kt
- [ ] Migration registered in AppDatabase.getDatabase()
- [ ] Migration tests written and passing
- [ ] Schema exported and committed
- [ ] Tested upgrade from all previous versions
- [ ] Tested fresh install
- [ ] Code reviewed
- [ ] NO `fallbackToDestructiveMigration()` in production code

## References

- [Room Migration Documentation](https://developer.android.com/training/data-storage/room/migrating-db-versions)
- [Testing Room Migrations](https://developer.android.com/training/data-storage/room/migrating-db-versions#test)
- [SQLite ALTER TABLE](https://www.sqlite.org/lang_altertable.html)

## Support

For questions about database migrations, refer to:
- This guide
- `Migrations.kt` for examples
- `MigrationTest.kt` for test examples
- Android Room documentation
