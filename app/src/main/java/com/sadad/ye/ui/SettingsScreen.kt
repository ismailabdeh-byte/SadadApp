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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.sadad.ye.models.Customer
import com.sadad.ye.models.Transaction
import com.sadad.ye.models.User
import com.sadad.ye.utils.AccountUtils
import com.sadad.ye.utils.ExportUtils
import com.sadad.ye.utils.SubscriptionUtils
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    user: User?,
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
    
    var activationCode by remember { mutableStateOf("") }
    var isActivating by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var isDeletingAccount by remember { mutableStateOf(false) }

    val currencies = listOf("ريال يمني", "ريال سعودي", "درهم إماراتي", "دولار أمريكي", "جنية مصري")
    val userNameForReport = user?.name ?: "مستخدم سداد"

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("الإعدادات") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
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
                    SettingsSectionTitle(title = "الإدارة")
                    SettingsItem(
                        icon = Icons.Default.AdminPanelSettings,
                        title = "لوحة تحكم المدير",
                        subtitle = "إنشاء وإدارة أكواد التفعيل",
                        onClick = onAdminClick
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }

                SettingsSectionTitle(title = "الحساب والملف الشخصي")
                SettingsItem(
                    icon = Icons.Default.Store,
                    title = "اسم المحل / المؤسسة",
                    subtitle = user?.name ?: "لم يتم التحديد",
                    onClick = { showNameDialog = true }
                )
                SettingsItem(
                    icon = Icons.Default.LocationOn,
                    title = "عنوان العمل",
                    subtitle = user?.businessAddress ?: "غير محدد (يظهر في التقارير)",
                    onClick = { showAddressDialog = true }
                )
                SettingsItem(
                    icon = Icons.Default.Lock,
                    title = "كلمة المرور",
                    subtitle = "تغيير كلمة المرور الخاصة بك",
                    onClick = { showPasswordDialog = true }
                )
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                SettingsSectionTitle(title = "إعدادات التطبيق")
                SettingsItem(
                    icon = Icons.Default.MonetizationOn,
                    title = "العملة الافتراضية",
                    subtitle = user?.defaultCurrency ?: "ريال يمني",
                    onClick = { showCurrencyDialog = true }
                )
                
                var appLockEnabled by remember { mutableStateOf(user?.isAppLockEnabled ?: false) }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("قفل التطبيق", style = MaterialTheme.typography.titleMedium)
                        Text("طلب الرمز عند فتح التطبيق", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
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
                    SettingsItem(
                        icon = Icons.Default.Password,
                        title = "تغيير رمز الدخول (PIN)",
                        subtitle = "الرمز الحالي: ${user?.appLockPin}",
                        onClick = { showPinDialog = true }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                SettingsSectionTitle(title = "الاشتراك")
                val expiryDate = user?.subscriptionExpiry?.toDate()
                val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
                SettingsItem(
                    icon = Icons.Default.Star,
                    title = "حالة الاشتراك",
                    subtitle = if (expiryDate != null) "ينتهي في: ${dateFormat.format(expiryDate)}" else "فترة تجريبية (يوم واحد)",
                    onClick = {}
                )
                
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = activationCode,
                        onValueChange = { activationCode = it },
                        label = { Text("تفعيل كود جديد") },
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
                                        Toast.makeText(context, "الكود قصير جداً", Toast.LENGTH_SHORT).show()
                                    }
                                }) {
                                    Icon(Icons.Default.Check, contentDescription = "تفعيل", tint = Color(0xFF388E3C))
                                }
                            }
                        }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                SettingsSectionTitle(title = "البيانات والتقارير")
                if (isExporting) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                }
                
                SettingsItem(
                    icon = Icons.Default.PictureAsPdf,
                    title = "تصدير تقرير PDF",
                    subtitle = "مشاركة كشف حساب شامل للعملاء",
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
                    title = "تصدير ملف Excel",
                    subtitle = "حفظ البيانات بصيغة CSV المتوافقة مع الإكسل",
                    onClick = {
                        isExporting = true
                        fetchDataAndExport(db, auth.currentUser?.uid ?: "") { customers, transactions ->
                            ExportUtils.exportToExcel(context, customers, transactions, userNameForReport)
                            isExporting = false
                        }
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                SettingsSectionTitle(title = "الدعم والمساعدة")
                SettingsItem(
                    icon = Icons.Default.SupportAgent,
                    title = "تواصل مع الدعم الفني",
                    subtitle = "مساعدة عبر واتساب",
                    onClick = { 
                        try {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse("https://api.whatsapp.com/send?phone=967770000000&text=مرحباً، أحتاج مساعدة في تطبيق سداد")
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "فشل فتح واتساب", Toast.LENGTH_SHORT).show()
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
                        Text("تسجيل الخروج")
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
                            Text("حذف الحساب نهائياً", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // الحوارات
    if (showNameDialog) {
        var newName by remember { mutableStateOf(user?.name ?: "") }
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text("تعديل اسم المحل") },
            text = { OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("الاسم") }) },
            confirmButton = {
                TextButton(onClick = {
                    auth.currentUser?.uid?.let { db.collection("users").document(it).update("name", newName) }
                    showNameDialog = false
                }) { Text("حفظ") }
            }
        )
    }

    if (showAddressDialog) {
        var newAddress by remember { mutableStateOf(user?.businessAddress ?: "") }
        AlertDialog(
            onDismissRequest = { showAddressDialog = false },
            title = { Text("تعديل عنوان العمل") },
            text = { OutlinedTextField(value = newAddress, onValueChange = { newAddress = it }, label = { Text("العنوان") }) },
            confirmButton = {
                TextButton(onClick = {
                    auth.currentUser?.uid?.let { db.collection("users").document(it).update("businessAddress", newAddress) }
                    showAddressDialog = false
                }) { Text("حفظ") }
            }
        )
    }

    if (showPinDialog) {
        var newPin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("تغيير رمز PIN") },
            text = {
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) newPin = it },
                    label = { Text("رمز جديد (4 أرقام)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newPin.length == 4) {
                        auth.currentUser?.uid?.let { db.collection("users").document(it).update("appLockPin", newPin) }
                        showPinDialog = false
                    }
                }) { Text("حفظ") }
            }
        )
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = { Text("حذف الحساب نهائياً") },
            text = { Text("هل أنت متأكد؟ سيتم حذف جميع بياناتك وعملائك نهائياً.") },
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
                ) { Text("تأكيد الحذف") }
            },
            dismissButton = { TextButton(onClick = { showDeleteAccountDialog = false }) { Text("إلغاء") } }
        )
    }

    if (showCurrencyDialog) {
        AlertDialog(
            onDismissRequest = { showCurrencyDialog = false },
            title = { Text("اختر العملة") },
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
            title = { Text("تغيير كلمة المرور") },
            text = { OutlinedTextField(value = p1, onValueChange = { p1 = it }, label = { Text("كلمة المرور الجديدة") }, visualTransformation = PasswordVisualTransformation()) },
            confirmButton = {
                TextButton(onClick = {
                    if (p1.length >= 6) {
                        auth.currentUser?.updatePassword(p1)?.addOnSuccessListener {
                            Toast.makeText(context, "تم التحديث", Toast.LENGTH_SHORT).show()
                            showPasswordDialog = false
                        }
                    }
                }) { Text("تحديث") }
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
                    Text(text = (user?.name?.take(1) ?: "U").uppercase(), style = MaterialTheme.typography.headlineMedium, color = Color.White)
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = user?.name ?: "مستخدم سداد", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(text = email, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
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
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}
