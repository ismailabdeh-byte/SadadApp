package com.sadad.ye.models

import com.google.firebase.firestore.PropertyName

/**
 * تمثل إعدادات التطبيق العامة التي يمكن للمدير التحكم بها سحابياً
 */
data class AppSettings(
    @get:PropertyName("activationTitle") @set:PropertyName("activationTitle")
    var activationTitle: String = "تفعيل التطبيق",
    
    @get:PropertyName("activationDescription") @set:PropertyName("activationDescription")
    var activationDescription: String = "يرجى الاشتراك للاستمرار في استخدام التطبيق والحفاظ على بياناتك ومزامنتها سحابياً.",
    
    @get:PropertyName("paymentMethods") @set:PropertyName("paymentMethods")
    var paymentMethods: String = "• بنك الكريمي (حساب): 12345678\n• محفظة جوالي: 777111222\n• إم فلوس: 733444555",
    
    @get:PropertyName("contactInfo") @set:PropertyName("contactInfo")
    var contactInfo: String = "بعد التحويل، أرسل صورة الحوالة للرقم 777000000 لاستلام كود التفعيل.",

    @get:PropertyName("supportPhone") @set:PropertyName("supportPhone")
    var supportPhone: String = "967770000000", // رقم الدعم الفني

    @get:PropertyName("trialDays") @set:PropertyName("trialDays")
    var trialDays: Int = 1 // مدة الفترة التجريبية (افتراضياً يوم واحد)
)
