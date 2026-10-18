package com.example.blockblastbot.core
import android.graphics.Bitmap
import android.graphics.PointF
import android.media.Image
import android.media.ImageReader
import com.example.blockblastbot.input.TouchService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.ByteBuffer

class BotEngine(private val readerProvider: () -> ImageReader?) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val ai = GeminiAi()
    private var job: Job? = null
    private val imageMutex = Mutex()
    private val _status = MutableStateFlow("ИИ Готов"); val statusFlow: StateFlow<String> = _status
    val isRunning: Boolean get() = job?.isActive == true

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            try { runLoop() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _status.value = "Ошибка: " + (e.message ?: "unknown") }
        }
    }
    fun pause() { job?.cancel(); job = null; _status.value = "Пауза" }
    fun stop() { job?.cancel(); job = null; _status.value = "Стоп" }
    fun destroy() { stop(); scope.cancel() }
    
    private suspend fun runLoop() {
        while (currentCoroutineContext().isActive) {
            val ts = TouchService.instance
            if (ts == null) { _status.value = "Нет TouchService!"; delay(1000L); continue }
            
            val bitmap = imageMutex.withLock {
                val reader = readerProvider() ?: return@withLock null
                val img = reader.acquireLatestImage() ?: return@withLock null
                try { imageToBitmap(img) } finally { img.close() }
            }
            
            if (bitmap == null) { delay(500L); continue }
            
            _status.value = "ИИ анализирует..."
            val move = ai.analyzeBoard(bitmap)
            
            if (move == null) {
                _status.value = "Ошибка ИИ / сети"
                delay(3000L)
                continue
            }
            
            _status.value = "Ставлю блок..."
            ts.performPieceDrag(PointF(move.grabX, move.grabY), PointF(move.dropX, move.dropY))
            
            delay(2500L) 
        }
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val buffer: ByteBuffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width
        val bitmap = Bitmap.createBitmap(image.width + rowPadding / pixelStride, image.height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)
        return Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
    }
}
