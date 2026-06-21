package com.sadad.ye

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.sadad.ye.models.AppSettings
import com.sadad.ye.models.Customer
import com.sadad.ye.models.User
import com.sadad.ye.ui.*
import com.sadad.ye.ui.theme.سدادTheme
import com.sadad.ye.utils.NotificationUtils
import java.util.*

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationUtils.createNotificationChannel(this)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            
            // طلب إذن الإشعارات لأندرويد 13+
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { _ -> }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }

            val auth = remember { FirebaseAuth.getInstance() }
            val db = remember { FirebaseFirestore.getInstance() }
            
            var authInitialized by remember { mutableStateOf(false) }
            var currentUserId by remember { mutableStateOf(auth.currentUser?.uid ?: "") }
            
            var subscriptionStatus by remember { mutableStateOf<Boolean?>(null) }
            var currentUserData by remember { mutableStateOf<User?>(null) }
            var appSettings by remember { mutableStateOf(AppSettings()) }

            val isSystemInDark = isSystemInDarkTheme()
            val useDarkTheme = currentUserData?.isDarkMode ?: isSystemInDark
            
            سدادTheme(darkTheme = useDarkTheme) {
                // حالة قفل التطبيق
                var isAppLocked by remember { mutableStateOf(true) }
                var currentScreen by remember { mutableStateOf("list") }
                var selectedCustomer by remember { mutableStateOf<Customer?>(null) }

                // مراقبة إعدادات التطبيق العامة
                LaunchedEffect(Unit) {
                    db.collection("config").document("app_settings").addSnapshotListener { snapshot, _ ->
                        if (snapshot != null && snapshot.exists()) {
                            snapshot.toObject(AppSettings::class.java)?.let {
                                appSettings = it
                            }
                        }
                    }
                }

                // إعادة قفل التطبيق فور الخروج منه
                LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
                    if (currentUserData?.isAppLockEnabled == true) {
                        isAppLocked = true
                    }
                }

                // مراقبة حالة المصادقة
                DisposableEffect(Unit) {
                    val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                        val newUserId = firebaseAuth.currentUser?.uid ?: ""
                        if (currentUserId != newUserId) {
                            currentUserId = newUserId
                            subscriptionStatus = null
                            currentUserData = null
                            isAppLocked = true 
                        }
                        authInitialized = true
                    }
                    auth.addAuthStateListener(listener)
                    onDispose { auth.removeAuthStateListener(listener) }
                }

                // مراقبة بيانات المستخدم والاشتراك
                DisposableEffect(currentUserId, appSettings.trialDays) {
                    if (currentUserId.isEmpty()) {
                        subscriptionStatus = null
                        onDispose {}
                    } else {
                        val userRef = db.collection("users").document(currentUserId)
                        val registration = userRef.addSnapshotListener { snapshot, _ ->
                            if (snapshot != null && snapshot.exists()) {
                                val user = snapshot.toObject(User::class.java)
                                currentUserData = user
                                
                                if (user != null) {
                                    val createdAt = user.createdAt?.toDate() ?: Date(0)
                                    val calendar = Calendar.getInstance()
                                    calendar.time = createdAt
                                    calendar.add(Calendar.DAY_OF_YEAR, appSettings.trialDays)
                                    val trialExpiryDate = calendar.time
                                    
                                    val now = Date()
                                    val isTrialActive = now.before(trialExpiryDate)
                                    val subscriptionExpiry = user.subscriptionExpiry?.toDate()
                                    val isSubscriptionActive = subscriptionExpiry != null && now.before(subscriptionExpiry)
                                    
                                    subscriptionStatus = isTrialActive || isSubscriptionActive
                                } else {
                                    subscriptionStatus = false
                                }
                            } else if (snapshot != null && !snapshot.exists()) {
                                if (!snapshot.metadata.isFromCache) {
                                    val newUser = User(userId = currentUserId, createdAt = Timestamp.now())
                                    userRef.set(newUser)
                                    currentUserData = newUser
                                    subscriptionStatus = true
                                }
                            }
                        }
                        onDispose { registration.remove() }
                    }
                }

                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    when {
                        !authInitialized -> LoadingBox()
                        currentUserId.isEmpty() -> AuthScreen(onAuthSuccess = {})
                        subscriptionStatus == null -> LoadingBox()
                        subscriptionStatus == false -> ActivationScreen()
                        currentUserData?.isAppLockEnabled == true && isAppLocked -> {
                            AppLockScreen(
                                correctPin = currentUserData?.appLockPin ?: "1234",
                                isBiometricEnabled = currentUserData?.isBiometricEnabled ?: false,
                                onUnlock = { isAppLocked = false }
                            )
                        }
                        else -> MainNavigation(
                            currentScreen = currentScreen,
                            selectedCustomer = selectedCustomer,
                            userData = currentUserData,
                            appSettings = appSettings,
                            onNavigate = { screen, customer ->
                                currentScreen = screen
                                selectedCustomer = customer
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LoadingBox() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun MainNavigation(
    currentScreen: String,
    selectedCustomer: Customer?,
    userData: User?,
    appSettings: AppSettings,
    onNavigate: (String, Customer?) -> Unit
) {
    val currency = userData?.defaultCurrency ?: stringResource(R.string.currency_default)
    
    when (currentScreen) {
        "list" -> CustomerListScreen(
            onAddCustomerClick = { onNavigate("add", null) },
            onEditCustomerClick = { onNavigate("edit", it) },
            onCustomerClick = { onNavigate("details", it) },
            onDailyReportClick = { onNavigate("daily_report", null) },
            onSettingsClick = { onNavigate("settings", null) },
            currency = currency,
            showVoiceInstructions = userData?.showVoiceInstructions ?: true,
            onDisableInstructions = { show ->
                userData?.userId?.let { uid ->
                    FirebaseFirestore.getInstance().collection("users").document(uid)
                        .update("showVoiceInstructions", show)
                }
            }
        )
        "add" -> AddCustomerScreen(onBack = { onNavigate("list", null) }, currency = currency)
        "edit" -> selectedCustomer?.let {
            EditCustomerScreen(customer = it, onBack = { onNavigate("list", null) }, currency = currency)
        }
        "details" -> selectedCustomer?.let {
            CustomerDetailsScreen(customer = it, onBack = { onNavigate("list", null) }, currency = currency, user = userData)
        }
        "daily_report" -> DailyReportScreen(onBack = { onNavigate("list", null) }, currency = currency)
        "settings" -> SettingsScreen(
            user = userData,
            appSettings = appSettings,
            onBack = { onNavigate("list", null) },
            onAdminClick = { onNavigate("admin", null) }
        )
        "admin" -> AdminPanelScreen(onBack = { onNavigate("settings", null) })
    }
}
