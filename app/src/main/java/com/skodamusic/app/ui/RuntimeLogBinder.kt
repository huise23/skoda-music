package com.skodamusic.app.ui

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.skodamusic.app.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RuntimeLogBinder(
    private val activity: AppCompatActivity,
    private val logTag: String,
    private val showToast: (Int) -> Unit
) {
    private val runtimeLogLines = mutableListOf<String>()
    private val runtimeLogLock = Any()
    private var runtimeLogDialog: Dialog? = null
    private var runtimeLogDialogText: TextView? = null
    private var runtimeLogPreview: TextView? = null
    private var destroyed = false

    fun bind(preview: TextView, label: TextView) {
        runtimeLogPreview = preview
        preview.setOnClickListener { showFullscreen() }
        label.setOnClickListener { showFullscreen() }
        renderRuntimeLogSnapshot(snapshotRaw())
    }

    fun append(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        val line = "$timestamp $message"
        val snapshot: String
        synchronized(runtimeLogLock) {
            runtimeLogLines.add(line)
            if (runtimeLogLines.size > MAX_RUNTIME_LOG_LINES) {
                runtimeLogLines.removeAt(0)
            }
            snapshot = runtimeLogLines.joinToString("\n")
        }
        Log.d(logTag, line)
        if (destroyed || runtimeLogPreview == null) {
            return
        }
        // Logs can be appended from network/playback callbacks; only rendering touches Android views.
        activity.runOnUiThread {
            if (!destroyed) {
                renderRuntimeLogSnapshot(snapshot)
            }
        }
    }

    fun showFullscreen() {
        if (runtimeLogDialog?.isShowing == true || destroyed) {
            return
        }
        val dialog = Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(R.layout.dialog_runtime_logs)
        val fullText = dialog.findViewById<TextView>(R.id.runtime_log_fullscreen_text)
        dialog.findViewById<Button>(R.id.btn_copy_runtime_logs).setOnClickListener {
            copyToClipboard()
        }
        dialog.findViewById<Button>(R.id.btn_clear_runtime_logs).setOnClickListener {
            clear()
        }
        dialog.findViewById<Button>(R.id.btn_close_runtime_logs).setOnClickListener {
            dialog.dismiss()
        }
        fullText.text = snapshotForDisplay()
        runtimeLogDialog = dialog
        runtimeLogDialogText = fullText
        dialog.setOnDismissListener {
            runtimeLogDialog = null
            runtimeLogDialogText = null
        }
        dialog.show()
    }

    fun destroy() {
        destroyed = true
        runtimeLogDialog?.dismiss()
        runtimeLogDialog = null
        runtimeLogDialogText = null
        runtimeLogPreview = null
    }

    private fun snapshotRaw(): String {
        synchronized(runtimeLogLock) {
            return runtimeLogLines.joinToString("\n")
        }
    }

    private fun snapshotForDisplay(): String {
        val snapshot = snapshotRaw()
        return if (snapshot.isBlank()) {
            activity.getString(R.string.runtime_logs_empty)
        } else {
            snapshot
        }
    }

    private fun renderRuntimeLogSnapshot(snapshot: String) {
        val preview = if (snapshot.isBlank()) {
            activity.getString(R.string.runtime_logs_empty)
        } else {
            snapshot
                .split('\n')
                .takeLast(RUNTIME_LOG_PREVIEW_LINES)
                .joinToString("\n")
        }
        runtimeLogPreview?.text = preview
        runtimeLogDialogText?.text = if (snapshot.isBlank()) {
            activity.getString(R.string.runtime_logs_empty)
        } else {
            snapshot
        }
    }

    private fun copyToClipboard() {
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("Skoda Runtime Logs", snapshotForDisplay()))
        append("runtime logs copied to clipboard")
        showToast(R.string.toast_runtime_logs_copied)
    }

    private fun clear() {
        synchronized(runtimeLogLock) {
            runtimeLogLines.clear()
        }
        renderRuntimeLogSnapshot("")
        showToast(R.string.toast_runtime_logs_cleared)
    }

    private companion object {
        const val MAX_RUNTIME_LOG_LINES = 800
        const val RUNTIME_LOG_PREVIEW_LINES = 2
    }
}
