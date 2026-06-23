package com.buddylimit.app.monitor

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The full-screen block screen drawn over a budget-exceeded app (SYSTEM_DESIGN.md §5).
 * A plain View tree (no Compose) so it can live in a WindowManager overlay without an
 * Activity/lifecycle owner. All window operations are marshalled to the main thread.
 */
class BlockOverlay(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val main = Handler(Looper.getMainLooper())

    private var root: View? = null
    private var countdownView: TextView? = null

    /** Package the overlay is currently showing for, or null when hidden. */
    @Volatile private var shownFor: String? = null

    fun isShowing(): Boolean = shownFor != null

    fun show(appLabel: String, packageName: String, nextResetMillis: Long, onDismiss: () -> Unit) {
        main.post {
            if (shownFor == packageName && root != null) {
                setCountdownText(nextResetMillis)
                return@post
            }
            removeInternal()

            val density = context.resources.displayMetrics.density
            fun dp(value: Int): Int = (value * density).toInt()

            val title = TextView(context).apply {
                text = "$appLabel is done for today"
                setTextColor(Color.WHITE)
                textSize = 22f
                gravity = Gravity.CENTER
            }
            val countdown = TextView(context).apply {
                setTextColor(Color.LTGRAY)
                textSize = 16f
                gravity = Gravity.CENTER
            }
            val dismiss = Button(context).apply {
                text = "Got it"
                setOnClickListener {
                    onDismiss()
                    hide()
                }
            }
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setBackgroundColor(Color.argb(242, 27, 27, 27))
                setPadding(dp(32), dp(32), dp(32), dp(32))
                addView(title)
                addView(
                    countdown,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(12) }
                )
                addView(
                    dismiss,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(24) }
                )
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                // Not focusable: still receives touches (the button works) but lets the
                // back key fall through, so we don't have to own key handling.
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )

            runCatching { windowManager.addView(container, params) }
                .onSuccess {
                    root = container
                    countdownView = countdown
                    shownFor = packageName
                    setCountdownText(nextResetMillis)
                }
        }
    }

    fun updateCountdown(nextResetMillis: Long) {
        main.post { if (root != null) setCountdownText(nextResetMillis) }
    }

    fun hide() {
        main.post { removeInternal() }
    }

    private fun setCountdownText(nextResetMillis: Long) {
        val remainingMs = (nextResetMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        val totalMinutes = remainingMs / 60_000L
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        countdownView?.text = "Back in ${hours}h ${minutes}m"
    }

    private fun removeInternal() {
        root?.let { runCatching { windowManager.removeView(it) } }
        root = null
        countdownView = null
        shownFor = null
    }
}
