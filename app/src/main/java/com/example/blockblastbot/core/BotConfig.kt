package com.example.blockblastbot.core
import android.content.Context
import android.content.SharedPreferences
enum class BotStrategy { BALANCED, SAFE, CLEAR_FIELD }
class BotConfig(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("block_blast_bot_prefs", Context.MODE_PRIVATE)
    var moveDelayMs: Long get() = prefs.getLong("move_delay_ms", 650L); set(v) = prefs.edit().putLong("move_delay_ms", v).apply()
    var dragOffsetY: Float get() = prefs.getFloat("drag_offset_y", 0f); set(v) = prefs.edit().putFloat("drag_offset_y", v).apply()
    
    // ПРАВИЛЬНЫЕ ЗНАЧЕНИЯ ДЛЯ ТВОЕГО ЭКРАНА
    var boardTopRatio: Float get() = prefs.getFloat("board_top_ratio", 0.22f); set(v) = prefs.edit().putFloat("board_top_ratio", v).apply()
    var boardSideMarginRatio: Float get() = prefs.getFloat("board_margin_ratio", 0.04f); set(v) = prefs.edit().putFloat("board_margin_ratio", v).apply()
    
    var strategy: BotStrategy get() = BotStrategy.valueOf(prefs.getString("strategy", BotStrategy.BALANCED.name) ?: BotStrategy.BALANCED.name); set(v) = prefs.edit().putString("strategy", v.name).apply()
}
