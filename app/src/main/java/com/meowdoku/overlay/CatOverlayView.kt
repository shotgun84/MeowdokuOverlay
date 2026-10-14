package com.meowdoku.overlay

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View

/** Full-screen overlay: green highlight + cat face per solution cell. Tap to dismiss. */
class CatOverlayView(
    context: android.content.Context,
    private val boardX: Int, private val boardY: Int,
    private val gx: IntArray, private val gy: IntArray,
    private val cats: List<Pair<Int, Int>>
) : View(context) {

    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(210, 46, 204, 113) }
    private val black = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(30, 30, 36) }
    private val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val whiteStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 6f
    }
    private val pupil = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(15, 15, 18) }
    private val nose = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 150, 170) }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for ((r, c) in cats) {
            val x1 = (boardX + gx[c]).toFloat(); val x2 = (boardX + gx[c + 1]).toFloat()
            val y1 = (boardY + gy[r]).toFloat(); val y2 = (boardY + gy[r + 1]).toFloat()
            val pad = (x2 - x1) * 0.04f
            val rect = RectF(x1 + pad, y1 + pad, x2 - pad, y2 - pad)
            canvas.drawRoundRect(rect, (x2 - x1) * 0.2f, (x2 - x1) * 0.2f, glow)
            drawCat(canvas, (x1 + x2) / 2, (y1 + y2) / 2, minOf(x2 - x1, y2 - y1) * 0.82f)
        }
    }

    private fun drawCat(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val r = s / 2
        whiteStroke.strokeWidth = maxOf(3f, s * 0.045f)
        // ears
        fun ear(dir: Int) {
            val p = Path()
            p.moveTo(cx + dir * r * 0.75f, cy - r * 0.35f)
            p.lineTo(cx + dir * r * 0.55f, cy - r * 1.1f)
            p.lineTo(cx + dir * r * 0.05f, cy - r * 0.62f)
            p.close()
            canvas.drawPath(p, black)
        }
        ear(-1); ear(1)
        canvas.drawCircle(cx, cy, r, black)
        canvas.drawCircle(cx, cy, r, whiteStroke)
        // muzzle
        canvas.drawOval(RectF(cx - s * 0.26f, cy + r * 0.05f, cx + s * 0.26f, cy + r * 0.05f + s * 0.34f), white)
        // eyes
        for (sx in intArrayOf(-1, 1)) {
            val ex = cx + sx * r * 0.38f; val ey = cy - r * 0.12f
            val er = s * 0.11f
            canvas.drawCircle(ex, ey, er, white)
            canvas.drawCircle(ex, ey, er * 0.55f, pupil)
            canvas.drawCircle(ex - er * 0.1f, ey - er * 0.15f, er * 0.18f, white)
        }
        // nose
        val nx = cx; val ny = cy + r * 0.28f; val nr = s * 0.045f
        val p = Path()
        p.moveTo(nx - nr, ny - nr * 0.6f); p.lineTo(nx + nr, ny - nr * 0.6f); p.lineTo(nx, ny + nr * 0.7f)
        p.close()
        canvas.drawPath(p, nose)
    }
}
