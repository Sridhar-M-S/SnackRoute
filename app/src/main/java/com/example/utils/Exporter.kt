package com.example.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.LocationMaster
import com.example.data.ProductMaster
import com.example.data.ProductPrice
import com.example.data.SalesEntry
import com.example.data.ShopMaster
import com.example.data.ErrorLog
import com.example.data.DailyTask
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
        SHOPS, LOCATIONS, SALES, PRODUCTS, DAILY_TASKS, DYNAMIC_COST_ENGINE
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
        val parsedSales: List<SalesEntry> = emptyList(),
        val invalidDatesCount: Int = 0,
        val parsedProducts: List<Pair<ProductMaster, List<ProductPrice>>> = emptyList(),
        val parsedDailyTasks: List<DailyTask> = emptyList(),
        val updatedRecordsCount: Int = 0,
        val totalImagesFound: Int = 0,
        val imagesImportedSuccessfully: Int = 0,
        val imagesFailed: Int = 0,
        val imageImportReasons: List<String> = emptyList(),
        val invalidCoordinatesCount: Int = 0,
        val parsedIngredients: List<com.example.data.Ingredient> = emptyList(),
        val parsedPurchases: List<com.example.data.IngredientPurchase> = emptyList(),
        val parsedCalculations: List<com.example.data.CostCalculation> = emptyList(),
        val parsedCalculationItems: List<com.example.data.CostCalculationItem> = emptyList(),
        val isDynamicProfitEnabledSetting: Boolean? = null
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

    fun exportErrorLogs(context: Context, errorLogs: List<ErrorLog>) {
        val fileName = "Error_Logs_Export_${System.currentTimeMillis()}.xlsx"
        val file = File(context.cacheDir, fileName)

        try {
            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("Error Logs")
            
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
            
            // Header Row
            val headers = listOf("Date & Time", "Module", "Operation", "Error Type", "Error Message", "Possible Reason", "Stack Trace")
            val headerRow = sheet.createRow(0)
            for (i in headers.indices) {
                val cell = headerRow.createCell(i)
                cell.setCellValue(headers[i])
                cell.cellStyle = headerStyle
            }
            
            // Data Rows
            var rowIdx = 1
            for (log in errorLogs) {
                val row = sheet.createRow(rowIdx++)
                row.createCell(0).setCellValue(log.timestampFormatted)
                row.createCell(1).setCellValue(log.module)
                row.createCell(2).setCellValue(log.operation)
                row.createCell(3).setCellValue(log.errorType)
                row.createCell(4).setCellValue(log.errorMessage)
                row.createCell(5).setCellValue(log.possibleReason ?: "")
                row.createCell(6).setCellValue(log.stackTrace)
            }
            
            // Set fixed column widths
            sheet.setColumnWidth(0, 6000) // Date & Time
            sheet.setColumnWidth(1, 4000) // Module
            sheet.setColumnWidth(2, 6000) // Operation
            sheet.setColumnWidth(3, 5000) // Error Type
            sheet.setColumnWidth(4, 10000) // Error Message
            sheet.setColumnWidth(5, 8000) // Possible Reason
            sheet.setColumnWidth(6, 15000) // Stack Trace
            
            FileOutputStream(file).use { out ->
                workbook.write(out)
            }
            workbook.close()
            
            shareFile(context, file, "Error Logs Export")
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Export Failed: ${e.message}", Toast.LENGTH_SHORT).show()
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
                "Google Maps", "Latitude & Longitude", "Mobile", "Notes", "Image"
            )
            val headerRow = sheet.createRow(0)
            for (i in headers.indices) {
                val cell = headerRow.createCell(i)
                cell.setCellValue(headers[i])
                cell.cellStyle = headerStyle
            }
            
            // Data
            val helper = workbook.getCreationHelper()
            val drawing = sheet.createDrawingPatriarch()
            var rowIdx = 1
            for (shop in shops) {
                val row = sheet.createRow(rowIdx++)
                row.heightInPoints = 80f
                row.createCell(0).setCellValue(shop.shopNumber)
                row.createCell(1).setCellValue(shop.locationNumber)
                row.createCell(2).setCellValue(shop.storeName)
                
                // Numeric Rating & Score
                row.createCell(3).setCellValue(shop.rating.toDouble())
                row.createCell(4).setCellValue(shop.score.toDouble())
                
                row.createCell(5).setCellValue(shop.startingDateFormatted)
                row.createCell(6).setCellValue(shop.googleMapLink ?: "")
                
                // Latitude & Longitude
                val latLngString = if (shop.latitude != null && shop.longitude != null) {
                    "${shop.latitude},${shop.longitude}"
                } else {
                    ""
                }
                row.createCell(7).setCellValue(latLngString)
                
                row.createCell(8).setCellValue(shop.mobileNumber ?: "")
                row.createCell(9).setCellValue(shop.notes ?: "")
                
                // Embed the actual image
                val bytes = getBytesFromImagePath(context, shop.storeImage)
                if (bytes != null) {
                    try {
                        val anchor = helper.createClientAnchor().apply {
                            setCol1(10)
                            setRow1(row.rowNum)
                            setCol2(11)
                            setRow2(row.rowNum + 1)
                        }
                        val type = if (shop.storeImage?.endsWith(".png", ignoreCase = true) == true) {
                            Workbook.PICTURE_TYPE_PNG
                        } else {
                            Workbook.PICTURE_TYPE_JPEG
                        }
                        val pictureIdx = workbook.addPicture(bytes, type)
                        drawing.createPicture(anchor, pictureIdx)
                        row.createCell(10).setCellValue("")
                    } catch (e: Exception) {
                        e.printStackTrace()
                        row.createCell(10).setCellValue(shop.storeImage ?: "")
                    }
                } else {
                    row.createCell(10).setCellValue("")
                }
            }
            
            // Set fixed column widths
            sheet.setColumnWidth(0, 4500) // Shop Number
            sheet.setColumnWidth(1, 4500) // Location Number
            sheet.setColumnWidth(2, 7000) // Store Name
            sheet.setColumnWidth(3, 3000) // Rating
            sheet.setColumnWidth(4, 3000) // Score
            sheet.setColumnWidth(5, 5000) // Starting Date
            sheet.setColumnWidth(6, 8000) // Google Maps
            sheet.setColumnWidth(7, 6000) // Latitude & Longitude
            sheet.setColumnWidth(8, 4500) // Mobile
            sheet.setColumnWidth(9, 8000) // Notes
            sheet.setColumnWidth(10, 8000) // Image
            
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

    fun exportProducts(context: Context, products: List<ProductMaster>, allPrices: List<com.example.data.ProductPrice>) {
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
            val headers = listOf("Product ID", "Product Name", "Category", "Price/Profit Values", "Status")
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
                
                val productPrices = allPrices.filter { it.productId == prod.id }
                val priceString = productPrices.joinToString(",") { "(${it.sellingPrice.toInt()},${it.profitPerPacket.toInt()})" }
                
                row.createCell(3).setCellValue(priceString)
                row.createCell(4).setCellValue(prod.status)
            }
            
            // Set fixed column widths
            sheet.setColumnWidth(0, 4000) // Product ID
            sheet.setColumnWidth(1, 7000) // Product Name
            sheet.setColumnWidth(2, 5000) // Category
            sheet.setColumnWidth(3, 8000) // Price/Profit Values
            sheet.setColumnWidth(4, 4000) // Status
            
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

    // --- EXCEL IMPORTER FOR PRODUCT MASTER ---
    fun importProducts(context: Context, uri: Uri): ImportSummary {
        val successProducts = mutableListOf<Pair<ProductMaster, List<ProductPrice>>>()
        val errorRows = mutableListOf<List<String>>()
        
        var totalRows = 0
        var successfullyImported = 0
        var failedRowsCount = 0
        
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
            
            val nameIdx = headerMap["Product Name"]
            val catIdx = headerMap["Category"] ?: headerMap["Product Category"]
            val priceProfitIdx = headerMap["Price/Profit Values"]
            val statusIdx = headerMap["Status"]
            
            if (nameIdx == null || priceProfitIdx == null) {
                throw Exception("Missing required column headers: 'Product Name' and 'Price/Profit Values'")
            }
            
            val lastRowNum = sheet.lastRowNum
            for (r in 1..lastRowNum) {
                val row = sheet.getRow(r) ?: continue
                if (isRowEmpty(row)) continue
                
                totalRows++
                
                val name = getCellValueAsString(row, nameIdx)?.trim() ?: ""
                val cat = getCellValueAsString(row, catIdx)?.trim() ?: "Popcorn"
                val priceProfitStr = getCellValueAsString(row, priceProfitIdx)?.trim() ?: ""
                val status = getCellValueAsString(row, statusIdx)?.trim() ?: "Active"
                
                val originalRowData = listOf(name, cat, priceProfitStr, status)
                
                if (name.isEmpty() || priceProfitStr.isEmpty()) {
                    failedRowsCount++
                    errorRows.add(listOf("${r + 1}", name, priceProfitStr, "Product Name or Price/Profit Values is empty") + originalRowData)
                    continue
                }
                
                // Parse Price/Profit Values: (4,2),(8,4),(10,5)
                val prices = mutableListOf<ProductPrice>()
                try {
                    val pairs = priceProfitStr.split("),(")
                    for (pair in pairs) {
                        val cleanPair = pair.replace("(", "").replace(")", "")
                        val parts = cleanPair.split(",")
                        if (parts.size != 2) throw Exception("Invalid format")
                        val sellingPrice = parts[0].trim().toDouble()
                        val profit = parts[1].trim().toDouble()
                        prices.add(ProductPrice(productId = 0, sellingPrice = sellingPrice, profitPerPacket = profit))
                    }
                } catch (e: Exception) {
                    failedRowsCount++
                    errorRows.add(listOf("${r + 1}", name, priceProfitStr, "Invalid Price/Profit format") + originalRowData)
                    continue
                }
                
                val product = ProductMaster(productName = name, productCategory = cat, status = status)
                successProducts.add(Pair(product, prices))
                successfullyImported++
            }
            workbook.close()
        } catch (e: Exception) {
            e.printStackTrace()
            return ImportSummary(
                type = ImportType.PRODUCTS, 
                totalRows = 0, 
                successfullyImported = 0, 
                skippedRows = 0,
                failedRowsCount = 1, 
                errorReportFile = generateErrorReportGeneric(context, "Product_Import", listOf("Row", "Name", "Prices", "Reason"), listOf(listOf("1", "", "", e.message ?: "Error")))
            )
        }
        
        val errorFile = if (errorRows.isNotEmpty()) {
            generateErrorReportGeneric(context, "Product_Import", listOf("Row Number", "Product Name", "Original Price/Profit Values", "Reason"), errorRows)
        } else null
        
        return ImportSummary(
            type = ImportType.PRODUCTS, 
            totalRows = totalRows, 
            successfullyImported = successfullyImported, 
            skippedRows = failedRowsCount,
            failedRowsCount = failedRowsCount, 
            errorReportFile = errorFile, 
            parsedProducts = successProducts
        )
    }

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

    private fun parseStartingDate(row: Row, index: Int?): Long? {
        if (index == null || index < 0) return null
        val cell = row.getCell(index) ?: return null
        if (cell.cellType == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.dateCellValue?.time
        }
        val strVal = getCellValueAsString(row, index)?.trim() ?: return null
        if (strVal.isEmpty()) return null
        
        strVal.toLongOrNull()?.let { return it }
        
        val formats = listOf(
            "dd-MM-yyyy", "d-M-yyyy", "dd/MM/yyyy", "d/M/yyyy", "yyyy-MM-dd",
            "dd MMM yyyy", "MM/dd/yyyy", "yyyy/MM/dd"
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
        return null
    }

    private fun getFilePathFromUri(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") {
            return uri.path
        }
        if (uri.scheme == "content") {
            val projection = arrayOf("_data")
            try {
                context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val columnIndex = cursor.getColumnIndex("_data")
                        if (columnIndex != -1) {
                            return cursor.getString(columnIndex)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return null
    }

    private fun copyImportedImage(context: Context, imagePathOrUri: String?, excelUri: Uri? = null): Pair<String?, String?> {
        if (imagePathOrUri.isNullOrBlank()) return Pair(null, "No image path provided")
        val trimmed = imagePathOrUri.trim()
        
        // 1. If it starts with content:// or file://, try opening directly
        if (trimmed.startsWith("content://", ignoreCase = true) || trimmed.startsWith("file://", ignoreCase = true)) {
            try {
                val uri = Uri.parse(trimmed)
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    return Pair(saveImageToInternal(context, inputStream, trimmed), null)
                }
            } catch (e: Exception) {
                return Pair(null, "Error opening URI: ${e.message}")
            }
        }
        
        // 2. Try to find the file in various locations
        val candidates = mutableListOf<File>()
        
        // If it's an absolute path
        if (trimmed.startsWith("/")) {
            candidates.add(File(trimmed))
        } else {
            // It's a relative path. Let's add candidates relative to various bases
            val fileNameOnly = File(trimmed).name
            
            // Base 1: Excel file parent directory (if resolvable)
            if (excelUri != null) {
                val excelPath = getFilePathFromUri(context, excelUri)
                if (excelPath != null) {
                    val excelParent = File(excelPath).parentFile
                    if (excelParent != null) {
                        candidates.add(File(excelParent, trimmed))
                        candidates.add(File(excelParent, fileNameOnly))
                    }
                }
            }
            
            // Base 2: Downloads Directory
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            candidates.add(File(downloadsDir, trimmed))
            candidates.add(File(downloadsDir, fileNameOnly))
            
            // Base 3: Pictures Directory
            val picturesDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
            candidates.add(File(picturesDir, trimmed))
            candidates.add(File(picturesDir, fileNameOnly))
            
            // Base 4: App External Files
            val extFilesDir = context.getExternalFilesDir(null)
            if (extFilesDir != null) {
                candidates.add(File(extFilesDir, trimmed))
                candidates.add(File(extFilesDir, fileNameOnly))
            }
            
            // Base 5: App Files
            candidates.add(File(context.filesDir, trimmed))
            candidates.add(File(context.filesDir, fileNameOnly))
            
            // Base 6: External Storage Root
            val extStorage = android.os.Environment.getExternalStorageDirectory()
            candidates.add(File(extStorage, trimmed))
            candidates.add(File(extStorage, fileNameOnly))
        }
        
        // Try candidate files
        for (file in candidates) {
            if (file.exists() && file.isFile) {
                try {
                    java.io.FileInputStream(file).use { inputStream ->
                        return Pair(saveImageToInternal(context, inputStream, file.name), null)
                    }
                } catch (e: Exception) {
                    continue // Try next candidate
                }
            }
        }
        
        // 3. Fallback: maybe it is a raw URI without prefix
        try {
            val uri = Uri.parse(trimmed)
            if (uri.scheme != null) {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    return Pair(saveImageToInternal(context, inputStream, trimmed), null)
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        
        return Pair(null, "Could not locate image file: $trimmed")
    }

    private fun saveImageToInternal(context: Context, inputStream: java.io.InputStream, nameHint: String): String {
        val ext = when {
            nameHint.endsWith(".png", ignoreCase = true) -> "png"
            nameHint.endsWith(".jpeg", ignoreCase = true) || nameHint.endsWith(".jpg", ignoreCase = true) -> "jpg"
            else -> "jpg" // fallback
        }
        val localFile = File(context.filesDir, "shop_img_${System.currentTimeMillis()}_${(1000..9999).random()}.$ext")
        FileOutputStream(localFile).use { out ->
            inputStream.copyTo(out)
        }
        return localFile.absolutePath
    }

    private fun saveByteArrayToInternal(context: Context, bytes: ByteArray, extHint: String): String {
        val ext = when {
            extHint.endsWith("png", ignoreCase = true) -> "png"
            extHint.endsWith("jpeg", ignoreCase = true) || extHint.endsWith("jpg", ignoreCase = true) -> "jpg"
            else -> "png" // default to png for embedded
        }
        val localFile = File(context.filesDir, "shop_img_${System.currentTimeMillis()}_${(1000..9999).random()}.$ext")
        FileOutputStream(localFile).use { out ->
            out.write(bytes)
        }
        return localFile.absolutePath
    }

    private fun getBytesFromImagePath(context: Context, pathOrUri: String?): ByteArray? {
        if (pathOrUri.isNullOrBlank()) return null
        try {
            val file = File(pathOrUri)
            if (file.exists() && file.isFile) {
                return file.readBytes()
            }
        } catch (e: Exception) {
            // Ignore
        }
        try {
            val uri = Uri.parse(pathOrUri)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                return inputStream.readBytes()
            }
        } catch (e: Exception) {
            // Ignore
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

    private fun getHeaderIndex(headerMap: Map<String, Int>, vararg aliases: String): Int? {
        for (alias in aliases) {
            val key = headerMap.keys.find { it.equals(alias, ignoreCase = true) }
            if (key != null) {
                return headerMap[key]
            }
        }
        return null
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
        
        var totalImagesFound = 0
        var imagesImportedSuccessfully = 0
        var imagesFailed = 0
        val imageImportReasons = mutableListOf<String>()
        
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
            
            val shopNoIdx = getHeaderIndex(headerMap, "Shop Number", "Shop No", "ShopNo", "Shop_Number")
            val locationNoIdx = getHeaderIndex(headerMap, "Location Number", "Location No", "LocationNo", "Location_Number")
            val storeNameIdx = getHeaderIndex(headerMap, "Store Name", "Store", "Store_Name")
            
            // Check required column headers
            val missingHeaders = mutableListOf<String>()
            if (shopNoIdx == null) missingHeaders.add("Shop Number")
            if (locationNoIdx == null) missingHeaders.add("Location Number")
            if (storeNameIdx == null) missingHeaders.add("Store Name")
            if (missingHeaders.isNotEmpty()) {
                throw Exception("Missing required column headers: ${missingHeaders.joinToString(", ")}")
            }
            
            val imageIdx = getHeaderIndex(headerMap, "Image", "Store Image", "StoreImage")
            val ratingIdx = getHeaderIndex(headerMap, "Rating")
            val scoreIdx = getHeaderIndex(headerMap, "Score")
            val startDateIdx = getHeaderIndex(headerMap, "Starting Date", "Start Date")
            val mapsIdx = getHeaderIndex(headerMap, "Google Maps", "Location", "GoogleMapLink")
            val mobileIdx = getHeaderIndex(headerMap, "Mobile", "Mobile No", "MobileNo")
            val notesIdx = getHeaderIndex(headerMap, "Notes")
            val latitudeIdx = getHeaderIndex(headerMap, "Latitude", "Lat")
            val longitudeIdx = getHeaderIndex(headerMap, "Longitude", "Lng", "Long")
            val latLngIdx = getHeaderIndex(headerMap, "Latitude & Longitude", "Coordinates", "Lat & Lng", "Lat,Lng", "Lat, Lng", "Latitude/Longitude")
            
            // Map embedded images in the first sheet
            val embeddedImagesMap = mutableMapOf<Int, Pair<ByteArray, String>>()
            val imageColIdx = imageIdx
            try {
                val drawing = (sheet.getDrawingPatriarch() ?: sheet.createDrawingPatriarch()) as? org.apache.poi.xssf.usermodel.XSSFDrawing
                if (drawing != null) {
                    val shapes = drawing.shapes
                    for (shape in shapes) {
                        if (shape is org.apache.poi.xssf.usermodel.XSSFPicture) {
                            val anchor = shape.clientAnchor as? org.apache.poi.xssf.usermodel.XSSFClientAnchor
                            if (anchor != null) {
                                val rowIdx = anchor.row1
                                val colIdx = anchor.col1
                                val pictureData = shape.pictureData
                                if (pictureData != null) {
                                    val bytes = pictureData.data
                                    val ext = pictureData.suggestFileExtension() ?: "png"
                                    if (imageColIdx == null || colIdx.toInt() == imageColIdx) {
                                        embeddedImagesMap[rowIdx] = Pair(bytes, ext)
                                    } else if (embeddedImagesMap[rowIdx] == null) {
                                        embeddedImagesMap[rowIdx] = Pair(bytes, ext)
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("SnackRouteDiagnostic", "Error reading embedded images: ${e.message}")
            }
            
            val lastRowNum = sheet.lastRowNum
            for (r in 1..lastRowNum) {
                val row = sheet.getRow(r) ?: continue
                if (isRowEmpty(row)) continue
                
                totalRows++
                
                val shopNo = if (shopNoIdx != null) getCellValueAsString(row, shopNoIdx)?.trim() ?: "" else ""
                val locationNo = if (locationNoIdx != null) getCellValueAsString(row, locationNoIdx)?.trim() ?: "" else ""
                val storeName = if (storeNameIdx != null) getCellValueAsString(row, storeNameIdx)?.trim() ?: "" else ""
                
                // Original optional values
                val imageVal = if (imageIdx != null) getCellValueAsString(row, imageIdx)?.trim() ?: "" else ""
                val ratingVal = if (ratingIdx != null) getCellValueAsString(row, ratingIdx) ?: "" else ""
                val scoreVal = if (scoreIdx != null) getCellValueAsString(row, scoreIdx) ?: "" else ""
                val startDateVal = if (startDateIdx != null) getCellValueAsString(row, startDateIdx) ?: "" else ""
                val locationVal = if (mapsIdx != null) getCellValueAsString(row, mapsIdx) ?: "" else ""
                val mobileVal = if (mobileIdx != null) getCellValueAsString(row, mobileIdx) ?: "" else ""
                val notesVal = if (notesIdx != null) getCellValueAsString(row, notesIdx) ?: "" else ""
                val latVal = if (latitudeIdx != null) getCellValueAsString(row, latitudeIdx) ?: "" else ""
                val lngVal = if (longitudeIdx != null) getCellValueAsString(row, longitudeIdx) ?: "" else ""
                val latLngVal = if (latLngIdx != null) getCellValueAsString(row, latLngIdx)?.trim() ?: "" else ""
                
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
                    
                    android.util.Log.d("SnackRouteDiagnostic", "Row ${r + 1}: Validation failed - $missingFields")
                    failedRowsCount++
                    errorRows.add(listOf("${r + 1}", shopNo, storeName, missingFields) + originalRowData)
                    continue
                }
                
                // 2. Duplicate Shop Number validation
                val shopNoUpper = shopNo.uppercase()
                if (existingShopNumbersUpper.contains(shopNoUpper) || processedShopNumbers.contains(shopNoUpper)) {
                    android.util.Log.d("SnackRouteDiagnostic", "Row ${r + 1}: Duplicate Shop Number - $shopNo")
                    duplicateShopNumbersCount++
                    errorRows.add(listOf("${r + 1}", shopNo, storeName, "Duplicate Shop Number: already exists") + originalRowData)
                    continue
                }
                
                // 3. Invalid Location Number validation
                val locNoUpper = locationNo.uppercase()
                if (!existingLocNumbersUpper.contains(locNoUpper)) {
                    android.util.Log.d("SnackRouteDiagnostic", "Row ${r + 1}: Invalid Location Number - $locationNo")
                    invalidLocationNumbersCount++
                    errorRows.add(listOf("${r + 1}", shopNo, storeName, "Location Number '$locationNo' does not exist in Location Master") + originalRowData)
                    continue
                }
                
                android.util.Log.d("SnackRouteDiagnostic", "Row ${r + 1}: Validated shop - $shopNo")
                
                // Optional parse & values
                val rating = ratingVal.toFloatOrNull() ?: 0f
                val score = scoreVal.toIntOrNull() ?: if (rating > 0f) (rating * 20).toInt() else 0
                val finalRating = if (rating == 0f && score > 0) score / 20f else rating
                
                val startingDate = parseStartingDate(row, startDateIdx)
                
                // --- Copier for image ---
                var hasMissingImage = false
                var storeImageLocalPath: String? = null
                val embeddedImage = embeddedImagesMap[r]
                
                if (embeddedImage != null) {
                    totalImagesFound++
                    try {
                        val savedPath = saveByteArrayToInternal(context, embeddedImage.first, embeddedImage.second)
                        storeImageLocalPath = savedPath
                        imagesImportedSuccessfully++
                        imageImportReasons.add("Shop $shopNo: Image imported successfully (embedded)")
                    } catch (e: Exception) {
                        hasMissingImage = true
                        imagesFailed++
                        imageImportReasons.add("Shop $shopNo: Failed to save image (embedded): ${e.message ?: "Unknown error"}")
                    }
                } else if (imageVal.isNotEmpty()) {
                    totalImagesFound++
                    val result = copyImportedImage(context, imageVal, uri)
                    if (result.first != null) {
                        storeImageLocalPath = result.first
                        imagesImportedSuccessfully++
                        imageImportReasons.add("Shop $shopNo: Image imported successfully from path")
                    } else {
                        hasMissingImage = true
                        imagesFailed++
                        val reason = result.second ?: "Image not found"
                        imageImportReasons.add("Shop $shopNo: Failed to import image: $reason")
                    }
                }
                
                if (hasMissingImage) {
                    missingImagesCount++
                }
                
                val locationLink = locationVal.ifEmpty { null }
                val mobileNo = mobileVal.ifEmpty { null }
                var latDouble = latVal.toDoubleOrNull()
                var lngDouble = lngVal.toDoubleOrNull()
                
                if (latLngVal.isNotEmpty()) {
                    val parts = latLngVal.split(",")
                    if (parts.size == 2) {
                        val latPart = parts[0].trim().toDoubleOrNull()
                        val lngPart = parts[1].trim().toDoubleOrNull()
                        if (latPart != null && lngPart != null) {
                            latDouble = latPart
                            lngDouble = lngPart
                        }
                    }
                }
                
                val shop = ShopMaster(
                    shopNumber = shopNo,
                    locationNumber = locationNo,
                    storeName = storeName,
                    storeImage = storeImageLocalPath,
                    rating = finalRating,
                    score = score,
                    startingDate = startingDate ?: System.currentTimeMillis(),
                    googleMapLink = locationLink,
                    mobileNumber = mobileNo,
                    notes = notesVal.ifEmpty { null },
                    latitude = latDouble,
                    longitude = lngDouble
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
            parsedShops = successShops,
            totalImagesFound = totalImagesFound,
            imagesImportedSuccessfully = imagesImportedSuccessfully,
            imagesFailed = imagesFailed,
            imageImportReasons = imageImportReasons
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
        existingProducts: List<ProductMaster>,
        allPrices: List<ProductPrice>,
        existingSales: List<SalesEntry>
    ): ImportSummary {
        val successSales = mutableListOf<SalesEntry>()
        val errorRows = mutableListOf<List<String>>()
        
        var totalRows = 0
        var successfullyImported = 0
        var invalidShopsCount = 0
        var invalidProductsCount = 0
        var invalidDatesCount = 0
        var failedRowsCount = 0
        var duplicateCount = 0
        
        val shopMap = existingShops.associateBy { it.shopNumber.uppercase() }
        val productMap = existingProducts.associateBy { it.productName.uppercase() }
        val productPricesMap = allPrices.groupBy { it.productId }
        
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
            val entryDateIdx = headerMap["Entry Date (Today)"] ?: headerMap["Entry Date"] ?: headerMap["Date"]
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
            val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
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
                val entryDate = if (entryDateIdx != null) parseStartingDate(row, entryDateIdx) else null
                
                if (entryDate == null) {
                    invalidDatesCount++
                    failedRowsCount++
                    errorRows.add(listOf("${r + 1}", shopNo, prodType, "Missing or Invalid Entry Date") + originalRowData)
                    continue
                }
                
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
                
                // 4. Duplicate Sales record check (all fields match: Shop Number, Entry Date, Product, Selling Price, Packets Given, Packets Returned)
                val incomingDateFormatted = sdf.format(Date(entryDate))
                val isDuplicate = existingSales.any { existing ->
                    existing.shopNumber.equals(shopNo, ignoreCase = true) &&
                    existing.productName.equals(prodType, ignoreCase = true) &&
                    existing.ratePerPacket == ratePerPacket &&
                    existing.packetsGiven == packetsGiven &&
                    existing.packetsReturned == packetsReturned &&
                    existing.entryDateFormatted == incomingDateFormatted
                } || successSales.any { parsed ->
                    parsed.shopNumber.equals(shopNo, ignoreCase = true) &&
                    parsed.productName.equals(prodType, ignoreCase = true) &&
                    parsed.ratePerPacket == ratePerPacket &&
                    parsed.packetsGiven == packetsGiven &&
                    parsed.packetsReturned == packetsReturned &&
                    parsed.entryDateFormatted == incomingDateFormatted
                }
                
                if (isDuplicate) {
                    duplicateCount++
                    continue
                }
                
                val packetsSold = packetsGiven - packetsReturned
                
                // Find matching price
                val productPrices = productPricesMap[product.id] ?: emptyList()
                val priceConfig = productPrices.find { it.sellingPrice == ratePerPacket }
                val profitPerPacket = priceConfig?.profitPerPacket ?: 0.0
                
                val totalProfit = packetsSold * profitPerPacket
                
                val totalAmount = totalAmountStr.toDoubleOrNull() ?: (packetsSold * ratePerPacket)
                val status = "Paid" // Always Paid
                
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
            skippedRows = invalidShopsCount + invalidProductsCount + invalidDatesCount + failedRowsCount + duplicateCount,
            duplicateRecordsCount = duplicateCount,
            invalidShopNumbersCount = invalidShopsCount,
            invalidProductsCount = invalidProductsCount,
            invalidDatesCount = invalidDatesCount,
            failedRowsCount = failedRowsCount,
            errorReportFile = errorFile,
            parsedSales = successSales
        )
    }

    data class ProductPerformanceExportItem(
        val productName: String,
        val sellingPrice: Double,
        val packetsSold: Int,
        val revenue: Double,
        val estimatedProfit: Double
    )

    fun exportProductPerformance(context: Context, items: List<ProductPerformanceExportItem>) {
        val fileName = "Product_Performance_${System.currentTimeMillis()}.xlsx"
        val file = File(context.cacheDir, fileName)

        try {
            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("Product Performance")
            
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
            val headers = listOf("Product Name", "Selling Price", "Packets Sold", "Revenue", "Estimated Profit")
            val headerRow = sheet.createRow(0)
            for (i in headers.indices) {
                val cell = headerRow.createCell(i)
                cell.setCellValue(headers[i])
                cell.cellStyle = headerStyle
            }
            
            // Data
            var rowIdx = 1
            for (item in items) {
                val row = sheet.createRow(rowIdx++)
                row.createCell(0).setCellValue(item.productName)
                row.createCell(1).setCellValue("₹" + item.sellingPrice.toString())
                row.createCell(2).setCellValue(item.packetsSold.toDouble())
                row.createCell(3).setCellValue(item.revenue)
                row.createCell(4).setCellValue(item.estimatedProfit)
            }
            
            // Column widths
            sheet.setColumnWidth(0, 7000)
            sheet.setColumnWidth(1, 4000)
            sheet.setColumnWidth(2, 4000)
            sheet.setColumnWidth(3, 4000)
            sheet.setColumnWidth(4, 4500)
            
            FileOutputStream(file).use { out ->
                workbook.write(out)
            }
            workbook.close()
            
            shareFile(context, file, "Product Performance Export")
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Export Failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    data class MonthlyTimelineExportItem(
        val month: String,
        val packetsDistributed: Int,
        val salesAmount: Double,
        val estimatedProfit: Double
    )

    fun exportMonthlyTimeline(context: Context, items: List<MonthlyTimelineExportItem>) {
        val fileName = "Monthly_Timeline_${System.currentTimeMillis()}.xlsx"
        val file = File(context.cacheDir, fileName)

        try {
            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("Monthly Timeline")
            
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
            val headers = listOf("Month", "Total Packets Distributed", "Total Sales Amount", "Total Estimated Profit")
            val headerRow = sheet.createRow(0)
            for (i in headers.indices) {
                val cell = headerRow.createCell(i)
                cell.setCellValue(headers[i])
                cell.cellStyle = headerStyle
            }
            
            // Data
            var rowIdx = 1
            for (item in items) {
                val row = sheet.createRow(rowIdx++)
                row.createCell(0).setCellValue(item.month)
                row.createCell(1).setCellValue(item.packetsDistributed.toDouble())
                row.createCell(2).setCellValue(item.salesAmount)
                row.createCell(3).setCellValue(item.estimatedProfit)
            }
            
            // Column widths
            sheet.setColumnWidth(0, 6000)
            sheet.setColumnWidth(1, 6500)
            sheet.setColumnWidth(2, 5500)
            sheet.setColumnWidth(3, 5500)
            
            FileOutputStream(file).use { out ->
                workbook.write(out)
            }
            workbook.close()
            
            shareFile(context, file, "Monthly Distribution Timeline Export")
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Export Failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // --- DAILY TASK EXPORTS & IMPORTS ---
    fun exportDailyTasks(context: Context, tasks: List<DailyTask>) {
        val fileName = "Daily_Tasks_Export_${System.currentTimeMillis()}.xlsx"
        val file = File(context.cacheDir, fileName)

        try {
            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("Daily Tasks")
            
            // Header styling
            val headerFont = workbook.createFont().apply {
                bold = true
                color = IndexedColors.WHITE.getIndex()
            }
            val headerStyle = workbook.createCellStyle().apply {
                setFont(headerFont)
                fillForegroundColor = IndexedColors.TEAL.getIndex()
                fillPattern = FillPatternType.SOLID_FOREGROUND
                alignment = HorizontalAlignment.CENTER
            }
            
            // Header Row
            val headers = listOf("Task ID", "Title", "Description", "Is Completed", "Task Date", "Reminder Time", "Is Reminder Enabled")
            val headerRow = sheet.createRow(0)
            for (i in headers.indices) {
                val cell = headerRow.createCell(i)
                cell.setCellValue(headers[i])
                cell.cellStyle = headerStyle
            }
            
            // Data Rows
            var rowIdx = 1
            for (task in tasks) {
                val row = sheet.createRow(rowIdx++)
                row.createCell(0).setCellValue(task.id.toDouble())
                row.createCell(1).setCellValue(task.title)
                row.createCell(2).setCellValue(task.description)
                row.createCell(3).setCellValue(if (task.isCompleted) "Yes" else "No")
                row.createCell(4).setCellValue(task.taskDate)
                row.createCell(5).setCellValue(task.reminderTime ?: "")
                row.createCell(6).setCellValue(if (task.isReminderEnabled) "Yes" else "No")
            }
            
            // Set fixed column widths
            sheet.setColumnWidth(0, 3000) // Task ID
            sheet.setColumnWidth(1, 8000) // Title
            sheet.setColumnWidth(2, 10000) // Description
            sheet.setColumnWidth(3, 4000) // Is Completed
            sheet.setColumnWidth(4, 4000) // Task Date
            sheet.setColumnWidth(5, 4500) // Reminder Time
            sheet.setColumnWidth(6, 5000) // Is Reminder Enabled
            
            FileOutputStream(file).use { out ->
                workbook.write(out)
            }
            workbook.close()
            
            shareFile(context, file, "Daily Tasks Export")
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Export Failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun importDailyTasks(
        context: Context,
        uri: Uri,
        existingTasks: List<DailyTask>
    ): ImportSummary {
        val errorRows = mutableListOf<List<String>>()
        val successTasks = mutableListOf<DailyTask>()
        
        var totalRows = 0
        var successfullyImported = 0
        var duplicateRecordsCount = 0
        var failedRowsCount = 0
        var updatedRecordsCount = 0

        val existingTasksById = existingTasks.associateBy { it.id }
        val existingTasksByTitleAndDate = existingTasks.groupBy { Pair(it.title.lowercase().trim(), it.taskDate) }

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
            
            val idIdx = getHeaderIndex(headerMap, "Task ID", "ID", "TaskId", "Task_ID")
            val titleIdx = getHeaderIndex(headerMap, "Title", "Task Title", "TaskTitle", "Task_Title")
            val descIdx = getHeaderIndex(headerMap, "Description", "Task Description", "TaskDescription")
            val completedIdx = getHeaderIndex(headerMap, "Is Completed", "Completed", "IsCompleted", "Status")
            val dateIdx = getHeaderIndex(headerMap, "Task Date", "Date", "TaskDate", "Task_Date")
            val reminderTimeIdx = getHeaderIndex(headerMap, "Reminder Time", "Time", "ReminderTime")
            val reminderEnabledIdx = getHeaderIndex(headerMap, "Is Reminder Enabled", "Reminder Enabled", "IsReminderEnabled", "ReminderEnabled")
            
            val missingHeaders = mutableListOf<String>()
            if (titleIdx == null) missingHeaders.add("Title")
            if (dateIdx == null) missingHeaders.add("Task Date")
            if (missingHeaders.isNotEmpty()) {
                throw Exception("Missing required column headers: ${missingHeaders.joinToString(", ")}")
            }
            
            val lastRowNum = sheet.lastRowNum
            for (r in 1..lastRowNum) {
                val row = sheet.getRow(r) ?: continue
                if (isRowEmpty(row)) continue
                
                totalRows++
                
                val taskIdVal = if (idIdx != null) getCellValueAsString(row, idIdx)?.trim() ?: "" else ""
                val title = if (titleIdx != null) getCellValueAsString(row, titleIdx)?.trim() ?: "" else ""
                val desc = if (descIdx != null) getCellValueAsString(row, descIdx)?.trim() ?: "" else ""
                val completedVal = if (completedIdx != null) getCellValueAsString(row, completedIdx)?.trim() ?: "" else ""
                val dateVal = if (dateIdx != null) getCellValueAsString(row, dateIdx)?.trim() ?: "" else ""
                val reminderTime = if (reminderTimeIdx != null) getCellValueAsString(row, reminderTimeIdx)?.trim() ?: "" else ""
                val reminderEnabledVal = if (reminderEnabledIdx != null) getCellValueAsString(row, reminderEnabledIdx)?.trim() ?: "" else ""
                
                val originalRowData = listOf(taskIdVal, title, desc, completedVal, dateVal, reminderTime, reminderEnabledVal)
                
                if (title.isEmpty() || dateVal.isEmpty()) {
                    val missing = buildString {
                        if (title.isEmpty()) append("Title is empty. ")
                        if (dateVal.isEmpty()) append("Task Date is empty. ")
                    }.trim()
                    failedRowsCount++
                    errorRows.add(listOf("${r + 1}", title, dateVal, "Required fields missing: $missing") + originalRowData)
                    continue
                }
                
                // Validate date format: yyyy-MM-dd
                val dateRegex = Regex("^\\d{4}-\\d{2}-\\d{2}$")
                if (!dateRegex.matches(dateVal)) {
                    failedRowsCount++
                    errorRows.add(listOf("${r + 1}", title, dateVal, "Invalid date format. Expected: yyyy-MM-dd") + originalRowData)
                    continue
                }
                
                // Validate reminder time format if not empty: HH:mm
                var formattedReminderTime: String? = null
                if (reminderTime.isNotEmpty()) {
                    val timeRegex = Regex("^\\d{2}:\\d{2}$")
                    if (!timeRegex.matches(reminderTime)) {
                        failedRowsCount++
                        errorRows.add(listOf("${r + 1}", title, dateVal, "Invalid reminder time format. Expected: HH:mm") + originalRowData)
                        continue
                    }
                    formattedReminderTime = reminderTime
                }
                
                val isCompleted = completedVal.lowercase() in listOf("yes", "true", "1", "completed", "y")
                val isReminderEnabled = reminderEnabledVal.lowercase() in listOf("yes", "true", "1", "enabled", "y")
                
                // Check if updating an existing record or creating a new one
                var matchedTask: DailyTask? = null
                val idParsed = taskIdVal.toDoubleOrNull()?.toInt() ?: taskIdVal.toIntOrNull()
                if (idParsed != null && idParsed > 0) {
                    matchedTask = existingTasksById[idParsed]
                }
                
                if (matchedTask == null) {
                    // Match by title + date
                    val titleLower = title.lowercase().trim()
                    val matchedGroup = existingTasksByTitleAndDate[Pair(titleLower, dateVal)]
                    if (!matchedGroup.isNullOrEmpty()) {
                        matchedTask = matchedGroup.first()
                    }
                }
                
                if (matchedTask != null) {
                    // Update existing task
                    val updatedTask = matchedTask.copy(
                        title = title,
                        description = desc,
                        isCompleted = isCompleted || matchedTask.isCompleted, // Preserve task completion status (if either is true)
                        taskDate = dateVal,
                        reminderTime = formattedReminderTime,
                        isReminderEnabled = isReminderEnabled
                    )
                    successTasks.add(updatedTask)
                    updatedRecordsCount++
                } else {
                    // Create new task
                    val newTask = DailyTask(
                        title = title,
                        description = desc,
                        isCompleted = isCompleted,
                        taskDate = dateVal,
                        reminderTime = formattedReminderTime,
                        isReminderEnabled = isReminderEnabled
                    )
                    successTasks.add(newTask)
                }
                
                successfullyImported++
            }
            
            var errorReportFile: File? = null
            if (errorRows.isNotEmpty()) {
                errorReportFile = generateErrorReportGeneric(
                    context = context,
                    reportNamePrefix = "Daily_Tasks_Import",
                    headers = listOf("Row No", "Title", "Task Date", "Error Reason", "Task ID", "Title", "Description", "Is Completed", "Task Date", "Reminder Time", "Is Reminder Enabled"),
                    rows = errorRows
                )
            }
            
            return ImportSummary(
                type = ImportType.DAILY_TASKS,
                totalRows = totalRows,
                successfullyImported = successfullyImported,
                skippedRows = 0,
                duplicateRecordsCount = duplicateRecordsCount,
                failedRowsCount = failedRowsCount,
                errorReportFile = errorReportFile,
                parsedDailyTasks = successTasks,
                updatedRecordsCount = updatedRecordsCount
            )
            
        } catch (e: Exception) {
            e.printStackTrace()
            return ImportSummary(
                type = ImportType.DAILY_TASKS,
                totalRows = 0,
                successfullyImported = 0,
                skippedRows = 0,
                failedRowsCount = 1,
                imageImportReasons = listOf("Fatal Excel error: ${e.message}")
            )
        }
    }

    fun exportDynamicCostEngine(
        context: Context,
        ingredients: List<com.example.data.Ingredient>,
        purchases: List<com.example.data.IngredientPurchase>,
        calculations: List<com.example.data.CostCalculation>,
        calculationItems: List<com.example.data.CostCalculationItem>,
        isDynamicProfitEnabled: Boolean
    ) {
        val fileName = "Cost_Engine_Export_${System.currentTimeMillis()}.xlsx"
        val file = File(context.cacheDir, fileName)

        try {
            val workbook = XSSFWorkbook()
            
            // Header font and style
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

            // 1. Ingredients Sheet
            val ingredientsSheet = workbook.createSheet("Ingredients")
            val ingredientsHeaders = listOf("ID", "Name", "Variety", "Category", "Status")
            val ingredientsHeaderRow = ingredientsSheet.createRow(0)
            for (i in ingredientsHeaders.indices) {
                val cell = ingredientsHeaderRow.createCell(i)
                cell.setCellValue(ingredientsHeaders[i])
                cell.cellStyle = headerStyle
            }
            var rowIdx = 1
            for (item in ingredients) {
                val row = ingredientsSheet.createRow(rowIdx++)
                row.createCell(0).setCellValue(item.id.toDouble())
                row.createCell(1).setCellValue(item.name)
                row.createCell(2).setCellValue(item.variety)
                row.createCell(3).setCellValue(item.category)
                row.createCell(4).setCellValue(item.status)
            }
            for (i in ingredientsHeaders.indices) {
                ingredientsSheet.setColumnWidth(i, 5000)
            }

            // 2. Ingredient Purchases Sheet
            val purchasesSheet = workbook.createSheet("Ingredient Purchases")
            val purchasesHeaders = listOf(
                "Purchase ID", "Ingredient ID", "Purchase Quantity", "Unit", 
                "Purchase Price", "Purchase Date", "Supplier", "Remarks", 
                "Seal Cost", "Printing Cost", "Large Cover Distribution"
            )
            val purchasesHeaderRow = purchasesSheet.createRow(0)
            for (i in purchasesHeaders.indices) {
                val cell = purchasesHeaderRow.createCell(i)
                cell.setCellValue(purchasesHeaders[i])
                cell.cellStyle = headerStyle
            }
            rowIdx = 1
            for (item in purchases) {
                val row = purchasesSheet.createRow(rowIdx++)
                row.createCell(0).setCellValue(item.purchaseId.toDouble())
                row.createCell(1).setCellValue(item.ingredientId.toDouble())
                row.createCell(2).setCellValue(item.purchaseQuantity)
                row.createCell(3).setCellValue(item.unit)
                row.createCell(4).setCellValue(item.purchasePrice)
                row.createCell(5).setCellValue(item.purchaseDate)
                row.createCell(6).setCellValue(item.supplier ?: "")
                row.createCell(7).setCellValue(item.remarks ?: "")
                row.createCell(8).setCellValue(item.sealCost)
                row.createCell(9).setCellValue(item.printingCost)
                row.createCell(10).setCellValue(item.largeCoverDistribution.toDouble())
            }
            for (i in purchasesHeaders.indices) {
                purchasesSheet.setColumnWidth(i, 5000)
            }

            // 3. Cost Calculations Sheet
            val calcsSheet = workbook.createSheet("Cost Calculations")
            val calcsHeaders = listOf(
                "Calculation ID", "Product Price ID", "Version", "Calculation Date", 
                "Total Production Cost", "Selling Price Snapshot", "Profit Snapshot", "Remarks"
            )
            val calcsHeaderRow = calcsSheet.createRow(0)
            for (i in calcsHeaders.indices) {
                val cell = calcsHeaderRow.createCell(i)
                cell.setCellValue(calcsHeaders[i])
                cell.cellStyle = headerStyle
            }
            rowIdx = 1
            for (item in calculations) {
                val row = calcsSheet.createRow(rowIdx++)
                row.createCell(0).setCellValue(item.calculationId.toDouble())
                row.createCell(1).setCellValue(item.productPriceId.toDouble())
                row.createCell(2).setCellValue(item.version.toDouble())
                row.createCell(3).setCellValue(item.calculationDate)
                row.createCell(4).setCellValue(item.totalProductionCost)
                row.createCell(5).setCellValue(item.sellingPriceSnapshot)
                row.createCell(6).setCellValue(item.profitSnapshot)
                row.createCell(7).setCellValue(item.remarks ?: "")
            }
            for (i in calcsHeaders.indices) {
                calcsSheet.setColumnWidth(i, 5000)
            }

            // 4. Cost Calculation Items Sheet
            val itemsSheet = workbook.createSheet("Cost Calculation Items")
            val itemsHeaders = listOf(
                "Item ID", "Cost Calculation ID", "Ingredient ID", "Ingredient Name", 
                "Ingredient Variety", "Usage Quantity", "Usage Unit", 
                "Cost Per Unit Snapshot", "Calculated Cost", "Purchase Unit Snapshot"
            )
            val itemsHeaderRow = itemsSheet.createRow(0)
            for (i in itemsHeaders.indices) {
                val cell = itemsHeaderRow.createCell(i)
                cell.setCellValue(itemsHeaders[i])
                cell.cellStyle = headerStyle
            }
            rowIdx = 1
            for (item in calculationItems) {
                val row = itemsSheet.createRow(rowIdx++)
                row.createCell(0).setCellValue(item.itemId.toDouble())
                row.createCell(1).setCellValue(item.costCalculationId.toDouble())
                row.createCell(2).setCellValue(item.ingredientId.toDouble())
                row.createCell(3).setCellValue(item.ingredientName)
                row.createCell(4).setCellValue(item.ingredientVariety)
                row.createCell(5).setCellValue(item.usageQuantity)
                row.createCell(6).setCellValue(item.usageUnit)
                row.createCell(7).setCellValue(item.costPerUnitSnapshot)
                row.createCell(8).setCellValue(item.calculatedCost)
                row.createCell(9).setCellValue(item.purchaseUnitSnapshot)
            }
            for (i in itemsHeaders.indices) {
                itemsSheet.setColumnWidth(i, 5000)
            }

            // 5. Settings Sheet
            val settingsSheet = workbook.createSheet("Settings")
            val settingsHeaders = listOf("Setting Key", "Setting Value")
            val settingsHeaderRow = settingsSheet.createRow(0)
            for (i in settingsHeaders.indices) {
                val cell = settingsHeaderRow.createCell(i)
                cell.setCellValue(settingsHeaders[i])
                cell.cellStyle = headerStyle
            }
            val row1 = settingsSheet.createRow(1)
            row1.createCell(0).setCellValue("is_dynamic_profit_enabled")
            row1.createCell(1).setCellValue(isDynamicProfitEnabled.toString())
            for (i in settingsHeaders.indices) {
                settingsSheet.setColumnWidth(i, 8000)
            }

            FileOutputStream(file).use { out ->
                workbook.write(out)
            }
            workbook.close()

            shareFile(context, file, "Cost Engine Backup")
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Export Failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun importDynamicCostEngine(
        context: Context,
        uri: Uri
    ): ImportSummary {
        val errorRows = mutableListOf<List<String>>()
        var totalRows = 0
        var successfullyImported = 0
        val skippedRows = 0
        var failedRowsCount = 0

        val parsedIngredients = mutableListOf<com.example.data.Ingredient>()
        val parsedPurchases = mutableListOf<com.example.data.IngredientPurchase>()
        val parsedCalculations = mutableListOf<com.example.data.CostCalculation>()
        val parsedCalculationItems = mutableListOf<com.example.data.CostCalculationItem>()
        var parsedSettingsDynamicProfitEnabled: Boolean? = null

        try {
            context.contentResolver.openInputStream(uri).use { inputStream ->
                val workbook = XSSFWorkbook(inputStream)

                // 1. Verify existence of sheets
                val ingredientsSheet = workbook.getSheet("Ingredients")
                    ?: throw Exception("Required sheet 'Ingredients' is missing.")
                val purchasesSheet = workbook.getSheet("Ingredient Purchases")
                    ?: throw Exception("Required sheet 'Ingredient Purchases' is missing.")
                val calcsSheet = workbook.getSheet("Cost Calculations")
                    ?: throw Exception("Required sheet 'Cost Calculations' is missing.")
                val itemsSheet = workbook.getSheet("Cost Calculation Items")
                    ?: throw Exception("Required sheet 'Cost Calculation Items' is missing.")
                val settingsSheet = workbook.getSheet("Settings") // optional

                // Helper to map headers to column indices and check required columns
                fun getHeaderIndices(sheet: org.apache.poi.ss.usermodel.Sheet, required: List<String>, optional: List<String>): Map<String, Int> {
                    val headerRow = sheet.getRow(0) ?: throw Exception("Header row in sheet '${sheet.sheetName}' is missing.")
                    val map = mutableMapOf<String, Int>()
                    val missing = mutableListOf<String>()
                    
                    // Populate header map
                    for (i in 0 until headerRow.lastCellNum) {
                        val cellValue = headerRow.getCell(i)?.stringCellValue?.trim()
                        if (cellValue != null) {
                            map[cellValue.lowercase()] = i
                        }
                    }

                    // Check required
                    for (req in required) {
                        if (!map.containsKey(req.lowercase())) {
                            missing.add(req)
                        }
                    }

                    if (missing.isNotEmpty()) {
                        throw Exception("Sheet '${sheet.sheetName}' is missing required column(s): ${missing.joinToString(", ")}")
                    }

                    val result = mutableMapOf<String, Int>()
                    for (req in required) {
                        result[req] = map[req.lowercase()]!!
                    }
                    for (opt in optional) {
                        val colIndex = map[opt.lowercase()]
                        if (colIndex != null) {
                            result[opt] = colIndex
                        }
                    }
                    return result
                }

                // Helper parsers
                fun parseDouble(str: String?): Double {
                    if (str.isNullOrBlank()) return 0.0
                    return str.toDoubleOrNull() ?: 0.0
                }

                fun parseInt(str: String?): Int {
                    if (str.isNullOrBlank()) return 0
                    return str.toDoubleOrNull()?.toInt() ?: str.toIntOrNull() ?: 0
                }

                // --- Parsing Sheet 1: Ingredients ---
                val ingRequired = listOf("ID", "Name")
                val ingOptional = listOf("Variety", "Category", "Status")
                val ingIndices = getHeaderIndices(ingredientsSheet, ingRequired, ingOptional)

                for (rowIdx in 1..ingredientsSheet.lastRowNum) {
                    val row = ingredientsSheet.getRow(rowIdx) ?: continue
                    totalRows++
                    try {
                        val idStr = getCellValueAsString(row, ingIndices["ID"])
                        val name = getCellValueAsString(row, ingIndices["Name"])
                        if (name.isNullOrBlank()) {
                            throw Exception("Ingredient Name is empty.")
                        }
                        val variety = ingOptional.find { it == "Variety" }?.let { ingIndices[it]?.let { getCellValueAsString(row, it) } } ?: ""
                        val category = ingOptional.find { it == "Category" }?.let { ingIndices[it]?.let { getCellValueAsString(row, it) } } ?: "Other"
                        val status = ingOptional.find { it == "Status" }?.let { ingIndices[it]?.let { getCellValueAsString(row, it) } } ?: "Active"

                        val id = parseInt(idStr)
                        parsedIngredients.add(
                            com.example.data.Ingredient(
                                id = id,
                                name = name.trim(),
                                variety = variety.trim(),
                                category = category.trim(),
                                status = status.trim()
                            )
                        )
                        successfullyImported++
                    } catch (e: Exception) {
                        failedRowsCount++
                        errorRows.add(listOf("Row $rowIdx (Ingredients)", "", "", e.message ?: "Invalid formatting"))
                    }
                }

                // --- Parsing Sheet 2: Ingredient Purchases ---
                val purRequired = listOf("Purchase ID", "Ingredient ID", "Purchase Quantity", "Unit", "Purchase Price", "Purchase Date")
                val purOptional = listOf("Supplier", "Remarks", "Seal Cost", "Printing Cost", "Large Cover Distribution")
                val purIndices = getHeaderIndices(purchasesSheet, purRequired, purOptional)

                for (rowIdx in 1..purchasesSheet.lastRowNum) {
                    val row = purchasesSheet.getRow(rowIdx) ?: continue
                    totalRows++
                    try {
                        val purchaseIdStr = getCellValueAsString(row, purIndices["Purchase ID"])
                        val ingredientIdStr = getCellValueAsString(row, purIndices["Ingredient ID"])
                        val qtyStr = getCellValueAsString(row, purIndices["Purchase Quantity"])
                        val unit = getCellValueAsString(row, purIndices["Unit"])
                        val priceStr = getCellValueAsString(row, purIndices["Purchase Price"])
                        val date = getCellValueAsString(row, purIndices["Purchase Date"])

                        if (ingredientIdStr.isNullOrBlank() || qtyStr.isNullOrBlank() || priceStr.isNullOrBlank() || date.isNullOrBlank()) {
                            throw Exception("Missing required purchase fields.")
                        }

                        val supplier = purOptional.find { it == "Supplier" }?.let { purIndices[it]?.let { getCellValueAsString(row, it) } } ?: ""
                        val remarks = purOptional.find { it == "Remarks" }?.let { purIndices[it]?.let { getCellValueAsString(row, it) } } ?: ""
                        val sealCostStr = purOptional.find { it == "Seal Cost" }?.let { purIndices[it]?.let { getCellValueAsString(row, it) } }
                        val printingCostStr = purOptional.find { it == "Printing Cost" }?.let { purIndices[it]?.let { getCellValueAsString(row, it) } }
                        val largeCoverStr = purOptional.find { it == "Large Cover Distribution" }?.let { purIndices[it]?.let { getCellValueAsString(row, it) } }

                        parsedPurchases.add(
                            com.example.data.IngredientPurchase(
                                purchaseId = parseInt(purchaseIdStr),
                                ingredientId = parseInt(ingredientIdStr),
                                purchaseQuantity = parseDouble(qtyStr),
                                unit = unit?.trim() ?: "kg",
                                purchasePrice = parseDouble(priceStr),
                                purchaseDate = date.trim(),
                                supplier = supplier.trim(),
                                remarks = remarks.trim(),
                                sealCost = parseDouble(sealCostStr),
                                printingCost = parseDouble(printingCostStr),
                                largeCoverDistribution = parseInt(largeCoverStr).coerceAtLeast(1)
                            )
                        )
                        successfullyImported++
                    } catch (e: Exception) {
                        failedRowsCount++
                        errorRows.add(listOf("Row $rowIdx (Purchases)", "", "", e.message ?: "Invalid formatting"))
                    }
                }

                // --- Parsing Sheet 3: Cost Calculations ---
                val calcRequired = listOf("Calculation ID", "Product Price ID", "Version", "Calculation Date", "Total Production Cost", "Selling Price Snapshot", "Profit Snapshot")
                val calcOptional = listOf("Remarks")
                val calcIndices = getHeaderIndices(calcsSheet, calcRequired, calcOptional)

                for (rowIdx in 1..calcsSheet.lastRowNum) {
                    val row = calcsSheet.getRow(rowIdx) ?: continue
                    totalRows++
                    try {
                        val calcIdStr = getCellValueAsString(row, calcIndices["Calculation ID"])
                        val prodPriceIdStr = getCellValueAsString(row, calcIndices["Product Price ID"])
                        val versionStr = getCellValueAsString(row, calcIndices["Version"])
                        val date = getCellValueAsString(row, calcIndices["Calculation Date"])
                        val costStr = getCellValueAsString(row, calcIndices["Total Production Cost"])
                        val sellingStr = getCellValueAsString(row, calcIndices["Selling Price Snapshot"])
                        val profitStr = getCellValueAsString(row, calcIndices["Profit Snapshot"])

                        if (prodPriceIdStr.isNullOrBlank() || versionStr.isNullOrBlank() || date.isNullOrBlank() || costStr.isNullOrBlank() || sellingStr.isNullOrBlank() || profitStr.isNullOrBlank()) {
                            throw Exception("Missing required calculation fields.")
                        }

                        val remarks = calcOptional.find { it == "Remarks" }?.let { calcIndices[it]?.let { getCellValueAsString(row, it) } } ?: ""

                        parsedCalculations.add(
                            com.example.data.CostCalculation(
                                calculationId = parseInt(calcIdStr),
                                productPriceId = parseInt(prodPriceIdStr),
                                version = parseInt(versionStr),
                                calculationDate = date.trim(),
                                totalProductionCost = parseDouble(costStr),
                                sellingPriceSnapshot = parseDouble(sellingStr),
                                profitSnapshot = parseDouble(profitStr),
                                remarks = remarks.trim()
                            )
                        )
                        successfullyImported++
                    } catch (e: Exception) {
                        failedRowsCount++
                        errorRows.add(listOf("Row $rowIdx (Calculations)", "", "", e.message ?: "Invalid formatting"))
                    }
                }

                // --- Parsing Sheet 4: Cost Calculation Items ---
                val itemRequired = listOf("Item ID", "Cost Calculation ID", "Ingredient ID", "Ingredient Name", "Usage Quantity", "Usage Unit", "Calculated Cost")
                val itemOptional = listOf("Ingredient Variety", "Cost Per Unit Snapshot", "Purchase Unit Snapshot")
                val itemIndices = getHeaderIndices(itemsSheet, itemRequired, itemOptional)

                for (rowIdx in 1..itemsSheet.lastRowNum) {
                    val row = itemsSheet.getRow(rowIdx) ?: continue
                    totalRows++
                    try {
                        val itemIdStr = getCellValueAsString(row, itemIndices["Item ID"])
                        val calcIdStr = getCellValueAsString(row, itemIndices["Cost Calculation ID"])
                        val ingredientIdStr = getCellValueAsString(row, itemIndices["Ingredient ID"])
                        val name = getCellValueAsString(row, itemIndices["Ingredient Name"])
                        val qtyStr = getCellValueAsString(row, itemIndices["Usage Quantity"])
                        val unit = getCellValueAsString(row, itemIndices["Usage Unit"])
                        val costStr = getCellValueAsString(row, itemIndices["Calculated Cost"])

                        if (calcIdStr.isNullOrBlank() || ingredientIdStr.isNullOrBlank() || name.isNullOrBlank() || qtyStr.isNullOrBlank() || unit.isNullOrBlank() || costStr.isNullOrBlank()) {
                            throw Exception("Missing required calculation item fields.")
                        }

                        val variety = itemOptional.find { it == "Ingredient Variety" }?.let { itemIndices[it]?.let { getCellValueAsString(row, it) } } ?: ""
                        val costPerUnitStr = itemOptional.find { it == "Cost Per Unit Snapshot" }?.let { itemIndices[it]?.let { getCellValueAsString(row, it) } }
                        val purchaseUnit = itemOptional.find { it == "Purchase Unit Snapshot" }?.let { itemIndices[it]?.let { getCellValueAsString(row, it) } } ?: "kg"

                        parsedCalculationItems.add(
                            com.example.data.CostCalculationItem(
                                itemId = parseInt(itemIdStr),
                                costCalculationId = parseInt(calcIdStr),
                                ingredientId = parseInt(ingredientIdStr),
                                ingredientName = name.trim(),
                                ingredientVariety = variety.trim(),
                                usageQuantity = parseDouble(qtyStr),
                                usageUnit = unit.trim(),
                                costPerUnitSnapshot = parseDouble(costPerUnitStr),
                                calculatedCost = parseDouble(costStr),
                                purchaseUnitSnapshot = purchaseUnit.trim()
                            )
                        )
                        successfullyImported++
                    } catch (e: Exception) {
                        failedRowsCount++
                        errorRows.add(listOf("Row $rowIdx (Calculation Items)", "", "", e.message ?: "Invalid formatting"))
                    }
                }

                // --- Parsing Sheet 5: Settings ---
                if (settingsSheet != null) {
                    val setRequired = listOf("Setting Key", "Setting Value")
                    val setIndices = getHeaderIndices(settingsSheet, setRequired, emptyList())
                    for (rowIdx in 1..settingsSheet.lastRowNum) {
                        val row = settingsSheet.getRow(rowIdx) ?: continue
                        val key = getCellValueAsString(row, setIndices["Setting Key"])
                        val value = getCellValueAsString(row, setIndices["Setting Value"])
                        if (key?.lowercase()?.trim() == "is_dynamic_profit_enabled") {
                            parsedSettingsDynamicProfitEnabled = value?.lowercase()?.trim() == "true"
                        }
                    }
                }

                workbook.close()
            }

            var errorFile: File? = null
            if (errorRows.isNotEmpty()) {
                errorFile = generateErrorReportGeneric(
                    context = context,
                    reportNamePrefix = "Cost_Engine_Import",
                    headers = listOf("Row Number / Section", "", "", "Reason"),
                    rows = errorRows
                )
            }

            return ImportSummary(
                type = ImportType.DYNAMIC_COST_ENGINE,
                totalRows = totalRows,
                successfullyImported = successfullyImported,
                skippedRows = skippedRows,
                failedRowsCount = failedRowsCount,
                errorReportFile = errorFile,
                parsedIngredients = parsedIngredients,
                parsedPurchases = parsedPurchases,
                parsedCalculations = parsedCalculations,
                parsedCalculationItems = parsedCalculationItems,
                isDynamicProfitEnabledSetting = parsedSettingsDynamicProfitEnabled
            )

        } catch (e: Exception) {
            e.printStackTrace()
            return ImportSummary(
                type = ImportType.DYNAMIC_COST_ENGINE,
                totalRows = 0,
                successfullyImported = 0,
                skippedRows = 0,
                failedRowsCount = 1,
                errorReportFile = generateErrorReportGeneric(
                    context = context,
                    reportNamePrefix = "Cost_Engine_Import",
                    headers = listOf("Row Number / Section", "", "", "Reason"),
                    rows = listOf(listOf("Fatal Header Check/File Load", "", "", e.message ?: "Invalid file or structure"))
                )
            )
        }
    }
}
