package com.sadad.ye.utils

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

object AccountUtils {

    /**
     * دالة لحذف حساب المستخدم وكافة بياناته من قاعدة البيانات
     */
    fun deleteUserAccount(onComplete: (Boolean, String) -> Unit) {
        val auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()
        val user = auth.currentUser
        val userId = user?.uid

        if (userId == null) {
            onResult(onComplete, false, "المستخدم غير مسجل")
            return
        }

        // 1. جلب كافة عملاء المستخدم لحذف عملياتهم
        db.collection("customers").whereEqualTo("userId", userId).get()
            .addOnSuccessListener { customerDocs ->
                val batch = db.batch()
                val customerIds = customerDocs.map { it.id }

                // حذف مستند المستخدم
                batch.delete(db.collection("users").document(userId))

                // حذف العملاء
                customerDocs.forEach { batch.delete(it.reference) }

                // 2. حذف العمليات المرتبطة (يتم ذلك بشكل منفصل لضمان الوصول لكافة العمليات)
                if (customerIds.isNotEmpty()) {
                    db.collection("transactions").whereIn("customerId", customerIds).get()
                        .addOnSuccessListener { transDocs ->
                            val transBatch = db.batch()
                            transDocs.forEach { transBatch.delete(it.reference) }
                            
                            // تنفيذ حذف العمليات
                            transBatch.commit().addOnCompleteListener {
                                // 3. تنفيذ حذف المستخدم والعملاء
                                batch.commit().addOnCompleteListener {
                                    // 4. حذف الحساب من Authentication
                                    user.delete().addOnCompleteListener { task ->
                                        if (task.isSuccessful) {
                                            onResult(onComplete, true, "تم حذف الحساب وكافة البيانات بنجاح")
                                        } else {
                                            onResult(onComplete, false, "تم حذف البيانات، ولكن فشل حذف الحساب (قد يحتاج لإعادة تسجيل دخول حديثة)")
                                        }
                                    }
                                }
                            }
                        }
                } else {
                    // إذا لم يكن لديه عملاء، نحذف سجل المستخدم فقط
                    batch.commit().addOnCompleteListener {
                        user.delete().addOnCompleteListener { task ->
                            onResult(onComplete, task.isSuccessful, if (task.isSuccessful) "تم حذف الحساب" else "فشل حذف الحساب")
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                onResult(onComplete, false, "خطأ: ${e.localizedMessage}")
            }
    }

    private fun onResult(onComplete: (Boolean, String) -> Unit, success: Boolean, msg: String) {
        onComplete(success, msg)
    }
}
