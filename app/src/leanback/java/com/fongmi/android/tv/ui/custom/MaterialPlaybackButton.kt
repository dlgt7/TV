package com.fongmi.android.tv.ui.custom

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import com.fongmi.android.tv.R
import com.fongmi.android.tv.ui.theme.JetStreamPalette
import com.google.android.material.button.MaterialButton

/** Material owns drawing, icon placement, ripple, accessibility and click handling. */
open class MaterialPlaybackButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.materialButtonStyle,
    private val role: Role = Role.TONAL
) : MaterialButton(context, attrs, defStyleAttr) {
    enum class Role { TONAL, TEXT, CHOICE }

    init {
        minimumHeight = jetStreamDpInt(48)
        minHeight = minimumHeight
        minimumWidth = 0
        minWidth = 0
        insetTop = 0
        insetBottom = 0
        isAllCaps = false
        includeFontPadding = false
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        gravity = if (role == Role.CHOICE) Gravity.CENTER_VERTICAL or Gravity.START else Gravity.CENTER
        setPaddingRelative(jetStreamDpInt(24), jetStreamDpInt(10), jetStreamDpInt(24), jetStreamDpInt(10))
        iconSize = jetStreamDpInt(20)
        iconPadding = jetStreamDpInt(8)
        iconGravity = ICON_GRAVITY_TEXT_START
        cornerRadius = jetStreamDpInt(if (role == Role.CHOICE) 12 else 24)
        strokeWidth = jetStreamDpInt(2)
        applyJetStreamTypeface()
        applyColors()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        applyColors()
    }

    private fun applyColors() {
        // Neutral control colours, shared with the other playback dialogs.
        backgroundTintList = JetStreamPalette.controlContainer()
        val textColors = JetStreamPalette.controlText()
        setTextColor(textColors)
        iconTint = textColors
        strokeColor = JetStreamPalette.controlOutline()
        rippleColor = jetStreamColorStateList(R.color.jetstream_scrim_medium)
    }
}

class MaterialPlaybackTextButton @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    MaterialPlaybackButton(context, attrs, role = Role.TEXT)

class MaterialPlaybackChoiceButton @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    MaterialPlaybackButton(context, attrs, role = Role.CHOICE)

class MaterialPlaybackIconButton @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    MaterialPlaybackButton(context, attrs) {
    init {
        setPaddingRelative(jetStreamDpInt(14), jetStreamDpInt(14), jetStreamDpInt(14), jetStreamDpInt(14))
        iconPadding = 0
    }
}
