package com.budgetbuddy.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import java.security.MessageDigest

/**
 * DatabaseHelper — all SQLite operations for BudgetBuddy.
 * Tables: users, categories, expenses, budget_goals, earned_badges
 * v3: budget_goals now stores min_limit + max_limit (separate from monthly_limit)
 *     categories supports edit/delete
 *     user_prefs table added for dark mode toggle
 */
class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "budgetbuddy.db"
        private const val DATABASE_VERSION = 4
        private const val TAG = "DatabaseHelper"

        const val TABLE_USERS         = "users"
        const val TABLE_CATEGORIES    = "categories"
        const val TABLE_EXPENSES      = "expenses"
        const val TABLE_BUDGET_GOALS  = "budget_goals"
        const val TABLE_EARNED_BADGES = "earned_badges"
        const val TABLE_USER_PREFS    = "user_prefs"

        // users
        const val COL_USER_ID        = "id"
        const val COL_USERNAME       = "username"
        const val COL_PASSWORD_HASH  = "password_hash"

        // categories
        const val COL_CAT_ID      = "id"
        const val COL_CAT_NAME    = "name"
        const val COL_CAT_COLOUR  = "colour"
        const val COL_CAT_USER_ID = "user_id"

        // expenses
        const val COL_EXP_ID          = "id"
        const val COL_EXP_AMOUNT      = "amount"
        const val COL_EXP_DESCRIPTION = "description"
        const val COL_EXP_DATE        = "date"
        const val COL_EXP_CATEGORY_ID = "category_id"
        const val COL_EXP_PHOTO_PATH  = "photo_path"
        const val COL_EXP_PHOTO_BLOB  = "photo_blob"
        const val COL_EXP_USER_ID     = "user_id"

        // budget_goals
        const val COL_GOAL_ID            = "id"
        const val COL_GOAL_USER_ID       = "user_id"
        const val COL_GOAL_CATEGORY_ID   = "category_id"
        const val COL_GOAL_MONTHLY_LIMIT = "monthly_limit"   // overall max/limit (kept for compat)
        const val COL_GOAL_MIN_LIMIT     = "min_limit"       // NEW minimum spending goal
        const val COL_GOAL_MAX_LIMIT     = "max_limit"       // NEW maximum spending goal

        // earned_badges
        const val COL_BADGE_USER_ID = "user_id"
        const val COL_BADGE_ID      = "badge_id"

        // user_prefs
        const val COL_PREF_USER_ID    = "user_id"
        const val COL_PREF_DARK_MODE  = "dark_mode"
    }

    // =========================================================================
    // CREATE
    // =========================================================================
    override fun onCreate(db: SQLiteDatabase) {
        Log.d(TAG, "Creating database tables v$DATABASE_VERSION")

        db.execSQL("""CREATE TABLE $TABLE_USERS (
            $COL_USER_ID INTEGER PRIMARY KEY AUTOINCREMENT,
            $COL_USERNAME TEXT UNIQUE NOT NULL,
            $COL_PASSWORD_HASH TEXT NOT NULL
        )""")

        db.execSQL("""CREATE TABLE $TABLE_CATEGORIES (
            $COL_CAT_ID INTEGER PRIMARY KEY AUTOINCREMENT,
            $COL_CAT_NAME TEXT NOT NULL,
            $COL_CAT_COLOUR TEXT NOT NULL DEFAULT '#4CAF50',
            $COL_CAT_USER_ID INTEGER NOT NULL,
            FOREIGN KEY($COL_CAT_USER_ID) REFERENCES $TABLE_USERS($COL_USER_ID)
        )""")

        db.execSQL("""CREATE TABLE $TABLE_EXPENSES (
            $COL_EXP_ID INTEGER PRIMARY KEY AUTOINCREMENT,
            $COL_EXP_AMOUNT REAL NOT NULL,
            $COL_EXP_DESCRIPTION TEXT NOT NULL,
            $COL_EXP_DATE TEXT NOT NULL,
            $COL_EXP_CATEGORY_ID INTEGER NOT NULL,
            $COL_EXP_PHOTO_PATH TEXT,
            $COL_EXP_PHOTO_BLOB BLOB,
            $COL_EXP_USER_ID INTEGER NOT NULL,
            FOREIGN KEY($COL_EXP_CATEGORY_ID) REFERENCES $TABLE_CATEGORIES($COL_CAT_ID),
            FOREIGN KEY($COL_EXP_USER_ID) REFERENCES $TABLE_USERS($COL_USER_ID)
        )""")

        db.execSQL("""CREATE TABLE $TABLE_BUDGET_GOALS (
            $COL_GOAL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
            $COL_GOAL_USER_ID INTEGER NOT NULL,
            $COL_GOAL_CATEGORY_ID INTEGER NOT NULL,
            $COL_GOAL_MONTHLY_LIMIT REAL NOT NULL DEFAULT 0,
            $COL_GOAL_MIN_LIMIT REAL NOT NULL DEFAULT 0,
            $COL_GOAL_MAX_LIMIT REAL NOT NULL DEFAULT 0,
            FOREIGN KEY($COL_GOAL_USER_ID) REFERENCES $TABLE_USERS($COL_USER_ID)
        )""")

        db.execSQL("""CREATE TABLE $TABLE_EARNED_BADGES (
            $COL_BADGE_USER_ID INTEGER NOT NULL,
            $COL_BADGE_ID TEXT NOT NULL,
            PRIMARY KEY($COL_BADGE_USER_ID, $COL_BADGE_ID)
        )""")

        db.execSQL("""CREATE TABLE $TABLE_USER_PREFS (
            $COL_PREF_USER_ID INTEGER PRIMARY KEY,
            $COL_PREF_DARK_MODE INTEGER NOT NULL DEFAULT 0
        )""")
    }

    // =========================================================================
    // UPGRADE
    // =========================================================================
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Log.d(TAG, "Upgrading DB $oldVersion → $newVersion")
        if (oldVersion < 2) {
            runSafe(db, "ALTER TABLE $TABLE_EXPENSES ADD COLUMN $COL_EXP_PHOTO_BLOB BLOB")
        }
        if (oldVersion < 3) {
            runSafe(db, "ALTER TABLE $TABLE_BUDGET_GOALS ADD COLUMN $COL_GOAL_MIN_LIMIT REAL NOT NULL DEFAULT 0")
            runSafe(db, "ALTER TABLE $TABLE_BUDGET_GOALS ADD COLUMN $COL_GOAL_MAX_LIMIT REAL NOT NULL DEFAULT 0")
        }
        if (oldVersion < 4) {
            runSafe(db, """CREATE TABLE IF NOT EXISTS $TABLE_USER_PREFS (
                $COL_PREF_USER_ID INTEGER PRIMARY KEY,
                $COL_PREF_DARK_MODE INTEGER NOT NULL DEFAULT 0
            )""")
        }
    }

    private fun runSafe(db: SQLiteDatabase, sql: String) {
        try { db.execSQL(sql) } catch (e: Exception) { Log.e(TAG, "Migration error: ${e.message}") }
    }

    // =========================================================================
    // PASSWORD HASHING (SHA-256 + salt)
    // =========================================================================
    fun hashPassword(password: String): String {
        val salt = "BudgetBuddySalt2025"
        val bytes = MessageDigest.getInstance("SHA-256").digest((salt + password).toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // =========================================================================
    // USER OPERATIONS
    // =========================================================================
    fun registerUser(username: String, password: String): Long {
        if (username.isBlank() || password.length < 8) return -1
        return try {
            val values = ContentValues().apply {
                put(COL_USERNAME, username.trim())
                put(COL_PASSWORD_HASH, hashPassword(password))
            }
            val id = writableDatabase.insertOrThrow(TABLE_USERS, null, values)
            if (id > 0) seedDefaultCategories(id.toInt(), writableDatabase)
            id
        } catch (e: Exception) {
            Log.e(TAG, "registerUser: ${e.message}")
            -1L
        }
    }

    fun loginUser(username: String, password: String): User? {
        val cursor = readableDatabase.query(
            TABLE_USERS, null,
            "$COL_USERNAME = ?", arrayOf(username.trim()),
            null, null, null
        )
        return if (cursor.moveToFirst()) {
            val hash = cursor.getString(cursor.getColumnIndexOrThrow(COL_PASSWORD_HASH))
            val user = if (hash == hashPassword(password)) User(
                id = cursor.getInt(cursor.getColumnIndexOrThrow(COL_USER_ID)),
                username = cursor.getString(cursor.getColumnIndexOrThrow(COL_USERNAME)),
                passwordHash = hash
            ) else null
            cursor.close()
            user
        } else { cursor.close(); null }
    }

    fun updateUsername(userId: Int, newUsername: String): Boolean {
        if (newUsername.isBlank()) return false
        return try {
            writableDatabase.update(
                TABLE_USERS,
                ContentValues().apply { put(COL_USERNAME, newUsername.trim()) },
                "$COL_USER_ID = ?", arrayOf(userId.toString())
            ) > 0
        } catch (e: Exception) { Log.e(TAG, "updateUsername: ${e.message}"); false }
    }

    fun updatePassword(userId: Int, newPassword: String): Boolean {
        if (newPassword.length < 8) return false
        return writableDatabase.update(
            TABLE_USERS,
            ContentValues().apply { put(COL_PASSWORD_HASH, hashPassword(newPassword)) },
            "$COL_USER_ID = ?", arrayOf(userId.toString())
        ) > 0
    }

    // =========================================================================
    // USER PREFS (dark mode)
    // =========================================================================
    fun isDarkMode(userId: Int): Boolean {
        val cursor = readableDatabase.query(
            TABLE_USER_PREFS, null,
            "$COL_PREF_USER_ID = ?", arrayOf(userId.toString()),
            null, null, null
        )
        val result = if (cursor.moveToFirst())
            cursor.getInt(cursor.getColumnIndexOrThrow(COL_PREF_DARK_MODE)) == 1
        else false
        cursor.close()
        return result
    }

    fun setDarkMode(userId: Int, enabled: Boolean) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_PREF_USER_ID, userId)
            put(COL_PREF_DARK_MODE, if (enabled) 1 else 0)
        }
        db.insertWithOnConflict(TABLE_USER_PREFS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // =========================================================================
    // CATEGORY OPERATIONS
    // =========================================================================
    private fun seedDefaultCategories(userId: Int, db: SQLiteDatabase) {
        val defaults = listOf(
            "Groceries"     to "#4CAF50",
            "Transport"     to "#2196F3",
            "Entertainment" to "#9C27B0",
            "Utilities"     to "#FF9800",
            "Health"        to "#F44336"
        )
        for ((name, colour) in defaults) {
            db.insert(TABLE_CATEGORIES, null, ContentValues().apply {
                put(COL_CAT_NAME, name)
                put(COL_CAT_COLOUR, colour)
                put(COL_CAT_USER_ID, userId)
            })
        }
        Log.d(TAG, "Seeded default categories for user $userId")
    }

    fun getCategories(userId: Int): List<Category> {
        val list = mutableListOf<Category>()
        val cursor = readableDatabase.query(
            TABLE_CATEGORIES, null,
            "$COL_CAT_USER_ID = ?", arrayOf(userId.toString()),
            null, null, "$COL_CAT_NAME ASC"
        )
        while (cursor.moveToNext()) {
            list.add(Category(
                id     = cursor.getInt(cursor.getColumnIndexOrThrow(COL_CAT_ID)),
                name   = cursor.getString(cursor.getColumnIndexOrThrow(COL_CAT_NAME)),
                colour = cursor.getString(cursor.getColumnIndexOrThrow(COL_CAT_COLOUR)),
                userId = userId
            ))
        }
        cursor.close()
        return list
    }

    fun addCategory(userId: Int, name: String, colour: String): Long {
        if (name.isBlank()) return -1L
        // Duplicate name check (case-insensitive) for this user
        val check = readableDatabase.rawQuery(
            "SELECT $COL_CAT_ID FROM $TABLE_CATEGORIES WHERE LOWER($COL_CAT_NAME) = LOWER(?) AND $COL_CAT_USER_ID = ?",
            arrayOf(name.trim(), userId.toString())
        )
        val duplicate = check.moveToFirst()
        check.close()
        if (duplicate) return -2L   // caller can distinguish "duplicate" from "error"
        return try {
            writableDatabase.insert(TABLE_CATEGORIES, null, ContentValues().apply {
                put(COL_CAT_NAME, name.trim())
                put(COL_CAT_COLOUR, colour)
                put(COL_CAT_USER_ID, userId)
            })
        } catch (e: Exception) { Log.e(TAG, "addCategory: ${e.message}"); -1L }
    }

    fun updateCategory(catId: Int, name: String, colour: String): Boolean {
        if (name.isBlank()) return false
        return writableDatabase.update(
            TABLE_CATEGORIES,
            ContentValues().apply { put(COL_CAT_NAME, name.trim()); put(COL_CAT_COLOUR, colour) },
            "$COL_CAT_ID = ?", arrayOf(catId.toString())
        ) > 0
    }

    fun deleteCategory(catId: Int): Boolean {
        return writableDatabase.delete(
            TABLE_CATEGORIES, "$COL_CAT_ID = ?", arrayOf(catId.toString())
        ) > 0
    }

    // =========================================================================
    // EXPENSE OPERATIONS
    // =========================================================================
    fun addExpense(expense: Expense): Long {
        return writableDatabase.insert(TABLE_EXPENSES, null, expenseToValues(expense))
    }

    fun updateExpense(expense: Expense): Boolean {
        return writableDatabase.update(
            TABLE_EXPENSES, expenseToValues(expense),
            "$COL_EXP_ID = ?", arrayOf(expense.id.toString())
        ) > 0
    }

    fun deleteExpense(expenseId: Int): Boolean {
        return writableDatabase.delete(
            TABLE_EXPENSES, "$COL_EXP_ID = ?", arrayOf(expenseId.toString())
        ) > 0
    }

    private fun expenseToValues(e: Expense) = ContentValues().apply {
        put(COL_EXP_AMOUNT, e.amount)
        put(COL_EXP_DESCRIPTION, e.description)
        put(COL_EXP_DATE, e.date)
        put(COL_EXP_CATEGORY_ID, e.categoryId)
        put(COL_EXP_USER_ID, e.userId)
        put(COL_EXP_PHOTO_PATH, e.photoPath)
        if (e.photoBlob != null) put(COL_EXP_PHOTO_BLOB, e.photoBlob) else putNull(COL_EXP_PHOTO_BLOB)
    }

    fun getExpenseById(expenseId: Int): Expense? {
        val cursor = readableDatabase.rawQuery("""
            SELECT e.$COL_EXP_ID, e.$COL_EXP_AMOUNT, e.$COL_EXP_DESCRIPTION,
                   e.$COL_EXP_DATE, e.$COL_EXP_CATEGORY_ID, c.$COL_CAT_NAME,
                   e.$COL_EXP_PHOTO_PATH, e.$COL_EXP_PHOTO_BLOB, e.$COL_EXP_USER_ID
            FROM $TABLE_EXPENSES e
            LEFT JOIN $TABLE_CATEGORIES c ON e.$COL_EXP_CATEGORY_ID = c.$COL_CAT_ID
            WHERE e.$COL_EXP_ID = ?""",
            arrayOf(expenseId.toString())
        )
        val result = if (cursor.moveToFirst()) {
            val bi = cursor.getColumnIndex(COL_EXP_PHOTO_BLOB)
            Expense(
                id           = cursor.getInt(0),
                amount       = cursor.getDouble(1),
                description  = cursor.getString(2),
                date         = cursor.getString(3),
                categoryId   = cursor.getInt(4),
                categoryName = cursor.getString(5) ?: "",
                photoPath    = cursor.getString(6),
                photoBlob    = if (bi >= 0 && !cursor.isNull(bi)) cursor.getBlob(bi) else null,
                userId       = cursor.getInt(8)
            )
        } else null
        cursor.close()
        return result
    }

    fun getExpenses(userId: Int, startDate: String, endDate: String): List<Expense> {
        val list = mutableListOf<Expense>()
        val cursor = readableDatabase.rawQuery("""
            SELECT e.$COL_EXP_ID, e.$COL_EXP_AMOUNT, e.$COL_EXP_DESCRIPTION,
                   e.$COL_EXP_DATE, e.$COL_EXP_CATEGORY_ID, c.$COL_CAT_NAME,
                   e.$COL_EXP_PHOTO_PATH, e.$COL_EXP_PHOTO_BLOB, e.$COL_EXP_USER_ID
            FROM $TABLE_EXPENSES e
            LEFT JOIN $TABLE_CATEGORIES c ON e.$COL_EXP_CATEGORY_ID = c.$COL_CAT_ID
            WHERE e.$COL_EXP_USER_ID = ? AND e.$COL_EXP_DATE BETWEEN ? AND ?
            ORDER BY e.$COL_EXP_DATE DESC""",
            arrayOf(userId.toString(), startDate, endDate)
        )
        while (cursor.moveToNext()) {
            val bi = cursor.getColumnIndex(COL_EXP_PHOTO_BLOB)
            list.add(Expense(
                id           = cursor.getInt(0),
                amount       = cursor.getDouble(1),
                description  = cursor.getString(2),
                date         = cursor.getString(3),
                categoryId   = cursor.getInt(4),
                categoryName = cursor.getString(5) ?: "",
                photoPath    = cursor.getString(6),
                photoBlob    = if (bi >= 0 && !cursor.isNull(bi)) cursor.getBlob(bi) else null,
                userId       = cursor.getInt(8)
            ))
        }
        cursor.close()
        return list
    }

    fun getCategoryTotals(userId: Int, startDate: String, endDate: String): Map<String, Double> {
        val map = mutableMapOf<String, Double>()
        val cursor = readableDatabase.rawQuery("""
            SELECT c.$COL_CAT_NAME, SUM(e.$COL_EXP_AMOUNT)
            FROM $TABLE_EXPENSES e
            LEFT JOIN $TABLE_CATEGORIES c ON e.$COL_EXP_CATEGORY_ID = c.$COL_CAT_ID
            WHERE e.$COL_EXP_USER_ID = ? AND e.$COL_EXP_DATE BETWEEN ? AND ?
            GROUP BY c.$COL_CAT_NAME""",
            arrayOf(userId.toString(), startDate, endDate)
        )
        while (cursor.moveToNext()) map[cursor.getString(0) ?: "Unknown"] = cursor.getDouble(1)
        cursor.close()
        return map
    }

    fun getMonthlyTotal(userId: Int, yearMonth: String): Double {
        val cursor = readableDatabase.rawQuery(
            "SELECT SUM($COL_EXP_AMOUNT) FROM $TABLE_EXPENSES WHERE $COL_EXP_USER_ID = ? AND $COL_EXP_DATE LIKE ?",
            arrayOf(userId.toString(), "$yearMonth%")
        )
        val total = if (cursor.moveToFirst()) cursor.getDouble(0) else 0.0
        cursor.close()
        return total
    }

    fun getDailyTotals(userId: Int, startDate: String, endDate: String): Map<String, Double> {
        val map = mutableMapOf<String, Double>()
        val cursor = readableDatabase.rawQuery("""
            SELECT $COL_EXP_DATE, SUM($COL_EXP_AMOUNT)
            FROM $TABLE_EXPENSES
            WHERE $COL_EXP_USER_ID = ? AND $COL_EXP_DATE BETWEEN ? AND ?
            GROUP BY $COL_EXP_DATE ORDER BY $COL_EXP_DATE ASC""",
            arrayOf(userId.toString(), startDate, endDate)
        )
        while (cursor.moveToNext()) map[cursor.getString(0)] = cursor.getDouble(1)
        cursor.close()
        return map
    }

    // =========================================================================
    // RESET (fresh start)
    // =========================================================================
    /**
     * Wipe all expenses, budget goals and earned badges for a user.
     * The user account and categories are preserved.
     */
    fun resetAllData(userId: Int): Boolean {
        return try {
            val db = writableDatabase
            db.delete(TABLE_EXPENSES,      "$COL_EXP_USER_ID = ?",   arrayOf(userId.toString()))
            db.delete(TABLE_BUDGET_GOALS,  "$COL_GOAL_USER_ID = ?",  arrayOf(userId.toString()))
            db.delete(TABLE_EARNED_BADGES, "$COL_BADGE_USER_ID = ?", arrayOf(userId.toString()))
            Log.d(TAG, "Reset all data for user $userId")
            true
        } catch (e: Exception) { Log.e(TAG, "resetAllData: ${e.message}"); false }
    }

    // =========================================================================
    // CATEGORY TOTALS WITH COLOR (for bar chart)
    // =========================================================================
    data class CategoryTotal(val name: String, val total: Double, val colour: String)

    fun getCategoryTotalsWithColors(userId: Int, startDate: String, endDate: String): List<CategoryTotal> {
        val list = mutableListOf<CategoryTotal>()
        val cursor = readableDatabase.rawQuery("""
            SELECT c.$COL_CAT_NAME, SUM(e.$COL_EXP_AMOUNT), c.$COL_CAT_COLOUR
            FROM $TABLE_EXPENSES e
            INNER JOIN $TABLE_CATEGORIES c ON e.$COL_EXP_CATEGORY_ID = c.$COL_CAT_ID
            WHERE e.$COL_EXP_USER_ID = ? AND e.$COL_EXP_DATE BETWEEN ? AND ?
            GROUP BY c.$COL_CAT_ID, c.$COL_CAT_NAME, c.$COL_CAT_COLOUR
            ORDER BY SUM(e.$COL_EXP_AMOUNT) DESC""",
            arrayOf(userId.toString(), startDate, endDate)
        )
        while (cursor.moveToNext()) {
            list.add(CategoryTotal(
                name   = cursor.getString(0) ?: "Unknown",
                total  = cursor.getDouble(1),
                colour = cursor.getString(2) ?: "#1565C0"
            ))
        }
        cursor.close()
        return list
    }

    fun getExpenseDates(userId: Int): List<String> {
        val list = mutableListOf<String>()
        val cursor = readableDatabase.rawQuery(
            "SELECT DISTINCT $COL_EXP_DATE FROM $TABLE_EXPENSES WHERE $COL_EXP_USER_ID = ? ORDER BY $COL_EXP_DATE DESC",
            arrayOf(userId.toString())
        )
        while (cursor.moveToNext()) list.add(cursor.getString(0))
        cursor.close()
        return list
    }

    // =========================================================================
    // BUDGET GOAL OPERATIONS  (min + max per category)
    // =========================================================================

    /**
     * Set the overall monthly budget (categoryId = -1) OR a per-category limit.
     * monthlyLimit is the hard max; minLimit/maxLimit are the goal band.
     */
    fun setBudgetGoal(userId: Int, categoryId: Int, monthlyLimit: Double): Boolean {
        return setBudgetGoalFull(userId, categoryId, monthlyLimit, 0.0, monthlyLimit)
    }

    fun setBudgetGoalFull(userId: Int, categoryId: Int, monthlyLimit: Double, minLimit: Double, maxLimit: Double): Boolean {
        if (monthlyLimit < 0) return false
        val db = writableDatabase
        val cursor = db.query(
            TABLE_BUDGET_GOALS, arrayOf(COL_GOAL_ID),
            "$COL_GOAL_USER_ID = ? AND $COL_GOAL_CATEGORY_ID = ?",
            arrayOf(userId.toString(), categoryId.toString()),
            null, null, null
        )
        val values = ContentValues().apply {
            put(COL_GOAL_USER_ID, userId)
            put(COL_GOAL_CATEGORY_ID, categoryId)
            put(COL_GOAL_MONTHLY_LIMIT, monthlyLimit)
            put(COL_GOAL_MIN_LIMIT, minLimit)
            put(COL_GOAL_MAX_LIMIT, maxLimit)
        }
        return if (cursor.moveToFirst()) {
            val id = cursor.getInt(cursor.getColumnIndexOrThrow(COL_GOAL_ID))
            cursor.close()
            db.update(TABLE_BUDGET_GOALS, values, "$COL_GOAL_ID = ?", arrayOf(id.toString())) > 0
        } else {
            cursor.close()
            db.insert(TABLE_BUDGET_GOALS, null, values) > 0
        }
    }

    /** Returns map of categoryId → max monthly limit. */
    fun getBudgetGoals(userId: Int): Map<Int, Double> {
        val map = mutableMapOf<Int, Double>()
        val cursor = readableDatabase.query(
            TABLE_BUDGET_GOALS, null,
            "$COL_GOAL_USER_ID = ?", arrayOf(userId.toString()),
            null, null, null
        )
        while (cursor.moveToNext()) {
            map[cursor.getInt(cursor.getColumnIndexOrThrow(COL_GOAL_CATEGORY_ID))] =
                cursor.getDouble(cursor.getColumnIndexOrThrow(COL_GOAL_MONTHLY_LIMIT))
        }
        cursor.close()
        return map
    }

    data class GoalBand(val min: Double, val max: Double)

    /** Returns GoalBand (min, max) for a specific category, or null if not set. */
    fun getGoalBand(userId: Int, categoryId: Int): GoalBand? {
        val cursor = readableDatabase.query(
            TABLE_BUDGET_GOALS, null,
            "$COL_GOAL_USER_ID = ? AND $COL_GOAL_CATEGORY_ID = ?",
            arrayOf(userId.toString(), categoryId.toString()),
            null, null, null
        )
        return if (cursor.moveToFirst()) {
            val min = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_GOAL_MIN_LIMIT))
            val max = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_GOAL_MAX_LIMIT))
            cursor.close()
            GoalBand(min, max)
        } else { cursor.close(); null }
    }

    /** Returns map categoryId → GoalBand for all goals of this user. */
    fun getAllGoalBands(userId: Int): Map<Int, GoalBand> {
        val map = mutableMapOf<Int, GoalBand>()
        val cursor = readableDatabase.query(
            TABLE_BUDGET_GOALS, null,
            "$COL_GOAL_USER_ID = ?", arrayOf(userId.toString()),
            null, null, null
        )
        while (cursor.moveToNext()) {
            val catId = cursor.getInt(cursor.getColumnIndexOrThrow(COL_GOAL_CATEGORY_ID))
            val min   = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_GOAL_MIN_LIMIT))
            val max   = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_GOAL_MAX_LIMIT))
            map[catId] = GoalBand(min, max)
        }
        cursor.close()
        return map
    }

    // =========================================================================
    // BADGE OPERATIONS
    // =========================================================================
    fun awardBadge(userId: Int, badgeId: String) {
        writableDatabase.insertWithOnConflict(
            TABLE_EARNED_BADGES, null,
            ContentValues().apply { put(COL_BADGE_USER_ID, userId); put(COL_BADGE_ID, badgeId) },
            SQLiteDatabase.CONFLICT_IGNORE
        )
        Log.d(TAG, "Badge awarded: $badgeId → user $userId")
    }

    fun getEarnedBadgeIds(userId: Int): Set<String> {
        val set = mutableSetOf<String>()
        val cursor = readableDatabase.query(
            TABLE_EARNED_BADGES, arrayOf(COL_BADGE_ID),
            "$COL_BADGE_USER_ID = ?", arrayOf(userId.toString()),
            null, null, null
        )
        while (cursor.moveToNext()) set.add(cursor.getString(0))
        cursor.close()
        return set
    }

    // =========================================================================
    // GAMIFICATION
    // =========================================================================
    fun calculateStreak(userId: Int): Int {
        val dates = getExpenseDates(userId)
        if (dates.isEmpty()) return 0
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val cal = java.util.Calendar.getInstance()
        var streak = 0
        var expected = cal.time
        for (dateStr in dates) {
            val date = fmt.parse(dateStr) ?: break
            if (fmt.format(date) == fmt.format(expected)) {
                streak++
                cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
                expected = cal.time
            } else break
        }
        return streak
    }

    fun calculateBudgetScore(userId: Int, yearMonth: String): Int {
        val goals = getBudgetGoals(userId)
        val totalGoal = goals[-1] ?: return 0
        val spent = getMonthlyTotal(userId, yearMonth)
        val withinBudget   = if (spent <= totalGoal) 50 else (totalGoal / spent * 50).toInt()
        val streak         = calculateStreak(userId)
        val consistency    = minOf((streak / 30.0 * 30).toInt(), 30)
        val savingsScore   = if (spent <= totalGoal) 20 else 0
        return minOf(withinBudget + consistency + savingsScore, 100)
    }

    fun getBadgeTier(score: Int): String = when {
        score >= 90 -> "🏆 Platinum"
        score >= 70 -> "🥇 Gold"
        score >= 40 -> "🥈 Silver"
        else        -> "🥉 Bronze"
    }
}