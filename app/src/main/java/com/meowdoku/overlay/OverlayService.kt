package com.meowdoku.overlay

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlin.math.abs

class OverlayService : Service() {

    companion object {
        var projectionIntent: Intent? = null
        var projectionCode: Int = Activity.RESULT_OK
        const val CH = "solver"
        const val NOTIF = 1
    }

    private lateinit var wm: WindowManager
    private var fab: Button? = null
    private var overlay: View? = null
    private var mediaProjection: MediaProjection? = null
    private var captureError: String? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startFg()
        addFab()
        val pm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        try {
            mediaProjection = pm.getMediaProjection(projectionCode, projectionIntent!!)
        } catch (e: Exception) {
            captureError = "projection: ${e.message}"
            Toast.makeText(this, "Capture permission lost, reopen solver app (${e.message})", Toast.LENGTH_LONG).show()
        }
    }

    private fun startFg() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CH, "solver", NotificationManager.IMPORTANCE_MIN))
        val n: Notification = NotificationCompat.Builder(this, CH)
            .setContentTitle("Meowdoku solver running").setSmallIcon(android.R.drawable.ic_dialog_info).build()
        startForeground(NOTIF, n)
    }

    private fun fabParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.END; x = 20; y = 140
        }
    }

    private fun addFab() {
        val b = Button(this).apply { text = "\uD83D\uDC31 SOLVE" }
        b.setOnClickListener { captureAndSolve() }
        // drag-to-move with proper click-vs-drag disambiguation
        var downX = 0f; var downY = 0f; var moved = false
        b.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; moved = false
                    false // let button show press state
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!moved && (abs(e.rawX - downX) + abs(e.rawY - downY) > 24)) moved = true
                    if (moved) {
                        val p = v.layoutParams as WindowManager.LayoutParams
                        // rawX/rawY include status bar offset; keep button near finger
                        p.gravity = Gravity.TOP or Gravity.START
                        p.x = (e.rawX - v.width / 2).toInt()
                        p.y = (e.rawY - v.height).toInt()
                        try { wm.updateViewLayout(v, p) } catch (_: Exception) {}
                    }
                    moved // consume moves only when dragging
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) true // swallow up after drag so no click fires
                    else false // normal click
                }
                else -> false
            }
        }
        fab = b
        try { wm.addView(b, fabParams()) } catch (e: Exception) {
            Toast.makeText(this, "Enable 'Display over other apps' for this app", Toast.LENGTH_LONG).show()
        }
    }

    private fun captureAndSolve() {
        captureError?.let {
            showError("No capture session:\n$it\n\nReopen the Solver app and grant capture again.")
            return
        }
        if (mediaProjection == null) {
            showError("No capture session (null).\nReopen the Solver app and grant capture again.")
            return
        }
        Toast.makeText(this, "Capturing…", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                var bmp: Bitmap? = null
                var lastErr = ""
                repeat(4) { attempt ->
                    try { bmp = screenshot() } catch (e: Exception) { lastErr = e.message ?: e.toString() }
                    if (bmp == null) Thread.sleep(500)
                }
                if (bmp == null) throw IllegalStateException("screenshot empty after 4 tries. $lastErr")
                val shot: Bitmap = bmp
                handler.post { Toast.makeText(this, "Solving…", Toast.LENGTH_SHORT).show() }
                val sol = Solver.solveBitmap(shot)
                handler.post { showOverlay(sol) }
            } catch (e: Exception) {
                handler.post { showError("Solve failed:\n${e.message}\n\nSend a screenshot of this to the dev.") }
            }
        }.start()
    }

    private fun screenshot(): Bitmap? {
        val mp = mediaProjection ?: return null
        val metrics = resources.displayMetrics
        val w = metrics.widthPixels; val h = metrics.heightPixels; val dpi = metrics.densityDpi
        val reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        val vd: VirtualDisplay = mp.createVirtualDisplay("cap", w, h, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.surface, null, null)
        try {
            Thread.sleep(700)
            val img = reader.acquireLatestImage() ?: return null
            try {
                val plane = img.planes[0]; val buf = plane.buffer
                val rowStride = plane.rowStride; val pixStride = plane.pixelStride
                val rowW = rowStride / pixStride
                val tmp = Bitmap.createBitmap(rowW, h, Bitmap.Config.ARGB_8888)
                tmp.copyPixelsFromBuffer(buf)
                return Bitmap.createBitmap(tmp, 0, 0, w, h)
            } finally { img.close() }
        } finally { vd.release(); reader.close() }
    }

    private fun overlayParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START }
    }

    private fun showOverlay(sol: Solver.Solution) {
        removeOverlay()
        val v = CatOverlayView(this, sol.board.x, sol.board.y, sol.board.gx, sol.board.gy, sol.cats)
        v.setOnClickListener { removeOverlay() }
        overlay = v
        wm.addView(v, overlayParams())
        Toast.makeText(this, "${sol.board.n}x${sol.board.n} solved — tap cats to hide", Toast.LENGTH_SHORT).show()
        handler.postDelayed({ removeOverlay() }, 10_000)
    }

    /** Big red error box so failures are visible and reportable. Tap to dismiss. */
    private fun showError(msg: String) {
        removeOverlay()
        val tv = TextView(this).apply {
            text = "😿 $msg"
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.MONOSPACE
            setBackgroundColor(Color.argb(230, 180, 30, 30))
            setPadding(40, 40, 40, 40)
        }
        tv.setOnClickListener { removeOverlay() }
        overlay = tv
        val p = overlayParams().apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or Gravity.START
        }
        wm.addView(tv, p)
        Toast.makeText(this, msg.take(120), Toast.LENGTH_LONG).show()
        handler.postDelayed({ removeOverlay() }, 12_000)
    }

    private fun removeOverlay() {
        overlay?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        overlay = null
    }

    override fun onDestroy() {
        fab?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        removeOverlay()
        try { mediaProjection?.stop() } catch (_: Exception) {}
        super.onDestroy()
    }
}
