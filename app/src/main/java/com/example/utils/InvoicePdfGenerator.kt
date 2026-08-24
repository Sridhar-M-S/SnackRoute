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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object InvoicePdfGenerator {

    // A4 Dimensions at 72 points per inch
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN_LEFT = 36f
    private const val MARGIN_RIGHT = 559f
    private const val MARGIN_TOP = 36f
    private const val MARGIN_BOTTOM = 806f
    private const val CONTENT_WIDTH = MARGIN_RIGHT - MARGIN_LEFT // 523f

    fun generateInvoicePdf(
        context: Context,
        invoice: PaymentInvoice,
        salesEntries: List<SalesEntry>,
        profile: AppViewModel.BusinessProfile
    ): File {
        val invoiceSales = salesEntries.filter { it.id in invoice.salesEntryIds }

        val pdfDocument = PdfDocument()

        // Paints
        val primaryPaint = Paint().apply {
            color = Color.parseColor("#1E3A8A") // Deep Royal Navy
            isAntiAlias = true
        }

        val primaryDarkPaint = Paint().apply {
            color = Color.parseColor("#0F172A") // Slate 900
            isAntiAlias = true
        }

        val secondaryTextPaint = Paint().apply {
            color = Color.parseColor("#475569") // Slate 600
            isAntiAlias = true
        }

        val lightGrayTextPaint = Paint().apply {
            color = Color.parseColor("#64748B") // Slate 500
            isAntiAlias = true
        }

        val linePaint = Paint().apply {
            color = Color.parseColor("#E2E8F0") // Slate 200
            strokeWidth = 1f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        val tableHeaderPaint = Paint().apply {
            color = Color.parseColor("#1E293B") // Slate 800
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val rowEvenBgPaint = Paint().apply {
            color = Color.parseColor("#F8FAFC") // Slate 50
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val rowOddBgPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val cardBgPaint = Paint().apply {
            color = Color.parseColor("#F8FAFC")
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val cardBorderPaint = Paint().apply {
            color = Color.parseColor("#CBD5E1")
            style = Paint.Style.STROKE
            strokeWidth = 1f
            isAntiAlias = true
        }

        val badgePaidBgPaint = Paint().apply {
            color = Color.parseColor("#DCFCE7") // Light Green
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val badgePaidTextPaint = Paint().apply {
            color = Color.parseColor("#15803D") // Dark Green
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 9.5f
            isAntiAlias = true
        }

        val badgePartialBgPaint = Paint().apply {
            color = Color.parseColor("#FEF3C7") // Light Amber
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val badgePartialTextPaint = Paint().apply {
            color = Color.parseColor("#B45309") // Dark Amber
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 9.5f
            isAntiAlias = true
        }

        val badgeUnpaidBgPaint = Paint().apply {
            color = Color.parseColor("#FEE2E2") // Light Red
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val badgeUnpaidTextPaint = Paint().apply {
            color = Color.parseColor("#B91C1C") // Dark Red
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 9.5f
            isAntiAlias = true
        }

        // Calculate pages needed
        val maxItemsFirstPage = 8
        val maxItemsSubsequentPages = 16
        val totalItems = invoiceSales.size
        val totalPages = if (totalItems <= maxItemsFirstPage) {
            1
        } else {
            1 + Math.ceil((totalItems - maxItemsFirstPage).toDouble() / maxItemsSubsequentPages).toInt()
        }

        var currentItemIndex = 0

        for (pageIndex in 0 until totalPages) {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex + 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            var currentY = MARGIN_TOP

            if (pageIndex == 0) {
                // ==================== 1. TOP HEADER SECTION ====================
                // Top Brand & Company Name (Left Side)
                val brandName = profile.brandName.ifBlank { "SNACKROUTE PRO" }
                val companyName = profile.companyName.ifBlank { "" }
                val address = profile.address.ifBlank { "" }
                val phone = profile.phoneNumber.ifBlank { "" }
                val fssai = profile.fssaiNumber.ifBlank { "" }

                // Brand Name
                val brandPaint = Paint().apply {
                    color = Color.parseColor("#1E3A8A")
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textSize = 18f
                    isAntiAlias = true
                }
                canvas.drawText(brandName.uppercase(Locale.getDefault()), MARGIN_LEFT, currentY + 16f, brandPaint)
                currentY += 22f

                // Company Name
                if (companyName.isNotBlank()) {
                    val companyPaint = Paint().apply {
                        color = Color.parseColor("#1E293B")
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        textSize = 11f
                        isAntiAlias = true
                    }
                    canvas.drawText(companyName, MARGIN_LEFT, currentY + 11f, companyPaint)
                    currentY += 15f
                }

                // Address (with text wrap)
                if (address.isNotBlank()) {
                    val addrPaint = Paint().apply {
                        color = Color.parseColor("#475569")
                        textSize = 8.5f
                        isAntiAlias = true
                    }
                    val addrLines = wrapText(address, addrPaint, 260f)
                    for (line in addrLines.take(2)) {
                        canvas.drawText(line, MARGIN_LEFT, currentY + 9f, addrPaint)
                        currentY += 12f
                    }
                }

                // Phone
                if (phone.isNotBlank()) {
                    val phonePaint = Paint().apply {
                        color = Color.parseColor("#475569")
                        textSize = 8.5f
                        isAntiAlias = true
                    }
                    canvas.drawText("Phone: $phone", MARGIN_LEFT, currentY + 9f, phonePaint)
                    currentY += 13f
                }

                // FSSAI Badge
                if (fssai.isNotBlank()) {
                    val fssaiBadgeRect = RectF(MARGIN_LEFT, currentY + 2f, MARGIN_LEFT + 190f, currentY + 19f)
                    val fssaiBgPaint = Paint().apply {
                        color = Color.parseColor("#EFF6FF")
                        style = Paint.Style.FILL
                        isAntiAlias = true
                    }
                    val fssaiBorderPaint = Paint().apply {
                        color = Color.parseColor("#BFDBFE")
                        style = Paint.Style.STROKE
                        strokeWidth = 0.75f
                        isAntiAlias = true
                    }
                    canvas.drawRoundRect(fssaiBadgeRect, 4f, 4f, fssaiBgPaint)
                    canvas.drawRoundRect(fssaiBadgeRect, 4f, 4f, fssaiBorderPaint)

                    val fssaiTextPaint = Paint().apply {
                        color = Color.parseColor("#1D4ED8")
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        textSize = 8f
                        isAntiAlias = true
                    }
                    canvas.drawText("FSSAI Lic. No: $fssai", MARGIN_LEFT + 8f, currentY + 13.5f, fssaiTextPaint)
                    currentY += 24f
                } else {
                    currentY += 6f
                }

                // ==================== TOP RIGHT: INVOICE DETAILS PANEL ====================
                val rightPanelX = 350f
                val invoiceTitlePaint = Paint().apply {
                    color = Color.parseColor("#0F172A")
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textSize = 15f
                    textAlign = Paint.Align.RIGHT
                    isAntiAlias = true
                }
                canvas.drawText("TAX / PAYMENT INVOICE", MARGIN_RIGHT, MARGIN_TOP + 14f, invoiceTitlePaint)

                val invNumPaint = Paint().apply {
                    color = Color.parseColor("#1E3A8A")
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textSize = 11f
                    textAlign = Paint.Align.RIGHT
                    isAntiAlias = true
                }
                canvas.drawText("Invoice #: ${invoice.invoiceNumber}", MARGIN_RIGHT, MARGIN_TOP + 30f, invNumPaint)

                val invDatePaint = Paint().apply {
                    color = Color.parseColor("#475569")
                    textSize = 9f
                    textAlign = Paint.Align.RIGHT
                    isAntiAlias = true
                }
                canvas.drawText("Date: ${invoice.invoiceDateFormatted}", MARGIN_RIGHT, MARGIN_TOP + 44f, invDatePaint)

                // Status Badge
                val statusText = invoice.status.uppercase(Locale.getDefault())
                val statusWidth = 90f
                val statusHeight = 18f
                val statusRect = RectF(MARGIN_RIGHT - statusWidth, MARGIN_TOP + 52f, MARGIN_RIGHT, MARGIN_TOP + 52f + statusHeight)

                val (badgeBg, badgeTextP) = when (statusText) {
                    "PAID" -> Pair(badgePaidBgPaint, badgePaidTextPaint)
                    "PARTIALLY PAID", "PARTIAL" -> Pair(badgePartialBgPaint, badgePartialTextPaint)
                    else -> Pair(badgeUnpaidBgPaint, badgeUnpaidTextPaint)
                }

                canvas.drawRoundRect(statusRect, 4f, 4f, badgeBg)
                val badgeTextCenterPaint = Paint(badgeTextP).apply {
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText(statusText, statusRect.centerX(), statusRect.centerY() + 3.5f, badgeTextCenterPaint)

                // Accent Horizontal Divider
                val dividerY = Math.max(currentY, MARGIN_TOP + 82f)
                val accentLinePaint = Paint().apply {
                    color = Color.parseColor("#1E3A8A")
                    strokeWidth = 2f
                    isAntiAlias = true
                }
                canvas.drawLine(MARGIN_LEFT, dividerY, MARGIN_RIGHT, dividerY, accentLinePaint)
                currentY = dividerY + 12f

                // ==================== 2. BILL TO (CUSTOMER) BOX ====================
                val billToHeight = 56f
                val billToRect = RectF(MARGIN_LEFT, currentY, MARGIN_RIGHT, currentY + billToHeight)
                canvas.drawRoundRect(billToRect, 6f, 6f, cardBgPaint)
                canvas.drawRoundRect(billToRect, 6f, 6f, cardBorderPaint)

                val labelPaint = Paint().apply {
                    color = Color.parseColor("#64748B")
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textSize = 8f
                    isAntiAlias = true
                }
                canvas.drawText("BILL TO (CUSTOMER):", MARGIN_LEFT + 12f, currentY + 14f, labelPaint)

                val shopNamePaint = Paint().apply {
                    color = Color.parseColor("#0F172A")
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textSize = 12f
                    isAntiAlias = true
                }
                canvas.drawText(invoice.shopName, MARGIN_LEFT + 12f, currentY + 30f, shopNamePaint)

                val shopSubPaint = Paint().apply {
                    color = Color.parseColor("#475569")
                    textSize = 9f
                    isAntiAlias = true
                }
                canvas.drawText(
                    "Shop ID: ${invoice.shopNumber}   •   Location / Route: ${invoice.locationNumber}",
                    MARGIN_LEFT + 12f,
                    currentY + 45f,
                    shopSubPaint
                )

                // Right side of Bill To: Items count summary
                val itemsCountPaint = Paint().apply {
                    color = Color.parseColor("#1E3A8A")
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textSize = 9.5f
                    textAlign = Paint.Align.RIGHT
                    isAntiAlias = true
                }
                canvas.drawText("${invoiceSales.size} Item(s) Invoiced", MARGIN_RIGHT - 12f, currentY + 30f, itemsCountPaint)

                currentY += billToHeight + 14f
            } else {
                // Continuation Header for Subsequent Pages
                val contHeaderPaint = Paint().apply {
                    color = Color.parseColor("#1E3A8A")
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textSize = 11f
                    isAntiAlias = true
                }
                canvas.drawText(
                    "INVOICE #: ${invoice.invoiceNumber}  •  ${invoice.shopName} (Page ${pageIndex + 1} of $totalPages)",
                    MARGIN_LEFT,
                    currentY + 12f,
                    contHeaderPaint
                )
                currentY += 24f
            }

            // ==================== 3. TABLE SECTION ====================
            // Column boundaries
            // Columns: S.No (30), Description (213), Sale Date (80), Rate (65), Qty (55), Total (80)
            val colSNoX = MARGIN_LEFT
            val colDescX = colSNoX + 28f
            val colDateX = colDescX + 215f
            val colRateX = colDateX + 80f
            val colQtyX = colRateX + 65f
            val colTotalX = colQtyX + 55f
            val tableRight = MARGIN_RIGHT

            // Draw Table Header
            val tableHeaderHeight = 22f
            val headerRect = RectF(MARGIN_LEFT, currentY, MARGIN_RIGHT, currentY + tableHeaderHeight)
            canvas.drawRoundRect(headerRect, 4f, 4f, tableHeaderPaint)

            val thTextPaint = Paint().apply {
                color = Color.WHITE
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = 8.5f
                isAntiAlias = true
            }

            canvas.drawText("#", colSNoX + 8f, currentY + 14f, thTextPaint)
            canvas.drawText("ITEM DESCRIPTION", colDescX + 6f, currentY + 14f, thTextPaint)
            canvas.drawText("SALE DATE", colDateX + 6f, currentY + 14f, thTextPaint)

            val thRightPaint = Paint(thTextPaint).apply { textAlign = Paint.Align.RIGHT }
            canvas.drawText("RATE (₹)", colQtyX - 8f, currentY + 14f, thRightPaint)
            canvas.drawText("QTY (PKT)", colTotalX - 8f, currentY + 14f, thRightPaint)
            canvas.drawText("AMOUNT (₹)", tableRight - 8f, currentY + 14f, thRightPaint)

            currentY += tableHeaderHeight

            // Table Rows
            val rowHeight = 22f
            val itemsOnThisPage = if (pageIndex == 0) maxItemsFirstPage else maxItemsSubsequentPages

            val tdRegularPaint = Paint().apply {
                color = Color.parseColor("#1E293B")
                textSize = 8.5f
                isAntiAlias = true
            }

            val tdBoldPaint = Paint().apply {
                color = Color.parseColor("#0F172A")
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = 8.5f
                isAntiAlias = true
            }

            val tdRightRegular = Paint(tdRegularPaint).apply { textAlign = Paint.Align.RIGHT }
            val tdRightBold = Paint(tdBoldPaint).apply { textAlign = Paint.Align.RIGHT }

            if (invoiceSales.isEmpty()) {
                val rowRect = RectF(MARGIN_LEFT, currentY, MARGIN_RIGHT, currentY + rowHeight)
                canvas.drawRect(rowRect, rowOddBgPaint)
                canvas.drawText("No specific sales items linked to this invoice.", colDescX + 6f, currentY + 14f, tdRegularPaint)
                canvas.drawLine(MARGIN_LEFT, currentY + rowHeight, MARGIN_RIGHT, currentY + rowHeight, linePaint)
                currentY += rowHeight
            } else {
                var rowCount = 0
                while (currentItemIndex < invoiceSales.size && rowCount < itemsOnThisPage) {
                    val sale = invoiceSales[currentItemIndex]
                    val isEven = (currentItemIndex % 2 == 0)
                    val rowBg = if (isEven) rowOddBgPaint else rowEvenBgPaint

                    val rowRect = RectF(MARGIN_LEFT, currentY, MARGIN_RIGHT, currentY + rowHeight)
                    canvas.drawRect(rowRect, rowBg)

                    // S.No
                    canvas.drawText("${currentItemIndex + 1}", colSNoX + 8f, currentY + 14f, tdRegularPaint)

                    // Item Description (Truncate if too long)
                    val prodName = truncateText(sale.productName, tdBoldPaint, 205f)
                    canvas.drawText(prodName, colDescX + 6f, currentY + 14f, tdBoldPaint)

                    // Sale Date
                    canvas.drawText(sale.entryDateFormatted, colDateX + 6f, currentY + 14f, tdRegularPaint)

                    // Rate
                    val rate = sale.customSellingPrice ?: sale.ratePerPacket
                    canvas.drawText("₹${"%.2f".format(rate)}", colQtyX - 8f, currentY + 14f, tdRightRegular)

                    // Qty
                    canvas.drawText("${sale.packetsSold}", colTotalX - 8f, currentY + 14f, tdRightRegular)

                    // Total Amount
                    canvas.drawText("₹${"%.2f".format(sale.totalAmount)}", tableRight - 8f, currentY + 14f, tdRightBold)

                    // Row underline
                    canvas.drawLine(MARGIN_LEFT, currentY + rowHeight, MARGIN_RIGHT, currentY + rowHeight, linePaint)

                    currentY += rowHeight
                    currentItemIndex++
                    rowCount++
                }
            }

            // ==================== 4. TOTALS & FOOTER (ON LAST PAGE) ====================
            if (pageIndex == totalPages - 1) {
                currentY += 14f

                // Notes / Terms Section (Left side)
                val notesWidth = 270f
                val notesY = currentY

                if (!invoice.notes.isNullOrBlank()) {
                    val notesLabelPaint = Paint().apply {
                        color = Color.parseColor("#64748B")
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        textSize = 8f
                        isAntiAlias = true
                    }
                    canvas.drawText("NOTES / REMARKS:", MARGIN_LEFT, notesY + 10f, notesLabelPaint)

                    val notesTextPaint = Paint().apply {
                        color = Color.parseColor("#334155")
                        textSize = 8.5f
                        isAntiAlias = true
                    }
                    val noteLines = wrapText(invoice.notes, notesTextPaint, notesWidth)
                    var noteY = notesY + 22f
                    for (line in noteLines.take(3)) {
                        canvas.drawText(line, MARGIN_LEFT, noteY, notesTextPaint)
                        noteY += 12f
                    }
                }

                // Terms text
                val termsY = notesY + 60f
                val termsPaint = Paint().apply {
                    color = Color.parseColor("#64748B")
                    textSize = 7.5f
                    isAntiAlias = true
                }
                canvas.drawText("• Thank you for your business!", MARGIN_LEFT, termsY, termsPaint)
                canvas.drawText("• This is a computer generated payment receipt & invoice.", MARGIN_LEFT, termsY + 11f, termsPaint)

                // Summary Totals Card (Right side)
                val summaryX = 310f
                val summaryWidth = MARGIN_RIGHT - summaryX // 249f
                val summaryHeight = 92f
                val summaryRect = RectF(summaryX, currentY, MARGIN_RIGHT, currentY + summaryHeight)

                val summaryBgPaint = Paint().apply {
                    color = Color.parseColor("#F8FAFC")
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
                canvas.drawRoundRect(summaryRect, 6f, 6f, summaryBgPaint)
                canvas.drawRoundRect(summaryRect, 6f, 6f, cardBorderPaint)

                var sumRowY = currentY + 18f

                // Total Invoice Amount
                val sumLabelPaint = Paint().apply {
                    color = Color.parseColor("#475569")
                    textSize = 9f
                    isAntiAlias = true
                }
                val sumValPaint = Paint().apply {
                    color = Color.parseColor("#0F172A")
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textSize = 10f
                    textAlign = Paint.Align.RIGHT
                    isAntiAlias = true
                }
                canvas.drawText("Total Invoice Amount:", summaryX + 12f, sumRowY, sumLabelPaint)
                canvas.drawText("₹${"%.2f".format(invoice.totalAmount)}", MARGIN_RIGHT - 12f, sumRowY, sumValPaint)

                sumRowY += 20f

                // Amount Paid
                val paidValPaint = Paint().apply {
                    color = Color.parseColor("#15803D") // Dark Green
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textSize = 10f
                    textAlign = Paint.Align.RIGHT
                    isAntiAlias = true
                }
                canvas.drawText("Amount Paid / Received:", summaryX + 12f, sumRowY, sumLabelPaint)
                canvas.drawText("₹${"%.2f".format(invoice.paidAmount)}", MARGIN_RIGHT - 12f, sumRowY, paidValPaint)

                sumRowY += 12f
                canvas.drawLine(summaryX + 10f, sumRowY, MARGIN_RIGHT - 10f, sumRowY, linePaint)
                sumRowY += 16f

                // Balance Due
                val balLabelPaint = Paint().apply {
                    color = Color.parseColor("#0F172A")
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textSize = 10.5f
                    isAntiAlias = true
                }
                val balColor = if (invoice.balanceAmount > 0) Color.parseColor("#B91C1C") else Color.parseColor("#15803D")
                val balValPaint = Paint().apply {
                    color = balColor
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textSize = 12f
                    textAlign = Paint.Align.RIGHT
                    isAntiAlias = true
                }
                canvas.drawText("Balance Due:", summaryX + 12f, sumRowY, balLabelPaint)
                canvas.drawText("₹${"%.2f".format(invoice.balanceAmount)}", MARGIN_RIGHT - 12f, sumRowY, balValPaint)

                // Authorized Signatory Box
                val signY = currentY + summaryHeight + 35f
                val signLineX1 = MARGIN_RIGHT - 160f
                val signLineX2 = MARGIN_RIGHT - 10f
                canvas.drawLine(signLineX1, signY, signLineX2, signY, linePaint)

                val signTextPaint = Paint().apply {
                    color = Color.parseColor("#64748B")
                    textSize = 8f
                    textAlign = Paint.Align.CENTER
                    isAntiAlias = true
                }
                val signatoryName = if (profile.companyName.isNotBlank()) profile.companyName else "SnackRoute Pro"
                canvas.drawText("Authorized Signatory", (signLineX1 + signLineX2) / 2f, signY + 12f, signTextPaint)
                canvas.drawText("For $signatoryName", (signLineX1 + signLineX2) / 2f, signY + 22f, signTextPaint)
            }

            // ==================== 5. PAGE FOOTER (EVERY PAGE) ====================
            val footerY = MARGIN_BOTTOM + 16f
            canvas.drawLine(MARGIN_LEFT, MARGIN_BOTTOM, MARGIN_RIGHT, MARGIN_BOTTOM, linePaint)

            val footerTextPaint = Paint().apply {
                color = Color.parseColor("#94A3B8")
                textSize = 7.5f
                isAntiAlias = true
            }

            val timestamp = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
            canvas.drawText("Generated on $timestamp via SnackRoute Pro", MARGIN_LEFT, footerY, footerTextPaint)

            val pageNumberPaint = Paint(footerTextPaint).apply { textAlign = Paint.Align.RIGHT }
            canvas.drawText("Page ${pageIndex + 1} of $totalPages", MARGIN_RIGHT, footerY, pageNumberPaint)

            pdfDocument.finishPage(page)
        }

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
                    "Please find attached Payment Invoice ${invoice.invoiceNumber} for ${invoice.shopName}.\n" +
                            "Total Amount: ₹${"%.2f".format(invoice.totalAmount)} | Paid: ₹${"%.2f".format(invoice.paidAmount)} | Balance: ₹${"%.2f".format(invoice.balanceAmount)}"
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }

            val chooser = Intent.createChooser(intent, "Share Invoice PDF (${invoice.invoiceNumber})")
            if (context !is Activity) {
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to generate Invoice PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun truncateText(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 0 && paint.measureText(text.substring(0, end) + "...") > maxWidth) {
            end--
        }
        return if (end > 0) text.substring(0, end) + "..." else text
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
