package com.budgetbuddy.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

/**
 * BudgetGoalsActivity — set overall monthly budget AND per-category min/max goal bands.
 * Min = minimum expected spending (so you don't underspend on essentials).
 * Max = maximum allowed spending (overspend alert triggers above this).
 */
class BudgetGoalsActivity : AppCompatActivity() {

    private lateinit var db: DatabaseHelper
    private lateinit var session: SessionManager

    private lateinit var etTotalBudget: EditText
    private lateinit var btnSaveTotalBudget: Button
    private lateinit var llCategoryGoals: LinearLayout

    private var userId = -1
    private var categories = listOf<Category>()

    companion object {
        private const val TAG = "BudgetGoalsActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = DatabaseHelper(this)
        session = SessionManager(this)
        userId = session.getUserId()
        setContentView(R.layout.activity_budget_goals)

        etTotalBudget     = findViewById(R.id.etTotalBudget)
        btnSaveTotalBudget = findViewById(R.id.btnSaveTotalBudget)
        llCategoryGoals   = findViewById(R.id.llCategoryGoals)

        loadData()
        btnSaveTotalBudget.setOnClickListener { saveTotalBudget() }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Budget Goals"
    }

    @SuppressLint("SetTextI18n")
    private fun loadData() {
        categories = db.getCategories(userId)
        val goals  = db.getBudgetGoals(userId)
        val total  = goals[-1]
        if (total != null && total > 0) etTotalBudget.setText("%.2f".format(total))
        Log.d(TAG, "Loaded ${categories.size} categories")
        buildCategoryRows()
    }

    @SuppressLint("SetTextI18n")
    private fun buildCategoryRows() {
        llCategoryGoals.removeAllViews()
        val bands = db.getAllGoalBands(userId)

        for (cat in categories) {
            val band = bands[cat.id]

            // Section header per category
            val tvHeader = TextView(this).apply {
                text = cat.name
                textSize = 15f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(0xFF0D2137.toInt())
                setPadding(0, 16, 0, 4)
            }

            // Min row
            val rowMin = buildInputRow("Min Goal (R)", band?.min)
            val etMin  = rowMin.getChildAt(1) as EditText

            // Max row
            val rowMax = buildInputRow("Max / Limit (R)", band?.max)
            val etMax  = rowMax.getChildAt(1) as EditText

            // Save button — gradient style matching Quick Add button
            val btnSave = Button(this).apply {
                text = "⊕  SAVE  ${cat.name.uppercase()}"
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
                background = androidx.core.content.ContextCompat.getDrawable(
                    this@BudgetGoalsActivity, R.drawable.gradient_button
                )
                stateListAnimator = null
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dpToPx(54)
                )
                lp.setMargins(0, dpToPx(8), 0, dpToPx(16))
                layoutParams = lp
                setOnClickListener { saveCategoryGoal(cat.id, cat.name, etMin, etMax) }
            }

            // Divider
            val divider = android.view.View(this).apply {
                setBackgroundColor(0xFFE0E0E0.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1
                ).also { it.setMargins(0, 0, 0, 8) }
            }

            llCategoryGoals.addView(tvHeader)
            llCategoryGoals.addView(rowMin)
            llCategoryGoals.addView(rowMax)
            llCategoryGoals.addView(btnSave)
            llCategoryGoals.addView(divider)
        }
    }

    private fun buildInputRow(label: String, prefill: Double?): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 4, 0, 4)
        }
        val tv = TextView(this).apply {
            text = label
            textSize = 13f
            setTextColor(0xFF546E7A.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val et = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "0.00"
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            if (prefill != null && prefill > 0) setText("%.2f".format(prefill))
        }
        row.addView(tv)
        row.addView(et)
        return row
    }

    private fun saveTotalBudget() {
        val limit = etTotalBudget.text.toString().trim().toDoubleOrNull()
        if (limit == null || limit <= 0) { etTotalBudget.error = "Enter a valid amount"; return }
        if (db.setBudgetGoal(userId, -1, limit)) {
            Toast.makeText(this, "Total budget saved: R%.2f".format(limit), Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Total budget set: $limit")
        } else Toast.makeText(this, "Failed to save", Toast.LENGTH_SHORT).show()
    }

    private fun saveCategoryGoal(catId: Int, catName: String, etMin: EditText, etMax: EditText) {
        val min = etMin.text.toString().trim().toDoubleOrNull() ?: 0.0
        val max = etMax.text.toString().trim().toDoubleOrNull() ?: 0.0
        if (max > 0 && min > max) {
            etMin.error = "Min cannot exceed max"
            return
        }
        val limit = if (max > 0) max else min
        if (db.setBudgetGoalFull(userId, catId, limit, min, max)) {
            Toast.makeText(this, "$catName goals saved!", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "$catName: min=$min max=$max")
        } else Toast.makeText(this, "Failed to save $catName", Toast.LENGTH_SHORT).show()
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density + 0.5f).toInt()

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}