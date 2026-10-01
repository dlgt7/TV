package com.fongmi.android.tv.ui.custom

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.AttributeSet
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fongmi.android.tv.R
import com.fongmi.android.tv.ui.theme.JetStreamAnimations
import com.fongmi.android.tv.ui.theme.JetStreamTheme
import kotlinx.coroutines.delay

class JetStreamPushView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AbstractComposeView(context, attrs, defStyleAttr) {

    interface Listener {
        fun onCopyAddress(): Boolean
        fun onPushClipboard(): Boolean
        fun onOpenAddress()
    }

    private var addressText by mutableStateOf("")
    private var qrImage by mutableStateOf<Bitmap?>(null)
    private var copied by mutableStateOf(false)
    private var contentFocused by mutableStateOf(false)
    private var listener: Listener? = null
    private val hideCopied = Runnable { copied = false }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
    }

    fun setAddress(address: String) {
        addressText = address
    }

    fun setQrBitmap(bitmap: Bitmap?) {
        qrImage = bitmap
    }

    fun setListener(listener: Listener?) {
        this.listener = listener
    }

    fun showCopied() {
        removeCallbacks(hideCopied)
        copied = true
        postDelayed(hideCopied, 2000L)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(hideCopied)
        super.onDetachedFromWindow()
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        contentFocused = gainFocus
    }

    @Composable
    override fun Content() {
        JetStreamTheme {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 48.dp, vertical = 48.dp)
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                ) {
                    val availableWidth = maxWidth
                    val compactLandscape = maxHeight < 640.dp && availableWidth > maxHeight * 1.4f
                    if (compactLandscape) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .fillMaxWidth()
                                .widthIn(max = 900.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(40.dp)
                        ) {
                            PushInfoPanel(
                                modifier = Modifier
                                    .weight(1f)
                                    .widthIn(max = 460.dp),
                                compact = true
                            )
                            QrPanel(
                                modifier = Modifier.width(if (availableWidth < 820.dp) 320.dp else 360.dp),
                                compact = true
                            )
                        }
                    } else if (maxWidth < 920.dp) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .widthIn(max = 520.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(48.dp)
                        ) {
                            QrPanel(Modifier.fillMaxWidth())
                            PushInfoPanel(Modifier.fillMaxWidth())
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .fillMaxWidth()
                                .widthIn(max = 1152.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(96.dp)
                        ) {
                            PushInfoPanel(
                                modifier = Modifier
                                    .weight(1f)
                                    .widthIn(max = 520.dp)
                            )
                            QrPanel(Modifier.width(496.dp))
                        }
                    }
                }
                CopyToast(
                    visible = copied,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }
    }

    @Composable
    private fun PushInfoPanel(modifier: Modifier, compact: Boolean = false) {
        val primaryFocusRequester = remember { FocusRequester() }
        LaunchedEffect(contentFocused) {
            if (contentFocused) runCatching { primaryFocusRequester.requestFocus() }
        }
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(if (compact) 24.dp else 40.dp)
        ) {
            PushHeader(compact)
            Column(verticalArrangement = Arrangement.spacedBy(if (compact) 16.dp else 24.dp)) {
                AddressBlock(compact)
                PushClipboardButton(primaryFocusRequester, compact)
            }
            HintRow(compact)
        }
    }

    @Composable
    private fun PushHeader(compact: Boolean = false) {
        val colorScheme = MaterialTheme.colorScheme
        Column(verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_push_cast),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = colorScheme.primary
                )
                Text(
                    text = stringResource(id = R.string.push_service).uppercase(),
                    color = colorScheme.primary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    letterSpacing = 1.4.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = stringResource(id = R.string.push_title),
                color = colorScheme.onSurface,
                fontSize = if (compact) 40.sp else 60.sp,
                lineHeight = if (compact) 44.sp else 64.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(id = R.string.push_desc),
                modifier = Modifier.widthIn(max = 448.dp),
                color = colorScheme.onSurfaceVariant,
                fontSize = if (compact) 16.sp else 20.sp,
                lineHeight = if (compact) 22.sp else 30.sp,
                fontWeight = FontWeight.Normal
            )
        }
    }

    @Composable
    private fun AddressBlock(compact: Boolean = false) {
        val colorScheme = MaterialTheme.colorScheme
        val supportingColor = colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
        Column(verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_push_monitor),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = supportingColor
                )
                Text(
                    text = stringResource(id = R.string.push_browser_address),
                    color = supportingColor,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(colorScheme.surface)

                    .padding(
                        start = if (compact) 20.dp else 24.dp,
                        top = if (compact) 4.dp else 8.dp,
                        bottom = if (compact) 4.dp else 8.dp,
                        end = if (compact) 4.dp else 8.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = addressText,
                    modifier = Modifier.weight(1f),
                    color = colorScheme.primary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 18.sp,
                    lineHeight = 24.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(8.dp))
                CopyAddressButton(compact)
            }
        }
    }

    @Composable
    private fun CopyAddressButton(compact: Boolean = false) {
        val colorScheme = MaterialTheme.colorScheme
        val interactionSource = remember { MutableInteractionSource() }
        val focused by interactionSource.collectIsFocusedAsState()
        val scale by animateFloatAsState(
            targetValue = if (focused) 1.08f else 1f,
            animationSpec = JetStreamAnimations.ScaleSpring,
            label = "pushCopyScale"
        )
        val background by animateColorAsState(
            targetValue = if (focused) colorScheme.outlineVariant else Color.Transparent,
            animationSpec = JetStreamAnimations.ColorTween,
            label = "pushCopyBackground"
        )
        Box(
            modifier = Modifier
                .size(if (compact) 44.dp else 52.dp)
                .graphicsLayer(scaleX = scale, scaleY = scale)
                .clip(RoundedCornerShape(12.dp))
                .background(background)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        if (listener?.onCopyAddress() == true) showCopied()
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = if (copied) R.drawable.msr_check else R.drawable.msr_content_copy),
                contentDescription = stringResource(id = R.string.push_copy_url),
                modifier = Modifier.size(if (compact) 20.dp else 22.dp),
                tint = if (copied) colorScheme.tertiary else colorScheme.onSurfaceVariant
            )
        }
    }

    @Composable
    private fun PushClipboardButton(focusRequester: FocusRequester? = null, compact: Boolean = false) {
        val colorScheme = MaterialTheme.colorScheme
        var pushed by remember { mutableStateOf(false) }
        val interactionSource = remember { MutableInteractionSource() }
        val focused by interactionSource.collectIsFocusedAsState()
        val scale by animateFloatAsState(
            targetValue = when {
                pushed -> 0.95f
                focused -> 1.02f
                else -> 1f
            },
            animationSpec = JetStreamAnimations.ScaleSpring,
            label = "pushClipboardScale"
        )
        val background by animateColorAsState(
            targetValue = when {
                pushed -> colorScheme.tertiaryContainer
                focused -> colorScheme.primary
                else -> colorScheme.primaryContainer
            },
            animationSpec = JetStreamAnimations.ColorTween,
            label = "pushClipboardBackground"
        )
        val contentColor by animateColorAsState(
            targetValue = when {
                pushed -> colorScheme.onTertiaryContainer
                focused -> colorScheme.onPrimary
                else -> colorScheme.onPrimaryContainer
            },
            animationSpec = JetStreamAnimations.ColorTween,
            label = "pushClipboardContent"
        )

        LaunchedEffect(pushed) {
            if (pushed) {
                delay(2000L)
                pushed = false
            }
        }

        Row(
            modifier = Modifier
                .height(if (compact) 52.dp else 56.dp)
                .graphicsLayer(scaleX = scale, scaleY = scale)
                .clip(CircleShape)
                .background(background)
                .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        if (listener?.onPushClipboard() == true) pushed = true
                    }
                )
                .padding(horizontal = if (compact) 24.dp else 32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(id = if (pushed) R.drawable.msr_check else R.drawable.msr_content_paste),
                contentDescription = null,
                modifier = Modifier.size(if (compact) 23.dp else 25.dp),
                tint = contentColor
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(id = if (pushed) R.string.push_clip_sent else R.string.push_clip),
                color = contentColor,
                fontSize = if (compact) 17.sp else 18.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    @Composable
    private fun HintRow(compact: Boolean = false) {
        val colorScheme = MaterialTheme.colorScheme
        val supportingColor = colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 0.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 16.dp else 32.dp)
        ) {
            Box(
                modifier = Modifier
                    .height(1.dp)
                    .fillMaxWidth()
                    .background(colorScheme.outlineVariant.copy(alpha = 0.42f))
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_push_info),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = supportingColor
                )
                Text(
                    text = stringResource(id = R.string.push_wifi_hint),
                    color = supportingColor,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        }
    }

    @Composable
    private fun QrPanel(modifier: Modifier, compact: Boolean = false) {
        val colorScheme = MaterialTheme.colorScheme
        val panelShape = RoundedCornerShape(if (compact) 32.dp else 40.dp)
        Column(
            modifier = modifier
                .shadow(22.dp, panelShape, clip = false)
                .clip(panelShape)
                .background(colorScheme.surface)

                .padding(
                    horizontal = if (compact) 32.dp else 56.dp,
                    vertical = if (compact) 28.dp else 56.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            QrImageCard(compact)
            Spacer(Modifier.height(if (compact) 20.dp else 32.dp))
            Row(
                modifier = Modifier.heightIn(min = if (compact) 32.dp else 36.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.msr_smartphone),
                    contentDescription = null,
                    modifier = Modifier.size(if (compact) 22.dp else 26.dp),
                    tint = colorScheme.primary
                )
                Text(
                    text = stringResource(id = R.string.push_scan_title),
                    color = colorScheme.onSurface,
                    fontSize = if (compact) 20.sp else 24.sp,
                    lineHeight = if (compact) 28.sp else 32.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(if (compact) 6.dp else 8.dp))
            Text(
                text = stringResource(id = R.string.push_scan_desc),
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                fontSize = if (compact) 13.sp else 14.sp,
                lineHeight = if (compact) 18.sp else 20.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    @Composable
    private fun QrImageCard(compact: Boolean = false) {
        val colorScheme = MaterialTheme.colorScheme
        val interactionSource = remember { MutableInteractionSource() }
        val focused by interactionSource.collectIsFocusedAsState()
        val shape = RoundedCornerShape(if (compact) 22.dp else 28.dp)
        val scale by animateFloatAsState(
            targetValue = if (focused) 1.05f else 1f,
            animationSpec = JetStreamAnimations.ScaleSpring,
            label = "pushQrScale"
        )
        val borderColor by animateColorAsState(
            targetValue = if (focused) colorScheme.primary else Color.Transparent,
            animationSpec = JetStreamAnimations.ColorTween,
            label = "pushQrBorder"
        )
        Box(
            modifier = Modifier
                .size(if (compact) 220.dp else 288.dp)
                .graphicsLayer(scaleX = scale, scaleY = scale)
                .shadow(14.dp, shape, clip = false)
                .clip(shape)
                .background(Color.White)
                .border(3.dp, borderColor, shape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = { listener?.onOpenAddress() }
                )
                .padding(if (compact) 16.dp else 20.dp),
            contentAlignment = Alignment.Center
        ) {
            val bitmap = qrImage
            if (bitmap == null) {
                Text(
                    text = "QR",
                    color = Color.Black,
                    fontSize = 22.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }

    @Composable
    private fun CopyToast(visible: Boolean, modifier: Modifier) {
        val colorScheme = MaterialTheme.colorScheme
        AnimatedVisibility(
            visible = visible,
            modifier = modifier,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it })
        ) {
            Row(
                modifier = Modifier
                    .shadow(12.dp, CircleShape, clip = false)
                    .clip(CircleShape)
                    .background(colorScheme.surface)
                    .padding(horizontal = 22.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.msr_check),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = colorScheme.tertiary
                )
                Text(
                    text = stringResource(id = R.string.push_copied_url),
                    color = colorScheme.onSurface,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
