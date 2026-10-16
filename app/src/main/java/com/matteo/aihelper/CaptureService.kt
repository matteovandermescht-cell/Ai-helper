package com.matteo.aihelper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.WindowManager
import android.widget.Toast
import java.nio.ByteBuffer
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class CaptureService : Service() {
    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var worker: HandlerThread? = null
    private var workerHandler: Handler? = null
    private var displayManager: DisplayManager? = null
    private var lastFrameAt = 0L
    private var fpsWindowStarted = 0L
    private var fpsFrames = 0
    @Volatile private var measuredFps = 0f
    private val processing = AtomicBoolean(false)
    private val detector: Detector = NoModelDetector()
    private val decisionEngine = DecisionEngine()
    private var overlay: OverlayController? = null
    @Volatile private var detectionEnabled = true

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == android.view.Display.DEFAULT_DISPLAY && projection != null) {
                workerHandler?.post { configureDisplay() }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @Suppress("DEPRECATION")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopCapture()
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START) return START_NOT_STICKY
        if (projection != null) return START_NOT_STICKY
        startForeground(NOTIFICATION_ID, notification())
        if (!android.provider.Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Bitte zuerst die Berechtigung für die schwebende Anzeige erteilen.", Toast.LENGTH_LONG).show()
            stopSelf()
            return START_NOT_STICKY
        }
        overlay = OverlayController(
            this,
            onStop = {
                stopCapture()
                stopSelf()
            },
            onDetectionToggle = { detectionEnabled = it },
            onDebugToggle = { /* Debug visibility is handled by the overlay panel. */ },
            onStopAutoPlay = { /* Auto-Play is not implemented or enabled. */ }
        ).also { it.show() }

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                            else intent.getParcelableExtra(EXTRA_DATA)
        if (data == null) { stopCapture(); stopSelf(); return START_NOT_STICKY }
        try {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(resultCode, data)
            projection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    stopCapture()
                    stopSelf()
                }
            }, Handler(mainLooper))
            worker = HandlerThread("AIHelperCapture").also { it.start() }
            workerHandler = Handler(worker!!.looper)
            displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            displayManager?.registerDisplayListener(displayListener, Handler(mainLooper))
            configureDisplay()
        } catch (_: Exception) {
            stopCapture()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun currentMetrics(): DisplayMetrics {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        return metrics
    }

    /** Recreate the ImageReader surface after display-size or rotation changes. */
    private fun configureDisplay() {
        if (projection == null || workerHandler == null) return
        val metrics = currentMetrics()
        val width = metrics.widthPixels.coerceAtLeast(1)
        val height = metrics.heightPixels.coerceAtLeast(1)
        val newReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        newReader.setOnImageAvailableListener({ imageReader -> handleLatestFrame(imageReader) }, workerHandler)
        val oldReader = reader
        reader = newReader
        val display = virtualDisplay
        if (display == null) {
            virtualDisplay = projection?.createVirtualDisplay(
                "AIHelperDisplay", width, height, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, newReader.surface, null, workerHandler
            )
        } else {
            display.resize(width, height, metrics.densityDpi)
            display.setSurface(newReader.surface)
        }
        try { oldReader?.setOnImageAvailableListener(null, null); oldReader?.close() } catch (_: Exception) {}
        overlay?.setCaptureStatus("Aufnahme aktiv · " + width + "x" + height + " · Modell: " + detector.displayName)
    }

    private fun handleLatestFrame(imageReader: ImageReader) {
        if (!processing.compareAndSet(false, true)) {
            try { imageReader.acquireLatestImage()?.close() } catch (_: Exception) {}
            return
        }
        var image: Image? = null
        var fullBitmap: Bitmap? = null
        var inferenceBitmap: Bitmap? = null
        try {
            image = imageReader.acquireLatestImage() ?: return
            val now = SystemClock.elapsedRealtime()
            if (now - lastFrameAt < FRAME_INTERVAL_MS) return
            lastFrameAt = now

            val plane = image.planes.firstOrNull() ?: return
            val buffer: ByteBuffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val paddedWidth = (rowStride / pixelStride).coerceAtLeast(image.width)
            val padded = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
            buffer.rewind()
            padded.copyPixelsFromBuffer(buffer)
            fullBitmap = Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
            if (fullBitmap !== padded) padded.recycle()

            val scale = minOf(1f, MAX_INFERENCE_EDGE.toFloat() / maxOf(fullBitmap!!.width, fullBitmap!!.height))
            inferenceBitmap = if (scale < 1f) Bitmap.createScaledBitmap(
                fullBitmap!!,
                (fullBitmap!!.width * scale).toInt().coerceAtLeast(1),
                (fullBitmap!!.height * scale).toInt().coerceAtLeast(1),
                true
            ) else fullBitmap

            val startNs = SystemClock.elapsedRealtimeNanos()
            val found = detector.detect(inferenceBitmap!!)
            val durationMs = ((SystemClock.elapsedRealtimeNanos() - startNs) / 1_000_000L).coerceAtLeast(0L)
            val resultAt = SystemClock.elapsedRealtime()
            fpsFrames++
            if (fpsWindowStarted == 0L) fpsWindowStarted = resultAt
            val windowMs = resultAt - fpsWindowStarted
            if (windowMs >= 1000L) {
                measuredFps = fpsFrames * 1000f / windowMs
                fpsFrames = 0
                fpsWindowStarted = resultAt
            }
            val rotationDegrees = when ((getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation) {
                android.view.Surface.ROTATION_90 -> 90
                android.view.Surface.ROTATION_180 -> 180
                android.view.Surface.ROTATION_270 -> 270
                else -> 0
            }
            val visibleDetections = if (detectionEnabled && detector.isModelLoaded) found else emptyList()
            val decision = if (detector.isModelLoaded && detectionEnabled) {
                decisionEngine.observe(visibleDetections, resultAt)
            } else {
                DecisionSnapshot(
                    GameState.UNCLEAR, GameState.UNCLEAR, 0,
                    if (!detector.isModelLoaded) "Trockenlauf blockiert: kein trainiertes Modell geladen."
                    else "Erkennung ausgeschaltet: keine neue Zustandsentscheidung."
                )
            }
            overlay?.update(
                visibleDetections,
                inferenceBitmap!!.width,
                inferenceBitmap!!.height,
                image.width,
                image.height,
                durationMs,
                measuredFps,
                rotationDegrees,
                detector.displayName,
                detector.isModelLoaded,
                decision
            )

            // Make a distinct preview bitmap; the worker bitmap is recycled below.
            val previewCopy = inferenceBitmap!!.copy(Bitmap.Config.ARGB_8888, false)
            sendBroadcast(
                Intent(ACTION_FRAME).setPackage(packageName)
                    .putExtra(EXTRA_BITMAP, previewCopy)
                    .putExtra(EXTRA_FRAME_WIDTH, image.width)
                    .putExtra(EXTRA_FRAME_HEIGHT, image.height)
            )
        } catch (_: Exception) {
            overlay?.setCaptureStatus("Aufnahme-/Framefehler · Eingaben blockiert")
        } finally {
            image?.close()
            if (inferenceBitmap != null && inferenceBitmap !== fullBitmap) inferenceBitmap?.recycle()
            fullBitmap?.recycle()
            processing.set(false)
        }
    }

    private fun stopCapture() {
        try { displayManager?.unregisterDisplayListener(displayListener) } catch (_: Exception) {}
        displayManager = null
        try { virtualDisplay?.release() } catch (_: Exception) {}
        virtualDisplay = null
        try { reader?.setOnImageAvailableListener(null, null); reader?.close() } catch (_: Exception) {}
        reader = null
        try { projection?.stop() } catch (_: Exception) {}
        projection = null
        worker?.quitSafely()
        worker = null
        workerHandler = null
        processing.set(false)
        overlay?.stop()
        overlay = null
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else @Suppress("DEPRECATION") stopForeground(true)
    }

    private fun notification(): Notification {
        val channelId = "ai_helper_capture"
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(
            NotificationChannel(channelId, "AI Helper Bildschirmaufnahme", NotificationManager.IMPORTANCE_LOW)
        )
        val stop = Intent(this, CaptureService::class.java).setAction(ACTION_STOP)
        val pending = android.app.PendingIntent.getService(this, 1, stop,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) android.app.PendingIntent.FLAG_IMMUTABLE else 0)
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, channelId) else Notification.Builder(this)
        return builder.setContentTitle("AI Helper aktiv")
            .setContentText("Bildschirmaufnahme läuft · keine Speicherung oder Übertragung")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Stopp", pending).build())
            .build()
    }

    override fun onDestroy() { stopCapture(); super.onDestroy() }

    companion object {
        const val ACTION_START = "com.matteo.aihelper.START"
        const val ACTION_STOP = "com.matteo.aihelper.STOP"
        const val ACTION_FRAME = "com.matteo.aihelper.FRAME"
        const val EXTRA_BITMAP = "frameBitmap"
        const val EXTRA_FRAME_WIDTH = "frameWidth"
        const val EXTRA_FRAME_HEIGHT = "frameHeight"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_DATA = "projectionData"
        const val NOTIFICATION_ID = 701
        private const val MAX_INFERENCE_EDGE = 480
        private const val FRAME_INTERVAL_MS = 200L
    }
}
