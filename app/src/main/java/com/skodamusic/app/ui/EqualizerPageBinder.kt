package com.skodamusic.app.ui

import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.skodamusic.app.R
import com.skodamusic.app.audio.dsp.HiFiDspMode

class EqualizerPageBinder(
    private val activity: AppCompatActivity,
    private val onToggleEnabled: (Boolean) -> Unit,
    private val onOpenPage: () -> Unit,
    private val onBack: () -> Unit,
    private val onModeSelected: (HiFiDspMode) -> Unit
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

    fun renderSettings(enabled: Boolean, mode: HiFiDspMode, isPageVisible: Boolean) {
        if (!this::enableSwitch.isInitialized) {
            return
        }
        suppressSwitchListener = true
        enableSwitch.isChecked = enabled
        suppressSwitchListener = false
        enableSwitch.isEnabled = true
        entryValue.text = if (enabled) {
            activity.getString(R.string.sound_entry_value_enabled_format, resolveSoundModeName(mode))
        } else {
            activity.getString(R.string.sound_entry_value_disabled)
        }
        noteValue.text = activity.getString(R.string.sound_note_fail_open)
        noteValue.visibility = View.VISIBLE
        entryRow.alpha = if (enabled) 1f else 0.88f
        if (isPageVisible) {
            renderFullscreenPage(enabled, mode)
        }
    }

    fun renderFullscreenPage(enabled: Boolean, mode: HiFiDspMode) {
        if (!this::bandsContainer.isInitialized || !this::presetsContainer.isInitialized) {
            return
        }
        renderSoundModeDetails(mode)
        renderSoundModeButtons(enabled, mode)
        refreshFullscreenHeader(enabled, mode)
    }

    fun resolveSoundModeName(mode: HiFiDspMode): String {
        return activity.getString(resolveSoundModeTitleRes(mode))
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

    private fun renderSoundModeDetails(mode: HiFiDspMode) {
        bandsContainer.removeAllViews()
        bandsContainer.orientation = LinearLayout.VERTICAL
        bandsContainer.gravity = Gravity.CENTER_VERTICAL
        bandsContainer.minimumWidth = dpToPx(360)
        bandsContainer.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))

        val title = TextView(activity).apply {
            text = resolveSoundModeName(mode)
            setTextColor(activity.resources.getColor(R.color.text_primary))
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
        }
        bandsContainer.addView(
            title,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        )

        val description = TextView(activity).apply {
            text = activity.getString(resolveSoundModeDescriptionRes(mode))
            setTextColor(activity.resources.getColor(R.color.text_secondary))
            textSize = 18f
            setLineSpacing(0f, 1.16f)
            setPadding(0, dpToPx(12), 0, 0)
        }
        bandsContainer.addView(
            description,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        )

        val chain = TextView(activity).apply {
            text = activity.getString(R.string.sound_page_chain_note)
            setTextColor(activity.resources.getColor(R.color.text_muted))
            textSize = 15f
            setLineSpacing(0f, 1.14f)
            setPadding(0, dpToPx(18), 0, 0)
        }
        bandsContainer.addView(
            chain,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        )
    }

    private fun renderSoundModeButtons(enabled: Boolean, mode: HiFiDspMode) {
        presetsContainer.removeAllViews()
        SOUND_MODE_OPTIONS.forEachIndexed { index, option ->
            val active = (enabled && mode == option.mode) ||
                (!enabled && option.mode == HiFiDspMode.ORIGINAL)
            val button = Button(activity).apply {
                text = activity.getString(option.titleRes)
                isAllCaps = false
                textSize = 16f
                minHeight = dpToPx(46)
                setPadding(dpToPx(10), dpToPx(8), dpToPx(10), dpToPx(8))
                setBackgroundResource(if (active) R.drawable.button_eq_preset_active else R.drawable.button_eq_preset_inactive)
                setTextColor(activity.resources.getColor(if (active) R.color.white else R.color.text_primary))
                setOnClickListener { onModeSelected(option.mode) }
            }
            presetsContainer.addView(
                button,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    if (index > 0) {
                        topMargin = dpToPx(8)
                    }
                }
            )
        }
    }

    private fun refreshFullscreenHeader(enabled: Boolean, mode: HiFiDspMode) {
        if (enabled) {
            pageStateValue.text = activity.getString(R.string.sound_page_state_on_format, resolveSoundModeName(mode))
            pageStateValue.setTextColor(activity.resources.getColor(R.color.text_primary))
        } else {
            pageStateValue.text = activity.getString(R.string.sound_page_state_off)
            pageStateValue.setTextColor(activity.resources.getColor(R.color.text_secondary))
        }
    }

    @StringRes
    private fun resolveSoundModeTitleRes(mode: HiFiDspMode): Int {
        return SOUND_MODE_OPTIONS.firstOrNull { it.mode == mode }?.titleRes ?: R.string.sound_mode_fidelity
    }

    @StringRes
    private fun resolveSoundModeDescriptionRes(mode: HiFiDspMode): Int {
        return SOUND_MODE_OPTIONS.firstOrNull { it.mode == mode }?.descriptionRes
            ?: R.string.sound_mode_fidelity_desc
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * activity.resources.displayMetrics.density + 0.5f).toInt()
    }

    private data class SoundModeOption(
        val mode: HiFiDspMode,
        @StringRes val titleRes: Int,
        @StringRes val descriptionRes: Int
    )

    private companion object {
        val SOUND_MODE_OPTIONS = listOf(
            SoundModeOption(HiFiDspMode.ORIGINAL, R.string.sound_mode_original, R.string.sound_mode_original_desc),
            SoundModeOption(HiFiDspMode.FIDELITY, R.string.sound_mode_fidelity, R.string.sound_mode_fidelity_desc),
            SoundModeOption(HiFiDspMode.CLARITY, R.string.sound_mode_clarity, R.string.sound_mode_clarity_desc),
            SoundModeOption(HiFiDspMode.DYNAMIC, R.string.sound_mode_dynamic, R.string.sound_mode_dynamic_desc),
            SoundModeOption(HiFiDspMode.SOFT, R.string.sound_mode_soft, R.string.sound_mode_soft_desc)
        )
    }
}
