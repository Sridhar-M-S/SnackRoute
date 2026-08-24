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

object InvoicePdfGenerator {

    // Standard Clean Document Dimensions (595 x 842 points - A4 standard)
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN_X = 40f
    private const val MARGIN_TOP = 40f
    private const val MARGIN_BOTTOM = 800f
    private const val CONTENT_WIDTH = PAGE_WIDTH - (MARGIN_X * 2)

    /**
     * Builds the exact clean receipt text representation matching the user's template.
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
        val brand = if (profile.brandName.isNotBlank()) profile.brandName.uppercase(Locale.getDefault()) else "SNACKROUTE"
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
        val issuer = if (profile.brandName.isNotBlank()) profile.brandName else if (profile.companyName.isNotBlank()) profile.companyName else "SnackRoute"
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
     * Generates a high-quality PDF bill matching the clean invoice bill format shown in the photo.
     */
    fun generateInvoicePdf(
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

        // Calculate pages needed (generous line spacing)
        val lineHeight = 16f
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        // Background: Clean crisp white
        canvas.drawColor(Color.WHITE)

        val centerX = PAGE_WIDTH / 2f
        var currentY = MARGIN_TOP

        // Helper function to draw double separator line
        fun drawDoubleLine(y: Float) {
            canvas.drawLine(MARGIN_X, y - 1.5f, MARGIN_X + CONTENT_WIDTH, y - 1.5f, doubleLinePaint)
            canvas.drawLine(MARGIN_X, y + 1.5f, MARGIN_X + CONTENT_WIDTH, y + 1.5f, doubleLinePaint)
        }

        // 1. Top Double Line
        drawDoubleLine(currentY)
        currentY += 18f

        // 2. Centered Header: Brand Name
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

        // 3. Header Divider
        drawDoubleLine(currentY)
        currentY += 18f

        // 4. "PAYMENT INVOICE" Title
        canvas.drawText("PAYMENT INVOICE", centerX, currentY, centerBoldPaint)
        currentY += 12f

        // 5. Title Under Divider
        drawDoubleLine(currentY)
        currentY += 22f

        // 6. Invoice Metadata
        canvas.drawText("Invoice No : ${invoice.invoiceNumber}", MARGIN_X, currentY, boldPaint)
        currentY += lineHeight
        canvas.drawText("Date       : ${invoice.invoiceDateFormatted}", MARGIN_X, currentY, blackPaint)
        currentY += lineHeight
        canvas.drawText("Status     : ${invoice.status.uppercase(Locale.getDefault())}", MARGIN_X, currentY, boldPaint)
        currentY += lineHeight + 6f

        // 7. Shop Details
        canvas.drawText("--- SHOP DETAILS ---", MARGIN_X, currentY, boldPaint)
        currentY += lineHeight
        canvas.drawText("Shop Name  : ${invoice.shopName}", MARGIN_X, currentY, boldPaint)
        currentY += lineHeight
        canvas.drawText("Shop ID    : ${invoice.shopNumber}", MARGIN_X, currentY, blackPaint)
        currentY += lineHeight
        canvas.drawText("Location   : ${invoice.locationNumber}", MARGIN_X, currentY, blackPaint)
        currentY += lineHeight + 6f

        // 8. Sales Details
        canvas.drawText("--- SALES DETAILS ---", MARGIN_X, currentY, boldPaint)
        currentY += lineHeight

        if (invoiceSales.isEmpty()) {
            canvas.drawText("No linked sales records.", MARGIN_X, currentY, blackPaint)
            currentY += lineHeight
        } else {
            invoiceSales.forEachIndexed { index, sale ->
                val rate = sale.customSellingPrice ?: sale.ratePerPacket
                val itemLine = "${index + 1}. ${sale.productName} | Date: ${sale.entryDateFormatted} | Rate: ₹${"%.2f".format(rate)} | Pkts: ${sale.packetsSold} | Amount: ₹${"%.2f".format(sale.totalAmount)}"
                
                // Wrap item line if longer than content width
                val itemLines = wrapText(itemLine, blackPaint, CONTENT_WIDTH)
                for (subLine in itemLines) {
                    canvas.drawText(subLine, MARGIN_X, currentY, blackPaint)
                    currentY += lineHeight
                }
            }
        }
        currentY += 6f

        // 9. Amount Summary
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
        // 10. Bottom Double Line
        drawDoubleLine(currentY)
        currentY += 18f

        // 11. Footer Issuer
        val issuerName = if (profile.brandName.isNotBlank()) profile.brandName else if (profile.companyName.isNotBlank()) profile.companyName else "Pop Craze"
        canvas.drawText("Issued by $issuerName", MARGIN_X, currentY, boldPaint)

        pdfDocument.finishPage(page)

        // Save PDF to cache dir
        val outputDir = File(context.cacheDir, "invoices").apply { mkdirs() }
        val safeInvoiceNum = invoice.invoiceNumber.replace("/", "_").replace("\\", "_").replace(" ", "_")
        val outputFile = File(outputDir, "Invoice_${safeInvoiceNum}.pdf")

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
        profile: AppViewModel.BusinessProfile
    ) {
        try {
            val pdfFile = generateInvoicePdf(context, invoice, salesEntries, profile)
            val uri = FileProvider.getUriForFile(
                context,
                "com.example.snackroutepro.fileprovider",
                pdfFile
            )

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

            val chooser = Intent.createChooser(intent, "Share Invoice Bill PDF (${invoice.invoiceNumber})")
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
