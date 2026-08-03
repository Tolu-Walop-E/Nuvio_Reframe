package com.nuvio.tv.ui.screens.account

import com.nuvio.tv.domain.model.AuthState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailAuthFlowTest {

    @Test
    fun `signed out users see email sign in and create account choices`() {
        assertEquals(EmailAuthEntryState.SignedOut, emailAuthEntryState(AuthState.SignedOut))

        val choices = phase1AuthChoices().associateBy { it.action }
        assertTrue(choices.getValue(Phase1AuthAction.SignInWithEmail).enabled)
        assertTrue(choices.getValue(Phase1AuthAction.CreateAccount).enabled)
    }

    @Test
    fun `sync code linking is unavailable in phase one`() {
        val syncCodeChoice = phase1AuthChoices()
            .single { it.action == Phase1AuthAction.LinkWithSyncCode }

        assertFalse(syncCodeChoice.enabled)
    }

    @Test
    fun `invalid email blocks account creation`() {
        assertEquals(
            EmailAuthValidationError.InvalidEmail,
            validateEmailAuthForm(
                mode = EmailAuthMode.CreateAccount,
                email = "not-an-email",
                password = "password",
                confirmPassword = "password"
            )
        )
    }

    @Test
    fun `short password blocks account creation`() {
        assertEquals(
            EmailAuthValidationError.PasswordTooShort,
            validateEmailAuthForm(
                mode = EmailAuthMode.CreateAccount,
                email = "viewer@example.com",
                password = "12345",
                confirmPassword = "12345"
            )
        )
    }

    @Test
    fun `mismatched passwords block account creation`() {
        assertEquals(
            EmailAuthValidationError.PasswordsDoNotMatch,
            validateEmailAuthForm(
                mode = EmailAuthMode.CreateAccount,
                email = "viewer@example.com",
                password = "password",
                confirmPassword = "different"
            )
        )
    }

    @Test
    fun `valid account creation form passes validation`() {
        assertNull(
            validateEmailAuthForm(
                mode = EmailAuthMode.CreateAccount,
                email = "viewer@example.com",
                password = "password",
                confirmPassword = "password"
            )
        )
    }

    @Test
    fun `restored signed in session bypasses login choice`() {
        assertEquals(
            EmailAuthEntryState.Authenticated,
            emailAuthEntryState(
                AuthState.FullAccount(
                    userId = "user-1",
                    email = "viewer@example.com"
                )
            )
        )
    }
}
