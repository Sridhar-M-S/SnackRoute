package com.example.utils

import android.accounts.Account
import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object GoogleDriveSyncHelper {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private const val FOLDER_NAME = "SnackRoute Pro"
    private const val BACKUP_FILE_NAME = "snackroute_full_backup.enc"
    private const val SCOPE = "oauth2:https://www.googleapis.com/auth/drive.file https://www.googleapis.com/auth/userinfo.email https://www.googleapis.com/auth/userinfo.profile"

    fun getAccessToken(context: Context, account: Account): String {
        return GoogleAuthUtil.getToken(context, account, SCOPE)
    }

    private fun getRequest(url: String, token: String): JSONObject {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: "{}"
            if (!response.isSuccessful) {
                throw Exception("HTTP Error ${response.code}: $body")
            }
            return JSONObject(body)
        }
    }

    private fun getOrCreateFolder(token: String, onProgress: (String) -> Unit): String {
        onProgress("Searching for '$FOLDER_NAME' folder...")
        val searchUrl = "https://www.googleapis.com/drive/v3/files?q=" +
                java.net.URLEncoder.encode("mimeType = 'application/vnd.google-apps.folder' and name = '$FOLDER_NAME' and trashed = false", "UTF-8") +
                "&fields=files(id)"
        val searchResult = getRequest(searchUrl, token)
        val files = searchResult.optJSONArray("files")
        if (files != null && files.length() > 0) {
            val folderId = files.getJSONObject(0).getString("id")
            onProgress("Found existing '$FOLDER_NAME' folder.")
            return folderId
        }

        onProgress("Creating dedicated '$FOLDER_NAME' folder in Google Drive...")
        val createUrl = "https://www.googleapis.com/drive/v3/files"
        val json = JSONObject().apply {
            put("name", FOLDER_NAME)
            put("mimeType", "application/vnd.google-apps.folder")
        }
        val requestBody = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(createUrl)
            .header("Authorization", "Bearer $token")
            .post(requestBody)
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: "{}"
            if (!response.isSuccessful) {
                throw Exception("Failed to create folder: ${response.code} $body")
            }
            val created = JSONObject(body)
            val folderId = created.getString("id")
            onProgress("Created folder successfully.")
            return folderId
        }
    }

    private fun findBackupFileId(token: String, folderId: String): String? {
        val searchUrl = "https://www.googleapis.com/drive/v3/files?q=" +
                java.net.URLEncoder.encode("'$folderId' in parents and name = '$BACKUP_FILE_NAME' and trashed = false", "UTF-8") +
                "&fields=files(id)"
        val searchResult = getRequest(searchUrl, token)
        val files = searchResult.optJSONArray("files")
        if (files != null && files.length() > 0) {
            return files.getJSONObject(0).getString("id")
        }
        return null
    }

    suspend fun uploadBackup(
        context: Context,
        account: Account,
        onProgress: (String, Int) -> Unit
    ): Result<Long> {
        return kotlin.runCatching {
            val token = getAccessToken(context, account)

            onProgress("Preparing local backup archive...", 10)
            val tempZipFile = File(context.cacheDir, "temp_backup_plain.zip")
            if (tempZipFile.exists()) tempZipFile.delete()

            val zipSuccess = BackupHelper.createBackupZip(context, tempZipFile)
            if (!zipSuccess || !tempZipFile.exists()) {
                throw Exception("Failed to create local zip backup")
            }

            onProgress("Encrypting backup package (AES-256)...", 30)
            val encryptedFile = File(context.cacheDir, "temp_backup_encrypted.enc")
            if (encryptedFile.exists()) encryptedFile.delete()

            BackupHelper.EncryptionHelper.encrypt(tempZipFile, encryptedFile)
            tempZipFile.delete() // clean up plain zip immediately

            if (!encryptedFile.exists()) {
                throw Exception("Encryption failed")
            }

            val folderId = getOrCreateFolder(token) { status ->
                onProgress(status, 45)
            }

            onProgress("Checking for existing cloud backup...", 60)
            val existingFileId = findBackupFileId(token, folderId)

            onProgress("Uploading encrypted backup to Google Drive...", 80)
            val fileRequestBody = encryptedFile.asRequestBody("application/octet-stream".toMediaType())

            if (existingFileId != null) {
                val updateUrl = "https://www.googleapis.com/upload/drive/v3/files/$existingFileId?uploadType=media"
                val request = Request.Builder()
                    .url(updateUrl)
                    .header("Authorization", "Bearer $token")
                    .patch(fileRequestBody)
                    .build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: "{}"
                    if (!response.isSuccessful) {
                        throw Exception("Failed to update cloud backup: ${response.code} $body")
                    }
                }
            } else {
                val uploadUrl = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
                val metadata = JSONObject().apply {
                    put("name", BACKUP_FILE_NAME)
                    put("parents", JSONArray().put(folderId))
                }

                val multipartBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addPart(
                        MultipartBody.Part.create(
                            metadata.toString().toRequestBody("application/json".toMediaType())
                        )
                    )
                    .addPart(
                        MultipartBody.Part.createFormData(
                            "media",
                            BACKUP_FILE_NAME,
                            fileRequestBody
                        )
                    )
                    .build()

                val request = Request.Builder()
                    .url(uploadUrl)
                    .header("Authorization", "Bearer $token")
                    .post(multipartBody)
                    .build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: "{}"
                    if (!response.isSuccessful) {
                        throw Exception("Failed to create cloud backup: ${response.code} $body")
                    }
                }
            }

            encryptedFile.delete()
            onProgress("Sync complete! Backup uploaded successfully.", 100)
            System.currentTimeMillis()
        }
    }

    suspend fun downloadAndRestoreBackup(
        context: Context,
        account: Account,
        onProgress: (String, Int) -> Unit
    ): Result<Boolean> {
        return kotlin.runCatching {
            val token = getAccessToken(context, account)

            val folderId = getOrCreateFolder(token) { status ->
                onProgress(status, 20)
            }

            onProgress("Locating backup file on Google Drive...", 40)
            val fileId = findBackupFileId(token, folderId)
                ?: throw Exception("No backup file found in Google Drive folder.")

            onProgress("Downloading encrypted backup...", 60)
            val downloadUrl = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
            val request = Request.Builder()
                .url(downloadUrl)
                .header("Authorization", "Bearer $token")
                .build()

            val tempEncryptedFile = File(context.cacheDir, "temp_restore_encrypted.enc")
            if (tempEncryptedFile.exists()) tempEncryptedFile.delete()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    throw Exception("Failed to download backup: ${response.code} $body")
                }
                response.body?.byteStream()?.use { input ->
                    FileOutputStream(tempEncryptedFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }

            if (!tempEncryptedFile.exists() || tempEncryptedFile.length() == 0L) {
                throw Exception("Failed to download backup file")
            }

            onProgress("Decrypting backup package...", 85)
            val tempZipFile = File(context.cacheDir, "temp_restore_plain.zip")
            if (tempZipFile.exists()) tempZipFile.delete()

            BackupHelper.EncryptionHelper.decrypt(tempEncryptedFile, tempZipFile)
            tempEncryptedFile.delete()

            if (!tempZipFile.exists() || tempZipFile.length() == 0L) {
                throw Exception("Decryption failed. File might be corrupted.")
            }

            onProgress("Restoring application database and files...", 95)
            val restoreSuccess = BackupHelper.restoreBackupZip(context, tempZipFile)
            tempZipFile.delete()

            if (!restoreSuccess) {
                throw Exception("Restore extraction failed.")
            }

            onProgress("Restore successfully completed!", 100)
            true
        }
    }
}
