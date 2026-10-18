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
    private val apiKey = BuildConfig.GEMINI_API_KEY 
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun analyzeBoard(bitmap: Bitmap): AiMove? = withContext(Dispatchers.IO) {
        try {
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
            val base64Image = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)

            val prompt = """
                Ты профессиональный игрок в Block Blast. 
                Твоя задача - проанализировать скриншот игры и найти лучший ход.
                Внизу экрана есть 3 фигуры. На доске 8x8 есть свободные места.
                Выбери ОДНУ фигуру и найди для неё лучшую клетку.
                
                Верни СТРОГО JSON без лишних слов, без форматирования markdown:
                {"grabX": 150, "grabY": 1800, "dropX": 300, "dropY": 700}
                
                Где:
                grabX, grabY - координаты (в пикселях) центра фигуры, которую ты выбрал.
                dropX, dropY - координаты (в пикселях) центра клетки на доске, куда нужно поставить фигуру.
                
                ВАЖНО: Изображение имеет размер ${bitmap.width}x${bitmap.height} пикселей. Учитывай этот масштаб при расчете координат!
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

            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return@withContext null

            val jsonResponse = JSONObject(responseBody)
            val text = jsonResponse.getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
            
            val cleanJson = text.replace("```json", "").replace("```", "").trim()
            val moveJson = JSONObject(cleanJson)

            AiMove(
                moveJson.getDouble("grabX").toFloat(),
                moveJson.getDouble("grabY").toFloat(),
                moveJson.getDouble("dropX").toFloat(),
                moveJson.getDouble("dropY").toFloat()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
