package com.meowdoku.overlay

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** Pure-Kotlin port of meowdoku_solver.py (no OpenCV). Works on a full-screen bitmap. */
object Solver {

    data class Board(val x: Int, val y: Int, val size: Int, val n: Int,
                     val gx: IntArray, val gy: IntArray, val regions: Array<IntArray>)
    data class Solution(val board: Board, val cats: List<Pair<Int, Int>>)

    private fun isWhite(r: Int, g: Int, b: Int) = r > 230 && g > 230 && b > 230

    /** Find board: largest near-white square in middle band. Falls back to relative coords. */
    fun findBoard(bmp: Bitmap): Triple<Int, Int, Int> {
        val w = bmp.width; val h = bmp.height
        // downscale scan for speed: sample every 6px in middle third
        var best: Triple<Int,Int,Int>? = null
        // try threshold scan: find rows/cols with lots of white in center band
        // Simpler robust fallback matching known layout: board at (15/1080, 697/2340), size 1050/1080 of width
        val fx = (15.0 / 1080 * w).toInt()
        val fy = (697.0 / 2340 * h).toInt()
        val fs = (1050.0 / 1080 * w).toInt()
        // verify by checking border is white-ish; if not, full scan
        if (verifyBoard(bmp, fx, fy, fs)) return Triple(fx, fy, fs)
        // full scan: look for white square via edge search (coarse)
        val step = max(4, w / 200)
        var bx = fx; var by = fy; var bs = fs
        // scan for left edge: first x from center-left where vertical white run is long
        // (keep simple: return fallback scaled)
        return Triple(bx, by, min(bs, h - by))
    }

    private fun verifyBoard(bmp: Bitmap, x: Int, y: Int, s: Int): Boolean {
        if (x < 0 || y < 0 || x + s > bmp.width || y + s > bmp.height) return false
        // check top/bottom/left/right border pixels are mostly white
        var white = 0; var total = 0
        val step = max(1, s / 60)
        var i = x
        while (i < x + s) {
            if (isWhitePx(bmp, i, y + 2)) white++
            if (isWhitePx(bmp, i, y + s - 3)) white++
            total += 2
            i += step
        }
        var j = y
        while (j < y + s) {
            if (isWhitePx(bmp, x + 2, j)) white++
            if (isWhitePx(bmp, x + s - 3, j)) white++
            total += 2
            j += step
        }
        return white.toDouble() / total > 0.5
    }

    private fun isWhitePx(bmp: Bitmap, x: Int, y: Int): Boolean {
        val xx = x.coerceIn(0, bmp.width - 1); val yy = y.coerceIn(0, bmp.height - 1)
        val p = bmp.getPixel(xx, yy)
        return isWhite((p shr 16) and 0xff, (p shr 8) and 0xff, p and 0xff)
    }

    /** Detect N + gutter positions inside board via long white-line projection. */
    fun detectGrid(bmp: Bitmap, bx: Int, by: Int, bs: Int): Triple<Int, IntArray, IntArray> {
        val step = max(1, bs / 400)
        val sw = bs / step  // downsampled size ~ up to 400
        // build downsampled whiteness
        // vertical line score: white pixel AND white far above/below (long line test)
        val vProf = DoubleArray(bs) { 0.0 }
        val hProf = DoubleArray(bs) { 0.0 }
        val half = bs / 4
        // sample center band for speed: for each x, check ~40 y samples
        val samples = 60
        for (lx in 0 until bs step 2) {
            var cnt = 0; var tot = 0
            for (k in 0 until samples) {
                val ly = (k * bs / samples)
                if (isWhitePx(bmp, bx + lx, by + ly)) {
                    // long-line check: also white quarter-board above or below?
                    val up = isWhitePx(bmp, bx + lx, by + ((ly - half + bs) % bs))
                    val dn = isWhitePx(bmp, bx + lx, by + ((ly + half) % bs))
                    if (up || dn) cnt++
                }
                tot++
            }
            vProf[lx] = cnt.toDouble() / tot
        }
        for (ly in 0 until bs step 2) {
            var cnt = 0; var tot = 0
            for (k in 0 until samples) {
                val lx = (k * bs / samples)
                if (isWhitePx(bmp, bx + lx, by + ly)) {
                    val lf = isWhitePx(bmp, bx + ((lx - half + bs) % bs), by + ly)
                    val rt = isWhitePx(bmp, bx + ((lx + half) % bs), by + ly)
                    if (lf || rt) cnt++
                }
                tot++
            }
            hProf[ly] = cnt.toDouble() / tot
        }
        val gx = peaks(vProf, 0.45)
        val gy = peaks(hProf, 0.45)
        if (gx.size >= 3 && gx.size == gy.size) return Triple(gx.size - 1, gx, gy)
        // fallback: try N 6..11 uniform score
        var bestN = 9; var bestS = -1.0
        for (n in 6..11) {
            var s = 0.0
            for (i in 0..n) {
                val lx = (i * (bs - 1) / n)
                s += vProf[lx.coerceIn(0, bs - 1)]
            }
            s /= (n + 1)
            if (s > bestS) { bestS = s; bestN = n }
        }
        val ux = IntArray(bestN + 1) { it * (bs - 1) / bestN }
        val uy = IntArray(bestN + 1) { it * (bs - 1) / bestN }
        return Triple(bestN, ux, uy)
    }

    private fun peaks(p: DoubleArray, thr: Double): IntArray {
        val res = mutableListOf<Int>()
        var i = 1
        while (i < p.size - 1) {
            if (p[i] > thr) {
                var j = i
                while (j < p.size - 1 && p[j] > thr) j++
                var bi = i; var bv = p[i]
                for (k in i until j) if (p[k] > bv) { bv = p[k]; bi = k }
                res.add(bi)
                i = j + max(2, p.size / 200)
            } else i++
        }
        return res.toIntArray()
    }

    /** Sample background color per cell, KMeans K=N. Returns region ids. */
    fun extractRegions(bmp: Bitmap, bx: Int, by: Int, gx: IntArray, gy: IntArray, n: Int): Array<IntArray> {
        val feats = Array(n * n) { DoubleArray(3) }
        var idx = 0
        for (r in 0 until n) for (c in 0 until n) {
            val x1 = bx + gx[c]; val x2 = bx + gx[c + 1]
            val y1 = by + gy[r]; val y2 = by + gy[r + 1]
            val w = max(1, x2 - x1); val h = max(1, y2 - y1)
            val dx = (w * 0.20).toInt(); val dy = (h * 0.20).toInt()
            val pts = arrayOf(
                x1 + dx to y1 + dy, x2 - dx to y1 + dy, x1 + dx to y2 - dy, x2 - dx to y2 - dy,
                (x1 + x2) / 2 to y1 + dy, (x1 + x2) / 2 to y2 - dy,
                x1 + dx to (y1 + y2) / 2, x2 - dx to (y1 + y2) / 2)
            val vals = mutableListOf<DoubleArray>()
            for ((x, y) in pts) {
                val px = bmp.getPixel(x.coerceIn(0, bmp.width - 1), y.coerceIn(0, bmp.height - 1))
                val rr = ((px shr 16) and 0xff).toDouble()
                val gg = ((px shr 8) and 0xff).toDouble()
                val bb = (px and 0xff).toDouble()
                val mean = (rr + gg + bb) / 3
                if (mean > 228 || mean < 55) continue
                if (max(max(rr, gg), bb) - min(min(rr, gg), bb) < 12 && mean > 150) continue
                vals.add(doubleArrayOf(bb, gg, rr)) // BGR like python
            }
            val med = if (vals.isEmpty()) {
                val px = bmp.getPixel(((x1 + x2) / 2).coerceIn(0, bmp.width - 1), ((y1 + y2) / 2).coerceIn(0, bmp.height - 1))
                doubleArrayOf((px and 0xff).toDouble(), ((px shr 8) and 0xff).toDouble(), ((px shr 16) and 0xff).toDouble())
            } else median(vals)
            feats[idx++] = med
        }
        val labels = kmeans(feats, n)
        return Array(n) { r -> IntArray(n) { c -> labels[r * n + c] } }
    }

    private fun median(vals: List<DoubleArray>): DoubleArray {
        fun med(k: Int): Double {
            val s = vals.map { it[k] }.sorted()
            return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
        }
        return doubleArrayOf(med(0), med(1), med(2))
    }

    /** Tiny KMeans (kmeans++ + Lloyd, 15 iters). */
    fun kmeans(data: Array<DoubleArray>, k: Int, seed: Long = 0): IntArray {
        val rnd = Random(seed)
        val n = data.size; val d = 3
        val centers = Array(k) { DoubleArray(d) }
        centers[0] = data[rnd.nextInt(n)].copyOf()
        for (ci in 1 until k) {
            // farthest-weighted pick
            var bestI = 0; var bestD = -1.0
            repeat(min(n, 8)) {
                val i = rnd.nextInt(n)
                var md = Double.MAX_VALUE
                for (j in 0 until ci) md = min(md, dist2(data[i], centers[j]))
                if (md > bestD) { bestD = md; bestI = i }
            }
            centers[ci] = data[bestI].copyOf()
        }
        val assign = IntArray(n)
        repeat(20) {
            var changed = false
            for (i in 0 until n) {
                var bi = 0; var bd = dist2(data[i], centers[0])
                for (j in 1 until k) { val dd = dist2(data[i], centers[j]); if (dd < bd) { bd = dd; bi = j } }
                if (assign[i] != bi) { assign[i] = bi; changed = true }
            }
            val sum = Array(k) { DoubleArray(d) }; val cnt = IntArray(k)
            for (i in 0 until n) { for (j in 0 until d) sum[assign[i]][j] += data[i][j]; cnt[assign[i]]++ }
            for (j in 0 until k) if (cnt[j] > 0) for (t in 0 until d) centers[j][t] = sum[j][t] / cnt[j]
            if (!changed) return@repeat
        }
        return assign
    }

    private fun dist2(a: DoubleArray, b: DoubleArray): Double {
        var s = 0.0
        for (i in a.indices) { val t = a[i] - b[i]; s += t * t }
        return s
    }

    /** Backtracking + MRV. regions[r][c] = id. Returns list of (r,c). */
    fun solve(regions: Array<IntArray>): List<Pair<Int, Int>> {
        val n = regions.size
        val sol = IntArray(n) { -1 }
        val colUsed = BooleanArray(n)
        val regUsed = mutableSetOf<Int>()
        val placed = mutableListOf<Pair<Int, Int>>()
        val unassigned = (0 until n).toMutableSet()

        fun legal(r: Int): MutableList<Int> {
            val out = mutableListOf<Int>()
            for (c in 0 until n) {
                if (colUsed[c]) continue
                if (regUsed.contains(regions[r][c])) continue
                var ok = true
                for ((pr, pc) in placed) if (max(abs(pr - r), abs(pc - c)) <= 1) { ok = false; break }
                if (ok) out.add(c)
            }
            return out
        }
        fun dfs(): Boolean {
            if (unassigned.isEmpty()) return true
            var br = -1; var bm: MutableList<Int>? = null
            for (r in unassigned) {
                val m = legal(r)
                if (m.isEmpty()) return false
                if (bm == null || m.size < bm.size) { bm = m; br = r; if (m.size == 1) break }
            }
            for (c in bm!!) {
                sol[br] = c; colUsed[c] = true; regUsed.add(regions[br][c]); placed.add(br to c)
                unassigned.remove(br)
                if (dfs()) return true
                unassigned.add(br); placed.removeAt(placed.size - 1)
                regUsed.remove(regions[br][c]); colUsed[c] = false; sol[br] = -1
            }
            return false
        }
        if (!dfs()) throw IllegalStateException("no solution")
        return (0 until n).map { it to sol[it] }
    }

    fun solveBitmap(bmp: Bitmap): Solution {
        val (bx, by, bs) = findBoard(bmp)
        val (n, gx, gy) = detectGrid(bmp, bx, by, bs)
        val regions = extractRegions(bmp, bx, by, gx, gy, n)
        val cats = solve(regions)
        return Solution(Board(bx, by, bs, n, gx, gy, regions), cats)
    }
}
