package com.sadad.ye.ui

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DateRange
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
import com.sadad.ye.models.Transaction
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyReportScreen(onBack: () -> Unit, currency: String = "ريال") {
    var transactions by remember { mutableStateOf<List<Transaction>>(emptyList()) }
    var customers by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedDate by remember { mutableStateOf(Calendar.getInstance()) }
    
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val currentUserId = auth.currentUser?.uid ?: ""
    val db = FirebaseFirestore.getInstance()

    LaunchedEffect(selectedDate, currentUserId) {
        if (currentUserId.isEmpty()) return@LaunchedEffect
        isLoading = true
        
        // 1. جلب خريطة أسماء العملاء
        db.collection("customers")
            .whereEqualTo("userId", currentUserId)
            .get()
            .addOnSuccessListener { customerSnapshot ->
                val customerMap = customerSnapshot.documents.associate { 
                    it.id to (it.getString("name") ?: "عميل غير معروف")
                }
                customers = customerMap

                // 2. تحديد وقت بداية ونهاية اليوم المختار
                val calendar = selectedDate.clone() as Calendar
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startTime = calendar.timeInMillis

                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                calendar.set(Calendar.MILLISECOND, 999)
                val endTime = calendar.timeInMillis

                // 3. جلب العمليات (نحاول أولاً بـ userId لسرعة الأداء)
                db.collection("transactions")
                    .whereEqualTo("userId", currentUserId)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        val allList = snapshot.toObjects(Transaction::class.java)
                        val filtered = allList.filter { it.date in startTime..endTime }
                        
                        if (filtered.isNotEmpty()) {
                            transactions = filtered.sortedByDescending { it.date }
                            isLoading = false
                        } else {
                            // 4. الطريقة الاحتياطية للبيانات القديمة
                            val ids = customerMap.keys.toList()
                            if (ids.isNotEmpty()) {
                                fetchLegacy(db, ids, startTime, endTime) { legacyList ->
                                    transactions = legacyList.sortedByDescending { it.date }
                                    isLoading = false
                                }
                            } else {
                                transactions = emptyList()
                                isLoading = false
                            }
                        }
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
                    },
                    actions = {
                        if (transactions.isNotEmpty()) {
                            IconButton(onClick = { 
                                shareDailySummary(context, selectedDate, transactions, customers, totalDebt, totalPaid, currency) 
                            }) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "مشاركة", tint = Color(0xFF388E3C))
                            }
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
                // بطاقة التاريخ قابلة للضغط
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clickable {
                            DatePickerDialog(
                                context,
                                { _, year, month, day ->
                                    val newDate = Calendar.getInstance()
                                    newDate.set(year, month, day)
                                    selectedDate = newDate
                                },
                                selectedDate.get(Calendar.YEAR),
                                selectedDate.get(Calendar.MONTH),
                                selectedDate.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("تاريخ التقرير:", style = MaterialTheme.typography.labelSmall)
                            Text(text = SimpleDateFormat("EEEE، d MMMM yyyy", Locale("ar")).format(selectedDate.time), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Icon(Icons.Default.DateRange, contentDescription = "تغيير التاريخ", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryCard("إجمالي الديون", totalDebt, currency, Color(0xFFD32F2F), Modifier.weight(1f))
                    SummaryCard("إجمالي التحصيل", totalPaid, currency, Color(0xFF388E3C), Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else if (transactions.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("لا توجد عمليات لهذا التاريخ", color = Color.Gray) }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(transactions) { transaction ->
                            ReportTransactionItem(transaction, customers[transaction.customerId] ?: "عميل غير معروف", currency)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SummaryCard(title: String, amount: Double, currency: String, color: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            Text(text = formatAmount(amount), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
            Text(currency, style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}

@Composable
fun ReportTransactionItem(transaction: Transaction, customerName: String, currency: String) {
    val timeFormat = SimpleDateFormat("hh:mm a", Locale("ar"))
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(1.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(customerName, fontWeight = FontWeight.Bold)
                Text(text = timeFormat.format(Date(transaction.date)), fontSize = 11.sp, color = Color.Gray)
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

private fun fetchLegacy(db: FirebaseFirestore, ids: List<String>, start: Long, end: Long, onResult: (List<Transaction>) -> Unit) {
    val all = mutableListOf<Transaction>()
    val chunks = ids.chunked(30)
    var count = 0
    chunks.forEach { chunk ->
        db.collection("transactions").whereIn("customerId", chunk).get().addOnSuccessListener { snapshot ->
            all.addAll(snapshot.toObjects(Transaction::class.java).filter { it.date in start..end })
            count++
            if (count == chunks.size) onResult(all)
        }.addOnFailureListener { count++; if (count == chunks.size) onResult(all) }
    }
}

private fun shareDailySummary(
    context: Context, 
    date: Calendar, 
    transactions: List<Transaction>,
    customers: Map<String, String>,
    debt: Double, 
    paid: Double, 
    currency: String
) {
    val dateStr = SimpleDateFormat("yyyy/MM/dd", Locale("ar")).format(date.time)
    val timeFormat = SimpleDateFormat("hh:mm a", Locale("ar"))
    
    val sb = StringBuilder()
    sb.append("تقرير العمليات اليومي - سداد\n")
    sb.append("التاريخ: $dateStr\n")
    sb.append("--------------------------\n")
    
    // تفاصيل العمليات
    transactions.sortedBy { it.date }.forEach { trans ->
        val customerName = customers[trans.customerId] ?: "عميل غير معروف"
        val type = if (trans.debt) "دين (+)" else "تحصيل (-)"
        val time = timeFormat.format(Date(trans.date))
        
        sb.append("- $customerName ($time)\n")
        sb.append("  $type: ${formatAmount(trans.amount)} $currency\n")
        if (trans.note.isNotEmpty()) {
            sb.append("  ملاحظة: ${trans.note}\n")
        }
        sb.append("\n")
    }
    
    sb.append("--------------------------\n")
    sb.append("إجمالي الديون: ${formatAmount(debt)} $currency\n")
    sb.append("إجمالي التحصيل: ${formatAmount(paid)} $currency\n")
    sb.append("الصافي: ${formatAmount(paid - debt)} $currency")
    
    try {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse("https://api.whatsapp.com/send?text=${URLEncoder.encode(sb.toString(), "UTF-8")}")
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "فشل فتح واتساب", Toast.LENGTH_SHORT).show()
    }
}
