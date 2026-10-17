package com.example.blockblastbot.core
import android.graphics.PointF
import android.media.ImageReader
import com.example.blockblastbot.input.TouchService
import com.example.blockblastbot.solver.BitboardSolver
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
    private val _status = MutableStateFlow("Остановлен"); val statusFlow: StateFlow<String> = _status
    val isRunning: Boolean get() = job?.isActive == true
    
    fun autoDetect() { _status.value = "Настройки уже идеальны" }

    fun getDebugInfo(onResult: (String) -> Unit) {
        scope.launch {
            val result = try {
                delay(300)
                imageMutex.withLock {
                    val reader = readerProvider()
                    if (reader == null) { "Нет доступа к экрану" }
                    else {
                        val img = reader.acquireLatestImage()
                        if (img == null) "Нет кадра"
                        else {
                            try {
                                val rec = recognizer.analyzeImage(img)
                                if (rec == null) "Ошибка зрения"
                                else {
                                    val sb = StringBuilder()
                                    sb.append("Screen: ${img.width}x${img.height}\n")
                                    sb.append("MarginRatio: ${config.boardSideMarginRatio}\n")
                                    sb.append("TopRatio: ${config.boardTopRatio}\n")
                                    sb.append("BoardLeft: ${rec.boardLeft}\n")
                                    sb.append("BoardTop: ${rec.boardTop}\n")
                                    sb.append("CellSize: ${rec.cellSize}\n")
                                    sb.append("BoardMask: ${rec.board}\n")
                                    sb.append("Pieces: ${rec.pieces.size}\n")
                                    for (p in rec.pieces) {
                                        sb.append("- Slot ${p.slotIndex}: GrabX=${p.trayOriginScreen.x}, GrabY=${p.trayOriginScreen.y}, W=${p.width}, H=${p.height}\n")
                                    }
                                    sb.toString()
                                }
                            } finally { img.close() }
                        }
                    }
                }
            } catch (e: Exception) { "Ошибка: ${e.message}" }
            withContext(Dispatchers.Main) { onResult(result) }
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
                try { recognizer.analyzeImage(img) } finally { img.close() }
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
