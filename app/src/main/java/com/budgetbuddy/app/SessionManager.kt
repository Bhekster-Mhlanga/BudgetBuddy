package com.budgetbuddy.app

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * SessionManager stores the currently logged-in user's ID and username
 * in SharedPreferences so the session persists across app restarts.
 */
class SessionManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("BudgetBuddySession", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_USER_ID = "userId"
        private const val KEY_USERNAME = "username"
        private const val KEY_IS_LOGGED_IN = "isLoggedIn"
    }

    /** Save session after successful login. */
    fun saveSession(userId: Int, username: String) {
        prefs.edit {
            putInt(KEY_USER_ID, userId)
            putString(KEY_USERNAME, username)
            putBoolean(KEY_IS_LOGGED_IN, true)
        }
    }

    /** Clear session on logout. */
    fun clearSession() {
        prefs.edit { clear() }
    }

    /** Check if a user is currently logged in. */
    fun isLoggedIn(): Boolean = prefs.getBoolean(KEY_IS_LOGGED_IN, false)

    /** Get the current user's ID (-1 if not logged in). */
    fun getUserId(): Int = prefs.getInt(KEY_USER_ID, -1)

    /** Get the current user's username. */
    fun getUsername(): String = prefs.getString(KEY_USERNAME, "") ?: ""
}