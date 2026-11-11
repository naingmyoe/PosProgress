/**
 * Cloudflare Worker Backend for UN POS Application
 * Database: Cloudflare D1 (env.DB)
 *
 * Setup Instructions:
 * 1. Install wrangler: npm install -g wrangler
 * 2. Login to Cloudflare: wrangler login
 * 3. Create D1 Database: wrangler d1 create pos_db
 * 4. Add D1 binding to wrangler.toml:
 *    [[d1_databases]]
 *    binding = "DB"
 *    database_name = "pos_db"
 *    database_id = "<your-d1-database-id>"
 * 5. Deploy Worker: wrangler deploy
 */

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const path = url.pathname;

    // Standard CORS Headers for Android / Web access
    const corsHeaders = {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type, Authorization',
      'Content-Type': 'application/json'
    };

    if (request.method === 'OPTIONS') {
      return new Response(null, { headers: corsHeaders });
    }

    try {
      // Initialize D1 table if not exists
      if (env.DB) {
        await env.DB.prepare(`
          CREATE TABLE IF NOT EXISTS users (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            phone_no TEXT UNIQUE NOT NULL,
            username TEXT NOT NULL,
            business_name TEXT NOT NULL,
            business_type TEXT NOT NULL,
            address TEXT,
            role TEXT NOT NULL,
            password_hash TEXT NOT NULL,
            device_id TEXT,
            status TEXT DEFAULT 'off',
            created_at INTEGER NOT NULL
          )
        `).run();

        // Safely add missing columns for existing tables
        try { await env.DB.prepare("ALTER TABLE users ADD COLUMN device_id TEXT").run(); } catch (e) {}
        try { await env.DB.prepare("ALTER TABLE users ADD COLUMN status TEXT DEFAULT 'off'").run(); } catch (e) {}

        // Multi-tenant Cloud POS Tables (Partitioned strictly by user_phone so Customer A and Customer B data never mix)
        await env.DB.prepare(`
          CREATE TABLE IF NOT EXISTS cloud_vouchers (
            user_phone TEXT NOT NULL,
            receipt_no TEXT NOT NULL,
            timestamp INTEGER NOT NULL,
            cashier_name TEXT,
            total_amount REAL,
            total_items INTEGER,
            customer_name TEXT,
            payment_method TEXT,
            is_completed INTEGER,
            is_purchase INTEGER,
            paid_amount REAL,
            change_amount REAL,
            balance_amount REAL,
            discount REAL,
            fee REAL,
            synced_at INTEGER,
            PRIMARY KEY (user_phone, receipt_no)
          )
        `).run();

        await env.DB.prepare(`
          CREATE TABLE IF NOT EXISTS cloud_voucher_items (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_phone TEXT NOT NULL,
            voucher_id TEXT NOT NULL,
            product_id INTEGER,
            product_name TEXT,
            quantity INTEGER,
            purchase_price REAL,
            selling_price REAL
          )
        `).run();

        await env.DB.prepare(`
          CREATE TABLE IF NOT EXISTS cloud_products (
            user_phone TEXT NOT NULL,
            product_id INTEGER NOT NULL,
            name TEXT NOT NULL,
            group_name TEXT,
            purchase_price REAL,
            selling_price REAL,
            unit TEXT,
            barcode TEXT,
            quantity INTEGER,
            alert_quantity INTEGER,
            synced_at INTEGER,
            PRIMARY KEY (user_phone, product_id)
          )
        `).run();

        await env.DB.prepare(`
          CREATE TABLE IF NOT EXISTS cloud_customers (
            user_phone TEXT NOT NULL,
            name TEXT NOT NULL,
            phone TEXT,
            address TEXT,
            note TEXT,
            synced_at INTEGER,
            PRIMARY KEY (user_phone, name)
          )
        `).run();

        await env.DB.prepare(`
          CREATE TABLE IF NOT EXISTS cloud_expenses (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_phone TEXT NOT NULL,
            category_name TEXT,
            description TEXT,
            amount REAL,
            payment_method TEXT,
            timestamp INTEGER,
            date_string TEXT
          )
        `).run();
      }

      // API: REGISTER USER (POST /api/register)
      if (path === '/api/register' && request.method === 'POST') {
        const body = await request.json();
        const { phoneNo, username, businessName, businessType, address, role, password, deviceId } = body;

        if (!phoneNo || !username || !password) {
          return new Response(
            JSON.stringify({ success: false, message: 'လိုအပ်သော အချက်အလက်များ မပြည့်စုံပါ (Missing required fields)' }),
            { status: 400, headers: corsHeaders }
          );
        }

        if (env.DB) {
          // Check if phone number already exists in Cloudflare D1
          const existing = await env.DB.prepare('SELECT id, device_id FROM users WHERE phone_no = ?').bind(phoneNo).first();
          if (existing) {
            // Update device_id if missing or re-registering
            await env.DB.prepare('UPDATE users SET device_id = ?, username = ?, password_hash = ? WHERE phone_no = ?')
              .bind(deviceId || '', username, password, phoneNo)
              .run();

            return new Response(
              JSON.stringify({
                success: true,
                message: 'အကောင့် ပြန်လည်ပြင်ဆင်ပြီးပါပြီ (User updated, pending activation)',
                status: 'off',
                user: { phoneNo, username, businessName, businessType, address, role, deviceId, status: 'off' }
              }),
              { status: 200, headers: corsHeaders }
            );
          }

          // Insert into D1 with default status = 'off'
          const now = Date.now();
          await env.DB.prepare(`
            INSERT INTO users (phone_no, username, business_name, business_type, address, role, password_hash, device_id, status, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'off', ?)
          `).bind(phoneNo, username, businessName || '', businessType || '', address || '', role || 'ADMIN', password, deviceId || '', now).run();
        }

        return new Response(
          JSON.stringify({
            success: true,
            message: 'အကောင့် အောင်မြင်စွာ ပြုလုပ်ပြီးပါပြီ (Registration successful, pending activation)',
            status: 'off',
            user: { phoneNo, username, businessName, businessType, address, role, deviceId, status: 'off' }
          }),
          { status: 200, headers: corsHeaders }
        );
      }

      // API: LOGIN USER (POST /api/login)
      if (path === '/api/login' && request.method === 'POST') {
        const body = await request.json();
        const { phoneNo, password, deviceId } = body;

        if (!phoneNo || !password) {
          return new Response(
            JSON.stringify({ success: false, message: 'ဖုန်းနံပါတ် နှင့် Password ဖြည့်ပါ (Missing login credentials)' }),
            { status: 400, headers: corsHeaders }
          );
        }

        if (env.DB) {
          const phoneUser = await env.DB.prepare('SELECT * FROM users WHERE phone_no = ?').bind(phoneNo).first();

          if (!phoneUser) {
            return new Response(
              JSON.stringify({ success: false, status: 'not_found', message: 'ဤဖုန်းနံပါတ်ဖြင့် register ပြုလုပ်ထားခြင်းမရှိပါ (Phone number is not registered)' }),
              { status: 404, headers: corsHeaders }
            );
          }

          if (phoneUser.password_hash !== password) {
            return new Response(
              JSON.stringify({ success: false, status: 'invalid_password', message: 'Password မှားယွင်းနေပါသည် (Incorrect password)' }),
              { status: 401, headers: corsHeaders }
            );
          }

          const user = phoneUser;

          // Verify deviceId matching
          if (deviceId && user.device_id && user.device_id !== '' && user.device_id !== deviceId) {
            return new Response(
              JSON.stringify({
                success: false,
                message: 'Device ID မကိုက်ညီပါ။ သင်မှတ်ပုံတင်ထားသော စက်မဟုတ်ပါ (Device ID mismatch)'
              }),
              { status: 400, headers: corsHeaders }
            );
          }

          // Update device_id if it was not stored previously
          if (deviceId && (!user.device_id || user.device_id === '')) {
            await env.DB.prepare('UPDATE users SET device_id = ? WHERE phone_no = ?').bind(deviceId, phoneNo).run();
          }

          const userStatus = user.status || 'off';
          if (userStatus !== 'on') {
            return new Response(
              JSON.stringify({
                success: false,
                status: 'off',
                message: 'အကောင့်ဖွင့်ရန် စောင့်ဆိုင်းနေဆဲဖြစ်ပါသည် (Account is pending activation)',
                user: {
                  phoneNo: user.phone_no,
                  username: user.username,
                  deviceId: user.device_id || deviceId,
                  status: 'off'
                }
              }),
              { status: 403, headers: corsHeaders }
            );
          }

          return new Response(
            JSON.stringify({
              success: true,
              status: 'on',
              message: 'Login successful',
              user: {
                phoneNo: user.phone_no,
                username: user.username,
                businessName: user.business_name,
                businessType: user.business_type,
                address: user.address,
                role: user.role,
                deviceId: user.device_id || deviceId,
                status: 'on'
              }
            }),
            { status: 200, headers: corsHeaders }
          );
        }

        return new Response(
          JSON.stringify({ success: true, status: 'on', message: 'Mock Login Successful' }),
          { status: 200, headers: corsHeaders }
        );
      }

      // API: CHECK STATUS (GET or POST /api/check-status)
      if (path === '/api/check-status') {
        let phoneNo = url.searchParams.get('phoneNo');
        let deviceId = url.searchParams.get('deviceId');

        if (request.method === 'POST') {
          try {
            const body = await request.json();
            if (body.phoneNo) phoneNo = body.phoneNo;
            if (body.deviceId) deviceId = body.deviceId;
          } catch (e) {}
        }

        if (env.DB) {
          let user = null;
          if (phoneNo) {
            user = await env.DB.prepare('SELECT phone_no, username, device_id, status FROM users WHERE phone_no = ?').bind(phoneNo).first();
          } else if (deviceId) {
            user = await env.DB.prepare('SELECT phone_no, username, device_id, status FROM users WHERE device_id = ?').bind(deviceId).first();
          }

          if (user) {
            return new Response(
              JSON.stringify({
                success: true,
                status: user.status || 'off',
                phoneNo: user.phone_no,
                username: user.username,
                deviceId: user.device_id
              }),
              { status: 200, headers: corsHeaders }
            );
          } else {
            return new Response(
              JSON.stringify({ success: false, status: 'off', message: 'User not found' }),
              { status: 404, headers: corsHeaders }
            );
          }
        }

        return new Response(
          JSON.stringify({ success: true, status: 'off', message: 'User status checked' }),
          { status: 200, headers: corsHeaders }
        );
      }

      // API: ADMIN TOGGLE STATUS (POST /api/toggle-status)
      if (path === '/api/toggle-status' && request.method === 'POST') {
        const body = await request.json();
        const { phoneNo, status } = body; // status = 'on' or 'off'

        if (!phoneNo || !status) {
          return new Response(
            JSON.stringify({ success: false, message: 'Missing phoneNo or status' }),
            { status: 400, headers: corsHeaders }
          );
        }

        if (env.DB) {
          await env.DB.prepare('UPDATE users SET status = ? WHERE phone_no = ?').bind(status, phoneNo).run();
          return new Response(
            JSON.stringify({ success: true, message: `Status updated to ${status} for ${phoneNo}`, phoneNo, status }),
            { status: 200, headers: corsHeaders }
          );
        }

        return new Response(
          JSON.stringify({ success: true, message: `Mock status updated to ${status}` }),
          { status: 200, headers: corsHeaders }
        );
      }

      // API: GET ALL USERS (GET /api/users)
      if (path === '/api/users' && request.method === 'GET') {
        if (env.DB) {
          const { results } = await env.DB.prepare('SELECT phone_no, username, business_name, business_type, address, role, device_id, status, created_at FROM users').all();
          return new Response(JSON.stringify({ success: true, users: results }), { status: 200, headers: corsHeaders });
        }
        return new Response(JSON.stringify({ success: true, users: [] }), { status: 200, headers: corsHeaders });
      }

      // ==========================================
      // POS OFFLINE-FIRST CLOUD SYNC ENDPOINTS
      // Partitioned strictly by userPhone (Customer A and B separation)
      // ==========================================

      // API: SYNC SINGLE VOUCHER (POST /api/sync/voucher) - Instant upload on sale
      if (path === '/api/sync/voucher' && request.method === 'POST') {
        const body = await request.json();
        const { userPhone, voucher, items } = body;

        if (!userPhone || !voucher) {
          return new Response(
            JSON.stringify({ success: false, message: 'Missing userPhone or voucher' }),
            { status: 400, headers: corsHeaders }
          );
        }

        const now = Date.now();
        if (env.DB) {
          // Insert or replace voucher for this specific userPhone
          await env.DB.prepare(`
            INSERT OR REPLACE INTO cloud_vouchers (
              user_phone, receipt_no, timestamp, cashier_name, total_amount, total_items,
              customer_name, payment_method, is_completed, is_purchase, paid_amount,
              change_amount, balance_amount, discount, fee, synced_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          `).bind(
            userPhone,
            voucher.receiptNo,
            voucher.timestamp || now,
            voucher.cashierName || '',
            voucher.totalAmount || 0,
            voucher.totalItems || 0,
            voucher.customerName || 'Not Register',
            voucher.paymentMethod || 'CASH',
            voucher.isCompleted ? 1 : 0,
            voucher.isPurchase ? 1 : 0,
            voucher.paidAmount || 0,
            voucher.changeAmount || 0,
            voucher.balanceAmount || 0,
            voucher.discount || 0,
            voucher.fee || 0,
            now
          ).run();

          // Delete old items for this voucher and insert new
          await env.DB.prepare('DELETE FROM cloud_voucher_items WHERE user_phone = ? AND voucher_id = ?')
            .bind(userPhone, voucher.receiptNo).run();

          if (items && Array.isArray(items)) {
            for (const item of items) {
              await env.DB.prepare(`
                INSERT INTO cloud_voucher_items (
                  user_phone, voucher_id, product_id, product_name, quantity, purchase_price, selling_price
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
              `).bind(
                userPhone,
                voucher.receiptNo,
                item.productId || 0,
                item.productName || '',
                item.quantity || 1,
                item.purchasePrice || 0,
                item.sellingPrice || 0
              ).run();
            }
          }
        }

        return new Response(
          JSON.stringify({
            success: true,
            message: 'Voucher synced to cloud successfully',
            receiptNo: voucher.receiptNo,
            syncedAt: now
          }),
          { status: 200, headers: corsHeaders }
        );
      }

      // API: BATCH SYNC VOUCHERS (POST /api/sync/vouchers) - Offline backlog sync
      if (path === '/api/sync/vouchers' && request.method === 'POST') {
        const body = await request.json();
        const { userPhone, vouchers } = body;

        if (!userPhone || !Array.isArray(vouchers)) {
          return new Response(
            JSON.stringify({ success: false, message: 'Missing userPhone or vouchers array' }),
            { status: 400, headers: corsHeaders }
          );
        }

        const now = Date.now();
        let syncedCount = 0;

        if (env.DB) {
          for (const entry of vouchers) {
            const v = entry.voucher || entry;
            const items = entry.items || [];

            await env.DB.prepare(`
              INSERT OR REPLACE INTO cloud_vouchers (
                user_phone, receipt_no, timestamp, cashier_name, total_amount, total_items,
                customer_name, payment_method, is_completed, is_purchase, paid_amount,
                change_amount, balance_amount, discount, fee, synced_at
              ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            `).bind(
              userPhone,
              v.receiptNo,
              v.timestamp || now,
              v.cashierName || '',
              v.totalAmount || 0,
              v.totalItems || 0,
              v.customerName || 'Not Register',
              v.paymentMethod || 'CASH',
              v.isCompleted ? 1 : 0,
              v.isPurchase ? 1 : 0,
              v.paidAmount || 0,
              v.changeAmount || 0,
              v.balanceAmount || 0,
              v.discount || 0,
              v.fee || 0,
              now
            ).run();

            await env.DB.prepare('DELETE FROM cloud_voucher_items WHERE user_phone = ? AND voucher_id = ?')
              .bind(userPhone, v.receiptNo).run();

            for (const item of items) {
              await env.DB.prepare(`
                INSERT INTO cloud_voucher_items (
                  user_phone, voucher_id, product_id, product_name, quantity, purchase_price, selling_price
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
              `).bind(
                userPhone,
                v.receiptNo,
                item.productId || 0,
                item.productName || '',
                item.quantity || 1,
                item.purchasePrice || 0,
                item.sellingPrice || 0
              ).run();
            }

            syncedCount++;
          }
        } else {
          syncedCount = vouchers.length;
        }

        return new Response(
          JSON.stringify({
            success: true,
            message: `${syncedCount} vouchers synced successfully`,
            syncedCount,
            syncedAt: now
          }),
          { status: 200, headers: corsHeaders }
        );
      }

      // API: BATCH SYNC PRODUCTS (POST /api/sync/products)
      if (path === '/api/sync/products' && request.method === 'POST') {
        const body = await request.json();
        const { userPhone, products } = body;

        if (!userPhone || !Array.isArray(products)) {
          return new Response(
            JSON.stringify({ success: false, message: 'Missing userPhone or products' }),
            { status: 400, headers: corsHeaders }
          );
        }

        const now = Date.now();
        if (env.DB) {
          for (const p of products) {
            await env.DB.prepare(`
              INSERT OR REPLACE INTO cloud_products (
                user_phone, product_id, name, group_name, purchase_price, selling_price,
                unit, barcode, quantity, alert_quantity, synced_at
              ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            `).bind(
              userPhone,
              p.id || 0,
              p.name,
              p.groupName || '',
              p.purchasePrice || 0,
              p.sellingPrice || 0,
              p.unit || 'ခု',
              p.barcode || '',
              p.quantity || 0,
              p.alertQuantity || 0,
              now
            ).run();
          }
        }

        return new Response(
          JSON.stringify({ success: true, count: products.length, syncedAt: now }),
          { status: 200, headers: corsHeaders }
        );
      }

      // API: BATCH SYNC CUSTOMERS (POST /api/sync/customers)
      if (path === '/api/sync/customers' && request.method === 'POST') {
        const body = await request.json();
        const { userPhone, customers } = body;

        if (!userPhone || !Array.isArray(customers)) {
          return new Response(
            JSON.stringify({ success: false, message: 'Missing userPhone or customers' }),
            { status: 400, headers: corsHeaders }
          );
        }

        const now = Date.now();
        if (env.DB) {
          for (const c of customers) {
            await env.DB.prepare(`
              INSERT OR REPLACE INTO cloud_customers (
                user_phone, name, phone, address, note, synced_at
              ) VALUES (?, ?, ?, ?, ?, ?)
            `).bind(
              userPhone,
              c.name,
              c.phone || '',
              c.address || '',
              c.note || '',
              now
            ).run();
          }
        }

        return new Response(
          JSON.stringify({ success: true, count: customers.length, syncedAt: now }),
          { status: 200, headers: corsHeaders }
        );
      }

      // API: PULL ALL CLOUD DATA FOR USER (POST /api/sync/pull)
      // Strictly filtered by userPhone so User A and User B never get each other's records!
      if (path === '/api/sync/pull' && request.method === 'POST') {
        const body = await request.json();
        const { userPhone } = body;

        if (!userPhone) {
          return new Response(
            JSON.stringify({ success: false, message: 'Missing userPhone' }),
            { status: 400, headers: corsHeaders }
          );
        }

        if (env.DB) {
          const vouchersRes = await env.DB.prepare('SELECT * FROM cloud_vouchers WHERE user_phone = ? ORDER BY timestamp DESC').bind(userPhone).all();
          const itemsRes = await env.DB.prepare('SELECT * FROM cloud_voucher_items WHERE user_phone = ?').bind(userPhone).all();
          const productsRes = await env.DB.prepare('SELECT * FROM cloud_products WHERE user_phone = ?').bind(userPhone).all();
          const customersRes = await env.DB.prepare('SELECT * FROM cloud_customers WHERE user_phone = ?').bind(userPhone).all();

          return new Response(
            JSON.stringify({
              success: true,
              userPhone,
              vouchers: vouchersRes.results || [],
              voucherItems: itemsRes.results || [],
              products: productsRes.results || [],
              customers: customersRes.results || []
            }),
            { status: 200, headers: corsHeaders }
          );
        }

        return new Response(
          JSON.stringify({
            success: true,
            userPhone,
            vouchers: [],
            voucherItems: [],
            products: [],
            customers: []
          }),
          { status: 200, headers: corsHeaders }
        );
      }

      // API: SYNC STATUS (GET /api/sync/status)
      if (path === '/api/sync/status' && request.method === 'GET') {
        const userPhone = url.searchParams.get('phoneNo') || '';
        if (env.DB && userPhone) {
          const vCount = await env.DB.prepare('SELECT COUNT(*) as count FROM cloud_vouchers WHERE user_phone = ?').bind(userPhone).first();
          const pCount = await env.DB.prepare('SELECT COUNT(*) as count FROM cloud_products WHERE user_phone = ?').bind(userPhone).first();
          const cCount = await env.DB.prepare('SELECT COUNT(*) as count FROM cloud_customers WHERE user_phone = ?').bind(userPhone).first();

          return new Response(
            JSON.stringify({
              success: true,
              userPhone,
              vouchersCount: vCount ? vCount.count : 0,
              productsCount: pCount ? pCount.count : 0,
              customersCount: cCount ? cCount.count : 0
            }),
            { status: 200, headers: corsHeaders }
          );
        }

        return new Response(
          JSON.stringify({
            success: true,
            userPhone,
            vouchersCount: 0,
            productsCount: 0,
            customersCount: 0
          }),
          { status: 200, headers: corsHeaders }
        );
      }

      return new Response(
        JSON.stringify({ success: false, message: 'Endpoint not found' }),
        { status: 404, headers: corsHeaders }
      );
    } catch (err) {
      return new Response(
        JSON.stringify({ success: false, message: err.message }),
        { status: 500, headers: corsHeaders }
      );
    }
  }
};
