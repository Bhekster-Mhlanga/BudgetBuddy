package com.budgetbuddy.app

import org.junit.Assert.*
import org.junit.Test

/**
 * Pure JVM unit tests for Models.kt data classes.
 * No Android context required — these run locally on the JVM (fast).
 */
class ModelsTest {

    // =========================================================================
    // Expense equality and hashCode (uses ByteArray — needs custom equals)
    // =========================================================================

    @Test
    fun expense_equalityWithoutPhoto() {
        val e1 = Expense(id = 1, amount = 100.0, description = "Test", date = "2026-04-23",
            categoryId = 2, userId = 1)
        val e2 = Expense(id = 1, amount = 100.0, description = "Test", date = "2026-04-23",
            categoryId = 2, userId = 1)
        assertEquals("Two expenses with the same fields must be equal", e1, e2)
    }

    @Test
    fun expense_inequalityOnAmount() {
        val e1 = Expense(id = 1, amount = 100.0, description = "Test", date = "2026-04-23",
            categoryId = 2, userId = 1)
        val e2 = e1.copy(amount = 200.0)
        assertNotEquals("Expenses with different amounts must not be equal", e1, e2)
    }

    @Test
    fun expense_inequalityOnDescription() {
        val e1 = Expense(id = 1, amount = 50.0, description = "Alpha", date = "2026-04-01",
            categoryId = 1, userId = 1)
        val e2 = e1.copy(description = "Beta")
        assertNotEquals(e1, e2)
    }

    @Test
    fun expense_withPhotoBlob_notEqualToWithout() {
        val e1 = Expense(id = 1, amount = 50.0, description = "X", date = "2026-01-01",
            categoryId = 1, userId = 1, photoBlob = byteArrayOf(1, 2, 3))
        val e2 = e1.copy(photoBlob = null)
        assertNotEquals("Expense with photo must not equal expense without photo", e1, e2)
    }

    @Test
    fun expense_defaultCategoryNameIsEmpty() {
        val e = Expense(id = 0, amount = 10.0, description = "Test", date = "2026-04-01",
            categoryId = 1, userId = 1)
        assertEquals("", e.categoryName)
    }

    @Test
    fun expense_defaultPhotoFieldsAreNull() {
        val e = Expense(id = 0, amount = 10.0, description = "Test", date = "2026-04-01",
            categoryId = 1, userId = 1)
        assertNull(e.photoBlob)
        assertNull(e.photoPath)
    }

    // =========================================================================
    // Category
    // =========================================================================

    @Test
    fun category_defaultColourIsGreen() {
        val cat = Category(id = 1, name = "Food", userId = 1)
        assertEquals("#4CAF50", cat.colour)
    }

    @Test
    fun category_equalityOnAllFields() {
        val c1 = Category(id = 1, name = "Food", colour = "#4CAF50", userId = 1)
        val c2 = Category(id = 1, name = "Food", colour = "#4CAF50", userId = 1)
        assertEquals(c1, c2)
    }

    @Test
    fun category_inequalityOnName() {
        val c1 = Category(id = 1, name = "Food", userId = 1)
        val c2 = Category(id = 1, name = "Transport", userId = 1)
        assertNotEquals(c1, c2)
    }

    // =========================================================================
    // User
    // =========================================================================

    @Test
    fun user_defaultIdIsZero() {
        val user = User(username = "alice", passwordHash = "abc123")
        assertEquals(0, user.id)
    }

    @Test
    fun user_equalityOnAllFields() {
        val u1 = User(id = 1, username = "alice", passwordHash = "hash")
        val u2 = User(id = 1, username = "alice", passwordHash = "hash")
        assertEquals(u1, u2)
    }

    // =========================================================================
    // Badge
    // =========================================================================

    @Test
    fun badge_defaultEarnedIsFalse() {
        val badge = Badge(id = "FIRST_EXPENSE", name = "First Expense", description = "Log first expense")
        assertFalse("Badge must be unearned by default", badge.earned)
    }

    @Test
    fun badge_canBeMarkedEarned() {
        val badge = Badge(id = "STREAK_7", name = "7-Day Streak", description = "7 day streak", earned = true)
        assertTrue(badge.earned)
    }

    @Test
    fun badge_copyWithEarned() {
        val badge = Badge(id = "BUDGET_HERO", name = "Budget Hero", description = "Stay within budget")
        val earnedBadge = badge.copy(earned = true)
        assertFalse(badge.earned)
        assertTrue(earnedBadge.earned)
    }
}
