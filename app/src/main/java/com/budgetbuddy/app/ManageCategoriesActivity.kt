package com.budgetbuddy.app

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt

/**
 * ManageCategoriesActivity — create, rename, recolor and delete expense categories.
 * New categories can be added on top of the 5 seeded defaults.
 * A color-swatch picker lets the user choose the badge color.
 */
class ManageCategoriesActivity : AppCompatActivity() {

    private lateinit var db: DatabaseHelper
    private lateinit var session: SessionManager
    private lateinit var llCategories: LinearLayout
    private lateinit var etNewCatName: EditText
    private lateinit var btnAddCategory: Button
    private lateinit var llColourPicker: LinearLayout

    private var userId = -1
    private var selectedColour = "#4CAF50"

    private val palette = listOf(
        "#4CAF50", "#2196F3", "#9C27B0", "#FF9800", "#F44336",
        "#009688", "#795548", "#607D8B", "#E91E63", "#FF5722",
        "#00BCD4", "#FFC107", "#3F51B5", "#8BC34A", "#FF4081"
    )

    companion object { private const val TAG = "ManageCategoriesActivity" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db      = DatabaseHelper(this)
        session = SessionManager(this)
        userId  = session.getUserId()
        setContentView(R.layout.activity_manage_categories)

        llCategories   = findViewById(R.id.llCategories)
        etNewCatName   = findViewById(R.id.etNewCategoryName)
        btnAddCategory = findViewById(R.id.btnAddCategory)
        llColourPicker = findViewById(R.id.llColourPicker)

        buildColourPicker()
        btnAddCategory.setOnClickListener { addCategory() }
        loadCategories()

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Manage Categories"
    }

    override fun onResume() { super.onResume(); loadCategories() }

    // ── Colour picker ──────────────────────────────────────────────────────────
    private fun buildColourPicker() {
        llColourPicker.removeAllViews()
        for (hex in palette) {
            val swatch = android.view.View(this).apply {
                val size = dp(34)
                layoutParams = LinearLayout.LayoutParams(size, size).also { it.setMargins(dp(3), 0, dp(3), 0) }
                setBackgroundColor(parseColour(hex))
                alpha = if (hex == selectedColour) 1f else 0.4f
                tag = hex
                isClickable = true; isFocusable = true
                setOnClickListener { selectedColour = hex; refreshPicker(llColourPicker) }
            }
            llColourPicker.addView(swatch)
        }
    }

    private fun refreshPicker(row: LinearLayout) {
        for (i in 0 until row.childCount) {
            val v = row.getChildAt(i); v.alpha = if (v.tag == selectedColour) 1f else 0.4f
        }
    }

    // ── Category list ──────────────────────────────────────────────────────────
    @SuppressLint("SetTextI18n")
    private fun loadCategories() {
        llCategories.removeAllViews()
        val cats = db.getCategories(userId)
        if (cats.isEmpty()) {
            llCategories.addView(TextView(this).apply {
                text = "No categories yet. Add one below."
                setTextColor(0xFF9E9E9E.toInt()); setPadding(0, 16, 0, 16)
            })
            return
        }
        for (cat in cats) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 10, 0, 10); gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(android.view.View(this).apply {
                setBackgroundColor(parseColour(cat.colour))
                layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).also { it.setMargins(0, 0, dp(12), 0) }
            })
            row.addView(TextView(this).apply {
                text = cat.name; textSize = 15f; setTextColor(0xFF212121.toInt())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(Button(this).apply {
                text = "Edit"; textSize = 11f
                setBackgroundColor(0xFF1565C0.toInt()); setTextColor(0xFFFFFFFF.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.setMargins(dp(4), 0, dp(4), 0) }
                setOnClickListener { showEditDialog(cat) }
            })
            row.addView(Button(this).apply {
                text = "Delete"; textSize = 11f
                setBackgroundColor(0xFFB71C1C.toInt()); setTextColor(0xFFFFFFFF.toInt())
                setOnClickListener { confirmDelete(cat) }
            })
            val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            wrap.addView(row)
            wrap.addView(android.view.View(this).apply {
                setBackgroundColor(0xFFEEEEEE.toInt())
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
            })
            llCategories.addView(wrap)
        }
        Log.d(TAG, "Loaded ${cats.size} categories")
    }

    // ── Add ────────────────────────────────────────────────────────────────────
    private fun addCategory() {
        val name = etNewCatName.text.toString().trim()
        if (name.isEmpty()) { etNewCatName.error = "Enter a category name"; return }
        when (val result = db.addCategory(userId, name, selectedColour)) {
            -2L -> Toast.makeText(this, "\"$name\" already exists — try a different name", Toast.LENGTH_SHORT).show()
            -1L -> Toast.makeText(this, "Failed to add. Please try again.", Toast.LENGTH_SHORT).show()
            else -> if (result > 0) {
                etNewCatName.text.clear(); etNewCatName.error = null
                Toast.makeText(this, "\"$name\" added ✅", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Category added: $name id=$result colour=$selectedColour")
                loadCategories()
            } else Toast.makeText(this, "Could not save category", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Edit ───────────────────────────────────────────────────────────────────
    @SuppressLint("SetTextI18n")
    private fun showEditDialog(cat: Category) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(16), dp(24), dp(8))
        }
        val etName = EditText(this).apply { setText(cat.name); textSize = 15f; hint = "Category name" }
        container.addView(etName)
        container.addView(TextView(this).apply {
            text = "Pick a colour:"; textSize = 13f; setTextColor(0xFF546E7A.toInt())
            setPadding(0, dp(12), 0, dp(6))
        })
        var editColour = cat.colour
        val swatchRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (hex in palette) {
            swatchRow.addView(android.view.View(this).apply {
                val sz = dp(28)
                layoutParams = LinearLayout.LayoutParams(sz, sz).also { it.setMargins(dp(3), 0, dp(3), 0) }
                setBackgroundColor(parseColour(hex)); alpha = if (hex == editColour) 1f else 0.4f; tag = hex
                isClickable = true; isFocusable = true
                setOnClickListener {
                    editColour = hex
                    for (i in 0 until swatchRow.childCount) {
                        val v = swatchRow.getChildAt(i); v.alpha = if (v.tag == editColour) 1f else 0.4f
                    }
                }
            })
        }
        container.addView(swatchRow)
        AlertDialog.Builder(this)
            .setTitle("Edit \"${cat.name}\"").setView(container)
            .setPositiveButton("Save") { _, _ ->
                val newName = etName.text.toString().trim()
                if (newName.isEmpty()) { Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                if (db.updateCategory(cat.id, newName, editColour)) {
                    Toast.makeText(this, "Updated ✅", Toast.LENGTH_SHORT).show()
                    loadCategories()
                } else Toast.makeText(this, "Failed to update", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null).show()
    }

    // ── Delete ─────────────────────────────────────────────────────────────────
    private fun confirmDelete(cat: Category) {
        AlertDialog.Builder(this)
            .setTitle("Delete \"${cat.name}\"?")
            .setMessage("Expenses linked to this category will lose their label. Cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                if (db.deleteCategory(cat.id)) {
                    Toast.makeText(this, "\"${cat.name}\" deleted", Toast.LENGTH_SHORT).show()
                    loadCategories()
                } else Toast.makeText(this, "Failed to delete", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun parseColour(hex: String): Int = try { hex.toColorInt() } catch (_: Exception) { 0xFF4CAF50.toInt() }
    private fun dp(v: Int) = (v * resources.displayMetrics.density + 0.5f).toInt()
    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}