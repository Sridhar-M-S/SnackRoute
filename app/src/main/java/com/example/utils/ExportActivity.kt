package com.example.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale

class ExportActivity : ComponentActivity() {

    private lateinit var sourceFile: File
    private var fileTitle: String = "Exported File"
    private var originalUri: Uri? = null
    private var originalFileName: String? = null

    private val selectOriginalFileForWriteLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (e: Exception) {
                // Persistable permissions may not be needed if session active
            }
            val prefs = getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
            prefs.edit().putString("last_imported_unified_excel_uri", uri.toString()).apply()
            originalUri = uri
            saveFileToUri(uri)
        } else {
            Toast.makeText(this, "Update cancelled", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        if (uri != null) {
            saveFileToUri(uri)
        } else {
            Toast.makeText(this, "Save cancelled", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val filePath = intent.getStringExtra("file_path")
        fileTitle = intent.getStringExtra("file_title") ?: "Exported File"

        if (filePath.isNullOrBlank()) {
            Toast.makeText(this, "Error: Invalid file path", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        sourceFile = File(filePath)
        if (!sourceFile.exists()) {
            Toast.makeText(this, "Error: File does not exist", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Check if there is an imported Excel file URI
        val prefs = getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        val savedUriString = prefs.getString("last_imported_unified_excel_uri", null)
        if (!savedUriString.isNullOrBlank()) {
            try {
                val parsed = Uri.parse(savedUriString)
                originalUri = parsed
                contentResolver.query(parsed, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            originalFileName = cursor.getString(nameIndex)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        showExportOptionsDialog()
    }

    private fun showExportOptionsDialog() {
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle(fileTitle)

        if (originalUri != null) {
            val fileName = originalFileName ?: "Imported Excel"
            builder.setMessage("Imported file detected: $fileName\n\nChoose an export action:")

            builder.setPositiveButton("Update Original File") { _, _ ->
                updateOriginalFile(originalUri!!)
            }

            builder.setNeutralButton("Save as New File") { _, _ ->
                launchSaveToDevice()
            }

            builder.setNegativeButton("Share") { _, _ ->
                shareFileDirectly()
            }
        } else {
            builder.setMessage("Choose how you want to export this file:")

            builder.setPositiveButton("Save to Device") { _, _ ->
                launchSaveToDevice()
            }

            builder.setNegativeButton("Share") { _, _ ->
                shareFileDirectly()
            }
        }

        builder.setOnCancelListener {
            finish()
        }

        builder.show()
    }

    private fun updateOriginalFile(uri: Uri) {
        try {
            val outputStream = contentResolver.openOutputStream(uri, "wt")
                ?: throw Exception("Could not obtain write stream for this file")
            outputStream.use { os ->
                sourceFile.inputStream().use { inputStream ->
                    inputStream.copyTo(os)
                }
            }
            val displayName = originalFileName ?: "Imported Excel file"
            Toast.makeText(this, "$displayName updated with latest data successfully!", Toast.LENGTH_LONG).show()
            finish()
        } catch (e: Exception) {
            e.printStackTrace()
            val fileName = originalFileName ?: "your original Excel file"
            Toast.makeText(this, "Please select '$fileName' once to grant write permission", Toast.LENGTH_LONG).show()
            try {
                selectOriginalFileForWriteLauncher.launch(
                    arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel", "*/*")
                )
            } catch (ex: Exception) {
                ex.printStackTrace()
                launchSaveToDevice()
            }
        }
    }

    private fun shareFileDirectly() {
        try {
            val uri = FileProvider.getUriForFile(
                this,
                "com.example.snackroutepro.fileprovider",
                sourceFile
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                val extension = sourceFile.extension.lowercase(Locale.ROOT)
                type = when (extension) {
                    "zip" -> "application/zip"
                    "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    else -> "*/*"
                }
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, fileTitle)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Export via"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error sharing: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            finish()
        }
    }

    private fun launchSaveToDevice() {
        try {
            createDocumentLauncher.launch(sourceFile.name)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error opening save dialog: ${e.message}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun saveFileToUri(uri: Uri) {
        try {
            val outputStream = contentResolver.openOutputStream(uri, "wt") ?: contentResolver.openOutputStream(uri)
                ?: throw Exception("Could not open file for writing")
            outputStream.use { os ->
                sourceFile.inputStream().use { inputStream ->
                    inputStream.copyTo(os)
                }
            }
            Toast.makeText(this, "File updated and saved successfully!", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to save file: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            finish()
        }
    }
}

