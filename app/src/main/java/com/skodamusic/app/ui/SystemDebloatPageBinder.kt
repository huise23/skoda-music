package com.skodamusic.app.ui

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.skodamusic.app.R
import com.skodamusic.app.core.concurrent.AppBackgroundExecutor
import com.skodamusic.app.system.SystemDebloatManager

class SystemDebloatPageBinder(
    private val activity: AppCompatActivity,
    private val backgroundExecutor: AppBackgroundExecutor,
    private val onBack: () -> Unit
) {
    private lateinit var pageContainer: View
    private lateinit var backButton: ImageButton
    private lateinit var summaryBadge: TextView
    private lateinit var btnDisableAllDead: Button
    private lateinit var btnEnableAll: Button
    private lateinit var listContainer: LinearLayout

    private var appsList: List<SystemDebloatManager.AppInfo> = emptyList()

    fun bindViews(
        pageContainer: View,
        backButton: ImageButton,
        summaryBadge: TextView,
        btnDisableAllDead: Button,
        btnEnableAll: Button,
        listContainer: LinearLayout
    ) {
        this.pageContainer = pageContainer
        this.backButton = backButton
        this.summaryBadge = summaryBadge
        this.btnDisableAllDead = btnDisableAllDead
        this.btnEnableAll = btnEnableAll
        this.listContainer = listContainer

        backButton.setOnClickListener { onBack() }

        btnDisableAllDead.setOnClickListener {
            promptDisableAllDead()
        }

        btnEnableAll.setOnClickListener {
            promptEnableAll()
        }
    }

    fun loadAndRender() {
        if (appsList.isEmpty()) {
            appsList = SystemDebloatManager.getCatalog()
        }
        backgroundExecutor.execute {
            SystemDebloatManager.refreshAppStates(activity, appsList)
            activity.runOnUiThread {
                renderList()
                updateSummaryBadge()
            }
        }
    }

    private fun updateSummaryBadge() {
        val total = appsList.size
        val disabledCount = appsList.count { it.isDisabled }
        val savedMb = SystemDebloatManager.calculateSavedSpaceMb(appsList)
        summaryBadge.text = String.format("已停用: %d / %d (释放~%.1f MB)", disabledCount, total, savedMb)
    }

    private fun renderList() {
        listContainer.removeAllViews()

        // 1. Dead cloud section
        val deadApps = appsList.filter { it.category == SystemDebloatManager.Category.DEAD_CLOUD }
        if (deadApps.isNotEmpty()) {
            listContainer.addView(createSectionHeader(
                title = "【彻底停服服务】(云端服务器已下线，无响应)",
                subtitle = "建议全部停用，消除后台持续轮询重试、无谓报错与唤醒锁"
            ))
            for (app in deadApps) {
                listContainer.addView(createAppRowView(app))
            }
        }

        // 2. Recommended debloat section
        val recommendedApps = appsList.filter { it.category == SystemDebloatManager.Category.RECOMMENDED }
        if (recommendedApps.isNotEmpty()) {
            listContainer.addView(createSectionHeader(
                title = "【建议停用应用】(原厂过期或大型冗余第三方)",
                subtitle = "体积巨大且已过时，可按需单独开启或停用，随时可一键还原"
            ))
            for (app in recommendedApps) {
                listContainer.addView(createAppRowView(app))
            }
        }

        // 3. Safety Notice Footer
        listContainer.addView(createSafetyFooter())
    }

    private fun createSectionHeader(title: String, subtitle: String): View {
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 20, 8, 8)
        }
        val titleTv = TextView(activity).apply {
            text = title
            setTextColor(Color.parseColor("#4DA3FF"))
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
        }
        val subTv = TextView(activity).apply {
            text = subtitle
            setTextColor(Color.parseColor("#888888"))
            textSize = 12f
            setPadding(0, 4, 0, 0)
        }
        layout.addView(titleTv)
        layout.addView(subTv)
        return layout
    }

    private fun createAppRowView(app: SystemDebloatManager.AppInfo): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.field_surface)
            setPadding(16, 12, 16, 12)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 8
            }
            layoutParams = params
        }

        // Info Column
        val infoCol = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        }

        // Top line: Name + Badge
        val titleRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val nameTv = TextView(activity).apply {
            text = app.displayName
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
        }
        val badgeTv = TextView(activity).apply {
            if (app.category == SystemDebloatManager.Category.DEAD_CLOUD) {
                text = " [彻底停服] "
                setTextColor(Color.parseColor("#FF6B6B"))
            } else {
                text = " [建议停用] "
                setTextColor(Color.parseColor("#FFA94D"))
            }
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
        }
        titleRow.addView(nameTv)
        titleRow.addView(badgeTv)

        // Subtitle line: Apk name + Size
        val subTv = TextView(activity).apply {
            text = String.format("%s · %.1f MB · %s", app.apkFileName, app.sizeMb, app.packageName)
            setTextColor(Color.parseColor("#A0A0A0"))
            textSize = 12f
            setPadding(0, 2, 0, 0)
        }

        // Description
        val descTv = TextView(activity).apply {
            text = app.description
            setTextColor(Color.parseColor("#777777"))
            textSize = 12f
            setPadding(0, 2, 0, 0)
        }

        infoCol.addView(titleRow)
        infoCol.addView(subTv)
        infoCol.addView(descTv)

        // Action Button
        val actionBtn = Button(activity).apply {
            val btnParams = LinearLayout.LayoutParams(110, 42).apply {
                leftMargin = 12
            }
            layoutParams = btnParams
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            updateButtonState(this, app)

            setOnClickListener {
                toggleApp(app, this)
            }
        }

        row.addView(infoCol)
        row.addView(actionBtn)
        return row
    }

    private fun updateButtonState(button: Button, app: SystemDebloatManager.AppInfo) {
        if (!app.isInstalled) {
            button.text = "未安装"
            button.setTextColor(Color.parseColor("#888888"))
            button.setBackgroundResource(R.drawable.button_secondary)
            button.isEnabled = false
        } else if (app.isDisabled) {
            button.text = "已停用"
            button.setTextColor(Color.parseColor("#FFA94D"))
            button.setBackgroundResource(R.drawable.button_secondary)
            button.isEnabled = true
        } else {
            button.text = "运行中"
            button.setTextColor(Color.parseColor("#40C057"))
            button.setBackgroundResource(R.drawable.button_primary)
            button.isEnabled = true
        }
    }

    private fun toggleApp(app: SystemDebloatManager.AppInfo, button: Button) {
        val targetEnable = app.isDisabled
        val actionText = if (targetEnable) "恢复启用" else "停用"
        Toast.makeText(activity, "正在${actionText} ${app.displayName}…", Toast.LENGTH_SHORT).show()

        backgroundExecutor.execute {
            val result = SystemDebloatManager.setAppEnabled(app.packageName, targetEnable)
            activity.runOnUiThread {
                if (result.first) {
                    app.isDisabled = !targetEnable
                    updateButtonState(button, app)
                    updateSummaryBadge()
                    Toast.makeText(activity, "已${actionText}: ${app.displayName}", Toast.LENGTH_SHORT).show()
                } else {
                    AlertDialog.Builder(activity)
                        .setTitle("操作失败")
                        .setMessage(result.second)
                        .setPositiveButton("确定", null)
                        .show()
                }
            }
        }
    }

    private fun promptDisableAllDead() {
        AlertDialog.Builder(activity)
            .setTitle("一键停用彻底停服服务")
            .setMessage("确定要一键冻结所有已彻底停服的云端服务吗？\n\n• 涵盖斑马运营中心、云车联、智能推荐、天气服务等 14 项失效服务；\n• 停用后不再自启，杜绝后台网络空耗与报错；\n• 原车倒车影像、收音机、车身通信等核心功能受保护不受任何影响；\n• 支持随时一键全量恢复。")
            .setNegativeButton("取消", null)
            .setPositiveButton("确认停用") { _, _ ->
                executeBatchDisableDead()
            }
            .show()
    }

    private fun executeBatchDisableDead() {
        Toast.makeText(activity, "正在批量停用停服服务，请稍候…", Toast.LENGTH_SHORT).show()
        backgroundExecutor.execute {
            val count = SystemDebloatManager.disableAllDeadCloud(appsList)
            activity.runOnUiThread {
                renderList()
                updateSummaryBadge()
                Toast.makeText(activity, "已成功停用 $count 个停服组件！", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun promptEnableAll() {
        AlertDialog.Builder(activity)
            .setTitle("一键恢复所有停用应用")
            .setMessage("确定要将所有已停用的应用与服务全部恢复为启用状态吗？\n\n操作后所有组件将恢复出厂可用状态。")
            .setNegativeButton("取消", null)
            .setPositiveButton("确认恢复") { _, _ ->
                executeBatchEnableAll()
            }
            .show()
    }

    private fun executeBatchEnableAll() {
        Toast.makeText(activity, "正在全量恢复启用组件，请稍候…", Toast.LENGTH_SHORT).show()
        backgroundExecutor.execute {
            val count = SystemDebloatManager.enableAllDebloated(appsList)
            activity.runOnUiThread {
                renderList()
                updateSummaryBadge()
                Toast.makeText(activity, "已成功全量恢复 $count 个组件！", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun createSafetyFooter(): View {
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.panel_eq_state_badge)
            setPadding(16, 16, 16, 16)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 24
                bottomMargin = 24
            }
            layoutParams = params
        }

        val titleTv = TextView(activity).apply {
            text = "🛡️ 硬件级安全保护机制 (不可动组件已严格隐藏保护)"
            setTextColor(Color.parseColor("#4DA3FF"))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        }

        val descTv = TextView(activity).apply {
            text = "原车倒车影像 (JSBDBackCar)、原车收音机 (CaroceanFMRadio)、CAN总线通信协议 (CaroceanCarServer)、原车设置 (CaroceanCarSetting)、蓝牙驱动 (JSBDBtActivity)、车机原厂桌面 (uShell)、输入法 (AliIME) 等 12 个底层核心组件已被严格锁定并隐藏，在此界面完全不可见、不可修改，杜绝任何误操作破坏原车硬件功能的风险。"
            setTextColor(Color.parseColor("#A0A0A0"))
            textSize = 12f
            setPadding(0, 6, 0, 0)
        }

        layout.addView(titleTv)
        layout.addView(descTv)
        return layout
    }
}
