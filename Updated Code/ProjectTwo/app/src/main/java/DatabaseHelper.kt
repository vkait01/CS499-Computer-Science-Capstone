package com.zybooks.cs360project2updated

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

// UPDATED!!
// DatabaseHelper manages user accounts and weight entries.
// Includes user-specific goal weight instead of a hard-coded value.
class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        // Database name and version constants
        private const val DATABASE_NAME = "userDatabase.db"
        // UPDATED!! Bumped version for schema change
        private const val DATABASE_VERSION = 2

        // Table and column name constants
        private const val TABLE_USERS = "users"
        private const val TABLE_WEIGHT_ENTRIES = "weight_entries"

        // Common column
        private const val COLUMN_ID = "id"

        // User table columns
        private const val COLUMN_USERNAME = "username"
        private const val COLUMN_PASSWORD = "password"
        private const val COLUMN_GOAL_WEIGHT = "goal_weight"   // UPDATED!! new column

        // Weight entries table columns
        private const val COLUMN_USER_ID = "user_id"
        private const val COLUMN_DATE = "date"
        private const val COLUMN_WEIGHT = "weight"
    }

    // Called when database is created for first time
    override fun onCreate(db: SQLiteDatabase) {
        // UPDATED!!
        // Users table now includes goal_weight so each user can have their own target
        val CREATE_USERS_TABLE = """
            CREATE TABLE $TABLE_USERS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_USERNAME TEXT NOT NULL UNIQUE,
                $COLUMN_PASSWORD TEXT NOT NULL,
                $COLUMN_GOAL_WEIGHT REAL DEFAULT 154.3
            )
        """.trimIndent()
        db.execSQL(CREATE_USERS_TABLE)

        // Weight entries table links each entry to a user by ID
        val CREATE_WEIGHT_ENTRIES_TABLE = """
            CREATE TABLE $TABLE_WEIGHT_ENTRIES (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_USER_ID INTEGER NOT NULL,
                $COLUMN_DATE TEXT NOT NULL,
                $COLUMN_WEIGHT REAL NOT NULL,
                FOREIGN KEY($COLUMN_USER_ID) REFERENCES $TABLE_USERS($COLUMN_ID)
            )
        """.trimIndent()
        db.execSQL(CREATE_WEIGHT_ENTRIES_TABLE)
    }

    // Called when database needs to be upgraded
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Drop old tables and recreate with new schema
        db.execSQL("DROP TABLE IF EXISTS $TABLE_USERS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_WEIGHT_ENTRIES")
        onCreate(db)
    }

    // UPDATED!!
    // Add a weight entry with basic validation
    fun addWeightEntry(userId: Int, date: String, weight: Double): Boolean {
        if (date.isBlank() || weight <= 0.0) return false

        // Updated!!
        // Save entry with ISO date so database can sort correctly
        // Convert "M/d/yyyy" to "yyyy-MM-dd" for proper sorting
        val inFmt = java.text.SimpleDateFormat("M/d/yyyy", java.util.Locale.US)
        val outFmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val isoDate = inFmt.parse(date)?.let { outFmt.format(it) } ?: return false

        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_USER_ID, userId)
            // Store ISO date
            put(COLUMN_DATE, isoDate)
            put(COLUMN_WEIGHT, weight)
        }

        val result = db.insert(TABLE_WEIGHT_ENTRIES, null, values)
        db.close()
        return result != -1L
    }


    // UPDATED!!
    // Delete a weight entry by its unique ID
    fun deleteWeightEntry(id: Int) {
        val db = this.writableDatabase
        db.delete(TABLE_WEIGHT_ENTRIES, "$COLUMN_ID = ?", arrayOf(id.toString()))
        db.close()
    }

    // UPDATED!!
    // Update a weight entry by its unique ID
    fun updateWeightEntry(id: Int, newDate: String, newWeight: Float): Int {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_DATE, newDate)
            put(COLUMN_WEIGHT, newWeight)
        }

        val rowsUpdated = db.update(
            TABLE_WEIGHT_ENTRIES,
            values,
            "$COLUMN_ID = ?",
            arrayOf(id.toString())
        )

        db.close()
        return rowsUpdated
    }

    // UPDATED!!
    // Get entries sorted by date
    fun getAllWeightEntries(userId: Int): Cursor {
        val db = this.readableDatabase
        val query = "SELECT * FROM $TABLE_WEIGHT_ENTRIES " +
                "WHERE $COLUMN_USER_ID = ? " +
                "ORDER BY $COLUMN_DATE ASC"   // sort by date
        return db.rawQuery(query, arrayOf(userId.toString()))
    }

    // UPDATED!!
    // Check if a user exists with matching username and password
    fun validateUser(username: String, hashedPassword: String): Boolean {
        val db = this.readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_USERS WHERE $COLUMN_USERNAME = ? AND $COLUMN_PASSWORD = ?",
            arrayOf(username, hashedPassword)
        )
        val isValid = cursor.moveToFirst()
        cursor.close()
        db.close()
        return isValid
    }

    // UPDATED!!
    // Check if username already exists
    fun userExists(username: String): Boolean {
        val db = this.readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_USERS WHERE $COLUMN_USERNAME = ?",
            arrayOf(username)
        )
        val exists = cursor.moveToFirst()
        cursor.close()
        db.close()
        return exists
    }

    // UPDATED!!
    // Register new user with hashed password and default goal weight
    fun registerUser(username: String, hashedPassword: String): Boolean {
        if (username.isBlank() || hashedPassword.isBlank()) return false

        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_USERNAME, username)
            put(COLUMN_PASSWORD, hashedPassword)
            put(COLUMN_GOAL_WEIGHT, 154.3) // default goal weight
        }

        val result = db.insert(TABLE_USERS, null, values)
        db.close()
        return result != -1L
    }
}
