package com.example.utils

import android.util.Log
import com.example.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiHelper {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private const val MODEL_NAME = "gemini-3.5-flash"
    private const val API_URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent"

    suspend fun queryGemini(prompt: String): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return "API Key is not configured. Please enter your Gemini API key in the Secrets panel in AI Studio UI to enable the AI Assistant."
        }

        try {
            val jsonPayload = buildRequestJson(prompt)
            val requestBody = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())
            
            val request = Request.Builder()
                .url("$API_URL?key=$apiKey")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.e("GeminiHelper", "API Error: ${response.code} - $responseBody")
                    val errorObj = try { JSONObject(responseBody) } catch (e: Exception) { null }
                    val message = errorObj?.getJSONArray("error")?.getJSONObject(0)?.getString("message")
                        ?: errorObj?.getJSONObject("error")?.getString("message")
                        ?: "HTTP ${response.code}"
                    return "Error from Gemini API: $message"
                }

                // Parse response
                val jsonResponse = JSONObject(responseBody)
                val candidates = jsonResponse.getJSONArray("candidates")
                if (candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val content = firstCandidate.getJSONObject("content")
                    val parts = content.getJSONArray("parts")
                    if (parts.length() > 0) {
                        return parts.getJSONObject(0).getString("text")
                    }
                }
                return "Received empty or unexpected response from AI service."
            }
        } catch (e: Exception) {
            Log.e("GeminiHelper", "Request failed", e)
            return "Connection failed: ${e.localizedMessage ?: "Unknown error"}. Check internet connection."
        }
    }

    private fun buildRequestJson(prompt: String): String {
        val escapedPrompt = escapeJson(prompt)
        return """
            {
              "contents": [
                {
                  "parts": [
                    {
                      "text": "$escapedPrompt"
                    }
                  ]
                }
              ],
              "generationConfig": {
                "temperature": 0.25,
                "topP": 0.95
              }
            }
        """.trimIndent()
    }

    private fun escapeJson(value: String): String {
        val builder = StringBuilder()
        for (element in value) {
            when (element) {
                '\\' -> builder.append("\\\\")
                '"' -> builder.append("\\\"")
                '\n' -> builder.append("\\n")
                '\r' -> builder.append("\\r")
                '\t' -> builder.append("\\t")
                else -> {
                    if (element.code < 0x20) {
                        builder.append(String.format("\\u%04x", element.code))
                    } else {
                        builder.append(element)
                    }
                }
            }
        }
        return builder.toString()
    }
}
