package com.example.blockblastbot.vision
import android.graphics.Color
import android.graphics.PointF
import android.media.Image
import com.example.blockblastbot.core.BotConfig
import com.example.blockblastbot.solver.Piece
import java.nio.ByteBuffer
data class RecognizedFrame(val board: ULong, val pieces: List<Piece>, val boardLeft: Float, val boardTop: Float, val cellSize: Float)
class VisionRecognizer(private val config: BotConfig) {
    fun autoDetect(image: Image): Boolean {
        val plane = image.planes[0]; val buf = plane.buffer
        val w = image.width; val h = image.height
        val pStride = plane.pixelStride; val rStride = plane.rowStride
        fun getRGB(x: Int, y: Int): IntArray {
            val off = y * rStride + x * pStride
            if (off + 2 >= buf.limit()) return intArrayOf(0, 0, 0)
            return intArrayOf(buf.get(off).toInt() and 0xFF, buf.get(off + 1).toInt() and 0xFF, buf.get(off + 2).toInt() and 0xFF)
        }
        val bg = getRGB(10, h/2) 
        fun isBg(c: IntArray): Boolean = kotlin.math.abs(c[0] - bg[0]) < 25 && kotlin.math.abs(c[1] - bg[1]) < 25 && kotlin.math.abs(c[2] - bg[2]) < 25
        var left = 0
        for (x in 0 until w/2) { if (!isBg(getRGB(x, h/2))) { left = x; break } }
        var right = w - 1
        for (x in w-1 downTo w/2) { if (!isBg(getRGB(x, h/2))) { right = x; break } }
        var top = 0
        for (y in h/3 downTo 0) { if (!isBg(getRGB(w/2, y))) { top = y; break } }
        val cellSize = (right - left) / 8f
        if (cellSize < w * 0.05f) return false
        config.boardSideMarginRatio = left / w.toFloat()
        config.boardTopRatio = top / h.toFloat()
        return true
    }
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
        
        // Лоток всегда находится под полем. Берем низ поля и отступаем 1.2 клетки вниз.
        val bBottom = bTop + (8f * cellSize)
        val trayY = bBottom + (cellSize * 1.2f)
        val trayCell = cellSize * 0.6f
        val pieces = mutableListOf<Piece>()
        
        // Ищем фигуры в трех колонках (слева, центр, справа), отталкиваясь от краев поля, а не экрана
        val slotXs = listOf(bLeft + cellSize * 1.5f, bLeft + cellSize * 4f, bLeft + cellSize * 6.5f)
        
        for ((idx, sx) in slotXs.withIndex()) {
            val raw = mutableListOf<Pair<Int, Int>>()
            for (dr in -3..3) for (dc in -3..3) {
                val px = (sx + dc * trayCell).toInt(); val py = (trayY + dr * trayCell).toInt()
                if (isBlock(buf, px, py, w, h, rStride, pStride)) raw.add(Pair(dr, dc))
            }
            if (raw.isNotEmpty()) {
                val minR = raw.minOf { it.first }; val minC = raw.minOf { it.second }
                val maxR = raw.maxOf { it.first }; val maxC = raw.maxOf { it.second }
                val midR = (minR + maxR) / 2f
                val midC = (minC + maxC) / 2f
                
                val grabCell = raw.minByOrNull { (it.first - midR) * (it.first - midR) + (it.second - midC) * (it.second - midC) }!!
                val grabX = sx + grabCell.second * trayCell
                val grabY = trayY + grabCell.first * trayCell
                
                pieces.add(Piece(
                    slotIndex = idx, 
                    cells = raw.map { Pair(it.first - minR, it.second - minC) }, 
                    trayOriginScreen = PointF(grabX, grabY),
                    grabOffsetRow = grabCell.first - minR,
                    grabOffsetCol = grabCell.second - minC
                ))
            }
        }
        return RecognizedFrame(boardMask, pieces, bLeft, bTop, cellSize)
    }
    private fun isBlock(buf: ByteBuffer, x: Int, y: Int, w: Int, h: Int, rStride: Int, pStride: Int): Boolean {
        val cx = x.coerceIn(1, w - 2); val cy = y.coerceIn(1, h - 2)
        val off = cy * rStride + cx * pStride
        if (off + 2 >= buf.limit()) return false
        val hsv = FloatArray(3)
        Color.RGBToHSV(buf.get(off).toInt() and 0xFF, buf.get(off+1).toInt() and 0xFF, buf.get(off+2).toInt() and 0xFF, hsv)
        return hsv[2] > 0.70f && hsv[1] > 0.40f
    }
}
