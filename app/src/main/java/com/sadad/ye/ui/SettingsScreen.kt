package com.sadad.ye.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.sadad.ye.R
import com.sadad.ye.data.DataRepository
import com.sadad.ye.models.AppSettings
import com.sadad.ye.models.Customer
import com.sadad.ye.models.Transaction
import com.sadad.ye.models.User
import com.sadad.ye.utils.AccountUtils
import com.sadad.ye.utils.BackupUtils
import com.sadad.ye.utils.ExportUtils
import com.sadad.ye.utils.SubscriptionUtils
import com.sadad.ye.utils.SyncUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    user: User?,
    appSettings: AppSettings,
    repository: DataRepository,
    onBack: () -> Unit,
    onAdminClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }
    var showNameEnDialog by remember { mutableStateOf(false) }
    var showPhoneDialog by remember { mutableStateOf(false) }
    var showAddressDialog by remember { mutableStateOf(false) }
    var showAddressEnDialog by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showCurrencyDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showTemplateDialog by remember { mutableStateOf(false) }
    
    var activationCode by remember { mutableStateOf("") }
    var isActivating by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var isDeletingAccount by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }
    
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            isExporting = true
            scope.launch {
                val customers = repository.getCustomers(auth.currentUser?.uid ?: "").first()
                val transactions = repository.getTransactions(auth.currentUser?.uid ?: "").first()
                val json = BackupUtils.generateBackupJson(user, customers, transactions)
                try {
                    context.contentResolver.openOutputStream(it)?.use { output ->
                        output.write(json.toByteArray())
                    }
                    Toast.makeText(context, context.getString(R.string.save_success), Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, context.getString(R.string.backup_export_error, e.message), Toast.LENGTH_LONG).show()
                }
                isExporting = false
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            isImporting = true
            BackupUtils.importBackup(context, it) { success, msg ->
                isImporting = false
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                if (success) {
                    scope.launch {
                        repository.syncWithCloud(forceDownload = true)
                    }
                }
            }
        }
    }

    val logoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {}
            
            user?.let { u ->
                scope.launch {
                    repository.saveUserLocally(u.copy(logoUri = it.toString(), isSynced = false))
                }
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
                icon = Icons.Default.Image,
                title = "شعار المتجر (Logo)",
                subtitle = if (user?.logoUri?.isNotEmpty() == true) "تم اختيار شعار" else "اضغط لاختيار شعار للتقارير",
                onClick = { logoLauncher.launch("image/*") }
            )

            SettingsItem(
                icon = Icons.Default.Store,
                title = stringResource(R.string.store_name) + " (عربي)",
                subtitle = user?.name ?: stringResource(R.string.default_user_name),
                onClick = { showNameDialog = true }
            )

            SettingsItem(
                icon = Icons.Default.Store,
                title = stringResource(R.string.store_name) + " (English)",
                subtitle = user?.nameEn ?: "Set store name in English",
                onClick = { showNameEnDialog = true }
            )

            SettingsItem(
                icon = Icons.Default.Phone,
                title = stringResource(R.string.phone_number),
                subtitle = user?.phoneNumber ?: "أضف رقم هاتف المتجر",
                onClick = { showPhoneDialog = true }
            )

            SettingsItem(
                icon = Icons.Default.LocationOn,
                title = stringResource(R.string.business_address) + " (عربي)",
                subtitle = user?.businessAddress ?: stringResource(R.string.business_address),
                onClick = { showAddressDialog = true }
            )

            SettingsItem(
                icon = Icons.Default.LocationOn,
                title = stringResource(R.string.business_address) + " (English)",
                subtitle = user?.businessAddressEn ?: "Set business address in English",
                onClick = { showAddressEnDialog = true }
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
                icon = Icons.Default.MonetizationOn,
                title = stringResource(R.string.default_currency),
                subtitle = user?.defaultCurrency ?: stringResource(R.string.currency_yer),
                onClick = { showCurrencyDialog = true }
            )

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
                    checked = user?.showVoiceInstructions ?: true,
                    onCheckedChange = { isChecked ->
                        user?.let { 
                            scope.launch {
                                repository.saveUserLocally(it.copy(showVoiceInstructions = isChecked, isSynced = false))
                            }
                        }
                    }
                )
            }
            
            val appLockEnabled = user?.isAppLockEnabled ?: false
            val biometricEnabled = user?.isBiometricEnabled ?: false

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
                    onCheckedChange = { isChecked ->
                        user?.let {
                            scope.launch {
                                repository.saveUserLocally(it.copy(isAppLockEnabled = isChecked, isSynced = false))
                            }
                        }
                    }
                )
            }

            if (appLockEnabled) {
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
                        onCheckedChange = { isChecked ->
                            user?.let {
                                scope.launch {
                                    repository.saveUserLocally(it.copy(isBiometricEnabled = isChecked, isSynced = false))
                                }
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
                    checked = user?.debtNotificationEnabled ?: true,
                    onCheckedChange = { isChecked ->
                        user?.let {
                            scope.launch {
                                repository.saveUserLocally(it.copy(debtNotificationEnabled = isChecked, isSynced = false))
                            }
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
            if (isExporting || isImporting || isSyncing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            }

            SettingsItem(
                icon = Icons.Default.CloudSync,
                title = "مزامنة سحابية الآن",
                subtitle = "رفع البيانات والتعديلات الجديدة فوراً إلى السحاب",
                onClick = {
                    isSyncing = true
                    scope.launch {
                        repository.syncWithCloud(forceDownload = false)
                        isSyncing = false
                        Toast.makeText(context, "تمت المزامنة بنجاح", Toast.LENGTH_SHORT).show()
                    }
                }
            )
            
            SettingsItem(
                icon = Icons.Default.CloudUpload,
                title = stringResource(R.string.create_backup),
                subtitle = stringResource(R.string.create_backup_subtitle),
                onClick = {
                    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    val prefix = context.getString(R.string.backup_file_prefix)
                    exportLauncher.launch("$prefix$timeStamp.json")
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
                    scope.launch {
                        val userId = auth.currentUser?.uid ?: ""
                        val customers = repository.getCustomers(userId).first()
                        val transactions = repository.getTransactions(userId).first()
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
                    scope.launch {
                        val userId = auth.currentUser?.uid ?: ""
                        val customers = repository.getCustomers(userId).first()
                        val transactions = repository.getTransactions(userId).first()
                        ExportUtils.exportToExcel(context, customers, transactions, userNameForReport)
                        isExporting = false
                    }
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SettingsSectionTitle(title = stringResource(R.string.support_help))
            val supportNumber = appSettings.supportPhone
            if (supportNumber.isNotEmpty()) {
                SettingsItem(
                    icon = Icons.Default.SupportAgent,
                    title = stringResource(R.string.contact_support),
                    subtitle = "واتساب: +$supportNumber",
                    onClick = { 
                        try {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse("https://api.whatsapp.com/send?phone=$supportNumber&text=${java.net.URLEncoder.encode(context.getString(R.string.whatsapp_support_message), "UTF-8")}")
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, context.getString(R.string.whatsapp_error), Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
            
            Column(modifier = Modifier.padding(16.dp)) {
                Button(
                    onClick = {
                        auth.signOut()
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
            title = { Text(stringResource(R.string.edit_store_name) + " (عربي)") },
            text = { OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text(stringResource(R.string.name)) }) },
            confirmButton = {
                TextButton(onClick = {
                    user?.let {
                        scope.launch {
                            repository.saveUserLocally(it.copy(name = newName, isSynced = false))
                        }
                    }
                    showNameDialog = false
                }) { Text(stringResource(R.string.save)) }
            }
        )
    }

    if (showNameEnDialog) {
        var newNameEn by remember { mutableStateOf(user?.nameEn ?: "") }
        AlertDialog(
            onDismissRequest = { showNameEnDialog = false },
            title = { Text("تعديل اسم المتجر (English)") },
            text = { OutlinedTextField(value = newNameEn, onValueChange = { newNameEn = it }, label = { Text("Store Name (EN)") }) },
            confirmButton = {
                TextButton(onClick = {
                    user?.let {
                        scope.launch {
                            repository.saveUserLocally(it.copy(nameEn = newNameEn, isSynced = false))
                        }
                    }
                    showNameEnDialog = false
                }) { Text(stringResource(R.string.save)) }
            }
        )
    }

    if (showPhoneDialog) {
        var newPhone by remember { mutableStateOf(user?.phoneNumber ?: "") }
        AlertDialog(
            onDismissRequest = { showPhoneDialog = false },
            title = { Text(stringResource(R.string.phone_number)) },
            text = { OutlinedTextField(value = newPhone, onValueChange = { newPhone = it }, label = { Text(stringResource(R.string.phone_number)) }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone)) },
            confirmButton = {
                TextButton(onClick = {
                    user?.let {
                        scope.launch {
                            repository.saveUserLocally(it.copy(phoneNumber = newPhone, isSynced = false))
                        }
                    }
                    showPhoneDialog = false
                }) { Text(stringResource(R.string.save)) }
            }
        )
    }

    if (showAddressDialog) {
        var newAddress by remember { mutableStateOf(user?.businessAddress ?: "") }
        AlertDialog(
            onDismissRequest = { showAddressDialog = false },
            title = { Text(stringResource(R.string.edit_business_address) + " (عربي)") },
            text = { OutlinedTextField(value = newAddress, onValueChange = { newAddress = it }, label = { Text(stringResource(R.string.address)) }) },
            confirmButton = {
                TextButton(onClick = {
                    user?.let {
                        scope.launch {
                            repository.saveUserLocally(it.copy(businessAddress = newAddress, isSynced = false))
                        }
                    }
                    showAddressDialog = false
                }) { Text(stringResource(R.string.save)) }
            }
        )
    }

    if (showAddressEnDialog) {
        var newAddressEn by remember { mutableStateOf(user?.businessAddressEn ?: "") }
        AlertDialog(
            onDismissRequest = { showAddressEnDialog = false },
            title = { Text("تعديل العنوان (English)") },
            text = { OutlinedTextField(value = newAddressEn, onValueChange = { newAddressEn = it }, label = { Text("Address (EN)") }) },
            confirmButton = {
                TextButton(onClick = {
                    user?.let {
                        scope.launch {
                            repository.saveUserLocally(it.copy(businessAddressEn = newAddressEn, isSynced = false))
                        }
                    }
                    showAddressEnDialog = false
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
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newPin.length == 4) {
                        user?.let {
                            scope.launch {
                                repository.saveUserLocally(it.copy(appLockPin = newPin, isSynced = false))
                            }
                        }
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
                                user?.let {
                                    scope.launch {
                                        repository.saveUserLocally(it.copy(defaultCurrency = curr, isSynced = false))
                                    }
                                }
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
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    user?.let {
                        scope.launch {
                            repository.saveUserLocally(it.copy(whatsappReminderTemplate = template, isSynced = false))
                        }
                    }
                    showTemplateDialog = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showTemplateDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
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
