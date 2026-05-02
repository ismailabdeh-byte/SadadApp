package com.sadad.ye.utils

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.sadad.ye.models.ActivationCode
import com.sadad.ye.models.User
import java.util.*

object SubscriptionUtils {

    /**
     * دالة موحدة لاستخدام كود تفعيل وتمديد الاشتراك
     */
    fun redeemCode(
        db: FirebaseFirestore,
        userId: String,
        code: String,
        onResult: (Boolean, String) -> Unit
    ) {
        if (userId.isEmpty()) {
            onResult(false, "يجب تسجيل الدخول أولاً")
            return
        }

        val codeRef = db.collection("activation_codes").document(code)
        
        codeRef.get().addOnSuccessListener { codeDoc ->
            val activationCode = codeDoc.toObject(ActivationCode::class.java)
            
            if (activationCode != null && !activationCode.isUsed) {
                // الكود صالح، نجلب بيانات المستخدم لمعرفة تاريخ انتهائه الحالي
                val userRef = db.collection("users").document(userId)
                userRef.get().addOnSuccessListener { userDoc ->
                    val user = userDoc.toObject(User::class.java)
                    val currentExpiry = user?.subscriptionExpiry?.toDate()
                    val now = Date()
                    
                    val calendar = Calendar.getInstance()
                    // إذا كان لديه اشتراك ساري، نضيف الأيام فوقه، وإلا نبدأ من الآن
                    if (currentExpiry != null && currentExpiry.after(now)) {
                        calendar.time = currentExpiry
                    } else {
                        calendar.time = now
                    }
                    
                    calendar.add(Calendar.DAY_OF_YEAR, activationCode.durationDays)
                    val newExpiry = Timestamp(calendar.time)
                    
                    val batch = db.batch()
                    batch.update(userRef, "subscriptionExpiry", newExpiry)
                    batch.update(codeRef, mapOf(
                        "isUsed" to true,
                        "usedBy" to userId,
                        "usedAt" to Timestamp.now()
                    ))
                    
                    batch.commit().addOnSuccessListener {
                        onResult(true, "تم تفعيل الاشتراك لمدة ${activationCode.durationDays} يوم بنجاح!")
                    }.addOnFailureListener {
                        onResult(false, "فشل في تحديث بيانات الاشتراك")
                    }
                }
            } else {
                onResult(false, "الكود غير صحيح أو مستخدم مسبقاً")
            }
        }.addOnFailureListener {
            onResult(false, "خطأ في الاتصال بالسيرفر")
        }
    }
}
