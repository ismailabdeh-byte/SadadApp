package com.sadad.ye.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

/**
 * تمثل كود تفعيل لتمديد الاشتراك
 */
data class ActivationCode(
    @get:PropertyName("code") @set:PropertyName("code")
    var code: String = "",
    
    @get:PropertyName("durationDays") @set:PropertyName("durationDays")
    var durationDays: Int = 30,
    
    @get:PropertyName("isUsed") @set:PropertyName("isUsed")
    var isUsed: Boolean = false,
    
    @get:PropertyName("usedBy") @set:PropertyName("usedBy")
    var usedBy: String = "",
    
    @get:PropertyName("createdAt") @set:PropertyName("createdAt")
    var createdAt: Timestamp? = null,
    
    @get:PropertyName("usedAt") @set:PropertyName("usedAt")
    var usedAt: Timestamp? = null
)
