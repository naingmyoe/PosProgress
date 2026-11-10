package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.fillMaxSize
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.POSViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: POSViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.example.util.BackupAndExcelUtils.requestAllFilesAccessPermission(this)

        setContent {
            val isDark by viewModel.isDarkTheme.collectAsState()
            com.example.ui.screens.globalIsDarkTheme = isDark
            MyApplicationTheme(darkTheme = isDark) {
                androidx.compose.material3.Surface(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    color = if (isDark) androidx.compose.ui.graphics.Color(0xFF1B1A1F) else androidx.compose.ui.graphics.Color.White
                ) {
                    val navController = rememberNavController()
                    NavHost(
                        navController = navController,
                        startDestination = "splash",
                        enterTransition = { fadeIn(animationSpec = tween(150)) },
                        exitTransition = { fadeOut(animationSpec = tween(150)) },
                        popEnterTransition = { fadeIn(animationSpec = tween(150)) },
                        popExitTransition = { fadeOut(animationSpec = tween(150)) }
                    ) {
                    composable("splash") {
                        SplashScreen(navController = navController, viewModel = viewModel)
                    }
                    composable("login") {
                        LoginScreen(navController = navController, viewModel = viewModel)
                    }
                    composable("account_selection") {
                        AccountSelectionScreen(navController = navController, viewModel = viewModel)
                    }
                    composable("register") {
                        RegisterScreen(navController = navController, viewModel = viewModel)
                    }
                    composable("loading") {
                        LoadingScreen(navController = navController, viewModel = viewModel)
                    }
                    composable("waiting_activation") {
                        WaitingActivationScreen(navController = navController, viewModel = viewModel)
                    }
                    composable("subscription_expired") {
                        SubscriptionExpiredScreen(navController = navController, viewModel = viewModel)
                    }
                    composable("dashboard") {
                        DashboardScreen(navController = navController, viewModel = viewModel)
                    }
                    composable("user_profile") {
                        UserProfileScreen(navController = navController, viewModel = viewModel)
                    }
                    composable("accounts_list") {
                        AccountsScreen(navController = navController, viewModel = viewModel)
                    }
                    composable("products_list") {
                        ProductsListScreen(navController = navController, viewModel = viewModel)
                    }
                    composable("add_product") {
                        AddProductScreen(navController = navController, viewModel = viewModel)
                    }
                    composable("groups_list") {
                        GroupsListScreen(navController = navController, viewModel = viewModel)
                    }
                    composable("groups_list_select") {
                        GroupsSelectScreen(navController = navController, viewModel = viewModel)
                    }
                    composable("units_list_select") {
                        UnitsSelectScreen(navController = navController, viewModel = viewModel)
                    }
                    composable("customers_list") {
                        CustomersListScreen(navController = navController, viewModel = viewModel)
                    }
                    composable("add_customer") {
                        AddCustomerScreen(navController = navController, viewModel = viewModel)
                    }
                    composable("suppliers_list") {
                        SuppliersListScreen(navController = navController, viewModel = viewModel)
                    }
                    composable("add_supplier") {
                        AddSupplierScreen(navController = navController, viewModel = viewModel)
                    }
                    composable("payments_list") {
                        PaymentsListScreen(navController = navController, viewModel = viewModel)
                    }
                    composable("new_voucher") {
                        NewVoucherScreen(navController = navController, viewModel = viewModel)
                    }
                    composable("sales_history") {
                        SalesHistoryScreen(navController = navController, viewModel = viewModel)
                    }
                    composable("sales_product_total") {
                        com.example.ui.screens.SalesProductTotalScreen(navController = navController, viewModel = viewModel, isQuantityMode = false)
                    }
                    composable("sales_product_quantity") {
                        com.example.ui.screens.SalesProductTotalScreen(navController = navController, viewModel = viewModel, isQuantityMode = true)
                    }
                    composable("sales_product_detail/{productName}/{startDate}/{endDate}") { backStackEntry ->
                        val productName = backStackEntry.arguments?.getString("productName") ?: ""
                        val startDate = backStackEntry.arguments?.getString("startDate")?.toLongOrNull() ?: 0L
                        val endDate = backStackEntry.arguments?.getString("endDate")?.toLongOrNull() ?: 0L
                        com.example.ui.screens.SalesProductDetailScreen(
                            navController = navController,
                            viewModel = viewModel,
                            productName = productName,
                            startDate = startDate,
                            endDate = endDate
                        )
                    }
                    composable("sales_calendar") {
                        com.example.ui.screens.SalesCalendarScreen(navController = navController, viewModel = viewModel)
                    }
                    composable("sales_month_selector") {
                        com.example.ui.screens.SalesMonthSelectorScreen(navController = navController, viewModel = viewModel)
                    }
                    composable("sales_daily_receipts/{dateMillis}") { backStackEntry ->
                        val dateMillis = backStackEntry.arguments?.getString("dateMillis")?.toLongOrNull() ?: 0L
                        com.example.ui.screens.SalesDailyReceiptsScreen(navController = navController, viewModel = viewModel, dateMillis = dateMillis)
                    }
                    composable("receipt_detail/{receiptNo}") { backStackEntry ->
                        val receiptNo = backStackEntry.arguments?.getString("receiptNo") ?: ""
                        com.example.ui.screens.ReceiptDetailScreen(navController = navController, viewModel = viewModel, receiptNo = receiptNo)
                    }
                    composable("receipt_detail/{receiptNo}/{highlightProduct}") { backStackEntry ->
                        val receiptNo = backStackEntry.arguments?.getString("receiptNo") ?: ""
                        val highlightProduct = backStackEntry.arguments?.getString("highlightProduct") ?: ""
                        com.example.ui.screens.ReceiptDetailScreen(navController = navController, viewModel = viewModel, receiptNo = receiptNo, highlightProduct = highlightProduct)
                    }
                    composable("settings") {
                        SettingsScreen(navController = navController, viewModel = viewModel)
                    }
                    composable("cloud_sync") {
                        CloudSyncScreenContainer(navController = navController, viewModel = viewModel)
                    }
                    composable("customer_sales_total") {
                        com.example.ui.screens.CustomerSalesTotalScreen(navController = navController, viewModel = viewModel)
                    }
                    composable("customer_sales_detail/{customerName}/{startDate}/{endDate}") { backStackEntry ->
                        val customerName = backStackEntry.arguments?.getString("customerName") ?: ""
                        val startDate = backStackEntry.arguments?.getString("startDate")?.toLongOrNull() ?: 0L
                        val endDate = backStackEntry.arguments?.getString("endDate")?.toLongOrNull() ?: 0L
                        com.example.ui.screens.CustomerSalesDetailScreen(
                            navController = navController,
                            viewModel = viewModel,
                            customerName = customerName,
                            startDate = startDate,
                            endDate = endDate
                        )
                    }
                    composable("profit_loss_report") {
                        com.example.ui.screens.ProfitLossReportScreen(navController = navController, viewModel = viewModel)
                    }
                    composable("customer_debt") {
                        com.example.ui.screens.CustomerDebtScreen(navController = navController, viewModel = viewModel)
                    }
                    composable("customer_debt_detail/{customerName}") { backStackEntry ->
                        val customerName = backStackEntry.arguments?.getString("customerName") ?: ""
                        com.example.ui.screens.CustomerDebtDetailScreen(navController = navController, viewModel = viewModel, name = customerName)
                    }
                    composable("supplier_debt") {
                        com.example.ui.screens.SupplierDebtScreen(navController = navController, viewModel = viewModel)
                    }
                    composable("supplier_debt_detail/{supplierName}") { backStackEntry ->
                        val supplierName = backStackEntry.arguments?.getString("supplierName") ?: ""
                        com.example.ui.screens.SupplierDebtDetailScreen(navController = navController, viewModel = viewModel, name = supplierName)
                    }
                    composable("about") {
                        AboutScreen(navController = navController)
                    }
                    composable(
                        route = "add_expense?expenseId={expenseId}",
                        arguments = listOf(
                            navArgument("expenseId") {
                                type = NavType.LongType
                                defaultValue = 0L
                            }
                        )
                    ) { backStackEntry ->
                        val expenseId = backStackEntry.arguments?.getLong("expenseId") ?: 0L
                        com.example.ui.screens.AddExpenseScreen(
                            navController = navController,
                            viewModel = viewModel,
                            expenseId = expenseId
                        )
                    }
                    composable("expense_categories") {
                        com.example.ui.screens.ExpenseCategoriesScreen(navController = navController, viewModel = viewModel)
                    }
                    composable("add_expense_category") {
                        com.example.ui.screens.AddExpenseCategoryScreen(navController = navController, viewModel = viewModel)
                    }
                    composable("expenses_list") {
                        com.example.ui.screens.ExpensesListScreen(navController = navController, viewModel = viewModel)
                    }
                    composable("expense_category_detail/{categoryName}/{startDate}/{endDate}") { backStackEntry ->
                        val categoryName = android.net.Uri.decode(backStackEntry.arguments?.getString("categoryName") ?: "")
                        val startDate = backStackEntry.arguments?.getString("startDate")?.toLongOrNull() ?: 0L
                        val endDate = backStackEntry.arguments?.getString("endDate")?.toLongOrNull() ?: 0L
                        com.example.ui.screens.ExpenseCategoryDetailScreen(
                            navController = navController,
                            viewModel = viewModel,
                            categoryName = categoryName,
                            startDate = startDate,
                            endDate = endDate
                        )
                    }
                }
            }
        }
        }
    }
}
