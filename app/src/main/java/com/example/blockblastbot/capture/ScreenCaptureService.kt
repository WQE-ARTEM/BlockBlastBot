package com.example.blockblastbot.capture
import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.*
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat
import com.example.blockblastbot.R
import com.example.blockblastbot.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
class ScreenCaptureService : Service() {
    companion object {
        const val ACTION_START = "START"
        private const val CHANNEL_ID = "bb_channel"
        private const val NOTIF_ID = 1001
    }
    private var mp: MediaProjection? = null
    private var reader: ImageReader? = null
    private var overlay: View? = null
    private var wm: WindowManager? = null
    private lateinit var engine: BotEngine
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    override fun onCreate() {
        super.onCreate()
        engine = BotEngine(BotConfig(this)) { reader }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_START) { stopSelf(); return START_NOT_STICKY }
        val code = intent.getIntExtra("code", Activity.RESULT_CANCELED)
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra("data", Intent::class.java) else @Suppress("DEPRECATION") intent.getParcelableExtra("data") as? Intent
        if (code != Activity.RESULT_OK || data == null) { stopSelf(); return START_NOT_STICKY }
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Block Blast Bot", NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle("Bot").setSmallIcon(android.R.drawable.ic_media_play).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION) else startForeground(NOTIF_ID, notification)
        
        mp = (getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).getMediaProjection(code, data)
        mp?.registerCallback(object : MediaProjection.Callback() { override fun onStop() { stopSelf() } }, Handler(Looper.getMainLooper()))
        
        val m = resources.displayMetrics
        reader = ImageReader.newInstance(m.widthPixels, m.heightPixels, PixelFormat.RGBA_8888, 2)
        mp?.createVirtualDisplay("V", m.widthPixels, m.heightPixels, m.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader?.surface, null, null)
        if (Settings.canDrawOverlays(this)) showOverlay()
        return START_NOT_STICKY
    }
    private fun showOverlay() {
        if (overlay != null) return
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val density = resources.displayMetrics.density
        val lp = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (12 * density).toInt(); y = (60 * density).toInt()
        }
        val view = LayoutInflater.from(this).inflate(R.layout.overlay_controls, null)
        val btnAuto = view.findViewById<Button>(R.id.btnOverlayAuto)
        val btnDebug = view.findViewById<Button>(R.id.btnOverlayDebug)
        val btnToggle = view.findViewById<Button>(R.id.btnOverlayToggle)
        val btnStop = view.findViewById<Button>(R.id.btnOverlayStop)
        val tvInfo = view.findViewById<TextView>(R.id.tvOverlayInfo)
        
        btnAuto.setOnClickListener { engine.autoDetect() }
        
        btnDebug.setOnClickListener {
            engine.pause() // Ставим на паузу перед снимком
            btnToggle.text = "СТАРТ"
            engine.getDebugInfo { info ->
                try {
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Bot Debug", info)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(this@ScreenCaptureService, "Скопировано!", Toast.LENGTH_SHORT).show()
                    tvInfo.text = "Скопировано!"
                } catch (e: Exception) {
                    tvInfo.text = "Ошибка буфера"
                }
            }
        }

        btnToggle.setOnClickListener {
            if (engine.isRunning) { engine.pause(); btnToggle.text = "СТАРТ" }
            else { engine.start(); btnToggle.text = "ПАУЗА" }
        }
        btnStop.setOnClickListener { engine.stop(); stopSelf() }
        scope.launch { engine.statusFlow.collectLatest { tvInfo.text = it } }
        overlay = view
        wm?.addView(view, lp)
    }
    override fun onDestroy() {
        engine.destroy(); scope.cancel()
        overlay?.let { runCatching { wm?.removeView(it) } }
        overlay = null; reader?.close(); reader = null; mp?.stop(); mp = null
        super.onDestroy()
    }
    override fun onBind(intent: Intent?) = null
}
