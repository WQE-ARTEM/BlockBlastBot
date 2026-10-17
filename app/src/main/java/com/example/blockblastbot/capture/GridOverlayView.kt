package com.example.blockblastbot.capture
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
class GridOverlayView(context: Context) : View(context) {
    private val paint = Paint().apply { color = Color.RED; style = Paint.Style.STROKE; strokeWidth = 4f; isAntiAlias = true }
    var bLeft = 0f; var bTop = 0f; var cellSize = 0f
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (cellSize <= 0f) return
        for (i in 0..8) {
            canvas.drawLine(bLeft, bTop + i * cellSize, bLeft + 8 * cellSize, bTop + i * cellSize, paint)
            canvas.drawLine(bLeft + i * cellSize, bTop, bLeft + i * cellSize, bTop + 8 * cellSize, paint)
        }
        // Рисуем зеленые точки ТАМ ЖЕ, где бот ищет фигуры
        paint.color = Color.GREEN; paint.style = Paint.Style.FILL
        val trayY = bTop + (8f * cellSize) + (cellSize * 2.0f)
        canvas.drawCircle(bLeft + cellSize * 1.5f, trayY, 20f, paint)
        canvas.drawCircle(bLeft + cellSize * 4.2f, trayY, 20f, paint)
        canvas.drawCircle(bLeft + cellSize * 6.9f, trayY, 20f, paint)
    }
}
