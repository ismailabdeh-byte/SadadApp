package com.sadad.ye.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.sadad.ye.R
import com.sadad.ye.data.DataRepository
import com.sadad.ye.models.Customer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCustomerScreen(customer: Customer, repository: DataRepository, onBack: () -> Unit, defaultAppCurrency: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(customer.name) }
    var phone by remember { mutableStateOf(customer.phoneNumber) }
    var debtLimit by remember { mutableStateOf(if (customer.debtLimit > 0) customer.debtLimit.toString() else "") }
    var customerCurrency by remember { mutableStateOf(if (customer.currency.isNotEmpty()) customer.currency else defaultAppCurrency) }
    var isLoading by remember { mutableStateOf(false) }
    var showCurrencyDialog by remember { mutableStateOf(false) }

    val currencies = listOf(
        stringResource(R.string.currency_yer),
        stringResource(R.string.currency_sar),
        stringResource(R.string.currency_aed),
        stringResource(R.string.currency_usd),
        stringResource(R.string.currency_egp)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.edit_customer_details)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
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
                label = { Text(stringResource(R.string.customer_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text(stringResource(R.string.phone_number)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = debtLimit,
                onValueChange = { debtLimit = it },
                label = { Text(stringResource(R.string.debt_limit_label_with_currency, customerCurrency)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                placeholder = { Text(stringResource(R.string.debt_limit_hint)) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = customerCurrency,
                onValueChange = { },
                label = { Text(stringResource(R.string.default_currency)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showCurrencyDialog = true },
                enabled = false,
                readOnly = true,
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (name.isNotEmpty() && phone.isNotEmpty()) {
                        isLoading = true
                        val limitAmount = debtLimit.replaceDigitsToEnglish().toDoubleOrNull() ?: 0.0

                        scope.launch {
                            val auth = FirebaseAuth.getInstance()
                            val userId = auth.currentUser?.uid ?: ""
                            
                            // فحص التكرار محلياً
                            val currentCustomers = repository.getCustomers(userId).first()
                            val otherCustomer = currentCustomers.find { it.phoneNumber == phone.trim() && it.customerId != customer.customerId }
                            
                            if (otherCustomer == null) {
                                repository.updateCustomer(customer.copy(name = name.trim(), phoneNumber = phone.trim(), debtLimit = limitAmount, currency = customerCurrency))
                                isLoading = false
                                Toast.makeText(context, context.getString(R.string.update_success), Toast.LENGTH_SHORT).show()
                                onBack()
                            } else {
                                isLoading = false
                                Toast.makeText(context, context.getString(R.string.phone_number_exists_other), Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        Toast.makeText(context, context.getString(R.string.fill_all_fields), Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                } else {
                    Text(stringResource(R.string.save_changes))
                }
            }
        }
    }

    if (showCurrencyDialog) {
        AlertDialog(
            onDismissRequest = { showCurrencyDialog = false },
            title = { Text(stringResource(R.string.choose_currency)) },
            text = {
                Column {
                    currencies.forEach { curr ->
                        Text(
                            text = curr,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    customerCurrency = curr
                                    showCurrencyDialog = false
                                }
                                .padding(16.dp)
                        )
                    }
                }
            },
            confirmButton = {}
        )
    }
}
