package com.budgetbuddy.app

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * SettingsActivity — change username, password, dark mode, manage categories,
 * export CSV, and logout.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var db: DatabaseHelper
    private lateinit var session: SessionManager

    private lateinit var tvCurrentUsername: TextView
    private lateinit var etNewUsername: EditText
    private lateinit var btnChangeUsername: Button
    private lateinit var etCurrentPassword: EditText
    private lateinit var etNewPassword: EditText
    private lateinit var btnChangePassword: Button
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private lateinit var switchDarkMode: Switch
    private lateinit var btnManageCategories: LinearLayout
    private lateinit var btnExportCsv: LinearLayout
    private lateinit var btnResetData: Button
    private lateinit var btnLogout: Button

    companion object { private const val TAG = "SettingsActivity" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = DatabaseHelper(this)
        session = SessionManager(this)
        setContentView(R.layout.activity_settings)

        bindViews()
        displayCurrentUser()
        loadDarkModeState()
        setupListeners()

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Settings"
    }

    private fun bindViews() {
        tvCurrentUsername  = findViewById(R.id.tvCurrentUsername)
        etNewUsername      = findViewById(R.id.etNewUsername)
        btnChangeUsername  = findViewById(R.id.btnChangeUsername)
        etCurrentPassword  = findViewById(R.id.etCurrentPassword)
        etNewPassword      = findViewById(R.id.etNewPassword)
        btnChangePassword  = findViewById(R.id.btnChangePassword)
        switchDarkMode     = findViewById(R.id.switchDarkMode)
        btnManageCategories = findViewById(R.id.btnManageCategories)
        btnExportCsv       = findViewById(R.id.btnExportCsv)
        btnResetData       = findViewById(R.id.btnResetData)
        btnLogout          = findViewById(R.id.btnLogout)
    }

    @SuppressLint("SetTextI18n")
    private fun displayCurrentUser() {
        tvCurrentUsername.text = "Logged in as: ${session.getUsername()}"
    }

    private fun loadDarkModeState() {
        switchDarkMode.isChecked = db.isDarkMode(session.getUserId())
    }

    private fun setupListeners() {
        btnChangeUsername.setOnClickListener   { changeUsername() }
        btnChangePassword.setOnClickListener   { changePassword() }
        switchDarkMode.setOnCheckedChangeListener { _, checked -> toggleDarkMode(checked) }
        btnManageCategories.setOnClickListener { startActivity(Intent(this, ManageCategoriesActivity::class.java)) }
        btnExportCsv.setOnClickListener        { exportCsv() }
        btnResetData.setOnClickListener        { confirmResetData() }
        btnLogout.setOnClickListener           { logout() }
    }

    // -------------------------------------------------------------------------
    private fun changeUsername() {
        val newUsername = etNewUsername.text.toString().trim()
        if (newUsername.isEmpty()) { etNewUsername.error = "Enter a new username"; return }
        val userId = session.getUserId()
        if (db.updateUsername(userId, newUsername)) {
            session.saveSession(userId, newUsername)
            displayCurrentUser()
            etNewUsername.text.clear()
            Toast.makeText(this, "Username updated to $newUsername", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Username changed to $newUsername")
        } else Toast.makeText(this, "Username already taken", Toast.LENGTH_SHORT).show()
    }

    private fun changePassword() {
        val currentPw = etCurrentPassword.text.toString()
        val newPw     = etNewPassword.text.toString()
        if (currentPw.isEmpty()) { etCurrentPassword.error = "Enter current password"; return }
        if (newPw.length < 8)   { etNewPassword.error = "Min 8 characters"; return }
        if (db.loginUser(session.getUsername(), currentPw) == null) {
            etCurrentPassword.error = "Current password is incorrect"; return
        }
        if (db.updatePassword(session.getUserId(), newPw)) {
            etCurrentPassword.text.clear(); etNewPassword.text.clear()
            Toast.makeText(this, "Password changed successfully", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Password updated")
        } else Toast.makeText(this, "Failed to change password", Toast.LENGTH_SHORT).show()
    }

    private fun toggleDarkMode(enabled: Boolean) {
        db.setDarkMode(session.getUserId(), enabled)
        val mode = if (enabled)
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
        else
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(mode)
        // Recreate so the current activity re-inflates with the new theme immediately
        recreate()
        Log.d(TAG, "Dark mode: $enabled")
    }

    private fun exportCsv() {
        val userId   = session.getUserId()
        val expenses = db.getExpenses(userId, "2000-01-01", "2099-12-31")
        if (expenses.isEmpty()) { Toast.makeText(this, "No expenses to export", Toast.LENGTH_SHORT).show(); return }
        try {
            val ts   = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "BudgetBuddy_$ts.csv")
            FileWriter(file).use { w ->
                w.append("ID,Date,Category,Description,Amount,HasPhoto\n")
                for (e in expenses) {
                    w.append("${e.id},${e.date},\"${e.categoryName}\",\"${e.description}\",${e.amount},${e.photoBlob != null}\n")
                }
            }
            Log.d(TAG, "CSV exported: ${file.absolutePath}")
            Toast.makeText(this, "Exported: ${file.name}", Toast.LENGTH_LONG).show()
            val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Share CSV"))
        } catch (e: Exception) {
            Log.e(TAG, "CSV export failed: ${e.message}")
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmResetData() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("⚠️  Reset All Data")
            .setMessage(
                "This will permanently delete ALL your:\n\n" +
                        "• Expenses\n• Budget goals\n• Earned badges\n\n" +
                        "Your account, username and categories will be kept.\n\n" +
                        "This cannot be undone."
            )
            .setPositiveButton("Reset Everything") { _, _ ->
                if (db.resetAllData(session.getUserId())) {
                    Log.d(TAG, "Data reset for user ${session.getUserId()}")
                    Toast.makeText(this, "✅ All data cleared. Fresh start!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Reset failed. Please try again.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun logout() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout") { _, _ ->
                session.clearSession()
                Log.d(TAG, "User logged out")
                startActivity(Intent(this, AuthActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}