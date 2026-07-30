package com.example.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class ExportActivity : ComponentActivity() {

    private lateinit var sourceFile: File
    private var fileTitle: String = "Exported File"

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

        showExportOptionsDialog()
    }

    private fun showExportOptionsDialog() {
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle(fileTitle)
        builder.setMessage("Choose how you want to export this file:")
        
        builder.setPositiveButton("Share") { _, _ ->
            shareFileDirectly()
        }
        
        builder.setNegativeButton("Save to Device") { _, _ ->
            launchSaveToDevice()
        }
        
        builder.setOnCancelListener {
            finish()
        }
        
        builder.show()
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
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                sourceFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            Toast.makeText(this, "File saved successfully!", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to save file: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            finish()
        }
    }
}
