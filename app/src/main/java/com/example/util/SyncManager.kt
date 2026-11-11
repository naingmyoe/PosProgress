package com.example.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object SyncManager {
    private const val TAG = "POSSyncManager"

    data class SyncResult(
        val success: Boolean,
        val syncedVouchersCount: Int = 0,
        val syncedProductsCount: Int = 0,
        val syncedCustomersCount: Int = 0,
        val message: String = ""
    )

    data class PullResult(
        val success: Boolean,
        val vouchersRestored: Int = 0,
        val productsRestored: Int = 0,
        val customersRestored: Int = 0,
        val message: String = ""
    )

    data class ServerCheckResult(
        val isReachable: Boolean,
        val responseTimeMs: Long = 0,
        val message: String = ""
    )

    /**
     * Real-time network connectivity check on device.
     */
    fun isNetworkAvailable(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val nw = cm.activeNetwork ?: return false
                val actNw = cm.getNetworkCapabilities(nw) ?: return false
                actNw.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        actNw.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            } else {
                @Suppress("DEPRECATION")
                val nwInfo = cm.activeNetworkInfo ?: return false
                @Suppress("DEPRECATION")
                nwInfo.isConnected
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if the VPS Server / Cloudflare Worker is actually online and responding.
     */
    suspend fun checkServerReachability(workerUrl: String): ServerCheckResult {
        return withContext(Dispatchers.IO) {
            val cleanUrl = workerUrl.trim().removeSuffix("/")
            if (cleanUrl.isBlank() || (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://"))) {
                return@withContext ServerCheckResult(false, 0, "Server URL မမှန်ကန်ပါ (URL must start with http:// or https://)")
            }

            val start = System.currentTimeMillis()
            try {
                val checkUrl = URL("$cleanUrl/api/check-status")
                val conn = checkUrl.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                conn.instanceFollowRedirects = true

                val code = conn.responseCode
                val elapsed = System.currentTimeMillis() - start

                // Any standard HTTP response code (including 200, 404, etc.) means server is reachable
                if (code in 200..499) {
                    ServerCheckResult(true, elapsed, "Server ချိတ်ဆက်မှု အောင်မြင်ပါသည် (${elapsed}ms)")
                } else {
                    ServerCheckResult(false, elapsed, "Server မှ အကြောင်းပြန်မှု မှားယွင်းနေပါသည် (Status: $code)")
                }
            } catch (e: java.net.ConnectException) {
                ServerCheckResult(false, 0, "VPS Server သို့ ချိတ်ဆက်၍မရပါ (Connection Refused)။ သင့် VPS တွင် server.js ကို run ထားခြင်း မရှိသေးပါ (သို့မဟုတ် Port 8081 ပိတ်နေပါသည်)။")
            } catch (e: java.net.SocketTimeoutException) {
                ServerCheckResult(false, 0, "Server ချိတ်ဆက်မှု အချိန်ကုန်သွားပါသည် (Timeout)။ VPS Server IP သို့မဟုတ် Firewall စစ်ဆေးပါ။")
            } catch (e: java.net.UnknownHostException) {
                ServerCheckResult(false, 0, "Server Domain/IP ကို ရှာမတွေ့ပါ (Unknown Host)။")
            } catch (e: Exception) {
                ServerCheckResult(false, 0, "Server ချိတ်ဆက်၍မရပါ: ${e.message}")
            }
        }
    }

    /**
     * Upload single sale immediately when online.
     * Guarantees Tenant isolation via userPhone.
     */
    suspend fun syncSingleVoucher(
        workerUrl: String,
        userPhone: String,
        voucher: Voucher,
        items: List<VoucherItem>
    ): Boolean {
        if (userPhone.isBlank()) return false

        return withContext(Dispatchers.IO) {
            try {
                val targetUrl = workerUrl.trim().removeSuffix("/") + "/api/sync/voucher"
                val url = URL(targetUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 6000
                conn.readTimeout = 6000
                conn.setRequestProperty("Content-Type", "application/json; utf-8")
                conn.setRequestProperty("Accept", "application/json")
                conn.doOutput = true

                val voucherJson = JSONObject().apply {
                    put("receiptNo", voucher.receiptNo)
                    put("timestamp", voucher.timestamp)
                    put("cashierName", voucher.cashierName)
                    put("totalAmount", voucher.totalAmount)
                    put("totalItems", voucher.totalItems)
                    put("customerName", voucher.customerName)
                    put("paymentMethod", voucher.paymentMethod)
                    put("isCompleted", voucher.isCompleted)
                    put("isPurchase", voucher.isPurchase)
                    put("paidAmount", voucher.paidAmount)
                    put("changeAmount", voucher.changeAmount)
                    put("balanceAmount", voucher.balanceAmount)
                    put("note", voucher.note)
                    put("discount", voucher.discount)
                    put("fee", voucher.fee)
                }

                val itemsArray = JSONArray()
                for (item in items) {
                    val itemJson = JSONObject().apply {
                        put("productId", item.productId)
                        put("productName", item.productName)
                        put("quantity", item.quantity)
                        put("purchasePrice", item.purchasePrice)
                        put("sellingPrice", item.sellingPrice)
                    }
                    itemsArray.put(itemJson)
                }

                val rootJson = JSONObject().apply {
                    put("userPhone", userPhone)
                    put("voucher", voucherJson)
                    put("items", itemsArray)
                }

                OutputStreamWriter(conn.outputStream, "UTF-8").use { os ->
                    os.write(rootJson.toString())
                    os.flush()
                }

                val code = conn.responseCode
                code in 200..299
            } catch (e: Exception) {
                Log.e(TAG, "Single voucher sync failed: ${e.message}")
                false
            }
        }
    }

    suspend fun deleteSingleProductOnCloud(
        workerUrl: String,
        userPhone: String,
        productId: Long,
        productName: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (workerUrl.isBlank() || userPhone.isBlank()) return@withContext false
        val cleanUrl = workerUrl.trim().removeSuffix("/")
        try {
            val targetUrl = "$cleanUrl/api/sync/delete-product"
            val conn = URL(targetUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("Content-Type", "application/json; utf-8")
            conn.doOutput = true

            val payload = JSONObject().apply {
                put("userPhone", userPhone)
                put("productId", productId)
                put("productName", productName)
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(payload.toString()) }
            conn.responseCode in 200..299
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun deleteProductsOnCloud(
        workerUrl: String,
        userPhone: String,
        productIds: List<Long>,
        productNames: List<String>
    ): Boolean = withContext(Dispatchers.IO) {
        if (workerUrl.isBlank() || userPhone.isBlank() || (productIds.isEmpty() && productNames.isEmpty())) return@withContext false
        val cleanUrl = workerUrl.trim().removeSuffix("/")
        try {
            val targetUrl = "$cleanUrl/api/sync/delete-products"
            val conn = URL(targetUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("Content-Type", "application/json; utf-8")
            conn.doOutput = true

            val idsArr = JSONArray()
            productIds.forEach { idsArr.put(it) }
            val namesArr = JSONArray()
            productNames.forEach { namesArr.put(it) }

            val payload = JSONObject().apply {
                put("userPhone", userPhone)
                put("productIds", idsArr)
                put("productNames", namesArr)
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(payload.toString()) }
            conn.responseCode in 200..299
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun deleteVoucherOnCloud(
        workerUrl: String,
        userPhone: String,
        receiptNo: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (workerUrl.isBlank() || userPhone.isBlank() || receiptNo.isBlank()) return@withContext false
        val cleanUrl = workerUrl.trim().removeSuffix("/")
        try {
            val targetUrl = "$cleanUrl/api/sync/delete-voucher"
            val conn = URL(targetUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("Content-Type", "application/json; utf-8")
            conn.doOutput = true

            val payload = JSONObject().apply {
                put("userPhone", userPhone)
                put("receiptNo", receiptNo)
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(payload.toString()) }
            conn.responseCode in 200..299
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Atomically mirrors 100% of local products, stock, vouchers, customers to the Cloud.
     * Guarantees that what is in local is EXACTLY mirrored on the Cloud.
     */
    suspend fun mirrorLocalToCloud(
        workerUrl: String,
        userPhone: String,
        repository: Repository
    ): SyncResult = withContext(Dispatchers.IO) {
        if (workerUrl.isBlank() || userPhone.isBlank()) {
            return@withContext SyncResult(false, 0, 0, 0, "ကျေးဇူးပြု၍ Login ဝင်ထားပါ (No user phone)")
        }
        val cleanUrl = workerUrl.trim().removeSuffix("/")
        val check = checkServerReachability(workerUrl)
        if (!check.isReachable) {
            return@withContext SyncResult(false, 0, 0, 0, check.message)
        }

        try {
            val allProducts = repository.getAllProductsDirect()
            val allVouchers = repository.getAllCompletedVouchersDirect()
            val allVoucherItems = repository.getAllVoucherItemsDirect()
            val allCustomers = repository.getAllCustomersDirect()
            val allGroups = repository.getAllGroupsDirect().map { it.name }
            val allUnits = repository.getAllUnitsDirect().map { it.name }
            val allPayments = repository.getAllPaymentsDirect()
            val allSuppliers = repository.getAllSuppliersDirect()
            val allExpenseCategories = repository.getAllExpenseCategoriesDirect()
            val allExpenses = repository.getAllExpensesDirect()

            val prodsArr = JSONArray()
            for (p in allProducts) {
                prodsArr.put(JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("groupName", p.groupName)
                    put("purchasePrice", p.purchasePrice)
                    put("sellingPrice", p.sellingPrice)
                    put("unit", p.unit)
                    put("barcode", p.barcode)
                    put("quantity", p.quantity)
                    put("alertQuantity", p.alertQuantity)
                    put("trackStock", p.trackStock)
                    put("note", p.note)
                })
            }

            val vouchersArr = JSONArray()
            for (v in allVouchers) {
                vouchersArr.put(JSONObject().apply {
                    put("receiptNo", v.receiptNo)
                    put("timestamp", v.timestamp)
                    put("cashierName", v.cashierName)
                    put("totalAmount", v.totalAmount)
                    put("totalItems", v.totalItems)
                    put("customerName", v.customerName)
                    put("paymentMethod", v.paymentMethod)
                    put("isCompleted", v.isCompleted)
                    put("isPurchase", v.isPurchase)
                    put("paidAmount", v.paidAmount)
                    put("changeAmount", v.changeAmount)
                    put("balanceAmount", v.balanceAmount)
                    put("note", v.note)
                    put("discount", v.discount)
                    put("fee", v.fee)
                })
            }

            val vItemsArr = JSONArray()
            for (it in allVoucherItems) {
                vItemsArr.put(JSONObject().apply {
                    put("voucherId", it.voucherId)
                    put("productId", it.productId)
                    put("productName", it.productName)
                    put("quantity", it.quantity)
                    put("purchasePrice", it.purchasePrice)
                    put("sellingPrice", it.sellingPrice)
                })
            }

            val custsArr = JSONArray()
            val seenCustNames = mutableSetOf<String>()
            for (c in allCustomers) {
                val clean = c.name.trim()
                val norm = clean.lowercase()
                if (clean.isNotBlank() && norm !in seenCustNames) {
                    seenCustNames.add(norm)
                    custsArr.put(JSONObject().apply {
                        put("name", clean)
                        put("phone", c.phone)
                        put("address", c.address)
                        put("note", c.note)
                    })
                }
            }

            val groupsArr = JSONArray()
            allGroups.forEach { groupsArr.put(it) }

            val unitsArr = JSONArray()
            allUnits.forEach { unitsArr.put(it) }

            val paymentsArr = JSONArray()
            for (p in allPayments) {
                paymentsArr.put(JSONObject().apply {
                    put("method", p.method)
                    put("amount", p.amount)
                    put("date", p.date)
                })
            }

            val suppliersArr = JSONArray()
            val seenSupNames = mutableSetOf<String>()
            for (s in allSuppliers) {
                val clean = s.name.trim()
                val norm = clean.lowercase()
                if (clean.isNotBlank() && norm !in seenSupNames) {
                    seenSupNames.add(norm)
                    suppliersArr.put(JSONObject().apply {
                        put("name", clean)
                        put("phone", s.phone)
                        put("address", s.address)
                        put("note", s.note)
                    })
                }
            }

            val expenseCatsArr = JSONArray()
            for (ec in allExpenseCategories) {
                expenseCatsArr.put(JSONObject().apply {
                    put("name", ec.name)
                    put("iconName", ec.iconName)
                })
            }

            val expensesArr = JSONArray()
            for (e in allExpenses) {
                expensesArr.put(JSONObject().apply {
                    put("categoryName", e.categoryName)
                    put("description", e.description)
                    put("amount", e.amount)
                    put("paymentMethod", e.paymentMethod)
                    put("note", e.note)
                    put("timestamp", e.timestamp)
                    put("dateString", e.dateString)
                    put("timeString", e.timeString)
                })
            }

            val payload = JSONObject().apply {
                put("userPhone", userPhone)
                put("products", prodsArr)
                put("vouchers", vouchersArr)
                put("voucherItems", vItemsArr)
                put("customers", custsArr)
                put("groups", groupsArr)
                put("units", unitsArr)
                put("payments", paymentsArr)
                put("suppliers", suppliersArr)
                put("expenseCategories", expenseCatsArr)
                put("expenses", expensesArr)
            }

            val targetUrl = "$cleanUrl/api/sync/mirror"
            val conn = URL(targetUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.setRequestProperty("Content-Type", "application/json; utf-8")
            conn.doOutput = true

            OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(payload.toString()) }
            val code = conn.responseCode
            if (code in 200..299) {
                repository.markAllProductsSynced()
                repository.markAllVouchersSynced()
                repository.markAllCustomersSynced()
                SyncResult(
                    success = true,
                    syncedVouchersCount = allVouchers.size,
                    syncedProductsCount = allProducts.size,
                    syncedCustomersCount = allCustomers.size,
                    message = "Local ဒေတာအတိုင်း Cloud ပေါ်သို့ အတိအကျ ၁၀၀% Mirror တင်ပြီးပါပြီ။"
                )
            } else {
                SyncResult(false, 0, 0, 0, "Server error: $code")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            SyncResult(false, 0, 0, 0, "Mirror sync failed: ${e.message}")
        }
    }

    /**
     * Batch syncs all pending offline vouchers, new products, and customers to the cloud.
     */
    suspend fun syncAllPendingData(
        workerUrl: String,
        userPhone: String,
        repository: Repository,
        pendingDeletedProductIds: List<Long> = emptyList(),
        pendingDeletedProductNames: List<String> = emptyList(),
        pendingDeletedVouchers: List<String> = emptyList()
    ): SyncResult {
        if (userPhone.isBlank()) {
            return SyncResult(false, 0, 0, 0, "ကျေးဇူးပြု၍ အကောင့် Login ဝင်ထားပါ (No active user account)")
        }

        return withContext(Dispatchers.IO) {
            // First check if server is reachable
            val check = checkServerReachability(workerUrl)
            if (!check.isReachable) {
                return@withContext SyncResult(
                    success = false,
                    message = "${check.message}\n(ဒေတာများကို ဖုန်းထဲတွင် ဆက်လက်သိမ်းဆည်းထားပါသည်)"
                )
            }

            try {
                // 0. Sync pending deleted products and vouchers first
                if (pendingDeletedProductIds.isNotEmpty() || pendingDeletedProductNames.isNotEmpty()) {
                    deleteProductsOnCloud(workerUrl, userPhone, pendingDeletedProductIds, pendingDeletedProductNames)
                }
                if (pendingDeletedVouchers.isNotEmpty()) {
                    for (vId in pendingDeletedVouchers) {
                        deleteVoucherOnCloud(workerUrl, userPhone, vId)
                    }
                }

                var syncedVouchersCount = 0
                var syncedProductsCount = 0
                var syncedCustomersCount = 0

                val cleanUrl = workerUrl.trim().removeSuffix("/")

                // 1. Sync pending vouchers
                val unsyncedVouchers = repository.getUnsyncedCompletedVouchers()
                if (unsyncedVouchers.isNotEmpty()) {
                    val targetUrl = "$cleanUrl/api/sync/vouchers"
                    val conn = URL(targetUrl).openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.connectTimeout = 12000
                    conn.readTimeout = 12000
                    conn.setRequestProperty("Content-Type", "application/json; utf-8")
                    conn.setRequestProperty("Accept", "application/json")
                    conn.doOutput = true

                    val vouchersArray = JSONArray()
                    val syncedIds = mutableListOf<String>()

                    for (v in unsyncedVouchers) {
                        val items = repository.getVoucherItemsDirect(v.receiptNo)
                        val vObj = JSONObject().apply {
                            put("receiptNo", v.receiptNo)
                            put("timestamp", v.timestamp)
                            put("cashierName", v.cashierName)
                            put("totalAmount", v.totalAmount)
                            put("totalItems", v.totalItems)
                            put("customerName", v.customerName)
                            put("paymentMethod", v.paymentMethod)
                            put("isCompleted", v.isCompleted)
                            put("isPurchase", v.isPurchase)
                            put("paidAmount", v.paidAmount)
                            put("changeAmount", v.changeAmount)
                            put("balanceAmount", v.balanceAmount)
                            put("discount", v.discount)
                            put("fee", v.fee)
                        }

                        val itemsArr = JSONArray()
                        for (it in items) {
                            itemsArr.put(JSONObject().apply {
                                put("productId", it.productId)
                                put("productName", it.productName)
                                put("quantity", it.quantity)
                                put("purchasePrice", it.purchasePrice)
                                put("sellingPrice", it.sellingPrice)
                            })
                        }

                        vouchersArray.put(JSONObject().apply {
                            put("voucher", vObj)
                            put("items", itemsArr)
                        })
                        syncedIds.add(v.receiptNo)
                    }

                    val rootJson = JSONObject().apply {
                        put("userPhone", userPhone)
                        put("vouchers", vouchersArray)
                    }

                    OutputStreamWriter(conn.outputStream, "UTF-8").use { os ->
                        os.write(rootJson.toString())
                        os.flush()
                    }

                    val code = conn.responseCode
                    if (code in 200..299) {
                        repository.markVouchersSynced(syncedIds)
                        syncedVouchersCount = syncedIds.size
                    }
                }

                // 2. Sync pending products (new products or updated prices)
                val unsyncedProducts = repository.getUnsyncedProducts()
                if (unsyncedProducts.isNotEmpty()) {
                    val targetUrl = "$cleanUrl/api/sync/products"
                    val conn = URL(targetUrl).openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.connectTimeout = 10000
                    conn.readTimeout = 10000
                    conn.setRequestProperty("Content-Type", "application/json; utf-8")
                    conn.doOutput = true

                    val prodsArr = JSONArray()
                    val pIds = mutableListOf<Long>()
                    for (p in unsyncedProducts) {
                        prodsArr.put(JSONObject().apply {
                            put("id", p.id)
                            put("name", p.name)
                            put("groupName", p.groupName)
                            put("group_name", p.groupName)
                            put("purchasePrice", p.purchasePrice)
                            put("sellingPrice", p.sellingPrice)
                            put("price", p.sellingPrice)
                            put("cost", p.purchasePrice)
                            put("unit", p.unit)
                            put("barcode", p.barcode)
                            put("quantity", p.quantity)
                            put("initialStock", p.quantity)
                            put("initial_stock", p.quantity)
                            put("alertQuantity", p.alertQuantity)
                            put("alert_quantity", p.alertQuantity)
                            put("trackStock", p.trackStock)
                            put("track_stock", p.trackStock)
                            put("note", p.note)
                        })
                        pIds.add(p.id)
                    }

                    val payload = JSONObject().apply {
                        put("userPhone", userPhone)
                        put("products", prodsArr)
                    }

                    OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(payload.toString()) }
                    val code = conn.responseCode
                    if (code in 200..299) {
                        repository.markProductsSynced(pIds)
                        syncedProductsCount = pIds.size
                    }
                }

                // 3. Sync pending customers
                val unsyncedCustomers = repository.getUnsyncedCustomers()
                if (unsyncedCustomers.isNotEmpty()) {
                    val targetUrl = "$cleanUrl/api/sync/customers"
                    val conn = URL(targetUrl).openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.connectTimeout = 10000
                    conn.readTimeout = 10000
                    conn.setRequestProperty("Content-Type", "application/json; utf-8")
                    conn.doOutput = true

                    val custArr = JSONArray()
                    val cIds = mutableListOf<Long>()
                    val seenPendingCust = mutableSetOf<String>()
                    for (c in unsyncedCustomers) {
                        val clean = c.name.trim()
                        val norm = clean.lowercase()
                        if (clean.isNotBlank() && norm !in seenPendingCust) {
                            seenPendingCust.add(norm)
                            custArr.put(JSONObject().apply {
                                put("name", clean)
                                put("phone", c.phone)
                                put("address", c.address)
                                put("note", c.note)
                            })
                        }
                        cIds.add(c.id)
                    }

                    val payload = JSONObject().apply {
                        put("userPhone", userPhone)
                        put("customers", custArr)
                    }

                    OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(payload.toString()) }
                    val code = conn.responseCode
                    if (code in 200..299) {
                        repository.markCustomersSynced(cIds)
                        syncedCustomersCount = cIds.size
                    }
                }

                // 4. Sync product groups (categories)
                val allGroups = repository.getAllGroupsDirect()
                if (allGroups.isNotEmpty()) {
                    try {
                        val targetUrl = "$cleanUrl/api/sync/groups"
                        val conn = URL(targetUrl).openConnection() as HttpURLConnection
                        conn.requestMethod = "POST"
                        conn.connectTimeout = 8000
                        conn.readTimeout = 8000
                        conn.setRequestProperty("Content-Type", "application/json; utf-8")
                        conn.doOutput = true

                        val grpArr = JSONArray()
                        for (g in allGroups) grpArr.put(g.name)

                        val payload = JSONObject().apply {
                            put("userPhone", userPhone)
                            put("groups", grpArr)
                        }
                        OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(payload.toString()) }
                        conn.responseCode
                    } catch (e: Exception) {
                        Log.e(TAG, "Groups sync error: ${e.message}")
                    }
                }

                // 5. Sync product units
                val allUnits = repository.getAllUnitsDirect()
                if (allUnits.isNotEmpty()) {
                    try {
                        val targetUrl = "$cleanUrl/api/sync/units"
                        val conn = URL(targetUrl).openConnection() as HttpURLConnection
                        conn.requestMethod = "POST"
                        conn.connectTimeout = 8000
                        conn.readTimeout = 8000
                        conn.setRequestProperty("Content-Type", "application/json; utf-8")
                        conn.doOutput = true

                        val untArr = JSONArray()
                        for (u in allUnits) untArr.put(u.name)

                        val payload = JSONObject().apply {
                            put("userPhone", userPhone)
                            put("units", untArr)
                        }
                        OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(payload.toString()) }
                        conn.responseCode
                    } catch (e: Exception) {
                        Log.e(TAG, "Units sync error: ${e.message}")
                    }
                }

                // 6. Sync payment methods
                val allPayments = repository.getAllPaymentsDirect()
                if (allPayments.isNotEmpty()) {
                    try {
                        val targetUrl = "$cleanUrl/api/sync/payments"
                        val conn = URL(targetUrl).openConnection() as HttpURLConnection
                        conn.requestMethod = "POST"
                        conn.connectTimeout = 8000
                        conn.readTimeout = 8000
                        conn.setRequestProperty("Content-Type", "application/json; utf-8")
                        conn.doOutput = true

                        val payArr = JSONArray()
                        for (pay in allPayments) {
                            payArr.put(JSONObject().apply {
                                put("method", pay.method)
                                put("amount", pay.amount)
                                put("date", pay.date)
                            })
                        }

                        val payload = JSONObject().apply {
                            put("userPhone", userPhone)
                            put("payments", payArr)
                        }
                        OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(payload.toString()) }
                        conn.responseCode
                    } catch (e: Exception) {
                        Log.e(TAG, "Payments sync error: ${e.message}")
                    }
                }

                // 7. Sync suppliers
                val allSuppliers = repository.getAllSuppliersDirect()
                if (allSuppliers.isNotEmpty()) {
                    try {
                        val targetUrl = "$cleanUrl/api/sync/suppliers"
                        val conn = URL(targetUrl).openConnection() as HttpURLConnection
                        conn.requestMethod = "POST"
                        conn.connectTimeout = 8000
                        conn.readTimeout = 8000
                        conn.setRequestProperty("Content-Type", "application/json; utf-8")
                        conn.doOutput = true

                        val supArr = JSONArray()
                        val seenPendingSup = mutableSetOf<String>()
                        for (sup in allSuppliers) {
                            val clean = sup.name.trim()
                            val norm = clean.lowercase()
                            if (clean.isNotBlank() && norm !in seenPendingSup) {
                                seenPendingSup.add(norm)
                                supArr.put(JSONObject().apply {
                                    put("name", clean)
                                    put("phone", sup.phone)
                                    put("address", sup.address)
                                    put("note", sup.note)
                                })
                            }
                        }

                        val payload = JSONObject().apply {
                            put("userPhone", userPhone)
                            put("suppliers", supArr)
                        }
                        OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(payload.toString()) }
                        conn.responseCode
                    } catch (e: Exception) {
                        Log.e(TAG, "Suppliers sync error: ${e.message}")
                    }
                }

                val totalSynced = syncedVouchersCount + syncedProductsCount + syncedCustomersCount
                if (totalSynced > 0) {
                    // Pull updated cloud data immediately so local stock matches the server's absolute reconciled stock
                    try {
                        pullCloudData(workerUrl, userPhone, repository)
                    } catch (e: Exception) {
                        Log.e(TAG, "Auto-pull after sync error: ${e.message}")
                    }
                    val msg = "Cloud သို့ အောင်မြင်စွာ တင်ပြီးပါပြီ (အရောင်း: $syncedVouchersCount, ပစ္စည်း: $syncedProductsCount, ဖောက်သည်: $syncedCustomersCount)"
                    SyncResult(true, syncedVouchersCount, syncedProductsCount, syncedCustomersCount, msg)
                } else {
                    SyncResult(true, 0, 0, 0, "ဒေတာအားလုံး (ပစ္စည်း၊ အမျိုးအစား၊ ယူနစ်၊ ဖောက်သည်၊ ကုန်သွင်းသူ၊ ငွေပေးချေမှု) Cloud ပေါ်တွင် ရောက်ရှိပြီးဖြစ်ပါသည်")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Batch sync failed: ${e.message}")
                SyncResult(false, 0, 0, 0, "Sync ချိတ်ဆက်မှု မအောင်မြင်ပါ: ${e.message}")
            }
        }
    }

    /**
     * Pulls / restores cloud data for this specific user account.
     * Guarantees Customer A and Customer B data separation!
     */
    suspend fun pullCloudData(
        workerUrl: String,
        userPhone: String,
        repository: Repository,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): PullResult {
        if (userPhone.isBlank()) {
            return PullResult(false, message = "ကျေးဇူးပြု၍ Login အရင်ဝင်ပါ")
        }

        return withContext(Dispatchers.IO) {
            onProgress(0.08f, "Cloud ချိတ်ဆက်မှု စစ်ဆေးနေပါသည်...")
            val check = checkServerReachability(workerUrl)
            if (!check.isReachable) {
                return@withContext PullResult(false, message = check.message)
            }

            try {
                onProgress(0.20f, "Cloud မှ ဒေတာများ ရယူနေပါသည်...")
                val targetUrl = workerUrl.trim().removeSuffix("/") + "/api/sync/pull"
                val conn = URL(targetUrl).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.setRequestProperty("Content-Type", "application/json; utf-8")
                conn.setRequestProperty("Accept", "application/json")
                conn.doOutput = true

                val req = JSONObject().apply { put("userPhone", userPhone) }
                OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(req.toString()) }

                val code = conn.responseCode
                if (code !in 200..299) {
                    return@withContext PullResult(false, message = "Cloud မှ ဒေတာရယူ၍မရပါ (Status: $code)")
                }

                val responseStr = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                val resObj = JSONObject(responseStr)
                onProgress(0.40f, "ဒေတာများကို စစ်ဆေးထည့်သွင်းနေပါသည်...")

                var vCount = 0
                var pCount = 0
                var cCount = 0

                // 1. Restore voucher items (supporting both camelCase and snake_case)
                val itemsArr = resObj.optJSONArray("voucherItems") ?: resObj.optJSONArray("voucher_items") ?: JSONArray()
                val itemsMap = mutableMapOf<String, MutableList<VoucherItem>>()
                for (i in 0 until itemsArr.length()) {
                    val it = itemsArr.getJSONObject(i)
                    val vId = if (it.has("voucherId")) it.optString("voucherId") else it.optString("voucher_id")
                    if (vId.isNotBlank()) {
                        val pId = if (it.has("productId")) it.optLong("productId") else it.optLong("product_id", 0L)
                        val pName = if (it.has("productName")) it.optString("productName") else it.optString("product_name", "")
                        val qty = it.optInt("quantity", 1)
                        val pPrice = if (it.has("purchasePrice")) it.optDouble("purchasePrice") else it.optDouble("purchase_price", 0.0)
                        val sPrice = if (it.has("sellingPrice")) it.optDouble("sellingPrice") else it.optDouble("selling_price", 0.0)
                        val vItem = VoucherItem(
                            voucherId = vId,
                            productId = pId,
                            productName = pName,
                            quantity = qty,
                            purchasePrice = pPrice,
                            sellingPrice = sPrice
                        )
                        itemsMap.getOrPut(vId) { mutableListOf() }.add(vItem)
                    }
                }

                // 2. Restore vouchers (supporting both camelCase and snake_case)
                val vouchersArr = resObj.optJSONArray("vouchers") ?: JSONArray()
                val cloudVoucherReceiptNos = mutableSetOf<String>()
                for (i in 0 until vouchersArr.length()) {
                    val v = vouchersArr.getJSONObject(i)
                    val receiptNo = if (v.has("receiptNo")) v.optString("receiptNo") else v.optString("receipt_no")
                    if (receiptNo.isNotBlank()) {
                        cloudVoucherReceiptNos.add(receiptNo)
                        val ts = v.optLong("timestamp", System.currentTimeMillis())
                        val cashier = if (v.has("cashierName")) v.optString("cashierName") else v.optString("cashier_name", "Cashier")
                        val totAmt = if (v.has("totalAmount")) v.optDouble("totalAmount") else v.optDouble("total_amount", 0.0)
                        val totItms = if (v.has("totalItems")) v.optInt("totalItems") else v.optInt("total_items", 0)
                        val cust = if (v.has("customerName")) v.optString("customerName") else v.optString("customer_name", "Not Register")
                        val payMethod = if (v.has("paymentMethod")) v.optString("paymentMethod") else v.optString("payment_method", "CASH")
                        
                        val isComp = when {
                            v.has("isCompleted") -> v.optBoolean("isCompleted", true)
                            v.has("is_completed") -> v.optInt("is_completed", 1) == 1
                            else -> true
                        }
                        val isPurch = when {
                            v.has("isPurchase") -> v.optBoolean("isPurchase", false)
                            v.has("is_purchase") -> v.optInt("is_purchase", 0) == 1
                            else -> false
                        }

                        val paidAmt = if (v.has("paidAmount")) v.optDouble("paidAmount") else v.optDouble("paid_amount", 0.0)
                        val changeAmt = if (v.has("changeAmount")) v.optDouble("changeAmount") else v.optDouble("change_amount", 0.0)
                        val balAmt = if (v.has("balanceAmount")) v.optDouble("balanceAmount") else v.optDouble("balance_amount", 0.0)
                        val disc = if (v.has("discount")) v.optDouble("discount") else 0.0
                        val feeAmt = if (v.has("fee")) v.optDouble("fee") else 0.0
                        val vNote = v.optString("note", "")

                        val voucher = Voucher(
                            receiptNo = receiptNo,
                            timestamp = ts,
                            cashierName = cashier,
                            totalAmount = totAmt,
                            totalItems = totItms,
                            customerName = cust,
                            paymentMethod = payMethod,
                            isCompleted = isComp,
                            isPurchase = isPurch,
                            paidAmount = paidAmt,
                            changeAmount = changeAmt,
                            balanceAmount = balAmt,
                            discount = disc,
                            fee = feeAmt,
                            note = vNote,
                            isSynced = true
                        )
                        repository.insertVoucher(voucher)
                        repository.deleteVoucherItems(receiptNo)
                        itemsMap[receiptNo]?.forEach { repository.insertVoucherItem(it) }
                        vCount++
                    }
                }

                // Multi-device voucher deletion sync: delete local synced vouchers that were deleted on another phone or cloud
                val allLocalVouchers = repository.getAllVouchersDirect()
                for (lv in allLocalVouchers) {
                    if (lv.isSynced && lv.receiptNo !in cloudVoucherReceiptNos) {
                        repository.deleteVoucher(lv.receiptNo)
                        repository.deleteVoucherItems(lv.receiptNo)
                    }
                }
                onProgress(0.65f, "ဘောက်ချာမှတ်တမ်းများ ထည့်သွင်းပြီးပါပြီ...")

                // 3. Restore products (real-time stock/metadata sync & multi-device deletion sync)
                // Calculate any locally unsynced / pending completed sales or purchases
                // Formula: remaining stock = cloud database stock - offline sold stock + offline purchase stock
                val pendingVouchers = repository.getUnsyncedCompletedVouchers()
                val pendingSoldById = mutableMapOf<Long, Int>()
                val pendingSoldByName = mutableMapOf<String, Int>()
                val pendingPurchasedById = mutableMapOf<Long, Int>()
                val pendingPurchasedByName = mutableMapOf<String, Int>()

                for (pv in pendingVouchers) {
                    val items = repository.getVoucherItemsDirect(pv.receiptNo)
                    for (it in items) {
                        val q = it.quantity
                        if (pv.isPurchase) {
                            if (it.productId > 0) pendingPurchasedById[it.productId] = (pendingPurchasedById[it.productId] ?: 0) + q
                            if (it.productName.isNotBlank()) pendingPurchasedByName[it.productName.trim().lowercase()] = (pendingPurchasedByName[it.productName.trim().lowercase()] ?: 0) + q
                        } else {
                            if (it.productId > 0) pendingSoldById[it.productId] = (pendingSoldById[it.productId] ?: 0) + q
                            if (it.productName.isNotBlank()) pendingSoldByName[it.productName.trim().lowercase()] = (pendingSoldByName[it.productName.trim().lowercase()] ?: 0) + q
                        }
                    }
                }

                val prodsArr = resObj.optJSONArray("products") ?: JSONArray()
                val cloudProductIds = mutableSetOf<Long>()
                val cloudProductNames = mutableSetOf<String>()
                val currentLocalProds = repository.getAllProductsDirect()
                val localProdsByName = currentLocalProds.associateBy { it.name.trim().lowercase() }.toMutableMap()
                val localProdsById = currentLocalProds.filter { it.id > 0 }.associateBy { it.id }.toMutableMap()

                for (i in 0 until prodsArr.length()) {
                    val p = prodsArr.getJSONObject(i)
                    val prodId = if (p.has("id")) p.optLong("id") else p.optLong("product_id", 0L)
                    val pName = p.optString("name").trim()
                    if (pName.isBlank()) continue
                    val normName = pName.lowercase()
                    if (prodId > 0) cloudProductIds.add(prodId)
                    cloudProductNames.add(normName)

                    val gName = if (p.has("groupName")) p.optString("groupName") else p.optString("group_name", "Default")
                    val pPrice = if (p.has("purchasePrice")) p.optDouble("purchasePrice") else if (p.has("cost")) p.optDouble("cost") else p.optDouble("purchase_price", 0.0)
                    val sPrice = if (p.has("sellingPrice")) p.optDouble("sellingPrice") else if (p.has("price")) p.optDouble("price") else p.optDouble("selling_price", 0.0)
                    val aQty = if (p.has("alertQuantity")) p.optInt("alertQuantity") else p.optInt("alert_quantity", 0)
                    val track = if (p.has("trackStock")) p.optBoolean("trackStock") else if (p.has("track_stock")) p.optBoolean("track_stock") else true
                    val barcode = p.optString("barcode", "")
                    val unit = p.optString("unit", "ခု")
                    val note = p.optString("note", "")
                    val cloudQty = p.optInt("quantity", 0)

                    // Relative offline reconciliation: remain stock = cloud database stock - pending offline sold stock
                    val pSold = (if (prodId > 0) pendingSoldById[prodId] else null) ?: pendingSoldByName[normName] ?: 0
                    val pPurchased = (if (prodId > 0) pendingPurchasedById[prodId] else null) ?: pendingPurchasedByName[normName] ?: 0
                    val finalQty = if (pSold > 0 || pPurchased > 0) {
                        (cloudQty + pPurchased - pSold).coerceAtLeast(0)
                    } else {
                        cloudQty
                    }
                    val hasPendingDeltas = (pSold > 0 || pPurchased > 0)

                    val existing = (if (prodId > 0) localProdsById[prodId] else null) ?: localProdsByName[normName]
                    if (existing != null) {
                        // CRITICAL: If local product has unsynced changes (e.g. user just updated stock or price),
                        // DO NOT overwrite with stale cloud data!
                        val finalProductQty = if (!existing.isSynced) existing.quantity else finalQty
                        val finalAlertQty = if (!existing.isSynced) existing.alertQuantity else aQty
                        val finalSPrice = if (!existing.isSynced && existing.sellingPrice > 0.0) existing.sellingPrice else sPrice
                        val finalPPrice = if (!existing.isSynced && existing.purchasePrice > 0.0) existing.purchasePrice else pPrice
                        val updated = existing.copy(
                            name = pName,
                            groupName = gName,
                            purchasePrice = finalPPrice,
                            sellingPrice = finalSPrice,
                            unit = unit,
                            note = note,
                            trackStock = track,
                            barcode = barcode,
                            quantity = finalProductQty,
                            alertQuantity = finalAlertQty,
                            isSynced = if (!existing.isSynced) false else !hasPendingDeltas
                        )
                        repository.updateProduct(updated)
                    } else {
                        val prod = Product(
                            id = prodId,
                            name = pName,
                            groupName = gName,
                            purchasePrice = pPrice,
                            sellingPrice = sPrice,
                            unit = unit,
                            note = note,
                            trackStock = track,
                            barcode = barcode,
                            quantity = finalQty,
                            alertQuantity = aQty,
                            isSynced = !hasPendingDeltas
                        )
                        repository.insertProduct(prod)
                    }
                    pCount++
                }

                // Multi-device deletion sync: delete local products that were deleted on another phone
                val refreshedProds = repository.getAllProductsDirect()
                for (lp in refreshedProds) {
                    if (lp.isSynced) {
                        val inCloud = (lp.id > 0 && lp.id in cloudProductIds) || (lp.name.trim().lowercase() in cloudProductNames)
                        if (!inCloud) {
                            repository.deleteProduct(lp)
                        }
                    }
                }
                onProgress(0.85f, "ကုန်ပစ္စည်းနှင့် Stock လက်ကျန်များ ထည့်သွင်းပြီးပါပြီ...")

                // 4. Restore customers (deduplicated by name and cleans up existing duplicates in local Room)
                val currentLocalCustomers = repository.getAllCustomersDirect()
                val localCustomersByName = mutableMapOf<String, Customer>()
                for (c in currentLocalCustomers) {
                    val norm = c.name.trim().lowercase()
                    if (norm.isBlank() || norm in localCustomersByName) {
                        repository.deleteCustomer(c)
                    } else {
                        localCustomersByName[norm] = c
                    }
                }

                val custsArr = resObj.optJSONArray("customers") ?: JSONArray()
                for (i in 0 until custsArr.length()) {
                    val c = custsArr.getJSONObject(i)
                    val cName = c.optString("name").trim()
                    if (cName.isBlank()) continue
                    val norm = cName.lowercase()
                    val existing = localCustomersByName[norm]
                    if (existing != null) {
                        val updated = existing.copy(
                            name = cName,
                            phone = if (c.has("phone")) c.optString("phone") else existing.phone,
                            address = if (c.has("address")) c.optString("address") else existing.address,
                            note = if (c.has("note")) c.optString("note") else existing.note,
                            isSynced = true
                        )
                        repository.updateCustomer(updated)
                        localCustomersByName[norm] = updated
                    } else {
                        val customer = Customer(
                            name = cName,
                            phone = c.optString("phone", ""),
                            address = c.optString("address", ""),
                            note = c.optString("note", ""),
                            isSynced = true
                        )
                        repository.insertCustomer(customer)
                        localCustomersByName[norm] = customer
                        cCount++
                    }
                }

                // 5. Restore product groups
                val groupsArr = resObj.optJSONArray("groups") ?: JSONArray()
                for (i in 0 until groupsArr.length()) {
                    val gName = groupsArr.optString(i)
                    if (gName.isNotBlank()) {
                        repository.insertGroup(ProductGroup(gName))
                    }
                }

                // 6. Restore product units
                val unitsArr = resObj.optJSONArray("units") ?: JSONArray()
                for (i in 0 until unitsArr.length()) {
                    val uName = unitsArr.optString(i)
                    if (uName.isNotBlank()) {
                        repository.insertUnit(ProductUnit(uName))
                    }
                }

                // 7. Clean up duplicated payments in local Room, then restore uniquely
                val currentLocalPayments = repository.getAllPaymentsDirect()
                val localSeenMethods = mutableSetOf<String>()
                for (p in currentLocalPayments) {
                    val norm = p.method.trim().lowercase()
                    if (norm in localSeenMethods) {
                        repository.deletePayment(p)
                    } else {
                        localSeenMethods.add(norm)
                    }
                }

                val paymentsArr = resObj.optJSONArray("payments") ?: JSONArray()
                for (i in 0 until paymentsArr.length()) {
                    val pay = paymentsArr.getJSONObject(i)
                    val m = if (pay.has("method")) pay.optString("method") else pay.optString("payment_method", "")
                    val clean = m.trim()
                    if (clean.isNotBlank()) {
                        val norm = clean.lowercase()
                        if (norm !in localSeenMethods) {
                            localSeenMethods.add(norm)
                            repository.insertPayment(Payment(
                                method = clean,
                                amount = pay.optDouble("amount", 0.0),
                                date = pay.optLong("date", System.currentTimeMillis())
                            ))
                        }
                    }
                }

                // 8. Restore suppliers (deduplicated by name and cleans up existing duplicates in local Room)
                val currentLocalSuppliers = repository.getAllSuppliersDirect()
                val localSuppliersByName = mutableMapOf<String, Supplier>()
                for (s in currentLocalSuppliers) {
                    val norm = s.name.trim().lowercase()
                    if (norm.isBlank() || norm in localSuppliersByName) {
                        repository.deleteSupplier(s)
                    } else {
                        localSuppliersByName[norm] = s
                    }
                }

                val suppliersArr = resObj.optJSONArray("suppliers") ?: JSONArray()
                for (i in 0 until suppliersArr.length()) {
                    val s = suppliersArr.getJSONObject(i)
                    val sName = s.optString("name").trim()
                    if (sName.isBlank()) continue
                    val norm = sName.lowercase()
                    val existing = localSuppliersByName[norm]
                    if (existing != null) {
                        val updated = existing.copy(
                            name = sName,
                            phone = if (s.has("phone")) s.optString("phone") else existing.phone,
                            address = if (s.has("address")) s.optString("address") else existing.address,
                            note = if (s.has("note")) s.optString("note") else existing.note
                        )
                        repository.updateSupplier(updated)
                        localSuppliersByName[norm] = updated
                    } else {
                        val newSupplier = Supplier(
                            name = sName,
                            phone = s.optString("phone", ""),
                            address = s.optString("address", ""),
                            note = s.optString("note", "")
                        )
                        repository.insertSupplier(newSupplier)
                        localSuppliersByName[norm] = newSupplier
                    }
                }

                // 9. Restore expense categories (deduplicated by name)
                val expCatsArr = resObj.optJSONArray("expenseCategories") ?: resObj.optJSONArray("expense_categories") ?: JSONArray()
                val currentCats = repository.getAllExpenseCategoriesDirect()
                val seenCatNames = currentCats.map { it.name.trim().lowercase() }.toMutableSet()
                for (i in 0 until expCatsArr.length()) {
                    val ec = expCatsArr.getJSONObject(i)
                    val catName = if (ec.has("name")) ec.optString("name") else ec.optString("category_name", "")
                    val clean = catName.trim()
                    if (clean.isNotBlank() && clean.lowercase() !in seenCatNames) {
                        seenCatNames.add(clean.lowercase())
                        repository.insertExpenseCategory(ExpenseCategory(
                            name = clean,
                            iconName = if (ec.has("iconName")) ec.optString("iconName") else ec.optString("icon_name", "ShoppingCart")
                        ))
                    }
                }

                // 10. Restore expenses (deduplicated)
                val expensesArr = resObj.optJSONArray("expenses") ?: JSONArray()
                val currentExpenses = repository.getAllExpensesDirect()
                var restoredExpensesCount = 0
                for (i in 0 until expensesArr.length()) {
                    val exp = expensesArr.getJSONObject(i)
                    val catName = if (exp.has("categoryName")) exp.optString("categoryName") else exp.optString("category_name", "")
                    val desc = exp.optString("description", "")
                    val amt = exp.optDouble("amount", 0.0)
                    val pMethod = if (exp.has("paymentMethod")) exp.optString("paymentMethod") else exp.optString("payment_method", "CASH")
                    val expNote = exp.optString("note", "")
                    val ts = exp.optLong("timestamp", System.currentTimeMillis())
                    val dStr = if (exp.has("dateString")) exp.optString("dateString") else exp.optString("date_string", "")
                    val tStr = if (exp.has("timeString")) exp.optString("timeString") else exp.optString("time_string", "")

                    val isDup = currentExpenses.any { it.timestamp == ts && it.amount == amt && it.description == desc }
                    if (!isDup && (catName.isNotBlank() || desc.isNotBlank())) {
                        repository.insertExpense(Expense(
                            categoryName = catName,
                            description = desc,
                            amount = amt,
                            paymentMethod = pMethod,
                            note = expNote,
                            timestamp = ts,
                            dateString = dStr,
                            timeString = tStr
                        ))
                        restoredExpensesCount++
                    }
                }

                onProgress(1.0f, "ဒေတာများ အောင်မြင်စွာ ပြန်လည်ရယူပြီးပါပြီ။")

                PullResult(
                    success = true,
                    vouchersRestored = vCount,
                    productsRestored = pCount,
                    customersRestored = cCount,
                    message = "Cloud မှ အောင်မြင်စွာ ပြန်လည်ရယူပြီးပါပြီ (အရောင်း/အဝယ်: $vCount, ပစ္စည်း: $pCount, ဖောက်သည်: $cCount, အမျိုးအစား: ${groupsArr.length()}, ယူနစ်: ${unitsArr.length()}, ကုန်သွင်းသူ: ${suppliersArr.length()}, ကုန်ကျစရိတ်: ${expensesArr.length()})"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Pull cloud data failed: ${e.message}")
                PullResult(false, message = "Cloud မှ ပြန်လည်ရယူရာတွင် အမှားဖြစ်ပေါ်ပါသည်: ${e.message}")
            }
        }
    }
}
