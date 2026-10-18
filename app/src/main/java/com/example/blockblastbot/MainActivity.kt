package com.example.blockblastbot
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.blockblastbot.capture.ScreenCaptureService
import com.example.blockblastbot.databinding.ActivityMainBinding
import com.example.blockblastbot.input.TouchService
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val notifPermLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val projLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK && res.data != null) {
            val i = Intent(this, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_START
                putExtra("code", res.resultCode)
                putExtra("data", res.data)
            }
            startForegroundService(i)
            Toast.makeText(this, "Запущено! Зайди в игру.", Toast.LENGTH_SHORT).show()
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        binding.btnAccessibility.setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        binding.btnOverlay.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName)))
            else Toast.makeText(this, "Разрешение выдано", Toast.LENGTH_SHORT).show()
        }
        binding.btnStartService.setOnClickListener {
            if (TouchService.instance == null) {
                Toast.makeText(this, "Включите TouchService (Шаг 1)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            projLauncher.launch((getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).createScreenCaptureIntent())
        }
    }
}
