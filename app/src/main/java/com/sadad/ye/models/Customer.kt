package com.sadad.ye.models

/**
 * تمثل العميل الذي لديه ديون أو مدفوعات
 */
data class Customer(
    val customerId: String = "",      // معرف العميل الفريد
    val name: String = "",            // اسم العميل
    val phoneNumber: String = "",     // رقم الجوال
    val userId: String = "",          // معرف المستخدم (صاحب الحساب) الذي يتبعه هذا العميل
    val debtLimit: Double = 0.0       // سقف المديونية (0.0 تعني بدون سقف)
)
