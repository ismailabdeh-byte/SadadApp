package com.sadad.ye.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.sadad.ye.R
import com.sadad.ye.data.DataRepository
import com.sadad.ye.models.Customer
import com.sadad.ye.models.Transaction
import com.sadad.ye.models.User
import com.sadad.ye.utils.NotificationUtils
import com.sadad.ye.utils.PdfReportGenerator
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.util.*
import java.text.SimpleDateFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailsScreen(customer: Customer, repository: DataRepository, onBack: () -> Unit, defaultAppCurrency: String, user: User? = null) {
    var transactions by remember { mutableStateOf<List<Transaction>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    val currency = if (customer.currency.isNotEmpty()) customer.currency else defaultAppCurrency
    
    val showAddDialog = remember { mutableStateOf(false) }
    val showEditDialog = remember { mutableStateOf<Transaction?>(null) }
    val showSendConfirmDialog = remember { mutableStateOf<Transaction?>(null) }
    val showDeleteTransactionConfirm = remember { mutableStateOf<Transaction?>(null) }
    val showMenu = remember { mutableStateOf(false) }
    val showDeleteConfirm = remember { mutableStateOf(false) }
    val showReportOptions = remember { mutableStateOf(false) }
    val showWhatsAppChoice = remember { mutableStateOf(false) }
    val showDateRangePicker = remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    var targetScrollId by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(transactions, targetScrollId) {
        if (targetScrollId != null) {
            val index = transactions.indexOfFirst { it.transactionId == targetScrollId }
            if (index != -1) {
                listState.animateScrollToItem(index)
                targetScrollId = null
            }
        }
    }

    val totalDebt = transactions.filter { it.debt }.sumOf { it.amount }
    val totalPaid = transactions.filter { !it.debt }.sumOf { it.amount }
    val balance = totalDebt - totalPaid

    LaunchedEffect(customer.customerId) {
        coroutineScope.launch {
            repository.getTransactions(customer.userId).collectLatest { allTrans ->
                transactions = allTrans.filter { it.customerId == customer.customerId }
                    .sortedByDescending { it.date }
                isLoading = false
            }
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides (if (Locale.getDefault().language == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { 
                        Column {
                            Text(customer.name)
                            Text(
                                text = stringResource(R.string.phone_label, customer.phoneNumber),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    actions = {
                        IconButton(onClick = { makeCall(context, customer.phoneNumber) }) {
                            Icon(Icons.Default.Call, contentDescription = null, tint = Color(0xFF388E3C))
                        }
                        IconButton(onClick = { showReportOptions.value = true }) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color(0xFF25D366))
                        }
                        Box {
                            IconButton(onClick = { showMenu.value = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = null)
                            }
                            DropdownMenu(
                                expanded = showMenu.value,
                                onDismissRequest = { showMenu.value = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.clear_history_title)) },
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
            }
        ) { paddingValues ->
            Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (balance > 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp), 
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(stringResource(R.string.current_balance_label), style = MaterialTheme.typography.titleMedium, color = if (balance > 0) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(
                            text = "${formatAmount(kotlin.math.abs(balance))} $currency",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (balance > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                        )
                        if (customer.debtLimit > 0) {
                            Text(
                                text = stringResource(R.string.debt_limit_label, formatAmount(customer.debtLimit), currency),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (balance > 0) MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
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
                        text = stringResource(R.string.transaction_history_label),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(
                        onClick = { showAddDialog.value = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.add_record))
                    }
                }

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    if (transactions.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.no_transactions), color = Color.Gray)
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize()) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
                            ) {
                                val sortedTransactions = transactions.sortedBy { it.date }
                                val lastUnsentId = transactions.filter { !it.sent }.maxByOrNull { it.date }?.transactionId

                                items(transactions, key = { it.transactionId }) { transaction ->
                                    TransactionItem(
                                        transaction = transaction,
                                        currency = currency,
                                        isNextToSent = transaction.transactionId == lastUnsentId,
                                        onEdit = { showEditDialog.value = it },
                                        onSend = { _ ->
                                            val unsentTransactions = transactions.filter { !it.sent }.sortedBy { it.date }
                                            if (unsentTransactions.isNotEmpty()) {
                                                sendBatchTransactionsWhatsApp(context, repository, customer, unsentTransactions, balance, currency)
                                            }
                                        },
                                        onDelete = { showDeleteTransactionConfirm.value = it },
                                        onMarkAsSent = { trans ->
                                            coroutineScope.launch {
                                                repository.addTransaction(trans.copy(sent = true, sentViaWhatsApp = false))
                                            }
                                        }
                                    )
                                }
                            }
                            
                            SmallFloatingActionButton(
                                onClick = { coroutineScope.launch { listState.animateScrollToItem(0) } },
                                modifier = Modifier.align(Alignment.TopEnd).padding(top = 0.dp, end = 8.dp),
                                containerColor = Color.LightGray.copy(alpha = 0.3f),
                                contentColor = Color.DarkGray,
                                elevation = FloatingActionButtonDefaults.elevation(0.dp)
                            ) {
                                Icon(Icons.Default.KeyboardDoubleArrowUp, contentDescription = stringResource(R.string.latest))
                            }
                            
                            SmallFloatingActionButton(
                                onClick = { coroutineScope.launch { if (transactions.isNotEmpty()) listState.animateScrollToItem(transactions.size - 1) } },
                                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 16.dp, end = 8.dp),
                                containerColor = Color.LightGray.copy(alpha = 0.3f),
                                contentColor = Color.DarkGray,
                                elevation = FloatingActionButtonDefaults.elevation(0.dp)
                            ) {
                                Icon(Icons.Default.KeyboardDoubleArrowDown, contentDescription = stringResource(R.string.earliest))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog.value) {
        AddTransactionDialog(
            customer = customer,
            user = user,
            repository = repository,
            currentBalance = balance,
            currency = currency,
            onDismiss = { showAddDialog.value = false },
            onConfirm = { transaction ->
                showAddDialog.value = false
                targetScrollId = transaction.transactionId
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
            repository = repository,
            onConfirm = { id -> targetScrollId = id },
            onDismiss = { showEditDialog.value = null }
        )
    }

    showSendConfirmDialog.value?.let { transaction ->
        AlertDialog(
            onDismissRequest = { showSendConfirmDialog.value = null },
            title = { Text(stringResource(R.string.send_notice_title)) },
            text = { Text(stringResource(R.string.send_notice_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    sendSingleTransactionWhatsApp(context, repository, customer, transaction, balance, currency)
                    showSendConfirmDialog.value = null
                }) { Text(stringResource(R.string.send)) }
            },
            dismissButton = {
                TextButton(onClick = { showSendConfirmDialog.value = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    showDeleteTransactionConfirm.value?.let { transaction ->
        AlertDialog(
            onDismissRequest = { showDeleteTransactionConfirm.value = null },
            title = { Text(stringResource(R.string.delete_transaction_title)) },
            text = { Text(stringResource(R.string.delete_transaction_confirm)) },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            repository.deleteTransaction(transaction)
                            if (user?.debtNotificationEnabled == true) NotificationUtils.cancelNotification(context, customer.customerId)
                        }
                        showDeleteTransactionConfirm.value = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteTransactionConfirm.value = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showDeleteConfirm.value) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm.value = false },
            title = { Text(stringResource(R.string.clear_history_title)) },
            text = { Text(stringResource(R.string.clear_history_confirm)) },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            clearHistoryAndSetBalance(repository, customer.customerId, balance, context)
                            Toast.makeText(context, context.getString(R.string.clear_history_success), Toast.LENGTH_SHORT).show()
                        }
                        showDeleteConfirm.value = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.confirm_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm.value = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showReportOptions.value) {
        ReportOptionsDialog(
            customer = customer,
            transactions = transactions,
            currentBalance = balance,
            currency = currency,
            user = user,
            onPdfClick = { showWhatsAppChoice.value = true },
            onCustomRangeClick = { 
                showReportOptions.value = false
                showDateRangePicker.value = true 
            },
            onDismiss = { showReportOptions.value = false }
        )
    }

    if (showWhatsAppChoice.value) {
        AlertDialog(
            onDismissRequest = { showWhatsAppChoice.value = false },
            title = { Text("اختر تطبيق الإرسال") },
            text = {
                Column {
                    ListItem(
                        headlineContent = { Text("واتساب (WhatsApp)") },
                        leadingContent = { Icon(Icons.Default.Chat, contentDescription = null, tint = Color(0xFF25D366)) },
                        modifier = Modifier.clickable { 
                            showWhatsAppChoice.value = false
                            PdfReportGenerator.generateCustomerReport(context, user, customer, transactions, "com.whatsapp")
                        }
                    )
                    ListItem(
                        headlineContent = { Text("واتساب الأعمال (Business)") },
                        leadingContent = { Icon(Icons.Default.BusinessCenter, contentDescription = null, tint = Color(0xFF075E54)) },
                        modifier = Modifier.clickable { 
                            showWhatsAppChoice.value = false
                            PdfReportGenerator.generateCustomerReport(context, user, customer, transactions, "com.whatsapp.w4b")
                        }
                    )
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showWhatsAppChoice.value = false }) { Text(stringResource(R.string.cancel)) } }
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
    val dateFormat = SimpleDateFormat("yyyy/MM/dd - hh:mm a", Locale.getDefault())
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (transaction.debt) stringResource(R.string.debt_label) else stringResource(R.string.paid_label),
                            fontWeight = FontWeight.Bold,
                            color = if (transaction.debt) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        if (!transaction.isSynced) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                        } else {
                            Icon(Icons.Default.CloudDone, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                        }

                        if (transaction.sent) {
                            Spacer(modifier = Modifier.width(4.dp))
                            if (transaction.sentViaWhatsApp) {
                                Icon(Icons.Default.DoneAll, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            } else {
                                Icon(Icons.Default.Done, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    Text(text = dateFormat.format(Date(transaction.date)), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (transaction.note.isNotEmpty()) {
                        Text(text = transaction.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                Text(
                    text = "${formatAmount(transaction.amount)} $currency",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = if (transaction.debt) MaterialTheme.colorScheme.error else Color(0xFF388E3C)
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (!transaction.sent && isNextToSent) {
                    IconButton(onClick = { onSend(transaction) }) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color(0xFF25D366))
                    }
                } else if (!transaction.sent) {
                    val context = LocalContext.current
                    IconButton(onClick = {
                        Toast.makeText(context, context.getString(R.string.send_previous_first_error), Toast.LENGTH_LONG).show()
                    }) {
                        Box(contentAlignment = Alignment.BottomEnd) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(22.dp))
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(10.dp).background(MaterialTheme.colorScheme.surface, CircleShape))
                        }
                    }
                }

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        if (!transaction.sent) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.mark_as_sent_manual)) },
                                leadingIcon = { Icon(Icons.Default.DoneAll, contentDescription = null) },
                                onClick = { onMarkAsSent(transaction); showMenu = false }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.edit)) },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = { onEdit(transaction); showMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete)) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { onDelete(transaction); showMenu = false }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AddTransactionDialog(customer: Customer, user: User?, repository: DataRepository, currentBalance: Double, currency: String, onDismiss: () -> Unit, onConfirm: (Transaction) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf<Boolean?>(null) }
    var selectedDate by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = FirebaseAuth.getInstance()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_new_transaction_title)) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selectedType == true, onClick = { selectedType = true })
                    Text(stringResource(R.string.debt_label))
                    Spacer(modifier = Modifier.width(16.dp))
                    RadioButton(selected = selectedType == false, onClick = { selectedType = false })
                    Text(stringResource(R.string.paid_label))
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { if (it.all { char -> char.isDigit() || char == '.' }) amount = it },
                    label = { Text(stringResource(R.string.amount_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = dateFormat.format(Date(selectedDate)),
                    onValueChange = { },
                    label = { Text(stringResource(R.string.date_label)) },
                    modifier = Modifier.fillMaxWidth().clickable { showDatePicker(context) { date -> selectedDate = date } },
                    enabled = false, readOnly = true,
                    trailingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.note_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val amt = amount.toDoubleOrNull() ?: 0.0
                if (selectedType == null || amt <= 0) {
                    Toast.makeText(context, context.getString(R.string.invalid_data), Toast.LENGTH_SHORT).show()
                    return@Button
                }
                if (selectedType == true && customer.debtLimit > 0 && currentBalance + amt > customer.debtLimit) {
                    Toast.makeText(context, context.getString(R.string.debt_limit_exceeded), Toast.LENGTH_LONG).show()
                    return@Button
                }
                
                val userId = auth.currentUser?.uid ?: ""
                val id = UUID.randomUUID().toString()
                val trans = Transaction(id, customer.customerId, userId, amt, note, selectedDate, selectedType!!)
                
                scope.launch {
                    repository.addTransaction(trans)
                    if (user?.debtNotificationEnabled == true && customer.debtLimit > 0) {
                        val newBalance = currentBalance + (if (selectedType!!) amt else -amt)
                        NotificationUtils.scheduleDebtNotification(context, customer, newBalance, selectedDate)
                    }
                    onConfirm(trans)
                }
            }) { Text(stringResource(R.string.add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun EditTransactionDialog(transaction: Transaction, repository: DataRepository, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var amount by remember { mutableStateOf(transaction.amount.toString()) }
    var note by remember { mutableStateOf(transaction.note) }
    var isDebt by remember { mutableStateOf(transaction.debt) }
    var selectedDate by remember { mutableLongStateOf(transaction.date) }
    val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit)) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = isDebt, onClick = { isDebt = true })
                    Text(stringResource(R.string.debt_label))
                    Spacer(modifier = Modifier.width(16.dp))
                    RadioButton(selected = !isDebt, onClick = { isDebt = false })
                    Text(stringResource(R.string.paid_label))
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { if (it.all { char -> char.isDigit() || char == '.' }) amount = it },
                    label = { Text(stringResource(R.string.amount_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = dateFormat.format(Date(selectedDate)),
                    onValueChange = { },
                    label = { Text(stringResource(R.string.date_label)) },
                    modifier = Modifier.fillMaxWidth().clickable { showDatePicker(context) { date -> selectedDate = date } },
                    enabled = false, readOnly = true,
                    trailingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.note_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val amt = amount.toDoubleOrNull() ?: 0.0
                if (amt > 0) {
                    scope.launch {
                        repository.addTransaction(transaction.copy(amount = amt, note = note, debt = isDebt, date = selectedDate))
                        onDismiss()
                    }
                }
            }) { Text(stringResource(R.string.edit)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun ReportOptionsDialog(
    customer: Customer, 
    transactions: List<Transaction>, 
    currentBalance: Double, 
    currency: String, 
    user: User?,
    onPdfClick: () -> Unit,
    onCustomRangeClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.share_report_title)) },
        text = {
            Column {
                ListItem(
                    headlineContent = { Text("تصدير كشف حساب PDF (احترافي)") },
                    leadingContent = { Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color.Red) },
                    modifier = Modifier.clickable { 
                        onPdfClick()
                        onDismiss()
                    }
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.full_account_statement)) },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    modifier = Modifier.clickable { sendWhatsAppReport(context, customer, currentBalance, transactions, context.getString(R.string.whatsapp_full_statement_title), currency); onDismiss() }
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.date_range_statement)) },
                    leadingContent = { Icon(Icons.Default.DateRange, contentDescription = null) },
                    modifier = Modifier.clickable { onCustomRangeClick() }
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.send_reminder)) },
                    leadingContent = { Icon(Icons.Default.Notifications, contentDescription = null) },
                    modifier = Modifier.clickable { sendWhatsAppReminder(context, customer, currentBalance, user, currency); onDismiss() }
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.debt_only_report)) },
                    leadingContent = { Icon(Icons.Default.AccountBox, contentDescription = null) },
                    modifier = Modifier.clickable { sendWhatsAppReport(context, customer, currentBalance, emptyList(), context.getString(R.string.whatsapp_debt_notice_title), currency); onDismiss() }
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } }
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
    var startDate by remember { mutableLongStateOf(System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000) }
    var endDate by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_period_title)) },
        text = {
            Column {
                Text(stringResource(R.string.from_date), style = MaterialTheme.typography.labelSmall)
                OutlinedTextField(
                    value = dateFormat.format(Date(startDate)),
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth().clickable { showDatePicker(context) { startDate = it } },
                    enabled = false, readOnly = true,
                    trailingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null) }
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.to_date), style = MaterialTheme.typography.labelSmall)
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
                val previousTransactions = transactions.filter { it.date < startDate }
                val prevDebt = previousTransactions.filter { it.debt }.sumOf { it.amount }
                val prevPaid = previousTransactions.filter { !it.debt }.sumOf { it.amount }
                val previousBalance = prevDebt - prevPaid
                val rangeTransactions = transactions.filter { it.date in startDate..endDate }
                sendWhatsAppReportWithRange(context, customer, startDate, endDate, previousBalance, rangeTransactions, currency)
                onDismiss()
            }) { Text(stringResource(R.string.generate_report)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

fun sendWhatsAppReportWithRange(context: Context, customer: Customer, startDate: Long, endDate: Long, previousBalance: Double, reportTransactions: List<Transaction>, currency: String) {
    val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
    val sb = StringBuilder(context.getString(R.string.whatsapp_report_for, customer.name) + "\n")
    sb.append(context.getString(R.string.whatsapp_period, dateFormat.format(Date(startDate)), dateFormat.format(Date(endDate))) + "\n")
    sb.append("-----------------\n")
    var runningBalance = previousBalance
    val prevStatus = when {
        previousBalance > 0 -> context.getString(R.string.whatsapp_you_owe)
        previousBalance < 0 -> context.getString(R.string.whatsapp_owe_you)
        else -> ""
    }
    sb.append(context.getString(R.string.whatsapp_previous_balance, formatAmount(kotlin.math.abs(previousBalance)), currency, prevStatus) + "\n")
    sb.append("-----------------\n")
    if (reportTransactions.isNotEmpty()) {
        val sorted = reportTransactions.sortedBy { it.date }
        sorted.forEach { trans ->
            val type = if (trans.debt) context.getString(R.string.voice_type_debt) else context.getString(R.string.voice_type_paid)
            if (trans.debt) runningBalance += trans.amount else runningBalance -= trans.amount
            val balanceStatus = when {
                runningBalance > 0 -> context.getString(R.string.whatsapp_balance_status_you_owe)
                runningBalance < 0 -> context.getString(R.string.whatsapp_balance_status_owe_you)
                else -> context.getString(R.string.whatsapp_balance_status_zero)
            }
            sb.append("- ${dateFormat.format(Date(trans.date))}\n")
            sb.append("  $type: ${formatAmount(trans.amount)} $currency\n")
            if (trans.note.isNotEmpty()) sb.append("  ${context.getString(R.string.note_label)}: ${trans.note}\n")
            sb.append("  ${context.getString(R.string.current_balance_label)}: ${formatAmount(kotlin.math.abs(runningBalance))} $currency ($balanceStatus)\n\n")
        }
        sb.append("-----------------\n")
    }
    val finalBalanceStatus = when {
        runningBalance > 0 -> context.getString(R.string.whatsapp_balance_status_you_owe)
        runningBalance < 0 -> context.getString(R.string.whatsapp_balance_status_owe_you)
        else -> ""
    }
    if (runningBalance == 0.0) sb.append(context.getString(R.string.whatsapp_final_balance_zero))
    else sb.append(context.getString(R.string.whatsapp_final_balance_prefix, finalBalanceStatus, formatAmount(kotlin.math.abs(runningBalance)), currency))
    val phoneNumber = customer.phoneNumber.filter { it.isDigit() }.let { if (it.length == 9) "967$it" else it }
    try {
        val message = sb.toString()
        val intent = Intent(Intent.ACTION_VIEW).apply { data = Uri.parse("https://api.whatsapp.com/send?phone=$phoneNumber&text=${URLEncoder.encode(message, "UTF-8")}") }
        context.startActivity(intent)
    } catch (e: Exception) { Toast.makeText(context, context.getString(R.string.whatsapp_error_send), Toast.LENGTH_SHORT).show() }
}

fun sendBatchTransactionsWhatsApp(
    context: Context, 
    repository: DataRepository, 
    customer: Customer, 
    unsentTransactions: List<Transaction>, 
    currentBalance: Double, 
    currency: String
) {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val isOnline = connectivityManager.activeNetwork?.let {
        connectivityManager.getNetworkCapabilities(it)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } ?: false
    
    if (!isOnline) { 
        Toast.makeText(context, context.getString(R.string.no_internet_error), Toast.LENGTH_LONG).show()
        return 
    }

    val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
    val sb = StringBuilder("*إشعار عمليات جديدة*\n")
    sb.append("${context.getString(R.string.report_customer_name)}: ${customer.name}\n")
    sb.append("-----------------\n")

    val unsentTotal = unsentTransactions.sumOf { if (it.debt) it.amount else -it.amount }
    val previousBalance = currentBalance - unsentTotal
    
    sb.append("الرصيد السابق: ${formatAmount(previousBalance)} $currency\n")
    sb.append("-----------------\n")

    unsentTransactions.forEach { trans ->
        val type = if (trans.debt) "دين (+)" else "سداد (-)"
        sb.append("• ${dateFormat.format(Date(trans.date))}\n")
        sb.append("  $type: ${formatAmount(trans.amount)} $currency\n")
        if (trans.note.isNotEmpty()) sb.append("  ملاحظة: ${trans.note}\n")
    }

    sb.append("-----------------\n")
    val balanceText = when {
        currentBalance > 0 -> "الرصيد الذي عليكم: ${formatAmount(currentBalance)} $currency"
        currentBalance < 0 -> "الرصيد لكم: ${formatAmount(kotlin.math.abs(currentBalance))} $currency"
        else -> "الرصيد الحالي: 0 $currency"
    }
    sb.append("*$balanceText*")

    val phoneNumber = customer.phoneNumber.filter { it.isDigit() }.let { if (it.length == 9) "967$it" else it }
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply { 
            data = Uri.parse("https://api.whatsapp.com/send?phone=$phoneNumber&text=${URLEncoder.encode(sb.toString(), "UTF-8")}") 
        }
        context.startActivity(intent)
        
        // تحديث كافة العمليات كمرسلة
        kotlinx.coroutines.MainScope().launch {
            unsentTransactions.forEach { trans ->
                repository.addTransaction(trans.copy(sent = true, sentViaWhatsApp = true))
            }
        }
    } catch (e: Exception) { 
        Toast.makeText(context, context.getString(R.string.whatsapp_error_open), Toast.LENGTH_SHORT).show() 
    }
}

fun sendSingleTransactionWhatsApp(context: Context, repository: DataRepository, customer: Customer, transaction: Transaction, currentBalance: Double, currency: String) {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork
    val capabilities = connectivityManager.getNetworkCapabilities(network)
    val isOnline = capabilities != null && (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
    if (!isOnline) { Toast.makeText(context, context.getString(R.string.no_internet_error), Toast.LENGTH_LONG).show(); return }
    val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
    val title = if (transaction.debt) context.getString(R.string.whatsapp_single_debt_title) else context.getString(R.string.whatsapp_single_paid_title)
    val transactionText = if (transaction.debt) context.getString(R.string.whatsapp_amount_you_owe) else context.getString(R.string.whatsapp_amount_owe_you)
    val balanceText = when {
        currentBalance > 0 -> context.getString(R.string.whatsapp_new_balance_you_owe, formatAmount(currentBalance), currency)
        currentBalance < 0 -> context.getString(R.string.whatsapp_new_balance_owe_you, formatAmount(kotlin.math.abs(currentBalance)), currency)
        else -> context.getString(R.string.whatsapp_new_balance_zero, currency)
    }
    val message = "$title\n${context.getString(R.string.report_customer_name)}: ${customer.name}\n$transactionText: ${formatAmount(transaction.amount)} $currency\n${context.getString(R.string.date_label)}: ${dateFormat.format(Date(transaction.date))}\n$balanceText"
    val phoneNumber = customer.phoneNumber.filter { it.isDigit() }.let { if (it.length == 9) "967$it" else it }
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply { data = Uri.parse("https://api.whatsapp.com/send?phone=$phoneNumber&text=${URLEncoder.encode(message, "UTF-8")}") }
        context.startActivity(intent)
        
        kotlinx.coroutines.MainScope().launch {
            repository.addTransaction(transaction.copy(sent = true, sentViaWhatsApp = true))
        }
    } catch (e: Exception) { Toast.makeText(context, context.getString(R.string.whatsapp_error_open), Toast.LENGTH_SHORT).show() }
}

fun deleteTransaction(context: Context, transaction: Transaction, customer: Customer, user: User?) {
    // تم نقل الكود إلى داخل الزر لاستخدام الـ Repository
}

fun sendWhatsAppReport(context: Context, customer: Customer, currentBalance: Double, reportTransactions: List<Transaction>, title: String, currency: String) {
    val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
    val sb = StringBuilder("$title\n${context.getString(R.string.report_customer_name)}: ${customer.name}\n${context.getString(R.string.date_label)}: ${dateFormat.format(Date())}\n-----------------\n")
    if (reportTransactions.isNotEmpty()) {
        val sorted = reportTransactions.sortedBy { it.date }
        var runningBalance = 0.0
        sorted.forEach { trans ->
            val type = if (trans.debt) context.getString(R.string.voice_type_debt) else context.getString(R.string.voice_type_paid)
            if (trans.debt) runningBalance += trans.amount else runningBalance -= trans.amount
            val balanceStatus = when {
                runningBalance > 0 -> context.getString(R.string.whatsapp_balance_status_you_owe)
                runningBalance < 0 -> context.getString(R.string.whatsapp_balance_status_owe_you)
                else -> context.getString(R.string.whatsapp_balance_status_zero)
            }
            sb.append("- ${dateFormat.format(Date(trans.date))}\n  $type: ${formatAmount(trans.amount)} $currency\n")
            if (trans.note.isNotEmpty()) sb.append("  ${context.getString(R.string.note_label)}: ${trans.note}\n")
            sb.append("  ${context.getString(R.string.current_balance_label)}: ${formatAmount(kotlin.math.abs(runningBalance))} $currency ($balanceStatus)\n\n")
        }
        sb.append("-----------------\n")
    }
    val finalBalanceStatus = when {
        currentBalance > 0 -> context.getString(R.string.whatsapp_balance_status_you_owe)
        currentBalance < 0 -> context.getString(R.string.whatsapp_balance_status_owe_you)
        else -> ""
    }
    if (currentBalance == 0.0) sb.append(context.getString(R.string.whatsapp_final_balance_zero))
    else sb.append(context.getString(R.string.whatsapp_final_balance_prefix, finalBalanceStatus, formatAmount(kotlin.math.abs(currentBalance)), currency))
    val phoneNumber = customer.phoneNumber.filter { it.isDigit() }.let { if (it.length == 9) "967$it" else it }
    try {
        val message = sb.toString()
        val intent = Intent(Intent.ACTION_VIEW).apply { data = Uri.parse("https://api.whatsapp.com/send?phone=$phoneNumber&text=${URLEncoder.encode(message, "UTF-8")}") }
        context.startActivity(intent)
    } catch (e: Exception) { Toast.makeText(context, context.getString(R.string.whatsapp_error_send), Toast.LENGTH_SHORT).show() }
}

fun sendWhatsAppReminder(context: Context, customer: Customer, balance: Double, user: User?, currency: String) {
    val template = user?.whatsappReminderTemplate ?: context.getString(R.string.default_whatsapp_template)
    val message = template.replace("{name}", customer.name).replace("{balance}", "${formatAmount(balance)} $currency")
    val phoneNumber = customer.phoneNumber.filter { it.isDigit() }.let { if (it.length == 9) "967$it" else it }
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply { data = Uri.parse("https://api.whatsapp.com/send?phone=$phoneNumber&text=${URLEncoder.encode(message, "UTF-8")}") }
        context.startActivity(intent)
    } catch (e: Exception) { Toast.makeText(context, context.getString(R.string.whatsapp_error_open), Toast.LENGTH_SHORT).show() }
}

suspend fun clearHistoryAndSetBalance(repository: DataRepository, customerId: String, balance: Double, context: Context) {
    val auth = FirebaseAuth.getInstance()
    val userId = auth.currentUser?.uid ?: ""
    
    repository.deleteTransactionsByCustomer(customerId)
    
    if (balance != 0.0) {
        val id = UUID.randomUUID().toString()
        repository.addTransaction(
            Transaction(
                transactionId = id, 
                customerId = customerId, 
                userId = userId, 
                amount = kotlin.math.abs(balance), 
                note = context.getString(R.string.previous_balance_note), 
                date = System.currentTimeMillis(), 
                debt = balance > 0
            )
        )
    }
}
