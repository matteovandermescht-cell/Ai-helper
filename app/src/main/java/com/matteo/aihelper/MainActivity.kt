package com.matteo.aihelper

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var preview: ImageView
    private lateinit var previewStatus: TextView
    private lateinit var apiKeyInput: EditText
    private lateinit var imageUrlInput: EditText
    private lateinit var cloudStatus: TextView
    private lateinit var cloudResponse: TextView
    private lateinit var cloudTestButton: Button
    private val cloudTestRunning = AtomicBoolean(false)
    private var receiverRegistered = false
    private var pendingProjectionAfterOverlayPermission = false
    private val captureRequest = 4102

    private val frameReceiver = object : BroadcastReceiver() {
        @Suppress("DEPRECATION")
        override fun onReceive(context: Context?, intent: Intent?) {
            val bitmap: Bitmap? = if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra(CaptureService.EXTRA_BITMAP, Bitmap::class.java)
                                  else intent?.getParcelableExtra(CaptureService.EXTRA_BITMAP)
            if (bitmap != null) {
                preview.setImageBitmap(bitmap)
                previewStatus.text = "Lokale Live-Vorschau · keine Speicherung oder Übertragung"
                status.text = "Aufnahme aktiv · lokales Modell: keines"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24, 18, 24, 18)
            setBackgroundColor(0xFF10141D.toInt())
        }
        fun label(text: String, size: Float, color: Int = 0xFFFFFFFF.toInt()) =
            TextView(this).apply { this.text = text; textSize = size; setTextColor(color); setPadding(0, 6, 0, 6) }
        root.addView(label("AI Helper", 30f))
        root.addView(label("Visueller Spielassistent · Prototyp", 16f, 0xFFB8C3D9.toInt()))
        status = label("Aufnahme gestoppt", 17f, 0xFF80CBC4.toInt())
        root.addView(status)
        root.addView(label("Lokaler Detector: Kein Modell geladen", 15f, 0xFFFFD180.toInt()))
        root.addView(label("Auto-Play: AUS · keine automatische Steuerung", 13f, 0xFFFFCC80.toInt()))

        root.addView(label("Roboflow-Cloud-Test · manuell und einmalig", 18f))
        root.addView(label(
            "Dieser Test sendet nur die unten angegebene Bild-URL an Roboflow. Das aktuelle Bildschirmbild wird NICHT übertragen. API-Schlüssel werden nicht gespeichert und nach dem Test aus dem Feld gelöscht.",
            13f, 0xFFB8C3D9.toInt()
        ))
        apiKeyInput = EditText(this).apply {
            hint = "Temporärer Roboflow API-Schlüssel"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF9AA8BC.toInt())
            setBackgroundColor(0xFF202938.toInt())
            setPadding(14, 10, 14, 10)
        }
        root.addView(apiKeyInput, matchWrap())
        imageUrlInput = EditText(this).apply {
            hint = "Öffentlich erreichbare Bild-URL (https://...)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF9AA8BC.toInt())
            setBackgroundColor(0xFF202938.toInt())
            setPadding(14, 10, 14, 10)
        }
        root.addView(imageUrlInput, matchWrap())
        cloudTestButton = Button(this).apply {
            text = "Roboflow-Test starten (1 Anfrage)"
            setOnClickListener { runCloudTestOnce() }
        }
        root.addView(cloudTestButton, matchWrap())
        cloudStatus = label("Cloud-Test noch nicht ausgeführt", 14f, 0xFF80CBC4.toInt())
        root.addView(cloudStatus)
        cloudResponse = label("", 12f, 0xFFE1F5FE.toInt()).apply { setTextIsSelectable(true) }
        root.addView(cloudResponse, matchWrap())

        root.addView(label("Bildschirm-Overlay", 18f))
        root.addView(label("Keine Audioaufnahme. Bildschirmframes bleiben lokal; nur der manuell gestartete URL-Test nutzt das Netzwerk.", 13f, 0xFFB8C3D9.toInt()))
        previewStatus = label("Zuerst die Berechtigung für die schwebende Anzeige erteilen", 13f, 0xFFB8C3D9.toInt())
        root.addView(previewStatus)
        preview = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(0xFF080A0F.toInt())
            contentDescription = "Lokale Bildschirmvorschau"
        }
        root.addView(preview, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(220)))
        root.addView(Button(this).apply {
            text = "Berechtigung + Bildschirmfreigabe"
            setOnClickListener { beginCapturePermissionFlow() }
        }, matchWrap())
        root.addView(Button(this).apply {
            text = "Aufnahme stoppen"
            setOnClickListener { stopCapture() }
        }, matchWrap())

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        }
        setContentView(scroll)
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun runCloudTestOnce() {
        if (!cloudTestRunning.compareAndSet(false, true)) return
        val key = apiKeyInput.text?.toString().orEmpty()
        val imageUrl = imageUrlInput.text?.toString().orEmpty().trim()
        if (key.isBlank() || imageUrl.isBlank()) {
            cloudStatus.text = "Bitte API-Schlüssel und Bild-URL eingeben."
            cloudTestRunning.set(false)
            return
        }
        cloudTestButton.isEnabled = false
        cloudStatus.text = "Anfrage läuft · maximal eine Anfrage"
        cloudResponse.text = ""
        Thread({
            try {
                val result = RoboflowWorkflowClient.runOnce(key, imageUrl)
                runOnUiThread {
                    cloudStatus.text = "HTTP ${result.httpStatus} · Vorhersageanzahl: ${result.predictionCount?.toString() ?: "im Antwortformat nicht gefunden"} · Ausgabefelder: ${result.outputKeys.joinToString().ifBlank { "keine erkannt" }}"
                    cloudResponse.text = result.rawBody.take(6000).ifBlank { "(Leere Antwort)" }
                    apiKeyInput.text?.clear()
                    cloudTestButton.isEnabled = true
                    cloudTestRunning.set(false)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    cloudStatus.text = "Cloud-Test fehlgeschlagen: ${e.message ?: e.javaClass.simpleName}"
                    cloudResponse.text = "Es wurden keine Erkennungen erfunden. Prüfe Schlüssel, Bild-URL und Netzwerk."
                    apiKeyInput.text?.clear()
                    cloudTestButton.isEnabled = true
                    cloudTestRunning.set(false)
                }
            }
        }, "RoboflowOneShot").start()
    }

    private fun beginCapturePermissionFlow() {
        if (!Settings.canDrawOverlays(this)) {
            pendingProjectionAfterOverlayPermission = true
            previewStatus.text = "Android-Einstellungen öffnen und „Über anderen Apps einblenden“ erlauben."
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName)))
            return
        }
        requestScreenCapture()
    }

    override fun onResume() {
        super.onResume()
        if (pendingProjectionAfterOverlayPermission) {
            pendingProjectionAfterOverlayPermission = false
            if (Settings.canDrawOverlays(this)) requestScreenCapture()
            else previewStatus.text = "Overlay-Berechtigung nicht erteilt; Aufnahme wurde nicht gestartet."
        }
    }

    private fun requestScreenCapture() {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), captureRequest)
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(CaptureService.ACTION_FRAME)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(frameReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(frameReceiver, filter)
        receiverRegistered = true
    }

    override fun onStop() {
        if (receiverRegistered) {
            try { unregisterReceiver(frameReceiver) } catch (_: IllegalArgumentException) {}
            receiverRegistered = false
        }
        super.onStop()
    }

    private fun stopCapture() {
        stopService(Intent(this, CaptureService::class.java).setAction(CaptureService.ACTION_STOP))
        status.text = "Aufnahme gestoppt"
        preview.setImageDrawable(null)
        previewStatus.text = "Vorschau gestoppt"
    }

    @Deprecated("Uses result API for broad compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != captureRequest) return
        if (resultCode == RESULT_OK && data != null) {
            val service = Intent(this, CaptureService::class.java).apply {
                action = CaptureService.ACTION_START
                putExtra(CaptureService.EXTRA_RESULT_CODE, resultCode)
                putExtra(CaptureService.EXTRA_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service) else startService(service)
            status.text = "Bildschirmaufnahme aktiv · lokales Modell: keines"
            previewStatus.text = "Warte auf erstes Bild"
        } else status.text = "Bildschirmfreigabe abgebrochen"
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
