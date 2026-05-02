package com.sadad.ye.ui

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.sadad.ye.models.Customer
import com.sadad.ye.models.Transaction
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCustomerScreen(onBack: () -> Unit, currency: String = "ريال") {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var initialBalance by remember { mutableStateOf("") }
    var debtLimit by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    
    val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.forLanguageTag("ar"))
    var selectedDate by remember { mutableStateOf(System.currentTimeMillis()) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("إضافة عميل جديد") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "رجوع")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم العميل") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("رقم الجوال") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Next
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = initialBalance,
                    onValueChange = { initialBalance = it },
                    label = { Text("رصيد مديونية سابقة ($currency)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    placeholder = { Text("0") }
                )
                
                if (initialBalance.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = dateFormat.format(Date(selectedDate)),
                        onValueChange = { },
                        label = { Text("تاريخ الرصيد الافتتاحي") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showDatePicker(context) { date -> selectedDate = date }
                            },
                        enabled = false,
                        readOnly = true,
                        trailingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = debtLimit,
                    onValueChange = { debtLimit = it },
                    label = { Text("سقف المديونية ($currency)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    placeholder = { Text("اتركه فارغاً ليكون بدون سقف") }
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (name.trim().isEmpty() || phone.trim().isEmpty()) {
                            Toast.makeText(context, "يرجى ملء الاسم ورقم الجوال", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        
                        isLoading = true
                        val balanceAmount = initialBalance.toDoubleOrNull() ?: 0.0
                        val limitAmount = debtLimit.toDoubleOrNull() ?: 0.0
                        
                        checkPhoneAndSave(name.trim(), phone.trim(), balanceAmount, limitAmount, selectedDate) { success, error ->
                            isLoading = false
                            if (success) {
                                Toast.makeText(context, "تمت إضافة العميل بنجاح", Toast.LENGTH_SHORT).show()
                                onBack()
                            } else {
                                Toast.makeText(context, error ?: "حدث خطأ", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("إضافة عميل")
                    }
                }
            }
        }
    }
}

private fun checkPhoneAndSave(name: String, phone: String, initialBalance: Double, limit: Double, date: Long, onResult: (Boolean, String?) -> Unit) {
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val userId = auth.currentUser?.uid ?: return

    db.collection("customers")
        .whereEqualTo("userId", userId)
        .whereEqualTo("phoneNumber", phone)
        .get()
        .addOnSuccessListener { documents ->
            if (documents.isEmpty) {
                saveCustomerWithBalance(name, phone, userId, initialBalance, limit, date, onResult)
            } else {
                onResult(false, "رقم الجوال مسجل مسبقاً")
            }
        }
        .addOnFailureListener { onResult(false, it.localizedMessage) }
}

private fun saveCustomerWithBalance(name: String, phone: String, userId: String, initialBalance: Double, limit: Double, date: Long, onResult: (Boolean, String?) -> Unit) {
    val db = FirebaseFirestore.getInstance()
    val batch = db.batch()
    val customerId = UUID.randomUUID().toString()
    val customer = Customer(customerId = customerId, name = name, phoneNumber = phone, userId = userId, debtLimit = limit)
    batch.set(db.collection("customers").document(customerId), customer)

    if (initialBalance > 0) {
        val transactionId = UUID.randomUUID().toString()
        val transaction = Transaction(transactionId = transactionId, customerId = customerId, amount = initialBalance, note = "رصيد مديونية سابقة", debt = true, date = date)
        batch.set(db.collection("transactions").document(transactionId), transaction)
    }

    batch.commit().addOnSuccessListener { onResult(true, null) }.addOnFailureListener { onResult(false, it.localizedMessage) }
}
