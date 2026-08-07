package com.nuvio.tv.core.auth

enum class AuthFailureReason {
    InvalidCredentials,
    EmailNotConfirmed,
    EmailAlreadyRegistered,
    InvalidEmail,
    PasswordTooShort,
    PasswordTooWeak,
    SignupDisabled,
    RateLimited,
    NetworkUnavailable,
    ConnectionTimeout,
    ConnectionRefused,
    MalformedResponse,
    MissingConfiguration,
    Unknown
}

class AuthFailureException(
    val reason: AuthFailureReason
) : Exception(reason.safeDiagnosticMessage())

private fun AuthFailureReason.safeDiagnosticMessage(): String = when (this) {
    AuthFailureReason.InvalidCredentials -> "Invalid login credentials"
    AuthFailureReason.EmailNotConfirmed -> "Email not confirmed"
    AuthFailureReason.EmailAlreadyRegistered -> "User already registered"
    AuthFailureReason.InvalidEmail -> "Invalid email"
    AuthFailureReason.PasswordTooShort -> "Password is too short"
    AuthFailureReason.PasswordTooWeak -> "Password is too weak"
    AuthFailureReason.SignupDisabled -> "Signup is disabled"
    AuthFailureReason.RateLimited -> "Authentication rate limit reached"
    AuthFailureReason.NetworkUnavailable -> "Authentication network unavailable"
    AuthFailureReason.ConnectionTimeout -> "Authentication connection timed out"
    AuthFailureReason.ConnectionRefused -> "Authentication connection refused"
    AuthFailureReason.MalformedResponse -> "Malformed Supabase authentication response"
    AuthFailureReason.MissingConfiguration -> "Supabase authentication configuration is missing"
    AuthFailureReason.Unknown -> "Authentication failed"
}
