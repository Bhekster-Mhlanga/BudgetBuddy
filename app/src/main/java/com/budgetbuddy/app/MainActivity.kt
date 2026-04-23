package com.budgetbuddy.app

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*

/**
 * MainActivity is the Dashboard — the central hub of BudgetBuddy.
 * Shows: monthly budget progress, Budget Score badge, streak counter,
 * per-category progress bars, and overspend highlights.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var db: DatabaseHelper
    private lateinit var session: SessionManager

    private lateinit var tvWelcome: TextView
    private lateinit var tvMonthlyBudget: TextView
    private lateinit var tvMonthlySpent: TextView
    private lateinit var pbOverallBudget: ProgressBar
    private lateinit var tvBudgetScore: TextView
    private lateinit var tvBadgeTier: TextView
    private lateinit var tvStreak: TextView
    private lateinit var llCategoryProgress: LinearLayout
    private lateinit var btnQuickAdd: Button
    private lateinit var btnExpenses: Button
    private lateinit var btnGraph: Button
    private lateinit var btnAchievements: Button
    private lateinit var btnBudgetGoals: Button
    private lateinit var btnSettings: TextView

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = DatabaseHelper(this)
        session = SessionManager(this)

        // Guard: if not logged in send to auth
        if (!session.isLoggedIn()) {
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
            return
        }

        // Apply dark mode preference saved by the user
        val darkMode = db.isDarkMode(session.getUserId())
        val mode = if (darkMode)
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
        else
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(mode)

        setContentView(R.layout.activity_main)
        bindViews()
        setupNavigation()
    }

    override fun onResume() {
        super.onResume()
        // Refresh dashboard data every time we return to this screen
        loadDashboard()
    }

    // -------------------------------------------------------------------------
    // Bind views
    // -------------------------------------------------------------------------

    private fun bindViews() {
        tvWelcome = findViewById(R.id.tvWelcome)
        tvMonthlyBudget = findViewById(R.id.tvMonthlyBudget)
        tvMonthlySpent = findViewById(R.id.tvMonthlySpent)
        pbOverallBudget = findViewById(R.id.pbOverallBudget)
        tvBudgetScore = findViewById(R.id.tvBudgetScore)
        tvBadgeTier = findViewById(R.id.tvBadgeTier)
        tvStreak = findViewById(R.id.tvStreak)
        llCategoryProgress = findViewById(R.id.llCategoryProgress)
        btnQuickAdd = findViewById(R.id.btnQuickAdd)
        btnExpenses = findViewById(R.id.btnExpenses)
        btnGraph = findViewById(R.id.btnGraph)
        btnAchievements = findViewById(R.id.btnAchievements)
        btnBudgetGoals = findViewById(R.id.btnBudgetGoals)
        btnSettings = findViewById(R.id.btnSettings)
    }

    // -------------------------------------------------------------------------
    // Load and display dashboard data
    // -------------------------------------------------------------------------

    @SuppressLint("SetTextI18n")
    private fun loadDashboard() {
        val userId = session.getUserId()
        val username = session.getUsername()

        // Use Kotlin property assignment — avoids setText lint warnings
        tvWelcome.text = "Hello, $username 👋"

        // Current month string e.g. "2025-04"
        val sdf = SimpleDateFormat("yyyy-MM", Locale.getDefault())
        val yearMonth = sdf.format(Date())

        // Monthly totals
        val goals = db.getBudgetGoals(userId)
        val totalGoal = goals[-1] ?: 0.0
        val spent = db.getMonthlyTotal(userId, yearMonth)

        tvMonthlyBudget.text = "Budget: R${"%.2f".format(totalGoal)}"
        tvMonthlySpent.text = "Spent: R${"%.2f".format(spent)}"

        if (totalGoal > 0) {
            val progress = ((spent / totalGoal) * 100).toInt().coerceIn(0, 100)
            pbOverallBudget.progress = progress
            Log.d(TAG, "Overall budget progress: $progress%")
        } else {
            pbOverallBudget.progress = 0
        }

        // Budget Score
        val score = db.calculateBudgetScore(userId, yearMonth)
        val tier = db.getBadgeTier(score)
        tvBudgetScore.text = "Budget Score: $score"
        tvBadgeTier.text = "🏅 $tier"

        // Streak
        val streak = db.calculateStreak(userId)
        tvStreak.text = "🔥 $streak day streak"

        // Award badges based on current state
        checkAndAwardBadges(userId, streak, spent, totalGoal)

        // Per-category progress
        loadCategoryProgress(userId, yearMonth, goals)
    }

    @SuppressLint("SetTextI18n")
    private fun loadCategoryProgress(userId: Int, yearMonth: String, goals: Map<Int, Double>) {
        llCategoryProgress.removeAllViews()

        val categories = db.getCategories(userId)
        val startDate = "$yearMonth-01"
        val endDate = "$yearMonth-31"
        val catTotals = db.getCategoryTotals(userId, startDate, endDate)

        for (cat in categories) {
            val limit = goals[cat.id] ?: 0.0
            val catSpent = catTotals[cat.name] ?: 0.0

            // Container row
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 8, 0, 8)
            }

            // Category name + spent — red if overspent
            val tvCat = TextView(this).apply {
                val overspend = if (limit > 0 && catSpent > limit)
                    " ⚠️ OVER by R${"%.2f".format(catSpent - limit)}" else ""
                text = "${cat.name}: R${"%.2f".format(catSpent)} / R${"%.2f".format(limit)}$overspend"
                setTextColor(
                    if (limit > 0 && catSpent > limit) 0xFFD32F2F.toInt() else 0xFF212121.toInt()
                )
            }

            val pb = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = if (limit > 0) ((catSpent / limit) * 100).toInt().coerceIn(0, 100) else 0
            }

            row.addView(tvCat)
            row.addView(pb)
            llCategoryProgress.addView(row)
        }
    }

    // -------------------------------------------------------------------------
    // Badge awarding logic
    // -------------------------------------------------------------------------

    private fun checkAndAwardBadges(userId: Int, streak: Int, spent: Double, totalGoal: Double) {
        val expenses = db.getExpenses(
            userId,
            "2000-01-01",
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        )

        if (expenses.isNotEmpty()) db.awardBadge(userId, "FIRST_EXPENSE")

        if (streak >= 7) db.awardBadge(userId, "STREAK_7")
        if (streak >= 30) db.awardBadge(userId, "STREAK_30")
        if (streak >= 100) db.awardBadge(userId, "STREAK_100")

        val goals = db.getBudgetGoals(userId)
        if (totalGoal > 0 && spent <= totalGoal) db.awardBadge(userId, "BUDGET_HERO")
        if (goals.isNotEmpty()) db.awardBadge(userId, "FIRST_SAVE")

        Log.d(TAG, "Badge check complete for user $userId")
    }

    // -------------------------------------------------------------------------
    // Navigation setup
    // -------------------------------------------------------------------------

    private fun setupNavigation() {
        btnQuickAdd.setOnClickListener {
            startActivity(Intent(this, AddExpenseActivity::class.java))
        }
        btnExpenses.setOnClickListener {
            startActivity(Intent(this, ExpenseListActivity::class.java))
        }
        btnGraph.setOnClickListener {
            startActivity(Intent(this, SpendingGraphActivity::class.java))
        }
        btnAchievements.setOnClickListener {
            startActivity(Intent(this, AchievementsActivity::class.java))
        }
        btnBudgetGoals.setOnClickListener {
            startActivity(Intent(this, BudgetGoalsActivity::class.java))
        }
        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }
}