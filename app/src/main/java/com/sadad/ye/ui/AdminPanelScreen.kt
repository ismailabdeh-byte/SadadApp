package com.sadad.ye.ui

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.sadad.ye.models.ActivationCode
import com.sadad.ye.models.AppSettings
import java.net.URLEncoder
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(onBack: () -> Unit) {
    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    
    // إحصائيات
    var totalUsers by remember { mutableIntStateOf(0) }
    var totalCodes by remember { mutableIntStateOf(0) }
    var usedCodes by remember { mutableIntStateOf(0) }

    // حالات إنشاء الأكواد
    var durationDays by remember { mutableStateOf("30") }
    var generatedCode by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var codesList by remember { mutableStateOf<List<ActivationCode>>(emptyList()) }
    var isLoadingList by remember { mutableStateOf(true) }

    // حالات تعديل الإعدادات
    var activationTitle by remember { mutableStateOf("") }
    var activationDescription by remember { mutableStateOf("") }
    var paymentMethods by remember { mutableStateOf("") }
    var contactInfo by remember { mutableStateOf("") }
    var trialDays by remember { mutableStateOf("1") }
    var isSavingSettings by remember { mutableStateOf(false) }
    
    // حالة إصلاح البيانات
    var isRepairing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        db.collection("users").count().get(AggregateSource.SERVER).addOnSuccessListener { snapshot ->
            totalUsers = snapshot.count.toInt()
        }
        
        db.collection("activation_codes").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                val list = snapshot.toObjects(ActivationCode::class.java)
                codesList = list.sortedByDescending { it.createdAt }.take(15)
                totalCodes = list.size
                usedCodes = list.count { it.isUsed }
            }
            isLoadingList = false
        }

        db.collection("config").document("app_settings").get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                val settings = doc.toObject(AppSettings::class.java)
                settings?.let {
                    activationTitle = it.activationTitle
                    activationDescription = it.activationDescription
                    paymentMethods = it.paymentMethods
                    contactInfo = it.contactInfo
                    trialDays = it.trialDays.toString()
                }
            }
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("لوحة التحكم العليا") },
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
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // قسم الإحصائيات
                Text("إحصائيات النظام", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatCard("المستخدمين", totalUsers.toString(), Icons.Default.Group, Color(0xFF2196F3), Modifier.weight(1f))
                    StatCard("الأكواد", "$usedCodes/$totalCodes", Icons.Default.Key, Color(0xFFFF9800), Modifier.weight(1f))
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                // قسم إصلاح البيانات
                Text("أدوات الصيانة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        isRepairing = true
                        repairTransactionData(db) { msg ->
                            isRepairing = false
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF673AB7)),
                    enabled = !isRepairing
                ) {
                    if (isRepairing) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    else {
                        Icon(Icons.Default.Build, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("إصلاح بيانات التقارير اليومية")
                    }
                }
                Text("استخدم هذا الزر لربط العمليات القديمة بحسابك لكي تظهر في التقارير.", style = MaterialTheme.typography.bodySmall, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))

                HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))

                Text("توليد كود اشتراك", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = durationDays,
                        onValueChange = { if (it.all { c -> c.isDigit() }) durationDays = it },
                        label = { Text("عدد الأيام") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val days = durationDays.toIntOrNull() ?: 0
                            if (days > 0) {
                                isGenerating = true
                                val newCode = (100000..999999).random().toString()
                                val actCode = ActivationCode(code = newCode, durationDays = days, isUsed = false, createdAt = Timestamp.now())
                                db.collection("activation_codes").document(newCode).set(actCode)
                                    .addOnSuccessListener {
                                        generatedCode = newCode
                                        isGenerating = false
                                        Toast.makeText(context, "تم التوليد بنجاح", Toast.LENGTH_SHORT).show()
                                    }
                            }
                        },
                        modifier = Modifier.height(56.dp),
                        enabled = !isGenerating
                    ) {
                        if (isGenerating) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                        else Text("توليد")
                    }
                }

                if (generatedCode.isNotEmpty()) {
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("كود جديد: $generatedCode", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            IconButton(onClick = { clipboardManager.setText(AnnotatedString(generatedCode)) }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null)
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))

                Text("إعدادات شاشة التفعيل والتجربة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(value = trialDays, onValueChange = { if (it.all { c -> c.isDigit() }) trialDays = it }, label = { Text("أيام الفترة التجريبية") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = activationTitle, onValueChange = { activationTitle = it }, label = { Text("عنوان التفعيل") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = activationDescription, onValueChange = { activationDescription = it }, label = { Text("وصف التفعيل") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = paymentMethods, onValueChange = { paymentMethods = it }, label = { Text("طرق الدفع") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = contactInfo, onValueChange = { contactInfo = it }, label = { Text("رقم الواتساب") }, modifier = Modifier.fillMaxWidth())
                
                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        isSavingSettings = true
                        val settings = AppSettings(activationTitle, activationDescription, paymentMethods, contactInfo, trialDays.toIntOrNull() ?: 1)
                        db.collection("config").document("app_settings").set(settings)
                            .addOnSuccessListener {
                                isSavingSettings = false
                                Toast.makeText(context, "تم الحفظ بنجاح", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener { e ->
                                isSavingSettings = false
                                Toast.makeText(context, "فشل الحفظ: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSavingSettings
                ) {
                    if (isSavingSettings) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                    else {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("حفظ وتحديث تطبيق المشتركين")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))

                Text("الأكواد الأخيرة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                
                if (isLoadingList) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    codesList.forEach { item ->
                        CodeItem(item, context)
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

private fun repairTransactionData(db: FirebaseFirestore, onComplete: (String) -> Unit) {
    db.collection("customers").get().addOnSuccessListener { customerDocs ->
        if (customerDocs.isEmpty) {
            onComplete("لا يوجد عملاء لإصلاح بياناتهم")
            return@addOnSuccessListener
        }

        var totalProcessed = 0
        var totalUpdated = 0
        val totalCustomers = customerDocs.size()

        customerDocs.forEach { customerDoc ->
            val userId = customerDoc.getString("userId") ?: ""
            val customerId = customerDoc.id

            if (userId.isNotEmpty()) {
                db.collection("transactions")
                    .whereEqualTo("customerId", customerId)
                    .get()
                    .addOnSuccessListener { transDocs ->
                        val batch = db.batch()
                        var batchCount = 0
                        transDocs.forEach { transDoc ->
                            if (!transDoc.contains("userId")) {
                                batch.update(transDoc.reference, "userId", userId)
                                batchCount++
                                totalUpdated++
                            }
                        }
                        if (batchCount > 0) batch.commit()
                        totalProcessed++
                        if (totalProcessed == totalCustomers) onComplete("تم تحديث $totalUpdated عملية قديمة بنجاح.")
                    }
                    .addOnFailureListener {
                        totalProcessed++
                        if (totalProcessed == totalCustomers) onComplete("اكتمل الفحص مع وجود بعض الأخطاء.")
                    }
            } else {
                totalProcessed++
                if (totalProcessed == totalCustomers) onComplete("اكتمل الفحص.")
            }
        }
    }.addOnFailureListener { onComplete("فشل الاتصال") }
}

@Composable
fun StatCard(title: String, value: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = color)
            Text(text = value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color)
            Text(text = title, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

@Composable
fun CodeItem(item: ActivationCode, context: android.content.Context) {
    Card(modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.code, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("${item.durationDays} يوم - ${if(item.isUsed) "مستخدم" else "جاهز"}", fontSize = 12.sp, color = if(item.isUsed) Color.Red else Color(0xFF388E3C))
            }
            if (!item.isUsed) {
                IconButton(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("https://api.whatsapp.com/send?text=${URLEncoder.encode("كود تفعيل سداد الخاص بك: ${item.code}\nالمدة: ${item.durationDays} يوم", "UTF-8")}")
                    }
                    context.startActivity(intent)
                }) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color(0xFF25D366))
                }
            }
        }
    }
}
