package com.budgetbuddy.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

/**
 * AchievementsActivity — shows streak, budget score and all badge cards.
 * Earned badges are full-color with a gold border; locked badges are greyed out.
 */
class AchievementsActivity : AppCompatActivity() {

    private lateinit var db: DatabaseHelper
    private lateinit var session: SessionManager

    private lateinit var tvStreak: TextView
    private lateinit var tvScore: TextView
    private lateinit var pbScore: ProgressBar
    private lateinit var llBadges: LinearLayout

    companion object {
        private const val TAG = "AchievementsActivity"

        val ALL_BADGES = listOf(
            Badge("FIRST_EXPENSE", "First Expense 🧾",      "Log your very first expense"),
            Badge("STREAK_7",      "7-Day Streak 🔥",        "Log an expense every day for 7 days"),
            Badge("STREAK_30",     "30-Day Streak 🔥🔥",     "Log an expense every day for 30 days"),
            Badge("STREAK_100",    "100-Day Streak 💯",      "Log an expense every day for 100 days"),
            Badge("BUDGET_HERO",   "Budget Hero 🦸",         "Stay within your monthly budget"),
            Badge("FIRST_SAVE",    "Goal Setter 💰",         "Set your first budget goal")
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db      = DatabaseHelper(this)
        session = SessionManager(this)
        setContentView(R.layout.activity_achievements)

        tvStreak  = findViewById(R.id.tvAchievementStreak)
        tvScore   = findViewById(R.id.tvAchievementScore)
        pbScore   = findViewById(R.id.pbScore)
        llBadges  = findViewById(R.id.llBadges)

        loadAchievements()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Achievements & Badges"
    }

    @SuppressLint("SetTextI18n")
    private fun loadAchievements() {
        val userId    = session.getUserId()
        val streak    = db.calculateStreak(userId)
        val yearMonth = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.getDefault()).format(java.util.Date())
        val score     = db.calculateBudgetScore(userId, yearMonth)
        val tier      = db.getBadgeTier(score)

        tvStreak.text  = "🔥 Current Streak: $streak days"
        tvScore.text   = "🏅 Budget Score: $score — $tier"
        pbScore.progress = score

        val earnedIds = db.getEarnedBadgeIds(userId)
        Log.d(TAG, "Earned badges: $earnedIds, score: $score")

        llBadges.removeAllViews()

        // Section header
        addSectionHeader("YOUR BADGES  (${earnedIds.size}/${ALL_BADGES.size} earned)")

        for (badge in ALL_BADGES) {
            val earned = badge.id in earnedIds
            llBadges.addView(buildBadgeCard(badge, earned))
        }
    }

    private fun addSectionHeader(text: String) {
        val tv = TextView(this).apply {
            this.text = text
            textSize = 11f
            setTextColor(0xFF546E7A.toInt())
            letterSpacing = 0.1f
            setPadding(dp(4), dp(8), 0, dp(8))
        }
        llBadges.addView(tv)
    }

    private fun buildBadgeCard(badge: Badge, earned: Boolean): android.view.View {
        val card = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(dp(16), dp(16), dp(16), dp(16))

            // Earned: white card with gold left border feel; Locked: gray card
            setBackgroundColor(if (earned) 0xFFFFFFFF.toInt() else 0xFFF5F5F5.toInt())

            val lp = android.widget.LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 0, dp(8))
            layoutParams = lp
            elevation = if (earned) dp(3).toFloat() else 0f
        }

        // Left color strip (gold if earned, gray if not)
        val strip = android.view.View(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(dp(4), android.widget.LinearLayout.LayoutParams.MATCH_PARENT).also {
                it.setMargins(0, 0, dp(16), 0)
            }
            setBackgroundColor(if (earned) 0xFFFFD600.toInt() else 0xFFBDBDBD.toInt())
        }

        // Emoji / lock icon
        val tvEmoji = TextView(this).apply {
            text = if (earned) "✅" else "🔒"
            textSize = 26f
            setPadding(0, 0, dp(14), 0)
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        // Text column
        val col = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            layoutParams = android.widget.LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
        }
        val tvName = TextView(this).apply {
            text = badge.name
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(if (earned) 0xFF0D2137.toInt() else 0xFF9E9E9E.toInt())
        }
        val tvDesc = TextView(this).apply {
            text = badge.description
            textSize = 12f
            setTextColor(if (earned) 0xFF546E7A.toInt() else 0xFFBDBDBD.toInt())
            setPadding(0, dp(2), 0, 0)
        }
        col.addView(tvName)
        col.addView(tvDesc)

        // Earned badge: show "EARNED" chip
        if (earned) {
            val chip = TextView(this).apply {
                text = "EARNED"
                textSize = 10f
                setTextColor(0xFF0D2137.toInt())
                setBackgroundColor(0xFFFFD600.toInt())
                setPadding(dp(6), dp(2), dp(6), dp(2))
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            card.addView(strip)
            card.addView(tvEmoji)
            card.addView(col)
            card.addView(chip)
        } else {
            card.addView(strip)
            card.addView(tvEmoji)
            card.addView(col)
        }

        return card
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density + 0.5f).toInt()

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}