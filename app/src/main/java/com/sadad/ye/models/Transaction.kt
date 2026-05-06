package com.sadad.ye.models

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName

/**
 * تمثل عملية مالية (دين أو سداد)
 */
data class Transaction(
    @get:PropertyName("transactionId") @set:PropertyName("transactionId")
    var transactionId: String = "",
    
    @get:PropertyName("customerId") @set:PropertyName("customerId")
    var customerId: String = "",
    
    @get:PropertyName("userId") @set:PropertyName("userId")
    var userId: String = "", // أضفنا هذا الحقل لتسريع التقارير
    
    @get:PropertyName("amount") @set:PropertyName("amount")
    var amount: Double = 0.0,
    
    @get:PropertyName("note") @set:PropertyName("note")
    var note: String = "",
    
    @get:PropertyName("date") @set:PropertyName("date")
    var date: Long = System.currentTimeMillis(),
    
    @get:PropertyName("debt") @set:PropertyName("debt")
    var debt: Boolean = true,
    
    @get:PropertyName("sent") @set:PropertyName("sent")
    var sent: Boolean = false
) {
    @get:Exclude
    val isDebt: Boolean get() = debt
}
