#!/usr/bin/env bash
# ====================================================================
# UN POS - Quick Database Viewer & Status Checker
# Displays current database counts, recent vouchers, and active users
# ====================================================================

DB_NAME="pos_db"

echo "========================================================"
echo "          UN POS - Database Status & Quick View         "
echo "========================================================"

# Check if PostgreSQL is running
if command -v psql >/dev/null 2>&1; then
  echo -e "\n--- [1] ဇယားများအလိုက် ဒေတာ အရေအတွက် (Record Counts) ---"
  sudo -u postgres psql -d "$DB_NAME" -t -A -c "
    SELECT 'အကောင့် (Users):        ' || COUNT(*) FROM users
    UNION ALL
    SELECT 'ကုန်ပစ္စည်း (Products):   ' || COUNT(*) FROM products
    UNION ALL
    SELECT 'ဘောက်ချာ (Vouchers):    ' || COUNT(*) FROM vouchers
    UNION ALL
    SELECT 'ဝယ်ယူသူ (Customers):   ' || COUNT(*) FROM customers
    UNION ALL
    SELECT 'ကုန်ရောင်းသူ (Suppliers): ' || COUNT(*) FROM suppliers
    UNION ALL
    SELECT 'ကုန်ကျစရိတ် (Expenses): ' || COUNT(*) FROM expenses;
  " 2>/dev/null || echo "Database သို့ ချိတ်ဆက်၍ မရသေးပါ။"

  echo -e "\n--- [2] စာရင်းသွင်းထားသော အကောင့်များ (Users) ---"
  sudo -u postgres psql -d "$DB_NAME" -c "
    SELECT id, phone, name, shop_name, status, TO_TIMESTAMP(created_at/1000)::timestamp(0) as registered_at
    FROM users
    ORDER BY id ASC;
  " 2>/dev/null

  echo -e "\n--- [3] နောက်ဆုံး ရောင်းချထားသော ဘောက်ချာ ၅ စောင် (Latest 5 Vouchers) ---"
  sudo -u postgres psql -d "$DB_NAME" -c "
    SELECT receipt_no, customer_name, total_amount, payment_method, is_completed, TO_TIMESTAMP(created_at/1000)::timestamp(0) as date
    FROM vouchers
    ORDER BY created_at DESC
    LIMIT 5;
  " 2>/dev/null

  echo -e "\n--- [4] လက်ကျန်နည်းနေသော ကုန်ပစ္စည်းများ (Low Stock <= 5) ---"
  sudo -u postgres psql -d "$DB_NAME" -c "
    SELECT id, name, category, price, quantity
    FROM products
    WHERE quantity <= 5 AND is_active = TRUE
    ORDER BY quantity ASC
    LIMIT 5;
  " 2>/dev/null

else
  echo "Error: psql command not found. Please ensure PostgreSQL is installed."
fi

echo "========================================================"
echo "Tip: psql ထဲ တိုက်ရိုက်ဝင်၍ query ရိုက်လိုပါက: sudo -u postgres psql -d pos_db"
echo "Tip: Browser မှ ကြည့်လိုပါက: http://YOUR_VPS_IP:8081/admin"
echo "========================================================"
