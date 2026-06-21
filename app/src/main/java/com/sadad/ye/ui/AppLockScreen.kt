package com.sadad.ye.ui

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.sadad.ye.R

@Composable
fun AppLockScreen(correctPin: String, isBiometricEnabled: Boolean, onUnlock: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    val unlockTitle = stringResource(R.string.unlock_title)
    val unlockSubtitle = stringResource(R.string.unlock_subtitle)
    val cancelStr = stringResource(R.string.cancel)

    // دالة لتشغيل بصمة الإصبع
    val showBiometricPrompt = {
        val activity = context as? FragmentActivity
        if (activity != null) {
            val executor = ContextCompat.getMainExecutor(activity)
            val biometricPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onUnlock()
                }
            })

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(unlockTitle)
                .setSubtitle(unlockSubtitle)
                .setNegativeButtonText(cancelStr)
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build()

            biometricPrompt.authenticate(promptInfo)
        }
    }

    // تشغيل البصمة تلقائياً عند فتح الشاشة إذا كانت مفعلة
    LaunchedEffect(Unit) {
        if (isBiometricEnabled) {
            showBiometricPrompt()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.app_protected_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = stringResource(R.string.enter_pin_to_continue),
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = { 
                if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                    pin = it
                    isError = false
                    if (it == correctPin) {
                        onUnlock()
                    } else if (it.length == 4) {
                        isError = true
                    }
                }
            },
            label = { Text(stringResource(R.string.pin_label)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(200.dp),
            isError = isError
        )

        if (isError) {
            Text(
                text = stringResource(R.string.wrong_pin),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (isBiometricEnabled) {
            Spacer(modifier = Modifier.height(16.dp))
            IconButton(
                onClick = { showBiometricPrompt() },
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    Icons.Default.Fingerprint,
                    contentDescription = stringResource(R.string.use_biometric_desc),
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Text(stringResource(R.string.click_to_use_biometric), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = stringResource(R.string.app_lock_hint),
            style = MaterialTheme.typography.bodySmall,
            color = Color.LightGray
        )
    }
}
