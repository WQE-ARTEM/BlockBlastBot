package com.example.blockblastbot.core
import android.graphics.PointF
import android.media.ImageReader
import com.example.blockblastbot.input.TouchService
import com.example.blockblastbot.solver.BitboardSolver
import com.example.blockblastbot.vision.VisionRecognizer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.random.Random
class BotEngine(private val config: BotConfig, private val readerProvider: () -> ImageReader?) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val recognizer = VisionRecognizer(config)
    private var job: Job? = null
    private val _status = MutableStateFlow("Остановлен"); val statusFlow: StateFlow<String> = _status
    val isRunning: Boolean get() = job?.isActive == true
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            try { runLoop() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _status.value = "Ошибка: " + (e.message ?: e.javaClass.simpleName) }
        }
    }
    fun pause() { job?.cancel(); job = null; _status.value = "Пауза" }
    fun stop() { job?.cancel(); job = null; _status.value = "Стоп" }
    fun destroy() { stop(); scope.cancel() }
    private suspend fun runLoop() {
        var lastBoard = 0UL
        while (currentCoroutineContext().isActive) {
            val ts = TouchService.instance
            if (ts == null) { _status.value = "Нет TouchService!"; delay(1000L); continue }
            val reader = readerProvider()
            if (reader == null) { _status.value = "Нет доступа к экрану"; delay(200L); continue }
            val img = reader.acquireLatestImage()
            if (img == null) { delay(200L); continue }
            val rec = try { recognizer.analyzeImage(img) } finally { img.close() }
            
            if (rec == null) { _status.value = "Ошибка зрения"; delay(300L); continue }
            if (rec.pieces.isEmpty()) { _status.value = "Фигур не видно (0)"; delay(300L); continue }
            
            val sol = BitboardSolver.findBestSequence(rec.board, rec.pieces, config.strategy)
            if (sol == null || sol.moves.isEmpty()) { 
                _status.value = "Нет решений"; pause(); break 
            }
            
            val m = sol.moves.first()
            val target = PointF(
                rec.boardLeft + (m.targetCol + m.piece.width / 2f) * rec.cellSize,
                rec.boardTop + (m.targetRow + m.piece.height / 2f) * rec.cellSize
            )
            
            _status.value = "Делаю ход..."
            ts.performPieceDrag(m.piece.trayOriginScreen, target)
            
            // Даем время игре прогрузить анимацию и проверить, изменилось ли поле
            delay(1200L) 
        }
    }
}
