package com.sadad.ye.models

import com.google.firebase.Timestamp

/**
 * تمثل كود تفعيل لتمديد الاشتراك
 */
data class ActivationCode(
    val code: String = "",            // الكود الفريد
    val durationDays: Int = 30,       // مدة الاشتراك بالأيام (مثلاً 30 يوم)
    val isUsed: Boolean = false,      // هل تم استخدامه؟
    val usedBy: String = "",          // معرف المستخدم الذي استعمل الكود
    val expiryDate: Timestamp? = null // تاريخ انتهاء الكود (صلاحية الكود نفسه)
)
