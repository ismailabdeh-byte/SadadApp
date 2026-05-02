package com.sadad.ye.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.sadad.ye.models.Transaction
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyReportScreen(onBack: () -> Unit, currency: String = "ريال") {
    var transactions by remember { mutableStateOf<List<Transaction>>(emptyList()) }
    var customers by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedDate by remember { mutableStateOf(Calendar.getInstance()) }

    val auth = FirebaseAuth.getInstance()
    val currentUserId = auth.currentUser?.uid ?: ""
    val db = FirebaseFirestore.getInstance()

    LaunchedEffect(selectedDate, currentUserId) {
        if (currentUserId.isEmpty()) return@LaunchedEffect
        isLoading = true
        
        db.collection("customers")
            .whereEqualTo("userId", currentUserId)
            .get()
            .addOnSuccessListener { snapshot ->
                val customerMap = snapshot.documents.associate { 
                    it.id to (it.getString("name") ?: "عميل غير معروف")
                }
                customers = customerMap

                val startOfDay = selectedDate.clone() as Calendar
                startOfDay.set(Calendar.HOUR_OF_DAY, 0)
                startOfDay.set(Calendar.MINUTE, 0)
                startOfDay.set(Calendar.SECOND, 0)
                startOfDay.set(Calendar.MILLISECOND, 0)

                val endOfDay = selectedDate.clone() as Calendar
                endOfDay.set(Calendar.HOUR_OF_DAY, 23)
                endOfDay.set(Calendar.MINUTE, 59)
                endOfDay.set(Calendar.SECOND, 59)
                endOfDay.set(Calendar.MILLISECOND, 999)

                if (customerMap.isNotEmpty()) {
                    db.collection("transactions")
                        .whereIn("customerId", customerMap.keys.toList())
                        .whereGreaterThanOrEqualTo("date", startOfDay.timeInMillis)
                        .whereLessThanOrEqualTo("date", endOfDay.timeInMillis)
                        .get()
                        .addOnSuccessListener { transSnapshot ->
                            transactions = transSnapshot.toObjects(Transaction::class.java)
                                .sortedByDescending { it.date }
                            isLoading = false
                        }
                        .addOnFailureListener { isLoading = false }
                } else {
                    transactions = emptyList()
                    isLoading = false
                }
            }
            .addOnFailureListener { isLoading = false }
    }

    val totalDebt = transactions.filter { it.debt }.sumOf { it.amount }
    val totalPaid = transactions.filter { !it.debt }.sumOf { it.amount }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("التقرير اليومي") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = SimpleDateFormat("EEEE، d MMMM yyyy", Locale("ar")).format(selectedDate.time),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Icon(Icons.Default.DateRange, contentDescription = null)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SummaryCard(
                        title = "إجمالي الديون",
                        amount = totalDebt,
                        currency = currency,
                        color = Color(0xFFD32F2F),
                        modifier = Modifier.weight(1f)
                    )
                    SummaryCard(
                        title = "إجمالي التحصيل",
                        amount = totalPaid,
                        currency = currency,
                        color = Color(0xFF388E3C),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (transactions.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("لا توجد عمليات لهذا اليوم", color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(transactions) { transaction ->
                            ReportTransactionItem(
                                transaction = transaction,
                                customerName = customers[transaction.customerId] ?: "عميل محذوف",
                                currency = currency
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SummaryCard(title: String, amount: Double, currency: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            Text(
                text = formatAmount(amount),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(currency, style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}

@Composable
fun ReportTransactionItem(transaction: Transaction, customerName: String, currency: String) {
    val timeFormat = SimpleDateFormat("hh:mm a", Locale("ar"))
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(customerName, fontWeight = FontWeight.Bold)
                Text(
                    text = timeFormat.format(Date(transaction.date)),
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${if (transaction.debt) "+" else "-"} ${formatAmount(transaction.amount)} $currency",
                    fontWeight = FontWeight.Bold,
                    color = if (transaction.debt) Color(0xFFD32F2F) else Color(0xFF388E3C)
                )
                if (transaction.note.isNotEmpty()) {
                    Text(text = transaction.note, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
        }
    }
}
