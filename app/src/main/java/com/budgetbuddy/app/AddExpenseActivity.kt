package com.budgetbuddy.app

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * AddExpenseActivity handles adding or editing an expense.
 * Receipt photos are compressed and stored as BLOB in SQLite.
 */
class AddExpenseActivity : AppCompatActivity() {

    private lateinit var db: DatabaseHelper
    private lateinit var session: SessionManager

    private lateinit var etAmount: EditText
    private lateinit var etDescription: EditText
    private lateinit var tvDate: TextView
    private lateinit var spinnerCategory: Spinner
    private lateinit var btnPickDate: Button
    private lateinit var btnAttachPhoto: LinearLayout
    private lateinit var btnSave: Button
    private lateinit var ivReceiptPreview: ImageView
    private lateinit var tvPhotoStatus: TextView

    private var selectedDate = ""
    private var photoBlob: ByteArray? = null
    private var pendingCameraPhotoPath: String? = null
    private var editingExpenseId = -1
    private var categoryList: List<Category> = emptyList()

    companion object {
        const val EXTRA_EXPENSE_ID = "expenseId"
        private const val TAG = "AddExpenseActivity"
        private const val MAX_PHOTO_BYTES = 500_000  // compress to ~500KB max
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && !pendingCameraPhotoPath.isNullOrBlank()) {
            val file = File(pendingCameraPhotoPath!!)
            if (file.exists()) {
                photoBlob = compressImageFile(file)
                showPhotoPreview()
                Log.d(TAG, "Camera photo stored as BLOB: ${photoBlob?.size} bytes")
            }
        }
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val bytes = contentResolver.openInputStream(uri)?.readBytes()
                if (bytes != null) {
                    photoBlob = compressBytes(bytes)
                    showPhotoPreview()
                    Log.d(TAG, "Gallery photo stored as BLOB: ${photoBlob?.size} bytes")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Gallery pick failed: ${e.message}")
                Toast.makeText(this, "Failed to load photo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) takePhoto()
        else Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = DatabaseHelper(this)
        session = SessionManager(this)
        setContentView(R.layout.activity_add_expense)

        bindViews()
        setupDatePicker()
        loadCategories()

        editingExpenseId = intent.getIntExtra(EXTRA_EXPENSE_ID, -1)
        if (editingExpenseId != -1) populateForEditing(editingExpenseId)

        btnAttachPhoto.setOnClickListener { showPhotoOptions() }
        btnSave.setOnClickListener { saveExpense() }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = if (editingExpenseId == -1) "Add Expense" else "Edit Expense"
    }

    private fun bindViews() {
        etAmount = findViewById(R.id.etAmount)
        etDescription = findViewById(R.id.etDescription)
        tvDate = findViewById(R.id.tvDate)
        spinnerCategory = findViewById(R.id.spinnerCategory)
        btnPickDate = findViewById(R.id.btnPickDate)
        btnAttachPhoto = findViewById(R.id.btnAttachPhoto)
        btnSave = findViewById(R.id.btnSaveExpense)
        ivReceiptPreview = findViewById(R.id.ivReceiptPreview)
        tvPhotoStatus = findViewById(R.id.tvPhotoStatus)
        selectedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        tvDate.text = selectedDate
    }

    private fun setupDatePicker() {
        btnPickDate.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(this, { _, year, month, day ->
                selectedDate = "%04d-%02d-%02d".format(year, month + 1, day)
                tvDate.text = selectedDate
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
    }

    private fun loadCategories() {
        categoryList = db.getCategories(session.getUserId())
        if (categoryList.isEmpty()) {
            Toast.makeText(this, "No categories found.", Toast.LENGTH_LONG).show()
            return
        }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categoryList.map { it.name })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCategory.adapter = adapter
    }

    private fun populateForEditing(expenseId: Int) {
        val expense = db.getExpenseById(expenseId) ?: return
        etAmount.setText(expense.amount.toString())
        etDescription.setText(expense.description)
        selectedDate = expense.date
        tvDate.text = expense.date
        photoBlob = expense.photoBlob
        val idx = categoryList.indexOfFirst { it.id == expense.categoryId }
        if (idx >= 0) spinnerCategory.setSelection(idx)
        if (photoBlob != null) showPhotoPreview()
    }

    // -------------------------------------------------------------------------
    // Photo handling
    // -------------------------------------------------------------------------

    private fun showPhotoOptions() {
        val options = arrayOf("Take Photo", "Choose from Gallery", "Remove Photo")
        android.app.AlertDialog.Builder(this)
            .setTitle("Attach Receipt Photo")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> checkCameraAndTakePhoto()
                    1 -> galleryLauncher.launch("image/*")
                    2 -> removePhoto()
                }
            }.show()
    }

    private fun checkCameraAndTakePhoto() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) takePhoto()
        else cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
    }

    private fun takePhoto() {
        val photoFile = createTempImageFile()
        pendingCameraPhotoPath = photoFile.absolutePath
        val uri = FileProvider.getUriForFile(this, "${packageName}.provider", photoFile)
        cameraLauncher.launch(uri)
    }

    @SuppressLint("SetTextI18n")
    private fun removePhoto() {
        photoBlob = null
        ivReceiptPreview.setImageDrawable(null)
        tvPhotoStatus.text = "No photo attached"
    }

    @SuppressLint("SetTextI18n")
    private fun showPhotoPreview() {
        val blob = photoBlob ?: return
        val bitmap = BitmapFactory.decodeByteArray(blob, 0, blob.size)
        ivReceiptPreview.setImageBitmap(bitmap)
        tvPhotoStatus.text = "Photo attached ✅"
    }

    /** Compress a JPEG file to under MAX_PHOTO_BYTES */
    private fun compressImageFile(file: File): ByteArray {
        return compressBytes(file.readBytes())
    }

    private fun compressBytes(input: ByteArray): ByteArray {
        val bitmap = BitmapFactory.decodeByteArray(input, 0, input.size) ?: return input
        var quality = 85
        var result: ByteArray
        do {
            val out = ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, out)
            result = out.toByteArray()
            quality -= 10
        } while (result.size > MAX_PHOTO_BYTES && quality > 10)
        return result
    }

    private fun createTempImageFile(): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("RECEIPT_${timestamp}_", ".jpg", storageDir)
    }

    // -------------------------------------------------------------------------
    // Save
    // -------------------------------------------------------------------------

    private fun saveExpense() {
        val amountText = etAmount.text.toString().trim()
        val description = etDescription.text.toString().trim()
        val userId = session.getUserId()

        if (amountText.isEmpty()) { etAmount.error = "Amount is required"; return }
        val amount = amountText.toDoubleOrNull()
        if (amount == null || amount <= 0) { etAmount.error = "Enter a valid positive amount"; return }
        if (description.isEmpty()) { etDescription.error = "Description is required"; return }
        if (categoryList.isEmpty()) {
            Toast.makeText(this, "Please create a category first", Toast.LENGTH_SHORT).show()
            return
        }

        val selectedCategory = categoryList[spinnerCategory.selectedItemPosition]
        val expense = Expense(
            id = if (editingExpenseId == -1) 0 else editingExpenseId,
            amount = amount,
            description = description,
            date = selectedDate,
            categoryId = selectedCategory.id,
            photoBlob = photoBlob,
            userId = userId
        )

        val success = if (editingExpenseId == -1) db.addExpense(expense) > 0
        else db.updateExpense(expense)

        if (success) {
            Log.d(TAG, "Expense saved successfully")
            Toast.makeText(this, "Expense saved!", Toast.LENGTH_SHORT).show()
            finish()
        } else {
            Toast.makeText(this, "Failed to save. Please try again.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}