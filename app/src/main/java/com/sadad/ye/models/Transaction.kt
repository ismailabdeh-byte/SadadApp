package com.sadad.ye.models

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName

/**
 * تمثل عملية مالية (دين أو سداد)
 */
data class Transaction(
    val transactionId: String = "",
    val customerId: String = "",
    val amount: Double = 0.0,
    val note: String = "",
    val date: Long = System.currentTimeMillis(),
    
    @get:PropertyName("debt")
    @set:PropertyName("debt")
    var debt: Boolean = true, // true للدين، false للسداد
    
    val sent: Boolean = false // حالة الإرسال للعميل
) {
    // نستخدم getter لضمان التوافق مع الكود في الشاشات
    @get:Exclude
    val isDebt: Boolean get() = debt
}
