package com.troyh.tvdinner

import com.troyh.tvdinner.data.network.XtreamApiClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

class NativeMediaAndNetworkTest {

    @Test
    fun testOkHttpClient_permissiveSSLAndVlcHeader() {
        val apiClient = XtreamApiClient()
        val client = apiClient.okHttpClient

        assertNotNull(client)
        assertTrue(client.followRedirects)
        assertTrue(client.followSslRedirects)
        assertEquals(8000, client.connectTimeoutMillis)

        // Permissive HostnameVerifier
        val verifier = client.hostnameVerifier
        assertTrue(verifier.verify("localhost", null))
        assertTrue(verifier.verify("untrusted-stream.net", null))
        assertTrue(verifier.verify("91.239.79.63", null))
        assertTrue(verifier.verify("expired-cert.iptv-relay.org", null))
    }

    @Test
    fun testSSL_permissiveTrustManagerDoesNotThrowOnAnyCert() {
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }

        try {
            trustManager.checkClientTrusted(null, null)
            trustManager.checkClientTrusted(emptyArray(), "RSA")
            trustManager.checkServerTrusted(null, null)
            trustManager.checkServerTrusted(emptyArray(), "ECDHE_RSA")
            assertEquals(0, trustManager.acceptedIssuers.size)
        } catch (e: CertificateException) {
            org.junit.Assert.fail("TrustManager threw CertificateException: ${e.message}")
        }
    }
}
