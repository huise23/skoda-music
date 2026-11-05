package com.skodamusic.app.ui

import android.content.Intent
import android.os.Build
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.skodamusic.app.R
import com.skodamusic.app.audio.EqualizerManager
import java.util.Locale

class EqualizerPageBinder(
    private val activity: AppCompatActivity,
    private val onToggleEnabled: (Boolean) -> Unit,
    private val onOpenPage: () -> Unit,
    private val onBack: () -> Unit,
    private val onPresetSelected: (Int) -> Unit,
    private val onBandLevelChanged: (bandIndex: Int, levelMillibel: Int) -> Unit,
    private val onOpenCarSystemSoundSetting: () -> Unit
) {
    private lateinit var enableSwitch: SwitchCompat
    private lateinit var entryRow: View
    private lateinit var entryValue: TextView
    private lateinit var noteValue: TextView
    private lateinit var pageStateValue: TextView
    private lateinit var bandsContainer: LinearLayout
    private lateinit var presetsContainer: LinearLayout
    private lateinit var backButton: ImageButton
    private var suppressSwitchListener = false

    fun bindViews(
        enableSwitch: SwitchCompat,
        entryRow: View,
        entryValue: TextView,
        noteValue: TextView,
        pageStateValue: TextView,
        bandsContainer: LinearLayout,
        presetsContainer: LinearLayout,
        backButton: ImageButton
    ) {
        this.enableSwitch = enableSwitch
        this.entryRow = entryRow
        this.entryValue = entryValue
        this.noteValue = noteValue
        this.pageStateValue = pageStateValue
        this.bandsContainer = bandsContainer
        this.presetsContainer = presetsContainer
        this.backButton = backButton
        bindActions()
    }

    fun renderSettings(
        enabled: Boolean,
        presetName: String,
        mode: EqualizerManager.EqMode,
        isPageVisible: Boolean
    ) {
        if (!this::enableSwitch.isInitialized) {
            return
        }
        suppressSwitchListener = true
        enableSwitch.isChecked = enabled
        suppressSwitchListener = false
        enableSwitch.isEnabled = true

        val modeLabel = if (mode == EqualizerManager.EqMode.CUSTOM) {
            activity.getString(R.string.eq_mode_custom)
        } else {
            activity.getString(R.string.eq_mode_preset)
        }

        entryValue.text = if (enabled) {
            activity.getString(R.string.eq_entry_value_enabled_format, presetName, modeLabel)
        } else {
            activity.getString(R.string.eq_entry_value_disabled)
        }
        noteValue.text = activity.getString(R.string.eq_note_fail_open)
        noteValue.visibility = View.VISIBLE
        entryRow.alpha = if (enabled) 1f else 0.88f
    }

    fun renderFullscreenPage(
        enabled: Boolean,
        eqMode: EqualizerManager.EqMode,
        presetIndex: Int,
        customBandLevels: List<Int>,
        capabilities: EqualizerManager.EqCapabilities
    ) {
        if (!this::bandsContainer.isInitialized || !this::presetsContainer.isInitialized) {
            return
        }
        val bands = resolveBandInfos(capabilities.bands)
        val presetNames = resolvePresetDisplayNames(capabilities.presetNames)

        renderBands(enabled, bands, customBandLevels)
        renderPresets(enabled, eqMode, presetIndex, presetNames)
        refreshHeader(enabled, eqMode, presetIndex, presetNames)
    }

    private fun bindActions() {
        enableSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (!suppressSwitchListener) {
                onToggleEnabled(isChecked)
            }
        }
        entryRow.setOnClickListener { onOpenPage() }
        backButton.setOnClickListener { onBack() }
    }

    private fun renderBands(
        enabled: Boolean,
        bands: List<EqualizerManager.EqBandInfo>,
        customLevels: List<Int>
    ) {
        bandsContainer.removeAllViews()
        bandsContainer.orientation = LinearLayout.HORIZONTAL
        bandsContainer.gravity = Gravity.CENTER_VERTICAL

        if (bands.isEmpty()) {
            val empty = TextView(activity).apply {
                text = activity.getString(R.string.eq_page_bands_empty)
                setTextColor(activity.resources.getColor(R.color.text_secondary))
                textSize = 15f
                gravity = Gravity.CENTER
                setPadding(0, dpToPx(12), 0, 0)
            }
            bandsContainer.addView(
                empty,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            )
            return
        }

        bands.forEachIndexed { index, band ->
            val column = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setBackgroundResource(R.drawable.field_surface)
                setPadding(dpToPx(10), dpToPx(10), dpToPx(10), dpToPx(10))
                alpha = if (enabled) 1f else 0.5f
            }

            val freq = TextView(activity).apply {
                text = formatBandLabel(band.centerFreqHz)
                setTextColor(activity.resources.getColor(R.color.text_primary))
                textSize = 14f
                gravity = Gravity.CENTER
            }
            column.addView(freq)

            val currentLevel = customLevels.getOrNull(index) ?: band.currentLevelMillibel
            val levelText = TextView(activity).apply {
                text = formatBandLevelLabel(currentLevel)
                setTextColor(activity.resources.getColor(R.color.brand_primary))
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(0, dpToPx(4), 0, 0)
            }

            val slider = VerticalSeekBar(activity).apply {
                val span = (band.maxLevelMillibel - band.minLevelMillibel).coerceAtLeast(1)
                max = span
                progress = (currentLevel - band.minLevelMillibel).coerceIn(0, span)
                isEnabled = enabled
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    splitTrack = false
                }
                setPadding(0, dpToPx(8), 0, dpToPx(8))
                onUserProgressChanged = { progressVal ->
                    val nextLevel = (band.minLevelMillibel + progressVal).coerceIn(
                        band.minLevelMillibel,
                        band.maxLevelMillibel
                    )
                    levelText.text = formatBandLevelLabel(nextLevel)
                    onBandLevelChanged(index, nextLevel)
                }
            }
            column.addView(
                slider,
                LinearLayout.LayoutParams(dpToPx(44), 0, 1f).apply {
                    topMargin = dpToPx(8)
                    bottomMargin = dpToPx(8)
                }
            )

            column.addView(levelText)

            val columnParams = LinearLayout.LayoutParams(dpToPx(80), LinearLayout.LayoutParams.MATCH_PARENT)
            if (index > 0) {
                columnParams.leftMargin = dpToPx(10)
            }
            bandsContainer.addView(column, columnParams)
        }
    }

    private fun renderPresets(
        enabled: Boolean,
        eqMode: EqualizerManager.EqMode,
        activePresetIndex: Int,
        presetNames: List<String>
    ) {
        presetsContainer.removeAllViews()

        var row: LinearLayout? = null
        presetNames.forEachIndexed { index, name ->
            if (index % 2 == 0) {
                row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                }
                presetsContainer.addView(
                    row,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        if (index > 0) topMargin = dpToPx(8)
                    }
                )
            }
            val active = enabled && eqMode == EqualizerManager.EqMode.PRESET && activePresetIndex == index
            val button = Button(activity).apply {
                text = name
                isAllCaps = false
                textSize = 15f
                minHeight = dpToPx(44)
                setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
                setBackgroundResource(if (active) R.drawable.button_eq_preset_active else R.drawable.button_eq_preset_inactive)
                setTextColor(activity.resources.getColor(if (active) R.color.white else R.color.text_primary))
                setOnClickListener { onPresetSelected(index) }
            }
            row?.addView(
                button,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (index % 2 == 1) leftMargin = dpToPx(8)
                }
            )
        }

        // Add entry to jump to factory/hardware 10-band car DSP settings
        val carSettingBtn = Button(activity).apply {
            text = "原车音效设置 (10段DSP/功放)"
            isAllCaps = false
            textSize = 14f
            minHeight = dpToPx(40)
            setBackgroundResource(R.drawable.button_secondary)
            setTextColor(activity.resources.getColor(R.color.brand_primary))
            setOnClickListener { onOpenCarSystemSoundSetting() }
        }
        presetsContainer.addView(
            carSettingBtn,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(14)
            }
        )
    }

    private fun refreshHeader(
        enabled: Boolean,
        eqMode: EqualizerManager.EqMode,
        presetIndex: Int,
        presetNames: List<String>
    ) {
        if (!this::pageStateValue.isInitialized) {
            return
        }
        val modeLabel = if (eqMode == EqualizerManager.EqMode.CUSTOM) {
            activity.getString(R.string.eq_mode_custom)
        } else {
            activity.getString(R.string.eq_mode_preset)
        }
        val presetName = presetNames.getOrNull(presetIndex) ?: activity.getString(R.string.eq_preset_default_name)
        if (enabled) {
            pageStateValue.text = activity.getString(R.string.eq_page_state_on_format, presetName, modeLabel)
            pageStateValue.setTextColor(activity.resources.getColor(R.color.text_primary))
        } else {
            pageStateValue.text = activity.getString(R.string.eq_page_state_off)
            pageStateValue.setTextColor(activity.resources.getColor(R.color.text_secondary))
        }
    }

    fun resolvePresetDisplayNames(rawNames: List<String>): List<String> {
        val cleaned = rawNames.mapIndexed { index, value ->
            mapPresetDisplayName(index = index, rawName = value.trim())
        }.filter { it.isNotBlank() }
        if (cleaned.isNotEmpty()) {
            return cleaned
        }
        return FALLBACK_PRESET_NAMES
    }

    private fun mapPresetDisplayName(index: Int, rawName: String): String {
        if (index == 0) return activity.getString(R.string.eq_preset_default_name)
        val lower = rawName.lowercase(Locale.US)
        return when {
            lower.contains("normal") || lower.contains("default") -> activity.getString(R.string.eq_preset_default_name)
            lower.contains("classic") -> activity.getString(R.string.eq_preset_classical)
            lower.contains("dance") -> activity.getString(R.string.eq_preset_dance)
            lower.contains("flat") -> activity.getString(R.string.eq_preset_flat)
            lower.contains("folk") -> activity.getString(R.string.eq_preset_folk)
            lower.contains("heavy") || lower.contains("metal") -> activity.getString(R.string.eq_preset_heavy_metal)
            lower.contains("hip") -> activity.getString(R.string.eq_preset_hip_hop)
            lower.contains("jazz") -> activity.getString(R.string.eq_preset_jazz)
            lower.contains("pop") -> activity.getString(R.string.eq_preset_pop)
            lower.contains("rock") -> activity.getString(R.string.eq_preset_rock)
            rawName.isNotBlank() -> rawName
            else -> activity.getString(R.string.eq_preset_fallback_format, index)
        }
    }

    fun resolveBandInfos(discovered: List<EqualizerManager.EqBandInfo>): List<EqualizerManager.EqBandInfo> {
        if (discovered.isNotEmpty()) {
            return discovered
        }
        return FALLBACK_BANDS
    }

    private fun formatBandLabel(centerFreqHz: Int): String {
        return if (centerFreqHz >= 1000) {
            val k = centerFreqHz / 1000f
            if (k % 1f == 0f) "${k.toInt()}k" else String.format(Locale.US, "%.1fk", k)
        } else {
            "${centerFreqHz}Hz"
        }
    }

    private fun formatBandLevelLabel(levelMillibel: Int): String {
        val db = levelMillibel / 100f
        return if (db > 0f) "+%.1fdB".format(Locale.US, db) else "%.1fdB".format(Locale.US, db)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * activity.resources.displayMetrics.density + 0.5f).toInt()
    }

    companion object {
        val FALLBACK_PRESET_NAMES = listOf(
            "默认", "古典", "舞曲", "平坦", "民谣", "重金属", "嘻哈", "爵士", "流行", "摇滚"
        )
        val FALLBACK_BANDS = listOf(
            EqualizerManager.EqBandInfo(0, 60, -1500, 1500, 0),
            EqualizerManager.EqBandInfo(1, 230, -1500, 1500, 0),
            EqualizerManager.EqBandInfo(2, 910, -1500, 1500, 0),
            EqualizerManager.EqBandInfo(3, 3600, -1500, 1500, 0),
            EqualizerManager.EqBandInfo(4, 14000, -1500, 1500, 0)
        )
    }
}
