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
            Toast.makeText(this, "Сервис запущен!", Toast.LENGTH_SHORT).show()
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
        binding.btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        binding.btnOverlay.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName)))
            } else {
                Toast.makeText(this, "Разрешение уже выдано", Toast.LENGTH_SHORT).show()
            }
        }
        binding.btnStartService.setOnClickListener {
            if (TouchService.instance == null) {
                Toast.makeText(this, "Включите TouchService (Шаг 1)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projLauncher.launch(mpm.createScreenCaptureIntent())
        }
        binding.sbDelay.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, p: Int, u: Boolean) {
                binding.tvDelayLabel.text = "Задержка между ходами: " + p + " мс"
                com.example.blockblastbot.core.BotConfig(this@MainActivity).moveDelayMs = p.toLong()
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
        })
        binding.sbDragOffset.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, p: Int, u: Boolean) {
                binding.tvOffsetLabel.text = "Компенсация сдвига пальца: " + p + " px"
                com.example.blockblastbot.core.BotConfig(this@MainActivity).dragOffsetY = p.toFloat()
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
        })
        binding.sbBoardTop.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, p: Int, u: Boolean) {
                binding.tvBoardTopLabel.text = "Верхняя граница поля: " + p + "%"
                com.example.blockblastbot.core.BotConfig(this@MainActivity).boardTopRatio = p / 100f
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
        })
        binding.rgStrategy.setOnCheckedChangeListener { _, checkedId ->
            val cfg = com.example.blockblastbot.core.BotConfig(this)
            cfg.strategy = when (checkedId) {
                R.id.rbSafe -> com.example.blockblastbot.core.BotStrategy.SAFE
                R.id.rbClear -> com.example.blockblastbot.core.BotStrategy.CLEAR_FIELD
                else -> com.example.blockblastbot.core.BotStrategy.BALANCED
            }
        }
    }
}
