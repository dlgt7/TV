package com.fongmi.android.tv.ui.custom

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
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
        val p = JetStreamPalette.current()
        val states = arrayOf(
            intArrayOf(-android.R.attr.state_enabled),
            intArrayOf(android.R.attr.state_focused),
            intArrayOf(android.R.attr.state_pressed),
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(android.R.attr.state_selected),
            intArrayOf(android.R.attr.state_activated),
            intArrayOf()
        )
        val base = when (role) {
            Role.TEXT -> Color.TRANSPARENT
            Role.CHOICE -> 0xFF27292E.toInt()
            Role.TONAL -> p.secondaryContainer
        }
        val foreground = when (role) {
            Role.TEXT -> p.primary
            Role.CHOICE -> 0xFFF2F2F2.toInt()
            Role.TONAL -> p.onSecondaryContainer
        }
        backgroundTintList = ColorStateList(states, intArrayOf(0x1FF2F2F2, p.primary,
            p.primaryContainer, p.primaryContainer, p.primaryContainer, p.primaryContainer, base))
        val textColors = ColorStateList(states, intArrayOf(0x61F2F2F2, p.onPrimary,
            p.onPrimaryContainer, p.onPrimaryContainer, p.onPrimaryContainer, p.onPrimaryContainer, foreground))
        setTextColor(textColors)
        iconTint = textColors
        strokeColor = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
            intArrayOf(0xFFF2F2F2.toInt(), Color.TRANSPARENT))
        rippleColor = ColorStateList.valueOf((p.primary and 0x00FFFFFF) or 0x29000000)
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
