package com.qiekj.hotwater

import com.qiekj.hotwater.data.model.ApiResult
import com.qiekj.hotwater.data.model.LoginOutcome
import com.qiekj.hotwater.data.remote.QiekjApi
import com.qiekj.hotwater.data.remote.QiekjHttpClient
import com.qiekj.hotwater.data.remote.QiekjSigner
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class QiekjSigningAndApiTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `signer matches official HeadInterceptor sha256 payload format`() {
        val payload = QiekjSigner.buildSignPayload(
            path = "/user/info",
            timestampMs = "1700000000000",
            token = "tok123",
            version = "1.96.1"
        )
        assertEquals(
            "appSecret=${QiekjSigner.APP_SECRET}&channel=android_app&timestamp=1700000000000&token=tok123&version=1.96.1&/user/info",
            payload
        )
        val sig = QiekjSigner.sign("/user/info", "1700000000000", "tok123", "1.96.1")
        assertEquals(64, sig.length)
        assertEquals(QiekjSigner.sha256(payload), sig)
    }

    @Test
    fun `http client sends required headers and detects code=2 auth expired`() = runTest {
        server.enqueue(MockResponse().setBody("""{"code":2,"msg":"未登录"}"""))

        val client = QiekjHttpClient(
            baseUrl = server.url("/").toString(),
            tokenProvider = { "abc_tok" },
            versionProvider = { "1.96.1" },
            phoneBrandProvider = { "Xiaomi" },
            timeProvider = { 1700000000000L }
        )
        val res = client.post("/user/info")
        assertTrue(res is ApiResult.AuthExpired)

        val req = server.takeRequest()
        assertEquals("abc_tok", req.getHeader("Authorization"))
        assertEquals("1.96.1", req.getHeader("Version"))
        assertEquals("android_app", req.getHeader("channel"))
        assertEquals("Xiaomi", req.getHeader("phoneBrand"))
        assertEquals("1700000000000", req.getHeader("timestamp"))
        assertEquals(
            QiekjSigner.sign("/user/info", "1700000000000", "abc_tok", "1.96.1"),
            req.getHeader("sign")
        )
    }

    @Test
    fun `verifyLogin rejects newRegister empty account and invalid phone`() = runTest {
        val client = QiekjHttpClient(
            baseUrl = server.url("/").toString(),
            tokenProvider = { "" },
            phoneBrandProvider = { "Pixel" }
        )
        var savedToken = ""
        val api = QiekjApi(client, onTokenUpdated = { t, _ -> savedToken = t })

        assertEquals(LoginOutcome.BadPhone, api.verifyLogin("12345678910", "1234"))

        server.enqueue(
            MockResponse().setBody("""{"code":0,"data":{"token":"fake_new","newRegister":true}}""")
        )
        assertEquals(LoginOutcome.NewAccountRejected, api.verifyLogin("13800138000", "1234"))
        assertEquals("", savedToken)
    }

    @Test
    fun `unlock parses orderNo and bleCmd from nested cmd object`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"code":0,"data":{"orderNo":"ORD998","msgId":"M1","cmd":{"bleCmd":"aabbccdd","encryptionCmd":"11223344"}}}"""
            )
        )
        val client = QiekjHttpClient(
            baseUrl = server.url("/").toString(),
            tokenProvider = { "tok" },
            phoneBrandProvider = { "Pixel" }
        )
        val api = QiekjApi(client)
        val res = api.unlock("sku_1")
        assertTrue(res is ApiResult.Success)
        val payload = (res as ApiResult.Success).data
        assertEquals("ORD998", payload.orderNo)
        assertEquals("aabbccdd", payload.bleCmd)
        assertEquals("11223344", payload.encryptionCmd)
    }
}
