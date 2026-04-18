package com.sadad.ye

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.sadad.ye.models.Customer
import com.sadad.ye.ui.*
import com.sadad.ye.ui.theme.سدادTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            سدادTheme {
                var currentScreen by remember { mutableStateOf("list") }
                var selectedCustomer by remember { mutableStateOf<Customer?>(null) }

                when (currentScreen) {
                    "list" -> CustomerListScreen(
                        onAddCustomerClick = { currentScreen = "add" },
                        onEditCustomerClick = { customer ->
                            selectedCustomer = customer
                            currentScreen = "edit"
                        },
                        onCustomerClick = { customer ->
                            selectedCustomer = customer
                            currentScreen = "details"
                        },
                        onDailyReportClick = { currentScreen = "daily_report" }
                    )
                    "add" -> AddCustomerScreen(
                        onBack = { currentScreen = "list" }
                    )
                    "edit" -> selectedCustomer?.let { customer ->
                        EditCustomerScreen(
                            customer = customer,
                            onBack = { currentScreen = "list" }
                        )
                    }
                    "details" -> selectedCustomer?.let { customer ->
                        CustomerDetailsScreen(
                            customer = customer,
                            onBack = { currentScreen = "list" }
                        )
                    }
                    "daily_report" -> DailyReportScreen(
                        onBack = { currentScreen = "list" }
                    )
                }
            }
        }
    }
}
