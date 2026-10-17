package com.example.blockblastbot.input
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PointF
import android.view.accessibility.AccessibilityEvent
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
class TouchService : AccessibilityService() {
    companion object { var instance: TouchService? = null; private set }
    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() { instance = null; super.onDestroy() }
    
    suspend fun performPieceDrag(start: PointF, target: PointF): Boolean = suspendCancellableCoroutine { cont ->
        val path = Path().apply { 
            moveTo(start.x, start.y)
            lineTo(start.x, start.y) // Зажим (ждем 200мс)
            lineTo(target.x, target.y) // Тащим (ждем 800мс)
            lineTo(target.x, target.y) // Отпускаем (ждем 200мс)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 1200L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(g: GestureDescription?) { if (cont.isActive) cont.resume(true) }
            override fun onCancelled(g: GestureDescription?) { if (cont.isActive) cont.resume(false) }
        }, null)
    }
}
