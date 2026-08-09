package com.sadad.ye

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
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
import com.sadad.ye.utils.BackupUtils
import com.sadad.ye.utils.NotificationUtils
import kotlinx.coroutines.launch
import java.util.*

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationUtils.createNotificationChannel(this)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val auth = remember { FirebaseAuth.getInstance() }
            val db = remember { FirebaseFirestore.getInstance() }
            
            var authInitialized by remember { mutableStateOf(false) }
            var currentUserId by remember { mutableStateOf(auth.currentUser?.uid ?: "") }
            var currentUserData by remember { mutableStateOf<User?>(null) }
            var appSettings by remember { mutableStateOf(AppSettings()) }

            val useDarkTheme = currentUserData?.isDarkMode ?: isSystemInDarkTheme()
            val layoutDirection = if (AppCompatDelegate.getApplicationLocales().toLanguageTags().contains("en")) LayoutDirection.Ltr else LayoutDirection.Rtl

            سدادTheme(darkTheme = useDarkTheme) {
                // نضع Provider هنا لضمان أن كل المكونات بما فيها Drawer تحترم الاتجاه
                CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                    val scope = rememberCoroutineScope()

                    var isAppLocked by remember { mutableStateOf(true) }
                    var currentScreen by remember { mutableStateOf("list") }
                    var selectedCustomer by remember { mutableStateOf<Customer?>(null) }

                    DisposableEffect(auth) {
                        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                            val newId = firebaseAuth.currentUser?.uid ?: ""
                            if (newId != currentUserId) {
                                currentUserId = newId
                                if (newId.isEmpty()) {
                                    currentUserData = null
                                    currentScreen = "list"
                                    isAppLocked = true
                                }
                            }
                        }
                        auth.addAuthStateListener(listener)
                        onDispose { auth.removeAuthStateListener(listener) }
                    }

                    if (drawerState.isOpen) {
                        BackHandler { scope.launch { drawerState.close() } }
                    }

                    LaunchedEffect(currentUserId) {
                        if (currentUserId.isNotEmpty()) {
                            db.collection("users").document(currentUserId).addSnapshotListener { snapshot, _ ->
                                if (snapshot != null && snapshot.exists()) currentUserData = snapshot.toObject(User::class.java)
                            }
                        }
                    }

                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        gesturesEnabled = !isAppLocked && currentUserId.isNotEmpty(),
                        drawerContent = {
                            ModalDrawerSheet(
                                modifier = Modifier.fillMaxWidth(0.75f),
                                drawerContainerColor = MaterialTheme.colorScheme.surface,
                                drawerTonalElevation = 2.dp
                            ) {
                                DrawerHeader(user = currentUserData, email = auth.currentUser?.email ?: "")
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                var showLanguageDialog by remember { mutableStateOf(false) }

                                DrawerItem(
                                    icon = Icons.Default.Language,
                                    label = stringResource(R.string.language),
                                    subtitle = if (layoutDirection == LayoutDirection.Ltr) stringResource(R.string.english) else stringResource(R.string.arabic),
                                    onClick = { showLanguageDialog = true }
                                )

                                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                                // صف الوضع الليلي مع إغلاق تلقائي
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { 
                                            scope.launch { drawerState.close() } // نغلق اللوحة فوراً
                                            val newDark = !(currentUserData?.isDarkMode ?: false)
                                            auth.currentUser?.uid?.let { db.collection("users").document(it).update("isDarkMode", newDark) }
                                        }
                                        .padding(horizontal = 24.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Brightness4, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(22.dp))
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(stringResource(R.string.dark_mode), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                    Switch(
                                        checked = currentUserData?.isDarkMode == true,
                                        onCheckedChange = { isChecked ->
                                            scope.launch { drawerState.close() }
                                            auth.currentUser?.uid?.let { uid -> db.collection("users").document(uid).update("isDarkMode", isChecked) }
                                        },
                                        modifier = Modifier.scale(0.8f)
                                    )
                                }

                                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                                DrawerItem(
                                    icon = Icons.AutoMirrored.Filled.List,
                                    label = stringResource(R.string.daily_report),
                                    onClick = { 
                                        scope.launch { drawerState.close() }
                                        currentScreen = "daily_report" 
                                    }
                                )

                                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                                DrawerItem(
                                    icon = Icons.Default.CloudUpload,
                                    label = stringResource(R.string.data_backup),
                                    onClick = { 
                                        scope.launch { drawerState.close() }
                                        currentScreen = "settings" 
                                    }
                                )

                                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                                if (currentUserData?.isAdmin == true) {
                                    DrawerItem(
                                        icon = Icons.Default.AdminPanelSettings,
                                        label = stringResource(R.string.admin_panel),
                                        onClick = { 
                                            scope.launch { drawerState.close() }
                                            currentScreen = "admin" 
                                        }
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                                }

                                DrawerItem(
                                    icon = Icons.Default.Settings,
                                    label = stringResource(R.string.settings_title),
                                    onClick = { 
                                        scope.launch { drawerState.close() }
                                        currentScreen = "settings" 
                                    }
                                )

                                Spacer(modifier = Modifier.weight(1f))

                                DrawerItem(
                                    icon = Icons.AutoMirrored.Filled.ExitToApp,
                                    label = stringResource(R.string.logout),
                                    onClick = { 
                                        scope.launch { drawerState.close() }
                                        auth.signOut() 
                                    },
                                    color = MaterialTheme.colorScheme.error
                                )
                                
                                Text(
                                    text = "سداد - v1.0",
                                    modifier = Modifier.padding(24.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                                )

                                if (showLanguageDialog) {
                                    LanguageSelectionDialog(onDismiss = { 
                                        showLanguageDialog = false 
                                        scope.launch { drawerState.close() }
                                    })
                                }
                            }
                        }
                    ) {
                        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            when {
                                currentUserId.isEmpty() -> AuthScreen(onAuthSuccess = { currentUserId = auth.currentUser?.uid ?: "" })
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
                                    },
                                    onOpenDrawer = { scope.launch { drawerState.open() } }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DrawerHeader(user: User?, email: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(top = 48.dp, start = 24.dp, end = 24.dp, bottom = 24.dp)
    ) {
        Column {
            Surface(modifier = Modifier.size(60.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                Box(contentAlignment = Alignment.Center) {
                    Text((user?.name?.take(1) ?: "U").uppercase(), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(user?.name ?: stringResource(R.string.default_user_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun DrawerItem(icon: ImageVector, label: String, subtitle: String? = null, onClick: () -> Unit, color: Color = MaterialTheme.colorScheme.onSurface) {
    NavigationDrawerItem(
        icon = { Icon(icon, null, tint = if (color == MaterialTheme.colorScheme.onSurface) MaterialTheme.colorScheme.secondary else color, modifier = Modifier.size(22.dp)) },
        label = {
            Column {
                Text(label, color = color, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyLarge)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        },
        selected = false,
        onClick = onClick,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )
}

@Composable
fun LanguageSelectionDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_language)) },
        text = {
            Column {
                LanguageOption(stringResource(R.string.arabic), !AppCompatDelegate.getApplicationLocales().toLanguageTags().contains("en")) {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ar"))
                    onDismiss()
                }
                LanguageOption(stringResource(R.string.english), AppCompatDelegate.getApplicationLocales().toLanguageTags().contains("en")) {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
                    onDismiss()
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
fun LanguageOption(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, null)
        Spacer(modifier = Modifier.width(16.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge)
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
    onNavigate: (String, Customer?) -> Unit,
    onOpenDrawer: () -> Unit
) {
    val currency = userData?.defaultCurrency ?: stringResource(R.string.currency_default)
    when (currentScreen) {
        "list" -> CustomerListScreen(
            onAddCustomerClick = { onNavigate("add", null) },
            onEditCustomerClick = { onNavigate("edit", it) },
            onCustomerClick = { onNavigate("details", it) },
            onDailyReportClick = {}, 
            onSettingsClick = onOpenDrawer,
            currency = currency,
            showVoiceInstructions = userData?.showVoiceInstructions ?: true,
            onDisableInstructions = { show ->
                userData?.userId?.let { uid ->
                    FirebaseFirestore.getInstance().collection("users").document(uid).update("showVoiceInstructions", show)
                }
            }
        )
        "add" -> AddCustomerScreen({ onNavigate("list", null) }, currency)
        "edit" -> selectedCustomer?.let { EditCustomerScreen(it, { onNavigate("list", null) }, currency) }
        "details" -> selectedCustomer?.let { CustomerDetailsScreen(it, { onNavigate("list", null) }, currency, userData) }
        "daily_report" -> DailyReportScreen({ onNavigate("list", null) }, currency)
        "settings" -> SettingsScreen(userData, appSettings, { onNavigate("list", null) }, { onNavigate("admin", null) })
        "admin" -> AdminPanelScreen { onNavigate("settings", null) }
    }
}
