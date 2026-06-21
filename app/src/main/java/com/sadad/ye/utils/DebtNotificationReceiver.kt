package com.sadad.ye.utils

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.sadad.ye.MainActivity

class DebtNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val customerName = intent.getStringExtra("customerName") ?: "عميل"
        val customerId = intent.getStringExtra("customerId") ?: ""
        val balance = intent.getDoubleExtra("balance", 0.0)

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("targetScreen", "details")
            putExtra("customerId", customerId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context, 
            customerId.hashCode(), 
            mainIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NotificationUtils.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // يفضل استخدام أيقونة التطبيق
            .setContentTitle("🔔 تنبيه مديونية: $customerName")
            .setContentText("الرصيد وصل إلى ${String.format("%.0f", balance)} ريال. اضغط للمتابعة.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(customerId.hashCode(), notification)

        // إعادة جدولة التنبيه لليوم التالي (تكرار يومي)
        // ملاحظة: نحتاج هنا لاسترجاع بيانات العميل الكاملة لإعادة الجدولة بدقة
        // لكن للتبسيط، يمكن للمستخدم إعادة الجدولة عند فتح التطبيق.
    }
}
