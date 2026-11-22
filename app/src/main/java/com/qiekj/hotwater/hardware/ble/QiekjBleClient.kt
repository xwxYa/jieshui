package com.qiekj.hotwater.hardware.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

sealed class BleExecutionResult {
    data class Reported(val frame: BleInboundFrame) : BleExecutionResult()
    data object WrittenNoReply : BleExecutionResult()
    data class Failed(val reason: String) : BleExecutionResult()
}

/**
 * 自适应低功耗蓝牙近场客户端（严格串行 GATT 状态机）：
 * 扫描匹配 -> 连接 -> requestMtu(等待回调/超时兜底) -> discoverServices ->
 * 开启 CCCD 通知(等待 onDescriptorWrite) -> 按真实 MTU 分包写入(等待 onCharacteristicWrite) ->
 * 多包拼装监听回包 -> 必释放 GATT 资源。
 */
class QiekjBleClient(context: Context) {

    companion object {
        private const val TAG = "HotWaterBLE"
        private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private val PREFERRED_SERVICE_PREFIXES = listOf(
            "6e400001", // Nordic UART Service
            "0000ffe0",
            "0000fff0",
            "0000fee7",
            "0000ff00"
        )
    }

    private val appContext = context.applicationContext
    private val bluetoothManager: BluetoothManager? =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    suspend fun sendCommand(
        imeiOrSn: String,
        bleCmdHex: String,
        scanTimeoutMs: Long = 9_000L,
        replyTimeoutMs: Long = 6_000L
    ): BleExecutionResult {
        val btAdapter = adapter ?: return BleExecutionResult.Failed("设备不支持蓝牙")
        if (!btAdapter.isEnabled) return BleExecutionResult.Failed("请先打开手机蓝牙")
        val payload = BleProtocol.hexToBytes(bleCmdHex)
        if (payload.isEmpty()) return BleExecutionResult.Failed("蓝牙指令为空")

        val device = try {
            withTimeout(scanTimeoutMs) { scanForMachine(btAdapter, imeiOrSn) }
        } catch (_: TimeoutCancellationException) {
            return BleExecutionResult.Failed("附近没搜到这台饮水机，请靠近一点再试")
        } catch (e: Exception) {
            return BleExecutionResult.Failed("蓝牙扫描失败：${e.message ?: "权限不足"}")
        }

        return connectWriteAndAwait(device, payload, replyTimeoutMs)
    }

    @SuppressLint("MissingPermission")
    private suspend fun scanForMachine(
        btAdapter: BluetoothAdapter,
        targetId: String
    ): BluetoothDevice = suspendCancellableCoroutine { cont ->
        val scanner = btAdapter.bluetoothLeScanner
        if (scanner == null) {
            cont.cancel(IllegalStateException("BLE Scanner 不可用"))
            return@suspendCancellableCoroutine
        }
        val clean = targetId.trim().uppercase()
        val tail8 = if (clean.length > 8) clean.takeLast(8) else clean
        val tail6 = if (clean.length > 6) clean.takeLast(6) else clean

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (!cont.isActive) return
                val dev = result.device ?: return
                val name = (result.scanRecord?.deviceName ?: dev.name).orEmpty().uppercase()
                val rawBytesHex = result.scanRecord?.bytes?.let(BleProtocol::bytesToHex).orEmpty().uppercase()
                val matched = clean.isNotBlank() && (
                    name.contains(clean) ||
                        name.contains(tail8) ||
                        name.contains(tail6) ||
                        rawBytesHex.contains(tail8)
                    )
                if (matched) {
                    Log.i(TAG, "Matched BLE machine name=$name addr=${dev.address} rssi=${result.rssi}")
                    runCatching { scanner.stopScan(this) }
                    cont.resume(dev)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                if (cont.isActive) {
                    cont.cancel(IllegalStateException("BLE 扫描错误码 $errorCode"))
                }
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner.startScan(null, settings, callback)
        cont.invokeOnCancellation {
            runCatching { scanner.stopScan(callback) }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun connectWriteAndAwait(
        device: BluetoothDevice,
        payload: ByteArray,
        replyTimeoutMs: Long
    ): BleExecutionResult {
        val connectedReady = CompletableDeferred<BluetoothGatt>()
        val mtuReady = CompletableDeferred<Int>()
        val servicesReady = CompletableDeferred<BluetoothGatt>()
        val descriptorAck = AtomicReference<CompletableDeferred<Boolean>?>(null)
        val writeAck = AtomicReference<CompletableDeferred<Boolean>?>(null)
        val inboundReply = CompletableDeferred<BleInboundFrame>()
        val assembler = BleFrameAssembler()
        var gattRef: BluetoothGatt? = null

        val gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    val err = IllegalStateException("GATT 连接错误 ($status)")
                    connectedReady.completeExceptionally(err)
                    servicesReady.completeExceptionally(err)
                    return
                }
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.i(TAG, "GATT connected")
                    connectedReady.complete(gatt)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    val err = IllegalStateException("蓝牙已断开")
                    connectedReady.completeExceptionally(err)
                    servicesReady.completeExceptionally(err)
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                val effective = if (status == BluetoothGatt.GATT_SUCCESS && mtu >= 23) mtu else 23
                Log.i(TAG, "MTU negotiated=$effective (status=$status)")
                mtuReady.complete(effective)
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    logDiscoveredServices(gatt)
                    servicesReady.complete(gatt)
                } else {
                    servicesReady.completeExceptionally(IllegalStateException("发现蓝牙服务失败 ($status)"))
                }
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int
            ) {
                descriptorAck.getAndSet(null)?.complete(status == BluetoothGatt.GATT_SUCCESS)
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                writeAck.getAndSet(null)?.complete(status == BluetoothGatt.GATT_SUCCESS)
            }

            @Deprecated("Deprecated in API 33")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                @Suppress("DEPRECATION")
                val value = characteristic.value ?: return
                handleInbound(value, assembler, inboundReply)
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                handleInbound(value, assembler, inboundReply)
            }
        }

        return try {
            gattRef = device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            val gatt = withTimeout(7_000L) { connectedReady.await() }

            // 1. 严格串行：先协商 MTU，等回调（最多 700ms）后再发起 discoverServices
            val mtuRequested = runCatching { gatt.requestMtu(185) }.getOrDefault(false)
            val negotiatedMtu = if (mtuRequested) {
                withTimeoutOrNull(700L) { mtuReady.await() } ?: 23
            } else {
                23
            }
            val chunkSize = (negotiatedMtu - 3).coerceIn(20, 180)

            // 2. 再发现服务
            if (!gatt.discoverServices()) {
                return BleExecutionResult.Failed("启动 GATT 服务发现失败")
            }
            withTimeout(7_000L) { servicesReady.await() }

            val (writeChar, notifyChar) = pickCharacteristics(gatt)
                ?: return BleExecutionResult.Failed("未找到可写入的蓝牙透传特征（已打印 GATT 日志）")

            // 3. 开启通知并等待 CCCD 写回调
            if (notifyChar != null) {
                val ack = CompletableDeferred<Boolean>()
                descriptorAck.set(ack)
                val started = enableNotification(gatt, notifyChar)
                if (started) {
                    withTimeoutOrNull(1_500L) { ack.await() }
                } else {
                    descriptorAck.set(null)
                }
            }

            // 4. 按协商后的 MTU 逐包串行写入，每包等待确认
            val chunks = BleProtocol.chunkPayload(payload, chunkSize)
            val useNoResponse =
                (writeChar.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) == 0 &&
                    (writeChar.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0

            for (chunk in chunks) {
                val ack = CompletableDeferred<Boolean>()
                writeAck.set(ack)
                val dispatched = writeChunk(gatt, writeChar, chunk, useNoResponse)
                if (!dispatched) {
                    writeAck.set(null)
                    return BleExecutionResult.Failed("蓝牙指令写入被系统拒绝")
                }
                if (useNoResponse) {
                    withTimeoutOrNull(120L) { ack.await() }
                    delay(25)
                } else {
                    val ok = withTimeoutOrNull(2_000L) { ack.await() } ?: false
                    if (!ok) return BleExecutionResult.Failed("蓝牙指令写入超时或失败")
                }
            }

            // 5. 等待机器拼装完整回包
            val frame = withTimeoutOrNull(replyTimeoutMs) { inboundReply.await() }
            if (frame != null) BleExecutionResult.Reported(frame)
            else BleExecutionResult.WrittenNoReply
        } catch (e: Exception) {
            BleExecutionResult.Failed(e.message ?: "蓝牙近场解锁失败")
        } finally {
            runCatching {
                gattRef?.disconnect()
                gattRef?.close()
            }
        }
    }

    private fun handleInbound(
        bytes: ByteArray,
        assembler: BleFrameAssembler,
        deferred: CompletableDeferred<BleInboundFrame>
    ) {
        val hex = BleProtocol.bytesToHex(bytes)
        Log.i(TAG, "BLE inbound chunk hex=$hex")
        val frame = assembler.append(bytes)
        if (frame != null && !deferred.isCompleted) {
            deferred.complete(frame)
        }
    }

    private fun logDiscoveredServices(gatt: BluetoothGatt) {
        gatt.services.forEach { svc ->
            val chars = svc.characteristics.joinToString { c ->
                "${c.uuid}(props=0x${c.properties.toString(16)})"
            }
            Log.i(TAG, "GATT Service ${svc.uuid}: $chars")
        }
    }

    private fun pickCharacteristics(
        gatt: BluetoothGatt
    ): Pair<BluetoothGattCharacteristic, BluetoothGattCharacteristic?>? {
        val candidates = gatt.services.sortedBy { svc ->
            val u = svc.uuid.toString().lowercase()
            val idx = PREFERRED_SERVICE_PREFIXES.indexOfFirst { u.startsWith(it) }
            if (idx >= 0) idx else 100
        }
        for (svc in candidates) {
            val u = svc.uuid.toString().lowercase()
            if (u.startsWith("00001800") || u.startsWith("00001801") || u.startsWith("0000180a")) continue
            val writeChar = svc.characteristics.firstOrNull {
                (it.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 ||
                    (it.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
            }
            if (writeChar != null) {
                val notifyChar = svc.characteristics.firstOrNull {
                    (it.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 ||
                        (it.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
                }
                return writeChar to notifyChar
            }
        }
        return null
    }

    @SuppressLint("MissingPermission")
    private fun enableNotification(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ): Boolean {
        gatt.setCharacteristicNotification(characteristic, true)
        val desc = characteristic.getDescriptor(CCCD_UUID) ?: return false
        val value = if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        } else {
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(desc, value) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            desc.value = value
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(desc)
        }
    }

    @SuppressLint("MissingPermission")
    private fun writeChunk(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        chunk: ByteArray,
        useNoResponse: Boolean
    ): Boolean {
        val writeType = if (useNoResponse) {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(characteristic, chunk, writeType) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.writeType = writeType
            @Suppress("DEPRECATION")
            characteristic.value = chunk
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(characteristic)
        }
    }
}
