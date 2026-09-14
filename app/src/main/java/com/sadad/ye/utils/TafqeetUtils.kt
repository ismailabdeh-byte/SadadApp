package com.sadad.ye.utils

import java.math.BigDecimal

object TafqeetUtils {

    private val ones = arrayOf("", "واحد", "اثنان", "ثلاثة", "أربعة", "خمسة", "ستة", "سبعة", "ثمانية", "تسعة", "عشرة", "أحد عشر", "اثنا عشر", "ثلاثة عشر", "أربعة عشر", "خمسة عشر", "ستة عشر", "سبعة عشر", "ثمانية عشر", "تسعة عشر")
    private val tens = arrayOf("", "عشرة", "عشرون", "ثلاثون", "أربعون", "خمسون", "ستون", "سبعون", "ثمانون", "تسعون")
    private val hundreds = arrayOf("", "مائة", "مائتان", "ثلاثمائة", "أربعمائة", "خمسمائة", "ستمائة", "سبعمائة", "ثمانمائة", "تسعمائة")

    fun convertNumberToWords(number: Double, currencyName: String = "ريال"): String {
        val bigDecimal = BigDecimal(number).setScale(2, BigDecimal.ROUND_HALF_UP)
        val integerPart = bigDecimal.toLong()
        val decimalPart = (bigDecimal.remainder(BigDecimal.ONE).toDouble() * 100).toInt()

        var result = convert(integerPart) + " " + currencyName
        if (decimalPart > 0) {
            result += " و " + convert(decimalPart.toLong()) + " هللة"
        }
        
        return "فقط " + result + " لا غير"
    }

    private fun convert(n: Long): String {
        if (n < 0) return "سالب " + convert(-n)
        if (n < 20) return ones[n.toInt()]
        if (n < 100) return tens[(n / 10).toInt()] + (if (n % 10 != 0L) " و " + ones[(n % 10).toInt()] else "")
        if (n < 1000) {
            var res = hundreds[(n / 100).toInt()]
            if (n % 100 != 0L) res += " و " + convert(n % 100)
            return res
        }
        if (n < 1000000) {
            var res = ""
            val thousands = n / 1000
            res = when (thousands) {
                1L -> "ألف"
                2L -> "ألفان"
                in 3..10 -> convert(thousands) + " آلاف"
                else -> convert(thousands) + " ألف"
            }
            if (n % 1000 != 0L) res += " و " + convert(n % 1000)
            return res
        }
        if (n < 1000000000) {
            var res = convert(n / 1000000) + " مليون"
            if (n % 1000000 != 0L) res += " و " + convert(n % 1000000)
            return res
        }
        return n.toString()
    }
}
