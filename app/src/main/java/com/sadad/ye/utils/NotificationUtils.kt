package com.sadad.ye.utils

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.sadad.ye.MainActivity
import com.sadad.ye.R
import com.sadad.ye.models.Customer
import java.util.*

object NotificationUtils {
    const val CHANNEL_ID = "debt_notifications"
    const val CHANNEL_NAME = "تنبيهات المديونيات"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = "تنبيهات عند اقتراب العملاء من سقف المديونية"
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun scheduleDebtNotification(context: Context, customer: Customer, balance: Double, lastTransactionTime: Long) {
        if (customer.debtLimit <= 0) return

        val threshold = customer.debtLimit * 0.9
        if (balance >= threshold) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, DebtNotificationReceiver::class.java).apply {
                putExtra("customerName", customer.name)
                putExtra("customerId", customer.customerId)
                putExtra("balance", balance)
            }
            
            val pendingIntent = PendingIntent.getBroadcast(
                context, 
                customer.customerId.hashCode(), 
                intent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // ضبط الوقت ليكون نفس وقت العملية ولكن في اليوم التالي
            val calendar = Calendar.getInstance().apply {
                timeInMillis = System.currentTimeMillis()
                val lastTime = Calendar.getInstance().apply { timeInMillis = lastTransactionTime }
                set(Calendar.HOUR_OF_DAY, lastTime.get(Calendar.HOUR_OF_DAY))
                set(Calendar.MINUTE, lastTime.get(Calendar.MINUTE))
                set(Calendar.SECOND, 0)
                add(Calendar.DAY_OF_YEAR, 1) // اليوم التالي
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            }
        } else {
            cancelNotification(context, customer.customerId)
        }
    }

    fun cancelNotification(context: Context, customerId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, DebtNotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, 
            customerId.hashCode(), 
            intent, 
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
        }
    }
}
