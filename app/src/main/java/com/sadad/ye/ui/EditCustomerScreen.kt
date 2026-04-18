package com.sadad.ye.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.sadad.ye.models.Customer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCustomerScreen(customer: Customer, onBack: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(customer.name) }
    var phone by remember { mutableStateOf(customer.phoneNumber) }
    var debtLimit by remember { mutableStateOf(if (customer.debtLimit > 0) customer.debtLimit.toString() else "") }
    var isLoading by remember { mutableStateOf(false) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("تعديل بيانات العميل") },
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
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("رقم الجوال") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = debtLimit,
                    onValueChange = { debtLimit = it },
                    label = { Text("سقف المديونية (اختياري)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    placeholder = { Text("اتركه فارغاً ليكون بدون سقف") }
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (name.isNotEmpty() && phone.isNotEmpty()) {
                            isLoading = true
                            val normalizedLimit = debtLimit.replaceDigitsToEnglish()
                            val limitAmount = normalizedLimit.toDoubleOrNull() ?: 0.0

                            checkPhoneAndSave(customer.customerId, name, phone, limitAmount, onResult = { success, error ->
                                isLoading = false
                                if (success) {
                                    Toast.makeText(context, "تم تحديث البيانات بنجاح", Toast.LENGTH_SHORT).show()
                                    onBack()
                                } else {
                                    Toast.makeText(context, error ?: "فشل في تحديث البيانات", Toast.LENGTH_SHORT).show()
                                }
                            })
                        } else {
                            Toast.makeText(context, "يرجى ملء جميع الحقول", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                    } else {
                        Text("حفظ التعديلات")
                    }
                }
            }
        }
    }
}

private fun checkPhoneAndSave(customerId: String, name: String, phone: String, limit: Double, onResult: (Boolean, String?) -> Unit) {
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val userId = auth.currentUser?.uid ?: "test_user_id"

    db.collection("customers")
        .whereEqualTo("userId", userId)
        .whereEqualTo("phoneNumber", phone)
        .get()
        .addOnSuccessListener { documents ->
            val otherCustomer = documents.documents.find { it.id != customerId }
            if (otherCustomer == null) {
                updateCustomer(customerId, name, phone, limit, onResult)
            } else {
                onResult(false, "رقم الجوال هذا مسجل مسبقاً لعميل آخر")
            }
        }
        .addOnFailureListener { e ->
            onResult(false, e.localizedMessage)
        }
}

private fun updateCustomer(id: String, name: String, phone: String, limit: Double, onResult: (Boolean, String?) -> Unit) {
    val db = FirebaseFirestore.getInstance()
    val updates = mapOf(
        "name" to name,
        "phoneNumber" to phone,
        "debtLimit" to limit
    )
    db.collection("customers").document(id)
        .update(updates)
        .addOnSuccessListener { onResult(true, null) }
        .addOnFailureListener { e -> onResult(false, e.localizedMessage) }
}
