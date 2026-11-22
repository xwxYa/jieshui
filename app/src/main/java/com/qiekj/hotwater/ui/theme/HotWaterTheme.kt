package com.qiekj.hotwater.ui.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

object HotWaterColors {
    val Primary = Color(0xFF8F4C38)
    val PrimaryDim = Color(0xFFB47054)
    val PrimaryLite = Color(0xFFC98D72)
    val Bg = Color(0xFFFFF8F6)
    val Surface = Color(0xFFFFFFFF)
    val OnSurface = Color(0xFF1A110F)
    val OnSurfaceVar = Color(0xFF5C4A45)
    val Muted = Color(0xFFA08E88)
    val Hint = Color(0xFFB3A39D)
    val OutlineVar = Color(0xFFE6D6D1)
    val DividerSoft = Color(0xFFF3E7E3)
    val WaterBlue = Color(0xFF5FB6E0)
    val Dark = Color(0xFF2B1C18)
    val DisabledBg = Color(0xFFEDE2DF)
    val DisabledFg = Color(0xFFB2A29C)
    val OnlineGreen = Color(0xFF4CC07A)
    val OfflineGray = Color(0xFFC0B4B0)
    val Danger = Color(0xFFE24B3F)
    val EggRed = Color(0xFFF0342F)
    val PillTint = Color(0x178F4C38)
    val MoreChipBg = Color(0xFFF6EBE6)
    val LockIdle = Color(0xFFC3AFA9)
    val DotsIdle = Color(0xFFD6C0BA)
}

object HotWaterMotion {
    val SpringEasing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1.0f)
    val SwipeEasing = CubicBezierEasing(0.87f, 0.05f, 0.02f, 0.97f)
    val MorphEasing = CubicBezierEasing(0.45f, 0f, 0.25f, 1.15f)
    val OutFastEasing = CubicBezierEasing(0.16f, 0.7f, 0.3f, 1f)

    fun <T> bouncySpring() = spring<T>(
        dampingRatio = 0.66f,
        stiffness = Spring.StiffnessMediumLow
    )

    fun <T> crispSpring() = spring<T>(
        dampingRatio = 0.78f,
        stiffness = Spring.StiffnessMedium
    )
}

private val HotWaterScheme = lightColorScheme(
    primary = HotWaterColors.Primary,
    onPrimary = Color.White,
    background = HotWaterColors.Bg,
    onBackground = HotWaterColors.OnSurface,
    surface = HotWaterColors.Surface,
    onSurface = HotWaterColors.OnSurface,
    surfaceVariant = HotWaterColors.MoreChipBg,
    onSurfaceVariant = HotWaterColors.OnSurfaceVar,
    outlineVariant = HotWaterColors.OutlineVar,
    error = HotWaterColors.Danger
)

@Composable
fun HotWaterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HotWaterScheme,
        content = content
    )
}

@Composable
fun HotWaterToastOverlay(
    message: String?,
    toastId: Long?,
    onDismiss: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(toastId) {
        if (toastId != null) {
            delay(2300)
            onDismiss(toastId)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = 148.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = message != null,
            enter = fadeIn() + scaleIn(initialScale = 0.75f, animationSpec = HotWaterMotion.bouncySpring()) +
                slideInVertically { 24 },
            exit = fadeOut() + scaleOut(targetScale = 0.92f) + slideOutVertically { 12 }
        ) {
            Box(
                modifier = Modifier
                    .shadow(10.dp, RoundedCornerShape(999.dp), ambientColor = HotWaterColors.Dark)
                    .clip(RoundedCornerShape(999.dp))
                    .background(HotWaterColors.Dark)
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = message.orEmpty(),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun HotWaterModalOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x6B1A110F))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
                .padding(horizontal = 26.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(18.dp, RoundedCornerShape(24.dp), ambientColor = HotWaterColors.Primary)
                    .clip(RoundedCornerShape(24.dp))
                    .background(HotWaterColors.Bg)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .padding(horizontal = 22.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content
            )
        }
    }
}
