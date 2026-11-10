/**
 * PostgreSQL Database Adapter for UN POS Backend Server
 * Supports connection pooling, automatic schema migration,
 * and seamless fallback if PostgreSQL is not configured.
 */

const fs = require('fs');
const path = require('path');

let Pool = null;
try {
  const pg = require('pg');
  Pool = pg.Pool;
} catch (e) {
  // pg library not yet installed via npm
}

let pool = null;
let isPostgresConnected = false;

function getConnectionString() {
  return process.env.DATABASE_URL || 
         process.env.POSTGRES_URL ||
         (process.env.PGHOST ? `postgresql://${process.env.PGUSER || 'postgres'}:${process.env.PGPASSWORD || ''}@${process.env.PGHOST}:${process.env.PGPORT || 5432}/${process.env.PGDATABASE || 'pos_db'}` : null);
}

async function initPostgres() {
  const connStr = getConnectionString();
  if (!connStr) {
    console.log('[PostgreSQL] No DATABASE_URL or PGHOST provided. Running in JSON file mode (pos_db.json).');
    return false;
  }

  if (!Pool) {
    console.warn('[PostgreSQL Warning] DATABASE_URL is set, but "pg" module is not installed.');
    console.warn('[PostgreSQL Warning] Please run: npm install pg');
    console.warn('[PostgreSQL Warning] Falling back to JSON file mode for now.');
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
    console.log('[PostgreSQL] Connected successfully to PostgreSQL database!');

    // Initialize Schema
    const schemaPath = path.join(__dirname, 'schema.sql');
    if (fs.existsSync(schemaPath)) {
      const schemaSql = fs.readFileSync(schemaPath, 'utf8');
      await client.query(schemaSql);
      console.log('[PostgreSQL] Schema initialized successfully from schema.sql');
    }

    client.release();
    isPostgresConnected = true;
    return true;
  } catch (err) {
    console.error(`[PostgreSQL Error] Connection failed: ${err.message}`);
    console.warn('[PostgreSQL] Falling back to local JSON file mode.');
    isPostgresConnected = false;
    return false;
  }
}

// Load all data from PostgreSQL into in-memory DB structure
async function loadAllFromPostgres() {
  if (!isPostgresConnected || !pool) return null;

  try {
    const db = {
      users: {},
      cloud_vouchers: {},
      cloud_voucher_items: {},
      cloud_products: {},
      cloud_initial_stock: {},
      cloud_customers: {},
      cloud_product_groups: {},
      cloud_product_units: {},
      cloud_payments: {},
      cloud_suppliers: {},
      cloud_expense_categories: {},
      cloud_expenses: {}
    };

    // 1. Users
    const usersRes = await pool.query('SELECT * FROM users');
    for (const row of usersRes.rows) {
      db.users[row.phone] = {
        phone: row.phone,
        name: row.name,
        shop_name: row.shop_name,
        password: row.password,
        role: row.role,
        status: row.status,
        device_id: row.device_id,
        devices: row.devices || [],
        created_at: Number(row.created_at),
        expires_at: row.expires_at ? Number(row.expires_at) : null,
        notes: row.notes
      };
    }

    // 2. Products
    const prodRes = await pool.query('SELECT * FROM products WHERE is_active = TRUE');
    for (const row of prodRes.rows) {
      const uPhone = row.user_phone;
      if (!db.cloud_products[uPhone]) db.cloud_products[uPhone] = {};
      db.cloud_products[uPhone][String(row.id)] = {
        id: Number(row.id),
        name: row.name,
        category: row.category,
        group_name: row.group_name,
        unit: row.unit,
        price: Number(row.price),
        cost: Number(row.cost),
        quantity: Number(row.quantity),
        initial_stock: Number(row.initial_stock),
        sku: row.sku,
        barcode: row.barcode,
        image_url: row.image_url,
        updated_at: Number(row.updated_at)
      };
    }

    // 3. Initial Stock
    const initStockRes = await pool.query('SELECT * FROM initial_stock');
    for (const row of initStockRes.rows) {
      const uPhone = row.user_phone;
      if (!db.cloud_initial_stock[uPhone]) db.cloud_initial_stock[uPhone] = {};
      db.cloud_initial_stock[uPhone][row.product_key] = Number(row.initial_stock);
    }

    // 4. Vouchers
    const vouchRes = await pool.query('SELECT * FROM vouchers');
    for (const row of vouchRes.rows) {
      const uPhone = row.user_phone;
      if (!db.cloud_vouchers[uPhone]) db.cloud_vouchers[uPhone] = {};
      db.cloud_vouchers[uPhone][row.receipt_no] = {
        receiptNo: row.receipt_no,
        voucherId: row.voucher_id ? Number(row.voucher_id) : null,
        customerName: row.customer_name,
        customerPhone: row.customer_phone,
        totalAmount: Number(row.total_amount),
        discount: Number(row.discount),
        netAmount: Number(row.net_amount),
        tax: Number(row.tax),
        paidAmount: Number(row.paid_amount),
        changeAmount: Number(row.change_amount),
        paymentMethod: row.payment_method,
        isCompleted: row.is_completed,
        isPurchase: row.is_purchase,
        isCredit: row.is_credit,
        notes: row.notes,
        createdAt: Number(row.created_at),
        updatedAt: Number(row.updated_at)
      };
    }

    // 5. Voucher Items
    const itemsRes = await pool.query('SELECT * FROM voucher_items');
    for (const row of itemsRes.rows) {
      const uPhone = row.user_phone;
      if (!db.cloud_voucher_items[uPhone]) db.cloud_voucher_items[uPhone] = [];
      db.cloud_voucher_items[uPhone].push({
        id: row.id,
        voucher_id: row.receipt_no,
        receiptNo: row.receipt_no,
        product_id: row.product_id ? Number(row.product_id) : null,
        product_name: row.product_name,
        quantity: Number(row.quantity),
        unit_price: Number(row.unit_price),
        total_price: Number(row.total_price),
        cost_price: Number(row.cost_price),
        unit: row.unit
      });
    }

    // 6. Customers
    const custRes = await pool.query('SELECT * FROM customers');
    for (const row of custRes.rows) {
      const uPhone = row.user_phone;
      if (!db.cloud_customers[uPhone]) db.cloud_customers[uPhone] = {};
      db.cloud_customers[uPhone][row.name] = {
        id: row.id,
        name: row.name,
        phone: row.phone,
        email: row.email,
        address: row.address,
        totalDebt: Number(row.total_debt),
        points: row.points
      };
    }

    // 7. Groups & Units
    const groupsRes = await pool.query('SELECT user_phone, name FROM product_groups');
    for (const row of groupsRes.rows) {
      if (!db.cloud_product_groups[row.user_phone]) db.cloud_product_groups[row.user_phone] = [];
      db.cloud_product_groups[row.user_phone].push(row.name);
    }

    const unitsRes = await pool.query('SELECT user_phone, name FROM product_units');
    for (const row of unitsRes.rows) {
      if (!db.cloud_product_units[row.user_phone]) db.cloud_product_units[row.user_phone] = [];
      db.cloud_product_units[row.user_phone].push(row.name);
    }

    // 8. Payments
    const payRes = await pool.query('SELECT * FROM payments');
    for (const row of payRes.rows) {
      if (!db.cloud_payments[row.user_phone]) db.cloud_payments[row.user_phone] = [];
      db.cloud_payments[row.user_phone].push({
        voucherId: row.voucher_id,
        customerName: row.customer_name,
        amount: Number(row.amount),
        paymentMethod: row.payment_method,
        paymentType: row.payment_type,
        date: Number(row.date),
        notes: row.notes
      });
    }

    // 9. Suppliers
    const suppRes = await pool.query('SELECT * FROM suppliers');
    for (const row of suppRes.rows) {
      if (!db.cloud_suppliers[row.user_phone]) db.cloud_suppliers[row.user_phone] = {};
      db.cloud_suppliers[row.user_phone][row.name] = {
        name: row.name,
        phone: row.phone,
        email: row.email,
        address: row.address,
        totalPayable: Number(row.total_payable)
      };
    }

    console.log(`[PostgreSQL] Loaded data from PostgreSQL (${Object.keys(db.users).length} users)`);
    return db;
  } catch (err) {
    console.error(`[PostgreSQL Error] Failed to load data: ${err.message}`);
    return null;
  }
}

// Save a user
async function saveUser(u) {
  if (!isPostgresConnected || !pool) return;
  try {
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
      u.phone || u.phoneNo,
      u.name || '',
      u.shop_name || u.shopName || '',
      u.password || '',
      u.role || 'user',
      u.status || 'active',
      u.device_id || '',
      JSON.stringify(u.devices || []),
      u.created_at || Date.now(),
      u.expires_at || null,
      u.notes || ''
    ]);
  } catch (err) {
    console.error(`[PostgreSQL Error] saveUser: ${err.message}`);
  }
}

// Save products batch
async function saveProducts(userPhone, productsMap) {
  if (!isPostgresConnected || !pool || !userPhone) return;
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    for (const key of Object.keys(productsMap)) {
      const p = productsMap[key];
      const pid = p.id || parseInt(key, 10);
      if (!pid) continue;
      await client.query(`
        INSERT INTO products (id, user_phone, name, category, group_name, unit, price, cost, quantity, initial_stock, sku, barcode, image_url, is_active, updated_at)
        VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, TRUE, $14)
        ON CONFLICT (user_phone, id) DO UPDATE SET
          name = EXCLUDED.name,
          category = EXCLUDED.category,
          group_name = EXCLUDED.group_name,
          unit = EXCLUDED.unit,
          price = EXCLUDED.price,
          cost = EXCLUDED.cost,
          quantity = EXCLUDED.quantity,
          initial_stock = EXCLUDED.initial_stock,
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
        p.group_name || p.group || '',
        p.unit || 'Pcs',
        Number(p.price || 0),
        Number(p.cost || 0),
        Number(p.quantity || 0),
        Number(p.initial_stock || p.initialStock || 0),
        p.sku || '',
        p.barcode || '',
        p.image_url || p.imageUrl || '',
        p.updated_at || Date.now()
      ]);
    }
    await client.query('COMMIT');
  } catch (err) {
    await client.query('ROLLBACK');
    console.error(`[PostgreSQL Error] saveProducts: ${err.message}`);
  } finally {
    client.release();
  }
}

// Save vouchers batch
async function saveVouchers(userPhone, vouchersMap, voucherItemsList) {
  if (!isPostgresConnected || !pool || !userPhone) return;
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    for (const key of Object.keys(vouchersMap)) {
      const v = vouchersMap[key];
      const receiptNo = v.receiptNo || v.receipt_no || key;
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
    }

    if (Array.isArray(voucherItemsList) && voucherItemsList.length > 0) {
      for (const it of voucherItemsList) {
        const receiptNo = it.voucher_id || it.receiptNo || it.voucherId;
        if (!receiptNo) continue;
        await client.query(`
          INSERT INTO voucher_items (user_phone, receipt_no, voucher_id, product_id, product_name, quantity, unit_price, total_price, cost_price, unit)
          VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
        `, [
          userPhone,
          receiptNo,
          it.voucherId || it.voucher_id || null,
          it.product_id || it.productId || null,
          it.product_name || it.productName || '',
          Number(it.quantity || 1),
          Number(it.unit_price || it.unitPrice || 0),
          Number(it.total_price || it.totalPrice || 0),
          Number(it.cost_price || it.costPrice || 0),
          it.unit || 'Pcs'
        ]);
      }
    }
    await client.query('COMMIT');
  } catch (err) {
    await client.query('ROLLBACK');
    console.error(`[PostgreSQL Error] saveVouchers: ${err.message}`);
  } finally {
    client.release();
  }
}

// Delete product
async function deleteProduct(userPhone, productId, productName) {
  if (!isPostgresConnected || !pool || !userPhone) return;
  try {
    if (productId) {
      await pool.query('UPDATE products SET is_active = FALSE WHERE user_phone = $1 AND id = $2', [userPhone, productId]);
    } else if (productName) {
      await pool.query('UPDATE products SET is_active = FALSE WHERE user_phone = $1 AND LOWER(name) = LOWER($2)', [userPhone, productName]);
    }
  } catch (err) {
    console.error(`[PostgreSQL Error] deleteProduct: ${err.message}`);
  }
}

// Delete voucher
async function deleteVoucher(userPhone, receiptNo) {
  if (!isPostgresConnected || !pool || !userPhone || !receiptNo) return;
  try {
    await pool.query('DELETE FROM voucher_items WHERE user_phone = $1 AND receipt_no = $2', [userPhone, receiptNo]);
    await pool.query('DELETE FROM vouchers WHERE user_phone = $1 AND receipt_no = $2', [userPhone, receiptNo]);
  } catch (err) {
    console.error(`[PostgreSQL Error] deleteVoucher: ${err.message}`);
  }
}

// Auto-migrate from JSON if PostgreSQL has 0 users
async function autoMigrateFromJson(jsonData) {
  if (!isPostgresConnected || !pool || !jsonData) return;
  try {
    const countRes = await pool.query('SELECT COUNT(*) as count FROM users');
    const userCount = parseInt(countRes.rows[0].count, 10);
    if (userCount > 0) return; // Already has data

    console.log('[PostgreSQL] PostgreSQL is empty. Auto-migrating data from pos_db.json...');

    // Migrate users
    if (jsonData.users) {
      for (const phone of Object.keys(jsonData.users)) {
        await saveUser(jsonData.users[phone]);
      }
    }

    // Migrate products
    if (jsonData.cloud_products) {
      for (const phone of Object.keys(jsonData.cloud_products)) {
        await saveProducts(phone, jsonData.cloud_products[phone]);
      }
    }

    // Migrate vouchers & items
    if (jsonData.cloud_vouchers) {
      for (const phone of Object.keys(jsonData.cloud_vouchers)) {
        const vMap = jsonData.cloud_vouchers[phone];
        const itList = jsonData.cloud_voucher_items ? jsonData.cloud_voucher_items[phone] : [];
        await saveVouchers(phone, vMap, itList);
      }
    }

    console.log('[PostgreSQL] Auto-migration completed successfully!');
  } catch (err) {
    console.error(`[PostgreSQL Error] Auto-migration failed: ${err.message}`);
  }
}

module.exports = {
  initPostgres,
  loadAllFromPostgres,
  saveUser,
  saveProducts,
  saveVouchers,
  deleteProduct,
  deleteVoucher,
  autoMigrateFromJson,
  isPostgresActive: () => isPostgresConnected,
  getPool: () => pool
};
