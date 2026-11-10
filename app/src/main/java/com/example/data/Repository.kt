package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class Repository(private val db: AppDatabase) {
    val allAccounts: Flow<List<UserAccount>> = db.userAccountDao().getAllAccountsFlow()
    val allProducts: Flow<List<Product>> = db.productDao().getAllProductsFlow()
    val allGroups: Flow<List<ProductGroup>> = db.productGroupDao().getAllGroupsFlow()
    val allUnits: Flow<List<ProductUnit>> = db.productUnitDao().getAllUnitsFlow()
    val allVouchers: Flow<List<Voucher>> = db.voucherDao().getAllVouchersFlow()
    val allVoucherItems: Flow<List<VoucherItem>> = db.voucherDao().getAllVoucherItemsFlow()
    val allCustomers: Flow<List<Customer>> = db.customerDao().getAllCustomersFlow()
    val allSuppliers: Flow<List<Supplier>> = db.supplierDao().getAllSuppliersFlow()
    val allPayments: Flow<List<Payment>> = db.paymentDao().getAllPaymentsFlow()
    val allExpenseCategories: Flow<List<ExpenseCategory>> = db.expenseCategoryDao().getAllCategoriesFlow()
    val allExpenses: Flow<List<Expense>> = db.expenseDao().getAllExpensesFlow()

    suspend fun getAccountByPhone(phone: String): UserAccount? {
        return db.userAccountDao().getAccountByPhone(phone)
    }

    suspend fun insertAccount(account: UserAccount) {
        db.userAccountDao().insertAccount(account)
    }

    suspend fun deleteAccount(account: UserAccount) {
        db.userAccountDao().deleteAccount(account)
    }

    suspend fun insertProduct(product: Product): Long {
        return db.productDao().insertProduct(product)
    }

    suspend fun updateProduct(product: Product) {
        db.productDao().updateProduct(product)
    }

    suspend fun deleteProduct(product: Product) {
        db.productDao().deleteProduct(product)
    }

    suspend fun updateStock(productId: Long, quantity: Int) {
        db.productDao().updateStock(productId, quantity)
    }

    suspend fun updateStockLocally(productId: Long, quantity: Int) {
        db.productDao().updateStockLocally(productId, quantity)
    }

    suspend fun updateStockAndAlert(productId: Long, quantity: Int, alertQuantity: Int) {
        db.productDao().updateStockAndAlert(productId, quantity, alertQuantity)
    }

    suspend fun getProductDirect(id: Long): Product? {
        return db.productDao().getProductById(id)
    }

    suspend fun insertGroup(group: ProductGroup) {
        db.productGroupDao().insertGroup(group)
    }

    suspend fun getAllGroupsDirect(): List<ProductGroup> {
        return db.productGroupDao().getAllGroupsDirect()
    }

    suspend fun deleteGroup(group: ProductGroup) {
        db.productGroupDao().deleteGroup(group)
    }

    suspend fun insertUnit(unit: ProductUnit) {
        db.productUnitDao().insertUnit(unit)
    }

    suspend fun getAllUnitsDirect(): List<ProductUnit> {
        return db.productUnitDao().getAllUnitsDirect()
    }

    suspend fun deleteUnit(unit: ProductUnit) {
        db.productUnitDao().deleteUnit(unit)
    }

    suspend fun getAllSuppliersDirect(): List<Supplier> {
        return db.supplierDao().getAllSuppliersDirect()
    }

    suspend fun getAllPaymentsDirect(): List<Payment> {
        return db.paymentDao().getAllPaymentsDirect()
    }

    suspend fun insertVoucher(voucher: Voucher) {
        db.voucherDao().insertVoucher(voucher)
    }

    suspend fun insertVoucherItem(item: VoucherItem) {
        db.voucherDao().insertVoucherItem(item)
    }

    fun getVoucherItems(voucherId: String): Flow<List<VoucherItem>> {
        return db.voucherDao().getVoucherItemsFlow(voucherId)
    }

    suspend fun getVoucherItemsDirect(voucherId: String): List<VoucherItem> {
        return db.voucherDao().getVoucherItemsDirect(voucherId)
    }

    suspend fun deleteVoucherItems(voucherId: String) {
        db.voucherDao().deleteVoucherItems(voucherId)
    }

    suspend fun deleteVoucher(voucherId: String) {
        db.voucherDao().deleteVoucher(voucherId)
    }

    suspend fun getVoucherDirect(voucherId: String): Voucher? {
        return db.voucherDao().getVoucherDirect(voucherId)
    }

    val unsyncedVoucherCount: Flow<Int> = db.voucherDao().getUnsyncedCountFlow()
    val unsyncedVouchers: Flow<List<Voucher>> = db.voucherDao().getUnsyncedCompletedVouchersFlow()
    val unsyncedProductCount: Flow<Int> = db.productDao().getUnsyncedProductCountFlow()
    val unsyncedCustomerCount: Flow<Int> = db.customerDao().getUnsyncedCustomerCountFlow()

    val totalUnsyncedCount: Flow<Int> = combine(
        unsyncedVoucherCount,
        unsyncedProductCount,
        unsyncedCustomerCount
    ) { v, p, c -> v + p + c }

    suspend fun getUnsyncedCompletedVouchers(): List<Voucher> {
        return db.voucherDao().getUnsyncedCompletedVouchers()
    }

    suspend fun markVoucherSynced(receiptNo: String) {
        db.voucherDao().markVoucherSynced(receiptNo)
    }

    suspend fun markVouchersSynced(receiptNos: List<String>) {
        if (receiptNos.isNotEmpty()) {
            db.voucherDao().markVouchersSynced(receiptNos)
        }
    }

    suspend fun getUnsyncedProducts(): List<Product> {
        return db.productDao().getUnsyncedProducts()
    }

    suspend fun markProductsSynced(ids: List<Long>) {
        if (ids.isNotEmpty()) {
            db.productDao().markProductsSynced(ids)
        }
    }

    suspend fun getUnsyncedCustomers(): List<Customer> {
        return db.customerDao().getUnsyncedCustomers()
    }

    suspend fun markCustomersSynced(ids: List<Long>) {
        if (ids.isNotEmpty()) {
            db.customerDao().markCustomersSynced(ids)
        }
    }

    suspend fun insertCustomer(customer: Customer) {
        db.customerDao().insertCustomer(customer)
    }

    suspend fun updateCustomer(customer: Customer) {
        db.customerDao().updateCustomer(customer)
    }

    suspend fun deleteCustomer(customer: Customer) {
        db.customerDao().deleteCustomer(customer)
    }

    suspend fun insertSupplier(supplier: Supplier) {
        db.supplierDao().insertSupplier(supplier)
    }

    suspend fun updateSupplier(supplier: Supplier) {
        db.supplierDao().updateSupplier(supplier)
    }

    suspend fun deleteSupplier(supplier: Supplier) {
        db.supplierDao().deleteSupplier(supplier)
    }

    suspend fun insertPayment(payment: Payment) {
        db.paymentDao().insertPayment(payment)
    }

    suspend fun updatePayment(payment: Payment) {
        db.paymentDao().updatePayment(payment)
    }

    suspend fun deletePayment(payment: Payment) {
        db.paymentDao().deletePayment(payment)
    }

    suspend fun insertExpenseCategory(category: ExpenseCategory): Long {
        return db.expenseCategoryDao().insertCategory(category)
    }

    suspend fun deleteExpenseCategory(category: ExpenseCategory) {
        db.expenseCategoryDao().deleteCategory(category)
    }

    suspend fun getExpenseById(id: Long): Expense? {
        return db.expenseDao().getExpenseById(id)
    }

    suspend fun insertExpense(expense: Expense): Long {
        return db.expenseDao().insertExpense(expense)
    }

    suspend fun updateExpense(expense: Expense) {
        db.expenseDao().updateExpense(expense)
    }

    suspend fun deleteExpense(expense: Expense) {
        db.expenseDao().deleteExpense(expense)
    }

    suspend fun getAllProductsDirect(): List<Product> = db.productDao().getAllProductsDirect()
    suspend fun markAllProductsSynced() = db.productDao().markAllProductsSynced()
    suspend fun getAllVouchersDirect(): List<Voucher> = db.voucherDao().getAllVouchersDirect()
    suspend fun getAllCompletedVouchersDirect(): List<Voucher> = db.voucherDao().getAllCompletedVouchersDirect()
    suspend fun getAllVoucherItemsDirect(): List<VoucherItem> = db.voucherDao().getAllVoucherItemsDirect()
    suspend fun markAllVouchersSynced() = db.voucherDao().markAllVouchersSynced()
    suspend fun getAllCustomersDirect(): List<Customer> = db.customerDao().getAllCustomersDirect()
    suspend fun markAllCustomersSynced() = db.customerDao().markAllCustomersSynced()
    suspend fun getAllExpenseCategoriesDirect(): List<ExpenseCategory> = db.expenseCategoryDao().getAllCategoriesDirect()
    suspend fun getAllExpensesDirect(): List<Expense> = db.expenseDao().getAllExpensesDirect()

    suspend fun clearAllBusinessData() {
        db.productDao().clearAllProducts()
        db.productGroupDao().clearAllGroups()
        db.productUnitDao().clearAllUnits()
        db.voucherDao().clearAllVoucherItems()
        db.voucherDao().clearAllVouchers()
        db.customerDao().clearAllCustomers()
        db.supplierDao().clearAllSuppliers()
        db.paymentDao().clearAllPayments()
        db.expenseCategoryDao().clearAllCategories()
        db.expenseDao().clearAllExpenses()
    }
}
