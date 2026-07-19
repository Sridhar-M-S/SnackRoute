package com.example.utils

import android.content.Context
import android.net.Uri
import android.os.Environment
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupHelper {

    fun cleanupUnusedImages(context: Context) {
        try {
            val db = com.example.data.AppDatabase.getDatabase(context)
            val activeImages = mutableSetOf<String>()
            
            // Query the SQLite database directly to retrieve active images
            try {
                db.openHelper.readableDatabase.let { sdb ->
                    val cursor = sdb.query("SELECT storeImage FROM shops WHERE storeImage IS NOT NULL")
                    if (cursor != null) {
                        if (cursor.moveToFirst()) {
                            do {
                                val path = cursor.getString(0)
                                if (!path.isNullOrEmpty()) {
                                    activeImages.add(File(path).name)
                                }
                            } while (cursor.moveToNext())
                        }
                        cursor.close()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            val filesDir = context.filesDir
            if (filesDir.exists()) {
                val imageFiles = filesDir.listFiles { file ->
                    file.name.startsWith("shop_img_")
                }
                imageFiles?.forEach { file ->
                    if (!activeImages.contains(file.name)) {
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun createBackupZip(context: Context, outputFile: File): Boolean {
        return try {
            // Flush WAL to ensure main database file has all recent changes without closing database
            try {
                com.example.data.AppDatabase.getDatabase(context).openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            // Clean up unused/orphaned images before backup
            cleanupUnusedImages(context)
            
            val zipOut = ZipOutputStream(BufferedOutputStream(FileOutputStream(outputFile)))
            
            val dbDir = context.getDatabasePath("snackroute_pro_db").parentFile
            val filesDir = context.filesDir
            val sharedPrefsDir = File(context.filesDir.parent, "shared_prefs")
            
            // Backup databases
            if (dbDir != null && dbDir.exists()) {
                zipDirectory(dbDir, "databases/", zipOut)
            }
            
            // Backup files (excluding backups folder itself)
            if (filesDir.exists()) {
                zipDirectory(filesDir, "files/", zipOut, excludeFolder = "backups")
            }
            
            // Backup shared preferences
            if (sharedPrefsDir.exists()) {
                zipDirectory(sharedPrefsDir, "shared_prefs/", zipOut)
            }
            
            zipOut.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun zipDirectory(dir: File, baseName: String, zipOut: ZipOutputStream, excludeFolder: String? = null) {
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (excludeFolder != null && file.name.equals(excludeFolder, ignoreCase = true)) {
                continue
            }
            if (file.isDirectory) {
                zipDirectory(file, "$baseName${file.name}/", zipOut, excludeFolder)
            } else {
                val entryName = "$baseName${file.name}"
                val zipEntry = ZipEntry(entryName)
                zipOut.putNextEntry(zipEntry)
                FileInputStream(file).use { input ->
                    input.copyTo(zipOut)
                }
                zipOut.closeEntry()
            }
        }
    }

    fun restoreBackupZip(context: Context, zipFile: File): Boolean {
        return try {
            // Close database and nullify active instance before restoration
            com.example.data.AppDatabase.closeDatabase()
            
            // Delete existing database files to prevent stale WAL corruption
            val dbFile = context.getDatabasePath("snackroute_pro_db")
            if (dbFile.exists()) {
                dbFile.delete()
            }
            val walFile = File(dbFile.path + "-wal")
            if (walFile.exists()) {
                walFile.delete()
            }
            val shmFile = File(dbFile.path + "-shm")
            if (shmFile.exists()) {
                shmFile.delete()
            }
            
            // Delete existing shop images to prevent keeping obsolete or old images
            val filesDir = context.filesDir
            if (filesDir.exists()) {
                val existingImageFiles = filesDir.listFiles { file ->
                    file.name.startsWith("shop_img_")
                }
                existingImageFiles?.forEach { it.delete() }
            }
            
            val zipIn = ZipInputStream(BufferedInputStream(FileInputStream(zipFile)))
            var entry = zipIn.nextEntry
            val buffer = ByteArray(4096)
            
            val appParentDir = context.filesDir.parentFile ?: return false
            
            while (entry != null) {
                val entryName = entry.name
                val outFile = File(appParentDir, entryName)
                
                // Ensure parent directories exist
                outFile.parentFile?.let {
                    if (!it.exists()) it.mkdirs()
                }
                
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    // Delete existing file before writing to prevent Linux sharing/lock violations
                    if (outFile.exists()) {
                        outFile.delete()
                    }
                    // Overwrite file
                    FileOutputStream(outFile).use { output ->
                        var len: Int
                        while (zipIn.read(buffer).also { len = it } > 0) {
                            output.write(buffer, 0, len)
                        }
                    }
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
            zipIn.close()
            // Clean up any unused restored images immediately to keep size small
            cleanupUnusedImages(context)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun restoreBackupFromUri(context: Context, uri: Uri): Boolean {
        return try {
            val tempFile = File(context.cacheDir, "temp_restore_backup.zip")
            if (tempFile.exists()) tempFile.delete()
            
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            val success = restoreBackupZip(context, tempFile)
            tempFile.delete()
            success
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    object EncryptionHelper {
        private val keyBytes = "SnackRouteKey2026SecurePass12345".toByteArray(Charsets.UTF_8).take(32).toByteArray()
        private val ivBytes = "SnackRouteIv2026".toByteArray(Charsets.UTF_8).take(16).toByteArray()

        fun encrypt(inputFile: File, outputFile: File) {
            val cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding")
            val secretKey = javax.crypto.spec.SecretKeySpec(keyBytes, "AES")
            val iv = javax.crypto.spec.IvParameterSpec(ivBytes)
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, secretKey, iv)

            FileInputStream(inputFile).use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        val outputBytes = cipher.update(buffer, 0, bytesRead)
                        if (outputBytes != null) {
                            output.write(outputBytes)
                        }
                    }
                    val finalBytes = cipher.doFinal()
                    if (finalBytes != null) {
                        output.write(finalBytes)
                    }
                }
            }
        }

        fun decrypt(inputFile: File, outputFile: File) {
            val cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding")
            val secretKey = javax.crypto.spec.SecretKeySpec(keyBytes, "AES")
            val iv = javax.crypto.spec.IvParameterSpec(ivBytes)
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, secretKey, iv)

            FileInputStream(inputFile).use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        val outputBytes = cipher.update(buffer, 0, bytesRead)
                        if (outputBytes != null) {
                            output.write(outputBytes)
                        }
                    }
                    val finalBytes = cipher.doFinal()
                    if (finalBytes != null) {
                        output.write(finalBytes)
                    }
                }
            }
        }
    }
}
