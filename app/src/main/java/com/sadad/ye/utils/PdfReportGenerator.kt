package com.sadad.ye.utils

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.TextPaint
import android.widget.Toast
import androidx.core.content.FileProvider
import com.sadad.ye.models.Customer
import com.sadad.ye.models.Transaction
import com.sadad.ye.models.User
import com.sadad.ye.ui.formatAmount
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object PdfReportGenerator {

    fun generateCustomerReport(
        context: Context,
        user: User?,
        customer: Customer,
        transactions: List<Transaction>,
        whatsappPackage: String = "com.whatsapp"
    ) {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas
        val paint = Paint()
        val textPaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 12f
            isAntiAlias = true
        }

        var y = 40f
        val margin = 40f
        val pageWidth = 595f

        // 1. Header
        user?.logoUri?.let { uriStr ->
            try {
                val inputStream = context.contentResolver.openInputStream(Uri.parse(uriStr))
                val bitmap = BitmapFactory.decodeStream(inputStream)
                val scaledLogo = scaleBitmap(bitmap, 80)
                canvas.drawBitmap(scaledLogo, (pageWidth - scaledLogo.width) / 2, y, paint)
            } catch (e: Exception) {
                drawTextCentered(canvas, "سداد", pageWidth / 2, y + 30, textPaint.apply { textSize = 24f; isFakeBoldText = true; color = Color.parseColor("#3498db") })
            }
        } ?: run {
            drawTextCentered(canvas, "سداد", pageWidth / 2, y + 30, textPaint.apply { textSize = 24f; isFakeBoldText = true; color = Color.parseColor("#3498db") })
        }

        textPaint.apply { textSize = 14f; isFakeBoldText = true; color = Color.BLACK; textAlign = Paint.Align.RIGHT }
        canvas.drawText(user?.name ?: "", pageWidth - margin, y + 20, textPaint)
        textPaint.apply { textSize = 10f; isFakeBoldText = false }
        canvas.drawText(user?.businessAddress ?: "", pageWidth - margin, y + 35, textPaint)
        val storePhone = user?.phoneNumber?.ifEmpty { user.uid ?: "" } ?: ""
        canvas.drawText("جوال: $storePhone", pageWidth - margin, y + 50, textPaint)

        textPaint.apply { textSize = 14f; isFakeBoldText = true; textAlign = Paint.Align.LEFT }
        canvas.drawText(user?.nameEn ?: "", margin, y + 20, textPaint)
        textPaint.apply { textSize = 10f; isFakeBoldText = false }
        canvas.drawText(user?.businessAddressEn ?: "", margin, y + 35, textPaint)
        canvas.drawText("Phone: $storePhone", margin, y + 50, textPaint)

        y += 90f
        paint.color = Color.parseColor("#3498db")
        paint.strokeWidth = 2f
        canvas.drawLine(margin, y, pageWidth - margin, y, paint)

        y += 30f
        textPaint.apply { textSize = 18f; isFakeBoldText = true; textAlign = Paint.Align.CENTER }
        canvas.drawText("كشف حساب تفصيلي", pageWidth / 2, y, textPaint)
        
        y += 25f
        textPaint.apply { textSize = 14f; color = Color.DKGRAY }
        canvas.drawText(customer.name, pageWidth / 2, y, textPaint)

        val dateStr = SimpleDateFormat("yyyy/MM/dd hh:mm a", Locale("ar")).format(Date())
        textPaint.apply { textSize = 10f; textAlign = Paint.Align.LEFT; isFakeBoldText = false }
        canvas.drawText(dateStr, margin, y, textPaint)

        y += 30f
        val colWidths = floatArrayOf(80f, 80f, 100f, 255f)
        val tableStartX = margin
        drawTableHeader(canvas, tableStartX, y, colWidths, textPaint)
        
        y += 25f
        val sortedTrans = transactions.sortedBy { it.date }
        sortedTrans.forEach { trans ->
            if (y > 750) {
                pdfDocument.finishPage(page)
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                y = 50f
                drawTableHeader(canvas, tableStartX, y, colWidths, textPaint)
                y += 25f
            }
            drawTableRow(canvas, tableStartX, y, colWidths, trans, textPaint)
            y += 20f
        }

        if (y > 600) { // Ensure space for summary and footer
            pdfDocument.finishPage(page)
            page = pdfDocument.startPage(pageInfo)
            canvas = page.canvas
            y = 50f
        }

        y += 30f
        // Summary Section - Box
        paint.color = Color.parseColor("#f8f9fa")
        canvas.drawRect(margin, y, pageWidth - margin, y + 110, paint)
        paint.color = Color.parseColor("#3498db")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRect(margin, y, pageWidth - margin, y + 110, paint)
        paint.style = Paint.Style.FILL // Reset to fill
        
        val totalDebt = transactions.filter { it.debt }.sumOf { it.amount }
        val totalPaid = transactions.filter { !it.debt }.sumOf { it.amount }
        val balance = totalDebt - totalPaid
        val currency = if (customer.currency.isNotEmpty()) customer.currency else (user?.defaultCurrency ?: "ريال")

        // Draw Totals
        textPaint.apply { textAlign = Paint.Align.RIGHT; color = Color.BLACK; textSize = 12f; isFakeBoldText = false }
        canvas.drawText("إجمالي الدين:", pageWidth - margin - 150, y + 30, textPaint)
        textPaint.apply { textAlign = Paint.Align.LEFT }
        canvas.drawText("${formatAmount(totalDebt)} $currency", margin + 150, y + 30, textPaint)

        textPaint.apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText("إجمالي السداد:", pageWidth - margin - 150, y + 55, textPaint)
        textPaint.apply { textAlign = Paint.Align.LEFT }
        canvas.drawText("${formatAmount(totalPaid)} $currency", margin + 150, y + 55, textPaint)

        paint.color = Color.LTGRAY
        canvas.drawLine(margin + 20, y + 65, pageWidth - margin - 20, y + 65, paint)

        textPaint.apply { textAlign = Paint.Align.RIGHT; isFakeBoldText = true; textSize = 14f; color = Color.parseColor("#e74c3c") }
        canvas.drawText("صافي الرصيد المتبقي:", pageWidth - margin - 150, y + 85, textPaint)
        textPaint.apply { textAlign = Paint.Align.LEFT }
        canvas.drawText("${formatAmount(balance)} $currency", margin + 150, y + 85, textPaint)
        
        // Tafqeet
        val tafqeet = TafqeetUtils.convertNumberToWords(kotlin.math.abs(balance), currency)
        textPaint.apply { 
            textSize = 10f
            isFakeBoldText = false
            color = Color.GRAY
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            textAlign = Paint.Align.CENTER 
        }
        canvas.drawText(tafqeet, pageWidth / 2, y + 102, textPaint)

        y += 150f
        // Note
        textPaint.apply { textAlign = Paint.Align.CENTER; color = Color.parseColor("#e67e22"); isFakeBoldText = true; textSize = 12f; typeface = Typeface.DEFAULT }
        canvas.drawText("نرجوا سداد حسابكم في أقرب وقت ممكن، وشكراً لتعاملكم معنا.", pageWidth / 2, y, textPaint)

        y += 60f
        // Signatures side by side
        textPaint.apply { color = Color.BLACK; textAlign = Paint.Align.CENTER; isFakeBoldText = true }
        
        // Customer Sign
        canvas.drawText("توقيع العميل", margin + 120, y, textPaint)
        canvas.drawLine(margin + 40, y + 40, margin + 200, y + 40, paint.apply { color = Color.BLACK; strokeWidth = 1f })
        
        // Store Sign
        canvas.drawText("توقيع المتجر / المؤسسة", pageWidth - margin - 120, y, textPaint)
        canvas.drawLine(pageWidth - margin - 200, y + 40, pageWidth - margin - 40, y + 40, paint)

        pdfDocument.finishPage(page)

        try {
            val fileName = "Report_${customer.name.replace(" ", "_")}.pdf"
            val file = File(context.cacheDir, fileName)
            pdfDocument.writeTo(FileOutputStream(file))
            shareFileViaWhatsApp(context, customer.phoneNumber, file, whatsappPackage)
        } catch (e: Exception) {
            Toast.makeText(context, "خطأ في إنشاء التقرير: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            pdfDocument.close()
        }
    }

    private fun drawTableHeader(canvas: Canvas, x: Float, y: Float, colWidths: FloatArray, paint: TextPaint) {
        val bgPaint = Paint().apply { color = Color.parseColor("#3498db") }
        canvas.drawRect(x, y - 15, x + colWidths.sum(), y + 10, bgPaint)
        paint.apply { color = Color.WHITE; isFakeBoldText = true; textAlign = Paint.Align.CENTER; textSize = 10f }
        var currentX = x
        val headers = arrayOf("التاريخ", "النوع", "المبلغ", "الملاحظة")
        for (i in headers.indices) {
            canvas.drawText(headers[i], currentX + colWidths[i] / 2, y, paint)
            currentX += colWidths[i]
        }
    }

    private fun drawTableRow(canvas: Canvas, x: Float, y: Float, colWidths: FloatArray, trans: Transaction, paint: TextPaint) {
        val rowBgPaint = Paint().apply { color = if (trans.debt) Color.parseColor("#fff5f5") else Color.parseColor("#f5fff5") }
        canvas.drawRect(x, y - 15, x + colWidths.sum(), y + 5, rowBgPaint)
        paint.apply { color = Color.BLACK; isFakeBoldText = false; textAlign = Paint.Align.CENTER; textSize = 9f }
        var currentX = x
        val dateStr = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(trans.date))
        val typeStr = if (trans.debt) "دين (+)" else "سداد (-)"
        val amountStr = String.format("%,.2f", trans.amount)
        canvas.drawText(dateStr, currentX + colWidths[0] / 2, y, paint)
        currentX += colWidths[0]
        canvas.drawText(typeStr, currentX + colWidths[1] / 2, y, paint)
        currentX += colWidths[1]
        canvas.drawText(amountStr, currentX + colWidths[2] / 2, y, paint)
        currentX += colWidths[2]
        paint.textAlign = Paint.Align.RIGHT
        val note = if (trans.note.length > 40) trans.note.substring(0, 37) + "..." else trans.note
        canvas.drawText(note, currentX + colWidths[3] - 5, y, paint)
    }

    private fun drawTextCentered(canvas: Canvas, text: String, x: Float, y: Float, paint: TextPaint) {
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(text, x, y, paint)
    }

    private fun scaleBitmap(bm: Bitmap, maxHeight: Int): Bitmap {
        val width = bm.width
        val height = bm.height
        val ratio = maxHeight.toFloat() / height
        val newWidth = (width * ratio).toInt()
        return Bitmap.createScaledBitmap(bm, newWidth, maxHeight, true)
    }

    private fun shareFileViaWhatsApp(context: Context, phoneNumber: String, file: File, packageName: String) {
        val cleanPhone = phoneNumber.filter { it.isDigit() }.let { 
            when {
                it.length == 9 -> "967$it"
                it.startsWith("00") -> it.substring(2)
                it.startsWith("+") -> it.substring(1)
                else -> it
            }
        }
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra("jid", "$cleanPhone@s.whatsapp.net")
            setPackage(packageName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "مشاركة كشف الحساب"))
        }
    }
}
