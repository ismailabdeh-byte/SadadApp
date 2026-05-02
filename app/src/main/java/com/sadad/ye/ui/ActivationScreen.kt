package com.sadad.ye.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.sadad.ye.utils.SubscriptionUtils
import java.util.*

@Composable
fun ActivationScreen() {
    var code by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "تفعيل التطبيق",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "يرجى الاشتراك للاستمرار في استخدام التطبيق والحفاظ على بياناتك ومزامنتها سحابياً.",
            textAlign = TextAlign.Center,
            color = Color.Gray
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("طرق الدفع المتاحة:", fontWeight = FontWeight.Bold)
                Text("• بنك الكريمي (حساب): 12345678")
                Text("• محفظة جوالي: 777111222")
                Text("• إم فلوس: 733444555")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "بعد التحويل، أرسل صورة الحوالة للرقم 777000000 لاستلام كود التفعيل.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        OutlinedTextField(
            value = code,
            onValueChange = { code = it },
            label = { Text("أدخل كود التفعيل هنا") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = {
                if (code.length >= 6) {
                    isLoading = true
                    SubscriptionUtils.redeemCode(db, auth.currentUser?.uid ?: "", code) { success, message ->
                        isLoading = false
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(context, "يرجى إدخال كود صحيح", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
            } else {
                Text("تفعيل الآن")
            }
        }
    }
}
