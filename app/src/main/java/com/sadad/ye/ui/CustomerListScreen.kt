package com.sadad.ye.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.sadad.ye.models.Customer
import com.sadad.ye.models.Transaction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerListScreen(
    onAddCustomerClick: () -> Unit,
    onEditCustomerClick: (Customer) -> Unit,
    onCustomerClick: (Customer) -> Unit,
    onDailyReportClick: () -> Unit,
    onSettingsClick: () -> Unit,
    currency: String = "ريال" // استلام العملة المختارة
) {
    var customers by remember { mutableStateOf<List<Customer>>(emptyList()) }
    var transactions by remember { mutableStateOf<List<Transaction>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    val context = LocalContext.current
    
    val auth = FirebaseAuth.getInstance()
    val currentUserId = auth.currentUser?.uid ?: ""

    LaunchedEffect(currentUserId) {
        if (currentUserId.isEmpty()) return@LaunchedEffect
        val db = FirebaseFirestore.getInstance()
        
        db.collection("customers")
            .whereEqualTo("userId", currentUserId)
            .addSnapshotListener { value, _ ->
                if (value != null) {
                    customers = value.toObjects(Customer::class.java)
                }
            }
            
        db.collection("transactions")
            .addSnapshotListener { value, _ ->
                if (value != null) {
                    val allTrans = value.toObjects(Transaction::class.java)
                    val customerIds = customers.map { it.customerId }.toSet()
                    transactions = allTrans.filter { it.customerId in customerIds }
                }
                isLoading = false
            }
    }

    val filteredCustomers = if (searchQuery.isEmpty()) {
        customers
    } else {
        customers.filter { 
            it.name.contains(searchQuery, ignoreCase = true) || 
            it.phoneNumber.contains(searchQuery) 
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("سداد - قائمة العملاء") },
                    actions = {
                        IconButton(onClick = onDailyReportClick) {
                            Icon(Icons.Default.List, contentDescription = "التقرير اليومي")
                        }
                        IconButton(onClick = onSettingsClick) {
                            Icon(Icons.Default.Settings, contentDescription = "الإعدادات")
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = onAddCustomerClick) {
                    Icon(Icons.Default.Add, contentDescription = "إضافة عميل")
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
            ) {
                val totalAllBalances = customers.sumOf { customer ->
                    val customerTransactions = transactions.filter { it.customerId == customer.customerId }
                    val debt = customerTransactions.filter { it.debt }.sumOf { it.amount }
                    val paid = customerTransactions.filter { !it.debt }.sumOf { it.amount }
                    val balance = debt - paid
                    if (balance > 0) balance else 0.0
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("إجمالي مديونيات العملاء", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
                            Text(
                                text = "${formatAmount(totalAllBalances)} $currency", // استخدام العملة
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD32F2F)
                            )
                        }
                        Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFD32F2F), modifier = Modifier.size(32.dp))
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("بحث عن عميل...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true
                )

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(filteredCustomers) { customer ->
                            val customerTransactions = transactions.filter { it.customerId == customer.customerId }
                            val totalDebt = customerTransactions.filter { it.debt }.sumOf { it.amount }
                            val totalPaid = customerTransactions.filter { !it.debt }.sumOf { it.amount }
                            val balance = totalDebt - totalPaid

                            CustomerItem(
                                customer = customer,
                                balance = balance,
                                currency = currency, // تمرير العملة
                                onClick = { onCustomerClick(customer) },
                                onEdit = { onEditCustomerClick(customer) },
                                onDelete = { deleteCustomer(customer.customerId, context) }
                            )
                        }
                    }
                }
            }
        }
    }
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
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteDialog = false
                }) { Text("حذف", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("إلغاء") }
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = customer.name, style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${formatAmount(balance)} $currency", // استخدام العملة
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (balance > 0) Color(0xFFD32F2F) else Color(0xFF388E3C),
                    fontWeight = FontWeight.Bold
                )
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { makeCall(context, customer.phoneNumber) }) {
                    Icon(Icons.Default.Call, contentDescription = "اتصال", tint = Color(0xFF388E3C))
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color.Red)
                }
            }
        }
    }
}

private fun deleteCustomer(customerId: String, context: Context) {
    val db = FirebaseFirestore.getInstance()
    db.collection("customers").document(customerId).delete()
        .addOnSuccessListener { Toast.makeText(context, "تم الحذف بنجاح", Toast.LENGTH_SHORT).show() }
}
