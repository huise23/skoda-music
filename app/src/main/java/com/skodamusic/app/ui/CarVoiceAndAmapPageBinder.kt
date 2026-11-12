package com.skodamusic.app.ui

import android.app.AlertDialog
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.skodamusic.app.R
import com.skodamusic.app.bridge.Amap65Manager
import com.skodamusic.app.bridge.CarBridgeHeartbeatManager
import com.skodamusic.app.bridge.CarBridgeManager
import com.skodamusic.app.bridge.CarVoiceDriverHelper
import com.skodamusic.app.bridge.CarVoiceFeedbackManager
import java.io.File

/**
 * UI Binder for Steering Wheel Voice, CarBridge keep-alive channel settings, as well as
 * AutoNavi (Amap Auto) 6.6.0 installation and management.
 * Fully compatible with Android API 17 (Jelly Bean 4.2.2).
 */
class CarVoiceAndAmapPageBinder(
    private val activity: AppCompatActivity,
    private val log: (String) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var tvGateway: TextView
    private lateinit var tvChannelStatus: TextView
    private lateinit var tvSpeechAdaptStatus: TextView
    private lateinit var btnRestoreSpeechAdapt: Button
    private lateinit var switchBeep: SwitchCompat
    private lateinit var switchToast: SwitchCompat
    private lateinit var btnFallbackPlayPause: Button
    private lateinit var btnFallbackNext: Button
    private lateinit var btnFallbackNone: Button
    private lateinit var tvRecentLog: TextView
    private lateinit var btnTestVoiceKey: Button
    private lateinit var btnPingPhone: Button

    private lateinit var tvAmapStatus: TextView
    private lateinit var progressBarAmap: ProgressBar
    private lateinit var tvProgressAmap: TextView
    private lateinit var btnAmapDownload: Button
    private lateinit var btnAmapScan: Button
    private lateinit var btnAmapLaunch: Button

    private val voiceListener: () -> Unit = {
        mainHandler.post { refreshVoiceSection() }
    }

    private val heartbeatListener: (CarBridgeHeartbeatManager.HeartbeatState) -> Unit = { state ->
        mainHandler.post { updateChannelStatusUi(state) }
    }

    fun bindViews(
        tvGateway: TextView,
        tvChannelStatus: TextView,
        tvSpeechAdaptStatus: TextView,
        btnRestoreSpeechAdapt: Button,
        switchBeep: SwitchCompat,
        switchToast: SwitchCompat,
        btnFallbackPlayPause: Button,
        btnFallbackNext: Button,
        btnFallbackNone: Button,
        tvRecentLog: TextView,
        btnTestVoiceKey: Button,
        btnPingPhone: Button,
        tvAmapStatus: TextView,
        progressBarAmap: ProgressBar,
        tvProgressAmap: TextView,
        btnAmapDownload: Button,
        btnAmapScan: Button,
        btnAmapLaunch: Button
    ) {
        this.tvGateway = tvGateway
        this.tvChannelStatus = tvChannelStatus
        this.tvSpeechAdaptStatus = tvSpeechAdaptStatus
        this.btnRestoreSpeechAdapt = btnRestoreSpeechAdapt
        this.switchBeep = switchBeep
        this.switchToast = switchToast
        this.btnFallbackPlayPause = btnFallbackPlayPause
        this.btnFallbackNext = btnFallbackNext
        this.btnFallbackNone = btnFallbackNone
        this.tvRecentLog = tvRecentLog
        this.btnTestVoiceKey = btnTestVoiceKey
        this.btnPingPhone = btnPingPhone

        this.tvAmapStatus = tvAmapStatus
        this.progressBarAmap = progressBarAmap
        this.tvProgressAmap = tvProgressAmap
        this.btnAmapDownload = btnAmapDownload
        this.btnAmapScan = btnAmapScan
        this.btnAmapLaunch = btnAmapLaunch

        CarVoiceFeedbackManager.loadHistory(activity)
        initListeners()
        refreshAll()
        CarVoiceFeedbackManager.addListener(voiceListener)
        CarBridgeHeartbeatManager.addListener(heartbeatListener)
    }

    fun unbind() {
        CarVoiceFeedbackManager.removeListener(voiceListener)
        CarBridgeHeartbeatManager.removeListener(heartbeatListener)
    }

    private fun initListeners() {
        switchBeep.setOnCheckedChangeListener { _, isChecked ->
            CarVoiceFeedbackManager.setBeepEnabled(activity, isChecked)
            log("Voice beep enabled changed: $isChecked")
        }

        switchToast.setOnCheckedChangeListener { _, isChecked ->
            CarVoiceFeedbackManager.setToastEnabled(activity, isChecked)
            log("Voice toast enabled changed: $isChecked")
        }

        btnFallbackPlayPause.setOnClickListener {
            CarVoiceFeedbackManager.setFallbackAction(activity, CarVoiceFeedbackManager.FALLBACK_PLAY_PAUSE)
            refreshFallbackButtons(CarVoiceFeedbackManager.FALLBACK_PLAY_PAUSE)
            Toast.makeText(activity, "已设置手机未连时兜底：切换播放/暂停", Toast.LENGTH_SHORT).show()
        }

        btnFallbackNext.setOnClickListener {
            CarVoiceFeedbackManager.setFallbackAction(activity, CarVoiceFeedbackManager.FALLBACK_NEXT)
            refreshFallbackButtons(CarVoiceFeedbackManager.FALLBACK_NEXT)
            Toast.makeText(activity, "已设置手机未连时兜底：播放下一曲", Toast.LENGTH_SHORT).show()
        }

        btnFallbackNone.setOnClickListener {
            CarVoiceFeedbackManager.setFallbackAction(activity, CarVoiceFeedbackManager.FALLBACK_NONE)
            refreshFallbackButtons(CarVoiceFeedbackManager.FALLBACK_NONE)
            Toast.makeText(activity, "已设置手机未连时兜底：仅提示，不执行动作", Toast.LENGTH_SHORT).show()
        }

        btnTestVoiceKey.setOnClickListener {
            log("Manual test voice trigger pressed")
            CarVoiceFeedbackManager.onVoiceTriggered(activity, "UI_MANUAL_TEST")
        }

        btnPingPhone.setOnClickListener {
            btnPingPhone.isEnabled = false
            btnPingPhone.text = "检测中..."
            CarBridgeManager.pingPhone(activity) { success, message ->
                mainHandler.post {
                    btnPingPhone.isEnabled = true
                    btnPingPhone.text = "测试手机连通性"
                    AlertDialog.Builder(activity)
                        .setTitle(if (success) "手机端易语助手连接正常" else "手机连接检测失败")
                        .setMessage(message)
                        .setPositiveButton("确定", null)
                        .show()
                }
            }
        }

        btnRestoreSpeechAdapt.setOnClickListener {
            btnRestoreSpeechAdapt.isEnabled = false
            btnRestoreSpeechAdapt.text = "正在解冻..."
            Thread {
                val (ok, msg) = CarVoiceDriverHelper.restoreDriver()
                mainHandler.post {
                    btnRestoreSpeechAdapt.isEnabled = true
                    btnRestoreSpeechAdapt.text = "一键解冻按键驱动"
                    refreshSpeechAdaptStatus()
                    AlertDialog.Builder(activity)
                        .setTitle(if (ok) "驱动恢复完成" else "恢复失败")
                        .setMessage(msg)
                        .setPositiveButton("确定", null)
                        .show()
                }
            }.start()
        }

        btnAmapDownload.setOnClickListener {
            promptDownloadAmap()
        }

        btnAmapScan.setOnClickListener {
            scanLocalAmap()
        }

        btnAmapLaunch.setOnClickListener {
            if (Amap65Manager.isInstalled(activity)) {
                Amap65Manager.launchAmap(activity)
            } else {
                Toast.makeText(activity, "高德车机版尚未安装，请点击下载或扫描安装", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun refreshAll() {
        refreshVoiceSection()
        refreshAmapSection()
        updateChannelStatusUi(CarBridgeHeartbeatManager.getState())
    }

    private fun updateChannelStatusUi(state: CarBridgeHeartbeatManager.HeartbeatState) {
        if (state.isConnected) {
            tvChannelStatus.text = "车载通道: 🟢 在线保活中 (延迟 ${state.latencyMs}ms | 目标: ${state.phoneIp}:8999 | 心跳: ${state.pingCount}次)"
            tvChannelStatus.setTextColor(Color.parseColor("#00D26A"))
        } else {
            val hint = if (state.consecutiveFailures > 0) {
                "连接异常 (连续超时 ${state.consecutiveFailures} 次: ${state.lastError ?: "网络无响应"})"
            } else {
                "未连通 (热点未开启或手机易语未运行)"
            }
            tvChannelStatus.text = "车载通道: 🔴 $hint"
            tvChannelStatus.setTextColor(Color.parseColor("#FF5252"))
        }
    }

    private fun refreshVoiceSection() {
        val phoneIp = CarBridgeManager.getPhoneIp(activity)
        val dynamicGateway = CarBridgeManager.resolveHotspotGatewayIp(activity)
        val statusText = if (dynamicGateway != null) "已连热点网关: $phoneIp:8999" else "静态配置网关: $phoneIp:8999 (未连热点)"
        tvGateway.text = statusText

        refreshSpeechAdaptStatus()

        switchBeep.isChecked = CarVoiceFeedbackManager.isBeepEnabled(activity)
        switchToast.isChecked = CarVoiceFeedbackManager.isToastEnabled(activity)

        val fallback = CarVoiceFeedbackManager.getFallbackAction(activity)
        refreshFallbackButtons(fallback)

        val history = CarVoiceFeedbackManager.getHistory()
        if (history.isNotEmpty()) {
            val sb = StringBuilder()
            for ((idx, item) in history.take(3).withIndex()) {
                if (idx > 0) sb.append("\n")
                sb.append("[${item.time}] ${item.source} -> ${item.detail}")
            }
            tvRecentLog.text = sb.toString()
        } else {
            tvRecentLog.text = "最近触发: 暂无触发记录 (按下方向盘语音键即可实时记录)"
        }
    }

    private fun refreshSpeechAdaptStatus() {
        val status = CarVoiceDriverHelper.checkStatus(activity)
        if (status.isReady) {
            tvSpeechAdaptStatus.text = "按键驱动: 🟢 ${status.detail}"
            tvSpeechAdaptStatus.setTextColor(Color.parseColor("#00D26A"))
            btnRestoreSpeechAdapt.visibility = View.GONE
        } else {
            tvSpeechAdaptStatus.text = "按键驱动: 🔴 ${status.detail}"
            tvSpeechAdaptStatus.setTextColor(Color.parseColor("#FF5252"))
            btnRestoreSpeechAdapt.visibility = if (status.needsRestore) View.VISIBLE else View.GONE
        }
    }

    private fun refreshFallbackButtons(selected: String) {
        val activeBg = R.drawable.button_primary
        val idleBg = R.drawable.button_secondary
        val activeColor = Color.parseColor("#FFFFFF")
        val idleColor = Color.parseColor("#C5D0DC")

        btnFallbackPlayPause.setBackgroundResource(if (selected == CarVoiceFeedbackManager.FALLBACK_PLAY_PAUSE) activeBg else idleBg)
        btnFallbackPlayPause.setTextColor(if (selected == CarVoiceFeedbackManager.FALLBACK_PLAY_PAUSE) activeColor else idleColor)

        btnFallbackNext.setBackgroundResource(if (selected == CarVoiceFeedbackManager.FALLBACK_NEXT) activeBg else idleBg)
        btnFallbackNext.setTextColor(if (selected == CarVoiceFeedbackManager.FALLBACK_NEXT) activeColor else idleColor)

        btnFallbackNone.setBackgroundResource(if (selected == CarVoiceFeedbackManager.FALLBACK_NONE) activeBg else idleBg)
        btnFallbackNone.setTextColor(if (selected == CarVoiceFeedbackManager.FALLBACK_NONE) activeColor else idleColor)
    }

    private fun refreshAmapSection() {
        val installedVersion = Amap65Manager.getInstalledVersion(activity)
        if (installedVersion != null) {
            tvAmapStatus.text = "当前状态: 已安装 v$installedVersion"
            tvAmapStatus.setTextColor(Color.parseColor("#00D26A"))
            btnAmapLaunch.visibility = View.VISIBLE
            btnAmapDownload.text = "覆盖重装 6.6.0"
        } else {
            tvAmapStatus.text = "当前状态: 未安装 (推荐安装 6.6.0 红绿灯倒计时增强版)"
            tvAmapStatus.setTextColor(Color.parseColor("#FFAB00"))
            btnAmapLaunch.visibility = View.GONE
            btnAmapDownload.text = "官方直链下载并安装"
        }
    }

    private fun promptDownloadAmap() {
        AlertDialog.Builder(activity)
            .setTitle("下载安装高德地图车机版 6.6.0")
            .setMessage(
                "安装包信息：\n" +
                "• 版本号：Auto_V6.6.0.602552_release_signed (官方原版)\n" +
                "• 核心功能：实时红绿灯倒计时推演、车道级引导、路口覆盖率增强\n" +
                "• 系统适配：Android 4.0+ (minSdk 14)，专为 1GB 内存优化\n" +
                "• 大小：约 85.4 MB\n\n" +
                "下载完成后将直接通过 Root 静默安装，确定开始下载吗？"
            )
            .setPositiveButton("开始下载") { _, _ ->
                startAmapDownload()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun startAmapDownload() {
        progressBarAmap.visibility = View.VISIBLE
        progressBarAmap.progress = 0
        tvProgressAmap.visibility = View.VISIBLE
        tvProgressAmap.text = "正在连接高德官方服务器..."
        btnAmapDownload.isEnabled = false
        btnAmapScan.isEnabled = false

        Amap65Manager.downloadAndInstall(
            context = activity,
            onProgress = { percent, bytesRead, total ->
                progressBarAmap.progress = percent
                val readMb = String.format(java.util.Locale.US, "%.1f", bytesRead / (1024f * 1024f))
                val totalMb = String.format(java.util.Locale.US, "%.1f", total / (1024f * 1024f))
                tvProgressAmap.text = "下载中: $percent% ($readMb MB / $totalMb MB)"
            },
            onComplete = { success, msg ->
                progressBarAmap.visibility = View.GONE
                tvProgressAmap.visibility = View.GONE
                btnAmapDownload.isEnabled = true
                btnAmapScan.isEnabled = true

                refreshAmapSection()

                AlertDialog.Builder(activity)
                    .setTitle(if (success) "高德地图车机版安装成功！" else "安装失败")
                    .setMessage(msg)
                    .setPositiveButton(if (success) "立即启动" else "知道了") { _, _ ->
                        if (success) {
                            Amap65Manager.launchAmap(activity)
                        }
                    }
                    .show()
            }
        )
    }

    private fun scanLocalAmap() {
        val localApk = Amap65Manager.findLocalApk(activity)
        if (localApk != null) {
            val sizeMb = String.format(java.util.Locale.US, "%.1f", localApk.length() / (1024f * 1024f))
            AlertDialog.Builder(activity)
                .setTitle("发现本地高德地图安装包")
                .setMessage(
                    "路径：${localApk.absolutePath}\n" +
                    "大小：$sizeMb MB\n\n" +
                    "是否立即使用 Root 权限静默安装？"
                )
                .setPositiveButton("立即安装") { _, _ ->
                    btnAmapScan.isEnabled = false
                    Toast.makeText(activity, "正在静默安装，请稍候...", Toast.LENGTH_SHORT).show()
                    val (success, msg) = Amap65Manager.installSilentViaRoot(localApk)
                    btnAmapScan.isEnabled = true
                    refreshAmapSection()
                    AlertDialog.Builder(activity)
                        .setTitle(if (success) "安装成功" else "安装失败")
                        .setMessage(msg)
                        .setPositiveButton(if (success) "启动" else "知道了") { _, _ ->
                            if (success) Amap65Manager.launchAmap(activity)
                        }
                        .show()
                }
                .setNegativeButton("取消", null)
                .show()
        } else {
            Toast.makeText(
                activity,
                "未在内部存储或 U 盘发现高德安装包，请插入 U 盘或点击官方下载",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
