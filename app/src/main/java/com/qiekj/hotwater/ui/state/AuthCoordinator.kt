package com.qiekj.hotwater.ui.state

import com.qiekj.hotwater.data.local.LocalStore
import com.qiekj.hotwater.data.model.ApiResult
import com.qiekj.hotwater.data.model.LoginOutcome
import com.qiekj.hotwater.data.model.TokenCheckResult
import com.qiekj.hotwater.data.remote.QiekjApi
import com.qiekj.hotwater.domain.PhoneValidator
import com.qiekj.hotwater.hardware.Haptics
import com.qiekj.hotwater.ui.AuthUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthCoordinator(
    private val scope: CoroutineScope,
    private val localStore: LocalStore,
    private val api: QiekjApi,
    private val haptics: Haptics,
    private val isDemoMode: () -> Boolean,
    private val onAuthenticated: (phone: String) -> Unit,
    private val onTokenExpired: () -> Unit
) {
    private val _state = MutableStateFlow(
        AuthUiState(
            phoneInput = localStore.phone,
            loggedInPhone = localStore.phone
        )
    )
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    private var countdownJob: Job? = null
    private var loginJob: Job? = null

    fun warmupDns() {
        scope.launch { api.warmup() }
    }

    fun verifyTokenInBackground() {
        scope.launch {
            val token = localStore.token
            if (api.checkToken(token) == TokenCheckResult.Expired && !isDemoMode()) {
                resetForLogin()
                emitBubble("登录已过期，请重新登录", isError = true)
                onTokenExpired()
            }
        }
    }

    fun onPhoneChange(v: String) {
        _state.update { it.copy(phoneInput = v.filter(Char::isDigit).take(11)) }
    }

    fun onCodeChange(v: String) {
        _state.update { it.copy(codeInput = v.filter(Char::isDigit).take(6)) }
    }

    fun sendSmsCode() {
        val st = _state.value
        if (st.sendingCode || st.sendCodeCountdown > 0) return
        val phone = st.phoneInput.trim()
        if (!PhoneValidator.isValidMainlandPhone(phone)) {
            _state.update { it.copy(phoneShakeTick = it.phoneShakeTick + 1) }
            emitBubble("这不像个手机号", isError = true)
            return
        }
        _state.update { it.copy(sendingCode = true) }
        scope.launch {
            when (val r = api.sendCode(phone)) {
                is ApiResult.Success -> {
                    _state.update {
                        it.copy(
                            sendingCode = false,
                            sendCodeSuccessTick = it.sendCodeSuccessTick + 1
                        )
                    }
                    emitBubble("验证码已发到 $phone", isError = false)
                    startCountdown()
                }
                is ApiResult.ServerError -> {
                    _state.update { it.copy(sendingCode = false) }
                    emitBubble(r.message, isError = true)
                }
                is ApiResult.NetworkError -> {
                    _state.update { it.copy(sendingCode = false) }
                    emitBubble(r.message, isError = true)
                }
                ApiResult.AuthExpired -> {
                    _state.update { it.copy(sendingCode = false) }
                    emitBubble("发不出去", isError = true)
                }
            }
        }
    }

    fun submitLogin() {
        val st = _state.value
        if (st.loggingIn) return
        val phone = st.phoneInput.trim()
        val code = st.codeInput.trim()
        if (!PhoneValidator.isValidMainlandPhone(phone)) {
            _state.update { it.copy(phoneShakeTick = it.phoneShakeTick + 1) }
            emitBubble("这不像个手机号", isError = true)
            return
        }
        if (code.length < 4) {
            _state.update { it.copy(codeShakeTick = it.codeShakeTick + 1) }
            emitBubble("验证码填完整", isError = true)
            return
        }
        _state.update { it.copy(loggingIn = true) }
        loginJob?.cancel()
        loginJob = scope.launch {
            when (val outcome = api.verifyLogin(phone, code)) {
                LoginOutcome.Success -> {
                    val who = localStore.phone.ifBlank { phone }
                    _state.update {
                        it.copy(
                            loggingIn = false,
                            loggedInPhone = who,
                            loginSuccessTick = it.loginSuccessTick + 1
                        )
                    }
                    emitBubble("已登录 · $who", isError = false)
                    delay(480)
                    if (!isDemoMode()) onAuthenticated(who)
                }
                LoginOutcome.BadPhone -> {
                    _state.update { it.copy(loggingIn = false, phoneShakeTick = it.phoneShakeTick + 1) }
                    emitBubble("这不像个手机号", isError = true)
                }
                LoginOutcome.NewAccountRejected -> {
                    _state.update { it.copy(loggingIn = false, phoneShakeTick = it.phoneShakeTick + 1) }
                    emitBubble("这个号没注册过 —— 登进去也是个空账号", isError = true)
                }
                is LoginOutcome.Failure -> {
                    _state.update { it.copy(loggingIn = false, codeShakeTick = it.codeShakeTick + 1) }
                    emitBubble(outcome.message, isError = true)
                }
            }
        }
    }

    fun setDemoIdentity() {
        cancel()
        _state.update { it.copy(loggingIn = false, loggedInPhone = "演示模式") }
    }

    fun resetForLogin() {
        cancel()
        _state.value = AuthUiState(
            phoneInput = localStore.phone,
            loggedInPhone = localStore.phone
        )
    }

    fun cancel() {
        loginJob?.cancel()
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = scope.launch {
            for (sec in 60 downTo 1) {
                _state.update { it.copy(sendCodeCountdown = sec) }
                delay(1000)
            }
            _state.update { it.copy(sendCodeCountdown = 0) }
        }
    }

    private fun emitBubble(msg: String, isError: Boolean) {
        if (isError) haptics.buzz(30)
        _state.update {
            it.copy(
                loginBubbleText = msg,
                loginBubbleError = isError,
                loginBubbleTick = it.loginBubbleTick + 1
            )
        }
    }
}
