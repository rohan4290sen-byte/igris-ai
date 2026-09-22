package com.igris.ai

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class IgrisBrain(private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    suspend fun processCommand(userQuery: String): IgrisResponse = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext IgrisResponse("Boss, Gemini API Key missing hai. Settings me daal dijiye.")
        }

        val prompt = buildPrompt(userQuery)
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"

        val jsonPayload = """
            {
              "contents": [{
                "parts": [{"text": ${gson.toJson(prompt)}}]
              }]
            }
        """.trimIndent()

        val requestBody = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        try {
            val response = client.newCall(request).execute()
            val responseString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext IgrisResponse("Boss, AI server se connect nahi ho pa raha.")
            }

            val jsonObject = gson.fromJson(responseString, JsonObject::class.java)
            val candidates = jsonObject.getAsJsonArray("candidates")
            val firstCandidate = candidates?.get(0)?.asJsonObject
            val content = firstCandidate?.getAsJsonObject("content")
            val parts = content?.getAsJsonArray("parts")
            val text = parts?.get(0)?.asJsonObject?.get("text")?.asString ?: "At your command, Boss."

            parseResponse(text)
        } catch (e: Exception) {
            IgrisResponse("Network issue hai, Boss.")
        }
    }

    private fun buildPrompt(userQuery: String): String {
        return """
            You are IGRIS, an ultra-loyal, badass Shadow Knight AI Assistant for your user (Boss).
            Tone: Respectful, deep, masculine, calm badass.
            Language: Hinglish (Hindi + English natural mix).
            Keep spoken reply concise (1-2 sentences).
            
            When executing an action, YOU MUST append exactly one action tag at the very end of your response:
            - For phone calls: [ACTION:CALL|<phone_number_or_name>]
            - For opening ANY app: [ACTION:APP|<app_name>]
            - For YouTube playing/search: [ACTION:YOUTUBE|<query>]
            - For flashlight: [ACTION:TORCH|ON] or [ACTION:TORCH|OFF]
            - For battery level: [ACTION:BATTERY]
            - For email: [ACTION:EMAIL|<recipient>|<subject>|<body>]
            
            User says: "$userQuery"
        """.trimIndent()
    }

    private fun parseResponse(raw: String): IgrisResponse {
        val tagIndex = raw.indexOf("[ACTION:")
        if (tagIndex != -1) {
            val spokenText = raw.substring(0, tagIndex).trim()
            val endTag = raw.indexOf("]", tagIndex)
            val tagContent = if (endTag != -1) raw.substring(tagIndex + 8, endTag) else ""
            val parts = tagContent.split("|")
            val actionType = parts.getOrNull(0) ?: ""
            val param1 = parts.getOrNull(1) ?: ""
            val param2 = parts.getOrNull(2) ?: ""
            val param3 = parts.getOrNull(3) ?: ""

            return IgrisResponse(
                speechText = spokenText,
                actionType = actionType,
                param1 = param1,
                param2 = param2,
                param3 = param3
            )
        }
        return IgrisResponse(speechText = raw.trim())
    }
}

data class IgrisResponse(
    val speechText: String,
    val actionType: String? = null,
    val param1: String = "",
    val param2: String = "",
    val param3: String = ""
)
