package com.nuvio.tv.core.auth.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthDiagnosticsRedactionTest {

    @Test
    fun `auth request body redacts credentials and tokens`() {
        val filtered = authDiagnosticFilteredBody(
            """{"email":"viewer@example.com","password":"plain-secret","access_token":"access-secret","refresh_token":"refresh-secret","apikey":"api-secret"}"""
        ).orEmpty()

        assertTrue(filtered.contains("viewer@example.com"))
        assertFalse(filtered.contains("plain-secret"))
        assertFalse(filtered.contains("access-secret"))
        assertFalse(filtered.contains("refresh-secret"))
        assertFalse(filtered.contains("api-secret"))
    }

    @Test
    fun `auth headers redact authorization and api key values`() {
        val filtered = authDiagnosticFilteredHeaders(
            mapOf(
                "Authorization" to "Bearer access-secret",
                "apikey" to "api-secret",
                "Content-Type" to "application/json"
            )
        )

        assertEquals("application/json", filtered["Content-Type"])
        assertFalse(filtered.values.any { it.contains("access-secret") })
        assertFalse(filtered.values.any { it.contains("api-secret") })
    }
}
