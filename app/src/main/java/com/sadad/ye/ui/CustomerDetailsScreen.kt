package com.sadad.ye.ui

import android.content.Context
import android.content.Intent
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
import com.google.firebase.firestore.FirebaseFirestore
import com.sadad.ye.models.Customer
import com.sadad.ye.models.Transaction
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
    
    val context = LocalContext.current
    
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
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showAddDialog.value = true },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "إضافة")
                }
            }
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

                Text(
                    text = "سجل العمليات",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                        items(transactions) { transaction ->
                            TransactionItem(
                                transaction = transaction,
                                currency = currency,
                                onEdit = { showEditDialog.value = it },
                                onSend = { showSendConfirmDialog.value = it },
                                onDelete = { showDeleteTransactionConfirm.value = it }
                            )
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
                showSendConfirmDialog.value = transaction
            }
        )
    }

    showEditDialog.value?.let { transaction ->
        EditTransactionDialog(
            transaction = transaction,
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
            onDismiss = { showReportOptions.value = false }
        )
    }
}

@Composable
fun TransactionItem(
    transaction: Transaction, 
    currency: String,
    onEdit: (Transaction) -> Unit, 
    onSend: (Transaction) -> Unit,
    onDelete: (Transaction) -> Unit
) {
    val dateFormat = SimpleDateFormat("yyyy/MM/dd - hh:mm a", Locale.forLanguageTag("ar"))
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onEdit(transaction) },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
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
                        if (transaction.sent) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF25D366), modifier = Modifier.size(14.dp))
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
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = { onSend(transaction) }) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = if (transaction.sent) Color.Gray else Color(0xFF25D366))
                }
                IconButton(onClick = { onDelete(transaction) }) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color.LightGray)
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
                val trans = Transaction(transactionId = id, customerId = customerId, amount = amt, note = note, debt = selectedType!!, date = selectedDate)
                db.collection("transactions").document(id).set(trans)
                onConfirm(trans)
            }) { Text("إضافة") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
fun EditTransactionDialog(transaction: Transaction, onDismiss: () -> Unit) {
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
fun ReportOptionsDialog(customer: Customer, transactions: List<Transaction>, currentBalance: Double, currency: String, onDismiss: () -> Unit) {
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

fun sendSingleTransactionWhatsApp(context: Context, customer: Customer, transaction: Transaction, currentBalance: Double, currency: String) {
    val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.forLanguageTag("ar"))
    val title = if (transaction.debt) "إشعار عملية دين" else "إشعار عملية سداد"
    val message = "$title\nالعميل: ${customer.name}\nالمبلغ: ${formatAmount(transaction.amount)} $currency\nالتاريخ: ${dateFormat.format(Date(transaction.date))}\nالرصيد المتبقي: ${formatAmount(currentBalance)} $currency"
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
    val report = StringBuilder("$title\nالعميل: ${customer.name}\nالتاريخ: ${dateFormat.format(Date())}\n-----------------\n")
    if (reportTransactions.isNotEmpty()) {
        reportTransactions.sortedBy { it.date }.forEach {
            val type = if (it.debt) "دين" else "سداد"
            report.append("$type: ${formatAmount(it.amount)} $currency - ${dateFormat.format(Date(it.date))}\n")
        }
        report.append("-----------------\n")
    }
    report.append("الرصيد النهائي: ${formatAmount(currentBalance)} $currency")
    val phoneNumber = customer.phoneNumber.filter { it.isDigit() }.let { if (it.length == 9) "967$it" else it }
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW).apply { data = "https://api.whatsapp.com/send?phone=$phoneNumber&text=${URLEncoder.encode(report.toString(), "UTF-8")}".toUri() })
    } catch (_: Exception) { Toast.makeText(context, "فشل الإرسال", Toast.LENGTH_SHORT).show() }
}

private fun clearHistoryAndSetBalance(customerId: String, balance: Double, onResult: (Boolean) -> Unit) {
    val db = FirebaseFirestore.getInstance()
    db.collection("transactions")
        .whereEqualTo("customerId", customerId)
        .get()
        .addOnSuccessListener { querySnapshot ->
            val batch = db.batch()
            for (doc in querySnapshot.documents) {
                batch.delete(doc.reference)
            }
            
            if (balance != 0.0) {
                val id = UUID.randomUUID().toString()
                val consolidatedTransaction = Transaction(
                    transactionId = id,
                    customerId = customerId,
                    amount = kotlin.math.abs(balance),
                    note = "رصيد سابق",
                    debt = balance > 0,
                    date = System.currentTimeMillis()
                )
                val newDocRef = db.collection("transactions").document(id)
                batch.set(newDocRef, consolidatedTransaction)
            }
            
            batch.commit()
                .addOnSuccessListener { onResult(true) }
                .addOnFailureListener { onResult(false) }
        }
        .addOnFailureListener { onResult(false) }
}
