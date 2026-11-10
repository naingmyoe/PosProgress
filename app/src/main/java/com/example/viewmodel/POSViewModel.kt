package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import java.text.SimpleDateFormat
import java.util.*

data class CartItem(
    val product: Product,
    val quantity: Int
)

class POSViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = Repository(database)
    private val sharedPrefs = application.getSharedPreferences("pos_prefs", android.content.Context.MODE_PRIVATE)
    private var isSavingCompletion = false

    // Flow states
    val allAccounts = repository.allAccounts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val allProducts = repository.allProducts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val allGroups = repository.allGroups.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val allUnits = repository.allUnits.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val allVouchers = repository.allVouchers.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val allVoucherItems = repository.allVoucherItems.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val allCustomers = repository.allCustomers.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val allSuppliers = repository.allSuppliers.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val allPayments = repository.allPayments.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val allExpenseCategories = repository.allExpenseCategories.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val allExpenses = repository.allExpenses.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Selected expense category state for AddExpenseScreen
    val selectedExpenseCategoryName = MutableStateFlow("No Category")
    val selectedExpenseCategoryIcon = MutableStateFlow("ShoppingCart")

    // Active logged in user
    private val _currentUser = MutableStateFlow<UserAccount?>(null)
    val currentUser: StateFlow<UserAccount?> = _currentUser.asStateFlow()

    suspend fun getLocalAccountByPhone(phone: String): UserAccount? {
        return repository.getAccountByPhone(phone)
    }

    // Registration UI Temp States
    val selectedRole = MutableStateFlow("ADMIN")
    val registerUsername = MutableStateFlow("")
    val registerBusinessName = MutableStateFlow("")
    val registerBusinessType = MutableStateFlow("")
    val registerAddress = MutableStateFlow("")
    val registerPhone = MutableStateFlow("")
    val registerPassword = MutableStateFlow("")
    val registerRetypePassword = MutableStateFlow("")

    // Circular loading states during registration
    private val _registrationProgress = MutableStateFlow(0f)
    val registrationProgress: StateFlow<Float> = _registrationProgress.asStateFlow()

    private val _registrationStatus = MutableStateFlow("")
    val registrationStatus: StateFlow<String> = _registrationStatus.asStateFlow()

    // Product registration inputs
    val prodName = MutableStateFlow("")
    val prodGroup = MutableStateFlow("")
    val prodPurchasePrice = MutableStateFlow("")
    val prodSellingPrice = MutableStateFlow("")
    val prodUnit = MutableStateFlow("")
    val prodNote = MutableStateFlow("")
    val prodTrackStock = MutableStateFlow(true)
    val prodBarcode = MutableStateFlow("")
    val prodQty = MutableStateFlow("")
    val prodAlertQty = MutableStateFlow("")
    val prodImageUri = MutableStateFlow("")

    // Editing States for Customers & Suppliers
    val editingCustomer = MutableStateFlow<Customer?>(null)
    val editingSupplier = MutableStateFlow<Supplier?>(null)
    val editingProduct = MutableStateFlow<Product?>(null)
    val isProductGridView = MutableStateFlow(sharedPrefs.getBoolean("is_product_grid_view", false))
    val isDarkTheme = MutableStateFlow(sharedPrefs.getBoolean("is_dark_theme", false))
    val selectedPrinterName = MutableStateFlow(sharedPrefs.getString("selected_printer_name", "") ?: "")
    val selectedPrinterMac = MutableStateFlow(sharedPrefs.getString("selected_printer_mac", "") ?: "")
    val selectedPaperWidth = MutableStateFlow(sharedPrefs.getString("selected_paper_width", "80mm") ?: "80mm")
    val selectedPrintMode = MutableStateFlow(sharedPrefs.getString("selected_print_mode", "mode2") ?: "mode2")
    val selectedTemplate = MutableStateFlow(sharedPrefs.getString("selected_template", "template2") ?: "template2")
    val selectedCurrency = MutableStateFlow(sharedPrefs.getString("selected_currency", "Ks") ?: "Ks")
    val appLanguage = MutableStateFlow(sharedPrefs.getString("app_language", "my") ?: "my")

    // Dynamic Header & Footer states
    val headerLines = MutableStateFlow(
        run {
            val raw = sharedPrefs.getString("header_lines", "") ?: ""
            if (raw.contains("|||")) raw.split("|||").filter { it.isNotEmpty() }
            else raw.split(",").filter { it.isNotEmpty() }
        }
    )
    val footerLines = MutableStateFlow(
        run {
            val raw = sharedPrefs.getString("footer_lines", "") ?: ""
            if (raw.contains("|||")) raw.split("|||").filter { it.isNotEmpty() }
            else raw.split(",").filter { it.isNotEmpty() }
        }
    )
    val headerFontSize = MutableStateFlow(sharedPrefs.getFloat("header_font_size", 40f))
    val headerFontFamily = MutableStateFlow(sharedPrefs.getString("header_font_family", "Default") ?: "Default")
    val headerIsBold = MutableStateFlow(sharedPrefs.getBoolean("header_is_bold", true))
    val headerAlignment = MutableStateFlow(sharedPrefs.getString("header_alignment", "Center") ?: "Center")
    val headerLogoUri = MutableStateFlow(sharedPrefs.getString("header_logo_uri", "") ?: "")
    val paperSpacing = MutableStateFlow(sharedPrefs.getFloat("paper_spacing", 10f))
    val bottomFeed = MutableStateFlow(sharedPrefs.getFloat("bottom_feed", 15f))
    val printBrightness = MutableStateFlow(sharedPrefs.getFloat("print_brightness", 100f))
    val receiptBodyFontSize = MutableStateFlow(sharedPrefs.getFloat("receipt_body_font_size", 16f))
    val printReceiptDiscount = MutableStateFlow(sharedPrefs.getBoolean("print_receipt_discount", true))
    val printReceiptFee = MutableStateFlow(sharedPrefs.getBoolean("print_receipt_fee", true))
    val lineSpacing = MutableStateFlow(sharedPrefs.getFloat("line_spacing", 12f))

    // Customizable Receipt/Voucher Labels
    val labelDate = MutableStateFlow(sharedPrefs.getString("label_date", "ရက်စွဲ") ?: "ရက်စွဲ")
    val labelCustomerName = MutableStateFlow(sharedPrefs.getString("label_customer_name", "ဈေးဝယ်သူ") ?: "ဈေးဝယ်သူ")
    val labelNo = MutableStateFlow(sharedPrefs.getString("label_no", "စဉ်") ?: "စဉ်")
    val labelItemName = MutableStateFlow(sharedPrefs.getString("label_item_name", "အမည်") ?: "အမည်")
    val labelQuantity = MutableStateFlow(sharedPrefs.getString("label_quantity", "ဦးရေ") ?: "ဦးရေ")
    val labelUnitPrice = MutableStateFlow(sharedPrefs.getString("label_unit_price", "ဈေးနှုန်း") ?: "ဈေးနှုန်း")
    val labelLineTotal = MutableStateFlow(sharedPrefs.getString("label_line_total", "သင့်ငွေ") ?: "သင့်ငွေ")
    val labelSubTotal = MutableStateFlow(sharedPrefs.getString("label_sub_total", "ကျသင့်ငွေ") ?: "ကျသင့်ငွေ")
    val labelTotal = MutableStateFlow(sharedPrefs.getString("label_total", "စုစုပေါင်း") ?: "စုစုပေါင်း")
    val labelPaid = MutableStateFlow(sharedPrefs.getString("label_paid", "ပေးငွေ") ?: "ပေးငွေ")
    val labelDue = MutableStateFlow(sharedPrefs.getString("label_due", "အမ်းငွေ") ?: "အမ်းငွေ")
    val labelVoucherNo = MutableStateFlow(sharedPrefs.getString("label_voucher_no", "ပြေစာအမှတ်") ?: "ပြေစာအမှတ်")


    // Active Voucher/Draft States
    val activeVoucherId = MutableStateFlow<String?>(null)
    val activeCustomerName = MutableStateFlow("Not Register")
    val activePaymentMethod = MutableStateFlow("CASH")
    val activeDiscount = MutableStateFlow(0.0)
    val activeFee = MutableStateFlow(0.0)
    val activeNote = MutableStateFlow("")

    val editingVoucherInitialItems = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val editingVoucher = MutableStateFlow<Voucher?>(null)

    fun getAvailableStock(product: Product): Int {
        if (!product.trackStock || isPurchaseMode.value) return Int.MAX_VALUE
        val originalQty = editingVoucherInitialItems.value[product.id] ?: 0
        val currentDbProduct = allProducts.value.find { it.id == product.id }
        val currentStock = currentDbProduct?.quantity ?: product.quantity
        return (currentStock + originalQty).coerceAtLeast(0)
    }

    val isPurchaseMode = MutableStateFlow(false)
    val isPurchaseHistoryMode = MutableStateFlow(false)
    
    // Stock report mode: "DEFAULT", "PURCHASE_VALUE", "SELLING_VALUE"
    val stockReportMode = MutableStateFlow("DEFAULT")

    val calendarYear = MutableStateFlow(java.util.Calendar.getInstance().get(java.util.Calendar.YEAR))
    val calendarMonth = MutableStateFlow(java.util.Calendar.getInstance().get(java.util.Calendar.MONTH))

    // Persisted Filter states for Sales History (ReceiptsScreen)
    val salesHistoryFilterMode = MutableStateFlow("month")
    val salesHistorySelectedCustomer = MutableStateFlow<Customer?>(null)
    val salesHistorySelectedPaymentMethod = MutableStateFlow<String?>(null)
    val salesHistoryCalendarTime = MutableStateFlow<Long>(java.util.Calendar.getInstance().timeInMillis)

    fun resetSalesHistoryFilters() {
        salesHistoryFilterMode.value = "month"
        salesHistorySelectedCustomer.value = null
        salesHistorySelectedPaymentMethod.value = null
        val now = java.util.Calendar.getInstance()
        calendarYear.value = now.get(java.util.Calendar.YEAR)
        calendarMonth.value = now.get(java.util.Calendar.MONTH)
        salesHistoryCalendarTime.value = now.timeInMillis
    }

    // Shopping Cart State
    private val _cart = MutableStateFlow<List<CartItem>>(emptyList())
    val cart: StateFlow<List<CartItem>> = _cart.asStateFlow()

    // Offline Sync State
    private val _isOfflineSaved = MutableStateFlow(true)
    val isOfflineSaved: StateFlow<Boolean> = _isOfflineSaved.asStateFlow()

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    val cloudDownloadProgress = MutableStateFlow(0f)
    val cloudDownloadProgressMsg = MutableStateFlow("Cloud မှ အကောင့်ဒေတာများ ရယူနေပါသည်...")

    val unsyncedVouchersCount: StateFlow<Int> = repository.unsyncedVoucherCount.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        0
    )
    val unsyncedProductsCount: StateFlow<Int> = repository.unsyncedProductCount.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        0
    )
    val unsyncedCustomersCount: StateFlow<Int> = repository.unsyncedCustomerCount.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        0
    )
    val totalUnsyncedCount: StateFlow<Int> = repository.totalUnsyncedCount.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        0
    )
    val cloudflareWorkerUrl = MutableStateFlow(
        run {
            val saved = sharedPrefs.getString("cf_worker_url", null)
            if (saved.isNullOrBlank() || saved.contains("74.81.63.87")) {
                sharedPrefs.edit().putString("cf_worker_url", "http://171.22.172.107:8081").apply()
                "http://171.22.172.107:8081"
            } else {
                saved
            }
        }
    )
    val isOnlineState = MutableStateFlow(com.example.util.SyncManager.isNetworkAvailable(application))
    val isServerReachable = MutableStateFlow(false)
    val syncStatusMessage = MutableStateFlow("")
    val lastSyncTime = MutableStateFlow(sharedPrefs.getLong("last_sync_time", 0L))

    var isFromSalesFlow = false

    init {
        viewModelScope.launch(Dispatchers.IO) {
            com.example.util.BackupAndExcelUtils.performAutoDailyBackup(application)
        }

        viewModelScope.launch {
            cloudflareWorkerUrl.collect { value ->
                sharedPrefs.edit().putString("cf_worker_url", value).apply()
            }
        }

        // Instant Deduplication: Clean up any duplicate customers & suppliers created by previous downloads
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentCustomers = repository.getAllCustomersDirect()
                val seenCust = mutableSetOf<String>()
                for (c in currentCustomers) {
                    val norm = c.name.trim().lowercase()
                    if (norm.isBlank() || norm in seenCust) {
                        repository.deleteCustomer(c)
                    } else {
                        seenCust.add(norm)
                    }
                }

                val currentSuppliers = repository.getAllSuppliersDirect()
                val seenSup = mutableSetOf<String>()
                for (s in currentSuppliers) {
                    val norm = s.name.trim().lowercase()
                    if (norm.isBlank() || norm in seenSup) {
                        repository.deleteSupplier(s)
                    } else {
                        seenSup.add(norm)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Instant Network Callback: as soon as internet connects, immediately trigger 100% cloud mirror sync!
        try {
            val cm = application.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && cm != null) {
                cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        viewModelScope.launch {
                            isOnlineState.value = true
                            delay(1000) // Brief pause for connection to settle
                            val currentUrl = cloudflareWorkerUrl.value
                            val serverCheck = com.example.util.SyncManager.checkServerReachability(currentUrl)
                            isServerReachable.value = serverCheck.isReachable
                            val phone = _currentUser.value?.phoneNo ?: sharedPrefs.getString("logged_in_phone", null)
                            if (!phone.isNullOrBlank() && serverCheck.isReachable && !_isUploading.value) {
                                triggerRealSync()
                            }
                        }
                    }

                    override fun onLost(network: Network) {
                        isOnlineState.value = false
                        isServerReachable.value = false
                    }
                })
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Periodic network and server reachability check, auto-sync and 5-device real-time sync loop
        viewModelScope.launch {
            var cycleCount = 0
            while (true) {
                try {
                    val available = com.example.util.SyncManager.isNetworkAvailable(application)
                    isOnlineState.value = available
                    if (available) {
                        val currentUrl = cloudflareWorkerUrl.value
                        val serverCheck = com.example.util.SyncManager.checkServerReachability(currentUrl)
                        isServerReachable.value = serverCheck.isReachable
                        val phone = _currentUser.value?.phoneNo ?: sharedPrefs.getString("logged_in_phone", null)
                        if (!phone.isNullOrBlank() && !_isUploading.value && serverCheck.isReachable) {
                            // 1. Upload any local offline backlog
                            val unsyncedV = repository.getUnsyncedCompletedVouchers()
                            val unsyncedP = repository.getUnsyncedProducts()
                            val unsyncedC = repository.getUnsyncedCustomers()
                            if (unsyncedV.isNotEmpty() || unsyncedP.isNotEmpty() || unsyncedC.isNotEmpty()) {
                                triggerRealSync()
                            }

                            // 2. Multi-device real-time sync: silently pull latest sales, stock & product edits from other phones every 6s
                            cycleCount++
                            if (cycleCount >= 1 && !_isUploading.value) {
                                cycleCount = 0
                                com.example.util.SyncManager.pullCloudData(
                                    workerUrl = currentUrl,
                                    userPhone = phone,
                                    repository = repository
                                )
                            }
                        }
                    } else {
                        isServerReachable.value = false
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    isServerReachable.value = false
                }
                delay(6000)
            }
        }

        viewModelScope.launch {
            isProductGridView.collect { value ->
                sharedPrefs.edit().putBoolean("is_product_grid_view", value).apply()
            }
        }

        viewModelScope.launch {
            isDarkTheme.collect { value ->
                sharedPrefs.edit().putBoolean("is_dark_theme", value).apply()
            }
        }

        viewModelScope.launch {
            selectedPrinterName.collect { value ->
                sharedPrefs.edit().putString("selected_printer_name", value).apply()
            }
        }

        viewModelScope.launch {
            selectedPrinterMac.collect { value ->
                sharedPrefs.edit().putString("selected_printer_mac", value).apply()
            }
        }

        viewModelScope.launch {
            selectedPaperWidth.collect { value ->
                sharedPrefs.edit().putString("selected_paper_width", value).apply()
            }
        }

        viewModelScope.launch {
            selectedPrintMode.collect { value ->
                sharedPrefs.edit().putString("selected_print_mode", value).apply()
            }
        }

        viewModelScope.launch {
            selectedTemplate.collect { value ->
                sharedPrefs.edit().putString("selected_template", value).apply()
            }
        }

        viewModelScope.launch {
            selectedCurrency.collect { value ->
                sharedPrefs.edit().putString("selected_currency", value).apply()
            }
        }

        com.example.util.AppLocalization.currentLang = appLanguage.value
        viewModelScope.launch {
            appLanguage.collect { value ->
                sharedPrefs.edit().putString("app_language", value).apply()
                com.example.util.AppLocalization.currentLang = value
                labelPaid.value = com.example.util.AppLocalization.translate("ပေးငွေ", value)
                labelDue.value = com.example.util.AppLocalization.translate("အမ်းငွေ", value)
            }
        }

        viewModelScope.launch {
            headerLines.collect { value ->
                sharedPrefs.edit().putString("header_lines", value.joinToString("|||")).apply()
            }
        }

        viewModelScope.launch {
            footerLines.collect { value ->
                sharedPrefs.edit().putString("footer_lines", value.joinToString("|||")).apply()
            }
        }

        viewModelScope.launch {
            headerFontSize.collect { value ->
                sharedPrefs.edit().putFloat("header_font_size", value).apply()
            }
        }

        viewModelScope.launch {
            headerFontFamily.collect { value ->
                sharedPrefs.edit().putString("header_font_family", value).apply()
            }
        }

        viewModelScope.launch {
            headerIsBold.collect { value ->
                sharedPrefs.edit().putBoolean("header_is_bold", value).apply()
            }
        }

        viewModelScope.launch {
            headerAlignment.collect { value ->
                sharedPrefs.edit().putString("header_alignment", value).apply()
            }
        }

        viewModelScope.launch {
            headerLogoUri.collect { value ->
                sharedPrefs.edit().putString("header_logo_uri", value).apply()
            }
        }

        viewModelScope.launch {
            paperSpacing.collect { value ->
                sharedPrefs.edit().putFloat("paper_spacing", value).apply()
            }
        }

        viewModelScope.launch {
            bottomFeed.collect { value ->
                sharedPrefs.edit().putFloat("bottom_feed", value).apply()
            }
        }

        viewModelScope.launch {
            printBrightness.collect { value ->
                sharedPrefs.edit().putFloat("print_brightness", value).apply()
            }
        }

        viewModelScope.launch {
            receiptBodyFontSize.collect { value ->
                sharedPrefs.edit().putFloat("receipt_body_font_size", value).apply()
            }
        }

        viewModelScope.launch {
            printReceiptDiscount.collect { value ->
                sharedPrefs.edit().putBoolean("print_receipt_discount", value).apply()
            }
        }

        viewModelScope.launch {
            printReceiptFee.collect { value ->
                sharedPrefs.edit().putBoolean("print_receipt_fee", value).apply()
            }
        }

        viewModelScope.launch {
            lineSpacing.collect { value ->
                sharedPrefs.edit().putFloat("line_spacing", value).apply()
            }
        }

        viewModelScope.launch {
            labelDate.collect { value -> sharedPrefs.edit().putString("label_date", value).apply() }
        }
        viewModelScope.launch {
            labelCustomerName.collect { value -> sharedPrefs.edit().putString("label_customer_name", value).apply() }
        }
        viewModelScope.launch {
            labelNo.collect { value -> sharedPrefs.edit().putString("label_no", value).apply() }
        }
        viewModelScope.launch {
            labelItemName.collect { value -> sharedPrefs.edit().putString("label_item_name", value).apply() }
        }
        viewModelScope.launch {
            labelQuantity.collect { value -> sharedPrefs.edit().putString("label_quantity", value).apply() }
        }
        viewModelScope.launch {
            labelUnitPrice.collect { value -> sharedPrefs.edit().putString("label_unit_price", value).apply() }
        }
        viewModelScope.launch {
            labelLineTotal.collect { value -> sharedPrefs.edit().putString("label_line_total", value).apply() }
        }
        viewModelScope.launch {
            labelSubTotal.collect { value -> sharedPrefs.edit().putString("label_sub_total", value).apply() }
        }
        viewModelScope.launch {
            labelTotal.collect { value -> sharedPrefs.edit().putString("label_total", value).apply() }
        }
        viewModelScope.launch {
            labelPaid.collect { value -> sharedPrefs.edit().putString("label_paid", value).apply() }
        }
        viewModelScope.launch {
            labelDue.collect { value -> sharedPrefs.edit().putString("label_due", value).apply() }
        }
        viewModelScope.launch {
            labelVoucherNo.collect { value -> sharedPrefs.edit().putString("label_voucher_no", value).apply() }
        }

        // Auto-save active voucher as draft on any changes
        viewModelScope.launch {
            combine(activeVoucherId, _cart, activeCustomerName, activePaymentMethod) { voucherId, cartList, customer, payment ->
                if (voucherId != null && !isSavingCompletion) {
                    val existingVoucher = repository.getVoucherDirect(voucherId)
                    if (existingVoucher != null && existingVoucher.isCompleted) {
                        return@combine
                    }
                    if (cartList.isEmpty()) {
                        repository.deleteVoucher(voucherId)
                        repository.deleteVoucherItems(voucherId)
                    } else {
                        val totalAmount = cartList.sumOf { (if (isPurchaseMode.value) it.product.purchasePrice else it.product.sellingPrice) * it.quantity }
                        val totalItems = cartList.sumOf { it.quantity }
                        
                        val timestamp = existingVoucher?.timestamp ?: System.currentTimeMillis()
                        
                        val voucher = Voucher(
                            receiptNo = voucherId,
                            timestamp = timestamp,
                            cashierName = _currentUser.value?.username ?: "Guest Cashier",
                            totalAmount = totalAmount,
                            totalItems = totalItems,
                            customerName = customer,
                            paymentMethod = payment,
                            isCompleted = false,
                            isPurchase = isPurchaseMode.value
                        )
                        
                        repository.insertVoucher(voucher)
                        repository.deleteVoucherItems(voucherId)
                        for (item in cartList) {
                            val vItem = VoucherItem(
                                voucherId = voucherId,
                                productId = item.product.id,
                                productName = item.product.name,
                                quantity = item.quantity,
                                purchasePrice = item.product.purchasePrice,
                                sellingPrice = item.product.sellingPrice
                            )
                            repository.insertVoucherItem(vItem)
                        }
                    }
                }
            }.collect {}
        }

        // Sync logged-in user with database accounts
        viewModelScope.launch {
            allAccounts.collect { accList ->
                if (accList.isNotEmpty()) {
                    val currentPhone = _currentUser.value?.phoneNo
                    if (currentPhone.isNullOrEmpty() || accList.none { it.phoneNo == currentPhone }) {
                        var savedPhone = sharedPrefs.getString("logged_in_phone", null)
                        if (savedPhone.isNullOrEmpty()) {
                            savedPhone = sharedPrefs.getString("last_registered_phone", null)
                        }
                        val matched = if (!savedPhone.isNullOrEmpty()) accList.firstOrNull { it.phoneNo == savedPhone } else null
                        val selected = matched ?: accList.lastOrNull()
                        if (selected != null) {
                            _currentUser.value = selected
                            sharedPrefs.edit().putString("logged_in_phone", selected.phoneNo).putString("last_registered_phone", selected.phoneNo).apply()
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            // Pre-populate default payment methods (Cash and Credit) if empty
            repository.allPayments.first().let { payments ->
                if (payments.isEmpty()) {
                    repository.insertPayment(Payment(method = "Cash", amount = 0.0, date = System.currentTimeMillis()))
                    repository.insertPayment(Payment(method = "Credit", amount = 0.0, date = System.currentTimeMillis()))
                }
            }
        }
    }

    fun setAppLanguage(lang: String) {
        appLanguage.value = lang
        com.example.util.AppLocalization.currentLang = lang
        labelPaid.value = com.example.util.AppLocalization.translate("ပေးငွေ", lang)
        labelDue.value = com.example.util.AppLocalization.translate("အမ်းငွေ", lang)
    }

    // Device ID and Internet helpers
    fun getDeviceId(context: android.content.Context): String {
        val prefs = context.getSharedPreferences("pos_prefs", android.content.Context.MODE_PRIVATE)
        var devId = prefs.getString("device_id", null)
        if (devId.isNullOrEmpty()) {
            val androidId = try {
                android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
            } catch (e: Exception) { null }
            devId = if (!androidId.isNullOrEmpty() && androidId != "9774d56d682e549c") androidId else java.util.UUID.randomUUID().toString()
            prefs.edit().putString("device_id", devId).apply()
        }
        return devId
    }

    fun isInternetAvailable(context: android.content.Context): Boolean {
        val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        if (cm != null) {
            val capabilities = cm.getNetworkCapabilities(cm.activeNetwork)
            if (capabilities != null) {
                return capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) ||
                       capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                       capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)
            }
        }
        return false
    }

    fun checkAccessAuthorization(
        serverStatus: String,
        localAccount: UserAccount?,
        currentDeviceId: String
    ): Boolean {
        if (serverStatus == "expired" || serverStatus == "device_limit_reached" || serverStatus == "off" || serverStatus == "pending" || serverStatus == "device_mismatch") {
            return false
        }
        if (localAccount == null || localAccount.status != "on") {
            return false
        }
        val targetPhone = localAccount.phoneNo
        val expiresAt = sharedPrefs.getLong("account_expires_at_$targetPhone", 0L)
        if (expiresAt > 0L && System.currentTimeMillis() > expiresAt) {
            return false
        }
        return (serverStatus == "on")
    }

    fun checkAccessAuthorization(
        serverIsActive: Boolean,
        localAccount: UserAccount?,
        currentDeviceId: String
    ): Boolean {
        val serverStatus = if (serverIsActive) "on" else "off"
        return checkAccessAuthorization(serverStatus, localAccount, currentDeviceId)
    }

    suspend fun checkUserActivationStatus(context: android.content.Context, phoneNo: String? = null): Pair<Boolean, String> {
        val targetPhone = phoneNo ?: sharedPrefs.getString("logged_in_phone", null) ?: currentUser.value?.phoneNo ?: registerPhone.value
        val deviceId = getDeviceId(context)

        return withContext(Dispatchers.IO) {
            try {
                val apiUrl = cloudflareWorkerUrl.value.trim().removeSuffix("/") + "/api/check-status"
                val url = java.net.URL(apiUrl)
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; utf-8")
                conn.setRequestProperty("Accept", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 6000
                conn.readTimeout = 6000

                val jsonInputString = org.json.JSONObject().apply {
                    if (!targetPhone.isNullOrEmpty()) put("phoneNo", targetPhone)
                    put("deviceId", deviceId)
                }.toString()

                conn.outputStream.use { os ->
                    val input = jsonInputString.toByteArray(charset("utf-8"))
                    os.write(input, 0, input.size)
                }

                if (conn.responseCode in 200..299) {
                    val responseStr = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = org.json.JSONObject(responseStr)
                    val status = json.optString("status", "off")
                    val serverMsg = json.optString("message", "")
                    val expiresAt = json.optLong("expiresAt", 0L)
                    val daysLeft = json.optInt("daysLeft", 7)
                    val isTrial = json.optBoolean("isTrial", true)
                    val localAcc = if (!targetPhone.isNullOrEmpty()) repository.getAccountByPhone(targetPhone) else null

                    if (!targetPhone.isNullOrEmpty()) {
                        sharedPrefs.edit()
                            .putLong("account_expires_at_$targetPhone", expiresAt)
                            .putInt("account_days_left_$targetPhone", daysLeft)
                            .putBoolean("account_is_trial_$targetPhone", isTrial)
                            .apply()
                    }

                    if (status == "expired") {
                        if (localAcc != null) {
                            val updatedAcc = localAcc.copy(status = "expired")
                            repository.insertAccount(updatedAcc)
                            _currentUser.value = updatedAcc
                        }
                        return@withContext Pair(false, "expired")
                    }

                    if (status == "device_limit_reached") {
                        if (localAcc != null) {
                            val updatedAcc = localAcc.copy(status = "device_limit_reached")
                            repository.insertAccount(updatedAcc)
                            _currentUser.value = updatedAcc
                        }
                        return@withContext Pair(false, "device_limit_reached")
                    }

                    if (status == "on") {
                        if (!targetPhone.isNullOrEmpty()) {
                            val acc = repository.getAccountByPhone(targetPhone) ?: _currentUser.value
                            val updatedAcc = acc?.copy(
                                status = "on",
                                deviceId = deviceId
                            ) ?: UserAccount(
                                phoneNo = targetPhone,
                                username = json.optString("username", "User"),
                                businessName = json.optString("businessName", ""),
                                businessType = "",
                                address = "",
                                role = "ADMIN",
                                passwordHash = "",
                                deviceId = deviceId,
                                status = "on"
                            )
                            repository.insertAccount(updatedAcc)
                            _currentUser.value = updatedAcc
                            sharedPrefs.edit().putString("logged_in_phone", targetPhone).putString("last_registered_phone", targetPhone).apply()
                        }
                        Pair(true, "အကောင့် ဖွင့်လှစ်ပြီးပါပြီ (Account Active)")
                    } else {
                        if (localAcc != null) {
                            val updatedAcc = localAcc.copy(status = status)
                            repository.insertAccount(updatedAcc)
                            _currentUser.value = updatedAcc
                        }
                        val msg = if (serverMsg.isNotEmpty()) serverMsg else "အကောင့်ကို Admin မှ မဖွင့်ပေးရသေးပါ (Account Pending)"
                        Pair(false, msg)
                    }
                } else {
                    Pair(false, "အကောင့် အခြေအနေ စစ်ဆေး၍မရပါ (Status Check Failed)")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                val localAcc = if (!targetPhone.isNullOrEmpty()) repository.getAccountByPhone(targetPhone) else null
                if (localAcc != null) {
                    if (localAcc.status == "expired") {
                        Pair(false, "expired")
                    } else if (localAcc.status == "device_limit_reached") {
                        Pair(false, "device_limit_reached")
                    } else if (localAcc.status == "on") {
                        Pair(true, "အကောင့် ဖွင့်လှစ်ပြီးပါပြီ (Offline Active)")
                    } else {
                        Pair(false, "အကောင့်ကို Admin မှ မဖွင့်ပေးရသေးပါ (Account Pending)")
                    }
                } else {
                    Pair(false, "အင်တာနက် ချိတ်ဆက်မှု အဆင်မပြေပါ")
                }
            }
        }
    }

    // Login Method
    suspend fun login(context: android.content.Context, phone: String, pass: String): Triple<Boolean, String, String> {
        val deviceId = getDeviceId(context)

        return withContext(Dispatchers.IO) {
            val localAccCheck = repository.getAccountByPhone(phone)

            try {
                val apiUrl = cloudflareWorkerUrl.value.trim().removeSuffix("/") + "/api/login"
                val url = java.net.URL(apiUrl)
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; utf-8")
                conn.setRequestProperty("Accept", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 6000
                conn.readTimeout = 6000

                val jsonInputString = org.json.JSONObject().apply {
                    put("phoneNo", phone)
                    put("password", pass)
                    put("deviceId", deviceId)
                }.toString()

                conn.outputStream.use { os ->
                    val input = jsonInputString.toByteArray(charset("utf-8"))
                    os.write(input, 0, input.size)
                }

                val responseCode = conn.responseCode
                val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
                val responseStr = stream?.bufferedReader()?.use { it.readText() } ?: ""
                val json = if (responseStr.isNotEmpty()) org.json.JSONObject(responseStr) else org.json.JSONObject()

                val success = json.optBoolean("success", false)
                val message = json.optString("message", "")
                val status = json.optString("status", "")
                val userObj = json.optJSONObject("user")

                // 1. Explicit check for unregistered phone number
                if (responseCode == 404 || status == "not_found" || message.contains("not registered", ignoreCase = true) || message.contains("not found", ignoreCase = true) || message.contains("register ပြုလုပ်ထားခြင်းမရှိပါ") || message.contains("မရှိပါ")) {
                    return@withContext Triple(false, "ဤဖုန်းနံပါတ်ဖြင့် အကောင့်ဖွင့်ထားခြင်း မရှိပါ (Account not registered)", "not_found")
                }

                // 2. Explicit check for incorrect password
                if (responseCode == 401 || status == "invalid_password" || message.contains("password", ignoreCase = true) || message.contains("credentials", ignoreCase = true) || message.contains("မှားယွင်း")) {
                    if (!message.contains("Device", ignoreCase = true) && !message.contains("မတူညီ") && status != "device_mismatch") {
                        return@withContext Triple(false, "Password မှားယွင်းနေပါသည် (Incorrect password)", "invalid_password")
                    }
                }

                // 3. Status checks: Expiration and Device Limit
                if (status == "expired") {
                    val expiresAt = json.optLong("expiresAt", 0L)
                    sharedPrefs.edit()
                        .putString("logged_in_phone", phone)
                        .putLong("account_expires_at_$phone", expiresAt)
                        .putInt("account_days_left_$phone", 0)
                        .apply()
                    val localAcc = repository.getAccountByPhone(phone)
                    if (localAcc != null) {
                        repository.insertAccount(localAcc.copy(status = "expired"))
                    }
                    return@withContext Triple(false, if (message.isNotEmpty()) message else "အကောင့် သက်တမ်းကုန်ဆုံးသွားပါပြီ (Subscription Expired)", "expired")
                }

                if (status == "device_limit_reached") {
                    return@withContext Triple(false, if (message.isNotEmpty()) message else "ဤအကောင့်ကို အများဆုံး စက် ၅ လုံးအထိသာ သုံးခွင့်ပြုထားပါသည် (Device limit of 5 reached)", "device_limit_reached")
                }

                if (success && status == "on") {
                    val expiresAt = json.optLong("expiresAt", 0L)
                    val daysLeft = json.optInt("daysLeft", 7)
                    val isTrial = json.optBoolean("isTrial", true)
                    sharedPrefs.edit()
                        .putString("logged_in_phone", phone)
                        .putString("last_registered_phone", phone)
                        .putLong("account_expires_at_$phone", expiresAt)
                        .putInt("account_days_left_$phone", daysLeft)
                        .putBoolean("account_is_trial_$phone", isTrial)
                        .apply()

                    val remoteUsername = userObj?.optString("username")?.takeIf { it.isNotEmpty() && it != "null" }
                    val remoteBusinessName = userObj?.optString("businessName")?.takeIf { it.isNotEmpty() && it != "null" }
                    val remoteBusinessType = userObj?.optString("businessType")?.takeIf { it.isNotEmpty() && it != "null" }
                    val remoteAddress = userObj?.optString("address")?.takeIf { it.isNotEmpty() && it != "null" }
                    val remoteRole = userObj?.optString("role")?.takeIf { it.isNotEmpty() && it != "null" }

                    val remoteAccount = UserAccount(
                        phoneNo = phone,
                        username = remoteUsername ?: localAccCheck?.username ?: "User",
                        businessName = remoteBusinessName ?: localAccCheck?.businessName ?: "",
                        businessType = remoteBusinessType ?: localAccCheck?.businessType ?: "",
                        address = remoteAddress ?: localAccCheck?.address ?: "",
                        role = remoteRole ?: localAccCheck?.role ?: "ADMIN",
                        passwordHash = pass,
                        deviceId = deviceId,
                        status = "on"
                    )
                    repository.insertAccount(remoteAccount)
                    _currentUser.value = remoteAccount
                    Triple(true, "Login Successful", "on")
                } else if (status == "off" || status == "pending" || message.contains("စောင့်ဆိုင်း") || message.contains("မဖွင့်ပေးရသေးပါ")) {
                    sharedPrefs.edit().putString("logged_in_phone", phone).putString("last_registered_phone", phone).apply()
                    Triple(false, if (message.isNotEmpty()) message else "အကောင့်ကို Admin မှ မဖွင့်ပေးရသေးပါ (Account Pending)", "pending")
                } else {
                    val finalMsg = if (message.isNotEmpty()) message else "ဖုန်းနံပါတ် သို့မဟုတ် စကားဝှက် မှားယွင်းနေပါသည်"
                    Triple(false, finalMsg, "failed")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Offline fallback
                val localAcc = repository.getAccountByPhone(phone)
                if (localAcc != null) {
                    if (localAcc.passwordHash != pass) {
                        return@withContext Triple(false, "Password မှားယွင်းနေပါသည် (Incorrect password)", "invalid_password")
                    }
                    if (localAcc.status == "expired") {
                        return@withContext Triple(false, "အကောင့် သက်တမ်းကုန်ဆုံးသွားပါပြီ (Subscription Expired)", "expired")
                    }
                    if (localAcc.status == "device_limit_reached") {
                        return@withContext Triple(false, "ဤအကောင့်ကို အများဆုံး စက် ၅ လုံးအထိသာ သုံးခွင့်ပြုထားပါသည် (Device limit of 5 reached)", "device_limit_reached")
                    }
                    val isDeviceMatch = localAcc.deviceId.isNotBlank() && localAcc.deviceId == deviceId
                    if (!isDeviceMatch) {
                        sharedPrefs.edit().putString("logged_in_phone", phone).putString("last_registered_phone", phone).apply()
                        return@withContext Triple(false, "ဒီဖုန်းနံပါတ်သည် အခြား Device တွင် Register ပြုလုပ်ထားပါသည် (Device ID မတူညီပါ)", "device_mismatch")
                    }
                    // Allow valid password login for accounts in local DB (e.g. restored database)
                    val activeAcc = localAcc.copy(status = "on")
                    repository.insertAccount(activeAcc)
                    _currentUser.value = activeAcc
                    sharedPrefs.edit().putString("logged_in_phone", phone).putString("last_registered_phone", phone).apply()
                    Triple(true, "Offline Login Successful", "on")
                } else {
                    Triple(false, "ဤဖုန်းနံပါတ်ဖြင့် အကောင့်ဖွင့်ထားခြင်း မရှိပါ (Account not registered)", "not_found")
                }
            }
        }
    }

    fun getAccountTrialInfo(phoneNo: String? = null): Triple<Long, Int, Boolean> {
        val phone = phoneNo ?: sharedPrefs.getString("logged_in_phone", null) ?: currentUser.value?.phoneNo ?: ""
        val expiresAt = sharedPrefs.getLong("account_expires_at_$phone", 0L)
        val daysLeft = sharedPrefs.getInt("account_days_left_$phone", -1)
        val isTrial = sharedPrefs.getBoolean("account_is_trial_$phone", false)
        return Triple(expiresAt, daysLeft, isTrial)
    }

    fun insertAccount(account: UserAccount) {
        viewModelScope.launch {
            repository.insertAccount(account)
        }
    }

    // Trigger Registration Simulation & Send Data to Cloudflare D1
    fun runRegistration(context: android.content.Context, onComplete: (String) -> Unit) {
        viewModelScope.launch {
            _registrationProgress.value = 0f
            _registrationStatus.value = "Registering..."

            val deviceId = getDeviceId(context)

            val stages = listOf(0.00f, 0.05f, 0.22f, 0.50f, 0.74f, 0.95f, 1.00f)
            for (stage in stages) {
                _registrationProgress.value = stage
                delay(300)
            }

            _registrationStatus.value = "Successfully Finished"

            // Save user in local database with status = "off" (will be updated on trial activation)
            val newUser = UserAccount(
                phoneNo = registerPhone.value,
                username = registerUsername.value,
                businessName = registerBusinessName.value,
                businessType = registerBusinessType.value,
                address = registerAddress.value,
                role = selectedRole.value,
                passwordHash = registerPassword.value,
                deviceId = deviceId,
                status = "off"
            )
            repository.insertAccount(newUser)
            _currentUser.value = newUser
            sharedPrefs.edit().putString("logged_in_phone", newUser.phoneNo).putString("last_registered_phone", newUser.phoneNo).apply()

            var registrationFinalStatus = "off"

            // Asynchronously send data to Cloudflare D1 Database via Worker
            withContext(Dispatchers.IO) {
                try {
                    val apiUrl = cloudflareWorkerUrl.value.trim().removeSuffix("/") + "/api/register"
                    val url = java.net.URL(apiUrl)
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json; utf-8")
                    conn.setRequestProperty("Accept", "application/json")
                    conn.doOutput = true
                    conn.connectTimeout = 6000
                    conn.readTimeout = 6000

                    val jsonInputString = org.json.JSONObject().apply {
                        put("phoneNo", newUser.phoneNo)
                        put("username", newUser.username)
                        put("businessName", newUser.businessName)
                        put("businessType", newUser.businessType)
                        put("address", newUser.address)
                        put("role", newUser.role)
                        put("password", newUser.passwordHash)
                        put("deviceId", deviceId)
                    }.toString()

                    conn.outputStream.use { os ->
                        val input = jsonInputString.toByteArray(charset("utf-8"))
                        os.write(input, 0, input.size)
                    }

                    if (conn.responseCode in 200..299) {
                        val respStr = conn.inputStream.bufferedReader().use { it.readText() }
                        val json = org.json.JSONObject(respStr)
                        val st = json.optString("status", "on")
                        registrationFinalStatus = st
                        val expiresAt = json.optLong("expiresAt", System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L)
                        val daysLeft = json.optInt("daysLeft", 7)

                        sharedPrefs.edit()
                            .putLong("account_expires_at_${newUser.phoneNo}", expiresAt)
                            .putInt("account_days_left_${newUser.phoneNo}", daysLeft)
                            .putBoolean("account_is_trial_${newUser.phoneNo}", true)
                            .apply()

                        val updatedUser = newUser.copy(status = st)
                        repository.insertAccount(updatedUser)
                        _currentUser.value = updatedUser
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            delay(300)
            onComplete(registrationFinalStatus)
        }
    }

    // Change Password Method
    suspend fun changePassword(phone: String, oldPass: String, newPass: String): Pair<Boolean, String> {
        return withContext(Dispatchers.IO) {
            try {
                val apiUrl = cloudflareWorkerUrl.value.trim().removeSuffix("/") + "/api/change-password"
                val url = java.net.URL(apiUrl)
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; utf-8")
                conn.setRequestProperty("Accept", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 6000
                conn.readTimeout = 6000

                val jsonInputString = org.json.JSONObject().apply {
                    put("phoneNo", phone)
                    put("oldPassword", oldPass)
                    put("newPassword", newPass)
                }.toString()

                conn.outputStream.use { os ->
                    val input = jsonInputString.toByteArray(charset("utf-8"))
                    os.write(input, 0, input.size)
                }

                val responseCode = conn.responseCode
                val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
                val responseStr = stream?.bufferedReader()?.use { it.readText() } ?: ""
                val json = if (responseStr.isNotEmpty()) org.json.JSONObject(responseStr) else org.json.JSONObject()

                val success = json.optBoolean("success", false)
                val message = json.optString("message", if (success) "Password changed successfully" else "Failed to change password")

                if (success) {
                    val localAcc = repository.getAccountByPhone(phone)
                    if (localAcc != null) {
                        val updated = localAcc.copy(passwordHash = newPass)
                        repository.insertAccount(updated)
                        if (_currentUser.value?.phoneNo == phone) {
                            _currentUser.value = updated
                        }
                    }
                }
                Pair(success, message)
            } catch (e: Exception) {
                e.printStackTrace()
                val localAcc = repository.getAccountByPhone(phone)
                if (localAcc != null && localAcc.passwordHash == oldPass) {
                    val updated = localAcc.copy(passwordHash = newPass)
                    repository.insertAccount(updated)
                    if (_currentUser.value?.phoneNo == phone) {
                        _currentUser.value = updated
                    }
                    Pair(true, "Password changed locally")
                } else if (localAcc != null && localAcc.passwordHash != oldPass) {
                    Pair(false, "ယခင်စကားဝှက် မှားယွင်းနေပါသည်")
                } else {
                    Pair(false, "အင်တာနက် ချိတ်ဆက်မှု အဆင်မပြေပါ")
                }
            }
        }
    }

    suspend fun updatePasswordDirectly(phone: String, newPass: String): Pair<Boolean, String> {
        return withContext(Dispatchers.IO) {
            val localAcc = repository.getAccountByPhone(phone)
            val oldPass = localAcc?.passwordHash ?: ""
            if (oldPass.isNotEmpty()) {
                val res = changePassword(phone, oldPass, newPass)
                if (res.first) return@withContext res
            }
            try {
                if (localAcc != null) {
                    val updated = localAcc.copy(passwordHash = newPass)
                    repository.insertAccount(updated)
                    if (_currentUser.value?.phoneNo == phone) {
                        _currentUser.value = updated
                    }
                    Pair(true, "စကားဝှက် ပြောင်းလဲပြီးပါပြီ")
                } else {
                    Pair(false, "အကောင့် ရှာမတွေ့ပါ")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Pair(false, e.message ?: "စကားဝှက် ပြောင်းလဲ၍ မရပါ")
            }
        }
    }

    // Delete User Account Method
    suspend fun deleteUserAccount(phone: String, pass: String): Pair<Boolean, String> {
        return withContext(Dispatchers.IO) {
            try {
                val apiUrl = cloudflareWorkerUrl.value.trim().removeSuffix("/") + "/api/delete-user"
                val url = java.net.URL(apiUrl)
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; utf-8")
                conn.setRequestProperty("Accept", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 6000
                conn.readTimeout = 6000

                val jsonInputString = org.json.JSONObject().apply {
                    put("phoneNo", phone)
                    put("password", pass)
                }.toString()

                conn.outputStream.use { os ->
                    val input = jsonInputString.toByteArray(charset("utf-8"))
                    os.write(input, 0, input.size)
                }

                val responseCode = conn.responseCode
                val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
                val responseStr = stream?.bufferedReader()?.use { it.readText() } ?: ""
                val json = if (responseStr.isNotEmpty()) org.json.JSONObject(responseStr) else org.json.JSONObject()

                val success = json.optBoolean("success", false)
                val message = json.optString("message", if (success) "Account deleted successfully" else "Failed to delete account")

                if (success) {
                    val localAcc = repository.getAccountByPhone(phone)
                    if (localAcc != null) {
                        repository.deleteAccount(localAcc)
                        if (_currentUser.value?.phoneNo == phone) {
                            _currentUser.value = null
                            sharedPrefs.edit().remove("logged_in_phone").apply()
                        }
                    }
                }
                Pair(success, message)
            } catch (e: Exception) {
                e.printStackTrace()
                val localAcc = repository.getAccountByPhone(phone)
                if (localAcc != null && localAcc.passwordHash == pass) {
                    repository.deleteAccount(localAcc)
                    if (_currentUser.value?.phoneNo == phone) {
                        _currentUser.value = null
                        sharedPrefs.edit().remove("logged_in_phone").apply()
                    }
                    Pair(true, "Account deleted locally")
                } else if (localAcc != null && localAcc.passwordHash != pass) {
                    Pair(false, "စကားဝှက် မှားယွင်းနေပါသည်")
                } else {
                    Pair(false, "အင်တာနက် ချိတ်ဆက်မှု အဆင်မပြေပါ")
                }
            }
        }
    }

    // Add/Update Product Method
    fun addProduct(onSuccess: (Product?) -> Unit) {
        viewModelScope.launch {
            val name = prodName.value
            val group = prodGroup.value
            val pPrice = prodPurchasePrice.value.toDoubleOrNull() ?: 0.0
            val sPrice = prodSellingPrice.value.toDoubleOrNull() ?: 0.0
            val unit = prodUnit.value
            val note = prodNote.value
            val track = prodTrackStock.value
            val barcode = prodBarcode.value
            val qty = prodQty.value.toIntOrNull() ?: 0
            val alert = prodAlertQty.value.toIntOrNull() ?: 0
            val imgUri = prodImageUri.value

            if (name.isNotEmpty() && sPrice > 0.0) {
                val currentEditing = editingProduct.value
                var savedProduct: Product? = null
                if (currentEditing != null) {
                    val updatedProduct = currentEditing.copy(
                        name = name,
                        groupName = group,
                        purchasePrice = pPrice,
                        sellingPrice = sPrice,
                        unit = unit,
                        note = note,
                        trackStock = track,
                        barcode = barcode,
                        quantity = qty,
                        alertQuantity = alert,
                        imageUri = imgUri,
                        isSynced = false
                    )
                    repository.updateProduct(updatedProduct)
                    repository.updateStockAndAlert(currentEditing.id, qty, alert)
                    editingProduct.value = null
                    savedProduct = updatedProduct

                    // Immediately update cart if product is currently in cart
                    val currentCart = _cart.value.toMutableList()
                    val idx = currentCart.indexOfFirst { it.product.id == updatedProduct.id }
                    if (idx >= 0) {
                        currentCart[idx] = currentCart[idx].copy(product = updatedProduct)
                        _cart.value = currentCart
                    }
                } else {
                    val product = Product(
                        name = name,
                        groupName = group,
                        purchasePrice = pPrice,
                        sellingPrice = sPrice,
                        unit = unit,
                        note = note,
                        trackStock = track,
                        barcode = barcode,
                        quantity = qty,
                        alertQuantity = alert,
                        imageUri = imgUri,
                        isSynced = false
                    )
                    val newId = repository.insertProduct(product)
                    savedProduct = product.copy(id = newId)
                }

                // Auto-sync in background if online
                if (com.example.util.SyncManager.isNetworkAvailable(getApplication())) {
                    viewModelScope.launch {
                        triggerRealSync()
                    }
                }

                // Clear state
                prodName.value = ""
                prodGroup.value = ""
                prodPurchasePrice.value = ""
                prodSellingPrice.value = ""
                prodUnit.value = ""
                prodNote.value = ""
                prodTrackStock.value = true
                prodBarcode.value = ""
                prodQty.value = ""
                prodAlertQty.value = ""
                prodImageUri.value = ""

                onSuccess(savedProduct)
            }
        }
    }

    fun insertDirectProduct(product: Product, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            repository.insertProduct(product.copy(isSynced = false))
            if (product.groupName.isNotEmpty()) {
                repository.insertGroup(ProductGroup(product.groupName))
            }
            if (product.unit.isNotEmpty()) {
                repository.insertUnit(ProductUnit(product.unit))
            }
            if (isOnlineState.value && isServerReachable.value) {
                triggerRealSync()
            }
            onSuccess()
        }
    }

    fun recordPendingDeletedProduct(id: Long, name: String) {
        val currentSet = sharedPrefs.getStringSet("pending_deleted_products", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        currentSet.add("$id::$name")
        sharedPrefs.edit().putStringSet("pending_deleted_products", currentSet).apply()
    }

    fun removePendingDeletedProduct(id: Long, name: String) {
        val currentSet = sharedPrefs.getStringSet("pending_deleted_products", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        currentSet.remove("$id::$name")
        sharedPrefs.edit().putStringSet("pending_deleted_products", currentSet).apply()
    }

    fun getPendingDeletedProducts(): Pair<List<Long>, List<String>> {
        val set = sharedPrefs.getStringSet("pending_deleted_products", emptySet()) ?: emptySet()
        val ids = mutableListOf<Long>()
        val names = mutableListOf<String>()
        for (entry in set) {
            val parts = entry.split("::")
            parts.getOrNull(0)?.toLongOrNull()?.let { ids.add(it) }
            parts.getOrNull(1)?.let { if (it.isNotEmpty()) names.add(it) }
        }
        return Pair(ids, names)
    }

    fun clearPendingDeletedProducts() {
        sharedPrefs.edit().remove("pending_deleted_products").apply()
    }

    fun recordPendingDeletedVoucher(receiptNo: String) {
        val currentSet = sharedPrefs.getStringSet("pending_deleted_vouchers", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        currentSet.add(receiptNo)
        sharedPrefs.edit().putStringSet("pending_deleted_vouchers", currentSet).apply()
    }

    fun removePendingDeletedVoucher(receiptNo: String) {
        val currentSet = sharedPrefs.getStringSet("pending_deleted_vouchers", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        currentSet.remove(receiptNo)
        sharedPrefs.edit().putStringSet("pending_deleted_vouchers", currentSet).apply()
    }

    fun getPendingDeletedVouchers(): List<String> {
        val set = sharedPrefs.getStringSet("pending_deleted_vouchers", emptySet()) ?: emptySet()
        return set.toList()
    }

    fun clearPendingDeletedVouchers() {
        sharedPrefs.edit().remove("pending_deleted_vouchers").apply()
    }

    fun deleteProduct(product: Product, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.deleteProduct(product)
            val currentCart = _cart.value.toMutableList()
            currentCart.removeAll { it.product.id == product.id }
            _cart.value = currentCart

            recordPendingDeletedProduct(product.id, product.name)

            val phone = _currentUser.value?.phoneNo ?: sharedPrefs.getString("logged_in_phone", "") ?: ""
            if (phone.isNotEmpty() && com.example.util.SyncManager.isNetworkAvailable(getApplication())) {
                viewModelScope.launch(Dispatchers.IO) {
                    val ok = com.example.util.SyncManager.deleteSingleProductOnCloud(
                        workerUrl = cloudflareWorkerUrl.value,
                        userPhone = phone,
                        productId = product.id,
                        productName = product.name
                    )
                    if (ok) {
                        removePendingDeletedProduct(product.id, product.name)
                    }
                }
            }
            onSuccess()
        }
    }

    fun clearProductForm() {
        prodName.value = ""
        prodGroup.value = ""
        prodPurchasePrice.value = ""
        prodSellingPrice.value = ""
        prodUnit.value = ""
        prodNote.value = ""
        prodTrackStock.value = true
        prodBarcode.value = ""
        prodQty.value = ""
        prodAlertQty.value = ""
        prodImageUri.value = ""
    }

    fun loadProductToForm(product: Product) {
        editingProduct.value = product
        prodName.value = product.name
        prodGroup.value = product.groupName
        prodPurchasePrice.value = if (product.purchasePrice > 0.0) product.purchasePrice.toString() else ""
        prodSellingPrice.value = if (product.sellingPrice > 0.0) product.sellingPrice.toString() else ""
        prodUnit.value = product.unit
        prodNote.value = product.note
        prodTrackStock.value = product.trackStock
        prodBarcode.value = product.barcode
        prodQty.value = product.quantity.toString()
        prodAlertQty.value = product.alertQuantity.toString()
        prodImageUri.value = product.imageUri
    }

    // Product Group Management
    fun addProductGroup(name: String) {
        viewModelScope.launch {
            if (name.isNotEmpty()) {
                repository.insertGroup(ProductGroup(name))
            }
        }
    }

    fun updateProductGroup(oldGroup: ProductGroup, newName: String) {
        viewModelScope.launch {
            if (newName.isNotEmpty() && oldGroup.name != newName) {
                repository.deleteGroup(oldGroup)
                repository.insertGroup(ProductGroup(newName))
            }
        }
    }

    fun deleteProductGroup(group: ProductGroup) {
        viewModelScope.launch {
            repository.deleteGroup(group)
        }
    }

    // Product Unit Management
    fun addProductUnit(name: String) {
        viewModelScope.launch {
            if (name.isNotEmpty()) {
                repository.insertUnit(ProductUnit(name))
            }
        }
    }

    fun updateProductUnit(oldUnit: ProductUnit, newName: String) {
        viewModelScope.launch {
            if (newName.isNotEmpty() && oldUnit.name != newName) {
                repository.deleteUnit(oldUnit)
                repository.insertUnit(ProductUnit(newName))
            }
        }
    }

    fun deleteProductUnit(unit: ProductUnit) {
        viewModelScope.launch {
            repository.deleteUnit(unit)
        }
    }

    // Customer Management
    fun addCustomer(name: String, phone: String, address: String, note: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val cleanName = name.trim()
            if (cleanName.isNotEmpty()) {
                val existing = repository.getAllCustomersDirect().find { it.name.trim().equals(cleanName, ignoreCase = true) }
                if (existing != null) {
                    repository.updateCustomer(existing.copy(phone = phone, address = address, note = note, isSynced = false))
                } else {
                    repository.insertCustomer(Customer(name = cleanName, phone = phone, address = address, note = note, isSynced = false))
                }
                onSuccess()
            }
        }
    }

    fun updateCustomer(customer: Customer, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.updateCustomer(customer.copy(isSynced = false))
            onSuccess()
        }
    }

    fun deleteCustomer(customer: Customer, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.deleteCustomer(customer)
            onSuccess()
        }
    }

    // Supplier Management
    fun addSupplier(name: String, phone: String, address: String, note: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val cleanName = name.trim()
            if (cleanName.isNotEmpty()) {
                val existing = repository.getAllSuppliersDirect().find { it.name.trim().equals(cleanName, ignoreCase = true) }
                if (existing != null) {
                    repository.updateSupplier(existing.copy(phone = phone, address = address, note = note))
                } else {
                    repository.insertSupplier(Supplier(name = cleanName, phone = phone, address = address, note = note))
                }
                onSuccess()
            }
        }
    }

    fun updateSupplier(supplier: Supplier, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.updateSupplier(supplier)
            onSuccess()
        }
    }

    fun deleteSupplier(supplier: Supplier, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.deleteSupplier(supplier)
            onSuccess()
        }
    }

    // Payment Methods Management
    fun addPayment(method: String) {
        viewModelScope.launch {
            if (method.isNotEmpty()) {
                repository.insertPayment(Payment(method = method, amount = 0.0, date = System.currentTimeMillis()))
            }
        }
    }

    fun updatePayment(payment: Payment, newMethod: String) {
        viewModelScope.launch {
            if (newMethod.isNotEmpty()) {
                repository.updatePayment(payment.copy(method = newMethod))
            }
        }
    }

    fun deletePayment(payment: Payment) {
        viewModelScope.launch {
            repository.deletePayment(payment)
        }
    }

    // Cart Actions
    fun addToCart(product: Product) {
        val current = _cart.value.toMutableList()
        val index = current.indexOfFirst { it.product.id == product.id }
        if (index != -1) {
            val oldItem = current[index]
            current[index] = oldItem.copy(quantity = oldItem.quantity + 1)
        } else {
            current.add(CartItem(product, 1))
        }
        _cart.value = current
    }

    fun updateCartQuantity(productId: Long, qty: Int) {
        val current = _cart.value.toMutableList()
        val index = current.indexOfFirst { it.product.id == productId }
        if (index != -1) {
            if (qty <= 0) {
                current.removeAt(index)
            } else {
                current[index] = current[index].copy(quantity = qty)
            }
            _cart.value = current
        }
    }

    fun clearCart() {
        _cart.value = emptyList()
    }

    fun resetActiveVoucher() {
        activeVoucherId.value = null
        activeCustomerName.value = "Not Register"
        activePaymentMethod.value = "CASH"
        activeDiscount.value = 0.0
        activeFee.value = 0.0
        activeNote.value = ""
        _cart.value = emptyList()
        editingVoucherInitialItems.value = emptyMap()
        editingVoucher.value = null
    }

    fun startNewVoucher() {
        isSavingCompletion = true
        val formatter = SimpleDateFormat("yyMMdd-HHmmss", Locale.getDefault())
        val rand = (10000000..99999999).random()
        val receiptNo = "A" + formatter.format(Date()).substring(0, 6) + "-" + rand
        
        activeVoucherId.value = receiptNo
        activeCustomerName.value = "Not Register"
        activePaymentMethod.value = "CASH"
        activeDiscount.value = 0.0
        activeFee.value = 0.0
        activeNote.value = ""
        _cart.value = emptyList()
        editingVoucherInitialItems.value = emptyMap()
        editingVoucher.value = null
        isSavingCompletion = false
    }

    fun loadDraftVoucher(voucher: Voucher) {
        isSavingCompletion = true
        editingVoucher.value = voucher
        viewModelScope.launch {
            isPurchaseMode.value = voucher.isPurchase
            activeVoucherId.value = voucher.receiptNo
            activeCustomerName.value = voucher.customerName
            activePaymentMethod.value = voucher.paymentMethod
            activeDiscount.value = voucher.discount
            activeFee.value = voucher.fee
            activeNote.value = voucher.note
            
            // Get voucher items from direct
            val dbItems = repository.getVoucherItemsDirect(voucher.receiptNo)
            val productsList = repository.allProducts.first()

            val initialMap = mutableMapOf<Long, Int>()
            if (voucher.isCompleted && !voucher.isPurchase) {
                for (item in dbItems) {
                    val pId = if (item.productId != 0L) {
                        item.productId
                    } else {
                        productsList.find { it.name.equals(item.productName, ignoreCase = true) }?.id ?: 0L
                    }
                    if (pId != 0L) {
                        initialMap[pId] = (initialMap[pId] ?: 0) + item.quantity
                    }
                }
            }
            editingVoucherInitialItems.value = initialMap
            
            // Map to CartItems
            val cartItems = dbItems.mapNotNull { item ->
                val baseProd = productsList.find { it.id == item.productId || it.name.equals(item.productName, ignoreCase = true) }
                val prod = if (baseProd != null) {
                    baseProd.copy(
                        name = item.productName,
                        purchasePrice = item.purchasePrice,
                        sellingPrice = item.sellingPrice
                    )
                } else {
                    Product(
                        id = item.productId,
                        name = item.productName,
                        groupName = "",
                        purchasePrice = item.purchasePrice,
                        sellingPrice = item.sellingPrice,
                        unit = "",
                        note = "",
                        trackStock = false,
                        barcode = "",
                        quantity = 0,
                        alertQuantity = 0
                    )
                }
                CartItem(product = prod, quantity = item.quantity)
            }
            _cart.value = cartItems
            isSavingCompletion = false
        }
    }

    fun saveOrUpdateVoucher(
        isCompleted: Boolean,
        customTimestamp: Long? = null,
        customTotalAmount: Double? = null,
        paidAmount: Double = 0.0,
        changeAmount: Double = 0.0,
        balanceAmount: Double = 0.0,
        note: String = activeNote.value,
        discount: Double = activeDiscount.value,
        fee: Double = activeFee.value,
        onComplete: () -> Unit
    ) {
        val voucherId = activeVoucherId.value ?: return
        val items = _cart.value
        
        isSavingCompletion = true
        
        viewModelScope.launch {
            val totalAmount = customTotalAmount ?: items.sumOf { (if (isPurchaseMode.value) it.product.purchasePrice else it.product.sellingPrice) * it.quantity }
            val totalItems = items.sumOf { it.quantity }
            
            val voucher = Voucher(
                receiptNo = voucherId,
                timestamp = customTimestamp ?: System.currentTimeMillis(),
                cashierName = _currentUser.value?.username ?: "Guest Cashier",
                totalAmount = totalAmount,
                totalItems = totalItems,
                customerName = activeCustomerName.value,
                paymentMethod = activePaymentMethod.value,
                isCompleted = isCompleted,
                isPurchase = isPurchaseMode.value,
                paidAmount = paidAmount,
                changeAmount = changeAmount,
                balanceAmount = balanceAmount,
                note = note,
                discount = discount,
                fee = fee
            )
            
            // Revert stock of previous completed items if updating an existing voucher
            val existingVoucher = repository.getVoucherDirect(voucherId)
            if (existingVoucher != null && existingVoucher.isCompleted) {
                val existingItems = repository.getVoucherItemsDirect(voucherId)
                val allProds = repository.allProducts.first()
                for (oldItem in existingItems) {
                    val product = if (oldItem.productId != 0L) {
                        repository.getProductDirect(oldItem.productId)
                    } else {
                        allProds.find { it.name.equals(oldItem.productName, ignoreCase = true) }
                    }
                    if (product != null && product.trackStock) {
                        val restoredQty = if (existingVoucher.isPurchase) {
                            product.quantity - oldItem.quantity
                        } else {
                            product.quantity + oldItem.quantity
                        }
                        repository.updateStockLocally(product.id, restoredQty)
                    }
                }
            }

            repository.insertVoucher(voucher)
            repository.deleteVoucherItems(voucherId)
            
            val stockUpdates = mutableMapOf<Long, Int>()
            for (item in items) {
                val vItem = VoucherItem(
                    voucherId = voucherId,
                    productId = item.product.id,
                    productName = item.product.name,
                    quantity = item.quantity,
                    purchasePrice = item.product.purchasePrice,
                    sellingPrice = item.product.sellingPrice
                )
                repository.insertVoucherItem(vItem)
                
                if (isCompleted && item.product.trackStock) {
                    stockUpdates[item.product.id] = (stockUpdates[item.product.id] ?: 0) + item.quantity
                }
            }
            
            if (isCompleted) {
                for ((pId, totalQty) in stockUpdates) {
                    val dbProduct = repository.getProductDirect(pId)
                    if (dbProduct != null && dbProduct.trackStock) {
                        val remaining = if (isPurchaseMode.value) {
                            dbProduct.quantity + totalQty
                        } else {
                            (dbProduct.quantity - totalQty).coerceAtLeast(0)
                        }
                        repository.updateStockLocally(pId, remaining)
                    }
                }
            }
            
            activeVoucherId.value = null
            activeCustomerName.value = "Not Register"
            activePaymentMethod.value = "CASH"
            _cart.value = emptyList()
            editingVoucherInitialItems.value = emptyMap()
            editingVoucher.value = null
            isSavingCompletion = false
            onComplete()

            // Real-time Cloud Sync for online checkout
            if (isCompleted) {
                val phone = _currentUser.value?.phoneNo ?: sharedPrefs.getString("logged_in_phone", "") ?: ""
                val isOnlineNow = com.example.util.SyncManager.isNetworkAvailable(getApplication())
                if (isOnlineNow && phone.isNotEmpty()) {
                    viewModelScope.launch {
                        val currentVoucherItems = repository.getVoucherItemsDirect(voucherId)
                        val synced = com.example.util.SyncManager.syncSingleVoucher(
                            workerUrl = cloudflareWorkerUrl.value,
                            userPhone = phone,
                            voucher = voucher,
                            items = currentVoucherItems
                        )
                        if (synced) {
                            repository.markVoucherSynced(voucherId)
                            _isOfflineSaved.value = true
                        } else {
                            _isOfflineSaved.value = false
                        }
                        // Also sync updated products to cloud so stock matches local immediately!
                        triggerRealSync()
                    }
                } else {
                    _isOfflineSaved.value = false
                }
            }
        }
    }

    fun deleteVoucher(voucherId: String) {
        viewModelScope.launch {
            val voucher = repository.getVoucherDirect(voucherId)
            if (voucher != null && voucher.isCompleted) {
                val items = repository.getVoucherItemsDirect(voucherId)
                for (item in items) {
                    val product = repository.getProductDirect(item.productId)
                    if (product != null && product.trackStock) {
                        val newQty = if (voucher.isPurchase) {
                            product.quantity - item.quantity
                        } else {
                            product.quantity + item.quantity
                        }
                        repository.updateStockLocally(product.id, newQty)
                    }
                }
            }
            repository.deleteVoucher(voucherId)
            repository.deleteVoucherItems(voucherId)

            recordPendingDeletedVoucher(voucherId)

            val phone = _currentUser.value?.phoneNo ?: sharedPrefs.getString("logged_in_phone", "") ?: ""
            if (phone.isNotEmpty() && com.example.util.SyncManager.isNetworkAvailable(getApplication())) {
                viewModelScope.launch(Dispatchers.IO) {
                    val ok = com.example.util.SyncManager.deleteVoucherOnCloud(
                        workerUrl = cloudflareWorkerUrl.value,
                        userPhone = phone,
                        receiptNo = voucherId
                    )
                    if (ok) {
                        removePendingDeletedVoucher(voucherId)
                    }
                }
                triggerRealSync()
            }
        }
    }

    fun updateVoucherDirectly(voucher: Voucher) {
        viewModelScope.launch {
            repository.insertVoucher(voucher.copy(isSynced = false))
            if (isOnlineState.value && isServerReachable.value) {
                triggerRealSync()
            }
        }
    }

    fun updateProductStock(productId: Long, newQuantity: Int) {
        viewModelScope.launch {
            repository.updateStock(productId, newQuantity)
            if (com.example.util.SyncManager.isNetworkAvailable(getApplication())) {
                triggerRealSync()
            }
        }
    }

    fun updateProductStockAndAlert(productId: Long, newQuantity: Int, newAlertQuantity: Int) {
        viewModelScope.launch {
            repository.updateStockAndAlert(productId, newQuantity, newAlertQuantity)
            if (com.example.util.SyncManager.isNetworkAvailable(getApplication())) {
                triggerRealSync()
            }
        }
    }

    fun updateCartItemPrice(productId: Long, newPrice: Double) {
        val current = _cart.value.toMutableList()
        val index = current.indexOfFirst { it.product.id == productId }
        if (index != -1) {
            val oldItem = current[index]
            val updatedProduct = if (isPurchaseMode.value) {
                oldItem.product.copy(purchasePrice = newPrice)
            } else {
                oldItem.product.copy(sellingPrice = newPrice)
            }
            current[index] = oldItem.copy(product = updatedProduct)
            _cart.value = current
        }
    }

    fun updateCartItemQuantityByIndex(index: Int, qty: Int) {
        val current = _cart.value.toMutableList()
        if (index >= 0 && index < current.size) {
            if (qty <= 0) {
                current.removeAt(index)
            } else {
                current[index] = current[index].copy(quantity = qty)
            }
            _cart.value = current
        }
    }

    fun updateCartItemPriceByIndex(index: Int, newPrice: Double) {
        val current = _cart.value.toMutableList()
        if (index >= 0 && index < current.size) {
            val oldItem = current[index]
            val updatedProduct = if (isPurchaseMode.value) {
                oldItem.product.copy(purchasePrice = newPrice)
            } else {
                oldItem.product.copy(sellingPrice = newPrice)
            }
            current[index] = oldItem.copy(product = updatedProduct)
            _cart.value = current
        }
    }

    fun updateCartItemNameByIndex(index: Int, newName: String) {
        val current = _cart.value.toMutableList()
        if (index >= 0 && index < current.size) {
            val oldItem = current[index]
            current[index] = oldItem.copy(product = oldItem.product.copy(name = newName))
            _cart.value = current
        }
    }

    fun updateCartItemPurchasePriceByIndex(index: Int, newPurchasePrice: Double) {
        val current = _cart.value.toMutableList()
        if (index >= 0 && index < current.size) {
            val oldItem = current[index]
            current[index] = oldItem.copy(product = oldItem.product.copy(purchasePrice = newPurchasePrice))
            _cart.value = current
        }
    }

    fun updateCartItemSellingPriceByIndex(index: Int, newSellingPrice: Double) {
        val current = _cart.value.toMutableList()
        if (index >= 0 && index < current.size) {
            val oldItem = current[index]
            current[index] = oldItem.copy(product = oldItem.product.copy(sellingPrice = newSellingPrice))
            _cart.value = current
        }
    }

    fun updateProductPricesInDb(productId: Long, newPurchasePrice: Double, newSellingPrice: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = repository.getProductDirect(productId)
            if (existing != null) {
                val updated = existing.copy(
                    purchasePrice = newPurchasePrice,
                    sellingPrice = newSellingPrice,
                    isSynced = false
                )
                repository.updateProduct(updated)
                if (isOnlineState.value && isServerReachable.value) {
                    triggerRealSync()
                }
            }
        }
    }

    fun duplicateCartItem(index: Int): Int {
        val current = _cart.value.toMutableList()
        if (index >= 0 && index < current.size) {
            val itemToDuplicate = current[index]
            current.add(itemToDuplicate.copy())
            _cart.value = current
            return current.size - 1
        }
        return index
    }

    // Real Cloud Synchronization (Online/Offline First)
    fun triggerUpload() {
        triggerRealSync()
    }

    fun triggerMirrorSync(onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        val phone = _currentUser.value?.phoneNo ?: sharedPrefs.getString("logged_in_phone", "") ?: ""
        if (phone.isBlank()) {
            val msg = "ကျေးဇူးပြု၍ အကောင့် Login အရင်ဝင်ပါ (No active login)"
            syncStatusMessage.value = msg
            onResult(false, msg)
            return
        }

        if (!com.example.util.SyncManager.isNetworkAvailable(getApplication())) {
            val msg = "လက်ရှိ အင်တာနက်မရှိပါ (Offline Mode)။ အင်တာနက်ရရှိပါက Cloud သို့ အလိုအလျောက် Mirror တင်ပေးပါမည်။"
            syncStatusMessage.value = msg
            onResult(false, msg)
            return
        }

        viewModelScope.launch {
            _isUploading.value = true
            syncStatusMessage.value = "Local ဒေတာအတိုင်း Cloud ပေါ်သို့ ၁၀၀% Mirror တင်နေပါသည်..."
            val result = com.example.util.SyncManager.mirrorLocalToCloud(
                workerUrl = cloudflareWorkerUrl.value,
                userPhone = phone,
                repository = repository
            )
            clearPendingDeletedProducts()
            _isUploading.value = false
            syncStatusMessage.value = result.message
            if (result.success) {
                _isOfflineSaved.value = true
                val now = System.currentTimeMillis()
                lastSyncTime.value = now
                sharedPrefs.edit().putLong("last_sync_time", now).apply()
            }
            onResult(result.success, result.message)
        }
    }

    fun triggerRealSync(onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        val phone = _currentUser.value?.phoneNo ?: sharedPrefs.getString("logged_in_phone", "") ?: ""
        if (phone.isBlank()) {
            val msg = "ကျေးဇူးပြု၍ အကောင့် Login အရင်ဝင်ပါ (No active login)"
            syncStatusMessage.value = msg
            onResult(false, msg)
            return
        }

        if (!com.example.util.SyncManager.isNetworkAvailable(getApplication())) {
            val msg = "လက်ရှိ အင်တာနက်မရှိပါ (Offline Mode)။ အရောင်းဒေတာများကို ဖုန်းထဲတွင် သိမ်းဆည်းထားပြီး အင်တာနက်ရရှိပါက အလိုအလျောက် Cloud သို့ တင်ပေးပါမည်။"
            syncStatusMessage.value = msg
            _isOfflineSaved.value = false
            onResult(false, msg)
            return
        }

        viewModelScope.launch {
            _isUploading.value = true
            syncStatusMessage.value = "Cloud သို့ ဒေတာတင်နေပါသည်..."
            val (delIds, delNames) = getPendingDeletedProducts()
            val delVouchers = getPendingDeletedVouchers()
            val result = com.example.util.SyncManager.syncAllPendingData(
                workerUrl = cloudflareWorkerUrl.value,
                userPhone = phone,
                repository = repository,
                pendingDeletedProductIds = delIds,
                pendingDeletedProductNames = delNames,
                pendingDeletedVouchers = delVouchers
            )
            if (result.success) {
                if (delIds.isNotEmpty() || delNames.isNotEmpty()) {
                    clearPendingDeletedProducts()
                }
                if (delVouchers.isNotEmpty()) {
                    clearPendingDeletedVouchers()
                }
            }
            _isUploading.value = false
            syncStatusMessage.value = result.message
            if (result.success) {
                _isOfflineSaved.value = true
                val now = System.currentTimeMillis()
                lastSyncTime.value = now
                sharedPrefs.edit().putLong("last_sync_time", now).apply()
            }
            onResult(result.success, result.message)
        }
    }

    fun testServerConnection(onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val result = com.example.util.SyncManager.checkServerReachability(cloudflareWorkerUrl.value)
            isServerReachable.value = result.isReachable
            onResult(result.isReachable, result.message)
        }
    }

    fun triggerPullCloudData(onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        val phone = _currentUser.value?.phoneNo ?: sharedPrefs.getString("logged_in_phone", "") ?: ""
        if (phone.isBlank()) {
            val msg = "ကျေးဇူးပြု၍ အကောင့် Login အရင်ဝင်ပါ"
            onResult(false, msg)
            return
        }

        if (!com.example.util.SyncManager.isNetworkAvailable(getApplication())) {
            val msg = "အင်တာနက် မရှိပါ (No internet connection)"
            onResult(false, msg)
            return
        }

        viewModelScope.launch {
            _isUploading.value = true
            cloudDownloadProgress.value = 0.05f
            cloudDownloadProgressMsg.value = "Cloud မှ အကောင့်ဒေတာများ ရယူနေပါသည်..."
            syncStatusMessage.value = "Cloud မှ ဒေတာရယူနေပါသည်..."
            val result = com.example.util.SyncManager.pullCloudData(
                workerUrl = cloudflareWorkerUrl.value,
                userPhone = phone,
                repository = repository,
                onProgress = { progress, msg ->
                    cloudDownloadProgress.value = progress
                    cloudDownloadProgressMsg.value = msg
                    syncStatusMessage.value = msg
                }
            )
            _isUploading.value = false
            syncStatusMessage.value = result.message
            if (result.success) {
                cloudDownloadProgress.value = 1f
                _isOfflineSaved.value = true
                val now = System.currentTimeMillis()
                lastSyncTime.value = now
                sharedPrefs.edit().putLong("last_sync_time", now).apply()
            }
            onResult(result.success, result.message)
        }
    }

    suspend fun pullCloudDataDirect(phoneNo: String? = null): Boolean {
        val phone = phoneNo ?: _currentUser.value?.phoneNo ?: sharedPrefs.getString("logged_in_phone", "") ?: ""
        if (phone.isBlank()) return false
        val result = com.example.util.SyncManager.pullCloudData(
            workerUrl = cloudflareWorkerUrl.value,
            userPhone = phone,
            repository = repository,
            onProgress = { progress, msg ->
                cloudDownloadProgress.value = progress
                cloudDownloadProgressMsg.value = msg
            }
        )
        if (result.success) {
            val now = System.currentTimeMillis()
            lastSyncTime.value = now
            sharedPrefs.edit().putLong("last_sync_time", now).apply()
        }
        return result.success
    }

    suspend fun clearAllBusinessData() {
        withContext(Dispatchers.IO) {
            repository.clearAllBusinessData()
        }
        resetActiveVoucher()
        resetSalesHistoryFilters()
    }

    suspend fun isLocalDataEmpty(): Boolean {
        return withContext(Dispatchers.IO) {
            val prods = repository.getAllProductsDirect()
            val vouchers = repository.getAllCompletedVouchersDirect()
            prods.isEmpty() && vouchers.isEmpty()
        }
    }

    fun logout() {
        _currentUser.value = null
        sharedPrefs.edit().remove("logged_in_phone").remove("data_owner_phone").apply()
        resetActiveVoucher()
        resetSalesHistoryFilters()
    }

    fun logoutWithSync(
        onStatusUpdate: (String) -> Unit = {},
        onComplete: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        val phone = _currentUser.value?.phoneNo ?: sharedPrefs.getString("logged_in_phone", "") ?: ""
        if (phone.isBlank() || !com.example.util.SyncManager.isNetworkAvailable(getApplication())) {
            viewModelScope.launch {
                clearAllBusinessData()
                logout()
                onComplete(true, "Logout ပြုလုပ်ပြီးပါပြီ")
            }
            return
        }

        viewModelScope.launch {
            _isUploading.value = true
            onStatusUpdate("Cloud ပေါ်သို့ ဒေတာများ သိမ်းဆည်းနေပါသည်...")
            val result = com.example.util.SyncManager.mirrorLocalToCloud(
                workerUrl = cloudflareWorkerUrl.value,
                userPhone = phone,
                repository = repository
            )
            _isUploading.value = false
            // Clear local business data so it won't leak to another account
            clearAllBusinessData()
            logout()
            onComplete(
                result.success,
                if (result.success) "Cloud ပေါ်သို့ ဒေတာများ သိမ်းဆည်းပြီး Logout ပြုလုပ်ပြီးပါပြီ" else result.message
            )
        }
    }

    fun addExpenseCategory(name: String, iconName: String) {
        viewModelScope.launch {
            repository.insertExpenseCategory(ExpenseCategory(name = name, iconName = iconName))
        }
    }

    fun deleteExpenseCategory(category: ExpenseCategory) {
        viewModelScope.launch {
            repository.deleteExpenseCategory(category)
        }
    }

    fun saveExpense(expense: Expense, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            if (expense.id == 0L) {
                repository.insertExpense(expense)
            } else {
                repository.updateExpense(expense)
            }
            onComplete()
        }
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch {
            repository.deleteExpense(expense)
        }
    }

    suspend fun getExpenseById(id: Long): Expense? {
        return repository.getExpenseById(id)
    }

    // Helper functions for sales reports
    fun getVoucherItemsFlow(voucherId: String): Flow<List<VoucherItem>> {
        return repository.getVoucherItems(voucherId)
    }
}
