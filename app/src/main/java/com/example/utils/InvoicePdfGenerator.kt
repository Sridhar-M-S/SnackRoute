package com.example.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.PaymentInvoice
import com.example.data.SalesEntry
import com.example.ui.AppViewModel
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

enum class InvoicePdfStyle {
    RECEIPT_STYLE,
    EXECUTIVE_STYLE
}

object InvoicePdfGenerator {

    // Standard Clean Document Dimensions (595 x 842 points - A4 standard)
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN_X = 40f
    private const val MARGIN_TOP = 40f
    private const val CONTENT_WIDTH = PAGE_WIDTH - (MARGIN_X * 2)

    /**
     * Builds the clean receipt text representation matching the user's template.
     */
    fun buildInvoiceBillText(
        invoice: PaymentInvoice,
        salesEntries: List<SalesEntry>,
        profile: AppViewModel.BusinessProfile
    ): String {
        val invoiceSales = salesEntries.filter { it.id in invoice.salesEntryIds }
        val sb = StringBuilder()
        val sep = "==================================================================="

        sb.appendLine(sep)
        val brand = if (profile.brandName.isNotBlank()) profile.brandName.uppercase(Locale.getDefault()) else "POP CRAZE"
        sb.appendLine(centerText(brand, 67))
        if (profile.companyName.isNotBlank()) {
            sb.appendLine(centerText(profile.companyName, 67))
        }
        if (profile.address.isNotBlank()) {
            val addrLines = wrapTextSimple("Address: ${profile.address}", 67)
            for (line in addrLines) {
                sb.appendLine(centerText(line, 67))
            }
        }
        if (profile.phoneNumber.isNotBlank()) {
            sb.appendLine(centerText("Phone: ${profile.phoneNumber}", 67))
        }
        if (profile.fssaiNumber.isNotBlank()) {
            sb.appendLine(centerText("FSSAI Lic No: ${profile.fssaiNumber}", 67))
        }
        sb.appendLine(sep)
        sb.appendLine(centerText("PAYMENT INVOICE", 67))
        sb.appendLine(sep)

        sb.appendLine("Invoice No : ${invoice.invoiceNumber}")
        sb.appendLine("Date       : ${invoice.invoiceDateFormatted}")
        sb.appendLine("Status     : ${invoice.status.uppercase(Locale.getDefault())}")
        sb.appendLine()

        sb.appendLine("--- SHOP DETAILS ---")
        sb.appendLine("Shop Name  : ${invoice.shopName}")
        sb.appendLine("Shop ID    : ${invoice.shopNumber}")
        sb.appendLine("Location   : ${invoice.locationNumber}")
        sb.appendLine()

        sb.appendLine("--- SALES DETAILS ---")
        if (invoiceSales.isEmpty()) {
            sb.appendLine("No items attached to this invoice.")
        } else {
            invoiceSales.forEachIndexed { index, sale ->
                val rate = sale.customSellingPrice ?: sale.ratePerPacket
                sb.appendLine(
                    "${index + 1}. ${sale.productName} | Date: ${sale.entryDateFormatted} | Rate: ₹${"%.2f".format(rate)} | Pkts: ${sale.packetsSold} | Amount: ₹${"%.2f".format(sale.totalAmount)}"
                )
            }
        }
        sb.appendLine()

        sb.appendLine("--- AMOUNT SUMMARY ---")
        sb.appendLine("Total Amount   : ₹${"%.2f".format(invoice.totalAmount)}")
        sb.appendLine("Amount Paid    : ₹${"%.2f".format(invoice.paidAmount)}")
        sb.appendLine("Balance Amount : ₹${"%.2f".format(invoice.balanceAmount)}")

        if (!invoice.notes.isNullOrBlank()) {
            sb.appendLine()
            sb.appendLine("Notes: ${invoice.notes}")
        }

        sb.appendLine(sep)
        val issuer = if (profile.brandName.isNotBlank()) profile.brandName else if (profile.companyName.isNotBlank()) profile.companyName else "Pop Craze"
        sb.appendLine("Issued by $issuer")

        return sb.toString()
    }

    private fun centerText(text: String, width: Int): String {
        if (text.length >= width) return text
        val leftPad = (width - text.length) / 2
        return " ".repeat(leftPad) + text
    }

    private fun wrapTextSimple(text: String, maxChars: Int): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (w in words) {
            if (current.isEmpty()) {
                current.append(w)
            } else if (current.length + 1 + w.length <= maxChars) {
                current.append(" ").append(w)
            } else {
                lines.add(current.toString())
                current = StringBuilder(w)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines
    }

    /**
     * Generates a PDF in either the Receipt Style or Executive Corporate Style.
     */
    fun generateInvoicePdf(
        context: Context,
        invoice: PaymentInvoice,
        salesEntries: List<SalesEntry>,
        profile: AppViewModel.BusinessProfile,
        style: InvoicePdfStyle = InvoicePdfStyle.RECEIPT_STYLE
    ): File {
        return when (style) {
            InvoicePdfStyle.RECEIPT_STYLE -> generateReceiptStylePdf(context, invoice, salesEntries, profile)
            InvoicePdfStyle.EXECUTIVE_STYLE -> generateExecutiveStylePdf(context, invoice, salesEntries, profile)
        }
    }

    /**
     * Format 1: Clean Thermal / Receipt Paper Layout
     */
    private fun generateReceiptStylePdf(
        context: Context,
        invoice: PaymentInvoice,
        salesEntries: List<SalesEntry>,
        profile: AppViewModel.BusinessProfile
    ): File {
        val invoiceSales = salesEntries.filter { it.id in invoice.salesEntryIds }
        val pdfDocument = PdfDocument()

        val monoTypeface = Typeface.MONOSPACE
        val monoBoldTypeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)

        val blackPaint = Paint().apply {
            color = Color.BLACK
            typeface = monoTypeface
            textSize = 10f
            isAntiAlias = true
        }

        val boldPaint = Paint().apply {
            color = Color.BLACK
            typeface = monoBoldTypeface
            textSize = 10f
            isAntiAlias = true
        }

        val brandHeaderPaint = Paint().apply {
            color = Color.BLACK
            typeface = monoBoldTypeface
            textSize = 14f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        val centerRegularPaint = Paint().apply {
            color = Color.BLACK
            typeface = monoTypeface
            textSize = 9.5f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        val centerBoldPaint = Paint().apply {
            color = Color.BLACK
            typeface = monoBoldTypeface
            textSize = 11.5f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        val doubleLinePaint = Paint().apply {
            color = Color.BLACK
            strokeWidth = 1.2f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        val lineHeight = 16f
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        canvas.drawColor(Color.WHITE)

        val centerX = PAGE_WIDTH / 2f
        var currentY = MARGIN_TOP

        fun drawDoubleLine(y: Float) {
            canvas.drawLine(MARGIN_X, y - 1.5f, MARGIN_X + CONTENT_WIDTH, y - 1.5f, doubleLinePaint)
            canvas.drawLine(MARGIN_X, y + 1.5f, MARGIN_X + CONTENT_WIDTH, y + 1.5f, doubleLinePaint)
        }

        // Top Double Line
        drawDoubleLine(currentY)
        currentY += 18f

        // Centered Header: Brand Name
        val brandName = if (profile.brandName.isNotBlank()) profile.brandName.uppercase(Locale.getDefault()) else "POP CRAZE"
        canvas.drawText(brandName, centerX, currentY, brandHeaderPaint)
        currentY += 16f

        // Company Name
        if (profile.companyName.isNotBlank()) {
            canvas.drawText(profile.companyName, centerX, currentY, centerRegularPaint)
            currentY += 14f
        }

        // Address
        if (profile.address.isNotBlank()) {
            val addrPaint = Paint(centerRegularPaint)
            val addrLines = wrapText(profile.address, addrPaint, CONTENT_WIDTH - 20f)
            for (line in addrLines) {
                canvas.drawText("Address: $line", centerX, currentY, addrPaint)
                currentY += 13f
            }
        }

        // Phone
        if (profile.phoneNumber.isNotBlank()) {
            canvas.drawText("Phone: ${profile.phoneNumber}", centerX, currentY, centerRegularPaint)
            currentY += 13f
        }

        // FSSAI
        if (profile.fssaiNumber.isNotBlank()) {
            canvas.drawText("FSSAI Lic No: ${profile.fssaiNumber}", centerX, currentY, centerRegularPaint)
            currentY += 14f
        }

        currentY += 4f
        drawDoubleLine(currentY)
        currentY += 18f

        // PAYMENT INVOICE Title (No "tax" word)
        canvas.drawText("PAYMENT INVOICE", centerX, currentY, centerBoldPaint)
        currentY += 12f

        drawDoubleLine(currentY)
        currentY += 22f

        // Invoice Metadata
        canvas.drawText("Invoice No : ${invoice.invoiceNumber}", MARGIN_X, currentY, boldPaint)
        currentY += lineHeight
        canvas.drawText("Date       : ${invoice.invoiceDateFormatted}", MARGIN_X, currentY, blackPaint)
        currentY += lineHeight
        canvas.drawText("Status     : ${invoice.status.uppercase(Locale.getDefault())}", MARGIN_X, currentY, boldPaint)
        currentY += lineHeight + 6f

        // Shop Details
        canvas.drawText("--- SHOP DETAILS ---", MARGIN_X, currentY, boldPaint)
        currentY += lineHeight
        canvas.drawText("Shop Name  : ${invoice.shopName}", MARGIN_X, currentY, boldPaint)
        currentY += lineHeight
        canvas.drawText("Shop ID    : ${invoice.shopNumber}", MARGIN_X, currentY, blackPaint)
        currentY += lineHeight
        canvas.drawText("Location   : ${invoice.locationNumber}", MARGIN_X, currentY, blackPaint)
        currentY += lineHeight + 6f

        // Sales Details
        canvas.drawText("--- SALES DETAILS ---", MARGIN_X, currentY, boldPaint)
        currentY += lineHeight

        if (invoiceSales.isEmpty()) {
            canvas.drawText("No linked sales records.", MARGIN_X, currentY, blackPaint)
            currentY += lineHeight
        } else {
            invoiceSales.forEachIndexed { index, sale ->
                val rate = sale.customSellingPrice ?: sale.ratePerPacket
                val itemLine = "${index + 1}. ${sale.productName} | Date: ${sale.entryDateFormatted} | Rate: ₹${"%.2f".format(rate)} | Pkts: ${sale.packetsSold} | Amount: ₹${"%.2f".format(sale.totalAmount)}"
                val itemLines = wrapText(itemLine, blackPaint, CONTENT_WIDTH)
                for (subLine in itemLines) {
                    canvas.drawText(subLine, MARGIN_X, currentY, blackPaint)
                    currentY += lineHeight
                }
            }
        }
        currentY += 6f

        // Amount Summary
        canvas.drawText("--- AMOUNT SUMMARY ---", MARGIN_X, currentY, boldPaint)
        currentY += lineHeight
        canvas.drawText("Total Amount   : ₹${"%.2f".format(invoice.totalAmount)}", MARGIN_X, currentY, boldPaint)
        currentY += lineHeight
        canvas.drawText("Amount Paid    : ₹${"%.2f".format(invoice.paidAmount)}", MARGIN_X, currentY, blackPaint)
        currentY += lineHeight
        canvas.drawText("Balance Amount : ₹${"%.2f".format(invoice.balanceAmount)}", MARGIN_X, currentY, boldPaint)
        currentY += lineHeight

        if (!invoice.notes.isNullOrBlank()) {
            currentY += 4f
            val noteLines = wrapText("Notes: ${invoice.notes}", blackPaint, CONTENT_WIDTH)
            for (line in noteLines) {
                canvas.drawText(line, MARGIN_X, currentY, blackPaint)
                currentY += lineHeight
            }
        }

        currentY += 10f
        drawDoubleLine(currentY)
        currentY += 18f

        val issuerName = if (profile.brandName.isNotBlank()) profile.brandName else if (profile.companyName.isNotBlank()) profile.companyName else "Pop Craze"
        canvas.drawText("Issued by $issuerName", MARGIN_X, currentY, boldPaint)

        pdfDocument.finishPage(page)

        val outputDir = File(context.cacheDir, "invoices").apply { mkdirs() }
        val safeInvoiceNum = invoice.invoiceNumber.replace("/", "_").replace("\\", "_").replace(" ", "_")
        val outputFile = File(outputDir, "Receipt_${safeInvoiceNum}.pdf")

        FileOutputStream(outputFile).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        return outputFile
    }

    /**
     * Format 2: Modern Executive Corporate Layout (Grid Table, Badges, Clean Typography, Zero Tax Mentions)
     */
    private fun generateExecutiveStylePdf(
        context: Context,
        invoice: PaymentInvoice,
        salesEntries: List<SalesEntry>,
        profile: AppViewModel.BusinessProfile
    ): File {
        val invoiceSales = salesEntries.filter { it.id in invoice.salesEntryIds }
        val pdfDocument = PdfDocument()

        val sansTypeface = Typeface.SANS_SERIF
        val sansBoldTypeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

        // Palette
        val primaryColor = Color.rgb(30, 58, 138) // Deep Corporate Indigo/Navy
        val secondaryColor = Color.rgb(71, 85, 105) // Slate
        val tableHeaderBg = Color.rgb(241, 245, 249) // Slate 100
        val altRowBg = Color.rgb(248, 250, 252) // Slate 50
        val borderLineColor = Color.rgb(226, 232, 240) // Slate 200

        val paintBrand = Paint().apply {
            color = primaryColor
            typeface = sansBoldTypeface
            textSize = 20f
            isAntiAlias = true
        }

        val paintSubBrand = Paint().apply {
            color = Color.rgb(51, 65, 85)
            typeface = sansBoldTypeface
            textSize = 11f
            isAntiAlias = true
        }

        val paintSmallMuted = Paint().apply {
            color = secondaryColor
            typeface = sansTypeface
            textSize = 9f
            isAntiAlias = true
        }

        val paintTitle = Paint().apply {
            color = primaryColor
            typeface = sansBoldTypeface
            textSize = 18f
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }

        val paintMetaLabel = Paint().apply {
            color = secondaryColor
            typeface = sansTypeface
            textSize = 9.5f
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }

        val paintMetaVal = Paint().apply {
            color = Color.rgb(15, 23, 42)
            typeface = sansBoldTypeface
            textSize = 9.5f
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }

        val paintCardBg = Paint().apply {
            color = Color.rgb(248, 250, 252)
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val paintBorder = Paint().apply {
            color = borderLineColor
            strokeWidth = 1f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        val paintTh = Paint().apply {
            color = Color.rgb(30, 41, 59)
            typeface = sansBoldTypeface
            textSize = 9f
            isAntiAlias = true
        }

        val paintTd = Paint().apply {
            color = Color.rgb(51, 65, 85)
            typeface = sansTypeface
            textSize = 9f
            isAntiAlias = true
        }

        val paintTdBold = Paint().apply {
            color = Color.rgb(15, 23, 42)
            typeface = sansBoldTypeface
            textSize = 9f
            isAntiAlias = true
        }

        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas
        canvas.drawColor(Color.WHITE)

        var currentY = MARGIN_TOP

        // Top Color Accent Bar
        val accentPaint = Paint().apply {
            color = primaryColor
            style = Paint.Style.FILL
        }
        canvas.drawRect(MARGIN_X, currentY, MARGIN_X + CONTENT_WIDTH, currentY + 4f, accentPaint)
        currentY += 24f

        // --- Header Section ---
        val brandName = if (profile.brandName.isNotBlank()) profile.brandName else "POP CRAZE"
        canvas.drawText(brandName, MARGIN_X, currentY, paintBrand)

        // Right side: PAYMENT INVOICE Title
        canvas.drawText("PAYMENT INVOICE", MARGIN_X + CONTENT_WIDTH, currentY, paintTitle)
        currentY += 15f

        if (profile.companyName.isNotBlank()) {
            canvas.drawText(profile.companyName, MARGIN_X, currentY, paintSubBrand)
        }
        canvas.drawText("Invoice #: ${invoice.invoiceNumber}", MARGIN_X + CONTENT_WIDTH, currentY, paintMetaVal)
        currentY += 13f

        if (profile.address.isNotBlank()) {
            canvas.drawText(profile.address, MARGIN_X, currentY, paintSmallMuted)
        }
        canvas.drawText("Date: ${invoice.invoiceDateFormatted}", MARGIN_X + CONTENT_WIDTH, currentY, paintMetaLabel)
        currentY += 13f

        val phoneFssai = buildString {
            if (profile.phoneNumber.isNotBlank()) append("Phone: ${profile.phoneNumber}")
            if (profile.phoneNumber.isNotBlank() && profile.fssaiNumber.isNotBlank()) append(" | ")
            if (profile.fssaiNumber.isNotBlank()) append("FSSAI: ${profile.fssaiNumber}")
        }
        if (phoneFssai.isNotBlank()) {
            canvas.drawText(phoneFssai, MARGIN_X, currentY, paintSmallMuted)
        }

        // Status Badge on Right
        val statusText = invoice.status.uppercase(Locale.getDefault())
        val statusBgColor = when (statusText) {
            "PAID" -> Color.rgb(220, 252, 231) // Green 100
            "PARTIALLY PAID" -> Color.rgb(254, 249, 195) // Yellow 100
            else -> Color.rgb(254, 226, 226) // Red 100
        }
        val statusTextColor = when (statusText) {
            "PAID" -> Color.rgb(22, 101, 52) // Green 800
            "PARTIALLY PAID" -> Color.rgb(133, 77, 14) // Yellow 800
            else -> Color.rgb(153, 27, 27) // Red 800
        }
        val statusPaintBg = Paint().apply {
            color = statusBgColor
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val statusPaintText = Paint().apply {
            color = statusTextColor
            typeface = sansBoldTypeface
            textSize = 8.5f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        val badgeWidth = 90f
        val badgeHeight = 16f
        val badgeRect = RectF(
            MARGIN_X + CONTENT_WIDTH - badgeWidth,
            currentY - 11f,
            MARGIN_X + CONTENT_WIDTH,
            currentY - 11f + badgeHeight
        )
        canvas.drawRoundRect(badgeRect, 4f, 4f, statusPaintBg)
        canvas.drawText(statusText, badgeRect.centerX(), badgeRect.centerY() + 3f, statusPaintText)

        currentY += 24f

        // --- Bill To Card ---
        val cardHeight = 54f
        val cardRect = RectF(MARGIN_X, currentY, MARGIN_X + CONTENT_WIDTH, currentY + cardHeight)
        canvas.drawRoundRect(cardRect, 6f, 6f, paintCardBg)
        canvas.drawRoundRect(cardRect, 6f, 6f, paintBorder)

        val cardPadX = MARGIN_X + 12f
        var cardInnerY = currentY + 16f

        val billToPaint = Paint().apply {
            color = primaryColor
            typeface = sansBoldTypeface
            textSize = 8.5f
            isAntiAlias = true
        }
        canvas.drawText("BILLED TO / SHOP DETAILS", cardPadX, cardInnerY, billToPaint)
        cardInnerY += 15f

        val shopNamePaint = Paint().apply {
            color = Color.rgb(15, 23, 42)
            typeface = sansBoldTypeface
            textSize = 11f
            isAntiAlias = true
        }
        canvas.drawText(invoice.shopName, cardPadX, cardInnerY, shopNamePaint)

        val shopMetaPaint = Paint().apply {
            color = secondaryColor
            typeface = sansTypeface
            textSize = 9.5f
            isAntiAlias = true
        }
        canvas.drawText("Shop ID: ${invoice.shopNumber}   •   Location / Route: ${invoice.locationNumber}", cardPadX + 180f, cardInnerY, shopMetaPaint)

        currentY += cardHeight + 20f

        // --- Itemized Table Section ---
        val colWidths = floatArrayOf(28f, 195f, 85f, 65f, 60f, 82f)
        val colAligns = arrayOf(
            Paint.Align.LEFT,
            Paint.Align.LEFT,
            Paint.Align.LEFT,
            Paint.Align.RIGHT,
            Paint.Align.RIGHT,
            Paint.Align.RIGHT
        )
        val headers = arrayOf("#", "Product Description", "Sale Date", "Rate", "Quantity", "Amount")

        // Draw Table Header Row
        val thHeight = 22f
        val thRect = RectF(MARGIN_X, currentY, MARGIN_X + CONTENT_WIDTH, currentY + thHeight)
        val thBgPaint = Paint().apply {
            color = tableHeaderBg
            style = Paint.Style.FILL
        }
        canvas.drawRect(thRect, thBgPaint)
        canvas.drawRect(thRect, paintBorder)

        var curColX = MARGIN_X
        for (i in headers.indices) {
            val align = colAligns[i]
            paintTh.textAlign = align
            val tx = when (align) {
                Paint.Align.LEFT -> curColX + 6f
                Paint.Align.RIGHT -> curColX + colWidths[i] - 6f
                else -> curColX + (colWidths[i] / 2f)
            }
            canvas.drawText(headers[i], tx, currentY + 14f, paintTh)
            curColX += colWidths[i]
        }
        currentY += thHeight

        // Draw Table Body Rows
        val rowHeight = 22f
        if (invoiceSales.isEmpty()) {
            val rowRect = RectF(MARGIN_X, currentY, MARGIN_X + CONTENT_WIDTH, currentY + rowHeight)
            canvas.drawRect(rowRect, paintBorder)
            canvas.drawText("No itemized sales attached.", MARGIN_X + 10f, currentY + 14f, paintTd)
            currentY += rowHeight
        } else {
            invoiceSales.forEachIndexed { index, sale ->
                val rowRect = RectF(MARGIN_X, currentY, MARGIN_X + CONTENT_WIDTH, currentY + rowHeight)
                if (index % 2 == 1) {
                    val altPaint = Paint().apply {
                        color = altRowBg
                        style = Paint.Style.FILL
                    }
                    canvas.drawRect(rowRect, altPaint)
                }
                canvas.drawRect(rowRect, paintBorder)

                val rate = sale.customSellingPrice ?: sale.ratePerPacket
                val values = arrayOf(
                    "${index + 1}",
                    sale.productName,
                    sale.entryDateFormatted,
                    "₹${"%.2f".format(rate)}",
                    "${sale.packetsSold} pkts",
                    "₹${"%.2f".format(sale.totalAmount)}"
                )

                var cX = MARGIN_X
                for (c in values.indices) {
                    val align = colAligns[c]
                    val p = if (c == values.lastIndex) paintTdBold else paintTd
                    p.textAlign = align
                    val tx = when (align) {
                        Paint.Align.LEFT -> cX + 6f
                        Paint.Align.RIGHT -> cX + colWidths[c] - 6f
                        else -> cX + (colWidths[c] / 2f)
                    }
                    canvas.drawText(values[c], tx, currentY + 14f, p)
                    cX += colWidths[c]
                }
                currentY += rowHeight
            }
        }

        currentY += 16f

        // --- Summary & Totals Block ---
        val summaryBoxWidth = 220f
        val summaryBoxX = MARGIN_X + CONTENT_WIDTH - summaryBoxWidth
        val summaryLineHeight = 18f

        // Left Side: Notes
        if (!invoice.notes.isNullOrBlank()) {
            val notesPaintTitle = Paint().apply {
                color = primaryColor
                typeface = sansBoldTypeface
                textSize = 9f
                isAntiAlias = true
            }
            canvas.drawText("PAYMENT NOTES & REMARKS:", MARGIN_X, currentY + 12f, notesPaintTitle)
            val noteLines = wrapText(invoice.notes, paintTd, CONTENT_WIDTH - summaryBoxWidth - 30f)
            var nY = currentY + 26f
            for (nl in noteLines) {
                canvas.drawText(nl, MARGIN_X, nY, paintTd)
                nY += 13f
            }
        }

        // Right Side Summary Box
        val summaryCardRect = RectF(summaryBoxX, currentY, MARGIN_X + CONTENT_WIDTH, currentY + 76f)
        canvas.drawRoundRect(summaryCardRect, 6f, 6f, paintCardBg)
        canvas.drawRoundRect(summaryCardRect, 6f, 6f, paintBorder)

        var sY = currentY + 16f
        val sumLabelPaint = Paint().apply {
            color = secondaryColor
            typeface = sansTypeface
            textSize = 9.5f
            isAntiAlias = true
        }
        val sumValPaint = Paint().apply {
            color = Color.rgb(15, 23, 42)
            typeface = sansBoldTypeface
            textSize = 9.5f
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }

        // Total Amount
        canvas.drawText("Total Amount:", summaryBoxX + 12f, sY, sumLabelPaint)
        canvas.drawText("₹${"%.2f".format(invoice.totalAmount)}", MARGIN_X + CONTENT_WIDTH - 12f, sY, sumValPaint)
        sY += summaryLineHeight

        // Amount Paid
        canvas.drawText("Amount Paid:", summaryBoxX + 12f, sY, sumLabelPaint)
        canvas.drawText("₹${"%.2f".format(invoice.paidAmount)}", MARGIN_X + CONTENT_WIDTH - 12f, sY, sumValPaint)
        sY += summaryLineHeight

        // Balance Due (Highlighted)
        val balanceDueLabelPaint = Paint().apply {
            color = if (invoice.balanceAmount > 0.0) Color.rgb(185, 28, 28) else primaryColor
            typeface = sansBoldTypeface
            textSize = 10.5f
            isAntiAlias = true
        }
        val balanceDueValPaint = Paint().apply {
            color = if (invoice.balanceAmount > 0.0) Color.rgb(185, 28, 28) else primaryColor
            typeface = sansBoldTypeface
            textSize = 10.5f
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }
        canvas.drawText("Balance Due:", summaryBoxX + 12f, sY, balanceDueLabelPaint)
        canvas.drawText("₹${"%.2f".format(invoice.balanceAmount)}", MARGIN_X + CONTENT_WIDTH - 12f, sY, balanceDueValPaint)

        currentY += 96f

        // --- Bottom Signatory Section ---
        val sigLinePaint = Paint().apply {
            color = Color.rgb(148, 163, 184)
            strokeWidth = 1f
            isAntiAlias = true
        }
        val sigX = MARGIN_X + CONTENT_WIDTH - 160f
        canvas.drawLine(sigX, currentY + 30f, MARGIN_X + CONTENT_WIDTH, currentY + 30f, sigLinePaint)

        val sigTextPaint = Paint().apply {
            color = secondaryColor
            typeface = sansBoldTypeface
            textSize = 8.5f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val issuerTitle = if (profile.brandName.isNotBlank()) profile.brandName else "Authorized Signatory"
        canvas.drawText("For $issuerTitle", sigX + 80f, currentY + 42f, sigTextPaint)
        canvas.drawText("Authorized Signatory", sigX + 80f, currentY + 54f, paintSmallMuted.apply { textAlign = Paint.Align.CENTER })

        // Bottom Brand Banner
        val footerY = PAGE_HEIGHT - 35f
        canvas.drawLine(MARGIN_X, footerY, MARGIN_X + CONTENT_WIDTH, footerY, paintBorder)
        val footerPaint = Paint().apply {
            color = secondaryColor
            typeface = sansTypeface
            textSize = 8f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText(
            "This is a system generated payment invoice. Thank you for your business!",
            PAGE_WIDTH / 2f,
            footerY + 15f,
            footerPaint
        )

        pdfDocument.finishPage(page)

        val outputDir = File(context.cacheDir, "invoices").apply { mkdirs() }
        val safeInvoiceNum = invoice.invoiceNumber.replace("/", "_").replace("\\", "_").replace(" ", "_")
        val outputFile = File(outputDir, "Executive_Invoice_${safeInvoiceNum}.pdf")

        FileOutputStream(outputFile).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        return outputFile
    }

    fun shareInvoicePdf(
        context: Context,
        invoice: PaymentInvoice,
        salesEntries: List<SalesEntry>,
        profile: AppViewModel.BusinessProfile,
        style: InvoicePdfStyle = InvoicePdfStyle.RECEIPT_STYLE
    ) {
        try {
            val pdfFile = generateInvoicePdf(context, invoice, salesEntries, profile, style)
            val uri = FileProvider.getUriForFile(
                context,
                "com.example.snackroutepro.fileprovider",
                pdfFile
            )

            val styleName = if (style == InvoicePdfStyle.RECEIPT_STYLE) "Receipt Bill" else "Executive Invoice"
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Payment Invoice - ${invoice.invoiceNumber} (${invoice.shopName})")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Payment Invoice ${invoice.invoiceNumber} for ${invoice.shopName} - ₹${"%.2f".format(invoice.totalAmount)}"
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }

            val chooser = Intent.createChooser(intent, "Share $styleName (${invoice.invoiceNumber})")
            if (context !is Activity) {
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to generate Invoice PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val lines = mutableListOf<String>()
        val words = text.split(" ")
        var currentLine = StringBuilder()

        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(testLine) <= maxWidth) {
                currentLine = StringBuilder(testLine)
            } else {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine.toString())
                }
                currentLine = StringBuilder(word)
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }
        return lines
    }
}
