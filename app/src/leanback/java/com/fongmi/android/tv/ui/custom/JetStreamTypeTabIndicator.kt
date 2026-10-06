package com.fongmi.android.tv.ui.custom

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.drawable.GradientDrawable
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.RecyclerView
import com.fongmi.android.tv.R

/** A moving selection mark for the real TypeAdapter pills; no additional focus target. */
class JetStreamTypeTabIndicator : RecyclerView.ItemDecoration() {
    private val mark = GradientDrawable()
    private var animator: ValueAnimator? = null
    private var selectedPosition = RecyclerView.NO_POSITION
    private var currentLeft = Float.NaN
    private var startLeft = 0f
    private var fraction = 1f

    override fun onDrawOver(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        var selected: android.view.View? = null
        for (index in 0 until parent.childCount) {
            val child = parent.getChildAt(index)
            if (child.isSelected && parent.getChildAdapterPosition(child) != RecyclerView.NO_POSITION) {
                selected = child
                break
            }
        }
        val child = selected ?: return
        val position = parent.getChildAdapterPosition(child)
        val width = parent.jetStreamDp(16)
        val targetLeft = child.x + (child.width - width) / 2f
        if (position != selectedPosition) {
            animator?.cancel()
            selectedPosition = position
            if (currentLeft.isNaN()) {
                currentLeft = targetLeft
                fraction = 1f
            } else {
                startLeft = currentLeft
                fraction = 0f
                animator = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 180L
                    interpolator = DecelerateInterpolator()
                    addUpdateListener {
                        fraction = it.animatedValue as Float
                        parent.postInvalidateOnAnimation()
                    }
                    start()
                }
            }
        }
        // Resolve the destination from today's child bounds so scrolling never detaches
        // the mark from its selected pill or creates another scrolling animation.
        currentLeft = if (fraction >= 1f) targetLeft else startLeft + (targetLeft - startLeft) * fraction
        val top = child.y + child.height - parent.jetStreamDp(6)
        mark.cornerRadius = parent.jetStreamDp(1)
        mark.setColor(parent.jetStreamColor(R.color.jetstream_on_surface))
        mark.setBounds(currentLeft.toInt(), top.toInt(), (currentLeft + width).toInt(), (top + parent.jetStreamDp(2)).toInt())
        val checkpoint = canvas.save()
        canvas.clipRect(0, 0, parent.width, parent.height)
        mark.draw(canvas)
        canvas.restoreToCount(checkpoint)
    }

    fun dispose() {
        animator?.cancel()
        animator = null
        selectedPosition = RecyclerView.NO_POSITION
        currentLeft = Float.NaN
    }
}
