package com.qiekj.hotwater.ui.orders

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qiekj.hotwater.domain.MonthOrderGroup
import com.qiekj.hotwater.ui.theme.HotWaterColors
import com.qiekj.hotwater.ui.theme.HotWaterMotion

@Composable
internal fun MonthAccordionSection(
    group: MonthOrderGroup,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val arrowRot by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = HotWaterMotion.bouncySpring(),
        label = "monthArrow_${group.month}"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clickable(onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${group.month} 月",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = HotWaterColors.Primary
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${group.days.size} 天 · ${group.totalCount} 单",
                fontSize = 11.sp,
                color = Color(0xFFB49A93)
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "¥${"%.2f".format(group.totalAmount)}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = HotWaterColors.Primary
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = Color(0xFFC4AEA8),
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer { rotationZ = arrowRot }
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(340, easing = HotWaterMotion.SpringEasing)) + fadeIn(tween(220)),
            exit = shrinkVertically(tween(240)) + fadeOut(tween(140))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val x = 12.dp.toPx()
                    drawLine(
                        color = Color(0xFFE8D5D0),
                        start = Offset(x, 12.dp.toPx()),
                        end = Offset(x, size.height - 8.dp.toPx()),
                        strokeWidth = 1.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    group.days.forEach { day ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp, bottom = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.width(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(HotWaterColors.Primary)
                                )
                            }
                            Text(
                                text = day.dateLabel,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = HotWaterColors.Primary
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = "¥${"%.2f".format(day.totalAmount)}",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFB49A93)
                            )
                        }

                        day.items.forEachIndexed { idx, item ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 26.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item.machineName,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = HotWaterColors.OnSurface
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = item.timeLabel,
                                        fontSize = 11.sp,
                                        color = HotWaterColors.Muted
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        text = "¥${"%.2f".format(item.payPrice)}",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = HotWaterColors.Primary
                                    )
                                }
                                if (idx < day.items.lastIndex) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(HotWaterColors.DividerSoft)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(HotWaterColors.OutlineVar)
        )
    }
}
