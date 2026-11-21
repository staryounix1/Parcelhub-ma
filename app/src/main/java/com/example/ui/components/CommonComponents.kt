package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.carriers.LabelResult
import com.example.domain.model.MerchantSession
import com.example.domain.model.UnifiedParcelStatus
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AppLanguage
import com.example.ui.theme.CoralContainer
import com.example.ui.theme.CoralRetour
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.IndigoContainer
import com.example.ui.theme.LocalAppLanguage
import com.example.ui.theme.OnAmberContainer
import com.example.ui.theme.OnCoralContainer
import com.example.ui.theme.OnEmeraldContainer
import com.example.ui.theme.OnIndigoContainer
import com.example.ui.theme.strings

@Composable
fun ParcelStatusBadge(
    status: UnifiedParcelStatus,
    modifier: Modifier = Modifier
) {
    val isArabic = LocalAppLanguage.current == AppLanguage.AR
    val (bg, fg) = when (status) {
        UnifiedParcelStatus.DELIVERED -> EmeraldContainer to OnEmeraldContainer
        UnifiedParcelStatus.OUT_FOR_DELIVERY, UnifiedParcelStatus.IN_TRANSIT -> IndigoContainer to OnIndigoContainer
        UnifiedParcelStatus.CREATED, UnifiedParcelStatus.PICKED_UP -> AmberContainer to OnAmberContainer
        UnifiedParcelStatus.REFUSED, UnifiedParcelStatus.RETURNED, UnifiedParcelStatus.CANCELLED -> CoralContainer to OnCoralContainer
    }
    Surface(
        color = bg,
        contentColor = fg,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Text(
            text = status.label(isArabic),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParcelHubTopBar(
    session: MerchantSession,
    onToggleLanguage: () -> Unit,
    onOpenAuth: () -> Unit
) {
    val s = strings()
    val currentLang = LocalAppLanguage.current

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        ),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocalShipping,
                        contentDescription = stringResource(R.string.cd_app_logo),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = s.appTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = session.storeBrandName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        actions = {
            FilledTonalButton(
                onClick = onToggleLanguage,
                modifier = Modifier
                    .testTag("language_toggle_button")
                    .minimumInteractiveComponentSize()
                    .padding(end = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Language,
                    contentDescription = stringResource(R.string.cd_language_toggle),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (currentLang == AppLanguage.AR) "FR" else "AR",
                    style = MaterialTheme.typography.labelLarge
                )
            }
            IconButton(
                onClick = onOpenAuth,
                modifier = Modifier
                    .testTag("account_auth_button")
                    .minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Filled.AccountCircle,
                    contentDescription = s.accountBtn,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    )
}

private val EMAIL_PATTERN = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

@Composable
fun MerchantAccountDialog(
    session: MerchantSession,
    isBusy: Boolean,
    authErrorMessage: String?,
    onClearError: () -> Unit,
    onDismiss: () -> Unit,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String, String) -> Unit,
    onSignOut: () -> Unit
) {
    val s = strings()
    val isArabic = LocalAppLanguage.current == AppLanguage.AR

    var isSignUpMode by remember { mutableStateOf(session.accessToken == null) }
    var storeName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf(if (session.accessToken == null) "" else session.email) }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    val displayedError = localError ?: authErrorMessage

    fun switchMode(signUp: Boolean) {
        isSignUpMode = signUp
        localError = null
        onClearError()
        password = ""
        confirmPassword = ""
        if (signUp) {
            if (email == "atlas.shop@parcelhub.ma") email = ""
        } else if (email.isBlank()) {
            email = session.email
        }
    }

    fun submit() {
        localError = null
        onClearError()
        val trimmedEmail = email.trim()
        val trimmedStore = storeName.trim()

        if (isSignUpMode && trimmedStore.length < 2) {
            localError = if (isArabic) {
                "يرجى إدخال اسم المتجر (حرفين على الأقل)"
            } else {
                "Veuillez saisir le nom de la boutique (2 caractères min.)"
            }
            return
        }
        if (!EMAIL_PATTERN.matches(trimmedEmail)) {
            localError = if (isArabic) {
                "يرجى إدخال بريد إلكتروني صالح (مثال: name@shop.ma)"
            } else {
                "Veuillez saisir une adresse email valide"
            }
            return
        }
        if (password.length < 6) {
            localError = if (isArabic) {
                "كلمة المرور يجب أن تتكون من 6 أحرف على الأقل"
            } else {
                "Le mot de passe doit contenir au moins 6 caractères"
            }
            return
        }
        if (isSignUpMode && password != confirmPassword) {
            localError = if (isArabic) {
                "كلمتا المرور غير متطابقتين"
            } else {
                "Les mots de passe ne correspondent pas"
            }
            return
        }

        if (isSignUpMode) {
            onSignUp(trimmedStore, trimmedEmail, password)
        } else {
            onSignIn(trimmedEmail, password)
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = {
            Text(text = s.signInTitle, style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Surface(
                    color = EmeraldContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.padding(10.dp)
                    ) {
                        Text(
                            text = if (isArabic) "الحساب النشط حالياً:" else "Compte actif :",
                            style = MaterialTheme.typography.labelSmall,
                            color = OnEmeraldContainer
                        )
                        Text(
                            text = "${session.storeBrandName} • ${session.email}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = OnEmeraldContainer
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilterChip(
                        selected = isSignUpMode,
                        onClick = { switchMode(true) },
                        label = {
                            Text(
                                text = s.signUpBtn,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("auth_tab_signup")
                    )
                    FilterChip(
                        selected = !isSignUpMode,
                        onClick = { switchMode(false) },
                        label = {
                            Text(
                                text = s.signInBtn,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("auth_tab_signin")
                    )
                }

                if (isSignUpMode) {
                    OutlinedTextField(
                        value = storeName,
                        onValueChange = {
                            storeName = it
                            localError = null
                        },
                        label = { Text(s.storeNameLabel) },
                        placeholder = {
                            Text(if (isArabic) "مثال: Atlas Shop MA" else "Ex: Atlas Shop MA")
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_store_name_input")
                    )
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        localError = null
                        onClearError()
                    },
                    label = { Text(s.emailLabel) },
                    placeholder = { Text("merchant@shop.ma") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    trailingIcon = {
                        if (email.isNotEmpty()) {
                            IconButton(onClick = { email = "" }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = if (isArabic) "مسح البريد" else "Effacer"
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_email_input")
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        localError = null
                        onClearError()
                    },
                    label = { Text(s.passwordLabel) },
                    supportingText = {
                        Text(
                            if (isArabic) "6 أحرف على الأقل" else "6 caractères minimum"
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (isArabic) "إظهار أو إخفاء كلمة المرور" else "Afficher/Masquer"
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_password_input")
                )

                if (isSignUpMode) {
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = {
                            confirmPassword = it
                            localError = null
                        },
                        label = { Text(s.confirmPasswordLabel) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_confirm_password_input")
                    )
                }

                if (displayedError != null) {
                    Surface(
                        color = CoralContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_error_banner")
                    ) {
                        Text(
                            text = displayedError,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = OnCoralContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Button(
                    onClick = { submit() },
                    enabled = !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag(if (isSignUpMode) "auth_signup_button" else "auth_signin_button")
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (isSignUpMode) s.signUpBtn else s.signInBtn)
                }

                Text(
                    text = if (isArabic) {
                        "معطيات متجرك وزبنائك محمية وفق القانون المغربي 09-08."
                    } else {
                        "Vos données et celles de vos clients sont protégées conformément à la loi 09-08."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            if (session.accessToken != null) {
                TextButton(
                    enabled = !isBusy,
                    onClick = onSignOut
                ) {
                    Text(s.signOutBtn, color = CoralRetour)
                }
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isBusy,
                onClick = onDismiss
            ) {
                Text(s.closeBtn)
            }
        }
    )
}

@Composable
fun WaybillLabelDialog(
    label: LabelResult,
    onDismiss: () -> Unit
) {
    val s = strings()
    val isArabic = LocalAppLanguage.current == AppLanguage.AR

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.QrCode2,
                contentDescription = s.viewLabelBtn,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(text = label.waybillTitle, style = MaterialTheme.typography.titleMedium)
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PARCELHUB MA",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape
                    ) {
                        Text(
                            text = "COD",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                HorizontalDivider()
                Text(
                    text = label.trackingNumber,
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = label.barcodeText,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Text(
                    text = if (isArabic) "بوليصة PDF جاهزة للطباعة الحرارية (A6)" else "Bordereau PDF prêt pour impression (A6)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.testTag("close_label_preview_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Print,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(s.closeBtn)
            }
        }
    )
}
