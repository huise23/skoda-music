package com.skodamusic.app.ui

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.SeekBar

class VerticalSeekBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.seekBarStyle
) : SeekBar(context, attrs, defStyleAttr) {

    var onUserProgressChanged: ((Int) -> Unit)? = null
    var onUserStopTracking: (() -> Unit)? = null

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(heightMeasureSpec, widthMeasureSpec)
        setMeasuredDimension(measuredHeight, measuredWidth)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.rotate(-90f)
        canvas.translate(-height.toFloat(), 0f)
        super.onDraw(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) {
            return false
        }
        when (event.action) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_MOVE,
            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(event.action != MotionEvent.ACTION_UP)
                if (height <= 0) {
                    return true
                }
                val nextProgress = (max - (max * event.y / height).toInt()).coerceIn(0, max)
                if (progress != nextProgress) {
                    progress = nextProgress
                    onUserProgressChanged?.invoke(nextProgress)
                }
                onSizeChanged(width, height, 0, 0)
                if (event.action == MotionEvent.ACTION_UP) {
                    onUserStopTracking?.invoke()
                    performClick()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                onUserStopTracking?.invoke()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
