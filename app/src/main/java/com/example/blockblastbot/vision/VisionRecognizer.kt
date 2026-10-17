package com.example.blockblastbot.vision
import android.graphics.Color
import android.graphics.PointF
import android.media.Image
import com.example.blockblastbot.core.BotConfig
import com.example.blockblastbot.solver.Piece
import java.nio.ByteBuffer
data class RecognizedFrame(val board: ULong, val pieces: List<Piece>, val boardLeft: Float, val boardTop: Float, val cellSize: Float)

class VisionRecognizer(private val config: BotConfig) {
    fun autoDetect(image: Image): Boolean = true 
    
    fun analyzeImage(image: Image): RecognizedFrame? {
        val plane = image.planes[0]; val buf = plane.buffer
        val w = image.width; val h = image.height
        val pStride = plane.pixelStride; val rStride = plane.rowStride
        
        // ЖЕСТКАЯ КАЛИБРОВКА ПОД ЭКРАН 1080x2280
        val scaleX = w / 1080f
        val scaleY = h / 2280f
        
        val bLeft = 43.2f * scaleX
        val bTop = 501.6f * scaleY
        val cellSize = 124.2f * scaleX
        
        var boardMask = 0UL
        for (r in 0..7) for (c in 0..7) {
            val cx = (bLeft + (c + 0.5f) * cellSize).toInt()
            val cy = (bTop + (r + 0.5f) * cellSize).toInt()
            if (isBlock(buf, cx, cy, w, h, rStride, pStride)) boardMask = boardMask or (1UL shl (r * 8 + c))
        }
        
        val pieces = mutableListOf<Piece>()
        // ЖЕСТКИЕ КООРДИНАТЫ ЛОТКА
        val trayY = 1800f * scaleY
        val slotXs = listOf(270f * scaleX, 540f * scaleX, 810f * scaleX)
        
        for ((idx, sx) in slotXs.withIndex()) {
            val raw = mutableListOf<Pair<Int, Int>>()
            for (dr in -2..2) for (dc in -2..2) {
                val px = (sx + dc * cellSize).toInt()
                val py = (trayY + dr * cellSize).toInt()
                if (isBlock(buf, px, py, w, h, rStride, pStride)) raw.add(Pair(dr, dc))
            }
            if (raw.isNotEmpty()) {
                val minR = raw.minOf { it.first }; val minC = raw.minOf { it.second }
                val maxR = raw.maxOf { it.first }; val maxC = raw.maxOf { it.second }
                val midR = (minR + maxR) / 2f
                val midC = (minC + maxC) / 2f
                
                val grabCell = raw.minByOrNull { (it.first - midR) * (it.first - midR) + (it.second - midC) * (it.second - midC) }!!
                
                pieces.add(Piece(
                    slotIndex = idx, 
                    cells = raw.map { Pair(it.first - minR, it.second - minC) }, 
                    trayOriginScreen = PointF(sx + grabCell.second * cellSize, trayY + grabCell.first * cellSize),
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
