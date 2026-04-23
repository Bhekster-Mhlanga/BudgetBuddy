package com.budgetbuddy.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import java.text.SimpleDateFormat
import java.util.*

/**
 * SpendingGraphActivity — Pro bar chart matching the design mockup:
 *  • Budget summary card (total budget, period, spent / remaining)
 *  • Daily / By Category radio toggle
 *  • Goal band status banner
 *  • BarChartView with Y-axis price labels + dashed reference lines
 *  • Detail list rows (category or daily)
 *  • Dashed-line legend
 *  • Total spent footer
 */
class SpendingGraphActivity : AppCompatActivity() {

    private lateinit var db: DatabaseHelper
    private lateinit var session: SessionManager

    // Budget summary card
    private lateinit var tvTotalBudget: TextView
    private lateinit var spinnerPeriod: Spinner
    private lateinit var tvPeriodLabel: TextView
    private lateinit var tvTrendLabel: TextView
    private lateinit var tvTrend: TextView
    private lateinit var tvTrendSub: TextView

    // Toggle
    private lateinit var rgViewMode: RadioGroup
    private lateinit var rbDaily: RadioButton
    private lateinit var rbCategory: RadioButton

    // Goal banner
    private lateinit var layoutGoalStatus: LinearLayout
    private lateinit var tvGoalStatus: TextView

    // Chart
    private lateinit var tvChartTitle: TextView
    private lateinit var tvChartSubtitle: TextView
    private lateinit var barChart: BarChartView

    // Lists
    private lateinit var llLegend: LinearLayout
    private lateinit var llLineLegend: LinearLayout
    private lateinit var tvTotalSpent: TextView

    private var currentStartDate = ""
    private var currentEndDate   = ""
    private var userId = -1

    companion object { private const val TAG = "SpendingGraphActivity" }

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db      = DatabaseHelper(this)
        session = SessionManager(this)
        userId  = session.getUserId()
        setContentView(R.layout.activity_spending_graph)

        tvTotalBudget    = findViewById(R.id.tvTotalBudget)
        spinnerPeriod    = findViewById(R.id.spinnerGraphPeriod)
        tvPeriodLabel    = findViewById(R.id.tvPeriodLabel)
        tvTrendLabel     = findViewById(R.id.tvTrendLabel)
        tvTrend          = findViewById(R.id.tvTrend)
        tvTrendSub       = findViewById(R.id.tvTrendSub)
        rgViewMode       = findViewById(R.id.rgViewMode)
        rbDaily          = findViewById(R.id.rbDaily)
        rbCategory       = findViewById(R.id.rbCategory)
        layoutGoalStatus = findViewById(R.id.layoutGoalStatus)
        tvGoalStatus     = findViewById(R.id.tvGoalStatus)
        tvChartTitle     = findViewById(R.id.tvChartTitle)
        tvChartSubtitle  = findViewById(R.id.tvChartSubtitle)
        barChart         = findViewById(R.id.barChartView)
        llLegend         = findViewById(R.id.llLegend)
        llLineLegend     = findViewById(R.id.llLineLegend)
        tvTotalSpent     = findViewById(R.id.tvTotalSpent)

        setupSpinner()
        rgViewMode.setOnCheckedChangeListener { _, _ -> refreshGraph() }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Spending Graph"
    }

    override fun onResume() {
        super.onResume()
        val budgetTotal = db.getBudgetGoals(userId)[-1] ?: 0.0
        tvTotalBudget.text = "R${"%.2f".format(budgetTotal)}"
        if (currentStartDate.isEmpty()) loadGraph(7) else refreshGraph()
    }

    private fun setupSpinner() {
        val options = listOf("Last 7 Days", "Last 14 Days", "This Month")
        spinnerPeriod.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, options)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spinnerPeriod.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                when (pos) { 0 -> loadGraph(7); 1 -> loadGraph(14); 2 -> loadMonthGraph() }
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    @SuppressLint("SetTextI18n")
    private fun loadGraph(days: Int) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        currentEndDate = sdf.format(cal.time)
        cal.add(Calendar.DAY_OF_YEAR, -(days - 1))
        currentStartDate = sdf.format(cal.time)
        tvPeriodLabel.text = "$currentStartDate  →  $currentEndDate"
        refreshGraph()
    }

    @SuppressLint("SetTextI18n")
    private fun loadMonthGraph() {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        currentEndDate = sdf.format(cal.time)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        currentStartDate = sdf.format(cal.time)
        tvPeriodLabel.text = "$currentStartDate  →  $currentEndDate"
        refreshGraph()
    }

    private fun refreshGraph() {
        if (currentStartDate.isEmpty()) return
        if (rbCategory.isChecked) fetchAndDisplayByCategory(currentStartDate, currentEndDate)
        else fetchAndDisplayDaily(currentStartDate, currentEndDate)
    }

    // ── DAILY ─────────────────────────────────────────────────────────────────

    @SuppressLint("SetTextI18n")
    private fun fetchAndDisplayDaily(startDate: String, endDate: String) {
        val dailyTotals  = db.getDailyTotals(userId, startDate, endDate)
        val band         = db.getGoalBand(userId, -1)
        val minGoal      = band?.min ?: 0.0
        val maxGoal      = band?.max ?: (db.getBudgetGoals(userId)[-1] ?: 0.0)
        val currentSum   = dailyTotals.values.sum()

        tvChartTitle.text    = "Spending by Day"
        tvChartSubtitle.text = "Track your spending each day over time"

        // Right side of budget card → Total Spent
        tvTrendLabel.text = "Total Spent"
        tvTrend.text      = "R${"%.2f".format(currentSum)}"
        tvTrend.setTextColor(0xFFE53935.toInt())

        // Trend % vs previous period
        val sdf      = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val calStart = Calendar.getInstance().also { c -> sdf.parse(startDate)?.let { c.time = it } }
        val calEnd   = Calendar.getInstance().also { c -> sdf.parse(endDate)?.let { c.time = it } }
        val dayCount = ((calEnd.timeInMillis - calStart.timeInMillis) / 86_400_000L).toInt() + 1
        calStart.add(Calendar.DAY_OF_YEAR, -dayCount)
        calEnd.add(Calendar.DAY_OF_YEAR, -dayCount)
        val prevSum = db.getDailyTotals(userId, sdf.format(calStart.time), sdf.format(calEnd.time)).values.sum()
        val pct     = if (prevSum > 0) ((currentSum - prevSum) / prevSum * 100).toInt() else 0
        tvTrendSub.text = if (pct >= 0) "+$pct% vs previous period" else "$pct% vs previous period"
        tvTrendSub.setTextColor(if (pct > 10) 0xFFE53935.toInt() else 0xFF2E7D32.toInt())

        // Goal banner
        if (maxGoal > 0) {
            val daysIn   = dailyTotals.values.count { it in minGoal..maxGoal }
            val daysOver = dailyTotals.values.count { it > maxGoal }
            tvGoalStatus.text = "Goal Band\n$daysIn/${dailyTotals.size} within target" +
                    if (daysOver > 0) "   ⚠️ $daysOver over max" else ""
            layoutGoalStatus.visibility = View.VISIBLE
        } else {
            layoutGoalStatus.visibility = View.GONE
        }

        barChart.setDailyData(dailyTotals, minGoal, maxGoal)
        buildDailyRows(dailyTotals)
        buildLineLegend(minGoal, maxGoal)
        tvTotalSpent.text = "R${"%.2f".format(currentSum)}"
        Log.d(TAG, "Daily: ${dailyTotals.size} days, total=$currentSum")
    }

    // ── CATEGORY ──────────────────────────────────────────────────────────────

    @SuppressLint("SetTextI18n")
    private fun fetchAndDisplayByCategory(startDate: String, endDate: String) {
        val catData    = db.getCategoryTotalsWithColors(userId, startDate, endDate)
        val budget     = db.getBudgetGoals(userId)[-1] ?: 0.0
        val band       = db.getGoalBand(userId, -1)
        val minGoal    = band?.min ?: 0.0
        val maxGoal    = band?.max ?: budget
        val total      = catData.sumOf { it.total }

        tvChartTitle.text    = "Spending by Category"
        tvChartSubtitle.text = "Track your spending by category over time"

        // Right side of budget card → Remaining or Total Spent
        if (budget > 0) {
            val remaining = budget - total
            tvTrendLabel.text = "Remaining"
            tvTrend.text      = "R${"%.2f".format(remaining.coerceAtLeast(0.0))}"
            tvTrend.setTextColor(if (remaining < 0) 0xFFE53935.toInt() else 0xFF2E7D32.toInt())
        } else {
            tvTrendLabel.text = "Total Spent"
            tvTrend.text      = "R${"%.2f".format(total)}"
            tvTrend.setTextColor(0xFFE53935.toInt())
        }

        // Trend %
        val sdf      = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val calStart = Calendar.getInstance().also { c -> sdf.parse(startDate)?.let { c.time = it } }
        val calEnd   = Calendar.getInstance().also { c -> sdf.parse(endDate)?.let { c.time = it } }
        val dayCount = ((calEnd.timeInMillis - calStart.timeInMillis) / 86_400_000L).toInt() + 1
        calStart.add(Calendar.DAY_OF_YEAR, -dayCount)
        calEnd.add(Calendar.DAY_OF_YEAR, -dayCount)
        val prevTotal = db.getCategoryTotalsWithColors(userId, sdf.format(calStart.time), sdf.format(calEnd.time)).sumOf { it.total }
        val pct       = if (prevTotal > 0) ((total - prevTotal) / prevTotal * 100).toInt() else 0
        tvTrendSub.text = if (pct >= 0) "+$pct% vs previous period" else "$pct% vs previous period"
        tvTrendSub.setTextColor(if (pct > 10) 0xFFE53935.toInt() else 0xFF2E7D32.toInt())

        // Goal banner
        if (maxGoal > 0) {
            val within = catData.count { it.total <= maxGoal }
            tvGoalStatus.text = "Goal Band\n$within/${catData.size} within target"
            layoutGoalStatus.visibility = View.VISIBLE
        } else {
            layoutGoalStatus.visibility = View.GONE
        }

        barChart.setCategoryData(catData, maxGoal)
        buildCategoryRows(catData, budget)
        buildLineLegend(minGoal, maxGoal)
        tvTotalSpent.text = "R${"%.2f".format(total)}"
        Log.d(TAG, "Category: ${catData.size} cats, total=$total")
    }

    // ── BUILD DAILY ROWS ──────────────────────────────────────────────────────

    @SuppressLint("SetTextI18n")
    private fun buildDailyRows(data: Map<String, Double>) {
        llLegend.removeAllViews()
        if (data.isEmpty()) return
        val sdfIn  = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val sdfOut = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

        for ((date, amount) in data.entries.sortedByDescending { it.key }) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity     = Gravity.CENTER_VERTICAL
                setPadding(0, dp(12), 0, dp(12))
            }
            // Calendar icon chip
            val iconChip = LinearLayout(this).apply {
                setBackgroundColor(0xFFEEEEFF.toInt())
                setPadding(dp(8), dp(8), dp(8), dp(8))
                layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).also { it.setMargins(0, 0, dp(12), 0) }
                gravity = Gravity.CENTER
            }
            iconChip.addView(TextView(this).apply { text = "📅"; textSize = 13f })

            val tvDate = TextView(this).apply {
                text         = try { sdfOut.format(sdfIn.parse(date) ?: Date()) } catch (e: Exception) { date }
                textSize     = 14f
                setTextColor(0xFF1A1A2E.toInt())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val tvAmt = TextView(this).apply {
                text     = "R${"%.2f".format(amount)}"
                textSize = 14f
                setTextColor(0xFF5B4FE9.toInt())
                setTypeface(null, Typeface.BOLD)
            }
            row.addView(iconChip)
            row.addView(tvDate)
            row.addView(tvAmt)
            llLegend.addView(row)
            llLegend.addView(divider())
        }
    }

    // ── BUILD CATEGORY ROWS ───────────────────────────────────────────────────

    @SuppressLint("SetTextI18n")
    private fun buildCategoryRows(data: List<DatabaseHelper.CategoryTotal>, budget: Double) {
        llLegend.removeAllViews()
        if (data.isEmpty()) return

        for (cat in data) {
            val catColor = try { cat.colour.toColorInt() } catch (e: Exception) { 0xFF1565C0.toInt() }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity     = Gravity.CENTER_VERTICAL
                setPadding(0, dp(12), 0, dp(12))
            }
            // Colored icon square
            val iconSquare = LinearLayout(this).apply {
                setBackgroundColor(catColor)
                setPadding(dp(8), dp(8), dp(8), dp(8))
                layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).also { it.setMargins(0, 0, dp(12), 0) }
                gravity = Gravity.CENTER
            }
            iconSquare.addView(TextView(this).apply { text = categoryEmoji(cat.name); textSize = 13f })

            val tvName = TextView(this).apply {
                text         = cat.name
                textSize     = 14f
                setTextColor(0xFF1A1A2E.toInt())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val tvAmt = TextView(this).apply {
                text     = "R${"%.2f".format(cat.total)}"
                textSize = 14f
                setTextColor(catColor)
                setTypeface(null, Typeface.BOLD)
            }
            // % of budget
            if (budget > 0) {
                val pct = (cat.total / budget * 100).toInt()
                row.addView(iconSquare)
                row.addView(tvName)
                row.addView(tvAmt)
                row.addView(TextView(this).apply {
                    text     = "  $pct% of budget"
                    textSize = 11f
                    setTextColor(0xFF9E9E9E.toInt())
                })
            } else {
                row.addView(iconSquare)
                row.addView(tvName)
                row.addView(tvAmt)
            }
            llLegend.addView(row)
            llLegend.addView(divider())
        }
    }

    // ── BUILD LINE LEGEND ─────────────────────────────────────────────────────

    @SuppressLint("SetTextI18n")
    private fun buildLineLegend(minGoal: Double, maxGoal: Double) {
        llLineLegend.removeAllViews()
        if (maxGoal <= 0 && minGoal <= 0) { llLineLegend.visibility = View.GONE; return }
        llLineLegend.visibility = View.VISIBLE

        if (maxGoal > 0) addLineLegendRow(0xFFE53935.toInt(), dashed = true,  "Max (Over Budget)", "R${maxGoal.toInt()}")
        addLineLegendRow(0xFFE53935.toInt(),                   dashed = false, "Over Budget",        "Above Budget Limit")
        addLineLegendRow(0xFF1565C0.toInt(),                   dashed = true,  "Within Budget",      "Within Budget Limit")
    }

    @SuppressLint("SetTextI18n")
    private fun addLineLegendRow(lineColor: Int, dashed: Boolean, label: String, value: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
        }
        row.addView(LineSampleView(this, lineColor, dashed).apply {
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(14)).also { it.setMargins(0, 0, dp(10), 0) }
        })
        row.addView(TextView(this).apply {
            text         = label
            textSize     = 12f
            setTextColor(0xFF616161.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(TextView(this).apply {
            text     = value
            textSize = 12f
            setTextColor(0xFF9E9E9E.toInt())
        })
        llLineLegend.addView(row)
    }

    // ── HELPERS ───────────────────────────────────────────────────────────────

    private fun divider() = View(this).apply {
        setBackgroundColor(0xFFF5F5F5.toInt())
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
    }

    private fun categoryEmoji(name: String): String = when {
        name.contains("Groceries",     ignoreCase = true) -> "🛒"
        name.contains("Transport",     ignoreCase = true) -> "🚌"
        name.contains("Utilities",     ignoreCase = true) -> "⚡"
        name.contains("Entertainment", ignoreCase = true) -> "🎬"
        name.contains("Health",        ignoreCase = true) -> "💊"
        name.contains("Food",          ignoreCase = true) -> "🍔"
        else                                              -> "💳"
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density + 0.5f).toInt()

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}

// =============================================================================
// LineSampleView — short horizontal line for the legend (solid or dashed)
// =============================================================================
class LineSampleView(
    context: Context,
    private val lineColor: Int = Color.RED,
    private val dashed: Boolean = true
) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color       = lineColor
        strokeWidth = 4f
        style       = Paint.Style.STROKE
        if (dashed) pathEffect = DashPathEffect(floatArrayOf(14f, 8f), 0f)
    }

    override fun onDraw(canvas: Canvas) {
        val midY = height / 2f
        canvas.drawLine(0f, midY, width.toFloat(), midY, paint)
    }
}

// =============================================================================
// BarChartView — Y-axis price labels, X-axis names, dashed reference lines
// =============================================================================
class BarChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var dailyData:    Map<String, Double>                = emptyMap()
    private var categoryData: List<DatabaseHelper.CategoryTotal> = emptyList()
    private var minGoal    = 0.0
    private var maxGoal    = 0.0
    private var isCategoryMode = false

    // Paints
    private val bgPaint = Paint().apply { color = Color.WHITE }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color       = 0xFFEEEEEE.toInt()
        strokeWidth = 1.5f
        style       = Paint.Style.STROKE
        pathEffect  = DashPathEffect(floatArrayOf(12f, 8f), 0f)
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color       = 0xFFCCCCCC.toInt()
        strokeWidth = 2f
        style       = Paint.Style.STROKE
    }
    private val yLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = 0xFF9E9E9E.toInt()
        textSize  = 28f
        textAlign = Paint.Align.RIGHT
    }
    private val xLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color          = 0xFF37474F.toInt()
        textSize       = 28f
        textAlign      = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color          = 0xFF37474F.toInt()
        textSize       = 26f
        textAlign      = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val maxLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color       = 0xFFE53935.toInt()
        strokeWidth = 3f
        style       = Paint.Style.STROKE
        pathEffect  = DashPathEffect(floatArrayOf(20f, 12f), 0f)
    }
    private val minLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color       = 0xFF1565C0.toInt()
        strokeWidth = 3f
        style       = Paint.Style.STROKE
        pathEffect  = DashPathEffect(floatArrayOf(20f, 12f), 0f)
    }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = 0xFFBDBDBD.toInt()
        textSize  = 36f
        textAlign = Paint.Align.CENTER
    }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    // Public setters
    fun setDailyData(data: Map<String, Double>, min: Double, max: Double) {
        dailyData = data; minGoal = min; maxGoal = max
        isCategoryMode = false; invalidate()
    }

    fun setCategoryData(data: List<DatabaseHelper.CategoryTotal>, budget: Double = 0.0) {
        categoryData = data; maxGoal = budget
        isCategoryMode = true; invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (width == 0 || height == 0) return
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        if (isCategoryMode) drawCategoryBars(canvas) else drawDailyBars(canvas)
    }

    private fun drawCategoryBars(canvas: Canvas) {
        if (categoryData.isEmpty()) {
            canvas.drawText("No expenses yet", width / 2f, height / 2f, emptyPaint)
            return
        }
        val leftPad = 110f; val rightPad = 20f; val topPad = 40f; val botPad = 60f
        val cR = width - rightPad
        val cT = topPad;  val cB = height - botPad
        val cW = cR - leftPad; val cH = cB - cT

        val dataMax = categoryData.maxOf { it.total }.coerceAtLeast(1.0)
        val yMax    = maxOf(dataMax, maxGoal, 1.0) * 1.12

        drawGridAndAxes(canvas, leftPad, cR, cT, cB, cH, yMax)

        if (maxGoal > 0 && maxGoal <= yMax) {
            val y = (cB - (maxGoal / yMax) * cH).toFloat()
            canvas.drawLine(leftPad, y, cR, y, maxLinePaint)
        }
        if (minGoal > 0 && minGoal <= yMax) {
            val y = (cB - (minGoal / yMax) * cH).toFloat()
            canvas.drawLine(leftPad, y, cR, y, minLinePaint)
        }

        val n = categoryData.size
        val spacing = cW / n
        val barW    = (spacing * 0.5f).coerceAtMost(130f)

        categoryData.forEachIndexed { i, cat ->
            val cx   = leftPad + i * spacing + spacing / 2f
            val barH = (cat.total / yMax * cH).toFloat()
            val top  = cB - barH

            barPaint.color  = try { cat.colour.toColorInt() } catch (e: Exception) { 0xFF1565C0.toInt() }
            barPaint.shader = null
            drawBar(canvas, cx, barW, top, cB)

            if (barH > 10f) {
                valuePaint.color = 0xFF37474F.toInt()
                canvas.drawText("R${cat.total.toInt()}", cx, top - 10f, valuePaint)
            }
            val label = if (cat.name.length > 10) cat.name.take(9) + "…" else cat.name
            canvas.drawText(label, cx, cB + xLabelPaint.textSize + 10f, xLabelPaint)
        }
    }

    private fun drawDailyBars(canvas: Canvas) {
        if (dailyData.isEmpty()) {
            canvas.drawText("No expenses yet", width / 2f, height / 2f, emptyPaint)
            return
        }
        val leftPad = 110f; val rightPad = 20f; val topPad = 40f; val botPad = 60f
        val cL = leftPad; val cR = width - rightPad
        val cT = topPad;  val cB = height - botPad
        val cW = cR - cL; val cH = cB - cT

        val dataMax = dailyData.values.maxOrNull() ?: 1.0
        val yMax    = maxOf(dataMax, maxGoal, minGoal, 1.0) * 1.12

        drawGridAndAxes(canvas, cL, cR, cT, cB, cH, yMax)

        if (maxGoal > 0) {
            val y = (cB - (maxGoal / yMax) * cH).toFloat()
            canvas.drawLine(cL, y, cR, y, maxLinePaint)
        }
        if (minGoal > 0) {
            val y = (cB - (minGoal / yMax) * cH).toFloat()
            canvas.drawLine(cL, y, cR, y, minLinePaint)
        }

        val entries = dailyData.entries.toList().sortedBy { it.key }
        val n       = entries.size
        val spacing = cW / n
        val barW    = (spacing * 0.5f).coerceAtMost(110f)

        barPaint.color  = 0xFF5B4FE9.toInt()   // purple for daily
        barPaint.shader = null

        entries.forEachIndexed { i, (date, value) ->
            val cx   = cL + i * spacing + spacing / 2f
            val barH = (value / yMax * cH).toFloat()
            val top  = cB - barH
            drawBar(canvas, cx, barW, top, cB)
            if (barH > 10f) {
                valuePaint.color = 0xFF37474F.toInt()
                canvas.drawText("R${value.toInt()}", cx, top - 10f, valuePaint)
            }
            canvas.drawText(formatDayLabel(date), cx, cB + xLabelPaint.textSize + 10f, xLabelPaint)
        }
    }

    /** Draw grid lines, Y-axis labels, and both axis lines */
    private fun drawGridAndAxes(
        canvas: Canvas, cL: Float, cR: Float, cT: Float, cB: Float, cH: Float, yMax: Double
    ) {
        for (s in 0..5) {
            val frac  = s.toDouble() / 5
            val value = yMax * frac
            val y     = (cB - frac * cH).toFloat()
            canvas.drawLine(cL, y, cR, y, gridPaint)
            canvas.drawText(formatY(value), cL - 8f, y + yLabelPaint.textSize * 0.35f, yLabelPaint)
        }
        canvas.drawLine(cL, cT, cL, cB, axisPaint)
        canvas.drawLine(cL, cB, cR, cB, axisPaint)
    }

    /** Draw a rounded-top bar centered at cx */
    private fun drawBar(canvas: Canvas, cx: Float, barW: Float, top: Float, baseY: Float) {
        val left  = cx - barW / 2f
        val right = cx + barW / 2f
        val r     = barW * 0.15f
        canvas.drawRoundRect(RectF(left, top, right, baseY), r, r, barPaint)
        // Fill the bottom corners to make only top rounded
        if (baseY - top > r) canvas.drawRect(RectF(left, top + r, right, baseY), barPaint)
    }

    private fun formatY(value: Double): String = when {
        value >= 1_000_000 -> "R${"%.1f".format(value / 1_000_000)}M"
        value >= 1_000     -> "R${"%.1f".format(value / 1_000)}k"
        value == 0.0       -> "R0"
        else               -> "R${value.toInt()}"
    }

    private fun formatDayLabel(date: String): String {
        return try {
            val sdfIn  = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val sdfOut = SimpleDateFormat("MMM dd",     Locale.getDefault())
            sdfOut.format(sdfIn.parse(date) ?: return date)
        } catch (e: Exception) { date.takeLast(5) }
    }
}