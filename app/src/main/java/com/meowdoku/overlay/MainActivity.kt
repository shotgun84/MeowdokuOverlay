package com.meowdoku.overlay

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val CAP = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 48, 48, 48) }
        val info = TextView(this).apply {
            text = "1) Grant overlay permission\n2) Grant screen capture\n3) Tap \uD83D\uDC31 Solve floating button in game\nOverlay auto-hides after 10s (tap to hide)."
            textSize = 16f
        }
        val bOverlay = Button(this).apply { text = "1) Allow display over apps" }
        bOverlay.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else Toast.makeText(this, "Overlay OK", Toast.LENGTH_SHORT).show()
        }
        val bCap = Button(this).apply { text = "2) Allow capture + start button" }
        bCap.setOnClickListener {
            val pm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(pm.createScreenCaptureIntent(), CAP)
        }
        layout.addView(info); layout.addView(bOverlay); layout.addView(bCap)
        setContentView(layout)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CAP && resultCode == RESULT_OK && data != null) {
            OverlayService.projectionIntent = data.clone() as Intent
            val svc = Intent(this, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc) else startService(svc)
            Toast.makeText(this, "Floating button started — open Meowdoku", Toast.LENGTH_LONG).show()
        } else if (requestCode == CAP) {
            Toast.makeText(this, "Capture denied", Toast.LENGTH_LONG).show()
        }
    }
}
