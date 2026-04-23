package com.budgetbuddy.app

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*

/**
 * ExpenseListActivity — displays expenses in a disciplined table layout.
 * Each row shows: Date | Description | Category | Amount
 * Long-press = edit / delete. Tap = view receipt photo if attached.
 */
class ExpenseListActivity : AppCompatActivity() {

    private lateinit var db: DatabaseHelper
    private lateinit var session: SessionManager

    private lateinit var spinnerPeriod: Spinner
    private lateinit var btnCustomDate: Button
    private lateinit var tvDateRange: TextView
    private lateinit var listView: ListView
    private lateinit var tvTotal: TextView
    private lateinit var tvRowCount: TextView
    private lateinit var btnCategoryTotals: Button

    private var expenses = listOf<Expense>()
    private var startDate = ""
    private var endDate = ""

    companion object {
        private const val TAG = "ExpenseListActivity"
        private const val PERIOD_TODAY = 0
        private const val PERIOD_WEEK = 1
        private const val PERIOD_MONTH = 2
        private const val PERIOD_CUSTOM = 3
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = DatabaseHelper(this)
        session = SessionManager(this)
        setContentView(R.layout.activity_expense_list)

        bindViews()
        setupPeriodSpinner()
        setupListViewClickListeners()

        btnCategoryTotals.setOnClickListener { showCategoryTotals() }
        btnCustomDate.setOnClickListener { showCustomDatePicker() }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "My Expenses"
    }

    override fun onResume() {
        super.onResume()
        loadExpenses()
    }

    // -------------------------------------------------------------------------
    // Bind views
    // -------------------------------------------------------------------------

    private fun bindViews() {
        spinnerPeriod   = findViewById(R.id.spinnerPeriod)
        btnCustomDate   = findViewById(R.id.btnCustomDate)
        tvDateRange     = findViewById(R.id.tvDateRange)
        listView        = findViewById(R.id.lvExpenses)
        tvTotal         = findViewById(R.id.tvExpenseTotal)
        tvRowCount      = findViewById(R.id.tvRowCount)
        btnCategoryTotals = findViewById(R.id.btnCategoryTotals)
        btnCustomDate.visibility = View.GONE
    }

    // -------------------------------------------------------------------------
    // Period spinner
    // -------------------------------------------------------------------------

    private fun setupPeriodSpinner() {
        val periods = listOf("Today", "This Week", "This Month", "Custom Range")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, periods)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerPeriod.adapter = adapter
        spinnerPeriod.setSelection(PERIOD_MONTH)

        spinnerPeriod.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                btnCustomDate.visibility = if (pos == PERIOD_CUSTOM) View.VISIBLE else View.GONE
                if (pos != PERIOD_CUSTOM) {
                    setDateRange(pos)
                    loadExpenses()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setDateRange(period: Int) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        endDate = sdf.format(cal.time)
        when (period) {
            PERIOD_TODAY -> startDate = endDate
            PERIOD_WEEK  -> { cal.add(Calendar.DAY_OF_YEAR, -6); startDate = sdf.format(cal.time) }
            PERIOD_MONTH -> { cal.set(Calendar.DAY_OF_MONTH, 1); startDate = sdf.format(cal.time) }
        }
        tvDateRange.text = "$startDate  →  $endDate"
        Log.d(TAG, "Date range: $startDate → $endDate")
    }

    @SuppressLint("SetTextI18n")
    private fun showCustomDatePicker() {
        val cal = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            startDate = "%04d-%02d-%02d".format(y, m + 1, d)
            DatePickerDialog(this, { _, y2, m2, d2 ->
                endDate = "%04d-%02d-%02d".format(y2, m2 + 1, d2)
                tvDateRange.text = "$startDate  →  $endDate"
                loadExpenses()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    // -------------------------------------------------------------------------
    // Load and display expenses
    // -------------------------------------------------------------------------

    @SuppressLint("SetTextI18n")
    private fun loadExpenses() {
        if (startDate.isEmpty() || endDate.isEmpty()) setDateRange(PERIOD_MONTH)

        val userId = session.getUserId()
        expenses = db.getExpenses(userId, startDate, endDate)
        Log.d(TAG, "Loaded ${expenses.size} expenses")

        // Sort: newest first
        val sorted = expenses.sortedByDescending { it.date }

        listView.adapter = ExpenseTableAdapter(this, sorted)

        val total = expenses.sumOf { it.amount }
        tvTotal.text = "Total: R${"%.2f".format(total)}"
        tvRowCount.text = "${expenses.size} ${if (expenses.size == 1) "entry" else "entries"}"
    }

    // -------------------------------------------------------------------------
    // List click listeners
    // -------------------------------------------------------------------------

    private fun setupListViewClickListeners() {
        // Tap = view receipt photo if present
        listView.setOnItemClickListener { _, _, position, _ ->
            val adapter = listView.adapter as? ExpenseTableAdapter ?: return@setOnItemClickListener
            val expense = adapter.getItem(position) ?: return@setOnItemClickListener
            val blob = expense.photoBlob
            if (blob != null) {
                val bitmap = BitmapFactory.decodeByteArray(blob, 0, blob.size)
                val imageView = ImageView(this).apply {
                    setImageBitmap(bitmap)
                    adjustViewBounds = true
                    setPadding(8, 8, 8, 8)
                }
                AlertDialog.Builder(this)
                    .setTitle("Receipt — ${expense.description}")
                    .setView(imageView)
                    .setPositiveButton("Close", null)
                    .show()
            }
        }

        // Long press = edit or delete
        listView.setOnItemLongClickListener { _, _, position, _ ->
            val adapter = listView.adapter as? ExpenseTableAdapter ?: return@setOnItemLongClickListener true
            val expense = adapter.getItem(position) ?: return@setOnItemLongClickListener true
            AlertDialog.Builder(this)
                .setTitle(expense.description)
                .setItems(arrayOf("✏️  Edit", "🗑️  Delete")) { _, which ->
                    when (which) {
                        0 -> {
                            val intent = Intent(this, AddExpenseActivity::class.java)
                            intent.putExtra(AddExpenseActivity.EXTRA_EXPENSE_ID, expense.id)
                            startActivity(intent)
                        }
                        1 -> confirmDelete(expense)
                    }
                }.show()
            true
        }
    }

    private fun confirmDelete(expense: Expense) {
        AlertDialog.Builder(this)
            .setTitle("Delete Expense")
            .setMessage("Permanently delete \"${expense.description}\"?")
            .setPositiveButton("Delete") { _, _ ->
                if (db.deleteExpense(expense.id)) {
                    Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show()
                    loadExpenses()
                } else {
                    Toast.makeText(this, "Failed to delete", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // -------------------------------------------------------------------------
    // Category totals dialog
    // -------------------------------------------------------------------------

    private fun showCategoryTotals() {
        val userId = session.getUserId()
        val totals = db.getCategoryTotals(userId, startDate, endDate)
        if (totals.isEmpty()) {
            Toast.makeText(this, "No expenses in this period", Toast.LENGTH_SHORT).show()
            return
        }
        val message = totals.entries
            .sortedByDescending { it.value }
            .joinToString("\n") { (cat, total) ->
                "%-18s  R%,.2f".format(cat, total)
            }
        AlertDialog.Builder(this)
            .setTitle("Category Totals")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}

// =============================================================================
// ExpenseTableAdapter — renders each expense as a disciplined table row
// =============================================================================

class ExpenseTableAdapter(
    context: Context,
    private val items: List<Expense>
) : ArrayAdapter<Expense>(context, 0, items) {

    // Alternating row colors — subtle stripe for readability
    private val colourEven = 0xFFFFFFFF.toInt()
    private val colourOdd  = 0xFFF5F8FC.toInt()

    // Category badge colors — cycles through a palette
    private val badgeColours = listOf(
        0xFF1565C0.toInt(), // deep blue
        0xFF2E7D32.toInt(), // forest green
        0xFF6A1B9A.toInt(), // purple
        0xFFE65100.toInt(), // deep orange
        0xFF00695C.toInt(), // teal
        0xFFC62828.toInt(), // red
        0xFF4527A0.toInt()  // indigo
    )

    @SuppressLint("SetTextI18n")
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val expense = items[position]

        // Build row programmatically for tight column control
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(12, 0, 12, 0)
            minimumHeight = dpToPx(60)
            setBackgroundColor(if (position % 2 == 0) colourEven else colourOdd)
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        // ── DATE column (55dp) ──
        val tvDate = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(55), ViewGroup.LayoutParams.WRAP_CONTENT)
            text = expense.date.takeLast(5) // MM-dd e.g. "04-22"
            textSize = 12f
            setTextColor(0xFF546E7A.toInt())
            typeface = Typeface.MONOSPACE
        }

        // ── DESCRIPTION column (flex) ──
        val descLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val tvDesc = TextView(context).apply {
            text = expense.description
            textSize = 13f
            setTextColor(0xFF1A2A3A.toInt())
            setTypeface(null, Typeface.BOLD)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        descLayout.addView(tvDesc)

        // ── UPLOADS column (80dp) — thumbnail if photo exists, else dash ──
        val photoSize = dpToPx(44)
        val tvPhotoCol: android.view.View = if (expense.photoBlob != null) {
            val blob = expense.photoBlob
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(blob, 0, blob!!.size)
            android.widget.ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(80), dpToPx(52)).apply {
                    setMargins(dpToPx(2), dpToPx(4), dpToPx(2), dpToPx(4))
                }
                setImageBitmap(bitmap)
                scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                clipToOutline = true
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dpToPx(6).toFloat()
                }
            }
        } else {
            TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(80), ViewGroup.LayoutParams.WRAP_CONTENT)
                text = "—"
                textSize = 13f
                setTextColor(0xFFBDBDBD.toInt())
                gravity = android.view.Gravity.CENTER
            }
        }

        // ── CATEGORY column (100dp) — full name ──
        val catColour = badgeColours[expense.categoryId % badgeColours.size]
        val tvCat = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(100), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(4, 0, 4, 0)
            }
            text = expense.categoryName
            textSize = 10f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(catColour)
            setPadding(dpToPx(6), dpToPx(4), dpToPx(6), dpToPx(4))
            gravity = android.view.Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        // ── AMOUNT column (75dp) right-aligned ──
        val tvAmount = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(75), ViewGroup.LayoutParams.WRAP_CONTENT)
            text = "R%,.2f".format(expense.amount)
            textSize = 13f
            setTextColor(0xFF0D2137.toInt())
            setTypeface(null, Typeface.BOLD)
            gravity = android.view.Gravity.END
        }

        row.addView(tvDate)
        row.addView(descLayout)
        row.addView(tvPhotoCol)   // 📷 — between description and category
        row.addView(tvCat)
        row.addView(tvAmount)
        return row
    }

    private fun dpToPx(dp: Int): Int =
        (dp * context.resources.displayMetrics.density + 0.5f).toInt()
}