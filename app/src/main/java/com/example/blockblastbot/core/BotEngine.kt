package com.example.blockblastbot.core
import android.graphics.PointF
import android.media.ImageReader
import com.example.blockblastbot.input.TouchService
import com.example.blockblastbot.solver.BitboardSolver
import com.example.blockblastbot.vision.RecognizedFrame
import com.example.blockblastbot.vision.VisionRecognizer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
class BotEngine(private val config: BotConfig, private val readerProvider: () -> ImageReader?) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val recognizer = VisionRecognizer(config)
    private var job: Job? = null
    private val imageMutex = Mutex()
    private val _status = MutableStateFlow("Готов"); val statusFlow: StateFlow<String> = _status
    private var lastFrame: RecognizedFrame? = null
    val isRunning: Boolean get() = job?.isActive == true
    
    fun getLastFrame() = lastFrame
    fun shift(dx: Float, dy: Float) {
        config.offsetX += dx
        config.offsetY += dy
        scope.launch {
            imageMutex.withLock {
                val reader = readerProvider() ?: return@withLock
                val img = reader.acquireLatestImage() ?: return@withLock
                try { lastFrame = recognizer.analyzeImage(img) } finally { img.close() }
            }
        }
    }
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
            val rec = imageMutex.withLock {
                val reader = readerProvider() ?: return@withLock null
                val img = reader.acquireLatestImage() ?: return@withLock null
                try { recognizer.analyzeImage(img).also { lastFrame = it } } finally { img.close() }
            }
            if (rec == null || rec.pieces.isEmpty()) { _status.value = "Ищу фигуры..."; delay(300L); continue }
            val sol = BitboardSolver.findBestSequence(rec.board, rec.pieces, config.strategy)
            if (sol == null || sol.moves.isEmpty()) { _status.value = "Нет решений"; pause(); break }
            val m = sol.moves.first()
            val target = PointF(
                rec.boardLeft + (m.targetCol + m.piece.grabOffsetCol + 0.5f) * rec.cellSize,
                rec.boardTop + (m.targetRow + m.piece.grabOffsetRow + 0.5f) * rec.cellSize
            )
            _status.value = "Делаю ход..."
            ts.performPieceDrag(m.piece.trayOriginScreen, target)
            delay(1500L)
        }
    }
}
