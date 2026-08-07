package com.nuvio.tv.core.viewpack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLEncoder

class ViewPackRemoteImportTest {
    @Test
    fun resolvePayload_rawJson() {
        val json = """{"schemaVersion":1,"id":"demo","name":"Demo","blocks":[]}"""
        val payload = ViewPackRemoteImport.resolvePayload(json)
        assertTrue(payload is ViewPackRemoteImport.PackPayload.Json)
        assertEquals(json, (payload as ViewPackRemoteImport.PackPayload.Json).text)
    }

    @Test
    fun resolvePayload_httpsUrl() {
        val url = "https://jsonblob.com/api/jsonBlob/abc"
        val payload = ViewPackRemoteImport.resolvePayload(url)
        assertEquals(
            ViewPackRemoteImport.PackPayload.RemoteUrl(url),
            payload
        )
    }

    @Test
    fun resolvePayload_httpLanUrl() {
        val url = "http://192.168.0.10:5173/__viewpacks/demo.json"
        val payload = ViewPackRemoteImport.resolvePayload(url)
        assertEquals(
            ViewPackRemoteImport.PackPayload.RemoteUrl(url),
            payload
        )
    }

    @Test
    fun resolvePayload_deepLink() {
        val packUrl = "https://litter.catbox.moe/pack.view.json"
        val deep =
            "nuvio://viewpack?url=${URLEncoder.encode(packUrl, "UTF-8")}"
        val payload = ViewPackRemoteImport.resolvePayload(deep)
        assertEquals(
            ViewPackRemoteImport.PackPayload.RemoteUrl(packUrl),
            payload
        )
    }

    @Test
    fun extractViewPackDeepLinkUrl_httpsOnlyRejectsHttp() {
        val deep =
            "nuvio://viewpack?url=${URLEncoder.encode("http://192.168.0.1/p.json", "UTF-8")}"
        assertNull(ViewPackRemoteImport.extractViewPackDeepLinkUrl(deep, httpsOnly = true))
        assertEquals(
            "http://192.168.0.1/p.json",
            ViewPackRemoteImport.extractViewPackDeepLinkUrl(deep, httpsOnly = false)
        )
    }

    @Test
    fun resolvePayload_garbage() {
        assertNull(ViewPackRemoteImport.resolvePayload("not-a-pack"))
    }
}
