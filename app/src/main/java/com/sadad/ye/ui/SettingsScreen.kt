package com.sadad.ye.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.sadad.ye.R
import com.sadad.ye.models.AppSettings
import com.sadad.ye.models.Customer
import com.sadad.ye.models.Transaction
import com.sadad.ye.models.User
import com.sadad.ye.utils.AccountUtils
import com.sadad.ye.utils.BackupUtils
import com.sadad.ye.utils.ExportUtils
import com.sadad.ye.utils.SubscriptionUtils
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    user: User?,
    appSettings: AppSettings, // استلام الإعدادات العامة
    onBack: () -> Unit,
    onAdminClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }
    var showAddressDialog by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showCurrencyDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showTemplateDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    
    var activationCode by remember { mutableStateOf("") }
    var isActivating by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var isDeletingAccount by remember { mutableStateOf(false) }
    
    // لاونشر اختيار ملف النسخة الاحتياطية
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            isImporting = true
            BackupUtils.importBackup(context, it) { success, msg ->
                isImporting = false
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    val currencies = listOf(
        stringResource(R.string.currency_yer),
        stringResource(R.string.currency_sar),
        stringResource(R.string.currency_aed),
        stringResource(R.string.currency_usd),
        stringResource(R.string.currency_egp)
    )
    val userNameForReport = user?.name ?: stringResource(R.string.default_user_name)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            ProfileHeader(user = user, email = auth.currentUser?.email ?: "")

            if (user?.isAdmin == true) {
                SettingsSectionTitle(title = stringResource(R.string.admin_panel))
                SettingsItem(
                    icon = Icons.Default.AdminPanelSettings,
                    title = stringResource(R.string.admin_panel),
                    subtitle = stringResource(R.string.admin_panel_subtitle),
                    onClick = onAdminClick
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            SettingsSectionTitle(title = stringResource(R.string.account_profile))
            SettingsItem(
                icon = Icons.Default.Store,
                title = stringResource(R.string.store_name),
                subtitle = user?.name ?: stringResource(R.string.default_user_name),
                onClick = { showNameDialog = true }
            )
            SettingsItem(
                icon = Icons.Default.LocationOn,
                title = stringResource(R.string.business_address),
                subtitle = user?.businessAddress ?: stringResource(R.string.business_address),
                onClick = { showAddressDialog = true }
            )
            SettingsItem(
                icon = Icons.Default.Lock,
                title = stringResource(R.string.password),
                subtitle = stringResource(R.string.change_password_subtitle),
                onClick = { showPasswordDialog = true }
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SettingsSectionTitle(title = stringResource(R.string.app_settings))
            
            SettingsItem(
                icon = Icons.Default.Language,
                title = stringResource(R.string.language),
                subtitle = if (AppCompatDelegate.getApplicationLocales().toLanguageTags().contains("en")) stringResource(R.string.english) else stringResource(R.string.arabic),
                onClick = { showLanguageDialog = true }
            )

            var isDarkMode by remember { mutableStateOf(user?.isDarkMode) }
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Brightness4, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.dark_mode), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = when(isDarkMode) {
                            true -> stringResource(R.string.dark_mode_on)
                            false -> stringResource(R.string.dark_mode_off)
                            else -> stringResource(R.string.dark_mode_auto)
                        },
                        style = MaterialTheme.typography.bodySmall, 
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isDarkMode == true,
                    onCheckedChange = { 
                        val newValue = if (isDarkMode == true) false else true
                        isDarkMode = newValue
                        auth.currentUser?.uid?.let { uid ->
                            db.collection("users").document(uid).update("isDarkMode", newValue)
                        }
                    }
                )
            }

            SettingsItem(
                icon = Icons.Default.MonetizationOn,
                title = stringResource(R.string.default_currency),
                subtitle = user?.defaultCurrency ?: stringResource(R.string.currency_yer),
                onClick = { showCurrencyDialog = true }
            )

            var voiceInstructionsEnabled by remember { mutableStateOf(user?.showVoiceInstructions ?: true) }
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.voice_instructions), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.voice_instructions_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = voiceInstructionsEnabled,
                    onCheckedChange = { 
                        voiceInstructionsEnabled = it
                        auth.currentUser?.uid?.let { uid ->
                            db.collection("users").document(uid).update("showVoiceInstructions", it)
                        }
                    }
                )
            }
            
            var appLockEnabled by remember { mutableStateOf(user?.isAppLockEnabled ?: false) }
            var biometricEnabled by remember { mutableStateOf(user?.isBiometricEnabled ?: false) }

            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.app_lock), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.app_lock_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = appLockEnabled,
                    onCheckedChange = { 
                        appLockEnabled = it
                        auth.currentUser?.uid?.let { uid ->
                            db.collection("users").document(uid).update("isAppLockEnabled", it)
                        }
                    }
                )
            }

            if (appLockEnabled) {
                // خيار تفعيل البصمة يظهر فقط إذا كان القفل مفعلاً
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Face, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.use_biometric), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.use_biometric_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = biometricEnabled,
                        onCheckedChange = { 
                            biometricEnabled = it
                            auth.currentUser?.uid?.let { uid ->
                                db.collection("users").document(uid).update("isBiometricEnabled", it)
                            }
                        }
                    )
                }

                SettingsItem(
                    icon = Icons.Default.Password,
                    title = stringResource(R.string.change_pin),
                    subtitle = stringResource(R.string.current_pin_prefix, user?.appLockPin ?: ""),
                    onClick = { showPinDialog = true }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SettingsSectionTitle(title = stringResource(R.string.debt_notifications))
            var debtNotificationEnabled by remember { mutableStateOf(user?.debtNotificationEnabled ?: true) }

            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.auto_notification_system), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.auto_notification_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = debtNotificationEnabled,
                    onCheckedChange = { 
                        debtNotificationEnabled = it
                        auth.currentUser?.uid?.let { uid ->
                            db.collection("users").document(uid).update("debtNotificationEnabled", it)
                        }
                    }
                )
            }

            SettingsItem(
                icon = Icons.Default.Message,
                title = stringResource(R.string.reminder_template),
                subtitle = stringResource(R.string.reminder_template_subtitle),
                onClick = { showTemplateDialog = true }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SettingsSectionTitle(title = stringResource(R.string.subscription))
            val expiryDate = user?.subscriptionExpiry?.toDate()
            val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
            SettingsItem(
                icon = Icons.Default.Star,
                title = stringResource(R.string.subscription_status),
                subtitle = if (expiryDate != null) stringResource(R.string.expiry_date_prefix, dateFormat.format(expiryDate)) else stringResource(R.string.trial_period),
                onClick = {}
            )
            
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = activationCode,
                    onValueChange = { activationCode = it },
                    label = { Text(stringResource(R.string.activate_new_code)) },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (isActivating) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(onClick = {
                                if (activationCode.length >= 6) {
                                    isActivating = true
                                    SubscriptionUtils.redeemCode(db, auth.currentUser?.uid ?: "", activationCode) { success, msg ->
                                        isActivating = false
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                        if (success) activationCode = ""
                                    }
                                } else {
                                    Toast.makeText(context, context.getString(R.string.code_too_short), Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Icon(Icons.Default.Check, contentDescription = stringResource(R.string.activate), tint = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SettingsSectionTitle(title = stringResource(R.string.data_backup))
            if (isExporting || isImporting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            }
            
            SettingsItem(
                icon = Icons.Default.CloudUpload,
                title = stringResource(R.string.create_backup),
                subtitle = stringResource(R.string.create_backup_subtitle),
                onClick = {
                    isExporting = true
                    fetchDataAndExport(db, auth.currentUser?.uid ?: "") { customers, transactions ->
                        BackupUtils.exportBackup(context, user, customers, transactions)
                        isExporting = false
                    }
                }
            )

            SettingsItem(
                icon = Icons.Default.CloudDownload,
                title = stringResource(R.string.restore_backup),
                subtitle = stringResource(R.string.restore_backup_subtitle),
                onClick = { importLauncher.launch("application/json") }
            )

            SettingsItem(
                icon = Icons.Default.PictureAsPdf,
                title = stringResource(R.string.export_pdf),
                subtitle = stringResource(R.string.export_pdf_subtitle),
                onClick = {
                    isExporting = true
                    fetchDataAndExport(db, auth.currentUser?.uid ?: "") { customers, transactions ->
                        ExportUtils.exportToPdf(context, customers, transactions, userNameForReport)
                        isExporting = false
                    }
                }
            )
            
            SettingsItem(
                icon = Icons.Default.TableChart,
                title = stringResource(R.string.export_excel),
                subtitle = stringResource(R.string.export_excel_subtitle),
                onClick = {
                    isExporting = true
                    fetchDataAndExport(db, auth.currentUser?.uid ?: "") { customers, transactions ->
                        ExportUtils.exportToExcel(context, customers, transactions, userNameForReport)
                        isExporting = false
                    }
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SettingsSectionTitle(title = stringResource(R.string.support_help))
            SettingsItem(
                icon = Icons.Default.SupportAgent,
                title = stringResource(R.string.contact_support),
                subtitle = stringResource(R.string.support_whatsapp_subtitle),
                onClick = { 
                    try {
                        val supportNumber = appSettings.supportPhone
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse("https://api.whatsapp.com/send?phone=$supportNumber&text=${URLEncoder.encode(context.getString(R.string.whatsapp_support_message), "UTF-8")}")
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, context.getString(R.string.whatsapp_error), Toast.LENGTH_SHORT).show()
                    }
                }
            )

            Spacer(modifier = Modifier.height(32.dp))
            
            Column(modifier = Modifier.padding(16.dp)) {
                Button(
                    onClick = {
                        auth.signOut()
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.logout))
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                TextButton(
                    onClick = { showDeleteAccountDialog = true },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    enabled = !isDeletingAccount
                ) {
                    if (isDeletingAccount) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.delete_account), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // الحوارات
    if (showNameDialog) {
        var newName by remember { mutableStateOf(user?.name ?: "") }
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text(stringResource(R.string.edit_store_name)) },
            text = { OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text(stringResource(R.string.name)) }) },
            confirmButton = {
                TextButton(onClick = {
                    auth.currentUser?.uid?.let { db.collection("users").document(it).update("name", newName) }
                    showNameDialog = false
                }) { Text(stringResource(R.string.save)) }
            }
        )
    }

    if (showAddressDialog) {
        var newAddress by remember { mutableStateOf(user?.businessAddress ?: "") }
        AlertDialog(
            onDismissRequest = { showAddressDialog = false },
            title = { Text(stringResource(R.string.edit_business_address)) },
            text = { OutlinedTextField(value = newAddress, onValueChange = { newAddress = it }, label = { Text(stringResource(R.string.address)) }) },
            confirmButton = {
                TextButton(onClick = {
                    auth.currentUser?.uid?.let { db.collection("users").document(it).update("businessAddress", newAddress) }
                    showAddressDialog = false
                }) { Text(stringResource(R.string.save)) }
            }
        )
    }

    if (showPinDialog) {
        var newPin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text(stringResource(R.string.change_pin_title)) },
            text = {
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) newPin = it },
                    label = { Text(stringResource(R.string.new_pin_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newPin.length == 4) {
                        auth.currentUser?.uid?.let { db.collection("users").document(it).update("appLockPin", newPin) }
                        showPinDialog = false
                    }
                }) { Text(stringResource(R.string.save)) }
            }
        )
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = { Text(stringResource(R.string.delete_account)) },
            text = { Text(stringResource(R.string.delete_account_confirmation)) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAccountDialog = false
                        isDeletingAccount = true
                        AccountUtils.deleteUserAccount { success, msg ->
                            isDeletingAccount = false
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            if (success) onBack()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) { Text(stringResource(R.string.confirm_delete)) }
            },
            dismissButton = { TextButton(onClick = { showDeleteAccountDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    if (showCurrencyDialog) {
        AlertDialog(
            onDismissRequest = { showCurrencyDialog = false },
            title = { Text(stringResource(R.string.choose_currency)) },
            text = {
                Column {
                    currencies.forEach { curr ->
                        Text(
                            text = curr,
                            modifier = Modifier.fillMaxWidth().clickable {
                                auth.currentUser?.uid?.let { db.collection("users").document(it).update("defaultCurrency", curr) }
                                showCurrencyDialog = false
                            }.padding(16.dp)
                        )
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showPasswordDialog) {
        var p1 by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            title = { Text(stringResource(R.string.change_password_title)) },
            text = { OutlinedTextField(value = p1, onValueChange = { p1 = it }, label = { Text(stringResource(R.string.new_password_label)) }, visualTransformation = PasswordVisualTransformation()) },
            confirmButton = {
                TextButton(onClick = {
                    if (p1.length >= 6) {
                        auth.currentUser?.updatePassword(p1)?.addOnSuccessListener {
                            Toast.makeText(context, context.getString(R.string.updated_successfully), Toast.LENGTH_SHORT).show()
                            showPasswordDialog = false
                        }
                    }
                }) { Text(stringResource(R.string.update)) }
            }
        )
    }

    if (showTemplateDialog) {
        var template by remember { mutableStateOf(user?.whatsappReminderTemplate ?: "") }
        val defaultTemplate = stringResource(R.string.default_whatsapp_template)
        
        AlertDialog(
            onDismissRequest = { showTemplateDialog = false },
            title = { Text(stringResource(R.string.edit_reminder_template)) },
            text = {
                Column {
                    Text(stringResource(R.string.template_hint), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = template.ifEmpty { defaultTemplate },
                        onValueChange = { template = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4
                    )
                    if (template.isEmpty()) {
                        Text(
                            text = stringResource(R.string.trial_period), // Reuse a label or add a new one like "Using default"
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    auth.currentUser?.uid?.let { db.collection("users").document(it).update("whatsappReminderTemplate", template) }
                    showTemplateDialog = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showTemplateDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.choose_language)) },
            text = {
                Column {
                    LanguageOption(
                        title = stringResource(R.string.arabic),
                        selected = !AppCompatDelegate.getApplicationLocales().toLanguageTags().contains("en"),
                        onClick = {
                            val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags("ar")
                            AppCompatDelegate.setApplicationLocales(appLocale)
                            showLanguageDialog = false
                        }
                    )
                    LanguageOption(
                        title = stringResource(R.string.english),
                        selected = AppCompatDelegate.getApplicationLocales().toLanguageTags().contains("en"),
                        onClick = {
                            val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags("en")
                            AppCompatDelegate.setApplicationLocales(appLocale)
                            showLanguageDialog = false
                        }
                    )
                }
            },
            confirmButton = {}
        )
    }
}

@Composable
fun LanguageOption(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun ProfileHeader(user: User?, email: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(modifier = Modifier.size(60.dp), shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.primary) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = (user?.name?.take(1) ?: "U").uppercase(), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = user?.name ?: stringResource(R.string.default_user_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(text = email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun fetchDataAndExport(db: FirebaseFirestore, userId: String, onReady: (List<Customer>, List<Transaction>) -> Unit) {
    db.collection("customers").whereEqualTo("userId", userId).get().addOnSuccessListener { customerDocs ->
        val customers = customerDocs.toObjects(Customer::class.java)
        val ids = customers.map { it.customerId }
        if (ids.isEmpty()) { onReady(emptyList(), emptyList()); return@addOnSuccessListener }
        db.collection("transactions").get().addOnSuccessListener { transDocs ->
            val list = transDocs.toObjects(Transaction::class.java).filter { it.customerId in ids }
            onReady(customers, list)
        }
    }
}

@Composable
fun SettingsSectionTitle(title: String) {
    Text(text = title, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
}

@Composable
fun SettingsItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
