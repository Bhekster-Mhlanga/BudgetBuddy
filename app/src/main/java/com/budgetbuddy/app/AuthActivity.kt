package com.budgetbuddy.app

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

/**
 * AuthActivity handles both Login and Registration on one screen.
 * It shows login fields by default; tapping "Register" switches the form.
 */
class AuthActivity : AppCompatActivity() {

    private lateinit var db: DatabaseHelper
    private lateinit var session: SessionManager

    // UI references — set after setContentView
    private lateinit var tvTitle: TextView
    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var btnAction: Button
    private lateinit var tvSwitch: TextView
    private lateinit var tvConfirmLabel: TextView

    private var isLoginMode = true

    companion object {
        private const val TAG = "AuthActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        db = DatabaseHelper(this)
        session = SessionManager(this)

        // If already logged in skip this screen
        if (session.isLoggedIn()) {
            goToDashboard()
            return
        }

        setContentView(R.layout.activity_auth)

        // Bind views
        tvTitle = findViewById(R.id.tvAuthTitle)
        etUsername = findViewById(R.id.etUsername)
        etPassword = findViewById(R.id.etPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)
        btnAction = findViewById(R.id.btnAuthAction)
        tvSwitch = findViewById(R.id.tvSwitchMode)
        tvConfirmLabel = findViewById(R.id.tvConfirmLabel)

        // Start in login mode
        setLoginMode()

        btnAction.setOnClickListener { handleAction() }
        tvSwitch.setOnClickListener { toggleMode() }
    }

    // -------------------------------------------------------------------------
    // Mode switching
    // -------------------------------------------------------------------------

    @SuppressLint("SetTextI18n")
    private fun setLoginMode() {
        isLoginMode = true
        tvTitle.text = "Welcome Back"
        btnAction.text = "Login"
        tvSwitch.text = "Don't have an account? Register"
        etConfirmPassword.visibility = View.GONE
        tvConfirmLabel.visibility = View.GONE
    }

    @SuppressLint("SetTextI18n")
    private fun setRegisterMode() {
        isLoginMode = false
        tvTitle.text = "Create Account"
        btnAction.text = "Register"
        tvSwitch.text = "Already have an account? Login"
        etConfirmPassword.visibility = View.VISIBLE
        tvConfirmLabel.visibility = View.VISIBLE
    }

    private fun toggleMode() {
        clearFields()
        if (isLoginMode) setRegisterMode() else setLoginMode()
    }

    // -------------------------------------------------------------------------
    // Handle login or register
    // -------------------------------------------------------------------------

    private fun handleAction() {
        val username = etUsername.text.toString().trim()
        val password = etPassword.text.toString()

        if (username.isEmpty()) {
            etUsername.error = "Username is required"
            return
        }

        if (password.length < 8) {
            etPassword.error = "Password must be at least 8 characters"
            return
        }

        if (isLoginMode) {
            performLogin(username, password)
        } else {
            val confirm = etConfirmPassword.text.toString()
            if (password != confirm) {
                etConfirmPassword.error = "Passwords do not match"
                return
            }
            performRegister(username, password)
        }
    }

    private fun performLogin(username: String, password: String) {
        val user = db.loginUser(username, password)
        if (user != null) {
            Log.d(TAG, "Login successful for user: ${user.username}")
            session.saveSession(user.id, user.username)
            goToDashboard()
        } else {
            Log.w(TAG, "Login failed for username: $username")
            Toast.makeText(this, "Invalid username or password", Toast.LENGTH_SHORT).show()
        }
    }

    private fun performRegister(username: String, password: String) {
        val id = db.registerUser(username, password)
        if (id > 0) {
            Log.d(TAG, "Registered new user: $username with id $id")
            Toast.makeText(this, "Account created! Please login.", Toast.LENGTH_SHORT).show()
            setLoginMode()
            etUsername.setText(username)
        } else {
            Log.w(TAG, "Registration failed for username: $username")
            Toast.makeText(this, "Username already taken. Try another.", Toast.LENGTH_SHORT).show()
        }
    }

    // -------------------------------------------------------------------------
    // Navigation
    // -------------------------------------------------------------------------

    private fun goToDashboard() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun clearFields() {
        etUsername.text.clear()
        etPassword.text.clear()
        etConfirmPassword.text.clear()
        etUsername.error = null
        etPassword.error = null
        etConfirmPassword.error = null
    }
}