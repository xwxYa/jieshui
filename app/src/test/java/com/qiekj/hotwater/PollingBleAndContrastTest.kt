package com.qiekj.hotwater

import com.qiekj.hotwater.data.model.Device
import com.qiekj.hotwater.data.model.WaterSyncState
import com.qiekj.hotwater.hardware.ble.BleFrameAssembler
import com.qiekj.hotwater.hardware.ble.BleInboundFrame
import com.qiekj.hotwater.hardware.ble.BleProtocol
import com.qiekj.hotwater.hardware.scanner.ContrastEnhancer
import com.qiekj.hotwater.ui.PollingAction
import com.qiekj.hotwater.ui.WaterPhase
import com.qiekj.hotwater.ui.WaterPollingReducer
import com.qiekj.hotwater.ui.WaterSessionUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PollingBleAndContrastTest {

    @Test
    fun `water polling reducer transitions waiting to dispensing to finished`() {
        val w1 = WaterPollingReducer.evaluate(WaterSyncState(workStatus = 1, status = 6, amount = 0.0, identify = ""), false)
        assertEquals(PollingAction.KEEP_WAITING, w1.action)
        assertEquals(false, w1.nextSawActive)

        val w2 = WaterPollingReducer.evaluate(WaterSyncState(workStatus = 2, status = 1, amount = 0.15, identify = "ORD1"), false)
        assertEquals(PollingAction.DISPENSING, w2.action)
        assertEquals(true, w2.nextSawActive)

        val w3 = WaterPollingReducer.evaluate(WaterSyncState(workStatus = 1, status = 5, amount = 0.25, identify = "ORD1"), true)
        assertEquals(PollingAction.FINISHED, w3.action)

        val w4 = WaterPollingReducer.evaluate(WaterSyncState(workStatus = 1, status = 0, amount = 0.0, identify = ""), true)
        assertEquals(PollingAction.FINISHED, w4.action)
    }

    @Test
    fun `ble protocol parses dedup 00, report 03 or 07, and chunks payload`() {
        assertEquals(BleInboundFrame.CloudDeduplicated, BleProtocol.parseCmdBack("aa0011223344"))

        val f07 = BleProtocol.parseCmdBack("aa0711223344556677deadbeef")
        assertTrue(f07 is BleInboundFrame.EncryptedReport)
        assertEquals("07", (f07 as BleInboundFrame.EncryptedReport).cmdType)
        assertEquals("deadbeef", f07.payloadHex)

        val f03 = BleProtocol.parseCmdBack("aa0300000000000000cafe0102")
        assertTrue(f03 is BleInboundFrame.EncryptedReport)
        assertEquals("cafe0102", (f03 as BleInboundFrame.EncryptedReport).payloadHex)

        val bytes = ByteArray(45) { it.toByte() }
        val chunks = BleProtocol.chunkPayload(bytes, 20)
        assertEquals(3, chunks.size)
        assertEquals(20, chunks[0].size)
        assertEquals(20, chunks[1].size)
        assertEquals(5, chunks[2].size)
    }

    @Test
    fun `ble frame assembler stitches multi-packet notification chunks`() {
        val assembler = BleFrameAssembler()
        // 第一包仅含前 8 字节 (16 hex chars)，未达到 07 帧完整长度 (>18)
        val part1 = BleProtocol.hexToBytes("aa07112233445566")
        val part2 = BleProtocol.hexToBytes("77deadbeef")
        assertNull(assembler.append(part1))
        val frame = assembler.append(part2)
        assertTrue(frame is BleInboundFrame.EncryptedReport)
        assertEquals("07", (frame as BleInboundFrame.EncryptedReport).cmdType)
        assertEquals("deadbeef", frame.payloadHex)
    }

    @Test
    fun `contrast enhancer expands low-contrast blue backlight luma range and computes centered roi`() {
        val lowContrast = ByteArray(100) { i -> (90 + (i % 40)).toByte() }
        val stretched = ContrastEnhancer.stretchLuma(lowContrast)
        val minVal = stretched.minOf { it.toInt() and 0xFF }
        val maxVal = stretched.maxOf { it.toInt() and 0xFF }
        assertTrue("Expected min <= 10, got $minVal", minVal <= 10)
        assertTrue("Expected max >= 240, got $maxVal", maxVal >= 240)

        val roi = ContrastEnhancer.centerSquareRoi(1280, 720, ratio = 0.68f)
        assertEquals(489, roi[2])
        assertEquals(489, roi[3])
        assertEquals((1280 - 489) / 2, roi[0])
        assertEquals((720 - 489) / 2, roi[1])
    }

    @Test
    fun `reopening unlocked or in-use device restores active session so end button stays enabled`() {
        val idleDev = Device(goodsId = "G1", skuId = "S1", name = "三楼开水机", online = true, workStatus = 1)
        val cachedUnlocked = WaterSessionUiState(
            device = idleDev,
            phase = WaterPhase.UNLOCKED,
            orderNo = "ORD123",
            syncStatusText = "现在到机器上按接水按钮"
        )
        // 1. 解锁后返回列表再重进，命中本地活跃缓存，直接恢复 UNLOCKED
        val restored = WaterPollingReducer.resolveInitialSession(idleDev, cachedUnlocked)
        assertEquals(WaterPhase.UNLOCKED, restored.phase)
        assertEquals("ORD123", restored.orderNo)

        // 2. 无本地缓存但服务端/列表已上报 workStatus == 2（我自己的订单正在使用中），直接进入 DISPENSING
        val runningDev = idleDev.copy(workStatus = 2)
        val resumedFromServer = WaterPollingReducer.resolveInitialSession(runningDev, cachedActive = null)
        assertEquals(WaterPhase.DISPENSING, resumedFromServer.phase)

        // 3. 普通空闲设备无缓存，保持 LOCKED
        val freshLocked = WaterPollingReducer.resolveInitialSession(idleDev, cachedActive = null)
        assertEquals(WaterPhase.LOCKED, freshLocked.phase)
    }
}
