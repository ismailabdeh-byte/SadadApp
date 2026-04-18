package com.sadad.ye.models

import com.google.firebase.Timestamp

/**
 * تمثل مستخدم التطبيق (التاجر أو صاحب المحل)
 */
data class User(
    val userId: String = "",          // معرف المستخدم الفريد
    val name: String = "",            // اسم المستخدم
    val subscriptionExpiry: Timestamp? = null // تاريخ انتهاء الاشتراك
)
