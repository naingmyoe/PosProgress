/**a
 * PostgreSQL Database Adapter for UN POS Backend Server
 * 100% Stateless & Direct Database Architecture
 * NO In-Memory Cache (Uses minimal RAM even with millions of records)
 */

const fs = require('fs');
const path = require('path');

let Pool = null;
try {
  const pg = require('pg');
  Pool = pg.Pool;
} catch (e) {
  // pg library
}

let pool = null;
let isPostgresConnected = false;

function getConnectionString() {
  return process.env.DATABASE_URL || 
         process.env.POSTGRES_URL ||
         (process.env.PGHOST ? `postgresql://${process.env.PGUSER || 'postgres'}:${process.env.PGPASSWORD || ''}@${process.env.PGHOST}:${process.env.PGPORT || 5432}/${process.env.PGDATABASE || 'pos_db'}` : 'postgresql://pos_user:pos1234@localhost:5432/pos_db');
}

async function initPostgres() {
  const connStr = getConnectionString();

  if (!Pool) {
    console.error('[PostgreSQL Error] "pg" module is not installed. Please run: npm install pg');
    return false;
  }

  try {
    const isSsl = connStr.includes('sslmode=require') || 
                  connStr.includes('supabase.co') || 
                  connStr.includes('neon.tech') || 
                  process.env.PGSSL === 'true';

    pool = new Pool({
      connectionString: connStr,
      ssl: isSsl ? { rejectUnauthorized: false } : false,
      max: 20,
      idleTimeoutMillis: 30000,
      connectionTimeoutMillis: 5000,
    });

    const client = await pool.connect();
    console.log('[PostgreSQL] Connected successfully to PostgreSQL database pool!');

    // Initialize Schema from schema.sql
    const schemaPath = path.join(__dirname, 'schema.sql');
    if (fs.existsSync(schemaPath)) {
      const schemaSql = fs.readFileSync(schemaPath, 'utf8');
      await client.query(schemaSql);
      console.log('[PostgreSQL] Schema verified and initialized successfully from schema.sql');
    }

    client.release();
    isPostgresConnected = true;
    return true;
  } catch (err) {
    console.error(`[PostgreSQL Error] Connection failed: ${err.message}`);
    isPostgresConnected = false;
    return false;
  }
}

// -------------------------------------------------------------
// USER & AUTH QUERIES (Direct PostgreSQL)
// -------------------------------------------------------------

async function getUserByPhone(phone) {
  if (!pool || !phone) return null;
  const res = await pool.query('SELECT * FROM users WHERE phone = $1', [phone]);
  if (res.rows.length === 0) return null;
  const r = res.rows[0];
  return {
    phone: r.phone,
    phoneNo: r.phone,
    name: r.name,
    username: r.name,
    shop_name: r.shop_name,
    businessName: r.shop_name,
    password: r.password,
    role: r.role,
    status: r.status,
    device_id: r.device_id,
    devices: r.devices || [],
    created_at: Number(r.created_at),
    expires_at: r.expires_at ? Number(r.expires_at) : null,
    notes: r.notes
  };
}

async function createUser(u) {
  if (!pool || !u) return null;
  const phone = u.phone || u.phoneNo;
  const now = Date.now();
  const expiresAt = u.expires_at || (now + 7 * 24 * 60 * 60 * 1000);
  await pool.query(`
    INSERT INTO users (phone, name, shop_name, password, role, status, device_id, devices, created_at, expires_at, notes)
    VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11)
    ON CONFLICT (phone) DO UPDATE SET
      name = EXCLUDED.name,
      shop_name = EXCLUDED.shop_name,
      password = EXCLUDED.password,
      role = EXCLUDED.role,
      status = EXCLUDED.status,
      device_id = EXCLUDED.device_id,
      devices = EXCLUDED.devices,
      expires_at = EXCLUDED.expires_at,
      notes = EXCLUDED.notes
  `, [
    phone,
    u.name || u.username || '',
    u.shop_name || u.shopName || u.businessName || '',
    u.password || '',
    u.role || 'user',
    u.status || 'on',
    u.device_id || u.deviceId || '',
    JSON.stringify(u.devices || []),
    u.created_at || now,
    expiresAt,
    u.notes || ''
  ]);
  return getUserByPhone(phone);
}

async function updateUser(phone, fields) {
  if (!pool || !phone || !fields) return;
  const updates = [];
  const values = [phone];
  let idx = 2;

  if (fields.name !== undefined) { updates.push(`name = $${idx++}`); values.push(fields.name); }
  if (fields.shop_name !== undefined) { updates.push(`shop_name = $${idx++}`); values.push(fields.shop_name); }
  if (fields.password !== undefined) { updates.push(`password = $${idx++}`); values.push(fields.password); }
  if (fields.role !== undefined) { updates.push(`role = $${idx++}`); values.push(fields.role); }
  if (fields.status !== undefined) { updates.push(`status = $${idx++}`); values.push(fields.status); }
  if (fields.device_id !== undefined) { updates.push(`device_id = $${idx++}`); values.push(fields.device_id); }
  if (fields.devices !== undefined) { updates.push(`devices = $${idx++}`); values.push(JSON.stringify(fields.devices)); }
  if (fields.expires_at !== undefined) { updates.push(`expires_at = $${idx++}`); values.push(fields.expires_at); }
  if (fields.notes !== undefined) { updates.push(`notes = $${idx++}`); values.push(fields.notes); }

  if (updates.length > 0) {
    await pool.query(`UPDATE users SET ${updates.join(', ')} WHERE phone = $1`, values);
  }
}

async function deleteUser(phone) {
  if (!pool || !phone) return;
  await pool.query('DELETE FROM users WHERE phone = $1', [phone]);
}

async function getAllUsers() {
  if (!pool) return [];
  const res = await pool.query('SELECT phone, name, shop_name, role, status, device_id, devices, created_at, expires_at FROM users ORDER BY created_at DESC');
  return res.rows.map(r => ({
    phoneNo: r.phone,
    username: r.name,
    businessName: r.shop_name,
    role: r.role,
    status: r.status,
    deviceId: r.device_id,
    devices: r.devices || [],
    deviceCount: (r.devices || []).length,
    createdAt: Number(r.created_at),
    expiresAt: r.expires_at ? Number(r.expires_at) : null
  }));
}

// -------------------------------------------------------------
// ADMIN STATS & SETTINGS
// -------------------------------------------------------------

async function getAdminStats() {
  if (!pool) return { totalUsers: 0, activeUsers: 0, expiredUsers: 0, totalSales: 0, totalVouchers: 0, totalProducts: 0 };
  const res = await pool.query(`
    SELECT
      (SELECT COUNT(*) FROM users) as total_users,
      (SELECT COUNT(*) FROM users WHERE status = 'on' OR status = 'active') as active_users,
      (SELECT COUNT(*) FROM users WHERE status = 'expired') as expired_users,
      (SELECT COALESCE(SUM(total_amount), 0) FROM vouchers WHERE is_purchase = FALSE) as total_sales,
      (SELECT COUNT(*) FROM vouchers) as total_vouchers,
      (SELECT COUNT(*) FROM products WHERE is_active = TRUE) as total_products
  `);
  const r = res.rows[0];
  return {
    totalUsers: Number(r.total_users || 0),
    activeUsers: Number(r.active_users || 0),
    expiredUsers: Number(r.expired_users || 0),
    totalSales: Number(r.total_sales || 0),
    totalVouchers: Number(r.total_vouchers || 0),
    totalProducts: Number(r.total_products || 0)
  };
}

async function getAdminPassword() {
  if (!pool) return 'admin1234';
  const res = await pool.query("SELECT setting_value FROM admin_settings WHERE setting_key = 'admin_password'");
  if (res.rows.length > 0) return res.rows[0].setting_value;
  return 'admin1234';
}

async function setAdminPassword(password) {
  if (!pool || !password) return;
  await pool.query(`
    INSERT INTO admin_settings (setting_key, setting_value, updated_at)
    VALUES ('admin_password', $1, $2)
    ON CONFLICT (setting_key) DO UPDATE SET setting_value = EXCLUDED.setting_value, updated_at = EXCLUDED.updated_at
  `, [password, Date.now()]);
}

// -------------------------------------------------------------
// PRODUCTS & INVENTORY QUERIES (Direct PostgreSQL)
// -------------------------------------------------------------

async function syncProducts(userPhone, productsList) {
  if (!pool || !userPhone || !Array.isArray(productsList)) return 0;
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    for (const p of productsList) {
      // 1. Flexible Product ID Matching (Fixes undefined pid issue)
      const pid = (p.id !== undefined && p.id !== null) ? p.id : 
                  ((p.productId !== undefined && p.productId !== null) ? p.productId : p.product_id);
      if (pid === undefined || pid === null) continue;

      // 2. Flexible Quantity & Stock Matching
      const newQty = Number(p.quantity !== undefined ? p.quantity : 
                     (p.stock !== undefined ? p.stock : 
                     (p.qty !== undefined ? p.qty : 0)));

      let initialStock = Number(p.initialStock !== undefined ? p.initialStock : 
                         (p.initial_stock !== undefined ? p.initial_stock : newQty));

      const sellingPrice = Number(p.sellingPrice !== undefined ? p.sellingPrice : (p.price || 0));
      const purchasePrice = Number(p.purchasePrice !== undefined ? p.purchasePrice : (p.cost || 0));
      const alertQty = Number(p.alertQuantity !== undefined ? p.alertQuantity : (p.alert_quantity || 0));
      const trackStock = p.trackStock !== undefined ? Boolean(p.trackStock) : (p.track_stock !== undefined ? Boolean(p.track_stock) : true);
      const note = p.note || '';

      const isManualEdit = p.isManualStockAdjustment === true || p.is_manual_stock_adjustment === true;

      // 3. Manual Stock Adjustment Logic: Baseline stock recalculation
      if (isManualEdit) {
        const deltaRes = await client.query(`
          SELECT 
            COALESCE(SUM(CASE WHEN v.is_purchase = FALSE THEN vi.quantity ELSE 0 END), 0) as sold,
            COALESCE(SUM(CASE WHEN v.is_purchase = TRUE THEN vi.quantity ELSE 0 END), 0) as purchased
          FROM voucher_items vi
          JOIN vouchers v ON vi.receipt_no = v.receipt_no AND vi.user_phone = v.user_phone
          WHERE vi.user_phone = $1 AND vi.product_id = $2 AND v.is_completed = TRUE
        `, [userPhone, pid]);

        const sold = Number(deltaRes.rows[0]?.sold || 0);
        const purchased = Number(deltaRes.rows[0]?.purchased || 0);

        // Adjust baseline so that (baseline + purchased - sold) = newQty
        initialStock = Math.max(0, newQty + sold - purchased);
      }

      await client.query(`
        INSERT INTO products (id, user_phone, name, category, group_name, unit, price, cost, quantity, initial_stock, alert_quantity, track_stock, note, sku, barcode, image_url, is_active, updated_at)
        VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, $16, TRUE, $17)
        ON CONFLICT (user_phone, id) DO UPDATE SET
          name = EXCLUDED.name,
          category = EXCLUDED.category,
          group_name = EXCLUDED.group_name,
          unit = EXCLUDED.unit,
          price = EXCLUDED.price,
          cost = EXCLUDED.cost,
          quantity = EXCLUDED.quantity,
          initial_stock = CASE WHEN $18 THEN EXCLUDED.initial_stock ELSE products.initial_stock END,
          alert_quantity = EXCLUDED.alert_quantity,
          track_stock = EXCLUDED.track_stock,
          note = EXCLUDED.note,
          sku = EXCLUDED.sku,
          barcode = EXCLUDED.barcode,
          image_url = EXCLUDED.image_url,
          is_active = TRUE,
          updated_at = EXCLUDED.updated_at
      `, [
        pid,
        userPhone,
        p.name || '',
        p.category || 'General',
        p.group || p.group_name || p.groupName || '',
        p.unit || 'Pcs',
        sellingPrice,
        purchasePrice,
        newQty,
        initialStock,
        alertQty,
        trackStock,
        note,
        p.sku || '',
        p.barcode || '',
        p.imageUrl || p.image_url || p.imageUri || '',
        p.updatedAt || p.updated_at || Date.now(),
        isManualEdit
      ]);
    }
    await client.query('COMMIT');
    return productsList.length;
  } catch (err) {
    await client.query('ROLLBACK');
    console.error(`[PostgreSQL Error] syncProducts: ${err.message}`);
    throw err;
  } finally {
    client.release();
  }
}

async function deleteProduct(userPhone, productId, productName) {
  if (!pool || !userPhone) return;
  if (productId) {
    await pool.query('UPDATE products SET is_active = FALSE WHERE user_phone = $1 AND id = $2', [userPhone, productId]);
  } else if (productName) {
    await pool.query('UPDATE products SET is_active = FALSE WHERE user_phone = $1 AND LOWER(name) = LOWER($2)', [userPhone, productName]);
  }
}

async function deleteProductsBatch(userPhone, productIds, productNames) {
  if (!pool || !userPhone) return;
  if (Array.isArray(productIds) && productIds.length > 0) {
    await pool.query('UPDATE products SET is_active = FALSE WHERE user_phone = $1 AND id = ANY($2::bigint[])', [userPhone, productIds]);
  }
  if (Array.isArray(productNames) && productNames.length > 0) {
    await pool.query('UPDATE products SET is_active = FALSE WHERE user_phone = $1 AND name = ANY($2::text[])', [userPhone, productNames]);
  }
}

// -------------------------------------------------------------
// VOUCHERS & TRANSACTIONS (Direct PostgreSQL)
// -------------------------------------------------------------

async function syncVoucher(userPhone, v, items) {
  if (!pool || !userPhone || !v) return;
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const receiptNo = v.receiptNo || v.receipt_no;
    await client.query(`
      INSERT INTO vouchers (receipt_no, user_phone, voucher_id, customer_name, customer_phone, total_amount, discount, net_amount, tax, paid_amount, change_amount, payment_method, is_completed, is_purchase, is_credit, notes, created_at, updated_at)
      VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, $16, $17, $18)
      ON CONFLICT (user_phone, receipt_no) DO UPDATE SET
        customer_name = EXCLUDED.customer_name,
        customer_phone = EXCLUDED.customer_phone,
        total_amount = EXCLUDED.total_amount,
        discount = EXCLUDED.discount,
        net_amount = EXCLUDED.net_amount,
        tax = EXCLUDED.tax,
        paid_amount = EXCLUDED.paid_amount,
        change_amount = EXCLUDED.change_amount,
        payment_method = EXCLUDED.payment_method,
        is_completed = EXCLUDED.is_completed,
        is_purchase = EXCLUDED.is_purchase,
        is_credit = EXCLUDED.is_credit,
        notes = EXCLUDED.notes,
        updated_at = EXCLUDED.updated_at
    `, [
      receiptNo,
      userPhone,
      v.voucherId || v.voucher_id || null,
      v.customerName || v.customer_name || 'Walk-in Customer',
      v.customerPhone || v.customer_phone || '',
      Number(v.totalAmount || v.total_amount || 0),
      Number(v.discount || 0),
      Number(v.netAmount || v.net_amount || 0),
      Number(v.tax || 0),
      Number(v.paidAmount || v.paid_amount || 0),
      Number(v.changeAmount || v.change_amount || 0),
      v.paymentMethod || v.payment_method || 'Cash',
      v.isCompleted === true || v.isCompleted === 1,
      v.isPurchase === true || v.isPurchase === 1,
      v.isCredit === true || v.isCredit === 1,
      v.notes || '',
      Number(v.createdAt || v.created_at || Date.now()),
      Number(v.updatedAt || v.updated_at || Date.now())
    ]);

    // Replace items
    await client.query('DELETE FROM voucher_items WHERE user_phone = $1 AND receipt_no = $2', [userPhone, receiptNo]);
    if (Array.isArray(items) && items.length > 0) {
      for (const it of items) {
        await client.query(`
          INSERT INTO voucher_items (user_phone, receipt_no, voucher_id, product_id, product_name, quantity, unit_price, total_price, cost_price, unit)
          VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
        `, [
          userPhone,
          receiptNo,
          v.voucherId || v.voucher_id || null,
          it.productId || it.product_id || null,
          it.productName || it.product_name || '',
          Number(it.quantity || 1),
          Number(it.unitPrice || it.sellingPrice || it.selling_price || it.unit_price || 0),
          Number(it.totalPrice || it.total_price || 0),
          Number(it.costPrice || it.purchasePrice || it.purchase_price || it.cost_price || 0),
          it.unit || 'Pcs'
        ]);
      }
    }

    await client.query('COMMIT');
  } catch (err) {
    await client.query('ROLLBACK');
    console.error(`[PostgreSQL Error] syncVoucher: ${err.message}`);
    throw err;
  } finally {
    client.release();
  }
}

async function syncVouchersBatch(userPhone, vouchersList) {
  if (!pool || !userPhone || !Array.isArray(vouchersList)) return 0;
  for (const entry of vouchersList) {
    const v = entry.voucher || entry;
    const items = entry.items || [];
    await syncVoucher(userPhone, v, items);
  }
  return vouchersList.length;
}

async function deleteVoucher(userPhone, receiptNo) {
  if (!pool || !userPhone || !receiptNo) return;
  await pool.query('DELETE FROM voucher_items WHERE user_phone = $1 AND receipt_no = $2', [userPhone, receiptNo]);
  await pool.query('DELETE FROM vouchers WHERE user_phone = $1 AND receipt_no = $2', [userPhone, receiptNo]);
}

// -------------------------------------------------------------
// AUXILIARY ENTITIES (Customers, Groups, Units, Payments, Suppliers)
// -------------------------------------------------------------

async function syncCustomers(userPhone, customersList) {
  if (!pool || !userPhone || !Array.isArray(customersList)) return;
  for (const c of customersList) {
    if (!c.name) continue;
    await pool.query(`
      INSERT INTO customers (user_phone, name, phone, email, address, total_debt, points, created_at)
      VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
      ON CONFLICT (user_phone, name) DO UPDATE SET
        phone = EXCLUDED.phone,
        email = EXCLUDED.email,
        address = EXCLUDED.address,
        total_debt = EXCLUDED.total_debt,
        points = EXCLUDED.points
    `, [
      userPhone,
      c.name,
      c.phone || '',
      c.email || '',
      c.address || '',
      Number(c.totalDebt || 0),
      Number(c.points || 0),
      Date.now()
    ]);
  }
}

async function syncGroups(userPhone, groupsList) {
  if (!pool || !userPhone || !Array.isArray(groupsList)) return;
  for (const g of groupsList) {
    if (!g || !g.trim()) continue;
    await pool.query(`
      INSERT INTO product_groups (user_phone, name)
      VALUES ($1, $2)
      ON CONFLICT (user_phone, name) DO NOTHING
    `, [userPhone, g.trim()]);
  }
}

async function syncUnits(userPhone, unitsList) {
  if (!pool || !userPhone || !Array.isArray(unitsList)) return;
  for (const u of unitsList) {
    if (!u || !u.trim()) continue;
    await pool.query(`
      INSERT INTO product_units (user_phone, name)
      VALUES ($1, $2)
      ON CONFLICT (user_phone, name) DO NOTHING
    `, [userPhone, u.trim()]);
  }
}

async function syncPayments(userPhone, paymentsList) {
  if (!pool || !userPhone || !Array.isArray(paymentsList)) return;
  for (const p of paymentsList) {
    await pool.query(`
      INSERT INTO payments (user_phone, voucher_id, customer_name, amount, payment_method, payment_type, date, notes)
      VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
    `, [
      userPhone,
      p.voucherId || '',
      p.customerName || '',
      Number(p.amount || 0),
      p.paymentMethod || 'Cash',
      p.paymentType || 'Sale',
      Number(p.date || Date.now()),
      p.notes || ''
    ]);
  }
}

async function syncSuppliers(userPhone, suppliersList) {
  if (!pool || !userPhone || !Array.isArray(suppliersList)) return;
  for (const s of suppliersList) {
    if (!s.name) continue;
    await pool.query(`
      INSERT INTO suppliers (user_phone, name, phone, email, address, total_payable, created_at)
      VALUES ($1, $2, $3, $4, $5, $6, $7)
      ON CONFLICT (user_phone, name) DO UPDATE SET
        phone = EXCLUDED.phone,
        email = EXCLUDED.email,
        address = EXCLUDED.address,
        total_payable = EXCLUDED.total_payable
    `, [
      userPhone,
      s.name,
      s.phone || '',
      s.email || '',
      s.address || '',
      Number(s.totalPayable || 0),
      Date.now()
    ]);
  }
}

// -------------------------------------------------------------
// PULL & RESTORE ALL DATA (Direct SQL streaming for tenant)
// -------------------------------------------------------------

async function pullUserData(userPhone) {
  if (!pool || !userPhone) {
    return { vouchers: [], voucherItems: [], products: [], customers: [], groups: [], units: [], payments: [], suppliers: [], expenseCategories: [], expenses: [] };
  }

  const [prodRes, vouchRes, itemRes, custRes, groupRes, unitRes, payRes, suppRes] = await Promise.all([
    pool.query('SELECT * FROM products WHERE user_phone = $1 AND is_active = TRUE ORDER BY name ASC', [userPhone]),
    pool.query('SELECT * FROM vouchers WHERE user_phone = $1 ORDER BY created_at DESC', [userPhone]),
    pool.query('SELECT * FROM voucher_items WHERE user_phone = $1', [userPhone]),
    pool.query('SELECT * FROM customers WHERE user_phone = $1 ORDER BY name ASC', [userPhone]),
    pool.query('SELECT name FROM product_groups WHERE user_phone = $1', [userPhone]),
    pool.query('SELECT name FROM product_units WHERE user_phone = $1', [userPhone]),
    pool.query('SELECT * FROM payments WHERE user_phone = $1 ORDER BY date DESC', [userPhone]),
    pool.query('SELECT * FROM suppliers WHERE user_phone = $1 ORDER BY name ASC', [userPhone])
  ]);

  const products = prodRes.rows.map(r => ({
    id: Number(r.id),
    productId: Number(r.id),
    name: r.name,
    category: r.category,
    group: r.group_name,
    group_name: r.group_name,
    groupName: r.group_name,
    unit: r.unit,
    price: Number(r.price),
    sellingPrice: Number(r.price),
    cost: Number(r.cost),
    purchasePrice: Number(r.cost),
    quantity: Number(r.quantity),
    stock: Number(r.quantity),
    initialStock: Number(r.initial_stock),
    alertQuantity: Number(r.alert_quantity || 0),
    trackStock: r.track_stock !== false,
    note: r.note || '',
    sku: r.sku,
    barcode: r.barcode,
    imageUrl: r.image_url,
    imageUri: r.image_url,
    updatedAt: Number(r.updated_at)
  }));

  const vouchers = vouchRes.rows.map(r => ({
    receiptNo: r.receipt_no,
    voucherId: r.voucher_id ? Number(r.voucher_id) : null,
    customerName: r.customer_name,
    customerPhone: r.customer_phone,
    totalAmount: Number(r.total_amount),
    discount: Number(r.discount),
    netAmount: Number(r.net_amount),
    tax: Number(r.tax),
    paidAmount: Number(r.paid_amount),
    changeAmount: Number(r.change_amount),
    paymentMethod: r.payment_method,
    isCompleted: r.is_completed,
    isPurchase: r.is_purchase,
    isCredit: r.is_credit,
    notes: r.notes,
    createdAt: Number(r.created_at),
    updatedAt: Number(r.updated_at)
  }));

  const voucherItems = itemRes.rows.map(r => ({
    voucherId: r.receipt_no,
    voucher_id: r.receipt_no,
    productId: r.product_id ? Number(r.product_id) : null,
    productName: r.product_name,
    quantity: Number(r.quantity),
    sellingPrice: Number(r.unit_price),
    purchasePrice: Number(r.cost_price),
    totalPrice: Number(r.total_price),
    unit: r.unit
  }));

  const customers = custRes.rows.map(r => ({
    id: r.id,
    name: r.name,
    phone: r.phone,
    email: r.email,
    address: r.address,
    totalDebt: Number(r.total_debt),
    points: r.points
  }));

  const groups = groupRes.rows.map(r => r.name);
  const units = unitRes.rows.map(r => r.name);

  const payments = payRes.rows.map(r => ({
    voucherId: r.voucher_id,
    customerName: r.customer_name,
    amount: Number(r.amount),
    paymentMethod: r.payment_method,
    paymentType: r.payment_type,
    date: Number(r.date),
    notes: r.notes
  }));

  const suppliers = suppRes.rows.map(r => ({
    name: r.name,
    phone: r.phone,
    email: r.email,
    address: r.address,
    totalPayable: Number(r.total_payable)
  }));

  return {
    products,
    vouchers,
    voucherItems,
    customers,
    groups,
    units,
    payments,
    suppliers,
    expenseCategories: [],
    expenses: []
  };
}

// -------------------------------------------------------------
// SYNC STATUS (Direct SQL count aggregation)
// -------------------------------------------------------------

async function getSyncStatus(userPhone) {
  if (!pool || !userPhone) {
    return { vouchersCount: 0, salesCount: 0, purchasesCount: 0, debtsCount: 0, productsCount: 0, customersCount: 0, suppliersCount: 0, expensesCount: 0 };
  }

  const res = await pool.query(`
    SELECT
      (SELECT COUNT(*) FROM vouchers WHERE user_phone = $1) as vouchers_count,
      (SELECT COUNT(*) FROM vouchers WHERE user_phone = $1 AND is_purchase = FALSE) as sales_count,
      (SELECT COUNT(*) FROM vouchers WHERE user_phone = $1 AND is_purchase = TRUE) as purchases_count,
      (SELECT COUNT(*) FROM vouchers WHERE user_phone = $1 AND is_credit = TRUE) as debts_count,
      (SELECT COUNT(*) FROM products WHERE user_phone = $1 AND is_active = TRUE) as products_count,
      (SELECT COUNT(*) FROM customers WHERE user_phone = $1) as customers_count,
      (SELECT COUNT(*) FROM suppliers WHERE user_phone = $1) as suppliers_count,
      (SELECT COUNT(*) FROM expenses WHERE user_phone = $1) as expenses_count
  `, [userPhone]);

  const r = res.rows[0];
  return {
    vouchersCount: Number(r.vouchers_count || 0),
    salesCount: Number(r.sales_count || 0),
    purchasesCount: Number(r.purchases_count || 0),
    debtsCount: Number(r.debts_count || 0),
    productsCount: Number(r.products_count || 0),
    customersCount: Number(r.customers_count || 0),
    suppliersCount: Number(r.suppliers_count || 0),
    expensesCount: Number(r.expenses_count || 0)
  };
}

module.exports = {
  initPostgres,
  isPostgresActive: () => isPostgresConnected,
  getPool: () => pool,
  getUserByPhone,
  createUser,
  updateUser,
  deleteUser,
  getAllUsers,
  getAdminStats,
  getAdminPassword,
  setAdminPassword,
  syncProducts,
  deleteProduct,
  deleteProductsBatch,
  syncVoucher,
  syncVouchersBatch,
  deleteVoucher,
  syncCustomers,
  syncGroups,
  syncUnits,
  syncPayments,
  syncSuppliers,
  pullUserData,
  getSyncStatus
};
