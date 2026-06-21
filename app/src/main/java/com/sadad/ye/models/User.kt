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
    var isAdmin: Boolean = false,

    @get:PropertyName("showVoiceInstructions") @set:PropertyName("showVoiceInstructions")
    var showVoiceInstructions: Boolean = true,

    @get:PropertyName("isBiometricEnabled") @set:PropertyName("isBiometricEnabled")
    var isBiometricEnabled: Boolean = false,

    @get:PropertyName("debtNotificationEnabled") @set:PropertyName("debtNotificationEnabled")
    var debtNotificationEnabled: Boolean = true, // تفعيل تنبيهات المديونيات

    @get:PropertyName("whatsappReminderTemplate") @set:PropertyName("whatsappReminderTemplate")
    var whatsappReminderTemplate: String = "عزيزي {name}، نود إحاطتكم بأن حسابكم قد قارب على تجاوز السقف المسموح به. رصيدكم الحالي هو: {balance}. يرجى التكرم بالسداد لضمان استمرار الخدمة.",

    @get:PropertyName("isDarkMode") @set:PropertyName("isDarkMode")
    var isDarkMode: Boolean? = null // null means follow system
)
