package com.example.blockblastbot.core
import android.graphics.PointF
import android.media.ImageReader
import com.example.blockblastbot.input.TouchService
import com.example.blockblastbot.solver.BitboardSolver
import com.example.blockblastbot.vision.VisionRecognizer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
class BotEngine(private val config: BotConfig, private val readerProvider: () -> ImageReader?) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val recognizer = VisionRecognizer(config)
    private var job: Job? = null
    private val _status = MutableStateFlow("Остановлен"); val statusFlow: StateFlow<String> = _status
    val isRunning: Boolean get() = job?.isActive == true
    
    fun autoDetect() {
        scope.launch {
            _status.value = "Сканирую экран..."
            val reader = readerProvider() ?: return@launch
            val img = reader.acquireLatestImage() ?: return@launch
            val success = try { recognizer.autoDetect(img) } finally { img.close() }
            if (success) {
                _status.value = "Автонастройка OK!"
            } else {
                _status.value = "Ошибка настройки"
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
            val ts = TouchService.instance ?: run { _status.value = "Нет TouchService!"; delay(1000L); return@run }
            val reader = readerProvider() ?: run { delay(200L); return@run }
            val img = reader.acquireLatestImage() ?: run { delay(200L); return@run }
            val rec = try { recognizer.analyzeImage(img) } finally { img.close() }
            
            if (rec == null || rec.pieces.isEmpty()) { _status.value = "Ищу фигуры..."; delay(300L); continue }
            
            val sol = BitboardSolver.findBestSequence(rec.board, rec.pieces, config.strategy)
            if (sol == null || sol.moves.isEmpty()) { _status.value = "Нет решений"; pause(); break }
            
            val m = sol.moves.first()
            val target = PointF(rec.boardLeft + (m.targetCol + m.piece.width / 2f) * rec.cellSize, rec.boardTop + (m.targetRow + m.piece.height / 2f) * rec.cellSize)
            _status.value = "Делаю ход..."
            ts.performPieceDrag(m.piece.trayOriginScreen, target)
            delay(1200L) 
        }
    }
}
