package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_accounts")
data class UserAccount(
    @PrimaryKey val phoneNo: String,
    val username: String,
    val businessName: String,
    val businessType: String,
    val address: String,
    val role: String, // "ADMIN" or "CASHIER"
    val passwordHash: String,
    val deviceId: String = "",
    val status: String = "off"
)

@Entity(tableName = "products")
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val groupName: String,
    val purchasePrice: Double,
    val sellingPrice: Double,
    val unit: String,
    val note: String,
    val trackStock: Boolean,
    val barcode: String,
    val quantity: Int,
    val alertQuantity: Int,
    val imageUri: String = "",
    val isSynced: Boolean = false
)

@Entity(tableName = "product_groups")
data class ProductGroup(
    @PrimaryKey val name: String
)

@Entity(tableName = "product_units")
data class ProductUnit(
    @PrimaryKey val name: String
)

@Entity(tableName = "vouchers")
data class Voucher(
    @PrimaryKey val receiptNo: String,
    val timestamp: Long,
    val cashierName: String,
    val totalAmount: Double,
    val totalItems: Int,
    val customerName: String = "Not Register",
    val paymentMethod: String = "CASH",
    val isCompleted: Boolean = false,
    val isPurchase: Boolean = false,
    val paidAmount: Double = 0.0,
    val changeAmount: Double = 0.0,
    val balanceAmount: Double = 0.0,
    val note: String = "",
    val discount: Double = 0.0,
    val fee: Double = 0.0,
    val isSynced: Boolean = false
)

@Entity(tableName = "voucher_items")
data class VoucherItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val voucherId: String,
    val productId: Long,
    val productName: String,
    val quantity: Int,
    val purchasePrice: Double,
    val sellingPrice: Double
)

@Entity(tableName = "customers")
data class Customer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String,
    val address: String,
    val note: String = "",
    val isSynced: Boolean = false
)

@Entity(tableName = "suppliers")
data class Supplier(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String,
    val address: String,
    val note: String = ""
)

@Entity(tableName = "payments")
data class Payment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val method: String,
    val amount: Double,
    val date: Long
)

@Entity(tableName = "expense_categories")
data class ExpenseCategory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val iconName: String = "ShoppingCart"
)

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryName: String,
    val description: String,
    val amount: Double,
    val paymentMethod: String,
    val note: String = "",
    val timestamp: Long,
    val dateString: String = "",
    val timeString: String = ""
)
