package it.eol.explorer

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/** Colori della tavolozza "anni 90" di EOL Explorer. */
object Pal {
    val FACE = 0xFFC0C0C0.toInt()
    val FACE_LIGHT = 0xFFDFDFDF.toInt()
    val TAB = 0xFF9A9A9A.toInt()
    val HI = 0xFFFFFFFF.toInt()
    val LO = 0xFF808080.toInt()
    val DK = 0xFF000000.toInt()
    val NAVY = 0xFF0E1F2B.toInt()
    val BLUE = 0xFF1F5478.toInt()
    val YELLOW = 0xFFF2E600.toInt()
}

/** Bordo "in rilievo" o "incassato" in stile Windows 98. */
class BevelDrawable(
    private val fill: Int,
    private val sunken: Boolean,
    private val edge: Float
) : Drawable() {

    private val paint = Paint()

    override fun draw(canvas: Canvas) {
        val r = bounds
        val l = r.left.toFloat()
        val t = r.top.toFloat()
        val rt = r.right.toFloat()
        val b = r.bottom.toFloat()
        paint.style = Paint.Style.FILL
        paint.color = fill
        canvas.drawRect(l, t, rt, b, paint)
        if (sunken) {
            ring(canvas, l, t, rt, b, Pal.LO, Pal.HI)
            ring(canvas, l + edge, t + edge, rt - edge, b - edge, Pal.DK, Pal.FACE_LIGHT)
        } else {
            ring(canvas, l, t, rt, b, Pal.HI, Pal.DK)
            ring(canvas, l + edge, t + edge, rt - edge, b - edge, Pal.FACE_LIGHT, Pal.LO)
        }
    }

    private fun ring(c: Canvas, l: Float, t: Float, r: Float, b: Float, topLeft: Int, bottomRight: Int) {
        paint.color = topLeft
        c.drawRect(l, t, r - edge, t + edge, paint)
        c.drawRect(l, t, l + edge, b - edge, paint)
        paint.color = bottomRight
        c.drawRect(l, b - edge, r, b, paint)
        c.drawRect(r - edge, t, r, b, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}
