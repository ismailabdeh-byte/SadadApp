package com.sadad.ye.models

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName

@Entity(tableName = "users")
data class User(
    @PrimaryKey
    @get:PropertyName("userId") @set:PropertyName("userId")
    var userId: String = "",
    
    @get:PropertyName("name") @set:PropertyName("name")
    var name: String = "",
    
    @get:PropertyName("nameEn") @set:PropertyName("nameEn")
    var nameEn: String = "",
    
    @get:PropertyName("businessAddress") @set:PropertyName("businessAddress")
    var businessAddress: String = "",
    
    @get:PropertyName("businessAddressEn") @set:PropertyName("businessAddressEn")
    var businessAddressEn: String = "",
    
    @get:PropertyName("phoneNumber") @set:PropertyName("phoneNumber")
    var phoneNumber: String = "",

    @get:PropertyName("logoUri") @set:PropertyName("logoUri")
    var logoUri: String = "",
    
    @get:PropertyName("createdAtLong") @set:PropertyName("createdAtLong")
    var createdAtLong: Long? = null,
    
    @get:PropertyName("subscriptionExpiryLong") @set:PropertyName("subscriptionExpiryLong")
    var subscriptionExpiryLong: Long? = null,
    
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
    var debtNotificationEnabled: Boolean = true,

    @get:PropertyName("whatsappReminderTemplate") @set:PropertyName("whatsappReminderTemplate")
    var whatsappReminderTemplate: String = "عزيزي {name}، نود إحاطتكم بأن حسابكم قد قارب على تجاوز السقف المسموح به. رصيدكم الحالي هو: {balance}. يرجى التكرم بالسداد لضمان استمرار الخدمة.",

    @get:PropertyName("isDarkMode") @set:PropertyName("isDarkMode")
    var isDarkMode: Boolean? = null,

    @get:Exclude
    var isSynced: Boolean = true
) {
    // وظائف مساعدة لاستقبال البيانات من Firebase بمسميات مختلفة (يتم تجاهلها في Room)
    @Ignore @get:Exclude
    var uid: String? = null
        @PropertyName("uid") set(value) { if (userId.isEmpty() && !value.isNullOrEmpty()) userId = value }

    @Ignore @get:Exclude
    var phone: String? = null
        @PropertyName("phone") set(value) { if (phoneNumber.isEmpty() && !value.isNullOrEmpty()) phoneNumber = value }

    @Ignore @get:Exclude
    var storeName: String? = null
        @PropertyName("storeName") set(value) { if (name.isEmpty() && !value.isNullOrEmpty()) name = value }
    
    @Ignore @get:Exclude
    var store_name: String? = null
        @PropertyName("store_name") set(value) { if (name.isEmpty() && !value.isNullOrEmpty()) name = value }

    // تحويل التوقيت
    @get:Exclude @set:Exclude
    var createdAt: Timestamp?
        get() = createdAtLong?.let { Timestamp(it / 1000, ((it % 1000) * 1000000).toInt()) }
        set(value) { createdAtLong = value?.toDate()?.time }

    @get:Exclude @set:Exclude
    var subscriptionExpiry: Timestamp?
        get() = subscriptionExpiryLong?.let { Timestamp(it / 1000, ((it % 1000) * 1000000).toInt()) }
        set(value) { subscriptionExpiryLong = value?.toDate()?.time }
}
