package com.sadad.ye.ui

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.*

fun formatAmount(amount: Double): String {
    val symbols = DecimalFormatSymbols(Locale.US)
    symbols.groupingSeparator = ','
    symbols.decimalSeparator = '.'
    
    return if (amount == amount.toLong().toDouble()) {
        val formatter = DecimalFormat("#,###", symbols)
        formatter.format(amount.toLong())
    } else {
        val formatter = DecimalFormat("#,###.##", symbols)
        formatter.format(amount)
    }
}

fun showDatePicker(context: Context, onDateSelected: (Long) -> Unit) {
    val calendar = Calendar.getInstance()
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val selectedCalendar = Calendar.getInstance()
            selectedCalendar.set(year, month, dayOfMonth, 0, 0, 0)
            onDateSelected(selectedCalendar.timeInMillis)
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    ).show()
}

fun String.replaceDigitsToEnglish(): String {
    var result = this
    val arabicDigits = arrayOf("٠", "١", "٢", "٣", "٤", "٥", "٦", "٧", "٨", "٩")
    for (i in 0..9) {
        result = result.replace(arabicDigits[i], i.toString())
    }
    return result
}

fun makeCall(context: Context, phoneNumber: String) {
    try {
        val intent = Intent(Intent.ACTION_DIAL)
        intent.data = Uri.parse("tel:$phoneNumber")
        context.startActivity(intent)
    } catch (e: Exception) {
        // Handle exception if needed
    }
}
