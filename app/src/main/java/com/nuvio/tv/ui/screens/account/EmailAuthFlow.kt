package com.nuvio.tv.ui.screens.account

import com.nuvio.tv.domain.model.AuthState

const val MIN_EMAIL_AUTH_PASSWORD_LENGTH = 6

enum class EmailAuthMode {
    Choice,
    SignIn,
    CreateAccount
}

enum class Phase1AuthAction {
    SignInWithEmail,
    CreateAccount,
    LinkWithSyncCode
}

data class Phase1AuthChoice(
    val action: Phase1AuthAction,
    val enabled: Boolean
)

enum class EmailAuthEntryState {
    Loading,
    SignedOut,
    Authenticated
}

enum class EmailAuthValidationError {
    InvalidEmail,
    PasswordRequired,
    PasswordTooShort,
    PasswordsDoNotMatch
}

fun phase1AuthChoices(): List<Phase1AuthChoice> = listOf(
    Phase1AuthChoice(Phase1AuthAction.SignInWithEmail, enabled = true),
    Phase1AuthChoice(Phase1AuthAction.CreateAccount, enabled = true),
    Phase1AuthChoice(Phase1AuthAction.LinkWithSyncCode, enabled = false)
)

fun emailAuthEntryState(authState: AuthState): EmailAuthEntryState = when (authState) {
    AuthState.Loading -> EmailAuthEntryState.Loading
    AuthState.SignedOut -> EmailAuthEntryState.SignedOut
    is AuthState.FullAccount -> EmailAuthEntryState.Authenticated
}

fun validateEmailAuthForm(
    mode: EmailAuthMode,
    email: String,
    password: String,
    confirmPassword: String = ""
): EmailAuthValidationError? {
    if (!EMAIL_PATTERN.matches(email.trim())) {
        return EmailAuthValidationError.InvalidEmail
    }
    if (password.isBlank()) {
        return EmailAuthValidationError.PasswordRequired
    }
    if (mode == EmailAuthMode.CreateAccount && password.length < MIN_EMAIL_AUTH_PASSWORD_LENGTH) {
        return EmailAuthValidationError.PasswordTooShort
    }
    if (mode == EmailAuthMode.CreateAccount && password != confirmPassword) {
        return EmailAuthValidationError.PasswordsDoNotMatch
    }
    return null
}

private val EMAIL_PATTERN = Regex(
    pattern = "^[A-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?(?:\\.[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?)+$",
    option = RegexOption.IGNORE_CASE
)
