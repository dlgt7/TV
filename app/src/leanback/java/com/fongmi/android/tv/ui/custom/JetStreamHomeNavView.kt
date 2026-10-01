package com.fongmi.android.tv.ui.custom

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.KeyEvent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fongmi.android.tv.ui.theme.JetStreamTheme

class JetStreamHomeNavView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AbstractComposeView(context, attrs, defStyleAttr) {

    interface Listener {
        fun onNavClick(key: String)
        fun onNavLongClick(key: String)
    }

    data class NavItem(
        val key: String,
        val text: String,
        val drawableRes: Int
    )

    var listener: Listener? = null
    private val items = mutableStateListOf<NavItem>()
    private var currentSelectedKey by mutableStateOf("")
    private var focusedIndex by mutableStateOf(0)
    private var navFocused by mutableStateOf(false)
    private var centerLongPressed = false

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
    }

    fun setItems(newItems: List<NavItem>) {
        items.clear()
        items.addAll(newItems)
        focusedIndex = if (items.isEmpty()) -1 else focusedIndex.coerceIn(0, items.size - 1)
    }

    fun setSelectedKey(key: String) {
        currentSelectedKey = key
        val index = items.indexOfFirst { it.key == key }
        if (index >= 0 && !navFocused) focusedIndex = index
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        navFocused = gainFocus
        if (gainFocus) normalizeFocus() else centerLongPressed = false
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (items.isEmpty()) return super.dispatchKeyEvent(event)
        return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> if (event.action == KeyEvent.ACTION_DOWN) moveFocus(-1) else super.dispatchKeyEvent(event)
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (event.action == KeyEvent.ACTION_DOWN) moveFocus(1) else super.dispatchKeyEvent(event)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> handleCenterKey(event)
            else -> super.dispatchKeyEvent(event)
        }
    }

    private fun normalizeFocus() {
        if (items.isEmpty()) {
            focusedIndex = -1
            return
        }
        if (focusedIndex in items.indices) return
        val selected = items.indexOfFirst { it.key == currentSelectedKey }
        focusedIndex = if (selected >= 0) selected else 0
    }

    private fun moveFocus(step: Int): Boolean {
        if (items.isEmpty()) return false
        normalizeFocus()
        val next = (focusedIndex + step).coerceIn(0, items.size - 1)
        if (next == focusedIndex) return false
        focusedIndex = next
        return true
    }

    private fun handleCenterKey(event: KeyEvent): Boolean {
        normalizeFocus()
        if (event.action == KeyEvent.ACTION_DOWN && event.isLongPress) {
            centerLongPressed = true
            clickNav(focusedIndex, true)
            return true
        }
        if (event.action == KeyEvent.ACTION_UP) {
            if (centerLongPressed) {
                centerLongPressed = false
            } else {
                clickNav(focusedIndex, false)
            }
            return true
        }
        return true
    }

    private fun clickNav(index: Int, longClick: Boolean) {
        if (index !in items.indices) return
        focusedIndex = index
        val key = items[index].key
        if (longClick) listener?.onNavLongClick(key) else listener?.onNavClick(key)
    }

    @Composable
    override fun Content() {
        JetStreamTheme {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items.forEachIndexed { index, item ->
                    NavButton(
                        item = item,
                        selected = item.key == currentSelectedKey,
                        focused = navFocused && index == focusedIndex,
                        onClick = { clickNav(index, false) },
                        onLongClick = { clickNav(index, true) }
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun NavButton(item: NavItem, selected: Boolean, focused: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
        val interactionSource = remember { MutableInteractionSource() }
        val scale by animateFloatAsState(
            if (focused) 1.04f else 1f,
            animationSpec = tween(120),
            label = "navScale"
        )
        val background by animateColorAsState(
            if (focused) MaterialTheme.colorScheme.onSurface else Color.Transparent,
            animationSpec = tween(120),
            label = "navBackground"
        )
        val contentColor by animateColorAsState(
            when {
                focused -> MaterialTheme.colorScheme.background
                selected -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            animationSpec = tween(120),
            label = "navContent"
        )
        Column(
            modifier = Modifier
                .height(42.dp)
                .widthIn(min = 62.dp)
                .graphicsLayer(scaleX = scale, scaleY = scale)
                .clip(RoundedCornerShape(24.dp))
                .background(background)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = item.text,
                color = contentColor,
                fontSize = 15.sp,
                fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Box(Modifier.width(16.dp).height(2.dp).background(
                if (selected && !focused) contentColor else Color.Transparent,
                RoundedCornerShape(1.dp)
            ))
        }
    }

}
