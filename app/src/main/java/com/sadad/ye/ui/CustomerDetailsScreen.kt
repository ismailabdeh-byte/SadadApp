package com.sadad.ye.ui

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.sadad.ye.models.Customer
import com.sadad.ye.models.Transaction
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.util.*
import java.text.SimpleDateFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailsScreen(customer: Customer, onBack: () -> Unit, currency: String = "ريال") {
    var transactions by remember { mutableStateOf<List<Transaction>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    val showAddDialog = remember { mutableStateOf(false) }
    val showEditDialog = remember { mutableStateOf<Transaction?>(null) }
    val showSendConfirmDialog = remember { mutableStateOf<Transaction?>(null) }
    val showDeleteTransactionConfirm = remember { mutableStateOf<Transaction?>(null) }
    val showMenu = remember { mutableStateOf(false) }
    val showDeleteConfirm = remember { mutableStateOf(false) }
    val showReportOptions = remember { mutableStateOf(false) }
    val showDateRangePicker = remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    // متغير لتتبع العملية التي يجب التمرير إليها
    var targetScrollId by remember { mutableStateOf<String?>(null) }
    
    // مراقبة حالة القائمة والتمرير للهدف
    LaunchedEffect(transactions, targetScrollId) {
        if (targetScrollId != null) {
            val index = transactions.indexOfFirst { it.transactionId == targetScrollId }
            if (index != -1) {
                listState.animateScrollToItem(index)
                targetScrollId = null
            }
        }
    }

    // مراقبة حالة الاتصال بالإنترنت
    var isOffline by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val db = FirebaseFirestore.getInstance()
        db.addSnapshotsInSyncListener {
            // يتم استدعاء هذا عند اكتمال مزامنة جميع البيانات المحلية مع السيرفر
        }
    }
    
    val totalDebt = transactions.filter { it.debt }.sumOf { it.amount }
    val totalPaid = transactions.filter { !it.debt }.sumOf { it.amount }
    val balance = totalDebt - totalPaid

    LaunchedEffect(customer.customerId) {
        val db = FirebaseFirestore.getInstance()
        db.collection("transactions")
            .whereEqualTo("customerId", customer.customerId)
            .addSnapshotListener { value, _ ->
                if (value != null) {
                    val list = value.toObjects(Transaction::class.java)
                    transactions = list.sortedByDescending { it.date }
                }
                isLoading = false
            }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { 
                        Column {
                            Text(customer.name)
                            Text(
                                text = "جوال: ${customer.phoneNumber}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                        }
                    },
                    actions = {
                        IconButton(onClick = { makeCall(context, customer.phoneNumber) }) {
                            Icon(Icons.Default.Call, contentDescription = "اتصال", tint = Color(0xFF388E3C))
                        }
                        IconButton(onClick = { showReportOptions.value = true }) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "مشاركة التقرير عبر واتساب", tint = Color(0xFF25D366))
                        }
                        Box {
                            IconButton(onClick = { showMenu.value = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "المزيد")
                            }
                            DropdownMenu(
                                expanded = showMenu.value,
                                onDismissRequest = { showMenu.value = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("تصفية السجلات (رصيد سابق)") },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                    onClick = {
                                        showMenu.value = false
                                        showDeleteConfirm.value = true
                                    }
                                )
                            }
                        }
                    }
                )
            },
            floatingActionButton = {}
        ) { paddingValues ->
            Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (balance > 0) Color(0xFFFFF3F3) else Color(0xFFF1F8E9)
                    )
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp), 
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("الرصيد الحالي", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${formatAmount(kotlin.math.abs(balance))} $currency",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (balance > 0) Color(0xFFD32F2F) else Color(0xFF388E3C)
                        )
                        if (customer.debtLimit > 0) {
                            Text(
                                text = "سقف المديونية: ${formatAmount(customer.debtLimit)} $currency",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "سجل العمليات",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(
                        onClick = { showAddDialog.value = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("إضافة سجل")
                    }
                }

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    if (transactions.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("لا توجد عمليات مسجلة", color = Color.Gray)
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize()) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
                            ) {
                                // ترتيب العمليات من الأقدم للأحدث لمعرفة الترتيب الصحيح للإرسال
                                val sortedTransactions = transactions.sortedBy { it.date }
                                val firstUnsentId = sortedTransactions.find { !it.sent }?.transactionId

                                items(transactions, key = { it.transactionId }) { transaction ->
                                    TransactionItem(
                                        transaction = transaction,
                                        currency = currency,
                                        isNextToSent = transaction.transactionId == firstUnsentId,
                                        onEdit = { showEditDialog.value = it },
                                        onSend = { trans ->
                                            // حساب الرصيد التراكمي حتى هذه العملية فقط
                                            val index = sortedTransactions.indexOfFirst { it.transactionId == trans.transactionId }
                                            val transactionsUntilNow = if (index != -1) sortedTransactions.take(index + 1) else emptyList()
                                            val debtUntilNow = transactionsUntilNow.filter { it.debt }.sumOf { it.amount }
                                            val paidUntilNow = transactionsUntilNow.filter { !it.debt }.sumOf { it.amount }
                                            val balanceUntilNow = debtUntilNow - paidUntilNow
                                            
                                            sendSingleTransactionWhatsApp(context, customer, trans, balanceUntilNow, currency)
                                        },
                                        onDelete = { showDeleteTransactionConfirm.value = it },
                                        onMarkAsSent = { trans ->
                                            FirebaseFirestore.getInstance().collection("transactions")
                                                .document(trans.transactionId).update("sent", true)
                                        }
                                    )
                                }
                            }
                            
                            // أزرار التنقل الشفافة والموزعة
                            // زر الانتقال للأعلى (الأحدث) - تحت زر الإضافة
                            SmallFloatingActionButton(
                                onClick = {
                                    coroutineScope.launch {
                                        listState.animateScrollToItem(0)
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 0.dp, end = 8.dp),
                                containerColor = Color.LightGray.copy(alpha = 0.3f),
                                contentColor = Color.DarkGray,
                                elevation = FloatingActionButtonDefaults.elevation(0.dp)
                            ) {
                                Icon(Icons.Default.KeyboardDoubleArrowUp, contentDescription = "الأحدث")
                            }
                            
                            // زر الانتقال للأسفل (الأقدم) - أسفل الصفحة
                            SmallFloatingActionButton(
                                onClick = {
                                    coroutineScope.launch {
                                        if (transactions.isNotEmpty()) {
                                            listState.animateScrollToItem(transactions.size - 1)
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(bottom = 16.dp, end = 8.dp),
                                containerColor = Color.LightGray.copy(alpha = 0.3f),
                                contentColor = Color.DarkGray,
                                elevation = FloatingActionButtonDefaults.elevation(0.dp)
                            ) {
                                Icon(Icons.Default.KeyboardDoubleArrowDown, contentDescription = "الأقدم")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog.value) {
        AddTransactionDialog(
            customerId = customer.customerId,
            currentBalance = balance,
            debtLimit = customer.debtLimit,
            currency = currency,
            onDismiss = { showAddDialog.value = false },
            onConfirm = { transaction ->
                showAddDialog.value = false
                
                // تحديد الهدف للتمرير التلقائي
                targetScrollId = transaction.transactionId

                // لا يظهر تنبيه الإرسال إلا إذا كانت هذه العملية هي التالية في الترتيب
                val hasUnsentBefore = transactions.any { !it.sent && it.date < transaction.date }
                if (!hasUnsentBefore) {
                    showSendConfirmDialog.value = transaction
                }
            }
        )
    }

    showEditDialog.value?.let { transaction ->
        EditTransactionDialog(
            transaction = transaction,
            onConfirm = { id -> targetScrollId = id },
            onDismiss = { showEditDialog.value = null }
        )
    }

    showSendConfirmDialog.value?.let { transaction ->
        AlertDialog(
            onDismissRequest = { showSendConfirmDialog.value = null },
            title = { Text("إرسال إشعار") },
            text = { Text("هل تريد إرسال تفاصيل هذه العملية للعميل عبر واتساب؟") },
            confirmButton = {
                TextButton(onClick = {
                    sendSingleTransactionWhatsApp(context, customer, transaction, balance, currency)
                    showSendConfirmDialog.value = null
                }) { Text("إرسال") }
            },
            dismissButton = {
                TextButton(onClick = { showSendConfirmDialog.value = null }) { Text("إلغاء") }
            }
        )
    }

    showDeleteTransactionConfirm.value?.let { transaction ->
        AlertDialog(
            onDismissRequest = { showDeleteTransactionConfirm.value = null },
            title = { Text("حذف العملية") },
            text = { Text("هل أنت متأكد من حذف هذه العملية؟") },
            confirmButton = {
                Button(
                    onClick = {
                        deleteTransaction(transaction.transactionId)
                        showDeleteTransactionConfirm.value = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("حذف") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteTransactionConfirm.value = null }) { Text("إلغاء") }
            }
        )
    }

    if (showDeleteConfirm.value) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm.value = false },
            title = { Text("تصفية السجلات") },
            text = { Text("سيتم حذف جميع السجلات واستبدالها برصيد سابق. هل أنت متأكد؟") },
            confirmButton = {
                Button(
                    onClick = {
                        clearHistoryAndSetBalance(customer.customerId, balance) { success ->
                            if (success) Toast.makeText(context, "تمت التصفية بنجاح", Toast.LENGTH_SHORT).show()
                        }
                        showDeleteConfirm.value = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("تأكيد التصفية") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm.value = false }) { Text("إلغاء") }
            }
        )
    }

    if (showReportOptions.value) {
        ReportOptionsDialog(
            customer = customer,
            transactions = transactions,
            currentBalance = balance,
            currency = currency,
            onCustomRangeClick = { 
                showReportOptions.value = false
                showDateRangePicker.value = true 
            },
            onDismiss = { showReportOptions.value = false }
        )
    }

    if (showDateRangePicker.value) {
        DateRangeReportDialog(
            customer = customer,
            transactions = transactions,
            currency = currency,
            onDismiss = { showDateRangePicker.value = false }
        )
    }
}

@Composable
fun TransactionItem(
    transaction: Transaction, 
    currency: String,
    isNextToSent: Boolean,
    onEdit: (Transaction) -> Unit, 
    onSend: (Transaction) -> Unit,
    onDelete: (Transaction) -> Unit,
    onMarkAsSent: (Transaction) -> Unit
) {
    val dateFormat = SimpleDateFormat("yyyy/MM/dd - hh:mm a", Locale.forLanguageTag("ar"))
    
    // مراقبة حالة المزامنة لكل عملية بشكل فردي
    var isPendingSync by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    LaunchedEffect(transaction.transactionId) {
        FirebaseFirestore.getInstance().collection("transactions").document(transaction.transactionId)
            .addSnapshotListener { snapshot, _ ->
                isPendingSync = snapshot?.metadata?.hasPendingWrites() ?: false
            }
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (transaction.debt) "دين" else "سداد",
                            fontWeight = FontWeight.Bold,
                            color = if (transaction.debt) Color(0xFFD32F2F) else Color(0xFF388E3C)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        // مؤشر المزامنة (Offline/Sync)
                        if (isPendingSync) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                        } else {
                            Icon(Icons.Default.CloudDone, contentDescription = null, tint = Color(0xFF2196F3), modifier = Modifier.size(14.dp))
                        }

                        if (transaction.sent) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.CheckCircle, contentDescription = "تم الإرسال", tint = Color(0xFF25D366), modifier = Modifier.size(14.dp))
                        }
                    }
                    Text(text = dateFormat.format(Date(transaction.date)), fontSize = 11.sp, color = Color.Gray)
                    if (transaction.note.isNotEmpty()) {
                        Text(text = transaction.note, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text(text = "${formatAmount(transaction.amount)} $currency", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp, color = Color.LightGray)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                // زر الإرسال يظهر فقط إذا كانت هذه هي العملية التالية في الطابور ولم يتم إرسالها بعد
                if (!transaction.sent && isNextToSent) {
                    IconButton(onClick = { onSend(transaction) }) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "إرسال لواتساب", tint = Color(0xFF25D366))
                    }
                } else if (!transaction.sent) {
                    // أيقونة قفل بسيطة توضح أن هذه العملية تنتظر ما قبلها
                    Icon(Icons.Default.LockClock, contentDescription = "بانتظار إرسال العمليات السابقة", tint = Color.LightGray, modifier = Modifier.padding(12.dp).size(20.dp))
                }

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "المزيد", tint = Color.Gray)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        if (!transaction.sent) {
                            DropdownMenuItem(
                                text = { Text("تحديد كمرسل (بدون إرسال)") },
                                leadingIcon = { Icon(Icons.Default.DoneAll, contentDescription = null) },
                                onClick = { onMarkAsSent(transaction); showMenu = false }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("تعديل") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = { onEdit(transaction); showMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("حذف") },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) },
                            onClick = { onDelete(transaction); showMenu = false }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AddTransactionDialog(customerId: String, currentBalance: Double, debtLimit: Double, currency: String, onDismiss: () -> Unit, onConfirm: (Transaction) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf<Boolean?>(null) }
    var selectedDate by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.forLanguageTag("ar"))
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة عملية جديدة") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selectedType == true, onClick = { selectedType = true })
                    Text("دين")
                    Spacer(modifier = Modifier.width(16.dp))
                    RadioButton(selected = selectedType == false, onClick = { selectedType = false })
                    Text("سداد")
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { if (it.all { char -> char.isDigit() || char == '.' }) amount = it },
                    label = { Text("المبلغ") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = dateFormat.format(Date(selectedDate)),
                    onValueChange = { },
                    label = { Text("التاريخ") },
                    modifier = Modifier.fillMaxWidth().clickable { showDatePicker(context) { date -> selectedDate = date } },
                    enabled = false, readOnly = true,
                    trailingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("ملاحظة") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val amt = amount.toDoubleOrNull() ?: 0.0
                if (selectedType == null || amt <= 0) {
                    Toast.makeText(context, "بيانات غير صحيحة", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                if (selectedType == true && debtLimit > 0 && currentBalance + amt > debtLimit) {
                    Toast.makeText(context, "تجاوز سقف المديونية!", Toast.LENGTH_LONG).show()
                    return@Button
                }
                val db = FirebaseFirestore.getInstance()
                val id = UUID.randomUUID().toString()
                val userId = auth.currentUser?.uid ?: ""
                
                val trans = Transaction(
                    transactionId = id, 
                    customerId = customerId, 
                    userId = userId, // حفظ الـ userId لضمان ظهور العملية في التقارير
                    amount = amt, 
                    note = note, 
                    debt = selectedType!!, 
                    date = selectedDate
                )
                db.collection("transactions").document(id).set(trans)
                onConfirm(trans)
            }) { Text("إضافة") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
fun EditTransactionDialog(transaction: Transaction, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var amount by remember { mutableStateOf(transaction.amount.toString()) }
    var note by remember { mutableStateOf(transaction.note) }
    var isDebt by remember { mutableStateOf(transaction.debt) }
    var selectedDate by remember { mutableLongStateOf(transaction.date) }
    val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.forLanguageTag("ar"))
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعديل العملية") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = isDebt, onClick = { isDebt = true })
                    Text("دين")
                    Spacer(modifier = Modifier.width(16.dp))
                    RadioButton(selected = !isDebt, onClick = { isDebt = false })
                    Text("سداد")
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { if (it.all { char -> char.isDigit() || char == '.' }) amount = it },
                    label = { Text("المبلغ") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = dateFormat.format(Date(selectedDate)),
                    onValueChange = { },
                    label = { Text("التاريخ") },
                    modifier = Modifier.fillMaxWidth().clickable { showDatePicker(context) { date -> selectedDate = date } },
                    enabled = false, readOnly = true,
                    trailingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("ملاحظة") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val amt = amount.toDoubleOrNull() ?: 0.0
                if (amt > 0) {
                    FirebaseFirestore.getInstance().collection("transactions").document(transaction.transactionId)
                        .update(mapOf("amount" to amt, "note" to note, "debt" to isDebt, "date" to selectedDate))
                    onDismiss()
                }
            }) { Text("تعديل") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
fun ReportOptionsDialog(
    customer: Customer, 
    transactions: List<Transaction>, 
    currentBalance: Double, 
    currency: String, 
    onCustomRangeClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("مشاركة تقرير") },
        text = {
            Column {
                ListItem(
                    headlineContent = { Text("كشف حساب كامل") },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    modifier = Modifier.clickable { sendWhatsAppReport(context, customer, currentBalance, transactions, "كشف حساب كامل", currency = currency); onDismiss() }
                )
                ListItem(
                    headlineContent = { Text("كشف حساب (بين تاريخين)") },
                    leadingContent = { Icon(Icons.Default.DateRange, contentDescription = null) },
                    modifier = Modifier.clickable { onCustomRangeClick() }
                )
                ListItem(
                    headlineContent = { Text("تقرير مديونية فقط") },
                    leadingContent = { Icon(Icons.Default.AccountBox, contentDescription = null) },
                    modifier = Modifier.clickable { sendWhatsAppReport(context, customer, currentBalance, emptyList(), "إشعار رصيد مديونية", currency = currency); onDismiss() }
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } }
    )
}

@Composable
fun DateRangeReportDialog(
    customer: Customer,
    transactions: List<Transaction>,
    currency: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var startDate by remember { mutableLongStateOf(System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000) } // افتراضياً آخر شهر
    var endDate by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.forLanguageTag("ar"))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تحديد فترة التقرير") },
        text = {
            Column {
                Text("من تاريخ:", style = MaterialTheme.typography.labelSmall)
                OutlinedTextField(
                    value = dateFormat.format(Date(startDate)),
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth().clickable { showDatePicker(context) { startDate = it } },
                    enabled = false, readOnly = true,
                    trailingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null) }
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("إلى تاريخ:", style = MaterialTheme.typography.labelSmall)
                OutlinedTextField(
                    value = dateFormat.format(Date(endDate)),
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth().clickable { showDatePicker(context) { endDate = it } },
                    enabled = false, readOnly = true,
                    trailingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null) }
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                // 1. حساب الرصيد السابق (كل ما هو قبل تاريخ البداية)
                val previousTransactions = transactions.filter { it.date < startDate }
                val prevDebt = previousTransactions.filter { it.debt }.sumOf { it.amount }
                val prevPaid = previousTransactions.filter { !it.debt }.sumOf { it.amount }
                val previousBalance = prevDebt - prevPaid

                // 2. تصفية عمليات الفترة
                val rangeTransactions = transactions.filter { it.date in startDate..endDate }
                
                sendWhatsAppReportWithRange(
                    context = context,
                    customer = customer,
                    startDate = startDate,
                    endDate = endDate,
                    previousBalance = previousBalance,
                    reportTransactions = rangeTransactions,
                    currency = currency
                )
                onDismiss()
            }) { Text("إنشاء التقرير") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

fun sendWhatsAppReportWithRange(
    context: Context,
    customer: Customer,
    startDate: Long,
    endDate: Long,
    previousBalance: Double,
    reportTransactions: List<Transaction>,
    currency: String
) {
    val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.forLanguageTag("ar"))
    val sb = StringBuilder("كشف حساب: ${customer.name}\n")
    sb.append("الفترة: من ${dateFormat.format(Date(startDate))} إلى ${dateFormat.format(Date(endDate))}\n")
    sb.append("-----------------\n")

    var runningBalance = previousBalance
    val prevStatus = when {
        previousBalance > 0 -> "(عليكم)"
        previousBalance < 0 -> "(لكم)"
        else -> ""
    }
    sb.append("رصيد سابق: ${formatAmount(kotlin.math.abs(previousBalance))} $currency $prevStatus\n")
    sb.append("-----------------\n")

    if (reportTransactions.isNotEmpty()) {
        val sorted = reportTransactions.sortedBy { it.date }
        sorted.forEach { trans ->
            val type = if (trans.debt) "دين (+)" else "سداد (-)"
            if (trans.debt) runningBalance += trans.amount else runningBalance -= trans.amount
            
            val balanceStatus = when {
                runningBalance > 0 -> "عليكم"
                runningBalance < 0 -> "لكم"
                else -> "صفر"
            }
            
            sb.append("- ${dateFormat.format(Date(trans.date))}\n")
            sb.append("  $type: ${formatAmount(trans.amount)} $currency\n")
            if (trans.note.isNotEmpty()) sb.append("  ملاحظة: ${trans.note}\n")
            sb.append("  الرصيد: ${formatAmount(kotlin.math.abs(runningBalance))} $currency ($balanceStatus)\n\n")
        }
        sb.append("-----------------\n")
    }

    val finalBalanceStatus = when {
        runningBalance > 0 -> "الذي عليكم"
        runningBalance < 0 -> "الذي لكم"
        else -> ""
    }
    
    if (runningBalance == 0.0) {
        sb.append("الرصيد النهائي: تم سداد كامل الرصيد")
    } else {
        sb.append("إجمالي الرصيد $finalBalanceStatus: ${formatAmount(kotlin.math.abs(runningBalance))} $currency")
    }

    val phoneNumber = customer.phoneNumber.filter { it.isDigit() }.let { if (it.length == 9) "967$it" else it }
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW).apply { data = "https://api.whatsapp.com/send?phone=$phoneNumber&text=${URLEncoder.encode(sb.toString(), "UTF-8")}".toUri() })
    } catch (_: Exception) { Toast.makeText(context, "فشل الإرسال", Toast.LENGTH_SHORT).show() }
}

fun sendSingleTransactionWhatsApp(context: Context, customer: Customer, transaction: Transaction, currentBalance: Double, currency: String) {
    // التحقق من وجود اتصال بالإنترنت
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork
    val capabilities = connectivityManager.getNetworkCapabilities(network)
    val isOnline = capabilities != null && (
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            )

    if (!isOnline) {
        Toast.makeText(context, "لا يوجد اتصال بالإنترنت لإرسال الإشعار", Toast.LENGTH_LONG).show()
        return
    }

    val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.forLanguageTag("ar"))
    val title = if (transaction.debt) "إشعار عملية دين" else "إشعار عملية سداد"
    
    val transactionText = if (transaction.debt) "المبلغ الذي عليكم" else "المبلغ الذي لكم"
    val balanceText = when {
        currentBalance > 0 -> "يصبح الرصيد الذي عليكم: ${formatAmount(currentBalance)} $currency"
        currentBalance < 0 -> "يصبح الرصيد الذي لكم: ${formatAmount(kotlin.math.abs(currentBalance))} $currency"
        else -> "الرصيد: 0 $currency"
    }
    
    val message = "$title\n" +
            "العميل: ${customer.name}\n" +
            "$transactionText: ${formatAmount(transaction.amount)} $currency\n" +
            "التاريخ: ${dateFormat.format(Date(transaction.date))}\n" +
            "$balanceText"

    val phoneNumber = customer.phoneNumber.filter { it.isDigit() }.let { if (it.length == 9) "967$it" else it }
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply { data = "https://api.whatsapp.com/send?phone=$phoneNumber&text=${URLEncoder.encode(message, "UTF-8")}".toUri() }
        context.startActivity(intent)
        FirebaseFirestore.getInstance().collection("transactions").document(transaction.transactionId).update("sent", true)
    } catch (_: Exception) { Toast.makeText(context, "فشل في فتح واتساب", Toast.LENGTH_SHORT).show() }
}

private fun updateTransactionSentStatus(transactionId: String) {
    val db = FirebaseFirestore.getInstance()
    db.collection("transactions").document(transactionId).update("sent", true)
}

private fun deleteTransaction(transactionId: String) {
    FirebaseFirestore.getInstance().collection("transactions").document(transactionId).delete()
}

private fun sendWhatsAppReport(context: Context, customer: Customer, currentBalance: Double, reportTransactions: List<Transaction>, title: String, currency: String) {
    val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.forLanguageTag("ar"))
    val sb = StringBuilder("$title\n")
    sb.append("العميل: ${customer.name}\n")
    sb.append("التاريخ: ${dateFormat.format(Date())}\n")
    sb.append("-----------------\n")

    if (reportTransactions.isNotEmpty()) {
        val sorted = reportTransactions.sortedBy { it.date }
        var runningBalance = 0.0

        // في هذا النظام، بما أنه كشف حساب "كامل"، الرصيد السابق في البداية يكون 0 
        // إلا إذا أردنا دعمه مستقبلاً لفترات محددة. حالياً سنبدأ بالعمليات مباشرة 
        // مع توضيح حالة الرصيد بعد كل عملية.
        
        sorted.forEach { trans ->
            val type = if (trans.debt) "دين (+)" else "سداد (-)"
            if (trans.debt) runningBalance += trans.amount else runningBalance -= trans.amount
            
            val balanceStatus = when {
                runningBalance > 0 -> "عليكم"
                runningBalance < 0 -> "لكم"
                else -> "صفر"
            }
            
            sb.append("- ${dateFormat.format(Date(trans.date))}\n")
            sb.append("  $type: ${formatAmount(trans.amount)} $currency\n")
            if (trans.note.isNotEmpty()) sb.append("  ملاحظة: ${trans.note}\n")
            sb.append("  الرصيد: ${formatAmount(kotlin.math.abs(runningBalance))} $currency ($balanceStatus)\n\n")
        }
        sb.append("-----------------\n")
    }

    val finalBalanceStatus = when {
        currentBalance > 0 -> "الذي عليكم"
        currentBalance < 0 -> "الذي لكم"
        else -> ""
    }
    
    if (currentBalance == 0.0) {
        sb.append("الرصيد النهائي: تم سداد كامل الرصيد")
    } else {
        sb.append("إجمالي الرصيد $finalBalanceStatus: ${formatAmount(kotlin.math.abs(currentBalance))} $currency")
    }

    val phoneNumber = customer.phoneNumber.filter { it.isDigit() }.let { if (it.length == 9) "967$it" else it }
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW).apply { data = "https://api.whatsapp.com/send?phone=$phoneNumber&text=${URLEncoder.encode(sb.toString(), "UTF-8")}".toUri() })
    } catch (_: Exception) { Toast.makeText(context, "فشل الإرسال", Toast.LENGTH_SHORT).show() }
}

private fun clearHistoryAndSetBalance(customerId: String, balance: Double, onResult: (Boolean) -> Unit) {
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val userId = auth.currentUser?.uid ?: ""

    db.collection("transactions").whereEqualTo("customerId", customerId).get().addOnSuccessListener { querySnapshot ->
        val batch = db.batch()
        querySnapshot.documents.forEach { batch.delete(it.reference) }
        if (balance != 0.0) {
            val id = UUID.randomUUID().toString()
            batch.set(db.collection("transactions").document(id), Transaction(
                transactionId = id, 
                customerId = customerId, 
                userId = userId, // إضافة الـ userId هنا أيضاً
                amount = kotlin.math.abs(balance), 
                note = "رصيد سابق", 
                debt = balance > 0, 
                date = System.currentTimeMillis()
            ))
        }
        batch.commit().addOnSuccessListener { onResult(true) }.addOnFailureListener { onResult(false) }
    }
}
