package com.example.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.LocationMaster
import com.example.data.ProductMaster
import com.example.data.SalesEntry
import com.example.data.ShopMaster
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.ss.usermodel.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Exporter {

    data class ErrorReportRow(
        val rowNum: Int,
        val shopNo: String,
        val storeName: String,
        val reason: String,
        val originalRowData: List<String>
    )

    enum class ImportType {
        SHOPS, LOCATIONS, SALES
    }

    data class ImportSummary(
        val type: ImportType,
        val totalRows: Int,
        val successfullyImported: Int,
        val skippedRows: Int,
        val duplicateRecordsCount: Int = 0,
        val invalidLocationNumbersCount: Int = 0,
        val invalidShopNumbersCount: Int = 0,
        val invalidProductsCount: Int = 0,
        val missingImagesCount: Int = 0,
        val failedRowsCount: Int = 0,
        val errorReportFile: File? = null,
        val parsedShops: List<ShopMaster> = emptyList(),
        val parsedLocations: List<LocationMaster> = emptyList(),
        val parsedSales: List<SalesEntry> = emptyList()
    )

    fun shareFile(context: Context, file: File, title: String) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "com.example.snackroutepro.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Export via"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error exporting: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun exportLocations(context: Context, locations: List<LocationMaster>) {
        val fileName = "Locations_Export_${System.currentTimeMillis()}.xlsx"
        val file = File(context.cacheDir, fileName)

        try {
            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("Locations")
            
            // Header styling
            val headerFont = workbook.createFont().apply {
                bold = true
                color = IndexedColors.WHITE.getIndex()
            }
            val headerStyle = workbook.createCellStyle().apply {
                setFont(headerFont)
                fillForegroundColor = IndexedColors.DARK_BLUE.getIndex()
                fillPattern = FillPatternType.SOLID_FOREGROUND
                alignment = HorizontalAlignment.CENTER
            }
            
            // Header Row
            val headers = listOf("Location Number", "Location Name")
            val headerRow = sheet.createRow(0)
            for (i in headers.indices) {
                val cell = headerRow.createCell(i)
                cell.setCellValue(headers[i])
                cell.cellStyle = headerStyle
            }
            
            // Data Rows
            var rowIdx = 1
            for (loc in locations) {
                val row = sheet.createRow(rowIdx++)
                row.createCell(0).setCellValue(loc.locationNumber)
                row.createCell(1).setCellValue(loc.locationName)
            }
            
            // Set fixed column widths
            sheet.setColumnWidth(0, 5000) // Location Number
            sheet.setColumnWidth(1, 8000) // Location Name
            
            FileOutputStream(file).use { out ->
                workbook.write(out)
            }
            workbook.close()
            
            shareFile(context, file, "Locations Export")
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Export Failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun exportShops(context: Context, shops: List<ShopMaster>) {
        val fileName = "Shops_Export_${System.currentTimeMillis()}.xlsx"
        val file = File(context.cacheDir, fileName)

        try {
            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("Shops")
            
            // Styles
            val headerFont = workbook.createFont().apply {
                bold = true
                color = IndexedColors.WHITE.getIndex()
            }
            val headerStyle = workbook.createCellStyle().apply {
                setFont(headerFont)
                fillForegroundColor = IndexedColors.DARK_BLUE.getIndex()
                fillPattern = FillPatternType.SOLID_FOREGROUND
                alignment = HorizontalAlignment.CENTER
            }
            
            // Headers
            val headers = listOf(
                "Shop Number", "Location Number", "Store Name", 
                "Rating", "Score", "Starting Date", 
                "Google Maps", "Mobile", "Notes"
            )
            val headerRow = sheet.createRow(0)
            for (i in headers.indices) {
                val cell = headerRow.createCell(i)
                cell.setCellValue(headers[i])
                cell.cellStyle = headerStyle
            }
            
            // Data
            var rowIdx = 1
            for (shop in shops) {
                val row = sheet.createRow(rowIdx++)
                row.createCell(0).setCellValue(shop.shopNumber)
                row.createCell(1).setCellValue(shop.locationNumber)
                row.createCell(2).setCellValue(shop.storeName)
                
                // Numeric Rating & Score
                row.createCell(3).setCellValue(shop.rating.toDouble())
                row.createCell(4).setCellValue(shop.score.toDouble())
                
                row.createCell(5).setCellValue(shop.startingDateFormatted)
                row.createCell(6).setCellValue(shop.googleMapLink ?: "")
                row.createCell(7).setCellValue(shop.mobileNumber ?: "")
                row.createCell(8).setCellValue(shop.notes ?: "")
            }
            
            // Set fixed column widths
            sheet.setColumnWidth(0, 4500) // Shop Number
            sheet.setColumnWidth(1, 4500) // Location Number
            sheet.setColumnWidth(2, 7000) // Store Name
            sheet.setColumnWidth(3, 3000) // Rating
            sheet.setColumnWidth(4, 3000) // Score
            sheet.setColumnWidth(5, 5000) // Starting Date
            sheet.setColumnWidth(6, 8000) // Google Maps
            sheet.setColumnWidth(7, 4500) // Mobile
            sheet.setColumnWidth(8, 8000) // Notes
            
            FileOutputStream(file).use { out ->
                workbook.write(out)
            }
            workbook.close()
            
            shareFile(context, file, "Shops Export")
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Export Failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun exportProducts(context: Context, products: List<ProductMaster>) {
        val fileName = "Products_Export_${System.currentTimeMillis()}.xlsx"
        val file = File(context.cacheDir, fileName)

        try {
            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("Products")
            
            // Styles
            val headerFont = workbook.createFont().apply {
                bold = true
                color = IndexedColors.WHITE.getIndex()
            }
            val headerStyle = workbook.createCellStyle().apply {
                setFont(headerFont)
                fillForegroundColor = IndexedColors.DARK_BLUE.getIndex()
                fillPattern = FillPatternType.SOLID_FOREGROUND
                alignment = HorizontalAlignment.CENTER
            }
            
            // Headers
            val headers = listOf("Product ID", "Product Name", "Category", "Selling Price", "Profit Per Packet", "Status")
            val headerRow = sheet.createRow(0)
            for (i in headers.indices) {
                val cell = headerRow.createCell(i)
                cell.setCellValue(headers[i])
                cell.cellStyle = headerStyle
            }
            
            // Data
            var rowIdx = 1
            for (prod in products) {
                val row = sheet.createRow(rowIdx++)
                row.createCell(0).setCellValue(prod.id.toDouble())
                row.createCell(1).setCellValue(prod.productName)
                row.createCell(2).setCellValue(prod.productCategory)
                row.createCell(3).setCellValue(prod.sellingPrice)
                row.createCell(4).setCellValue(prod.profitPerPacket)
                row.createCell(5).setCellValue(prod.status)
            }
            
            // Set fixed column widths
            sheet.setColumnWidth(0, 4000) // Product ID
            sheet.setColumnWidth(1, 7000) // Product Name
            sheet.setColumnWidth(2, 5000) // Category
            sheet.setColumnWidth(3, 4500) // Selling Price
            sheet.setColumnWidth(4, 5000) // Profit Per Packet
            sheet.setColumnWidth(5, 4000) // Status
            
            FileOutputStream(file).use { out ->
                workbook.write(out)
            }
            workbook.close()
            
            shareFile(context, file, "Products Export")
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Export Failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun exportSales(context: Context, salesList: List<SalesEntry>) {
        val fileName = "Sales_Export_${System.currentTimeMillis()}.xlsx"
        val file = File(context.cacheDir, fileName)

        try {
            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("Sales")
            
            // Styles
            val headerFont = workbook.createFont().apply {
                bold = true
                color = IndexedColors.WHITE.getIndex()
            }
            val headerStyle = workbook.createCellStyle().apply {
                setFont(headerFont)
                fillForegroundColor = IndexedColors.DARK_BLUE.getIndex()
                fillPattern = FillPatternType.SOLID_FOREGROUND
                alignment = HorizontalAlignment.CENTER
            }
            
            // Headers
            val headers = listOf(
                "Date", "Shop Number", "Shop Name", "Location Number", "Product Name",
                "Packets Given", "Packets Returned", "Packets Sold", "Rate", "Total Amount",
                "Profit Per Packet", "Total Profit", "Status", "Remarks"
            )
            val headerRow = sheet.createRow(0)
            for (i in headers.indices) {
                val cell = headerRow.createCell(i)
                cell.setCellValue(headers[i])
                cell.cellStyle = headerStyle
            }
            
            // Data
            var rowIdx = 1
            for (sales in salesList) {
                val row = sheet.createRow(rowIdx++)
                row.createCell(0).setCellValue(sales.entryDateFormatted)
                row.createCell(1).setCellValue(sales.shopNumber)
                row.createCell(2).setCellValue(sales.shopName)
                row.createCell(3).setCellValue(sales.locationNumber)
                row.createCell(4).setCellValue(sales.productName)
                
                row.createCell(5).setCellValue(sales.packetsGiven.toDouble())
                row.createCell(6).setCellValue(sales.packetsReturned.toDouble())
                row.createCell(7).setCellValue(sales.packetsSold.toDouble())
                row.createCell(8).setCellValue(sales.ratePerPacket)
                row.createCell(9).setCellValue(sales.totalAmount)
                row.createCell(10).setCellValue(sales.profitPerPacket)
                row.createCell(11).setCellValue(sales.totalProfit)
                row.createCell(12).setCellValue(sales.status)
                row.createCell(13).setCellValue(sales.remarks ?: "")
            }
            
            // Set fixed column widths
            sheet.setColumnWidth(0, 4500)  // Date
            sheet.setColumnWidth(1, 4500)  // Shop Number
            sheet.setColumnWidth(2, 7000)  // Shop Name
            sheet.setColumnWidth(3, 4500)  // Location Number
            sheet.setColumnWidth(4, 7000)  // Product Name
            sheet.setColumnWidth(5, 4500)  // Packets Given
            sheet.setColumnWidth(6, 4500)  // Packets Returned
            sheet.setColumnWidth(7, 4500)  // Packets Sold
            sheet.setColumnWidth(8, 3500)  // Rate
            sheet.setColumnWidth(9, 4500)  // Total Amount
            sheet.setColumnWidth(10, 4500) // Profit Per Packet
            sheet.setColumnWidth(11, 4500) // Total Profit
            sheet.setColumnWidth(12, 4000) // Status
            sheet.setColumnWidth(13, 8000) // Remarks
            
            FileOutputStream(file).use { out ->
                workbook.write(out)
            }
            workbook.close()
            
            shareFile(context, file, "Sales Export")
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Export Failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // --- EXCEL IMPORTER FOR SHOP MASTER ---

    private fun isRowEmpty(row: Row): Boolean {
        for (c in 0 until row.lastCellNum) {
            val cell = row.getCell(c)
            if (cell != null && cell.cellType != CellType.BLANK) {
                val valStr = getCellValueAsString(row, c)
                if (!valStr.isNullOrBlank()) {
                    return false
                }
            }
        }
        return true
    }

    private fun getCellValueAsString(row: Row, index: Int?): String? {
        if (index == null || index < 0) return null
        val cell = row.getCell(index) ?: return null
        return when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue
            CellType.NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    val date = cell.dateCellValue
                    if (date != null) {
                        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(date)
                    } else {
                        ""
                    }
                } else {
                    val doubleVal = cell.numericCellValue
                    if (doubleVal == doubleVal.toLong().toDouble()) {
                        doubleVal.toLong().toString()
                    } else {
                        doubleVal.toString()
                    }
                }
            }
            CellType.BOOLEAN -> cell.booleanCellValue.toString()
            CellType.FORMULA -> {
                try {
                    cell.stringCellValue
                } catch (e: Exception) {
                    try {
                        cell.numericCellValue.toString()
                    } catch (e2: Exception) {
                        ""
                    }
                }
            }
            else -> ""
        }
    }

    private fun parseStartingDate(row: Row, index: Int?): Long {
        if (index == null || index < 0) return System.currentTimeMillis()
        val cell = row.getCell(index) ?: return System.currentTimeMillis()
        if (cell.cellType == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.dateCellValue?.time ?: System.currentTimeMillis()
        }
        val strVal = getCellValueAsString(row, index)?.trim() ?: return System.currentTimeMillis()
        if (strVal.isEmpty()) return System.currentTimeMillis()
        
        strVal.toLongOrNull()?.let { return it }
        
        val formats = listOf(
            "dd MMM yyyy", "yyyy-MM-dd", "dd/MM/yyyy", "MM/dd/yyyy", "dd-MM-yyyy", "yyyy/MM/dd"
        )
        for (format in formats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.getDefault())
                val date = sdf.parse(strVal)
                if (date != null) return date.time
            } catch (e: Exception) {
                // Try next
            }
        }
        return System.currentTimeMillis()
    }

    private fun copyImportedImage(context: Context, imagePathOrUri: String?): String? {
        if (imagePathOrUri.isNullOrBlank()) return null
        try {
            val trimmed = imagePathOrUri.trim()
            val uri = if (trimmed.startsWith("content://") || trimmed.startsWith("file://")) {
                Uri.parse(trimmed)
            } else {
                val file = File(trimmed)
                if (file.exists() && file.isFile) {
                    Uri.fromFile(file)
                } else {
                    // Try parsing as URI anyway
                    try {
                        Uri.parse(trimmed)
                    } catch (e: Exception) {
                        null
                    }
                }
            }

            if (uri != null) {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    // Generate a persistent local filename
                    val ext = when {
                        trimmed.endsWith(".png", ignoreCase = true) -> "png"
                        trimmed.endsWith(".jpeg", ignoreCase = true) || trimmed.endsWith(".jpg", ignoreCase = true) -> "jpg"
                        else -> "jpg" // fallback
                    }
                    val localFile = File(context.filesDir, "shop_img_${System.currentTimeMillis()}_${(1000..9999).random()}.$ext")
                    FileOutputStream(localFile).use { out ->
                        inputStream.copyTo(out)
                    }
                    return localFile.absolutePath
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun generateErrorReportGeneric(
        context: Context,
        reportNamePrefix: String,
        headers: List<String>,
        rows: List<List<String>>
    ): File? {
        if (rows.isEmpty()) return null
        val fileName = "${reportNamePrefix}_Errors_${System.currentTimeMillis()}.xlsx"
        val file = File(context.cacheDir, fileName)
        try {
            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("Errors")
            
            // Header styling
            val headerFont = workbook.createFont().apply {
                bold = true
                color = IndexedColors.WHITE.getIndex()
            }
            val headerStyle = workbook.createCellStyle().apply {
                setFont(headerFont)
                fillForegroundColor = IndexedColors.RED.getIndex()
                fillPattern = FillPatternType.SOLID_FOREGROUND
                alignment = HorizontalAlignment.CENTER
            }
            
            // Headers Row
            val headerRow = sheet.createRow(0)
            for (i in headers.indices) {
                val cell = headerRow.createCell(i)
                cell.setCellValue(headers[i])
                cell.cellStyle = headerStyle
            }
            
            // Data Rows
            var rowIdx = 1
            for (rowData in rows) {
                val row = sheet.createRow(rowIdx++)
                for (j in rowData.indices) {
                    if (j < rowData.size) {
                        row.createCell(j).setCellValue(rowData[j])
                    }
                }
            }
            
            // Set fixed column widths
            for (i in headers.indices) {
                sheet.setColumnWidth(i, 6000)
            }
            
            FileOutputStream(file).use { out ->
                workbook.write(out)
            }
            workbook.close()
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun importShops(
        context: Context, 
        uri: Uri, 
        existingLocations: List<LocationMaster>, 
        existingShops: List<ShopMaster>
    ): ImportSummary {
        val errorRows = mutableListOf<List<String>>()
        val successShops = mutableListOf<ShopMaster>()
        
        var totalRows = 0
        var successfullyImported = 0
        var duplicateShopNumbersCount = 0
        var invalidLocationNumbersCount = 0
        var failedRowsCount = 0
        var missingImagesCount = 0
        
        val processedShopNumbers = mutableSetOf<String>()
        val existingShopNumbersUpper = existingShops.map { it.shopNumber.uppercase() }.toSet()
        val existingLocNumbersUpper = existingLocations.map { it.locationNumber.uppercase() }.toSet()

        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: throw Exception("Failed to open file stream")
            val workbook = XSSFWorkbook(inputStream)
            val sheet = workbook.getSheetAt(0) ?: throw Exception("Workbook is empty")
            
            val headerRow = sheet.getRow(0) ?: throw Exception("Excel file header row is missing")
            val headerMap = mutableMapOf<String, Int>()
            for (c in 0 until headerRow.lastCellNum) {
                val cell = headerRow.getCell(c)
                val headerVal = cell?.stringCellValue?.trim()
                if (!headerVal.isNullOrEmpty()) {
                    headerMap[headerVal] = c
                }
            }
            
            // Check required column headers
            val requiredHeaders = listOf("Shop No", "Location No", "Store")
            val missingHeaders = requiredHeaders.filter { !headerMap.containsKey(it) }
            if (missingHeaders.isNotEmpty()) {
                throw Exception("Missing required column headers: ${missingHeaders.joinToString(", ")}")
            }
            
            val lastRowNum = sheet.lastRowNum
            for (r in 1..lastRowNum) {
                val row = sheet.getRow(r) ?: continue
                if (isRowEmpty(row)) continue
                
                totalRows++
                
                val shopNo = getCellValueAsString(row, headerMap["Shop No"])?.trim() ?: ""
                val locationNo = getCellValueAsString(row, headerMap["Location No"])?.trim() ?: ""
                val storeName = getCellValueAsString(row, headerMap["Store"])?.trim() ?: ""
                
                // Original optional values
                val imageVal = getCellValueAsString(row, headerMap["Image"])?.trim() ?: ""
                val ratingVal = getCellValueAsString(row, headerMap["Rating"]) ?: ""
                val scoreVal = getCellValueAsString(row, headerMap["Score"]) ?: ""
                val startDateVal = getCellValueAsString(row, headerMap["Starting Date"]) ?: ""
                val locationVal = getCellValueAsString(row, headerMap["Location"]) ?: ""
                val mobileVal = getCellValueAsString(row, headerMap["Mobile No"]) ?: ""
                
                val originalRowData = listOf(
                    shopNo, locationNo, storeName, imageVal, ratingVal, scoreVal, startDateVal, locationVal, mobileVal
                )
                
                // 1. Missing required fields validation
                if (shopNo.isEmpty() || locationNo.isEmpty() || storeName.isEmpty()) {
                    val missingFields = buildString {
                        if (shopNo.isEmpty()) append("Shop No is empty. ")
                        if (locationNo.isEmpty()) append("Location No is empty. ")
                        if (storeName.isEmpty()) append("Store is empty. ")
                    }.trim()
                    
                    failedRowsCount++
                    errorRows.add(listOf("${r + 1}", shopNo, storeName, missingFields) + originalRowData)
                    continue
                }
                
                // 2. Duplicate Shop Number validation
                val shopNoUpper = shopNo.uppercase()
                if (existingShopNumbersUpper.contains(shopNoUpper) || processedShopNumbers.contains(shopNoUpper)) {
                    duplicateShopNumbersCount++
                    errorRows.add(listOf("${r + 1}", shopNo, storeName, "Duplicate Shop Number: already exists") + originalRowData)
                    continue
                }
                
                // 3. Invalid Location Number validation
                val locNoUpper = locationNo.uppercase()
                if (!existingLocNumbersUpper.contains(locNoUpper)) {
                    invalidLocationNumbersCount++
                    errorRows.add(listOf("${r + 1}", shopNo, storeName, "Location Number '$locationNo' does not exist in Location Master") + originalRowData)
                    continue
                }
                
                // Optional parse & values
                val rating = ratingVal.toFloatOrNull() ?: 0f
                val score = scoreVal.toIntOrNull() ?: if (rating > 0f) (rating * 20).toInt() else 0
                val finalRating = if (rating == 0f && score > 0) score / 20f else rating
                
                val startingDate = parseStartingDate(row, headerMap["Starting Date"])
                
                // --- Copier for image ---
                var hasMissingImage = false
                val storeImageLocalPath = if (imageVal.isNotEmpty()) {
                    val path = copyImportedImage(context, imageVal)
                    if (path == null) {
                        hasMissingImage = true
                    }
                    path
                } else {
                    null
                }
                
                if (hasMissingImage) {
                    missingImagesCount++
                }
                
                val locationLink = locationVal.ifEmpty { null }
                val mobileNo = mobileVal.ifEmpty { null }
                
                val shop = ShopMaster(
                    shopNumber = shopNo,
                    locationNumber = locationNo,
                    storeName = storeName,
                    storeImage = storeImageLocalPath,
                    rating = finalRating,
                    score = score,
                    startingDate = startingDate,
                    googleMapLink = locationLink,
                    mobileNumber = mobileNo,
                    notes = null
                )
                
                successShops.add(shop)
                processedShopNumbers.add(shopNoUpper)
                successfullyImported++
            }
            
            workbook.close()
        } catch (e: Exception) {
            e.printStackTrace()
            return ImportSummary(
                type = ImportType.SHOPS,
                totalRows = 0,
                successfullyImported = 0,
                skippedRows = 0,
                failedRowsCount = 1,
                errorReportFile = generateErrorReportGeneric(
                    context, "Shop_Import",
                    listOf("Row Number", "Shop No", "Store Name", "Reason", "Shop No Val", "Location No Val", "Store Val", "Image Val", "Rating Val", "Score Val", "Date Val", "Map Val", "Mobile Val"),
                    listOf(listOf("1", "", "", e.message ?: "Invalid file format"))
                )
            )
        }
        
        val errorFile = if (errorRows.isNotEmpty()) {
            generateErrorReportGeneric(
                context, "Shop_Import",
                listOf("Row Number", "Shop No", "Store Name", "Reason", "Shop No Val", "Location No Val", "Store Val", "Image Val", "Rating Val", "Score Val", "Date Val", "Map Val", "Mobile Val"),
                errorRows
            )
        } else {
            null
        }
        
        return ImportSummary(
            type = ImportType.SHOPS,
            totalRows = totalRows,
            successfullyImported = successfullyImported,
            skippedRows = duplicateShopNumbersCount + invalidLocationNumbersCount + failedRowsCount,
            duplicateRecordsCount = duplicateShopNumbersCount,
            invalidLocationNumbersCount = invalidLocationNumbersCount,
            missingImagesCount = missingImagesCount,
            failedRowsCount = failedRowsCount,
            errorReportFile = errorFile,
            parsedShops = successShops
        )
    }

    fun importLocations(
        context: Context,
        uri: Uri,
        existingLocations: List<LocationMaster>
    ): ImportSummary {
        val successLocations = mutableListOf<LocationMaster>()
        val errorRows = mutableListOf<List<String>>()
        
        var totalRows = 0
        var successfullyImported = 0
        var duplicateCount = 0
        var failedRowsCount = 0
        
        val processedLocNumbers = mutableSetOf<String>()
        val existingLocNumbersUpper = existingLocations.map { it.locationNumber.uppercase() }.toSet()
        
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: throw Exception("Failed to open file stream")
            val workbook = XSSFWorkbook(inputStream)
            val sheet = workbook.getSheetAt(0) ?: throw Exception("Workbook is empty")
            
            val headerRow = sheet.getRow(0) ?: throw Exception("Excel file header row is missing")
            val headerMap = mutableMapOf<String, Int>()
            for (c in 0 until headerRow.lastCellNum) {
                val cell = headerRow.getCell(c)
                val headerVal = cell?.stringCellValue?.trim()
                if (!headerVal.isNullOrEmpty()) {
                    headerMap[headerVal] = c
                }
            }
            
            val numberColIndex = headerMap["Number"] ?: headerMap["Location Number"] ?: headerMap["Code"] ?: headerMap["NumberCode"]
            val nameColIndex = headerMap["Location"] ?: headerMap["Location Name"] ?: headerMap["Name"]
            
            if (numberColIndex == null || nameColIndex == null) {
                throw Exception("Missing required column headers: 'Number' and 'Location'")
            }
            
            val lastRowNum = sheet.lastRowNum
            for (r in 1..lastRowNum) {
                val row = sheet.getRow(r) ?: continue
                if (isRowEmpty(row)) continue
                
                totalRows++
                
                val locNo = getCellValueAsString(row, numberColIndex)?.trim() ?: ""
                val locName = getCellValueAsString(row, nameColIndex)?.trim() ?: ""
                
                if (locNo.isEmpty() || locName.isEmpty()) {
                    failedRowsCount++
                    errorRows.add(listOf("${r + 1}", locNo, locName, "Location Number or Location Name is empty"))
                    continue
                }
                
                val locNoUpper = locNo.uppercase()
                if (existingLocNumbersUpper.contains(locNoUpper) || processedLocNumbers.contains(locNoUpper)) {
                    duplicateCount++
                    errorRows.add(listOf("${r + 1}", locNo, locName, "Duplicate Location Number"))
                    continue
                }
                
                val location = LocationMaster(
                    locationNumber = locNo,
                    locationName = locName
                )
                successLocations.add(location)
                processedLocNumbers.add(locNoUpper)
                successfullyImported++
            }
            
            workbook.close()
        } catch (e: Exception) {
            e.printStackTrace()
            return ImportSummary(
                type = ImportType.LOCATIONS,
                totalRows = 0,
                successfullyImported = 0,
                skippedRows = 0,
                failedRowsCount = 1,
                errorReportFile = generateErrorReportGeneric(
                    context, "Location_Import",
                    listOf("Row Number", "Location No", "Location Name", "Reason"),
                    listOf(listOf("1", "", "", e.message ?: "Invalid file format"))
                )
            )
        }
        
        val errorFile = if (errorRows.isNotEmpty()) {
            generateErrorReportGeneric(
                context, "Location_Import",
                listOf("Row Number", "Location No", "Location Name", "Reason"),
                errorRows
            )
        } else {
            null
        }
        
        return ImportSummary(
            type = ImportType.LOCATIONS,
            totalRows = totalRows,
            successfullyImported = successfullyImported,
            skippedRows = duplicateCount + failedRowsCount,
            duplicateRecordsCount = duplicateCount,
            failedRowsCount = failedRowsCount,
            errorReportFile = errorFile,
            parsedLocations = successLocations
        )
    }

    fun importSales(
        context: Context,
        uri: Uri,
        existingShops: List<ShopMaster>,
        existingProducts: List<ProductMaster>
    ): ImportSummary {
        val successSales = mutableListOf<SalesEntry>()
        val errorRows = mutableListOf<List<String>>()
        
        var totalRows = 0
        var successfullyImported = 0
        var invalidShopsCount = 0
        var invalidProductsCount = 0
        var failedRowsCount = 0
        
        val shopMap = existingShops.associateBy { it.shopNumber.uppercase() }
        val productMap = existingProducts.associateBy { it.productName.uppercase() }
        
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: throw Exception("Failed to open file stream")
            val workbook = XSSFWorkbook(inputStream)
            val sheet = workbook.getSheetAt(0) ?: throw Exception("Workbook is empty")
            
            val headerRow = sheet.getRow(0) ?: throw Exception("Excel file header row is missing")
            val headerMap = mutableMapOf<String, Int>()
            for (c in 0 until headerRow.lastCellNum) {
                val cell = headerRow.getCell(c)
                val headerVal = cell?.stringCellValue?.trim()
                if (!headerVal.isNullOrEmpty()) {
                    headerMap[headerVal] = c
                }
            }
            
            val shopNoIdx = headerMap["Shop No"] ?: headerMap["Shop Number"] ?: headerMap["ShopNo"]
            val entryDateIdx = headerMap["Entry Date"] ?: headerMap["Date"]
            val shopNameIdx = headerMap["Shop Name"] ?: headerMap["Store Name"]
            val productTypeIdx = headerMap["Product Type"] ?: headerMap["Product Name"] ?: headerMap["Product"]
            val packetsGivenIdx = headerMap["Packets Given"] ?: headerMap["Given"]
            val packetsReturnIdx = headerMap["Packets Return"] ?: headerMap["Packets Returned"] ?: headerMap["Return"]
            val rateIdx = headerMap["Rate per Packet (₹)"] ?: headerMap["Rate per Packet"] ?: headerMap["Rate"]
            val totalAmountIdx = headerMap["Total Amount (₹)"] ?: headerMap["Total Amount"] ?: headerMap["Amount"]
            val statusIdx = headerMap["Status"]
            val remarksIdx = headerMap["Remarks"] ?: headerMap["Notes"]
            
            if (shopNoIdx == null || productTypeIdx == null || packetsGivenIdx == null || rateIdx == null) {
                throw Exception("Missing required column headers: Shop No, Product Type, Packets Given, and Rate per Packet are required.")
            }
            
            val lastRowNum = sheet.lastRowNum
            for (r in 1..lastRowNum) {
                val row = sheet.getRow(r) ?: continue
                if (isRowEmpty(row)) continue
                
                totalRows++
                
                val shopNo = getCellValueAsString(row, shopNoIdx)?.trim() ?: ""
                val prodType = getCellValueAsString(row, productTypeIdx)?.trim() ?: ""
                val entryDateStr = if (entryDateIdx != null) getCellValueAsString(row, entryDateIdx)?.trim() ?: "" else ""
                val packetsGivenStr = getCellValueAsString(row, packetsGivenIdx)?.trim() ?: ""
                val packetsReturnStr = if (packetsReturnIdx != null) getCellValueAsString(row, packetsReturnIdx)?.trim() ?: "" else ""
                val rateStr = getCellValueAsString(row, rateIdx)?.trim() ?: ""
                val totalAmountStr = if (totalAmountIdx != null) getCellValueAsString(row, totalAmountIdx)?.trim() ?: "" else ""
                val statusStr = if (statusIdx != null) getCellValueAsString(row, statusIdx)?.trim() ?: "" else ""
                val remarksStr = if (remarksIdx != null) getCellValueAsString(row, remarksIdx)?.trim() ?: "" else ""
                
                val originalRowData = listOf(
                    shopNo, entryDateStr, prodType, packetsGivenStr, packetsReturnStr, rateStr, totalAmountStr, statusStr, remarksStr
                )
                
                // 1. Check basic empty required fields
                if (shopNo.isEmpty() || prodType.isEmpty() || packetsGivenStr.isEmpty() || rateStr.isEmpty()) {
                    val missing = buildString {
                        if (shopNo.isEmpty()) append("Shop No is empty. ")
                        if (prodType.isEmpty()) append("Product Type is empty. ")
                        if (packetsGivenStr.isEmpty()) append("Packets Given is empty. ")
                        if (rateStr.isEmpty()) append("Rate is empty. ")
                    }.trim()
                    failedRowsCount++
                    errorRows.add(listOf("${r + 1}", shopNo, prodType, missing) + originalRowData)
                    continue
                }
                
                // 2. Validate Shop Number exists in Shop Master
                val shop = shopMap[shopNo.uppercase()]
                if (shop == null) {
                    invalidShopsCount++
                    errorRows.add(listOf("${r + 1}", shopNo, prodType, "Shop Number does not exist in Shop Master") + originalRowData)
                    continue
                }
                
                // 3. Validate Product Name exists in Product Master
                val product = productMap[prodType.uppercase()]
                if (product == null) {
                    invalidProductsCount++
                    errorRows.add(listOf("${r + 1}", shopNo, prodType, "Product Type does not exist in Product Master") + originalRowData)
                    continue
                }
                
                // Parse optional/derived fields
                val entryDate = if (entryDateIdx != null) parseStartingDate(row, entryDateIdx) else System.currentTimeMillis()
                
                val packetsGiven = packetsGivenStr.toIntOrNull()
                val ratePerPacket = rateStr.toDoubleOrNull()
                
                if (packetsGiven == null || ratePerPacket == null || packetsGiven < 0 || ratePerPacket < 0.0) {
                    failedRowsCount++
                    errorRows.add(listOf("${r + 1}", shopNo, prodType, "Invalid numerical values for Packets Given or Rate") + originalRowData)
                    continue
                }
                
                val packetsReturned = packetsReturnStr.toIntOrNull() ?: 0
                if (packetsReturned < 0 || packetsReturned > packetsGiven) {
                    failedRowsCount++
                    errorRows.add(listOf("${r + 1}", shopNo, prodType, "Packets Returned must be >= 0 and <= Packets Given") + originalRowData)
                    continue
                }
                
                val packetsSold = packetsGiven - packetsReturned
                val profitPerPacket = product.profitPerPacket
                val totalProfit = packetsSold * profitPerPacket
                
                val totalAmount = totalAmountStr.toDoubleOrNull() ?: (packetsSold * ratePerPacket)
                val status = if (statusStr.isNotBlank()) statusStr else "Pending"
                
                val salesEntry = SalesEntry(
                    entryDate = entryDate,
                    shopNumber = shop.shopNumber,
                    shopName = shop.storeName,
                    locationNumber = shop.locationNumber,
                    productName = product.productName,
                    packetsGiven = packetsGiven,
                    packetsReturned = packetsReturned,
                    packetsSold = packetsSold,
                    ratePerPacket = ratePerPacket,
                    totalAmount = totalAmount,
                    profitPerPacket = profitPerPacket,
                    totalProfit = totalProfit,
                    status = status,
                    remarks = remarksStr.ifEmpty { null }
                )
                
                successSales.add(salesEntry)
                successfullyImported++
            }
            
            workbook.close()
        } catch (e: Exception) {
            e.printStackTrace()
            return ImportSummary(
                type = ImportType.SALES,
                totalRows = 0,
                successfullyImported = 0,
                skippedRows = 0,
                failedRowsCount = 1,
                errorReportFile = generateErrorReportGeneric(
                    context, "Sales_Import",
                    listOf("Row Number", "Shop No", "Product Type", "Reason", "Shop No Val", "Date Val", "Prod Val", "Given Val", "Return Val", "Rate Val", "Amount Val", "Status Val", "Remarks Val"),
                    listOf(listOf("1", "", "", e.message ?: "Invalid file format"))
                )
            )
        }
        
        val errorFile = if (errorRows.isNotEmpty()) {
            generateErrorReportGeneric(
                context, "Sales_Import",
                listOf("Row Number", "Shop No", "Product Type", "Reason", "Shop No Val", "Date Val", "Prod Val", "Given Val", "Return Val", "Rate Val", "Amount Val", "Status Val", "Remarks Val"),
                errorRows
            )
        } else {
            null
        }
        
        return ImportSummary(
            type = ImportType.SALES,
            totalRows = totalRows,
            successfullyImported = successfullyImported,
            skippedRows = invalidShopsCount + invalidProductsCount + failedRowsCount,
            invalidShopNumbersCount = invalidShopsCount,
            invalidProductsCount = invalidProductsCount,
            failedRowsCount = failedRowsCount,
            errorReportFile = errorFile,
            parsedSales = successSales
        )
    }
}
