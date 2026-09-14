package com.sadad.ye.data

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.sadad.ye.data.local.AppDatabase
import com.sadad.ye.models.Customer
import com.sadad.ye.models.Transaction
import com.sadad.ye.models.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import java.util.*

class DataRepository(context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val customerDao = db.customerDao()
    private val transactionDao = db.transactionDao()
    private val userDao = db.userDao()
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // --- User ---
    fun getUser(userId: String): Flow<User?> = userDao.getUser(userId)
    
    suspend fun saveUserLocally(user: User) = userDao.insertUser(user)

    // --- Customers ---
    fun getCustomers(userId: String): Flow<List<Customer>> = customerDao.getCustomers(userId)
    
    suspend fun addCustomer(customer: Customer) {
        val newCustomer = customer.copy(isSynced = false)
        customerDao.insertCustomer(newCustomer)
    }

    suspend fun updateCustomer(customer: Customer) {
        val updatedCustomer = customer.copy(isSynced = false, lastUpdated = System.currentTimeMillis())
        customerDao.updateCustomer(updatedCustomer)
    }

    suspend fun deleteCustomer(customer: Customer) {
        // حذف العمليات المرتبطة محلياً أولاً
        transactionDao.deleteTransactionsByCustomer(customer.customerId)
        customerDao.deleteCustomer(customer)
        
        // سنحتاج لإعلام Firebase بالحذف في المزامنة القادمة أو الحذف المباشر إذا كان هناك إنترنت
        // حالياً سنحاول الحذف مباشرة من Cloud للتبسيط أو ننتظر المزامنة
        try {
            firestore.collection("customers").document(customer.customerId).delete().await()
            firestore.collection("transactions").whereEqualTo("customerId", customer.customerId).get().await().forEach { 
                it.reference.delete()
            }
        } catch (e: Exception) {}
    }

    // --- Transactions ---
    fun getTransactions(userId: String): Flow<List<Transaction>> = transactionDao.getTransactions(userId)

    suspend fun addTransaction(transaction: Transaction) {
        val newTrans = transaction.copy(isSynced = false)
        transactionDao.insertTransaction(newTrans)
    }

    suspend fun deleteTransactionsByCustomer(customerId: String) {
        transactionDao.deleteTransactionsByCustomer(customerId)
        // سيتم الحذف من السحاب في المزامنة القادمة أو الحذف المباشر
        try {
            firestore.collection("transactions").whereEqualTo("customerId", customerId).get().await().forEach { 
                it.reference.delete()
            }
        } catch (e: Exception) {}
    }

    suspend fun deleteTransaction(transaction: Transaction) {
        transactionDao.deleteTransaction(transaction)
        try {
            firestore.collection("transactions").document(transaction.transactionId).delete().await()
        } catch (e: Exception) {}
    }

    // --- Sync ---
    suspend fun syncWithCloud(forceDownload: Boolean = false) {
        val userId = auth.currentUser?.uid ?: return
        
        // 0. رفع بيانات المستخدم
        val unsyncedUsers = userDao.getUnsyncedUsers()
        if (unsyncedUsers.isNotEmpty()) {
            val batch = firestore.batch()
            unsyncedUsers.forEach {
                batch.set(firestore.collection("users").document(it.userId), it.apply { isSynced = true })
            }
            batch.commit().await()
            unsyncedUsers.forEach { userDao.markAsSynced(it.userId) }
        }

        // 1. رفع البيانات الجديدة من الهاتف إلى السحاب (Upload)
        val unsyncedCustomers = customerDao.getUnsyncedCustomers()
        if (unsyncedCustomers.isNotEmpty()) {
            val batch = firestore.batch()
            unsyncedCustomers.forEach {
                batch.set(firestore.collection("customers").document(it.customerId), it.apply { isSynced = true })
            }
            batch.commit().await()
            customerDao.markAsSynced(unsyncedCustomers.map { it.customerId })
        }

        val unsyncedTrans = transactionDao.getUnsyncedTransactions()
        if (unsyncedTrans.isNotEmpty()) {
            val batch = firestore.batch()
            unsyncedTrans.forEach {
                batch.set(firestore.collection("transactions").document(it.transactionId), it.apply { isSynced = true })
            }
            batch.commit().await()
            transactionDao.markAsSynced(unsyncedTrans.map { it.transactionId })
        }

        // 2. تنزيل البيانات من السحاب إلى الهاتف (Download)
        // نقوم بذلك فقط إذا كانت قاعدة البيانات المحلية فارغة أو إذا طلب المستخدم ذلك يدوياً
        if (forceDownload) {
            // تنزيل بيانات المستخدم وتحدثيها محلياً
            val userDoc = firestore.collection("users").document(userId).get().await()
            if (userDoc.exists()) {
                val cloudUser = userDoc.toObject(User::class.java)
                cloudUser?.let { 
                    if (it.userId.isEmpty()) it.userId = userDoc.id
                    it.isSynced = true
                    userDao.insertUser(it) 
                }
            }

            val customerDocs = firestore.collection("customers").whereEqualTo("userId", userId).get().await()
            customerDocs.toObjects(Customer::class.java).forEach { 
                it.isSynced = true
                customerDao.insertCustomer(it) 
            }

            val transDocs = firestore.collection("transactions").whereEqualTo("userId", userId).get().await()
            transDocs.toObjects(Transaction::class.java).forEach { 
                it.isSynced = true
                transactionDao.insertTransaction(it) 
            }
        }
    }

    suspend fun initialDownload() {
        val userId = auth.currentUser?.uid ?: return
        
        try {
            val customerDocs = firestore.collection("customers").whereEqualTo("userId", userId).get().await()
            val customers = customerDocs.toObjects(Customer::class.java)
            customers.forEach { 
                it.isSynced = true
                customerDao.insertCustomer(it) 
            }

            val transDocs = firestore.collection("transactions").whereEqualTo("userId", userId).get().await()
            val transactions = transDocs.toObjects(Transaction::class.java)
            transactions.forEach { 
                it.isSynced = true
                transactionDao.insertTransaction(it) 
            }
        } catch (e: Exception) {}
    }
}
