package com.matteo.aihelper

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var status: TextView
    private val captureRequest = 4102

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 32, 28, 32)
            setBackgroundColor(0xFF10141D.toInt())
        }
        fun label(text: String, size: Float, color: Int = 0xFFFFFFFF.toInt()) =
            TextView(this).apply { this.text = text; textSize = size; setTextColor(color); setPadding(0, 12, 0, 12) }
        root.addView(label("AI Helper", 30f))
        root.addView(label("Visueller Spielassistent · Prototyp", 16f, 0xFFB8C3D9.toInt()))
        status = label("Aufnahme gestoppt", 17f, 0xFF80CBC4.toInt())
        root.addView(status)
        root.addView(label("Detector: Kein Modell geladen", 15f, 0xFFFFD180.toInt()))
        root.addView(label("Bildschirmbilder werden nicht gespeichert oder übertragen. Audio wird nicht aufgenommen.", 14f, 0xFFB8C3D9.toInt()))
        root.addView(Button(this).apply {
            text = "Bildschirmfreigabe starten"
            setOnClickListener {
                val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                startActivityForResult(manager.createScreenCaptureIntent(), captureRequest)
            }
        })
        root.addView(Button(this).apply {
            text = "Aufnahme stoppen"
            setOnClickListener {
                stopService(Intent(this@MainActivity, CaptureService::class.java))
                status.text = "Aufnahme gestoppt"
            }
        })
        setContentView(root)
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
            startForegroundService(service)
            status.text = "Bildschirmaufnahme aktiv · nur lokal"
        } else status.text = "Bildschirmfreigabe abgebrochen"
    }
}
