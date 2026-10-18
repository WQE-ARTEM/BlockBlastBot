package com.example.blockblastbot.core
import android.graphics.Bitmap
import android.util.Base64
import com.example.blockblastbot.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

data class AiMove(val grabX: Float, val grabY: Float, val dropX: Float, val dropY: Float)

class GeminiAi {
    private val apiKey = BuildConfig.GEMINI_API_KEY.trim() 
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun analyzeBoard(bitmap: Bitmap): Result<AiMove> = withContext(Dispatchers.IO) {
        try {
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)
            val base64Image = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)

            val prompt = """
                Ты играешь в Block Blast. Найди фигуру внизу и клетку на доске 8x8.
                Верни СТРОГО JSON: {"grabX": 150, "grabY": 1800, "dropX": 300, "dropY": 700}
                Размер картинки: ${bitmap.width}x${bitmap.height}.
            """.trimIndent()

            val json = JSONObject().apply {
                put("contents", org.json.JSONArray().put(JSONObject().apply {
                    put("parts", org.json.JSONArray().apply {
                        put(JSONObject().put("text", prompt))
                        put(JSONObject().put("inline_data", JSONObject().apply {
                            put("mime_type", "image/jpeg")
                            put("data", base64Image)
                        }))
                    })
                }))
            }

            val url = "https://gemini-proxy.89193240154art.workers.dev/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
            val request = Request.Builder().url(url).post(json.toString().toRequestBody("application/json".toMediaType())).build()
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return@withContext Result.failure(Exception("Пустой ответ"))
            
            if (!response.isSuccessful) return@withContext Result.failure(Exception("HTTP ${response.code}"))

            val jsonResponse = JSONObject(responseBody)
            val text = jsonResponse.getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
            
            val startIdx = text.indexOf("{")
            val endIdx = text.lastIndexOf("}")
            if (startIdx == -1 || endIdx == -1) return@withContext Result.failure(Exception("Нет JSON"))
            
            val moveJson = JSONObject(text.substring(startIdx, endIdx + 1))
            Result.success(AiMove(
                moveJson.getDouble("grabX").toFloat(),
                moveJson.getDouble("grabY").toFloat(),
                moveJson.getDouble("dropX").toFloat(),
                moveJson.getDouble("dropY").toFloat()
            ))
        } catch (e: Exception) {
            Result.failure(Exception(e.javaClass.simpleName))
        }
    }
}
