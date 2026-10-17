package com.example.blockblastbot.vision
import android.graphics.Color
import android.graphics.PointF
import android.media.Image
import com.example.blockblastbot.core.BotConfig
import com.example.blockblastbot.solver.Piece
import java.nio.ByteBuffer
import kotlin.math.abs
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
        fun isBg(c: IntArray): Boolean = abs(c[0] - bg[0]) < 25 && abs(c[1] - bg[1]) < 25 && abs(c[2] - bg[2]) < 25

        var left = 0
        for (x in 0 until w/2) { if (!isBg(getRGB(x, h/2))) { left = x; break } }
        var right = w - 1
        for (x in w-1 downTo w/2) { if (!isBg(getRGB(x, h/2))) { right = x; break } }
        var top = 0
        for (y in h/3 downTo 0) { if (!isBg(getRGB(w/2, y))) { top = y; break } }

        val cellSize = (right - left) / 8f
        if (cellSize < w * 0.05f) return false

        var minTrayY = h; var maxTrayY = 0
        for (y in (h*7/10) until h) {
            for (x in 0 until w step 4) {
                val c = getRGB(x, y)
                val hsv = FloatArray(3); Color.RGBToHSV(c[0], c[1], c[2], hsv)
                if (hsv[2] > 0.60f && hsv[1] > 0.35f) {
                    if (y < minTrayY) minTrayY = y
                    if (y > maxTrayY) maxTrayY = y
                }
            }
        }
        if (minTrayY < h) config.trayCenterYRatio = ((minTrayY + maxTrayY) / 2f) / h
        
        config.boardSideMarginRatio = left / w.toFloat()
        config.boardTopRatio = top / h.toFloat()
        config.dragOffsetY = 0f // Сбрасываем сдвиг
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
        val trayY = h * config.trayCenterYRatio
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
        val hsv = FloatArray(3)
        Color.RGBToHSV(buf.get(off).toInt() and 0xFF, buf.get(off+1).toInt() and 0xFF, buf.get(off+2).toInt() and 0xFF, hsv)
        return hsv[2] > 0.70f && hsv[1] > 0.40f
    }
}
