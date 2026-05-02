package com.sadad.ye.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

/**
 * تمثل مستخدم التطبيق (التاجر أو صاحب المحل)
 */
data class User(
    @get:PropertyName("userId") @set:PropertyName("userId")
    var userId: String = "",
    
    @get:PropertyName("name") @set:PropertyName("name")
    var name: String = "", // اسم المحل أو التاجر
    
    @get:PropertyName("businessAddress") @set:PropertyName("businessAddress")
    var businessAddress: String = "", // عنوان المحل
    
    @get:PropertyName("phoneNumber") @set:PropertyName("phoneNumber")
    var phoneNumber: String = "",
    
    @get:PropertyName("createdAt") @set:PropertyName("createdAt")
    var createdAt: Timestamp? = null,
    
    @get:PropertyName("subscriptionExpiry") @set:PropertyName("subscriptionExpiry")
    var subscriptionExpiry: Timestamp? = null,
    
    @get:PropertyName("defaultCurrency") @set:PropertyName("defaultCurrency")
    var defaultCurrency: String = "ريال يمني",
    
    @get:PropertyName("isAppLockEnabled") @set:PropertyName("isAppLockEnabled")
    var isAppLockEnabled: Boolean = false,
    
    @get:PropertyName("appLockPin") @set:PropertyName("appLockPin")
    var appLockPin: String = "1234",

    @get:PropertyName("isAdmin") @set:PropertyName("isAdmin")
    var isAdmin: Boolean = false // حقل جديد لتمييز المدير
)
