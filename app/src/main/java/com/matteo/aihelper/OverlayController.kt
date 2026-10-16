package com.matteo.aihelper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import kotlin.math.max

/** Separate windows ensure the full-screen drawing layer does not consume touches. */
class OverlayController(
    private val context: Context,
    private val onStop: () -> Unit,
    private val onDetectionToggle: (Boolean) -> Unit,
    private val onDebugToggle: (Boolean) -> Unit,
    private val onStopAutoPlay: () -> Unit
) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var drawing: DrawingView? = null
    private var panel: LinearLayout? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var statusText: TextView? = null
    private var debugText: TextView? = null
    private var detectionOn = true
    private var debugOn = false
    private var collapsed = false
    private var resultAt = 0L
    private var debugInfo = "Warte auf Bild"
    private var modelW = 1
    private var modelH = 1
    private var frameW = 1
    private var frameH = 1
    private var currentModelName = "Kein Modell geladen"
    private var currentModelLoaded = false

    private val ticker = object : Runnable {
        override fun run() {
            val age = if (resultAt == 0L) Long.MAX_VALUE else SystemClock.elapsedRealtime() - resultAt
            if (age > STALE_MS) drawing?.setFrame(emptyList(), modelW, modelH, frameW, frameH)
            refreshStatus()
            handler.postDelayed(this, 250L)
        }
    }

    fun show() {
        if (!android.provider.Settings.canDrawOverlays(context)) return
        if (drawing == null) {
            drawing = DrawingView(context)
            val p = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.TOP or Gravity.START }
            wm.addView(drawing, p)
        }
        if (panel == null) createPanel()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        refreshStatus()
    }

    fun update(found: List<Detection>, inferenceWidth: Int, inferenceHeight: Int,
               frameWidth: Int, frameHeight: Int, durationMs: Long?, fps: Float,
               rotation: Int, modelName: String, modelLoaded: Boolean,
               decision: DecisionSnapshot) {
        handler.post {
            modelW = max(1, inferenceWidth)
            modelH = max(1, inferenceHeight)
            frameW = max(1, frameWidth)
            frameH = max(1, frameHeight)
            currentModelName = modelName
            currentModelLoaded = modelLoaded
            resultAt = if (detectionOn && modelLoaded) SystemClock.elapsedRealtime() else 0L
            drawing?.setFrame(if (detectionOn && modelLoaded) found else emptyList(), modelW, modelH, frameW, frameH)
            val classes = found.groupingBy { it.label }.eachCount().entries.joinToString { it.key + "=" + it.value }.ifBlank { "keine" }
            debugInfo = "Frame " + frameW + "x" + frameH + "; Modellbild " + modelW + "x" + modelH +
                "; Rotation " + rotation + " Grad\nModell: " + modelName + "; geladen: " + modelLoaded +
                "\nInference: " + (durationMs?.let { it.toString() + " ms" } ?: "nicht ausgeführt") +
                "; verarbeitete Frames/s: " + String.format(Locale.US, "%.2f", fps) +
                "\nKlassen: " + classes +
                "\nZustand: " + decision.state + " (Kandidat: " + decision.candidate +
                ", Folgebeobachtungen: " + decision.consecutiveObservations + ")" +
                "\nVorschlag: " + decision.suggestion +
                "\nSteuerung: AUS · keine Eingabe wurde ausgelöst"
            refreshStatus()
        }
    }

    fun setCaptureStatus(text: String) {
        handler.post {
            debugInfo = text
            refreshStatus()
        }
    }

    fun stop() {
        handler.removeCallbacks(ticker)
        removePanel()
        drawing?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        drawing = null
        resultAt = 0L
    }

    private fun createPanel() {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(7), dp(6), dp(7), dp(6))
            background = GradientDrawable().apply {
                setColor(0xE619202B.toInt())
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), 0xFF607D8B.toInt())
            }
        }
        val header = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val title = TextView(context).apply {
            text = "BrawlPilot  ⋮"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(4), dp(5), dp(12), dp(5))
            setOnTouchListener(object : View.OnTouchListener {
                private var startX = 0f
                private var startY = 0f
                private var initialX = 0
                private var initialY = 0
                override fun onTouch(v: View, e: MotionEvent): Boolean {
                    val lp = panelParams ?: return false
                    when (e.action) {
                        MotionEvent.ACTION_DOWN -> {
                            startX = e.rawX; startY = e.rawY
                            initialX = lp.x; initialY = lp.y
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            lp.x = initialX + (e.rawX - startX).toInt()
                            lp.y = initialY + (e.rawY - startY).toInt()
                            try { wm.updateViewLayout(panel, lp) } catch (_: Exception) {}
                            return true
                        }
                        MotionEvent.ACTION_UP -> return true
                    }
                    return false
                }
            })
        }
        header.addView(title, LinearLayout.LayoutParams(0, dp(38), 1f))
        // STOPP stays reachable while the panel is collapsed.
        header.addView(button("STOPP", 0xFFB71C1C.toInt()) { onStop() })
        header.addView(button("−") { collapsed = !collapsed; updatePanelBody() })
        header.addView(button("×") {
            // Closing the panel must not leave an invisible capture/overlay session running.
            onStop()
        })
        root.addView(header)
        statusText = TextView(context).apply { setTextColor(0xFFE1F5FE.toInt()); textSize = 11f; setPadding(dp(3), dp(3), dp(3), dp(5)) }
        root.addView(statusText)
        val row1 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(button("Erkennung AN/AUS") {
            detectionOn = !detectionOn
            if (!detectionOn) drawing?.setFrame(emptyList(), modelW, modelH, frameW, frameH)
            onDetectionToggle(detectionOn)
            refreshStatus()
        })
        row1.addView(button("Trockenlauf") {
            Toast.makeText(context, "Trockenlauf: zeigt nur Diagnose und Vorschläge; keine Eingaben werden ausgeführt.", Toast.LENGTH_LONG).show()
            debugOn = true
            debugText?.visibility = View.VISIBLE
        })
        row1.addView(button("Pilotmodus gesperrt") {
            onStopAutoPlay()
            Toast.makeText(context, "Pilotmodus nicht verfügbar: trainiertes Modell, validierte Koordinaten und getestete Eingabesteuerung fehlen. Es wurden keine Eingaben ausgeführt.", Toast.LENGTH_LONG).show()
        }.apply {
            isEnabled = false
            alpha = 0.65f
            contentDescription = "Pilotmodus gesperrt: Modell, Kalibrierung und getestete Eingabesteuerung fehlen"
        })
        root.addView(row1)
        val row2 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(button("Debug anzeigen") {
            debugOn = !debugOn
            debugText?.visibility = if (debugOn) View.VISIBLE else View.GONE
            onDebugToggle(debugOn)
        })
        row2.addView(button("STOPP", 0xFFB71C1C.toInt()) { onStop() })
        root.addView(row2)
        debugText = TextView(context).apply { setTextColor(0xFFFFE0B2.toInt()); textSize = 10f; setPadding(dp(3), dp(5), dp(3), dp(3)); visibility = View.GONE }
        root.addView(debugText)
        panel = root
        panelParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = dp(10); y = dp(70) }
        wm.addView(root, panelParams)
    }

    private fun updatePanelBody() {
        val p = panel ?: return
        for (i in 1 until p.childCount) p.getChildAt(i).visibility = if (collapsed) View.GONE else View.VISIBLE
        p.getChildAt(0).visibility = View.VISIBLE
    }

    private fun refreshStatus() {
        val age = if (resultAt == 0L) null else SystemClock.elapsedRealtime() - resultAt
        val rec = when {
            !currentModelLoaded -> "KEIN MODELL"
            !detectionOn -> "AUS"
            age != null && age <= STALE_MS -> "AN"
            else -> "VERALTET"
        }
        val ageText = if (age == null) "keine Vorhersage" else age.toString() + " ms"
        statusText?.text = "Modus: Beobachten · keine Eingaben" +
            "\nErkennung: " + (if (detectionOn) "AN" else "AUS") +
            " · " + rec + "\nModell: " + currentModelName +
            "\nPilotmodus: GESPERRT · Steuerung nicht implementiert · Ergebnisalter: " + ageText
        debugText?.text = debugInfo + if (age == null) "\nNoch kein Ergebnis" else "\nErgebnisalter aktuell: " + age + " ms"
    }

    private fun button(text: String, color: Int = 0xFF263544.toInt(), action: () -> Unit): Button =
        Button(context).apply {
            this.text = text
            textSize = 10f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply { setColor(color); cornerRadius = dp(7).toFloat() }
            setPadding(dp(5), 0, dp(5), 0)
            setOnClickListener { action() }
        }

    private fun removePanel() {
        panel?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        panel = null; panelParams = null; statusText = null; debugText = null
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private class DrawingView(context: Context) : View(context) {
        private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; textSize = 28f; typeface = Typeface.DEFAULT_BOLD }
        @Volatile private var items: List<Detection> = emptyList()
        @Volatile private var modelWidth = 1
        @Volatile private var modelHeight = 1
        @Volatile private var captureWidth = 1
        @Volatile private var captureHeight = 1

        fun setFrame(found: List<Detection>, mw: Int, mh: Int, cw: Int, ch: Int) {
            items = found.toList()
            modelWidth = max(1, mw); modelHeight = max(1, mh)
            captureWidth = max(1, cw); captureHeight = max(1, ch)
            postInvalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (items.isEmpty()) return
            items.forEach { d ->
                val mapped = CoordinateTransform.mapBox(d, modelWidth, modelHeight, width, height) ?: return@forEach
                val color = when (d.label.lowercase(Locale.ROOT)) {
                    "player" -> 0xFF00E5FF.toInt()
                    "enemy" -> 0xFFFF5252.toInt()
                    "bush" -> 0xFF69F0AE.toInt()
                    "poison_zone" -> 0xFFFFAB40.toInt()
                    else -> 0xFFE040FB.toInt()
                }
                boxPaint.color = color; textPaint.color = color
                val rect = RectF(mapped[0], mapped[1], mapped[2], mapped[3])
                canvas.drawRect(rect, boxPaint)
                canvas.drawText(d.label + " " + String.format(Locale.US, "%.2f", d.confidence),
                    rect.left, (rect.top - 6f).coerceAtLeast(26f), textPaint)
            }
        }
    }

    companion object { private const val STALE_MS = 1000L }
}
