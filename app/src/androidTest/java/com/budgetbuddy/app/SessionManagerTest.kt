package com.budgetbuddy.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for SessionManager — SharedPreferences-backed login session.
 */
@RunWith(AndroidJUnit4::class)
class SessionManagerTest {

    private lateinit var session: SessionManager

    @Before
    fun setUp() {
        session = SessionManager(ApplicationProvider.getApplicationContext())
        session.clearSession() // always start from a clean state
    }

    @Test
    fun isLoggedIn_falseByDefault() {
        assertFalse("Session must not be active before any login", session.isLoggedIn())
    }

    @Test
    fun saveSession_setsLoggedInTrue() {
        session.saveSession(1, "alice")
        assertTrue("Session must be active after saveSession", session.isLoggedIn())
    }

    @Test
    fun saveSession_storesUserIdCorrectly() {
        session.saveSession(42, "alice")
        assertEquals(42, session.getUserId())
    }

    @Test
    fun saveSession_storesUsernameCorrectly() {
        session.saveSession(1, "bob")
        assertEquals("bob", session.getUsername())
    }

    @Test
    fun clearSession_resetsAllValues() {
        session.saveSession(5, "charlie")
        session.clearSession()
        assertFalse("Session must be inactive after clearSession", session.isLoggedIn())
        assertEquals("Username must be empty after clearSession", "", session.getUsername())
        assertEquals("User ID must be -1 after clearSession", -1, session.getUserId())
    }

    @Test
    fun getUserId_returnsNegativeOneWhenNotLoggedIn() {
        assertEquals(-1, session.getUserId())
    }

    @Test
    fun getUsername_returnsEmptyStringWhenNotLoggedIn() {
        assertEquals("", session.getUsername())
    }

    @Test
    fun saveSession_overwritesPreviousSession() {
        session.saveSession(1, "firstUser")
        session.saveSession(2, "secondUser")
        assertEquals(2, session.getUserId())
        assertEquals("secondUser", session.getUsername())
    }
}
