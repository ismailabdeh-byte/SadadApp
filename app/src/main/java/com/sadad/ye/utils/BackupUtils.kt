package com.sadad.ye.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.sadad.ye.R
import com.sadad.ye.models.Customer
import com.sadad.ye.models.Transaction
import com.sadad.ye.models.User
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object BackupUtils {

    fun generateBackupJson(user: User?, customers: List<Customer>, transactions: List<Transaction>): String {
        val root = JSONObject()
        
        // 1. بيانات المستخدم
        val userJson = JSONObject().apply {
            put("name", user?.name)
            put("businessAddress", user?.businessAddress)
            put("phoneNumber", user?.phoneNumber)
            put("defaultCurrency", user?.defaultCurrency)
        }
        root.put("user", userJson)

        // 2. قائمة العملاء
        val customersArray = JSONArray()
        customers.forEach { customer ->
            val cJson = JSONObject().apply {
                put("customerId", customer.customerId)
                put("name", customer.name)
                put("phoneNumber", customer.phoneNumber)
                put("debtLimit", customer.debtLimit)
            }
            customersArray.put(cJson)
        }
        root.put("customers", customersArray)

        // 3. العمليات
        val transactionsArray = JSONArray()
        transactions.forEach { trans ->
            val tJson = JSONObject().apply {
                put("transactionId", trans.transactionId)
                put("customerId", trans.customerId)
                put("amount", trans.amount)
                put("note", trans.note)
                put("date", trans.date)
                put("debt", trans.debt)
                put("sent", trans.sent)
            }
            transactionsArray.put(tJson)
        }
        root.put("transactions", transactionsArray)

        return root.toString(4)
    }

    fun exportBackup(context: Context, user: User?, customers: List<Customer>, transactions: List<Transaction>) {
        try {
            val root = JSONObject()
            
            // 1. بيانات المستخدم
            val userJson = JSONObject().apply {
                put("name", user?.name)
                put("businessAddress", user?.businessAddress)
                put("phoneNumber", user?.phoneNumber)
                put("defaultCurrency", user?.defaultCurrency)
            }
            root.put("user", userJson)

            // 2. قائمة العملاء
            val customersArray = JSONArray()
            customers.forEach { customer ->
                val cJson = JSONObject().apply {
                    put("customerId", customer.customerId)
                    put("name", customer.name)
                    put("phoneNumber", customer.phoneNumber)
                    put("debtLimit", customer.debtLimit)
                }
                customersArray.put(cJson)
            }
            root.put("customers", customersArray)

            // 3. العمليات
            val transactionsArray = JSONArray()
            transactions.forEach { trans ->
                val tJson = JSONObject().apply {
                    put("transactionId", trans.transactionId)
                    put("customerId", trans.customerId)
                    put("amount", trans.amount)
                    put("note", trans.note)
                    put("date", trans.date)
                    put("debt", trans.debt)
                    put("sent", trans.sent)
                }
                transactionsArray.put(tJson)
            }
            root.put("transactions", transactionsArray)

            // 4. إنشاء الملف
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val prefix = context.getString(R.string.backup_file_prefix)
            val fileName = "$prefix$timeStamp.json"
            val file = File(context.cacheDir, fileName)
            file.writeText(root.toString(4))

            // 5. مشاركة الملف
            val contentUri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_backup_title)))

        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.backup_export_error, e.message), Toast.LENGTH_LONG).show()
        }
    }

    fun importBackup(context: Context, uri: Uri, onComplete: (Boolean, String) -> Unit) {
        val db = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val userId = auth.currentUser?.uid ?: return

        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val jsonString = inputStream?.bufferedReader().use { it?.readText() } ?: ""
            val root = JSONObject(jsonString)

            // 1. استيراد العملاء
            val customersArray = root.getJSONArray("customers")
            val batch = db.batch()
            
            for (i in 0 until customersArray.length()) {
                val cJson = customersArray.getJSONObject(i)
                val customerId = cJson.getString("customerId")
                val customerMap = mapOf(
                    "customerId" to customerId,
                    "name" to cJson.getString("name"),
                    "phoneNumber" to cJson.getString("phoneNumber"),
                    "debtLimit" to cJson.getDouble("debtLimit"),
                    "userId" to userId
                )
                batch.set(db.collection("customers").document(customerId), customerMap)
            }

            // 2. استيراد العمليات
            val transactionsArray = root.getJSONArray("transactions")
            for (i in 0 until transactionsArray.length()) {
                val tJson = transactionsArray.getJSONObject(i)
                val transactionId = tJson.getString("transactionId")
                val transMap = mapOf(
                    "transactionId" to transactionId,
                    "customerId" to tJson.getString("customerId"),
                    "amount" to tJson.getDouble("amount"),
                    "note" to tJson.getString("note"),
                    "date" to tJson.getLong("date"),
                    "debt" to tJson.getBoolean("debt"),
                    "sent" to tJson.getBoolean("sent"),
                    "userId" to userId
                )
                batch.set(db.collection("transactions").document(transactionId), transMap)
            }

            batch.commit()
                .addOnSuccessListener { onComplete(true, context.getString(R.string.backup_success)) }
                .addOnFailureListener { exception -> onComplete(false, context.getString(R.string.backup_error, exception.message)) }

        } catch (e: Exception) {
            onComplete(false, context.getString(R.string.backup_read_error, e.message))
        }
    }
}
