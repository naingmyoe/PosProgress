package com.example.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.MutableStateFlow

data class Translation(
    val my: String,
    val en: String,
    val zh: String
)

object AppLocalization {
    val currentLanguageFlow = MutableStateFlow("my")
    var currentLang: String
        get() = currentLanguageFlow.value
        set(value) {
            currentLanguageFlow.value = value
        }

    private val translationList = listOf(
        // Languages
        Translation("မြန်မာ (Unicode)", "Myanmar (Unicode)", "缅甸语 (Unicode)"),
        Translation("English", "English", "英语"),
        Translation("中文", "Chinese", "中文"),
        Translation("中文 (Chinese)", "Chinese (中文)", "中文"),

        // Navigation Drawer & Common Actions
        Translation("ဘာသာစကား", "Language", "语言"),
        Translation("ဘာသာစကား (Language)", "Language", "语言 (Language)"),
        Translation("Language (ဘာသာစကား)", "Language", "语言 (Language)"),
        Translation("ပင်မစာမျက်နှာ", "Home", "主页"),
        Translation("အကောင့်များ", "Accounts", "账户"),
        Translation("ဆက်တင်များ", "Settings", "设置"),
        Translation("ဆက်တင်", "Setting", "设置"),
        Translation("အကြောင်း", "About", "关于"),
        Translation("ကိုယ်ရေးအချက်အလက်", "Profile", "个人资料"),
        Translation("ဒေတာချိန်ညှိရန်", "Sync", "同步"),
        Translation("တင်ပို့နေသည်...", "Uploading...", "上传中..."),
        Translation("သိမ်းမည်", "Save", "保存"),
        Translation("ပယ်ဖျက်", "Cancel", "取消"),
        Translation("ဖျက်မည်", "Delete", "删除"),
        Translation("ပြင်ဆင်မည်", "Edit", "编辑"),
        Translation("ပြင်ရန်", "Edit", "编辑"),
        Translation("ပြီးပါပြီ", "Done", "完成"),
        Translation("ပြီးပါပြီ", "FINISH", "完成"),
        Translation("ပြီးပါပြီ", "Finished", "完成"),
        Translation("အောင်မြင်စွာ သိမ်းဆည်းပြီးပါပြီ", "Successfully Finished", "已成功保存"),
        Translation("ရှာဖွေရန်", "Search", "搜索"),
        Translation("အားလုံး", "All", "全部"),
        Translation("ရက်စွဲ", "Date", "日期"),
        Translation("အချိန်", "Time", "时间"),
        Translation("မှတ်ချက်", "Note", "备注"),
        Translation("မှတ်ချက်ထည့်ရန်", "Add Note", "添加备注"),
        Translation("သတိပေးချက်", "Warning", "警告"),
        Translation("အောင်မြင်ပါသည်", "Success", "成功"),
        Translation("အတည်ပြုသည်", "Confirm", "确认"),
        Translation("နောက်သို့", "Back", "返回"),
        Translation("စကားဝှက် ပြောင်းလဲရန်", "Change Password", "修改密码"),
        Translation("ထွက်မည်", "Logout", "退出登录"),
        Translation("အသစ်ထည့်မည်", "Add New", "新增"),
        Translation("ထည့်မည်", "Add", "添加"),
        Translation("ပိတ်မည်", "Close", "关闭"),
        Translation("ရွေးချယ်ပါ", "Select", "选择"),
        Translation("ရှင်းလင်းရန်", "Clear", "清空"),
        Translation("အိုကေ", "OK", "确定"),
        Translation("ဟုတ်ကဲ့", "Yes", "是"),
        Translation("မဟုတ်ပါ", "No", "否"),

        // Dashboard Sections & Titles
        Translation("အခြေခံအချက်အလက်", "Basic Information", "基础信息"),
        Translation("ကုန်ပစ္စည်းများ", "Products", "商品管理"),
        Translation("ကုန်ပစ္စည်း အမျိုးအစား", "Product Categories", "商品分类"),
        Translation("ယူနစ်များ", "Units", "单位管理"),
        Translation("ငွေပေးချေမှုများ", "Payment Methods", "支付方式"),
        Translation("ဈေးဝယ်သူများ", "Customers", "客户管理"),
        Translation("ကုန်သွင်းသူများ", "Suppliers", "供应商管理"),
        Translation("အရောင်း", "Sales", "销售"),
        Translation("အရောင်းစာရင်း", "Sales History", "销售记录"),
        Translation("အရောင်းပြက္ခဒိန်", "Sales Calendar", "销售日历"),
        Translation("အဝယ်ကဏ္ဍ", "Purchases", "采购"),
        Translation("အဝယ်", "Purchase", "采购"),
        Translation("အဝယ်ဖွင့်မည်", "New Purchase", "新增采购"),
        Translation("အဝယ်စာရင်း", "Purchase History", "采购记录"),
        Translation("လအလိုက်အဝယ်", "Monthly Purchases", "月度采购"),
        Translation("လအလိုက် အဝယ်", "Monthly Purchases", "月度采购"),
        Translation("လအလိုက် အဝယ်စာရင်း", "Monthly Purchases", "月度采购"),
        Translation("အကြွေးစာရင်း", "Debt Records", "欠款记录"),
        Translation("စျေးဝယ်သူ အကြွေးစာရင်း", "Customer Debt", "客户欠款"),
        Translation("ကုန်သွင်းသူ အကြွေးစာရင်း", "Supplier Debt", "供应商欠款"),
        Translation("ကုန်ကျစရိတ်များ", "Expenses", "费用开支"),
        Translation("ကုန်ကျစရိတ် အသစ်", "New Expense", "新增开支"),
        Translation("ကုန်ကျစရိတ် များ", "Expenses List", "开支列表"),
        Translation("ကုန်ကျစရိတ် ခေါင်းစဉ်", "Expense Categories", "开支分类"),
        Translation("လက်ကျန်စစ်ဆေးရန်များ", "Stock Checks", "库存盘点"),
        Translation("ကုန်ပစ္စည်း လက်ကျန်", "Stock Inventory", "商品库存"),
        Translation("ကုန်ပစ္စည်း လက်ကျန် အဝယ်", "Stock Purchase Value", "库存成本总额"),
        Translation("ကုန်ပစ္စည်း လက်ကျန် ရောင်း", "Stock Selling Value", "库存销售总额"),
        Translation("အစီရင်ခံစာများ", "Reports", "报表统计"),
        Translation("နေ့စဉ်အရောင်း စာရင်း", "Daily Sales", "每日销售"),
        Translation("လအလိုက်အရောင်း", "Monthly Sales", "月度销售"),
        Translation("လအလိုက် အရောင်း", "Monthly Sales", "月度销售"),
        Translation("အမြတ်/အရှုံး စာရင်း", "Profit & Loss", "利润/亏损表"),
        Translation("အရောင်း ကုန်ပစ္စည်း စုစုပေါင်း", "Sales by Product Total", "商品销售额汇总"),
        Translation("အရောင်း ကုန်ပစ္စည်း အရေအတွက်", "Sales by Product Quantity", "商品销售量汇总"),
        Translation("စျေးဝယ်သူ အရောင်း စုစုပေါင်း", "Sales by Customer", "客户销售汇总"),
        Translation("ကုန်သွင်းသူ အဝယ် စုစုပေါင်း", "Purchases by Supplier", "供应商采购汇总"),
        Translation("အရောင်းဘောက်ချာများ", "Sale Receipt", "销售账单"),
        Translation("အရောင်းပြေစာ", "Sale Receipt", "销售凭条"),
        Translation("အဝယ်ဘောက်ချာများ", "Purchase Receipt", "采购账单"),
        Translation("လအလိုက် အရောင်းပြေစာများ", "Monthly Receipts", "月度销售账单"),
        Translation("ပြေစာ", "Receipt", "账单"),
        Translation("အမြတ်စာရင်း", "Profit Statement", "利润表"),
        Translation("အရောင်းအနှစ်ချုပ်", "Sales Summary", "销售汇总"),
        Translation("ကုန်ပစ္စည်းအလိုက်အရောင်း (အရေအတွက်)", "Sales by Product (Qty)", "商品销售汇总 (数量)"),
        Translation("Customer အလိုက် စုစုပေါင်း ဝယ်ယူမှု", "Total Purchases by Customer", "按客户采购总额"),
        Translation("Customer အလိုက်စုစုပေါင်းဝယ်ယူမှု", "Total Purchases by Customer", "按客户采购总额"),

        // POS & Voucher Detail
        Translation("ဘောက်ချာဖွင့်မည်", "New Voucher", "开具账单"),
        Translation("ဘောက်ချာ", "Voucher", "账单"),
        Translation("ဘောက်ချာအသစ်", "New Voucher", "新账单"),
        Translation("ဘောက်ချာပြင်ဆင်ခြင်း", "Edit Voucher", "修改账单"),
        Translation("ဘောက်ချာအမှတ်", "Receipt No", "账单编号"),
        Translation("ဘောက်ချာအမှတ်:", "Receipt No:", "账单编号:"),
        Translation("ဘားကုဒ် ရိုက်ထည့်ပါ / ဖတ်ပါ", "Enter/Scan Barcode", "输入/扫描条形码"),
        Translation("ဘားကုဒ် ရှာဖွေရန် / ဖတ်ရန်", "Search / Scan Barcode", "搜索/扫描条形码"),
        Translation("ဘားကုဒ် ဖတ်ရန်", "Scan Barcode", "扫描条形码"),
        Translation("အမည်", "Name", "名称"),
        Translation("စျေးနှုန်း", "Price", "单价"),
        Translation("သင့်ငွေ", "Amount", "金额"),
        Translation("အရေအတွက်", "Quantity", "数量"),
        Translation("စုစုပေါင်း", "Total", "总计"),
        Translation("စုစုပေါင်း:", "TOTAL:", "总计:"),
        Translation("စုစုပေါင်းကျသင့်ငွေ", "Total Due", "应付总额"),
        Translation("စုစုပေါင်း ကုန်ကျငွေ", "Total Cost", "总花费"),
        Translation("ပေးပြီးငွေ", "Paid Amount", "已付金额"),
        Translation("ပေးချေထားသောငွေ", "Previously Paid", "已付金额"),
        Translation("ပေးငွေ", "Paid", "实收金额"),
        Translation("ကျန်ငွေ", "Remaining Balance", "未付余款"),
        Translation("အမ်းငွေ", "Change", "找零"),
        Translation("ပို၍", "More", "更多"),
        Translation("ရှင်းမည်", "Checkout", "结账"),
        Translation("အကြွေးထားမည်", "Pay Later", "挂账稍后付"),
        Translation("အကြွေးထားမည်", "Pay and Later", "挂账稍后付"),
        Translation("Discount (လျော့စျေး)", "Discount", "折扣 (Discount)"),
        Translation("Fee (ဝန်ဆောင်ခ)", "Fee", "服务费 (Fee)"),
        Translation("လျှော့စျေး", "Discount", "折扣"),
        Translation("ဝန်ဆောင်ခ", "Fee", "服务费"),
        Translation("ငွေပေးချေမှု", "Payment Method", "支付方式"),
        Translation("ငွေသား", "CASH", "现金"),
        Translation("ငွေသား", "Cash", "现金"),
        Translation("အကြွေး", "CREDIT", "挂账"),
        Translation("အကြွေး", "Credit", "挂账"),
        Translation("စာရင်းမသွင်းရသေး", "Not Register", "未登记"),
        Translation("စာရင်းမသွင်းရသေး", "Not Registered", "未登记"),
        Translation("ငွေဆပ်ရန်", "Pay Debt", "还款"),
        Translation("ဘောက်ချာ ပရင့်ထုတ်မည်", "Print Receipt", "打印账单"),
        Translation("ဘောက်ချာ ပေးပို့မည်", "Send Receipt", "发送账单"),
        Translation("ပစ္စည်းအသစ်အဖြစ် ခွဲထုတ်မည်", "As Different Item", "作为独立商品"),
        Translation("ကုန်စည်များ", "Products", "商品"),
        Translation("ကုန်စည်အမည်", "Product Name", "商品名称"),
        Translation("ဝယ်ဈေး", "Purchase Price", "采购单价"),
        Translation("ရောင်းဈေး", "Selling Price", "销售单价"),
        Translation("ပစ္စည်းလက်ကျန်မရှိပါ", "Out of Stock", "缺货"),
        Translation("နားလည်ပါပြီ", "Got it", "知道了"),

        // Settings Screen & Subscreens
        Translation("ပရင်တာ ဆက်တင်များ", "Print Settings", "打印设置"),
        Translation("ငွေကြေး အမျိုးအစား", "Currency", "货币符号"),
        Translation("ဒေတာဘေ့စ်", "Database", "数据库"),
        Translation("ပရင်တာ ထည့်ရန်", "Add Printer", "添加打印机"),
        Translation("ဘောက်ချာ ပရင်တာ ထည့်ရန်", "Add Receipt Printer", "添加收据打印机"),
        Translation("မီးဖိုချောင် ပရင်တာ ထည့်ရန်", "Add Kitchen Printer", "添加厨房打印机"),
        Translation("ပရင်တာ ရွေးချယ်ပါ", "Select Printer", "选择打印机"),
        Translation("ပုံစံ", "Theme", "主题风格"),
        Translation("စားပွဲခုံ စီမံခန့်ခွဲမှု", "Table Mode", "桌台模式"),
        Translation("စားပွဲခုံ စီမံခန့်ခွဲမှု ဖွင့်ရန်", "Enable Table Management", "启用桌台管理"),
        Translation("Excel သို့ ထုတ်ယူရန်", "Excel Export", "导出至Excel"),
        Translation("Excel မှ ထည့်သွင်းရန်", "Excel Import", "从Excel导入"),
        Translation("ပရင်တာ မုဒ်", "Print Mode", "打印模式"),
        Translation("ဘောက်ချာ ပုံစံ ရွေးချယ်ပါ", "Choose Template", "选择收据模板"),
        Translation("ခေါင်းစီး ဒီဇိုင်း", "Header Design", "页眉设计"),
        Translation("အောက်ခြေ ဒီဇိုင်း", "Footer Design", "页脚设计"),
        Translation("ခေါင်းစီး", "Header", "页眉"),
        Translation("အောက်ခြေ", "Footer", "页脚"),
        Translation("စာသား အညွှန်း", "Label Text", "标签文本"),
        Translation("စာလုံး အရွယ်အစား", "Font Size", "字体大小"),
        Translation("ဖောင့် ရွေးချယ်ပါ", "Select Font", "选择字体"),
        Translation("စက္ကူ ခြားနားမှု", "Paper Spacing", "纸张间距"),
        Translation("စက္ကူ အကျယ်အဝန်း", "Paper Width", "纸张宽度"),
        Translation("အောက်ခြေ စက္ကူထုတ်ခြင်း", "Bottom Feed", "底部走纸"),
        Translation("ဘောက်ချာ ပရင့်ထုတ်ခြင်း", "Print Receipt", "收据打印"),
        Translation("စာကြောင်း ခြားနားမှု", "Line Spacing", "行距设置"),
        Translation("ပရင့် အလင်းအမှောင်", "Print Brightness", "打印明暗度"),
        Translation("ပရင့် အလင်းအမှောင် (Print Mode 1)", "Print Brightness (Print Mode 1)", "打印明暗度 (模式1)"),
        Translation("နေ့စဉ် အလိုအလျောက် သိမ်းဆည်းရန် (UNMobilePos)", "Auto Daily Backup (UNMobilePos)", "每日自动备份 (UNMobilePos)"),
        Translation("ဒေတာဘေ့စ် သိမ်းဆည်းရန် (.db)", "Database Backup (.db)", "备份数据库 (.db)"),
        Translation("ဒေတာဘေ့စ် ပြန်လည်ရယူရန် (.db)", "Database Restore (.db)", "恢复数据库 (.db)"),
        Translation("Excel ဖိုဒါသို့ အချက်အလက်အားလုံး ထုတ်ယူရန်", "Export All to UNMobilePos/Excel Folder", "导出全部数据至Excel文件夹"),
        Translation("ကုန်ပစ္စည်းများကို Excel သို့ ထုတ်ယူရန် (CSV)", "Export Products to Excel (CSV)", "导出商品至Excel (CSV)"),
        Translation("ဝယ်ယူသူများကို Excel သို့ ထုတ်ယူရန် (CSV)", "Export Customers to Excel (CSV)", "导出客户至Excel (CSV)"),
        Translation("ကုန်သွင်းသူများကို Excel သို့ ထုတ်ယူရန် (CSV)", "Export Suppliers to Excel (CSV)", "导出供应商至Excel (CSV)"),
        Translation("အရောင်းဘောက်ချာများကို Excel သို့ ထုတ်ယူရန် (CSV)", "Export Sales Vouchers to Excel (CSV)", "导出销售账单至Excel (CSV)"),
        Translation("ကုန်ကျစရိတ်များကို Excel သို့ ထုတ်ယူရန် (CSV)", "Export Expenses to Excel (CSV)", "导出费用至Excel (CSV)"),
        Translation("ကုန်ပစ္စည်းများကို Excel မှ ထည့်သွင်းရန် (CSV)", "Import Products from Excel (CSV)", "从Excel导入商品 (CSV)"),
        Translation("ဝယ်ယူသူများကို Excel မှ ထည့်သွင်းရန် (CSV)", "Import Customers from Excel (CSV)", "从Excel导入客户 (CSV)"),
        Translation("ကုန်သွင်းသူများကို Excel မှ ထည့်သွင်းရန် (CSV)", "Import Suppliers from Excel (CSV)", "从Excel导入供应商 (CSV)"),

        // Form Fields & Labels
        Translation("ကုန်ပစ္စည်း အသစ်ထည့်ရန်", "Add New Product", "添加新商品"),
        Translation("ကုန်ပစ္စည်း ပြင်ဆင်ရန်", "Edit Product", "编辑商品"),
        Translation("ကုန်ပစ္စည်းအမည်", "Product Name", "商品名称"),
        Translation("အဝယ်စျေး", "Purchase Price", "采购单价"),
        Translation("အရောင်းစျေး", "Selling Price", "销售单价"),
        Translation("လက်ကျန် အရေအတွက်", "Stock Quantity", "库存数量"),
        Translation("အမျိုးအစား", "Category", "商品分类"),
        Translation("ယူနစ်", "Unit", "单位"),
        Translation("ဘားကုဒ်", "Barcode", "条形码"),
        Translation("စျေးဝယ်သူ", "Customer", "客户"),
        Translation("ဈေးဝယ်သူအသစ်", "New Customer", "新增客户"),
        Translation("ဝယ်ယူသူ အသစ်ထည့်ရန်", "Add New Customer", "添加新客户"),
        Translation("ဝယ်ယူသူ အမည်", "Customer Name", "客户姓名"),
        Translation("ဖုန်းနံပါတ်", "Phone Number", "电话号码"),
        Translation("ဖုန်းနံပါတ်", "Phone", "电话"),
        Translation("လိပ်စာ", "Address", "地址"),
        Translation("ကုန်သွင်းသူ", "Supplier", "供应商"),
        Translation("ကုန်သွင်းသူအသစ်", "New Supplier", "新增供应商"),
        Translation("ကုန်သွင်းသူ အသစ်ထည့်ရန်", "Add New Supplier", "添加新供应商"),
        Translation("ကုန်သွင်းသူ အမည်", "Supplier Name", "供应商名称"),
        Translation("ငွေပေးချေမှုပုံစံ အသစ်", "New Payment Method", "新增支付方式"),
        Translation("အမျိုးအစား အသစ်", "New Category", "新增分类"),
        Translation("ယူနစ် အသစ်", "New Unit", "新增单位"),
        Translation("ဆိုင်အမည်", "Store Name", "店铺名称"),
        Translation("ဘောက်ချာတွင် Logo ထည့်မည်", "Print Receipt Logo", "打印收据LOGO"),
        Translation("အောက်ခြေစာတို", "Footer Message", "页脚祝福语"),
        Translation("ဆက်သွယ်ရန်", "Contact Us", "联系我们")
    )

    private val lookupMap = mutableMapOf<String, Translation>().apply {
        for (item in translationList) {
            put(item.my.trim().lowercase(), item)
            put(item.en.trim().lowercase(), item)
            put(item.zh.trim().lowercase(), item)
        }
    }

    fun translate(text: String, lang: String = currentLang): String {
        if (text.isBlank()) return text
        val trimmed = text.trim()
        val cleanKey = trimmed.lowercase()

        val directMatch = lookupMap[cleanKey]
        if (directMatch != null) {
            return when (lang) {
                "en" -> directMatch.en
                "zh" -> directMatch.zh
                else -> directMatch.my
            }
        }

        // Check if string ends with a colon (e.g., "TOTAL:", "Receipt No:", "ပေးငွေ :")
        if (cleanKey.endsWith(":")) {
            val baseKey = cleanKey.dropLast(1).trim()
            val baseMatch = lookupMap[baseKey]
            if (baseMatch != null) {
                val translated = when (lang) {
                    "en" -> baseMatch.en
                    "zh" -> baseMatch.zh
                    else -> baseMatch.my
                }
                return "$translated: "
            }
        }

        return text
    }
}

@Composable
fun tr(key: String): String {
    val lang by AppLocalization.currentLanguageFlow.collectAsState()
    return AppLocalization.translate(key, lang)
}
