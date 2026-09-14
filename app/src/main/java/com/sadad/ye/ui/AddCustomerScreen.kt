package com.sadad.ye.ui

import android.Manifest
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.sadad.ye.R
import com.sadad.ye.data.DataRepository
import com.sadad.ye.models.Customer
import com.sadad.ye.models.Transaction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCustomerScreen(repository: DataRepository, onBack: () -> Unit, defaultAppCurrency: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var initialBalance by remember { mutableStateOf("") }
    var debtLimit by remember { mutableStateOf("") }
    var customerCurrency by remember { mutableStateOf(defaultAppCurrency) }
    var isLoading by remember { mutableStateOf(false) }
    var showCurrencyDialog by remember { mutableStateOf(false) }

    val currencies = listOf(
        stringResource(R.string.currency_yer),
        stringResource(R.string.currency_sar),
        stringResource(R.string.currency_aed),
        stringResource(R.string.currency_usd),
        stringResource(R.string.currency_egp)
    )
    
    val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
    var selectedDate by remember { mutableStateOf(System.currentTimeMillis()) }

    // لاونشر استيراد جهات الاتصال
    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
                        val hasPhone = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.HAS_PHONE_NUMBER))
                        
                        name = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME)) ?: ""
                        
                        if (hasPhone == "1") {
                            context.contentResolver.query(
                                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                null,
                                ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                                arrayOf(id),
                                null
                            )?.use { phoneCursor ->
                                if (phoneCursor.moveToFirst()) {
                                    phone = phoneCursor.getString(phoneCursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
                                        ?.replace(" ", "")
                                        ?.replace("-", "")
                                        ?.replace("(", "")
                                        ?.replace(")", "") ?: ""
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                Toast.makeText(context, context.getString(R.string.contacts_import_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            contactPickerLauncher.launch(null)
        } else {
            Toast.makeText(context, context.getString(R.string.contacts_permission_required), Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_new_customer)) },
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
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                trailingIcon = {
                    IconButton(onClick = { 
                        when (PackageManager.PERMISSION_GRANTED) {
                            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) -> {
                                contactPickerLauncher.launch(null)
                            }
                            else -> {
                                permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                            }
                        }
                    }) {
                        Icon(Icons.Default.Contacts, contentDescription = stringResource(R.string.import_contacts), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text(stringResource(R.string.phone_number)) },
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
                label = { Text(stringResource(R.string.previous_debt_balance, customerCurrency)) },
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
                    label = { Text(stringResource(R.string.initial_balance_date)) },
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
                        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = debtLimit,
                onValueChange = { debtLimit = it },
                label = { Text(stringResource(R.string.debt_limit_label_with_currency, customerCurrency)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
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
                    if (name.trim().isEmpty() || phone.trim().isEmpty()) {
                        Toast.makeText(context, context.getString(R.string.fill_required_fields), Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    
                    isLoading = true
                    val balanceAmount = initialBalance.replaceDigitsToEnglish().toDoubleOrNull() ?: 0.0
                    val limitAmount = debtLimit.replaceDigitsToEnglish().toDoubleOrNull() ?: 0.0
                    
                    scope.launch {
                        val auth = FirebaseAuth.getInstance()
                        val userId = auth.currentUser?.uid ?: ""
                        
                        // فحص التكرار محلياً
                        val currentCustomers = repository.getCustomers(userId).first()
                        if (currentCustomers.any { it.name == name.trim() }) {
                            Toast.makeText(context, context.getString(R.string.customer_name_exists), Toast.LENGTH_SHORT).show()
                            isLoading = false
                            return@launch
                        }
                        if (currentCustomers.any { it.phoneNumber == phone.trim() }) {
                            Toast.makeText(context, context.getString(R.string.phone_number_exists), Toast.LENGTH_SHORT).show()
                            isLoading = false
                            return@launch
                        }

                        val customerId = UUID.randomUUID().toString()
                        val customer = Customer(customerId = customerId, name = name.trim(), phoneNumber = phone.trim(), userId = userId, debtLimit = limitAmount, currency = customerCurrency)
                        repository.addCustomer(customer)

                        if (balanceAmount > 0) {
                            val transactionId = UUID.randomUUID().toString()
                            val transaction = Transaction(
                                transactionId = transactionId, 
                                customerId = customerId, 
                                userId = userId,
                                amount = balanceAmount, 
                                note = context.getString(R.string.previous_balance_note),
                                debt = true, 
                                date = selectedDate
                            )
                            repository.addTransaction(transaction)
                        }

                        isLoading = false
                        Toast.makeText(context, context.getString(R.string.customer_added_success), Toast.LENGTH_SHORT).show()
                        onBack()
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
                    Text(stringResource(R.string.add_customer_button))
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
