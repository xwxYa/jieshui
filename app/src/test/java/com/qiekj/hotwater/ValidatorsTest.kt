package com.qiekj.hotwater

import com.qiekj.hotwater.domain.PhoneValidator
import com.qiekj.hotwater.domain.QrParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidatorsTest {

    @Test
    fun `phone validator blocks fake prefixes like 123 or 144`() {
        assertTrue(PhoneValidator.isValidMainlandPhone("13800138000"))
        assertTrue(PhoneValidator.isValidMainlandPhone("19912345678"))
        assertFalse(PhoneValidator.isValidMainlandPhone("12345678910"))
        assertFalse(PhoneValidator.isValidMainlandPhone("14412345678"))
        assertFalse(PhoneValidator.isValidMainlandPhone("1380013800"))
    }

    @Test
    fun `qr parser extracts url params, raw sn, and long digit ids in priority order`() {
        val fromUrl = QrParser.extractCandidates("https://h5.qiekj.com/scan?imei=860000000000001&foo=bar")
        assertEquals(listOf("860000000000001"), fromUrl)

        val multi = QrParser.extractCandidates("https://h5.qiekj.com/?sn=SN123456&deviceId=860000000000009")
        assertEquals(listOf("SN123456", "860000000000009"), multi)

        val raw = QrParser.extractCandidates("  861234567890123  ")
        assertEquals(listOf("861234567890123"), raw)

        val junk = QrParser.extractCandidates("hello!")
        assertTrue(junk.isEmpty())
    }
}
