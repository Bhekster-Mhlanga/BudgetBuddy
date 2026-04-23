package com.budgetbuddy.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for DatabaseHelper.
 * Runs on an Android device/emulator (or Robolectric in CI).
 * Tests every major DB function: auth, categories, expenses, budget goals, badges, gamification.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseHelperTest {

    private lateinit var db: DatabaseHelper

    // ── Setup & Teardown ──────────────────────────────────────────────────────

    @Before
    fun setUp() {
        // ApplicationProvider gives a real Android context without needing an Activity
        db = DatabaseHelper(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        db.close()
    }

    // =========================================================================
    // PASSWORD HASHING
    // =========================================================================

    @Test
    fun hashPassword_returnsSHA256HexString() {
        val hash = db.hashPassword("testPassword1")
        // SHA-256 produces a 64-character hex string
        assertEquals(64, hash.length)
        assertTrue("Hash should only contain hex characters", hash.matches(Regex("[0-9a-f]+")))
    }

    @Test
    fun hashPassword_sameInputSameOutput() {
        val hash1 = db.hashPassword("myPassword")
        val hash2 = db.hashPassword("myPassword")
        assertEquals("Same password must always produce the same hash", hash1, hash2)
    }

    @Test
    fun hashPassword_differentInputsDifferentOutputs() {
        val hash1 = db.hashPassword("password1")
        val hash2 = db.hashPassword("password2")
        assertNotEquals("Different passwords must not produce the same hash", hash1, hash2)
    }

    // =========================================================================
    // USER REGISTRATION
    // =========================================================================

    @Test
    fun registerUser_validCredentials_returnsPositiveId() {
        val id = db.registerUser("testuser_${System.currentTimeMillis()}", "securePass123")
        assertTrue("Register should return a positive user ID", id > 0)
    }

    @Test
    fun registerUser_duplicateUsername_returnsNegativeOne() {
        val username = "duplicate_${System.currentTimeMillis()}"
        db.registerUser(username, "password123")
        val secondId = db.registerUser(username, "password456")
        assertTrue("Duplicate username must return -1", secondId < 0)
    }

    @Test
    fun registerUser_shortPassword_returnsNegativeOne() {
        val id = db.registerUser("validuser_${System.currentTimeMillis()}", "short")
        assertEquals("Password shorter than 8 chars must be rejected", -1L, id)
    }

    @Test
    fun registerUser_blankUsername_returnsNegativeOne() {
        val id = db.registerUser("   ", "password123")
        assertEquals("Blank username must be rejected", -1L, id)
    }

    @Test
    fun registerUser_seedsDefaultCategories() {
        val username = "seedtest_${System.currentTimeMillis()}"
        val id = db.registerUser(username, "password123")
        assertTrue(id > 0)
        val categories = db.getCategories(id.toInt())
        assertEquals("New user must have 5 default categories", 5, categories.size)
        val names = categories.map { it.name }
        assertTrue(names.contains("Groceries"))
        assertTrue(names.contains("Transport"))
        assertTrue(names.contains("Entertainment"))
        assertTrue(names.contains("Utilities"))
        assertTrue(names.contains("Health"))
    }

    // =========================================================================
    // USER LOGIN
    // =========================================================================

    @Test
    fun loginUser_correctCredentials_returnsUser() {
        val username = "logintest_${System.currentTimeMillis()}"
        val password = "securePass123"
        db.registerUser(username, password)
        val user = db.loginUser(username, password)
        assertNotNull("Login with correct credentials must return a User", user)
        assertEquals(username, user!!.username)
    }

    @Test
    fun loginUser_wrongPassword_returnsNull() {
        val username = "wrongpass_${System.currentTimeMillis()}"
        db.registerUser(username, "correctPass123")
        val user = db.loginUser(username, "wrongPassword!")
        assertNull("Login with wrong password must return null", user)
    }

    @Test
    fun loginUser_unknownUsername_returnsNull() {
        val user = db.loginUser("nobody_${System.currentTimeMillis()}", "anyPassword123")
        assertNull("Login with unknown username must return null", user)
    }

    // =========================================================================
    // UPDATE USERNAME / PASSWORD
    // =========================================================================

    @Test
    fun updateUsername_validNewName_returnsTrue() {
        val id = db.registerUser("oldname_${System.currentTimeMillis()}", "password123").toInt()
        val newName = "newname_${System.currentTimeMillis()}"
        assertTrue(db.updateUsername(id, newName))
        val user = db.loginUser(newName, "password123")
        assertNotNull("Should be able to log in with the updated username", user)
    }

    @Test
    fun updatePassword_validNewPassword_returnsTrue() {
        val username = "pwdchange_${System.currentTimeMillis()}"
        val id = db.registerUser(username, "oldPassword1").toInt()
        assertTrue(db.updatePassword(id, "newPassword99"))
        // Old password should no longer work
        assertNull(db.loginUser(username, "oldPassword1"))
        // New password should work
        assertNotNull(db.loginUser(username, "newPassword99"))
    }

    @Test
    fun updatePassword_tooShort_returnsFalse() {
        val id = db.registerUser("shortpw_${System.currentTimeMillis()}", "password123").toInt()
        assertFalse("Password shorter than 8 chars must be rejected", db.updatePassword(id, "abc"))
    }

    // =========================================================================
    // CATEGORIES
    // =========================================================================

    @Test
    fun addCategory_newName_returnsPositiveId() {
        val userId = db.registerUser("cattest_${System.currentTimeMillis()}", "password123").toInt()
        val id = db.addCategory(userId, "Shopping", "#FF5722")
        assertTrue("New category must return positive ID", id > 0)
    }

    @Test
    fun addCategory_duplicateName_returnsNegativeTwo() {
        val userId = db.registerUser("catdup_${System.currentTimeMillis()}", "password123").toInt()
        db.addCategory(userId, "Dining", "#FF9800")
        val dupId = db.addCategory(userId, "Dining", "#FF9800")
        assertEquals("Duplicate category name must return -2", -2L, dupId)
    }

    @Test
    fun addCategory_blankName_returnsNegativeOne() {
        val userId = db.registerUser("catblank_${System.currentTimeMillis()}", "password123").toInt()
        val id = db.addCategory(userId, "   ", "#FF9800")
        assertEquals("Blank category name must be rejected with -1", -1L, id)
    }

    @Test
    fun updateCategory_changesNameAndColour() {
        val userId = db.registerUser("catupdate_${System.currentTimeMillis()}", "password123").toInt()
        val catId = db.addCategory(userId, "OldName", "#000000").toInt()
        val updated = db.updateCategory(catId, "NewName", "#FFFFFF")
        assertTrue(updated)
        val cats = db.getCategories(userId)
        val cat = cats.find { it.id == catId }
        assertNotNull(cat)
        assertEquals("NewName", cat!!.name)
        assertEquals("#FFFFFF", cat.colour)
    }

    @Test
    fun deleteCategory_removesFromList() {
        val userId = db.registerUser("catdelete_${System.currentTimeMillis()}", "password123").toInt()
        val catId = db.addCategory(userId, "ToDelete", "#123456").toInt()
        assertTrue(db.deleteCategory(catId))
        val cats = db.getCategories(userId)
        assertNull("Deleted category must not appear in list", cats.find { it.id == catId })
    }

    // =========================================================================
    // EXPENSES
    // =========================================================================

    private fun createUserAndCategory(tag: String): Pair<Int, Int> {
        val userId = db.registerUser("expuser_$tag", "password123").toInt()
        val catId  = db.addCategory(userId, "Food", "#4CAF50").toInt()
        return Pair(userId, catId)
    }

    @Test
    fun addExpense_validExpense_returnsPositiveId() {
        val (userId, catId) = createUserAndCategory(System.currentTimeMillis().toString())
        val expense = Expense(
            amount = 150.0, description = "Lunch", date = "2026-04-23",
            categoryId = catId, userId = userId
        )
        val id = db.addExpense(expense)
        assertTrue("addExpense must return a positive row ID", id > 0)
    }

    @Test
    fun getExpenseById_returnsCorrectExpense() {
        val (userId, catId) = createUserAndCategory("getbyid_${System.currentTimeMillis()}")
        val expense = Expense(
            amount = 99.99, description = "Coffee", date = "2026-04-23",
            categoryId = catId, userId = userId
        )
        val id = db.addExpense(expense).toInt()
        val fetched = db.getExpenseById(id)
        assertNotNull(fetched)
        assertEquals(99.99, fetched!!.amount, 0.001)
        assertEquals("Coffee", fetched.description)
        assertEquals("2026-04-23", fetched.date)
    }

    @Test
    fun updateExpense_changesAmount() {
        val (userId, catId) = createUserAndCategory("update_${System.currentTimeMillis()}")
        val expense = Expense(
            amount = 50.0, description = "Dinner", date = "2026-04-23",
            categoryId = catId, userId = userId
        )
        val id = db.addExpense(expense).toInt()
        val updated = expense.copy(id = id, amount = 75.0)
        assertTrue(db.updateExpense(updated))
        assertEquals(75.0, db.getExpenseById(id)!!.amount, 0.001)
    }

    @Test
    fun deleteExpense_removesExpense() {
        val (userId, catId) = createUserAndCategory("delete_${System.currentTimeMillis()}")
        val expense = Expense(
            amount = 30.0, description = "Snack", date = "2026-04-23",
            categoryId = catId, userId = userId
        )
        val id = db.addExpense(expense).toInt()
        assertTrue(db.deleteExpense(id))
        assertNull("Deleted expense must not be retrievable", db.getExpenseById(id))
    }

    @Test
    fun getExpenses_filtersCorrectlyByDateRange() {
        val (userId, catId) = createUserAndCategory("daterange_${System.currentTimeMillis()}")
        db.addExpense(Expense(amount = 10.0, description = "A", date = "2026-03-01", categoryId = catId, userId = userId))
        db.addExpense(Expense(amount = 20.0, description = "B", date = "2026-04-15", categoryId = catId, userId = userId))
        db.addExpense(Expense(amount = 30.0, description = "C", date = "2026-04-23", categoryId = catId, userId = userId))

        val results = db.getExpenses(userId, "2026-04-01", "2026-04-30")
        assertEquals("Only April expenses must be returned", 2, results.size)
        assertTrue(results.all { it.date.startsWith("2026-04") })
    }

    @Test
    fun getMonthlyTotal_sumsCorrectly() {
        val (userId, catId) = createUserAndCategory("monthly_${System.currentTimeMillis()}")
        db.addExpense(Expense(amount = 100.0, description = "A", date = "2026-04-01", categoryId = catId, userId = userId))
        db.addExpense(Expense(amount = 250.0, description = "B", date = "2026-04-15", categoryId = catId, userId = userId))
        db.addExpense(Expense(amount = 999.0, description = "C", date = "2026-03-01", categoryId = catId, userId = userId))

        val total = db.getMonthlyTotal(userId, "2026-04")
        assertEquals("Monthly total must only sum expenses for the given month", 350.0, total, 0.001)
    }

    @Test
    fun getCategoryTotals_groupsCorrectly() {
        val userId = db.registerUser("cattotals_${System.currentTimeMillis()}", "password123").toInt()
        val foodId = db.addCategory(userId, "Food", "#4CAF50").toInt()
        val transId = db.addCategory(userId, "Transport", "#2196F3").toInt()

        db.addExpense(Expense(amount = 100.0, description = "Groceries", date = "2026-04-01", categoryId = foodId, userId = userId))
        db.addExpense(Expense(amount = 50.0, description = "Bus", date = "2026-04-02", categoryId = transId, userId = userId))
        db.addExpense(Expense(amount = 75.0, description = "Lunch", date = "2026-04-10", categoryId = foodId, userId = userId))

        val totals = db.getCategoryTotals(userId, "2026-04-01", "2026-04-30")
        assertEquals(175.0, totals["Food"] ?: 0.0, 0.001)
        assertEquals(50.0, totals["Transport"] ?: 0.0, 0.001)
    }

    // =========================================================================
    // BUDGET GOALS
    // =========================================================================

    @Test
    fun setBudgetGoal_andGetBudgetGoals_roundTrip() {
        val userId = db.registerUser("goaltest_${System.currentTimeMillis()}", "password123").toInt()
        assertTrue(db.setBudgetGoal(userId, -1, 5000.0))
        val goals = db.getBudgetGoals(userId)
        assertEquals(5000.0, goals[-1] ?: 0.0, 0.001)
    }

    @Test
    fun setBudgetGoalFull_storesMinAndMax() {
        val userId = db.registerUser("goalfull_${System.currentTimeMillis()}", "password123").toInt()
        val catId = db.addCategory(userId, "Groceries", "#4CAF50").toInt()
        assertTrue(db.setBudgetGoalFull(userId, catId, 1000.0, 200.0, 1000.0))
        val band = db.getGoalBand(userId, catId)
        assertNotNull(band)
        assertEquals(200.0, band!!.min, 0.001)
        assertEquals(1000.0, band.max, 0.001)
    }

    @Test
    fun setBudgetGoal_negativLimit_returnsFalse() {
        val userId = db.registerUser("goalneg_${System.currentTimeMillis()}", "password123").toInt()
        assertFalse("Negative budget limit must be rejected", db.setBudgetGoal(userId, -1, -500.0))
    }

    @Test
    fun setBudgetGoal_updatesExistingGoal() {
        val userId = db.registerUser("goalupdate_${System.currentTimeMillis()}", "password123").toInt()
        db.setBudgetGoal(userId, -1, 3000.0)
        db.setBudgetGoal(userId, -1, 4500.0)
        val goals = db.getBudgetGoals(userId)
        assertEquals("Updating an existing goal must replace the old value", 4500.0, goals[-1] ?: 0.0, 0.001)
    }

    // =========================================================================
    // BADGES
    // =========================================================================

    @Test
    fun awardBadge_andGetEarnedBadgeIds_roundTrip() {
        val userId = db.registerUser("badgetest_${System.currentTimeMillis()}", "password123").toInt()
        db.awardBadge(userId, "FIRST_EXPENSE")
        val badges = db.getEarnedBadgeIds(userId)
        assertTrue("FIRST_EXPENSE badge must be in earned set", badges.contains("FIRST_EXPENSE"))
    }

    @Test
    fun awardBadge_duplicate_doesNotCrash() {
        val userId = db.registerUser("dupbadge_${System.currentTimeMillis()}", "password123").toInt()
        db.awardBadge(userId, "STREAK_7")
        db.awardBadge(userId, "STREAK_7") // awarding same badge twice must not throw
        val badges = db.getEarnedBadgeIds(userId)
        assertEquals("Duplicate badge award must not create duplicates", 1, badges.count { it == "STREAK_7" })
    }

    @Test
    fun getEarnedBadgeIds_emptyForNewUser() {
        val userId = db.registerUser("nobadge_${System.currentTimeMillis()}", "password123").toInt()
        val badges = db.getEarnedBadgeIds(userId)
        assertTrue("New user must have no earned badges", badges.isEmpty())
    }

    // =========================================================================
    // GAMIFICATION
    // =========================================================================

    @Test
    fun getBadgeTier_correctTiers() {
        assertEquals("🥉 Bronze",   db.getBadgeTier(0))
        assertEquals("🥉 Bronze",   db.getBadgeTier(39))
        assertEquals("🥈 Silver",   db.getBadgeTier(40))
        assertEquals("🥈 Silver",   db.getBadgeTier(69))
        assertEquals("🥇 Gold",     db.getBadgeTier(70))
        assertEquals("🥇 Gold",     db.getBadgeTier(89))
        assertEquals("🏆 Platinum", db.getBadgeTier(90))
        assertEquals("🏆 Platinum", db.getBadgeTier(100))
    }

    @Test
    fun calculateStreak_noExpenses_returnsZero() {
        val userId = db.registerUser("streak0_${System.currentTimeMillis()}", "password123").toInt()
        assertEquals(0, db.calculateStreak(userId))
    }

    // =========================================================================
    // DARK MODE
    // =========================================================================

    @Test
    fun darkMode_defaultIsFalse() {
        val userId = db.registerUser("darkdefault_${System.currentTimeMillis()}", "password123").toInt()
        assertFalse("Dark mode must be off by default", db.isDarkMode(userId))
    }

    @Test
    fun setDarkMode_persistsCorrectly() {
        val userId = db.registerUser("darkmode_${System.currentTimeMillis()}", "password123").toInt()
        db.setDarkMode(userId, true)
        assertTrue(db.isDarkMode(userId))
        db.setDarkMode(userId, false)
        assertFalse(db.isDarkMode(userId))
    }

    // =========================================================================
    // RESET
    // =========================================================================

    @Test
    fun resetAllData_clearsExpensesAndBadgesButKeepsAccount() {
        val username = "resettest_${System.currentTimeMillis()}"
        val userId = db.registerUser(username, "password123").toInt()
        val catId = db.getCategories(userId).first().id

        db.addExpense(Expense(amount = 50.0, description = "X", date = "2026-04-01", categoryId = catId, userId = userId))
        db.awardBadge(userId, "FIRST_EXPENSE")
        db.setBudgetGoal(userId, -1, 2000.0)

        assertTrue(db.resetAllData(userId))

        val expenses = db.getExpenses(userId, "2000-01-01", "2099-12-31")
        assertTrue("Expenses must be cleared after reset", expenses.isEmpty())

        val badges = db.getEarnedBadgeIds(userId)
        assertTrue("Badges must be cleared after reset", badges.isEmpty())

        val goals = db.getBudgetGoals(userId)
        assertTrue("Budget goals must be cleared after reset", goals.isEmpty())

        // Account must still exist
        assertNotNull("User account must still exist after reset", db.loginUser(username, "password123"))
    }
}
