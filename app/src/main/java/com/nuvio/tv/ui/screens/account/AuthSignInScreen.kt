@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.account

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.ui.theme.NuvioTheme

private val AuthPaneBackground = Color.White.copy(alpha = 0.035f)
private val AuthTextPrimary = Color(0xFFF5F7F8)
private val AuthTextSecondary = Color(0xFFA5ABB2)
private val AuthErrorBackground = Color(0x33C62828)
private val AuthErrorText = Color(0xFFFF8A80)

@Composable
fun AuthSignInScreen(
    onBackPress: () -> Unit = {},
    onContinue: (() -> Unit)? = null,
    initialMode: EmailAuthMode = EmailAuthMode.Choice,
    onSuccess: (() -> Unit)? = null,
    viewModel: AccountViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val entryState = emailAuthEntryState(uiState.authState)
    var mode by rememberSaveable(initialMode) { mutableStateOf(initialMode) }
    var completionHandled by remember { mutableStateOf(false) }

    fun returnToChoices() {
        viewModel.clearError()
        mode = EmailAuthMode.Choice
    }

    BackHandler {
        if (mode == EmailAuthMode.Choice) {
            onBackPress()
        } else {
            returnToChoices()
        }
    }

    LaunchedEffect(entryState, uiState.isLoading, uiState.error) {
        if (
            entryState == EmailAuthEntryState.Authenticated &&
            !uiState.isLoading &&
            uiState.error.isNullOrBlank() &&
            !completionHandled
        ) {
            completionHandled = true
            when {
                onSuccess != null -> onSuccess()
                onContinue != null -> onContinue()
                else -> onBackPress()
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AuthBrandPanel(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 56.dp)
        )

        Box(
            modifier = Modifier
                .width(520.dp)
                .fillMaxHeight()
                .background(AuthPaneBackground)
                .padding(horizontal = 52.dp, vertical = 38.dp),
            contentAlignment = Alignment.Center
        ) {
            when (entryState) {
                EmailAuthEntryState.Loading -> Text(
                    text = stringResource(R.string.auth_restoring_session),
                    color = AuthTextSecondary,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )

                EmailAuthEntryState.Authenticated -> Text(
                    text = stringResource(R.string.auth_qr_finishing),
                    color = AuthTextSecondary,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )

                EmailAuthEntryState.SignedOut -> when (mode) {
                    EmailAuthMode.Choice -> AuthChoicePane(
                        onSignIn = {
                            viewModel.clearError()
                            mode = EmailAuthMode.SignIn
                        },
                        onCreateAccount = {
                            viewModel.clearError()
                            mode = EmailAuthMode.CreateAccount
                        },
                        onContinueWithoutAccount = onContinue
                    )

                    EmailAuthMode.SignIn,
                    EmailAuthMode.CreateAccount -> EmailAuthForm(
                        mode = mode,
                        uiState = uiState,
                        onSubmit = { email, password ->
                            if (mode == EmailAuthMode.CreateAccount) {
                                viewModel.signUp(email, password)
                            } else {
                                viewModel.signIn(email, password)
                            }
                        },
                        onBackToChoices = ::returnToChoices,
                        onClearRemoteError = viewModel::clearError
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthBrandPanel(modifier: Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(R.drawable.app_logo_wordmark),
            contentDescription = stringResource(R.string.cd_nuvio),
            modifier = Modifier.height(58.dp),
            contentScale = ContentScale.Fit,
            alignment = Alignment.CenterStart
        )
        Spacer(Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.auth_qr_tagline),
            modifier = Modifier.widthIn(max = 500.dp),
            style = MaterialTheme.typography.displayMedium.copy(
                color = AuthTextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 44.sp,
                lineHeight = 50.sp
            )
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.auth_choice_description),
            modifier = Modifier.widthIn(max = 500.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = AuthTextSecondary
        )
    }
}

@Composable
private fun AuthChoicePane(
    onSignIn: () -> Unit,
    onCreateAccount: () -> Unit,
    onContinueWithoutAccount: (() -> Unit)?
) {
    val firstChoiceFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        firstChoiceFocusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("phase1_auth_choice"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = stringResource(R.string.auth_choice_title),
            style = MaterialTheme.typography.headlineMedium,
            color = AuthTextPrimary,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = stringResource(R.string.auth_email_instruction),
            style = MaterialTheme.typography.bodyMedium,
            color = AuthTextSecondary
        )
        Spacer(Modifier.height(8.dp))

        phase1AuthChoices().forEachIndexed { index, choice ->
            when (choice.action) {
                Phase1AuthAction.SignInWithEmail -> AuthChoiceButton(
                    icon = Icons.Default.Email,
                    title = stringResource(R.string.auth_choice_sign_in_email),
                    subtitle = stringResource(R.string.account_signin_email_subtitle),
                    enabled = choice.enabled,
                    onClick = onSignIn,
                    modifier = Modifier
                        .then(if (index == 0) Modifier.focusRequester(firstChoiceFocusRequester) else Modifier)
                        .testTag("auth_sign_in_action")
                )

                Phase1AuthAction.CreateAccount -> AuthChoiceButton(
                    icon = Icons.Default.PersonAdd,
                    title = stringResource(R.string.auth_choice_create_account),
                    subtitle = stringResource(
                        R.string.auth_create_account_description,
                        MIN_EMAIL_AUTH_PASSWORD_LENGTH
                    ),
                    enabled = choice.enabled,
                    onClick = onCreateAccount,
                    modifier = Modifier.testTag("auth_create_account_action")
                )

                Phase1AuthAction.LinkWithSyncCode -> AuthChoiceButton(
                    icon = Icons.Default.LinkOff,
                    title = stringResource(R.string.auth_choice_link_sync_code),
                    subtitle = stringResource(R.string.auth_choice_link_unavailable),
                    enabled = choice.enabled,
                    onClick = {},
                    modifier = Modifier.testTag("auth_sync_code_unavailable")
                )
            }
        }

        if (onContinueWithoutAccount != null) {
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onContinueWithoutAccount,
                modifier = Modifier.fillMaxWidth(),
                colors = secondaryButtonColors(),
                shape = ButtonDefaults.shape(RoundedCornerShape(12.dp))
            ) {
                Text(stringResource(R.string.auth_qr_continue_without_account))
            }
        }
    }
}

@Composable
private fun AuthChoiceButton(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        colors = ButtonDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.08f),
            focusedContainerColor = Color.White,
            contentColor = AuthTextPrimary,
            focusedContentColor = Color.Black,
            disabledContainerColor = Color.White.copy(alpha = 0.035f),
            disabledContentColor = AuthTextSecondary.copy(alpha = 0.62f)
        ),
        shape = ButtonDefaults.shape(RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) Color.Unspecified else AuthTextSecondary.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun EmailAuthForm(
    mode: EmailAuthMode,
    uiState: AccountUiState,
    onSubmit: (String, String) -> Unit,
    onBackToChoices: () -> Unit,
    onClearRemoteError: () -> Unit
) {
    var email by rememberSaveable(mode) { mutableStateOf("") }
    var password by rememberSaveable(mode) { mutableStateOf("") }
    var confirmPassword by rememberSaveable(mode) { mutableStateOf("") }
    var validationError by rememberSaveable(mode) { mutableStateOf<EmailAuthValidationError?>(null) }
    var passwordEditRequestId by remember(mode) { mutableStateOf(0) }
    var confirmPasswordEditRequestId by remember(mode) { mutableStateOf(0) }
    val emailFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }
    val confirmPasswordFocusRequester = remember { FocusRequester() }

    fun clearErrors() {
        validationError = null
        onClearRemoteError()
    }

    val submit = {
        if (!uiState.isLoading) {
            val error = validateEmailAuthForm(mode, email, password, confirmPassword)
            validationError = error
            if (error == null) {
                onClearRemoteError()
                onSubmit(email.trim(), password)
            }
        }
    }

    LaunchedEffect(mode) {
        emailFocusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(if (mode == EmailAuthMode.CreateAccount) "create_account_form" else "email_sign_in_form"),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Text(
            text = stringResource(
                if (mode == EmailAuthMode.CreateAccount) {
                    R.string.auth_create_account_title
                } else {
                    R.string.auth_signin_title
                }
            ),
            style = MaterialTheme.typography.headlineMedium,
            color = AuthTextPrimary,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = if (mode == EmailAuthMode.CreateAccount) {
                stringResource(R.string.auth_create_account_description, MIN_EMAIL_AUTH_PASSWORD_LENGTH)
            } else {
                stringResource(R.string.auth_email_instruction)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = AuthTextSecondary
        )
        Spacer(Modifier.height(4.dp))

        InputField(
            value = email,
            onValueChange = {
                email = it
                clearErrors()
            },
            placeholder = stringResource(R.string.auth_email_placeholder),
            modifier = Modifier
                .focusRequester(emailFocusRequester)
                .testTag("auth_email_field"),
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
            onImeAction = {
                passwordFocusRequester.requestFocus()
                passwordEditRequestId += 1
            }
        )
        InputField(
            value = password,
            onValueChange = {
                password = it
                clearErrors()
            },
            placeholder = stringResource(R.string.auth_password_placeholder),
            modifier = Modifier
                .focusRequester(passwordFocusRequester)
                .testTag("auth_password_field"),
            keyboardType = KeyboardType.Password,
            isPassword = true,
            imeAction = if (mode == EmailAuthMode.CreateAccount) ImeAction.Next else ImeAction.Done,
            editRequestId = passwordEditRequestId,
            onImeAction = {
                if (mode == EmailAuthMode.CreateAccount) {
                    confirmPasswordFocusRequester.requestFocus()
                    confirmPasswordEditRequestId += 1
                } else {
                    submit()
                }
            }
        )
        if (mode == EmailAuthMode.CreateAccount) {
            InputField(
                value = confirmPassword,
                onValueChange = {
                    confirmPassword = it
                    clearErrors()
                },
                placeholder = stringResource(R.string.auth_confirm_password_placeholder),
                modifier = Modifier
                    .focusRequester(confirmPasswordFocusRequester)
                    .testTag("auth_confirm_password_field"),
                keyboardType = KeyboardType.Password,
                isPassword = true,
                imeAction = ImeAction.Done,
                editRequestId = confirmPasswordEditRequestId,
                onImeAction = submit
            )
        }

        val displayedError = validationError?.let { validationErrorMessage(it) }
            ?: uiState.error?.takeIf(String::isNotBlank)
        if (displayedError != null) {
            Text(
                text = displayedError,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AuthErrorBackground, RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .testTag("auth_error_message"),
                color = AuthErrorText,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Button(
            onClick = submit,
            enabled = !uiState.isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("auth_submit_action"),
            colors = ButtonDefaults.colors(
                containerColor = Color.White,
                focusedContainerColor = Color(0xFFE9DFFF),
                contentColor = Color.Black,
                focusedContentColor = Color.Black,
                disabledContainerColor = Color.White.copy(alpha = 0.12f),
                disabledContentColor = AuthTextSecondary
            ),
            shape = ButtonDefaults.shape(RoundedCornerShape(12.dp))
        ) {
            Text(
                text = when {
                    uiState.isLoading && mode == EmailAuthMode.CreateAccount -> stringResource(R.string.auth_creating_account)
                    uiState.isLoading -> stringResource(R.string.auth_email_signing_in)
                    mode == EmailAuthMode.CreateAccount -> stringResource(R.string.auth_create_account_action)
                    else -> stringResource(R.string.auth_email_sign_in)
                },
                modifier = Modifier.padding(vertical = 5.dp),
                fontWeight = FontWeight.SemiBold
            )
        }
        Button(
            onClick = onBackToChoices,
            enabled = !uiState.isLoading,
            modifier = Modifier.fillMaxWidth(),
            colors = secondaryButtonColors(),
            shape = ButtonDefaults.shape(RoundedCornerShape(12.dp))
        ) {
            Text(stringResource(R.string.auth_back_to_choices))
        }
    }
}

@Composable
private fun validationErrorMessage(error: EmailAuthValidationError): String = when (error) {
    EmailAuthValidationError.InvalidEmail -> stringResource(R.string.auth_email_invalid)
    EmailAuthValidationError.PasswordRequired -> stringResource(R.string.auth_password_required)
    EmailAuthValidationError.PasswordTooShort -> stringResource(
        R.string.auth_password_minimum,
        MIN_EMAIL_AUTH_PASSWORD_LENGTH
    )
    EmailAuthValidationError.PasswordsDoNotMatch -> stringResource(R.string.auth_passwords_do_not_match)
}

@Composable
private fun secondaryButtonColors() = ButtonDefaults.colors(
    containerColor = Color.White.copy(alpha = 0.055f),
    focusedContainerColor = Color.White,
    contentColor = AuthTextPrimary,
    focusedContentColor = Color.Black,
    disabledContainerColor = Color.White.copy(alpha = 0.025f),
    disabledContentColor = AuthTextSecondary.copy(alpha = 0.5f)
)
