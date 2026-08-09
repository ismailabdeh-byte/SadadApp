package com.sadad.ye.ui

import android.app.Activity
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.sadad.ye.R
import com.sadad.ye.models.User
import kotlinx.coroutines.tasks.await

@Composable
fun AuthScreen(onAuthSuccess: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var showForgotPassword by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var isAgreed by remember { mutableStateOf(false) }

    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current

    // Google Sign In Configuration
    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
    }
    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                isLoading = true
                auth.signInWithCredential(credential).addOnCompleteListener { authResult ->
                    if (authResult.isSuccessful) {
                        val firebaseUser = auth.currentUser
                        val userRef = db.collection("users").document(firebaseUser?.uid ?: "")
                        
                        userRef.get().addOnSuccessListener { doc ->
                            if (!doc.exists()) {
                                // إنشاء سجل مستخدم جديد متوافق مع الموديل User
                                val newUser = User(
                                    userId = firebaseUser?.uid ?: "",
                                    name = firebaseUser?.displayName ?: "",
                                    phoneNumber = firebaseUser?.phoneNumber ?: "",
                                    createdAt = com.google.firebase.Timestamp.now()
                                )
                                userRef.set(newUser).addOnCompleteListener { onAuthSuccess() }
                            } else {
                                onAuthSuccess()
                            }
                        }
                    } else {
                        Toast.makeText(context, context.getString(R.string.login_failed), Toast.LENGTH_LONG).show()
                        isLoading = false
                    }
                }
            } catch (e: ApiException) {
                Toast.makeText(context, "${context.getString(R.string.error_occurred)}: ${e.statusCode}", Toast.LENGTH_LONG).show()
                isLoading = false
            }
        } else {
            isLoading = false
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (showForgotPassword) stringResource(R.string.password_reset_title) else if (isSignUp) stringResource(R.string.create_account_title) else stringResource(R.string.login_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (!showForgotPassword) {
                if (isSignUp) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.store_user_name)) },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text(stringResource(R.string.phone_number)) },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.email_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.password_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(imageVector = image, contentDescription = null)
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true
                )

                if (isSignUp) {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text(stringResource(R.string.confirm_password_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isAgreed,
                            onCheckedChange = { isAgreed = it }
                        )
                        Text(
                            text = stringResource(R.string.agree_to_terms),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (email.isEmpty() || password.isEmpty() || (isSignUp && (name.isEmpty() || phone.isEmpty() || confirmPassword.isEmpty()))) {
                            Toast.makeText(context, context.getString(R.string.fill_all_data), Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (isSignUp && !isAgreed) {
                            Toast.makeText(context, context.getString(R.string.must_agree_to_terms), Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (isSignUp && password != confirmPassword) {
                            Toast.makeText(context, context.getString(R.string.passwords_not_matching), Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        isLoading = true
                        if (isSignUp) {
                            auth.createUserWithEmailAndPassword(email, password)
                                .addOnCompleteListener { task ->
                                    if (task.isSuccessful) {
                                        val firebaseUser = auth.currentUser
                                        firebaseUser?.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name).build())
                                        
                                        val newUser = User(
                                            userId = firebaseUser?.uid ?: "",
                                            name = name,
                                            phoneNumber = phone,
                                            createdAt = com.google.firebase.Timestamp.now()
                                        )
                                        db.collection("users").document(firebaseUser?.uid ?: "").set(newUser)
                                        onAuthSuccess()
                                    } else {
                                        Toast.makeText(context, "${context.getString(R.string.error_occurred)}: ${task.exception?.localizedMessage}", Toast.LENGTH_LONG).show()
                                        isLoading = false
                                    }
                                }
                        } else {
                            auth.signInWithEmailAndPassword(email, password)
                                .addOnCompleteListener { task ->
                                    if (task.isSuccessful) {
                                        onAuthSuccess()
                                    } else {
                                        Toast.makeText(context, context.getString(R.string.login_failed), Toast.LENGTH_SHORT).show()
                                        isLoading = false
                                    }
                                }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) {
                    if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    else Text(if (isSignUp) stringResource(R.string.add) else stringResource(R.string.login_title))
                }

                if (!isSignUp) {
                    TextButton(onClick = { showForgotPassword = true }) {
                        Text(stringResource(R.string.forgot_password_q))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HorizontalDivider(modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.or), modifier = Modifier.padding(horizontal = 8.dp), color = Color.Gray)
                    HorizontalDivider(modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = { 
                        isLoading = true
                        googleLauncher.launch(googleSignInClient.signInIntent) 
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.google_login))
                }

                TextButton(onClick = { 
                    isSignUp = !isSignUp
                    password = ""
                    confirmPassword = ""
                }) {
                    Text(if (isSignUp) stringResource(R.string.have_account) else stringResource(R.string.no_account))
                }
            } else {
                // استعادة كلمة المرور
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.enter_email)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        if (email.isNotEmpty()) {
                            auth.sendPasswordResetEmail(email).addOnCompleteListener {
                                if (it.isSuccessful) {
                                    Toast.makeText(context, context.getString(R.string.reset_link_sent), Toast.LENGTH_LONG).show()
                                    showForgotPassword = false
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.send_reset_link))
                }
                TextButton(onClick = { showForgotPassword = false }) {
                    Text(stringResource(R.string.back_to_login))
                }
            }
        }
    }
}
