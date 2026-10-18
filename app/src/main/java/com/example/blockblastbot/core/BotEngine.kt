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
            catch (e: Exception) { _status.value = "Сбой: ${e.message?.take(30)}" }
        }
    }
    fun pause() { job?.cancel(); job = null; _status.value = "Пауза" }
    fun stop() { job?.cancel(); job = null; _status.value = "Стоп" }
    fun destroy() { stop(); scope.cancel() }
    
    private suspend fun runLoop() {
        while (currentCoroutineContext().isActive) {
            try {
                val ts = TouchService.instance
                if (ts == null) { _status.value = "Нет TouchService"; delay(1000L); continue }
                
                val reader = readerProvider() ?: run { delay(500L); continue }
                val img = reader.acquireLatestImage() ?: run { delay(500L); continue }
                
                val bitmap = imageMutex.withLock {
                    try { 
                        val bmp = imageToBitmap(img)
                        Bitmap.createScaledBitmap(bmp, bmp.width / 2, bmp.height / 2, true)
                    } finally { img.close() }
                }
                
                _status.value = "ИИ анализирует..."
                val result = ai.analyzeBoard(bitmap)
                
                if (result.isFailure) {
                    _status.value = "Ошибка: ${result.exceptionOrNull()?.message}"
                    delay(5000L)
                    continue
                }
                
                val move = result.getOrNull()!!
                _status.value = "Ставлю..."
                
                ts.performPieceDrag(PointF(move.grabX * 2f, move.grabY * 2f), PointF(move.dropX * 2f, move.dropY * 2f))
                delay(3000L) 
            } catch (e: Exception) {
                _status.value = "Цикл: ${e.message?.take(20)}"
                delay(3000L)
            }
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
