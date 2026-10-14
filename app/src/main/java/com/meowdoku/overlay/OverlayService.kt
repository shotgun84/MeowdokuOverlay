package com.meowdoku.overlay

import android.app.Activity
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
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    companion object {
        var projectionIntent: Intent? = null
        const val CH = "solver"
        const val NOTIF = 1
    }

    private lateinit var wm: WindowManager
    private var fab: Button? = null
    private var overlay: CatOverlayView? = null
    private var mediaProjection: MediaProjection? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startFg()
        addFab()
        val pm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        try { mediaProjection = pm.getMediaProjection(Service.RESULT_OK, projectionIntent!!) }
        catch (e: Exception) { Toast.makeText(this, "Capture permission lost, reopen app", Toast.LENGTH_LONG).show() }
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
            gravity = Gravity.TOP or Gravity.END; x = 20; y = 300
        }
    }

    private fun addFab() {
        val b = Button(this).apply { text = "\uD83D\uDC31 Solve" }
        b.setOnClickListener { captureAndSolve() }
        // crude drag-to-move
        b.setOnTouchListener(object : android.view.View.OnTouchListener {
            var dx = 0; var dy = 0; var down = 0L
            override fun onTouch(v: android.view.View, e: android.view.MotionEvent): Boolean {
                when (e.action) {
                    android.view.MotionEvent.ACTION_DOWN -> { dx = e.rawX.toInt(); dy = e.rawY.toInt(); down = System.currentTimeMillis(); return false }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val p = v.layoutParams as WindowManager.LayoutParams
                        p.x -= (e.rawX.toInt() - dx); p.y = e.rawY.toInt() - dy - 200
                        try { wm.updateViewLayout(v, p) } catch (_: Exception) {}
                        dx = e.rawX.toInt(); dy = e.rawY.toInt(); return true
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        if (System.currentTimeMillis() - down < 250) v.performClick()
                        return true
                    }
                }
                return false
            }
        })
        fab = b
        try { wm.addView(b, fabParams()) } catch (e: Exception) {
            Toast.makeText(this, "Enable 'Display over other apps' for this app", Toast.LENGTH_LONG).show()
        }
    }

    private fun captureAndSolve() {
        Toast.makeText(this, "Capturing…", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val bmp = screenshot() ?: throw IllegalStateException("screenshot failed")
                val sol = Solver.solveBitmap(bmp)
                handler.post { showOverlay(bmp, sol) }
            } catch (e: Exception) {
                handler.post { Toast.makeText(this, "Solve failed: ${e.message}", Toast.LENGTH_LONG).show() }
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
        Thread.sleep(600)
        val img = reader.acquireLatestImage() ?: run { vd.release(); reader.close(); return null }
        val plane = img.planes[0]; val buf = plane.buffer
        val rowStride = plane.rowStride; val pixStride = plane.pixelStride
        val rowW = rowStride / pixStride
        val tmp = Bitmap.createBitmap(rowW, h, Bitmap.Config.ARGB_8888)
        tmp.copyPixelsFromBuffer(buf)
        img.close(); vd.release(); reader.close()
        return Bitmap.createBitmap(tmp, 0, 0, w, h)
    }

    private fun showOverlay(shot: Bitmap, sol: Solver.Solution) {
        removeOverlay()
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE
        // position overlay exactly over screenshot area: screenshots are full-screen so use board coords directly
        val v = CatOverlayView(this, sol.board.x, sol.board.y, sol.board.gx, sol.board.gy, sol.cats)
        v.setOnClickListener { removeOverlay() }
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START }
        // make taps pass except on view: use NOT_FOCUSABLE and let view consume clicks to dismiss
        overlay = v
        wm.addView(v, p)
        Toast.makeText(this, "${sol.board.n}x${sol.board.n} solved — tap cats to hide", Toast.LENGTH_SHORT).show()
        handler.postDelayed({ removeOverlay() }, 10_000)
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
