package com.example.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.example.data.*
import com.example.viewmodel.POSViewModel
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BackupAndExcelUtils {

    fun requestAllFilesAccessPermission(context: Context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:" + context.packageName)
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                } catch (_: Exception) {
                    try {
                        val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    // 1. AUTO DAILY BACKUP TO UNMobilePos FOLDER
    fun performAutoDailyBackup(context: Context): String {
        return try {
            val db = AppDatabase.getDatabase(context)
            try {
                db.openHelper.writableDatabase.execSQL("PRAGMA wal_checkpoint(FULL);")
            } catch (_: Exception) {}

            val dbFile = context.getDatabasePath("unpatch_pos_db")
            if (!dbFile.exists()) {
                return "Database file not found"
            }

            val dateStr = SimpleDateFormat("ddMMyy", Locale.US).format(Date())
            val targetFileName = "$dateStr.db" // e.g., 310726.db or 010826.db

            // Target folder: Internal Storage UNMobilePos only
            val targetDirs = listOfNotNull(
                File(Environment.getExternalStorageDirectory(), "UNMobilePos"),
                context.getExternalFilesDir(null)?.let { File(it, "UNMobilePos") }
            )

            var successCount = 0

            for (dir in targetDirs) {
                try {
                    if (!dir.exists()) {
                        dir.mkdirs()
                    }

                    // Auto replace old backup files (*.db) in UNMobilePos with the new date filename
                    val existingFiles = dir.listFiles { _, name -> name.endsWith(".db", ignoreCase = true) }
                    existingFiles?.forEach { oldFile ->
                        if (oldFile.name != targetFileName) {
                            oldFile.delete() // Replace old day backup (e.g., 310726.db -> 010826.db)
                        }
                    }

                    val backupFile = File(dir, targetFileName)
                    dbFile.inputStream().use { input ->
                        FileOutputStream(backupFile).use { output ->
                            input.copyTo(output)
                        }
                    }

                    try {
                        android.media.MediaScannerConnection.scanFile(context, arrayOf(backupFile.absolutePath), null, null)
                    } catch (_: Exception) {}

                    successCount++
                } catch (_: Exception) {
                    // Ignore directory permission failures on specific paths
                }
            }

            if (successCount > 0) {
                "Auto backup success: $targetFileName"
            } else {
                "Auto backup failed to write to folder"
            }
        } catch (e: Exception) {
            "Auto backup error: ${e.message}"
        }
    }

    // CSV Helper function to escape text fields for CSV
    private fun escapeCsvField(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    // 2. EXCEL EXPORT FUNCTIONS
    fun exportProductsToCSV(context: Context, products: List<Product>): String {
        val sb = StringBuilder()
        sb.append("\uFEFF") // UTF-8 BOM for Excel
        sb.append("Barcode,Product Name,Group,Purchase Price,Selling Price,Unit,Stock Quantity,Alert Quantity,Note\n")

        for (p in products) {
            sb.append("${escapeCsvField(p.barcode)},")
            sb.append("${escapeCsvField(p.name)},")
            sb.append("${escapeCsvField(p.groupName)},")
            sb.append("${p.purchasePrice},")
            sb.append("${p.sellingPrice},")
            sb.append("${escapeCsvField(p.unit)},")
            sb.append("${p.quantity},")
            sb.append("${p.alertQuantity},")
            sb.append("${escapeCsvField(p.note)}\n")
        }
        return saveCsvToFolder(context, "UN_POS_Products_${SimpleDateFormat("ddMMyy", Locale.US).format(Date())}.csv", sb.toString())
    }

    fun exportCustomersToCSV(context: Context, customers: List<Customer>): String {
        val sb = StringBuilder()
        sb.append("\uFEFF")
        sb.append("Customer Name,Phone,Address,Note\n")

        for (c in customers) {
            sb.append("${escapeCsvField(c.name)},")
            sb.append("${escapeCsvField(c.phone)},")
            sb.append("${escapeCsvField(c.address)},")
            sb.append("${escapeCsvField(c.note)}\n")
        }
        return saveCsvToFolder(context, "UN_POS_Customers_${SimpleDateFormat("ddMMyy", Locale.US).format(Date())}.csv", sb.toString())
    }

    fun exportSuppliersToCSV(context: Context, suppliers: List<Supplier>): String {
        val sb = StringBuilder()
        sb.append("\uFEFF")
        sb.append("Supplier Name,Phone,Address,Note\n")

        for (s in suppliers) {
            sb.append("${escapeCsvField(s.name)},")
            sb.append("${escapeCsvField(s.phone)},")
            sb.append("${escapeCsvField(s.address)},")
            sb.append("${escapeCsvField(s.note)}\n")
        }
        return saveCsvToFolder(context, "UN_POS_Suppliers_${SimpleDateFormat("ddMMyy", Locale.US).format(Date())}.csv", sb.toString())
    }

    fun exportSalesToCSV(context: Context, vouchers: List<Voucher>): String {
        val sb = StringBuilder()
        sb.append("\uFEFF")
        sb.append("Voucher No,Date,Customer,Cashier,Payment Method,Total Items,Total Amount,Paid Amount,Change Amount,Note\n")

        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US)
        for (v in vouchers) {
            val dateStr = sdf.format(Date(v.timestamp))
            sb.append("${escapeCsvField(v.receiptNo)},")
            sb.append("${escapeCsvField(dateStr)},")
            sb.append("${escapeCsvField(v.customerName)},")
            sb.append("${escapeCsvField(v.cashierName)},")
            sb.append("${escapeCsvField(v.paymentMethod)},")
            sb.append("${v.totalItems},")
            sb.append("${v.totalAmount},")
            sb.append("${v.paidAmount},")
            sb.append("${v.changeAmount},")
            sb.append("${escapeCsvField(v.note)}\n")
        }
        return saveCsvToFolder(context, "UN_POS_Sales_${SimpleDateFormat("ddMMyy", Locale.US).format(Date())}.csv", sb.toString())
    }

    fun exportExpensesToCSV(context: Context, expenses: List<Expense>): String {
        val sb = StringBuilder()
        sb.append("\uFEFF")
        sb.append("Category,Description,Amount,Payment Method,Date,Note\n")

        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US)
        for (e in expenses) {
            val dateStr = if (e.dateString.isNotEmpty()) e.dateString else sdf.format(Date(e.timestamp))
            sb.append("${escapeCsvField(e.categoryName)},")
            sb.append("${escapeCsvField(e.description)},")
            sb.append("${e.amount},")
            sb.append("${escapeCsvField(e.paymentMethod)},")
            sb.append("${escapeCsvField(dateStr)},")
            sb.append("${escapeCsvField(e.note)}\n")
        }
        return saveCsvToFolder(context, "UN_POS_Expenses_${SimpleDateFormat("ddMMyy", Locale.US).format(Date())}.csv", sb.toString())
    }

    private fun saveCsvToFolder(context: Context, fileName: String, content: String): String {
        val targetDirs = listOfNotNull(
            File(Environment.getExternalStorageDirectory(), "UNMobilePos/Excel"),
            context.getExternalFilesDir(null)?.let { File(it, "UNMobilePos/Excel") }
        )

        var savedPath = ""
        for (dir in targetDirs) {
            try {
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)
                file.writeText(content, Charsets.UTF_8)
                try {
                    android.media.MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
                } catch (_: Exception) {}
                if (savedPath.isEmpty()) savedPath = file.name
            } catch (_: Exception) {}
        }
        return if (savedPath.isNotEmpty()) "Excel Export Successful: $fileName (Saved in UNMobilePos folder)" else "Export Failed"
    }

    // CSV Line Parser (handles quoted values with commas inside)
    private fun parseCsvLine(line: String): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false

        for (i in 0 until line.length) {
            val c = line[i]
            if (c == '"') {
                inQuotes = !inQuotes
            } else if (c == ',' && !inQuotes) {
                tokens.add(sb.toString().trim())
                sb.clear()
            } else {
                sb.append(c)
            }
        }
        tokens.add(sb.toString().trim())
        return tokens
    }

    // 3. EXCEL IMPORT FUNCTIONS
    fun importProductsFromCSV(context: Context, uri: Uri, viewModel: POSViewModel): Int {
        var count = 0
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
                var isFirstLine = true
                reader.forEachLine { rawLine ->
                    val line = rawLine.replace("\uFEFF", "").trim()
                    if (line.isNotEmpty()) {
                        if (isFirstLine) {
                            isFirstLine = false
                            if (line.contains("Name", ignoreCase = true) || line.contains("Barcode", ignoreCase = true)) {
                                return@forEachLine
                            }
                        }
                        val tokens = parseCsvLine(line)
                        if (tokens.size >= 2) {
                            val barcode = tokens.getOrNull(0) ?: ""
                            val name = tokens.getOrNull(1) ?: ""
                            val groupName = tokens.getOrNull(2) ?: "General"
                            val purchasePrice = tokens.getOrNull(3)?.toDoubleOrNull() ?: 0.0
                            val sellingPrice = tokens.getOrNull(4)?.toDoubleOrNull() ?: 0.0
                            val unit = tokens.getOrNull(5) ?: ""
                            val quantity = tokens.getOrNull(6)?.toIntOrNull() ?: 0
                            val alertQuantity = tokens.getOrNull(7)?.toIntOrNull() ?: 5
                            val note = tokens.getOrNull(8) ?: ""

                            if (name.isNotEmpty()) {
                                val product = Product(
                                    barcode = barcode,
                                    name = name,
                                    groupName = groupName,
                                    purchasePrice = purchasePrice,
                                    sellingPrice = sellingPrice,
                                    unit = unit,
                                    quantity = quantity,
                                    alertQuantity = alertQuantity,
                                    note = note,
                                    trackStock = true
                                )
                                viewModel.insertDirectProduct(product)
                                count++
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return count
    }

    fun importCustomersFromCSV(context: Context, uri: Uri, viewModel: POSViewModel): Int {
        var count = 0
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
                var isFirstLine = true
                reader.forEachLine { rawLine ->
                    val line = rawLine.replace("\uFEFF", "").trim()
                    if (line.isNotEmpty()) {
                        if (isFirstLine) {
                            isFirstLine = false
                            if (line.contains("Customer", ignoreCase = true) || line.contains("Name", ignoreCase = true)) {
                                return@forEachLine
                            }
                        }
                        val tokens = parseCsvLine(line)
                        if (tokens.isNotEmpty()) {
                            val name = tokens.getOrNull(0) ?: ""
                            val phone = tokens.getOrNull(1) ?: ""
                            val address = tokens.getOrNull(2) ?: ""
                            val note = tokens.getOrNull(3) ?: ""
                            if (name.isNotEmpty()) {
                                viewModel.addCustomer(name = name, phone = phone, address = address, note = note, onSuccess = {})
                                count++
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return count
    }

    fun importSuppliersFromCSV(context: Context, uri: Uri, viewModel: POSViewModel): Int {
        var count = 0
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
                var isFirstLine = true
                reader.forEachLine { rawLine ->
                    val line = rawLine.replace("\uFEFF", "").trim()
                    if (line.isNotEmpty()) {
                        if (isFirstLine) {
                            isFirstLine = false
                            if (line.contains("Supplier", ignoreCase = true) || line.contains("Name", ignoreCase = true)) {
                                return@forEachLine
                            }
                        }
                        val tokens = parseCsvLine(line)
                        if (tokens.isNotEmpty()) {
                            val name = tokens.getOrNull(0) ?: ""
                            val phone = tokens.getOrNull(1) ?: ""
                            val address = tokens.getOrNull(2) ?: ""
                            val note = tokens.getOrNull(3) ?: ""
                            if (name.isNotEmpty()) {
                                viewModel.addSupplier(name = name, phone = phone, address = address, note = note, onSuccess = {})
                                count++
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return count
    }
}
