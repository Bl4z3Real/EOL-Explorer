package it.eol.explorer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

/** Barra di caricamento sottile: sfondo blu notte, avanzamento giallo. */
class ProgressStrip(context: Context) : View(context) {
    private val paint = Paint()

    var progress: Int = 0
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        paint.color = Pal.NAVY
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        if (progress in 1..99) {
            paint.color = Pal.YELLOW
            canvas.drawRect(0f, 0f, width * progress / 100f, height.toFloat(), paint)
        }
    }
}
