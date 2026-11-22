package com.qiekj.hotwater.ui.login

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qiekj.hotwater.ui.AuthUiState
import com.qiekj.hotwater.ui.components.MorphButtonState
import com.qiekj.hotwater.ui.components.MorphingActionButton
import com.qiekj.hotwater.ui.components.ShakeableInputCard
import com.qiekj.hotwater.ui.theme.HotWaterColors
import com.qiekj.hotwater.ui.theme.HotWaterMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
fun LoginScreen(
    auth: AuthUiState,
    onPhoneChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onSendCode: () -> Unit,
    onSubmitLogin: () -> Unit,
    onEnterDemo: () -> Unit,
    onBuzz: (Int, Int) -> Unit
) {
    val scope = rememberCoroutineScope()
    val codeFocusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()

    var focusField by remember { mutableStateOf<String?>(null) }
    var pokes by remember { mutableIntStateOf(0) }
    var pokeTick by remember { mutableIntStateOf(0) }
    var eggSwelling by remember { mutableStateOf(false) }
    var moodOverride by remember { mutableStateOf<DropMood?>(null) }
    var activeBubble by remember { mutableStateOf<String?>(null) }

    val burstProgress = remember { Animatable(0f) }
    val flashAlpha = remember { Animatable(0f) }

    var coolJob by remember { mutableStateOf<Job?>(null) }
    var bubbleJob by remember { mutableStateOf<Job?>(null) }
    var moodJob by remember { mutableStateOf<Job?>(null) }

    fun showBubble(text: String, durationMs: Long) {
        bubbleJob?.cancel()
        activeBubble = text
        bubbleJob = scope.launch {
            delay(durationMs)
            activeBubble = null
        }
    }

    fun showTemporaryMood(mood: DropMood, durationMs: Long) {
        moodJob?.cancel()
        moodOverride = mood
        moodJob = scope.launch {
            delay(durationMs)
            moodOverride = null
        }
    }

    LaunchedEffect(auth.loginBubbleTick) {
        if (auth.loginBubbleTick > 0 && !auth.loginBubbleText.isNullOrBlank()) {
            showBubble(auth.loginBubbleText, 2200L)
            if (auth.loginBubbleError) {
                showTemporaryMood(DropMood.SAD, 1300L)
            }
        }
    }

    LaunchedEffect(auth.loginSuccessTick) {
        if (auth.loginSuccessTick > 0) {
            showTemporaryMood(DropMood.HAPPY, 1400L)
        }
    }

    LaunchedEffect(auth.sendCodeSuccessTick) {
        if (auth.sendCodeSuccessTick > 0) {
            runCatching { codeFocusRequester.requestFocus() }
        }
    }

    val heat = if (pokes < 12) 0f else ((pokes - 11) / 4f).coerceIn(0f, 1f)
    val effectiveMood = when {
        eggSwelling -> DropMood.HAPPY
        moodOverride != null -> moodOverride!!
        pokes >= 8 -> DropMood.SAD
        focusField == "phone" -> DropMood.LOOK_DOWN
        focusField == "code" -> DropMood.LOOK_UP
        else -> DropMood.IDLE
    }

    fun handleDropPoke() {
        if (eggSwelling || pokes >= 15) return
        pokes++
        pokeTick++

        coolJob?.cancel()
        if (pokes < 15) {
            coolJob = scope.launch {
                delay(3200)
                if (!eggSwelling) {
                    pokes = 0
                }
            }
        }

        when (pokes) {
            3 -> {
                showBubble("痒。", 1400L)
                onBuzz(16, 120)
            }
            8 -> {
                showBubble("手不想要了？", 1700L)
                onBuzz(30, 150)
            }
            12 -> {
                showBubble("……我记住你了", 1800L)
                onBuzz(42, 185)
            }
            15 -> {
                showBubble("好吧，你赢了", 1200L)
                scope.launch {
                    delay(560)
                    eggSwelling = true
                    onBuzz(60, 210)
                    delay(300)
                    flashAlpha.snapTo(1f)
                    launch {
                        flashAlpha.animateTo(0f, tween(520))
                    }
                    launch {
                        delay(110)
                        onBuzz(90, 240)
                    }
                    burstProgress.animateTo(1f, tween(640, easing = HotWaterMotion.OutFastEasing))
                    onEnterDemo()
                }
            }
            else -> {
                val ms = (10 + pokes * 1.6f).roundToInt()
                onBuzz(ms, (110 + pokes * 5).coerceAtMost(220))
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HotWaterColors.Bg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 30.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            WaterDropFace(
                mood = effectiveMood,
                heat = heat,
                pokeTick = pokeTick,
                madMode = pokes >= 8 && pokes < 15,
                swelling = eggSwelling,
                bubbleText = activeBubble,
                onPoke = ::handleDropPoke
            )

            Spacer(Modifier.height(14.dp))

            Text(
                text = "接水",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = HotWaterColors.OnSurface,
                letterSpacing = (-0.4).sp
            )

            Spacer(Modifier.height(24.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ShakeableInputCard(shakeTick = auth.phoneShakeTick) {
                    Text(
                        text = "手机号",
                        fontSize = 10.5.sp,
                        color = HotWaterColors.Hint
                    )
                    Spacer(Modifier.height(3.dp))
                    BasicTextField(
                        value = auth.phoneInput,
                        onValueChange = onPhoneChange,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { runCatching { codeFocusRequester.requestFocus() } }
                        ),
                        textStyle = TextStyle(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = HotWaterColors.OnSurface,
                            fontFeatureSettings = "tnum"
                        ),
                        cursorBrush = SolidColor(HotWaterColors.Primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focusField = if (it.isFocused) "phone" else if (focusField == "phone") null else focusField }
                    )
                }

                ShakeableInputCard(shakeTick = auth.codeShakeTick) {
                    Text(
                        text = "验证码",
                        fontSize = 10.5.sp,
                        color = HotWaterColors.Hint
                    )
                    Spacer(Modifier.height(3.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = auth.codeInput,
                            onValueChange = onCodeChange,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { onSubmitLogin() }
                            ),
                            textStyle = TextStyle(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = HotWaterColors.OnSurface,
                                fontFeatureSettings = "tnum"
                            ),
                            cursorBrush = SolidColor(HotWaterColors.Primary),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(codeFocusRequester)
                                .onFocusChanged { focusField = if (it.isFocused) "code" else if (focusField == "code") null else focusField }
                        )

                        val canSend = !auth.sendingCode && auth.sendCodeCountdown <= 0
                        val sendLabel = when {
                            auth.sendingCode -> "发送中…"
                            auth.sendCodeCountdown > 0 -> "${auth.sendCodeCountdown}s 后重发"
                            else -> "发送验证码"
                        }
                        Text(
                            text = sendLabel,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (canSend) HotWaterColors.Primary else Color(0xFFC4B4AE),
                            modifier = Modifier
                                .clickable(enabled = canSend, onClick = onSendCode)
                                .padding(start = 12.dp, top = 6.dp, bottom = 6.dp)
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                MorphingActionButton(
                    text = "登 录",
                    loadingText = "登录中…",
                    state = if (auth.loggingIn) MorphButtonState.LOADING else MorphButtonState.IDLE,
                    onClick = onSubmitLogin
                )
            }

            Spacer(Modifier.height(22.dp))
        }

        if (burstProgress.value > 0f || flashAlpha.value > 0f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width * 0.5f, size.height * 0.38f)
                val maxRadius = hypot(size.width, size.height)
                if (burstProgress.value > 0f) {
                    drawCircle(
                        color = HotWaterColors.EggRed,
                        radius = maxRadius * burstProgress.value,
                        center = center
                    )
                }
                if (flashAlpha.value > 0f) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = flashAlpha.value), Color.Transparent),
                            center = center,
                            radius = size.width * 0.85f
                        ),
                        radius = size.width * 0.85f,
                        center = center
                    )
                }
            }
        }
    }
}
