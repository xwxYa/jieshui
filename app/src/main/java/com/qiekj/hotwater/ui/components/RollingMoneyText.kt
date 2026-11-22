package com.qiekj.hotwater.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qiekj.hotwater.ui.theme.HotWaterMotion
import kotlinx.coroutines.delay

@Composable
fun RollingMoneyText(
    formatted: String,
    fontSize: TextUnit,
    color: Color,
    fontWeight: FontWeight = FontWeight.Bold,
    prefix: String = "",
    prefixFontSize: TextUnit = fontSize,
    prefixColor: Color = color,
    playKey: Int = 0,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom
    ) {
        if (prefix.isNotEmpty()) {
            Text(
                text = prefix,
                fontSize = prefixFontSize,
                fontWeight = FontWeight.SemiBold,
                color = prefixColor,
                modifier = Modifier.padding(end = 3.dp, bottom = 3.dp)
            )
        }
        formatted.forEachIndexed { idx, ch ->
            val rightSlot = formatted.length - 1 - idx
            val dropOffset = remember(playKey, rightSlot) {
                Animatable(if (playKey > 0) -1.05f else 0f)
            }
            LaunchedEffect(playKey) {
                if (playKey > 0) {
                    dropOffset.snapTo(-1.05f)
                    delay(idx * 45L)
                    dropOffset.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(480, easing = HotWaterMotion.SpringEasing)
                    )
                } else {
                    dropOffset.snapTo(0f)
                }
            }

            key(rightSlot) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(2.dp))
                        .graphicsLayer {
                            translationY = dropOffset.value * fontSize.toPx()
                        }
                ) {
                    AnimatedContent(
                        targetState = ch,
                        transitionSpec = {
                            (slideInVertically(tween(340, easing = HotWaterMotion.SpringEasing)) { -it } + fadeIn(tween(180))) togetherWith
                                (slideOutVertically(tween(220)) { it } + fadeOut(tween(140)))
                        },
                        label = "digitRoll_$rightSlot"
                    ) { digit ->
                        Text(
                            text = digit.toString(),
                            style = TextStyle(
                                fontSize = fontSize,
                                fontWeight = fontWeight,
                                color = color,
                                fontFeatureSettings = "tnum",
                                letterSpacing = (-0.5).sp
                            )
                        )
                    }
                }
            }
        }
    }
}
