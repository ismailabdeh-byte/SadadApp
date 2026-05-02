package com.sadad.ye.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.sadad.ye.models.ActivationCode
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(onBack: () -> Unit) {
    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    
    var durationDays by remember { mutableStateOf("30") }
    var generatedCode by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var codesList by remember { mutableStateOf<List<ActivationCode>>(emptyList()) }
    var isLoadingList by remember { mutableStateOf(true) }

    // جلب آخر الأكواد المنشأة
    LaunchedEffect(Unit) {
        db.collection("activation_codes")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(20)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    isLoadingList = false
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    codesList = snapshot.toObjects(ActivationCode::class.java)
                }
                isLoadingList = false
            }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("لوحة التحكم (الأدمن)") },
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
            ) {
                Text("إنشاء كود تفعيل جديد", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = durationDays,
                        onValueChange = { if (it.all { c -> c.isDigit() }) durationDays = it },
                        label = { Text("عدد الأيام (مثلاً 30 أو 365)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Button(
                        onClick = {
                            val days = durationDays.toIntOrNull() ?: 0
                            if (days > 0) {
                                isGenerating = true
                                val newCode = generateRandomCode()
                                val activationCode = ActivationCode(
                                    code = newCode,
                                    durationDays = days,
                                    isUsed = false,
                                    createdAt = Timestamp.now()
                                )
                                db.collection("activation_codes").document(newCode)
                                    .set(activationCode)
                                    .addOnSuccessListener {
                                        generatedCode = newCode
                                        isGenerating = false
                                        Toast.makeText(context, "تم إنشاء الكود بنجاح", Toast.LENGTH_SHORT).show()
                                    }
                                    .addOnFailureListener { e ->
                                        isGenerating = false
                                        // هنا سنعرف سبب التعليق الحقيقي
                                        Toast.makeText(context, "فشل الإنشاء: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                    }
                            }
                        },
                        enabled = !isGenerating,
                        modifier = Modifier.height(56.dp)
                    ) {
                        if (isGenerating) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        else Text("إنشاء")
                    }
                }

                if (generatedCode.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("الكود المنشأ:", style = MaterialTheme.typography.labelMedium)
                                Text(generatedCode, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            }
                            IconButton(onClick = {
                                clipboardManager.setText(AnnotatedString(generatedCode))
                                Toast.makeText(context, "تم نسخ الكود", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "نسخ")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
                
                Text("آخر الأكواد المنشأة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                
                Spacer(modifier = Modifier.height(8.dp))
                
                if (isLoadingList) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(codesList) { item ->
                            CodeListItem(item)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CodeListItem(item: ActivationCode) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.code, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(text = "المدة: ${item.durationDays} يوم", fontSize = 12.sp, color = Color.Gray)
            }
            
            if (item.isUsed) {
                Text("مستخدم", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            } else {
                Text("جاهز", color = Color(0xFF388E3C), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = {
                    clipboardManager.setText(AnnotatedString(item.code))
                    Toast.makeText(context, "تم نسخ الكود", Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

fun generateRandomCode(): String {
    val chars = "0123456789"
    return (1..6)
        .map { chars.random() }
        .joinToString("")
}
