package com.sadad.ye.ui

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.sadad.ye.models.Customer
import com.sadad.ye.models.Transaction
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerListScreen(
    onAddCustomerClick: () -> Unit,
    onEditCustomerClick: (Customer) -> Unit,
    onCustomerClick: (Customer) -> Unit,
    onDailyReportClick: () -> Unit,
    onSettingsClick: () -> Unit,
    currency: String = "ريال",
    showVoiceInstructions: Boolean = true,
    onDisableInstructions: (Boolean) -> Unit = {}
) {
    var customers by remember { mutableStateOf<List<Customer>>(emptyList()) }
    var transactions by remember { mutableStateOf<List<Transaction>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    val context = LocalContext.current
    
    val auth = FirebaseAuth.getInstance()
    val currentUserId = auth.currentUser?.uid ?: ""

    // حالة الأوامر الصوتية
    var voiceResult by remember { mutableStateOf<VoiceAction?>(null) }
    var showInstructionsDialog by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }

    // نستخدم rememberUpdatedState لضمان أن الميكروفون يستخدم دائماً أحدث البيانات
    val currentCustomers by rememberUpdatedState(customers)

    // إعداد SpeechRecognizer
    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
    val recognizerIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
    }

    val recognitionListener = remember {
        object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { isListening = true }
            override fun onBeginningOfSpeech() { isListening = true }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { isListening = false }
            override fun onError(error: Int) { isListening = false }
            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    voiceResult = voiceAnalysis(matches[0], currentCustomers)
                    showInstructionsDialog = false
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    DisposableEffect(Unit) {
        speechRecognizer.setRecognitionListener(recognitionListener)
        onDispose { speechRecognizer.destroy() }
    }

    LaunchedEffect(currentUserId) {
        if (currentUserId.isEmpty()) return@LaunchedEffect
        val db = FirebaseFirestore.getInstance()
        db.collection("customers").whereEqualTo("userId", currentUserId)
            .addSnapshotListener { value, _ -> if (value != null) customers = value.toObjects(Customer::class.java) }
        db.collection("transactions").addSnapshotListener { value, _ ->
            if (value != null) {
                val allTrans = value.toObjects(Transaction::class.java)
                val customerIds = customers.map { it.customerId }.toSet()
                transactions = allTrans.filter { it.customerId in customerIds }
            }
            isLoading = false
        }
    }

    val filteredCustomers = customers.filter { 
        searchQuery.isEmpty() || it.name.contains(searchQuery, ignoreCase = true) || it.phoneNumber.contains(searchQuery) 
    }.sortedByDescending { customer ->
        // البحث عن تاريخ أحدث عملية لهذا العميل
        transactions.filter { it.customerId == customer.customerId }.maxOfOrNull { it.date } ?: 0L
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("سداد - قائمة العملاء") },
                    actions = {
                        IconButton(onClick = onDailyReportClick) { Icon(Icons.AutoMirrored.Filled.List, "التقرير") }
                        IconButton(onClick = onSettingsClick) { Icon(Icons.Default.Settings, "الإعدادات") }
                    }
                )
            },
            floatingActionButton = {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // الزر الرئيسي يدعم الضغط المطول إذا كانت التعليمات معطلة
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(if (isListening) Color.Red else MaterialTheme.colorScheme.primary, CircleShape)
                            .pointerInput(showVoiceInstructions) { // نراقب حالة التعليمات هنا للتحديث الفوري
                                detectTapGestures(
                                    onTap = { 
                                        if (showVoiceInstructions) {
                                            showInstructionsDialog = true 
                                        } else {
                                            // فحص الإنترنت في حالة الضغط السريع
                                            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                                            val isOnline = connectivityManager.activeNetwork?.let {
                                                connectivityManager.getNetworkCapabilities(it)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                                            } ?: false
                                            
                                            if (!isOnline) {
                                                Toast.makeText(context, "الأوامر الصوتية تتطلب اتصالاً بالإنترنت", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "اضغط باستمرار للتحدث", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    onPress = {
                                        if (!showVoiceInstructions) {
                                            // فحص الإنترنت قبل البدء
                                            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                                            val isOnline = connectivityManager.activeNetwork?.let {
                                                connectivityManager.getNetworkCapabilities(it)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                                            } ?: false

                                            if (!isOnline) {
                                                Toast.makeText(context, "الأوامر الصوتية تتطلب اتصالاً بالإنترنت", Toast.LENGTH_SHORT).show()
                                                return@detectTapGestures
                                            }

                                            try {
                                                speechRecognizer.startListening(recognizerIntent)
                                                awaitRelease()
                                                speechRecognizer.stopListening()
                                            } catch (e: Exception) { isListening = false }
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Mic, "صوت", tint = Color.White)
                    }
                    
                    FloatingActionButton(onClick = onAddCustomerClick) { Icon(Icons.Default.Add, "إضافة") }
                }
            }
        ) { paddingValues ->
            Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
                val totalAllBalances = customers.sumOf { customer ->
                    val customerTransactions = transactions.filter { it.customerId == customer.customerId }
                    val balance = customerTransactions.filter { it.debt }.sumOf { it.amount } - customerTransactions.filter { !it.debt }.sumOf { it.amount }
                    if (balance > 0) balance else 0.0
                }

                Card(modifier = Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("إجمالي مديونيات العملاء", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
                            Text("${formatAmount(totalAllBalances)} $currency", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F))
                        }
                        Icon(Icons.Default.Info, null, tint = Color(0xFFD32F2F), modifier = Modifier.size(32.dp))
                    }
                }

                OutlinedTextField(
                    value = searchQuery, onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("بحث عن عميل...") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true
                )

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(filteredCustomers) { customer ->
                            val customerTransactions = transactions.filter { it.customerId == customer.customerId }
                            val balance = customerTransactions.filter { it.debt }.sumOf { it.amount } - customerTransactions.filter { !it.debt }.sumOf { it.amount }
                            CustomerItem(customer, balance, currency, { onCustomerClick(customer) }, { onEditCustomerClick(customer) }, { deleteCustomer(customer.customerId, context) })
                        }
                    }
                }
            }
        }
    }

    voiceResult?.let { action ->
        VoiceConfirmDialog(action, currency, { voiceResult = null }, { 
            // فحص سقف المديونية قبل الحفظ
            if (action.isDebt && action.customer.debtLimit > 0) {
                val customerTransactions = transactions.filter { it.customerId == action.customer.customerId }
                val currentBalance = customerTransactions.filter { it.debt }.sumOf { it.amount } - 
                                   customerTransactions.filter { !it.debt }.sumOf { it.amount }
                
                if (currentBalance + action.amount > action.customer.debtLimit) {
                    Toast.makeText(context, "عذراً، العميل تجاوز سقف المديونية المسموح به!", Toast.LENGTH_LONG).show()
                    return@VoiceConfirmDialog
                }
            }

            saveVoiceTransaction(action)
            voiceResult = null
            Toast.makeText(context, "تم حفظ العملية بنجاح", Toast.LENGTH_SHORT).show()
        })
    }

    if (showInstructionsDialog) {
        VoiceInstructionsDialog(
            isListening = isListening,
            onStartListen = { speechRecognizer.startListening(recognizerIntent) },
            onStopListen = { speechRecognizer.stopListening() },
            onDismiss = { showInstructionsDialog = false }
        )
    }
}

@Composable
fun VoiceInstructionsDialog(
    isListening: Boolean,
    onStartListen: () -> Unit,
    onStopListen: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعليمات التسجيل الصوتي") },
        text = {
            Column {
                Text("يمكنك تسجيل العمليات بسرعة باستخدام صوتك. يرجى التحدث بصيغة واضحة مثل:")
                Spacer(modifier = Modifier.height(12.dp))
                Card(colors = CardDefaults.cardColors(containerColor = Color.LightGray.copy(alpha = 0.1f))) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("\"سجل دين على محمد عمر بمبلغ خمسة آلاف مقابل كرتون عصير\"", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("\"سدد العميل محمد عمر مبلغ ألفين ريال عبر محفظة جيب\"", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("• استخدم كلمات (سجل على، قيد دين على، أرصد على) للديون.", style = MaterialTheme.typography.bodySmall)
                Text("• استخدم كلمات (سدد العميل، تم سداد من، دفع) للسداد.", style = MaterialTheme.typography.bodySmall)
                Text("• اذكر الملاحظة بعد كلمة (مقابل) أو (عن).", style = MaterialTheme.typography.bodySmall)
                
                Spacer(modifier = Modifier.height(16.dp))
                Text("للتسجيل: اضغط باستمرار على زر الميكروفون وتحدث، ثم ارفع إصبعك عند الانتهاء.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)

                // حجز مساحة ثابتة للنص لمنع اهتزاز الواجهة
                Box(modifier = Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
                    if (isListening) {
                        Text("جاري الاستماع الآن...", color = Color.Red, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "يمكنك إيقاف ظهور هذه التعليمات من شاشة الإعدادات",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        },
        confirmButton = {
            val context = LocalContext.current
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(if (isListening) Color.Red else MaterialTheme.colorScheme.primary, CircleShape)
                    .pointerInput(Unit) {
                        detectTapGestures(onPress = {
                            // فحص الإنترنت قبل البدء
                            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                            val isOnline = connectivityManager.activeNetwork?.let {
                                connectivityManager.getNetworkCapabilities(it)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                            } ?: false

                            if (!isOnline) {
                                Toast.makeText(context, "الأوامر الصوتية تتطلب اتصالاً بالإنترنت", Toast.LENGTH_SHORT).show()
                                return@detectTapGestures
                            }

                            try {
                                onStartListen()
                                awaitRelease()
                                onStopListen()
                            } catch (e: Exception) { onStopListen() }
                        })
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Mic, null, tint = Color.White)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Preview(showBackground = true)
@Composable
fun VoiceInstructionsDialogPreview() {
    MaterialTheme { Surface { VoiceInstructionsDialog(false, {}, {}, {}) } }
}

@Composable
fun CustomerItem(customer: Customer, balance: Double, currency: String, onClick: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("حذف العميل") },
            text = { Text("هل أنت متأكد من حذف العميل ${customer.name}؟") },
            confirmButton = { TextButton(onClick = { onDelete(); showDeleteDialog = false }) { Text("حذف", color = Color.Red) } },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("إلغاء") } }
        )
    }
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clickable { onClick() }, elevation = CardDefaults.cardElevation(2.dp)) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(customer.name, style = MaterialTheme.typography.titleLarge)
                Text("${formatAmount(balance)} $currency", style = MaterialTheme.typography.bodyMedium, color = if (balance > 0) Color(0xFFD32F2F) else Color(0xFF388E3C), fontWeight = FontWeight.Bold)
            }
            Row {
                IconButton(onClick = { makeCall(context, customer.phoneNumber) }) { Icon(Icons.Default.Call, "اتصال", tint = Color(0xFF388E3C)) }
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "تعديل", tint = MaterialTheme.colorScheme.primary) }
                IconButton(onClick = { showDeleteDialog = true }) { Icon(Icons.Default.Delete, "حذف", tint = Color.Red) }
            }
        }
    }
}

private fun deleteCustomer(customerId: String, context: Context) {
    FirebaseFirestore.getInstance().collection("customers").document(customerId).delete()
        .addOnSuccessListener { Toast.makeText(context, "تم الحذف", Toast.LENGTH_SHORT).show() }
}

data class VoiceAction(val customer: Customer, val amount: Double, val isDebt: Boolean, val note: String, val rawText: String)

@Composable
fun VoiceConfirmDialog(action: VoiceAction, currency: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تأكيد العملية الصوتية") },
        text = {
            Column {
                Text("لقد قلت: \"${action.rawText}\"", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Spacer(modifier = Modifier.height(16.dp))
                Text("العميل: ${action.customer.name}", fontWeight = FontWeight.Bold)
                Text("النوع: ${if (action.isDebt) "دين (+)" else "سداد (-)"}")
                Text("المبلغ: ${formatAmount(action.amount)} $currency", color = if (action.isDebt) Color.Red else Color(0xFF388E3C), fontWeight = FontWeight.Bold)
                if (action.note.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("الملاحظة: ${action.note}", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                }
            }
        },
        confirmButton = { Button(onClick = onConfirm) { Text("تأكيد وحفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

fun voiceAnalysis(text: String, customers: List<Customer>): VoiceAction? {
    if (text.isEmpty() || customers.isEmpty()) return null
    val cleanText = text.trim()
    val debtKeywords = listOf("سجل على", "قيد دين على", "أرصد على", "سجل", "دين", "اخذ", "عليه", "اكتب", "قيد", "ارفع", "مطلوب", "تسلف", "شل", "حساب", "فاتورة", "اديله", "اديلو", "بز")
    val paidKeywords = listOf("سدد العميل", "تم سداد من", "سدد", "دفع", "جاب", "استلمت", "حاسب", "له", "وصلني", "قبضت", "اعطاني", "نزل", "خصم", "وفاء", "تصفية", "رجع", "رد")
    val isDebt = debtKeywords.any { cleanText.contains(it) } || !paidKeywords.any { cleanText.contains(it) }
    val noteKeywords = listOf("بواسطة محفظة", "عبر محفظة", "شيك عبر بنك", "بشيك من", "حواله", "محفظة", "عبر", "مقابل", "عن", "بسبب", "حق", "بخصوص", "عشان", "عشان خاطر", "لجل", "وصف", "قيمة", "غرض")
    var note = ""
    var textForAmount = cleanText
    for (keyword in noteKeywords) {
        if (cleanText.contains(keyword)) {
            val parts = cleanText.split(keyword, limit = 2)
            if (parts.size > 1) {
                note = keyword + " " + parts[1].trim()
                textForAmount = parts[0]
                break
            }
        }
    }
    val amountRegex = "\\d+".toRegex()
    val matches = amountRegex.findAll(textForAmount).toList()
    var amount = if (matches.isNotEmpty()) matches[0].value.toDoubleOrNull() ?: 0.0 else 0.0
    if (amount == 0.0) {
        when {
            textForAmount.contains("مليون") -> amount = 1000000.0
            textForAmount.contains("ألفين") || textForAmount.contains("الفين") -> amount = 2000.0
            textForAmount.contains("ألف") || textForAmount.contains("الف") -> {
                amount = when {
                    textForAmount.contains("خمسة") || textForAmount.contains("خمس") -> 5000.0
                    textForAmount.contains("عشرة") || textForAmount.contains("عشر") -> 10000.0
                    textForAmount.contains("مية") || textForAmount.contains("مائة") -> 100000.0
                    else -> 1000.0
                }
            }
        }
        if (textForAmount.contains("ونص")) amount += (amount / 2).takeIf { amount > 0 } ?: 150.0
    }
    val foundCustomer = customers.map { customer ->
        val nameWords = customer.name.lowercase().split(" ").filter { it.length > 2 }
        var score = 0
        nameWords.forEach { word -> if (cleanText.contains(word, ignoreCase = true)) score += 10 }
        if (cleanText.contains(customer.name, ignoreCase = true)) score += 50
        customer to score
    }.filter { it.second > 0 }.maxByOrNull { it.second }?.first
    return if (foundCustomer != null && amount > 0) VoiceAction(foundCustomer, amount, isDebt, note, cleanText) else null
}

fun saveVoiceTransaction(action: VoiceAction) {
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val id = UUID.randomUUID().toString()
    val trans = Transaction(id, action.customer.customerId, auth.currentUser?.uid ?: "", action.amount, action.note.ifEmpty { "تم التسجيل صوتياً" }, System.currentTimeMillis(), action.isDebt)
    db.collection("transactions").document(id).set(trans)
}
