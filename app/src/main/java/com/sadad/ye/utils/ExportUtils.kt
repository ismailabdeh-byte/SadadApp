package com.sadad.ye.utils

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.sadad.ye.models.Customer
import com.sadad.ye.models.Transaction
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object ExportUtils {

    fun exportToExcel(context: Context, customers: List<Customer>, transactions: List<Transaction>, userName: String) {
        val csvData = StringBuilder()
        csvData.append("تقرير مديونيات: $userName\n")
        csvData.append("اسم العميل,رقم الجوال,إجمالي المديونية\n")
        
        customers.forEach { customer ->
            val customerTransactions = transactions.filter { it.customerId == customer.customerId }
            val debt = customerTransactions.filter { it.debt }.sumOf { it.amount }
            val paid = customerTransactions.filter { !it.debt }.sumOf { it.amount }
            val balance = debt - paid
            csvData.append("${customer.name},${customer.phoneNumber},${balance}\n")
        }

        try {
            val file = File(context.cacheDir, "customers_report.csv")
            val out = FileOutputStream(file)
            // إضافة BOM لدعم اللغة العربية في Excel
            out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())) 
            out.write(csvData.toString().toByteArray())
            out.close()
            shareFile(context, file, "application/vnd.ms-excel")
        } catch (e: Exception) {
            Toast.makeText(context, "فشل تصدير Excel: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun exportToPdf(context: Context, customers: List<Customer>, transactions: List<Transaction>, userName: String) {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas
        val paint = Paint()

        var y = 50f
        paint.textSize = 20f
        paint.isFakeBoldText = true
        canvas.drawText(userName, 50f, y, paint) // اسم المحل/المستخدم في الأعلى
        
        y += 30f
        paint.textSize = 16f
        canvas.drawText("تقرير المديونيات الشامل", 50f, y, paint)
        
        y += 30f
        paint.textSize = 12f
        paint.isFakeBoldText = false
        canvas.drawText("تاريخ التقرير: ${SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date())}", 50f, y, paint)
        
        y += 20f
        canvas.drawLine(50f, y, 550f, y, paint)
        
        y += 25f
        paint.isFakeBoldText = true
        canvas.drawText("اسم العميل", 50f, y, paint)
        canvas.drawText("رقم الجوال", 250f, y, paint)
        canvas.drawText("المبلغ المستحق", 450f, y, paint)

        y += 10f
        canvas.drawLine(50f, y, 550f, y, paint)

        y += 25f
        paint.isFakeBoldText = false
        customers.forEach { customer ->
            val customerTransactions = transactions.filter { it.customerId == customer.customerId }
            val balance = customerTransactions.filter { it.debt }.sumOf { it.amount } - customerTransactions.filter { !it.debt }.sumOf { it.amount }
            
            canvas.drawText(customer.name, 50f, y, paint)
            canvas.drawText(customer.phoneNumber, 250f, y, paint)
            canvas.drawText("${String.format("%.2f", balance)}", 450f, y, paint)
            y += 25f
            
            if (y > 800) return@forEach
        }

        pdfDocument.finishPage(page)

        try {
            val file = File(context.cacheDir, "report.pdf")
            pdfDocument.writeTo(FileOutputStream(file))
            shareFile(context, file, "application/pdf")
        } catch (e: Exception) {
            Toast.makeText(context, "فشل تصدير PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            pdfDocument.close()
        }
    }

    private fun shareFile(context: Context, file: File, mimeType: String) {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = mimeType
        intent.putExtra(Intent.EXTRA_STREAM, uri)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, "مشاركة التقرير عبر:"))
    }
}
