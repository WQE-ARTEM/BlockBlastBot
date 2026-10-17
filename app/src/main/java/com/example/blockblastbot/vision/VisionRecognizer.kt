package com.example.blockblastbot.vision
import android.graphics.Color
import android.graphics.PointF
import android.media.Image
import com.example.blockblastbot.core.BotConfig
import com.example.blockblastbot.solver.Piece
import java.nio.ByteBuffer
data class RecognizedFrame(val board: ULong, val pieces: List<Piece>, val boardLeft: Float, val boardTop: Float, val cellSize: Float)
class VisionRecognizer(private val config: BotConfig) {
    fun analyzeImage(image: Image): RecognizedFrame? {
        val plane = image.planes[0]; val buf = plane.buffer
        val w = image.width; val h = image.height
        val pStride = plane.pixelStride; val rStride = plane.rowStride
        val bLeft = w * config.boardSideMarginRatio
        val bRight = w * (1f - config.boardSideMarginRatio)
        val cellSize = (bRight - bLeft) / 8f
        val bTop = h * config.boardTopRatio
        var boardMask = 0UL
        for (r in 0..7) for (c in 0..7) {
            val cx = (bLeft + (c + 0.5f) * cellSize).toInt()
            val cy = (bTop + (r + 0.5f) * cellSize).toInt()
            if (isBlock(buf, cx, cy, w, h, rStride, pStride)) boardMask = boardMask or (1UL shl (r * 8 + c))
        }
        
        val trayY = bTop + (8.9f * cellSize)
        val trayCell = cellSize * 0.6f
        val pieces = mutableListOf<Piece>()
        
        for ((idx, sx) in listOf(w * 0.18f, w * 0.50f, w * 0.82f).withIndex()) {
            val raw = mutableListOf<Pair<Int, Int>>()
            for (dr in -2..2) for (dc in -2..2) {
                val px = (sx + dc * trayCell).toInt(); val py = (trayY + dr * trayCell).toInt()
                if (isBlock(buf, px, py, w, h, rStride, pStride)) raw.add(Pair(dr, dc))
            }
            if (raw.isNotEmpty()) {
                val minR = raw.minOf { it.first }; val minC = raw.minOf { it.second }
                val maxR = raw.maxOf { it.first }; val maxC = raw.maxOf { it.second }
                pieces.add(Piece(idx, raw.map { Pair(it.first - minR, it.second - minC) }, PointF(sx + ((minC + maxC)/2f)*trayCell, trayY + ((minR + maxR)/2f)*trayCell)))
            }
        }
        return RecognizedFrame(boardMask, pieces, bLeft, bTop, cellSize)
    }
    private fun isBlock(buf: ByteBuffer, x: Int, y: Int, w: Int, h: Int, rStride: Int, pStride: Int): Boolean {
        val cx = x.coerceIn(1, w - 2); val cy = y.coerceIn(1, h - 2)
        val off = cy * rStride + cx * pStride
        if (off + 2 >= buf.limit()) return false
        val red = buf.get(off).toInt() and 0xFF
        val green = buf.get(off + 1).toInt() and 0xFF
        val blue = buf.get(off + 2).toInt() and 0xFF
        val hsv = FloatArray(3)
        Color.RGBToHSV(red, green, blue, hsv)
        // УВЕЛИЧИЛИ ЯРКОСТЬ (V) до 0.70, чтобы отсечь темно-синий фон игры
        return hsv[2] > 0.70f && hsv[1] > 0.40f
    }
}
