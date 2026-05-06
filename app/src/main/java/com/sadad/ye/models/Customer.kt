package com.sadad.ye.models

import com.google.firebase.firestore.PropertyName

/**
 * تمثل العميل الذي لديه ديون أو مدفوعات
 */
data class Customer(
    @get:PropertyName("customerId") @set:PropertyName("customerId")
    var customerId: String = "",
    
    @get:PropertyName("name") @set:PropertyName("name")
    var name: String = "",
    
    @get:PropertyName("phoneNumber") @set:PropertyName("phoneNumber")
    var phoneNumber: String = "",
    
    @get:PropertyName("userId") @set:PropertyName("userId")
    var userId: String = "",
    
    @get:PropertyName("debtLimit") @set:PropertyName("debtLimit")
    var debtLimit: Double = 0.0
)
