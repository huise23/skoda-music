package com.skodamusic.app.bridge

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import com.skodamusic.app.R
import com.skodamusic.app.playback.PlaybackActions
import com.skodamusic.app.playback.PlaybackControlBus
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Handles instant auditory and visual feedback for steering wheel voice button presses,
 * manages outgoing wake requests to phone (Yiyu), logs trigger history, and executes
 * fallback actions when the phone assistant is offline.
 *
 * Fully compatible with Android API 17 (Jelly Bean 4.2.2).
 */
object CarVoiceFeedbackManager {
    private const val TAG = "CarVoiceFeedback"
    private const val PREFS_NAME = "car_voice_prefs"
    const val KEY_BEEP_ENABLED = "voice_beep_enabled"
    const val KEY_TOAST_ENABLED = "voice_toast_enabled"
    const val KEY_FALLBACK_ACTION = "voice_fallback_action"
    const val KEY_HISTORY_JSON = "voice_history_json"

    const val FALLBACK_PLAY_PAUSE = "PLAY_PAUSE"
    const val FALLBACK_NEXT = "NEXT"
    const val FALLBACK_GAODE = "GAODE"
    const val FALLBACK_NONE = "NONE"

    data class VoiceEvent(
        val id: Long = System.currentTimeMillis(),
        val time: String,
        val source: String,
        var detail: String,
        var isSuccess: Boolean? = null
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private val history = CopyOnWriteArrayList<VoiceEvent>()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private var currentToast: Toast? = null
    private var toneGenerator: ToneGenerator? = null

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize ToneGenerator: ${e.message}")
        }
    }

    fun isBeepEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_BEEP_ENABLED, true)
    }

    fun setBeepEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_BEEP_ENABLED, enabled)
            .apply()
    }

    fun isToastEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_TOAST_ENABLED, true)
    }

    fun setToastEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_TOAST_ENABLED, enabled)
            .apply()
    }

    fun getFallbackAction(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_FALLBACK_ACTION, FALLBACK_PLAY_PAUSE) ?: FALLBACK_PLAY_PAUSE
    }

    fun setFallbackAction(context: Context, action: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FALLBACK_ACTION, action)
            .apply()
    }

    fun addListener(listener: () -> Unit) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun getHistory(): List<VoiceEvent> = history.toList()

    private fun notifyListeners() {
        mainHandler.post {
            for (listener in listeners) {
                try {
                    listener()
                } catch (e: Exception) {
                    Log.w(TAG, "Listener invocation error: ${e.message}")
                }
            }
        }
    }

    /**
     * Plays immediate hardware auditory feedback tone.
     */
    fun playTone(context: Context, toneType: Int, durationMs: Int = 150) {
        if (!isBeepEnabled(context)) return
        try {
            if (toneGenerator == null) {
                toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
            }
            toneGenerator?.startTone(toneType, durationMs)
        } catch (e: Exception) {
            Log.w(TAG, "Tone playback failed: ${e.message}")
        }
    }

    /**
     * Displays a prominent high-visibility HUD banner toast.
     */
    fun showVoiceToast(
        context: Context,
        title: String,
        message: String,
        icon: String = "🎙️"
    ) {
        if (!isToastEnabled(context)) return

        mainHandler.post {
            try {
                currentToast?.cancel()

                val inflater = LayoutInflater.from(context.applicationContext)
                val layout = inflater.inflate(R.layout.toast_voice_feedback, null)
                val tvIcon = layout.findViewById<TextView>(R.id.toast_voice_icon)
                val tvTitle = layout.findViewById<TextView>(R.id.toast_voice_title)
                val tvMsg = layout.findViewById<TextView>(R.id.toast_voice_message)

                tvIcon?.text = icon
                tvTitle?.text = title
                tvMsg?.text = message

                val toast = Toast(context.applicationContext).apply {
                    setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, 70)
                    duration = Toast.LENGTH_LONG
                    view = layout
                }
                currentToast = toast
                toast.show()
            } catch (e: Exception) {
                Log.w(TAG, "showVoiceToast error: ${e.message}")
            }
        }
    }

    /**
     * Main entry point when a voice button / hardware intent is intercepted.
     */
    fun onVoiceTriggered(context: Context, sourceAction: String) {
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        Log.i(TAG, "onVoiceTriggered: source=$sourceAction at $timeStr")

        // 1. Instant auditory tone (driver hears instant response within milliseconds)
        playTone(context, ToneGenerator.TONE_PROP_BEEP, 120)

        // 2. Ensure CarBridge server is up
        CarBridgeManager.start(context)

        // 3. Resolve phone IP and display visual HUD
        val phoneIp = CarBridgeManager.getPhoneIp(context)
        val initialDetail = "正在呼叫手机语音助手 ($phoneIp)..."
        val event = VoiceEvent(
            time = timeStr,
            source = sourceAction,
            detail = initialDetail,
            isSuccess = null
        )
        addHistoryEvent(context, event)

        showVoiceToast(
            context = context,
            title = "已接收方向盘语音按键",
            message = "正在唤醒手机端易语助手 ($phoneIp)...",
            icon = "🎙️"
        )

        // Broadcast to runtime log if MainActivity is listening
        sendRuntimeBroadcast(context, "[VoiceKey] 收到按键: $sourceAction, 呼叫目标: $phoneIp")

        // 4. Send asynchronous wake request to phone
        CarBridgeManager.notifyVoiceTriggered(context) { success, statusCode, targetIp, errorMsg ->
            mainHandler.post {
                if (success) {
                    playTone(context, ToneGenerator.TONE_PROP_ACK, 180)
                    val successMsg = "手机端易语助手已就绪，请对手机说话"
                    event.detail = "手机已唤醒 (HTTP $statusCode)"
                    event.isSuccess = true
                    saveHistory(context)
                    notifyListeners()

                    showVoiceToast(
                        context = context,
                        title = "✅ 手机语音助手已唤醒",
                        message = successMsg,
                        icon = "✅"
                    )
                    sendRuntimeBroadcast(context, "[VoiceKey] 手机唤醒成功 (HTTP $statusCode)")
                } else {
                    playTone(context, ToneGenerator.TONE_PROP_NACK, 220)
                    val failReason = errorMsg ?: "HTTP $statusCode 无响应"
                    event.detail = "未连接到手机 ($failReason)"
                    event.isSuccess = false

                    val fallback = getFallbackAction(context)
                    val fallbackExecuted = executeFallbackAction(context, fallback, targetIp)

                    if (fallbackExecuted.isNotEmpty()) {
                        event.detail += " -> $fallbackExecuted"
                    }
                    saveHistory(context)
                    notifyListeners()

                    sendRuntimeBroadcast(context, "[VoiceKey] 手机未响应 ($failReason), 兜底: $fallback")
                }
            }
        }
    }

    private fun executeFallbackAction(context: Context, fallbackType: String, phoneIp: String): String {
        return when (fallbackType) {
            FALLBACK_PLAY_PAUSE -> {
                PlaybackControlBus.dispatchWithResult(
                    PlaybackActions.ACTION_CMD_PLAY_PAUSE,
                    source = "voice_fallback"
                )
                showVoiceToast(
                    context = context,
                    title = "⚠️ 手机未连接 (${phoneIp})",
                    message = "已自动执行兜底动作：切换音乐播放/暂停",
                    icon = "⏯️"
                )
                "已切换音乐播放/暂停"
            }
            FALLBACK_NEXT -> {
                PlaybackControlBus.dispatchWithResult(
                    PlaybackActions.ACTION_CMD_NEXT,
                    source = "voice_fallback"
                )
                showVoiceToast(
                    context = context,
                    title = "⚠️ 手机未连接 (${phoneIp})",
                    message = "已自动执行兜底动作：播放下一曲",
                    icon = "⏭️"
                )
                "已切换下一曲"
            }
            FALLBACK_GAODE -> {
                AmapAutoBridge.startNavi(context, "")
                showVoiceToast(
                    context = context,
                    title = "⚠️ 手机未连接 (${phoneIp})",
                    message = "已自动调起高德地图",
                    icon = "🗺️"
                )
                "已调起高德地图"
            }
            else -> {
                showVoiceToast(
                    context = context,
                    title = "⚠️ 手机语音助手未响应",
                    message = "车机未连通 $phoneIp:8999，请确认手机已开启热点与易语",
                    icon = "⚠️"
                )
                "无兜底动作"
            }
        }
    }

    private fun addHistoryEvent(context: Context, event: VoiceEvent) {
        history.add(0, event)
        while (history.size > 20) {
            history.removeAt(history.size - 1)
        }
        saveHistory(context)
        notifyListeners()
    }

    private fun saveHistory(context: Context) {
        try {
            val arr = JSONArray()
            for (item in history.take(15)) {
                val obj = JSONObject().apply {
                    put("time", item.time)
                    put("source", item.source)
                    put("detail", item.detail)
                    put("isSuccess", item.isSuccess ?: false)
                }
                arr.put(obj)
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_HISTORY_JSON, arr.toString())
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "saveHistory error: ${e.message}")
        }
    }

    fun loadHistory(context: Context) {
        if (history.isNotEmpty()) return
        try {
            val jsonStr = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_HISTORY_JSON, null) ?: return
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                history.add(
                    VoiceEvent(
                        time = obj.optString("time", "--:--"),
                        source = obj.optString("source", "未知"),
                        detail = obj.optString("detail", ""),
                        isSuccess = if (obj.has("isSuccess")) obj.optBoolean("isSuccess") else null
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "loadHistory error: ${e.message}")
        }
    }

    fun clearHistory(context: Context) {
        history.clear()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_HISTORY_JSON)
            .apply()
        notifyListeners()
    }

    private fun sendRuntimeBroadcast(context: Context, logMsg: String) {
        try {
            val intent = Intent("com.skodamusic.app.action.RUNTIME_LOG").apply {
                putExtra("log_message", logMsg)
                setPackage(context.packageName)
            }
            context.sendBroadcast(intent)
        } catch (_: Exception) {}
    }
}
