package com.waste.link.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SwipeToActionButton(
    text: String,
    onSwipeComplete: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    textColor: Color = Color.White,
    icon: ImageVector = Icons.Default.ArrowForward
) {
    val coroutineScope = rememberCoroutineScope()
    var isComplete by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(100.dp))
            .background(containerColor),
        contentAlignment = Alignment.CenterStart
    ) {
        val maxWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val thumbSizePx = with(LocalDensity.current) { 56.dp.toPx() }
        val maxDrag = maxWidthPx - thumbSizePx - with(LocalDensity.current) { 8.dp.toPx() }

        val offsetX = remember { Animatable(0f) }

        // Background Text
        Text(
            text = text,
            color = textColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center)
        )

        // Draggable Thumb
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .padding(4.dp)
                .size(56.dp)
                .clip(CircleShape)
                .background(if (isComplete) Color(0xFF25D366) else thumbColor)
                .draggable(
                    state = rememberDraggableState { delta ->
                        if (!isComplete) {
                            coroutineScope.launch {
                                val newOffset = (offsetX.value + delta).coerceIn(0f, maxDrag)
                                offsetX.snapTo(newOffset)
                            }
                        }
                    },
                    orientation = Orientation.Horizontal,
                    onDragStopped = {
                        if (!isComplete) {
                            if (offsetX.value > maxDrag * 0.8f) {
                                coroutineScope.launch {
                                    offsetX.animateTo(maxDrag)
                                    isComplete = true
                                    onSwipeComplete()
                                }
                            } else {
                                coroutineScope.launch {
                                    offsetX.animateTo(0f)
                                }
                            }
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isComplete) Icons.Default.Check else icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
