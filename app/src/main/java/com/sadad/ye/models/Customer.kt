package com.sadad.ye.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName

/**
 * تمثل العميل الذي لديه ديون أو مدفوعات
 */
@Entity(tableName = "customers")
data class Customer(
    @PrimaryKey
    @get:PropertyName("customerId") @set:PropertyName("customerId")
    var customerId: String = "",
    
    @get:PropertyName("name") @set:PropertyName("name")
    var name: String = "",
    
    @get:PropertyName("phoneNumber") @set:PropertyName("phoneNumber")
    var phoneNumber: String = "",
    
    @get:PropertyName("userId") @set:PropertyName("userId")
    var userId: String = "",
    
    @get:PropertyName("debtLimit") @set:PropertyName("debtLimit")
    var debtLimit: Double = 0.0,

    @get:PropertyName("currency") @set:PropertyName("currency")
    var currency: String = "", // العملة المخصصة للعميل

    @get:Exclude
    var isSynced: Boolean = true, // افتراضياً true للبيانات القديمة، false للجديدة

    @get:Exclude
    var lastUpdated: Long = System.currentTimeMillis()
)
