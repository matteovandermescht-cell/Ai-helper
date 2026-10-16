package com.matteo.aihelper

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var preview: ImageView
    private lateinit var previewStatus: TextView
    private var receiverRegistered = false
    private val captureRequest = 4102
    private val frameReceiver = object : BroadcastReceiver() {
        @Suppress("DEPRECATION")
        override fun onReceive(context: Context?, intent: Intent?) {
            val bitmap: Bitmap? = if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra(CaptureService.EXTRA_BITMAP, Bitmap::class.java)
                                  else intent?.getParcelableExtra(CaptureService.EXTRA_BITMAP)
            if (bitmap != null) {
                preview.setImageBitmap(bitmap)
                previewStatus.text = "Lokale Live-Vorschau · keine Speicherung oder Übertragung"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(0xFF10141D.toInt())
        }
        fun label(text: String, size: Float, color: Int = 0xFFFFFFFF.toInt()) =
            TextView(this).apply { this.text = text; textSize = size; setTextColor(color); setPadding(0, 8, 0, 8) }
        root.addView(label("AI Helper", 30f))
        root.addView(label("Visueller Spielassistent · Prototyp", 16f, 0xFFB8C3D9.toInt()))
        status = label("Aufnahme gestoppt", 17f, 0xFF80CBC4.toInt())
        root.addView(status)
        root.addView(label("Detector: Kein Modell geladen", 15f, 0xFFFFD180.toInt()))
        root.addView(label("Keine Audioaufnahme. Bildschirmbilder bleiben lokal.", 14f, 0xFFB8C3D9.toInt()))
        previewStatus = label("Vorschau wartet auf Bildschirmfreigabe", 13f, 0xFFB8C3D9.toInt())
        root.addView(previewStatus)
        preview = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(0xFF080A0F.toInt())
            contentDescription = "Lokale Bildschirmvorschau"
        }
        root.addView(preview, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(Button(this).apply {
            text = "Bildschirmfreigabe starten"
            setOnClickListener {
                val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                startActivityForResult(manager.createScreenCaptureIntent(), captureRequest)
            }
        })
        root.addView(Button(this).apply {
            text = "Aufnahme stoppen"
            setOnClickListener { stopCapture() }
        })
        setContentView(root)
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
        stopService(Intent(this, CaptureService::class.java))
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
            status.text = "Bildschirmaufnahme aktiv · nur lokal"
        } else status.text = "Bildschirmfreigabe abgebrochen"
    }
}
