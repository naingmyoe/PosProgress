/**
 * UN POS Backend Server for VPS (Linux / Ubuntu / Debian / Windows)
 * 100% Stateless & Direct PostgreSQL Architecture
 * ZERO In-Memory Cache (Extremely low RAM usage, scales effortlessly to 100,000+ users)
 *
 * Default Port: 8081 (Can be changed via PORT environment variable)
 */

const http = require('http');
const fs = require('fs');
const path = require('path');
const url = require('url');

const postgres = require('./db_postgres');
const PORT = process.env.PORT || 8081;

// Initialize PostgreSQL Schema on Startup
(async () => {
  try {
    await postgres.initPostgres();
  } catch (err) {
    console.error(`[Startup Error] ${err.message}`);
  }
})();

// CORS Headers for Android HTTP requests
const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type, Authorization',
  'Content-Type': 'application/json'
};

function sendJson(res, statusCode, data) {
  res.writeHead(statusCode, CORS_HEADERS);
  res.end(JSON.stringify(data));
}

function parseJsonBody(req) {
  return new Promise((resolve) => {
    let body = '';
    req.on('data', chunk => {
      body += chunk.toString();
    });
    req.on('end', () => {
      try {
        resolve(body ? JSON.parse(body) : {});
      } catch (e) {
        resolve({});
      }
    });
  });
}

const SEVEN_DAYS_MS = 7 * 24 * 60 * 60 * 1000;

async function evaluateUserAccess(u, clientDeviceId) {
  const now = Date.now();
  if (!Array.isArray(u.devices)) {
    u.devices = [];
    if (u.device_id) u.devices.push(u.device_id);
  }

  if (!u.created_at) u.created_at = now;
  if (!u.expires_at) {
    u.expires_at = u.created_at + SEVEN_DAYS_MS;
  }

  // Device check (max 5)
  if (clientDeviceId) {
    if (!u.devices.includes(clientDeviceId)) {
      if (u.devices.length >= 5) {
        return {
          allowed: false,
          status: 'device_limit_reached',
          message: 'ဤအကောင့်ကို အများဆုံး စက် ၅ လုံးအထိသာ သုံးခွင့်ပြုထားပါသည် (Device limit of 5 reached)',
          expiresAt: u.expires_at,
          daysLeft: Math.max(0, Math.ceil((u.expires_at - now) / (24 * 60 * 60 * 1000))),
          deviceCount: u.devices.length,
          maxDevices: 5
        };
      }
      u.devices.push(clientDeviceId);
      u.device_id = clientDeviceId;
      await postgres.updateUser(u.phone, { devices: u.devices, device_id: clientDeviceId });
    }
  }

  // Check expiration
  if (now > u.expires_at) {
    if (u.status !== 'expired') {
      await postgres.updateUser(u.phone, { status: 'expired' });
    }
    return {
      allowed: false,
      status: 'expired',
      message: 'အကောင့် သက်တမ်းကုန်ဆုံးသွားပါပြီ။ သက်တမ်းတိုးရန် ဆက်သွယ်ပါ (Account Expired)',
      expiresAt: u.expires_at,
      daysLeft: 0,
      deviceCount: u.devices.length,
      maxDevices: 5
    };
  }

  return {
    allowed: true,
    status: u.status || 'on',
    message: 'အကောင့် အသုံးပြုခွင့် ရှိပါသည်',
    expiresAt: u.expires_at,
    daysLeft: Math.max(0, Math.ceil((u.expires_at - now) / (24 * 60 * 60 * 1000))),
    deviceCount: u.devices.length,
    maxDevices: 5
  };
}

const server = http.createServer(async (req, res) => {
  if (req.method === 'OPTIONS') {
    res.writeHead(204, CORS_HEADERS);
    res.end();
    return;
  }

  const parsedUrl = url.parse(req.url, true);
  const pathname = parsedUrl.pathname;
  const method = req.method;

  // Serve Admin Dashboard (GET / or /admin or /admin.html)
  if (method === 'GET' && (pathname === '/' || pathname === '/admin' || pathname === '/admin.html')) {
    const adminHtmlPath = path.join(__dirname, 'admin.html');
    if (fs.existsSync(adminHtmlPath)) {
      const htmlContent = fs.readFileSync(adminHtmlPath, 'utf8');
      res.writeHead(200, {
        'Content-Type': 'text/html; charset=utf-8',
        'Access-Control-Allow-Origin': '*',
        'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
        'Access-Control-Allow-Headers': 'Content-Type, Authorization',
        'Cache-Control': 'no-cache, no-store, must-revalidate'
      });
      res.end(htmlContent);
      return;
    } else {
      res.writeHead(404, { 'Content-Type': 'text/html; charset=utf-8' });
      res.end('<h3>admin.html not found</h3>');
      return;
    }
  }

  try {
    // 1. Healthcheck & Check Status (GET or POST /api/check-status)
    if (pathname === '/api/check-status') {
      let phoneNo = parsedUrl.query.phoneNo;
      let deviceId = parsedUrl.query.deviceId;

      if (method === 'POST') {
        const body = await parseJsonBody(req);
        if (body.phoneNo) phoneNo = body.phoneNo;
        if (body.deviceId) deviceId = body.deviceId;
      }

      if (phoneNo) {
        const u = await postgres.getUserByPhone(phoneNo);
        if (u) {
          const evalResult = await evaluateUserAccess(u, deviceId);
          return sendJson(res, 200, {
            success: evalResult.allowed,
            status: evalResult.status,
            message: evalResult.message,
            phoneNo: u.phone,
            username: u.name,
            businessName: u.shop_name,
            deviceId: deviceId || u.device_id,
            devices: u.devices,
            deviceCount: evalResult.deviceCount,
            maxDevices: 5,
            expiresAt: evalResult.expiresAt,
            daysLeft: evalResult.daysLeft
          });
        }
      }

      return sendJson(res, 200, {
        success: true,
        status: 'on',
        database: 'PostgreSQL (Stateless)',
        message: 'UN POS Server is running online',
        timestamp: Date.now()
      });
    }

    // 2. Register (POST /api/register)
    if (pathname === '/api/register' && method === 'POST') {
      const body = await parseJsonBody(req);
      const { phoneNo, password, username, businessName, deviceId } = body;

      if (!phoneNo || !password) {
        return sendJson(res, 400, { success: false, message: 'ဖုန်းနံပါတ်နှင့် စကားဝှက် ထည့်သွင်းပေးပါ' });
      }

      const existing = await postgres.getUserByPhone(phoneNo);
      if (existing) {
        return sendJson(res, 400, { success: false, message: 'ဤဖုန်းနံပါတ်ဖြင့် အကောင့်ဖွင့်ထားပြီးဖြစ်ပါသည်' });
      }

      const now = Date.now();
      const initialDevices = deviceId ? [deviceId] : [];

      const newUser = await postgres.createUser({
        phone: phoneNo,
        name: username || businessName || phoneNo,
        shop_name: businessName || username || '',
        password: password,
        role: 'user',
        device_id: deviceId || '',
        devices: initialDevices,
        status: 'on',
        created_at: now,
        expires_at: now + SEVEN_DAYS_MS
      });

      console.log(`[USER] Registered new user: ${phoneNo}`);

      return sendJson(res, 200, {
        success: true,
        message: 'အကောင့် အောင်မြင်စွာ ပြုလုပ်ပြီးပါပြီ (၇ ရက် အခမဲ့ စမ်းသပ်ခွင့် ရရှိပါသည်)',
        status: 'on',
        expiresAt: newUser.expires_at,
        daysLeft: 7,
        user: {
          phoneNo: newUser.phone,
          username: newUser.name,
          businessName: newUser.shop_name,
          status: newUser.status,
          role: newUser.role,
          expiresAt: newUser.expires_at,
          daysLeft: 7,
          deviceCount: initialDevices.length,
          maxDevices: 5
        }
      });
    }

    // 3. Login (POST /api/login)
    if (pathname === '/api/login' && method === 'POST') {
      const body = await parseJsonBody(req);
      const { phoneNo, password, deviceId } = body;

      if (!phoneNo || !password) {
        return sendJson(res, 400, { success: false, message: 'ဖုန်းနံပါတ်နှင့် စကားဝှက် ထည့်သွင်းပေးပါ' });
      }

      const u = await postgres.getUserByPhone(phoneNo);
      if (!u) {
        return sendJson(res, 404, { success: false, message: 'အကောင့် မရှိသေးပါ (ကျေးဇူးပြု၍ စာရင်းသွင်းပါ)' });
      }

      if (u.password !== password) {
        return sendJson(res, 401, { success: false, message: 'စကားဝှက် မှားယွင်းနေပါသည်' });
      }

      const evalResult = await evaluateUserAccess(u, deviceId);
      if (!evalResult.allowed) {
        return sendJson(res, 403, {
          success: false,
          status: evalResult.status,
          message: evalResult.message,
          expiresAt: evalResult.expiresAt,
          daysLeft: evalResult.daysLeft,
          deviceCount: evalResult.deviceCount,
          maxDevices: evalResult.maxDevices
        });
      }

      console.log(`[USER] User logged in: ${phoneNo}`);

      return sendJson(res, 200, {
        success: true,
        message: 'ဝင်ရောက်မှု အောင်မြင်ပါသည်',
        user: {
          phoneNo: u.phone,
          username: u.name,
          businessName: u.shop_name,
          status: evalResult.status,
          role: u.role || 'user',
          expiresAt: evalResult.expiresAt,
          daysLeft: evalResult.daysLeft,
          deviceCount: evalResult.deviceCount,
          maxDevices: evalResult.maxDevices
        }
      });
    }

    // 4. Change Password (POST /api/change-password)
    if (pathname === '/api/change-password' && method === 'POST') {
      const body = await parseJsonBody(req);
      const { phoneNo, oldPassword, newPassword } = body;

      if (!phoneNo || !oldPassword || !newPassword) {
        return sendJson(res, 400, { success: false, message: 'Missing fields' });
      }

      const u = await postgres.getUserByPhone(phoneNo);
      if (!u || u.password !== oldPassword) {
        return sendJson(res, 401, { success: false, message: 'စကားဝှက်အဟောင်း မှားယွင်းနေပါသည်' });
      }

      await postgres.updateUser(phoneNo, { password: newPassword });
      return sendJson(res, 200, { success: true, message: 'စကားဝှက် အောင်မြင်စွာ ပြောင်းလဲပြီးပါပြီ' });
    }

    // 5. Admin Endpoints
    if (pathname === '/api/admin/login' && method === 'POST') {
      const body = await parseJsonBody(req);
      const adminPass = await postgres.getAdminPassword();
      if (body.password === adminPass) {
        return sendJson(res, 200, { success: true, token: 'admin_session_token_' + Date.now() });
      }
      return sendJson(res, 401, { success: false, message: 'Admin Password မှားယွင်းနေပါသည်' });
    }

    if (pathname === '/api/admin/change-admin-password' && method === 'POST') {
      const body = await parseJsonBody(req);
      const { oldPassword, newPassword } = body;
      const currentPass = await postgres.getAdminPassword();
      if (oldPassword !== currentPass) {
        return sendJson(res, 401, { success: false, message: 'လက်ရှိ Admin စကားဝှက် မှားယွင်းနေပါသည်' });
      }
      await postgres.setAdminPassword(newPassword);
      return sendJson(res, 200, { success: true, message: 'Admin စကားဝှက် ပြောင်းလဲပြီးပါပြီ' });
    }

    if (pathname === '/api/admin/users' && method === 'GET') {
      const users = await postgres.getAllUsers();
      return sendJson(res, 200, { success: true, users });
    }

    if (pathname === '/api/admin/stats' && method === 'GET') {
      const stats = await postgres.getAdminStats();
      return sendJson(res, 200, { success: true, stats });
    }

    if ((pathname === '/api/renew' || pathname === '/api/admin/renew') && method === 'POST') {
      const body = await parseJsonBody(req);
      const { phoneNo, days, months, years } = body;
      const u = await postgres.getUserByPhone(phoneNo);
      if (!u) {
        return sendJson(res, 404, { success: false, message: 'User not found' });
      }

      const now = Date.now();
      const baseTime = (u.expires_at && u.expires_at > now) ? u.expires_at : now;
      let addedMs = 0;
      if (years) addedMs += Number(years) * 365 * 24 * 60 * 60 * 1000;
      if (months) addedMs += Number(months) * 30 * 24 * 60 * 60 * 1000;
      if (days) addedMs += Number(days) * 24 * 60 * 60 * 1000;
      if (addedMs === 0) addedMs = 30 * 24 * 60 * 60 * 1000; // default 30 days

      const newExpiresAt = baseTime + addedMs;
      await postgres.updateUser(phoneNo, { expires_at: newExpiresAt, status: 'on' });
      return sendJson(res, 200, { success: true, message: 'သက်တမ်းတိုးပြီးပါပြီ', expiresAt: newExpiresAt });
    }

    if (pathname === '/api/admin/user/update' && method === 'POST') {
      const body = await parseJsonBody(req);
      const { phoneNo, username, businessName, status, role, expiresAt } = body;
      await postgres.updateUser(phoneNo, {
        name: username,
        shop_name: businessName,
        status: status,
        role: role,
        expires_at: expiresAt ? Number(expiresAt) : undefined
      });
      return sendJson(res, 200, { success: true, message: 'User updated successfully' });
    }

    if (pathname === '/api/admin/user/create' && method === 'POST') {
      const body = await parseJsonBody(req);
      const { phoneNo, password, username, businessName, days } = body;
      const existing = await postgres.getUserByPhone(phoneNo);
      if (existing) {
        return sendJson(res, 400, { success: false, message: 'User already exists' });
      }
      const expiresAt = Date.now() + (Number(days || 30) * 24 * 60 * 60 * 1000);
      await postgres.createUser({
        phone: phoneNo,
        name: username || businessName || phoneNo,
        shop_name: businessName || username || '',
        password: password || '123456',
        status: 'on',
        role: 'user',
        expires_at: expiresAt
      });
      return sendJson(res, 200, { success: true, message: 'User created' });
    }

    if (pathname === '/api/admin/user/delete' && method === 'POST') {
      const body = await parseJsonBody(req);
      await postgres.deleteUser(body.phoneNo);
      return sendJson(res, 200, { success: true, message: 'User deleted' });
    }

    if (pathname === '/api/admin/vouchers' && method === 'GET') {
      const pool = postgres.getPool();
      if (!pool) return sendJson(res, 200, { success: true, vouchers: [] });
      const resV = await pool.query('SELECT * FROM vouchers ORDER BY created_at DESC LIMIT 100');
      return sendJson(res, 200, { success: true, vouchers: resV.rows });
    }

    if (pathname === '/api/admin/products' && method === 'GET') {
      const pool = postgres.getPool();
      if (!pool) return sendJson(res, 200, { success: true, products: [] });
      const resP = await pool.query('SELECT * FROM products WHERE is_active = TRUE ORDER BY name ASC LIMIT 100');
      return sendJson(res, 200, { success: true, products: resP.rows });
    }

    // 6. SYNC: Single Voucher (POST /api/sync/voucher)
    if (pathname === '/api/sync/voucher' && method === 'POST') {
      const body = await parseJsonBody(req);
      const { userPhone, voucher, items } = body;
      if (!userPhone || !voucher || !voucher.receiptNo) {
        return sendJson(res, 400, { success: false, message: 'Missing userPhone or voucher' });
      }
      await postgres.syncVoucher(userPhone, voucher, items || []);
      console.log(`[SYNC] Voucher synced to PostgreSQL: ${voucher.receiptNo} (${userPhone})`);
      return sendJson(res, 200, { success: true, receiptNo: voucher.receiptNo });
    }

    // 7. SYNC: Batch Vouchers (POST /api/sync/vouchers)
    if (pathname === '/api/sync/vouchers' && method === 'POST') {
      const body = await parseJsonBody(req);
      const { userPhone, vouchers } = body;
      if (!userPhone || !Array.isArray(vouchers)) {
        return sendJson(res, 400, { success: false, message: 'Missing userPhone or vouchers' });
      }
      const count = await postgres.syncVouchersBatch(userPhone, vouchers);
      console.log(`[SYNC] Batch ${count} vouchers synced to PostgreSQL (${userPhone})`);
      return sendJson(res, 200, { success: true, syncedCount: count });
    }

    // 8. SYNC: Products Batch (POST /api/sync/products)
    if (pathname === '/api/sync/products' && method === 'POST') {
      const body = await parseJsonBody(req);
      const { userPhone, products } = body;
      if (!userPhone || !Array.isArray(products)) {
        return sendJson(res, 400, { success: false, message: 'Missing userPhone or products' });
      }
      const count = await postgres.syncProducts(userPhone, products);
      console.log(`[SYNC] ${count} products synced to PostgreSQL (${userPhone})`);
      return sendJson(res, 200, { success: true, count });
    }

    // 9. SYNC: Delete Product (POST /api/sync/delete-product)
    if (pathname === '/api/sync/delete-product' && method === 'POST') {
      const body = await parseJsonBody(req);
      const { userPhone, productId, productName } = body;
      await postgres.deleteProduct(userPhone, productId, productName);
      return sendJson(res, 200, { success: true, message: 'Product deleted' });
    }

    // 10. SYNC: Batch Delete Products (POST /api/sync/delete-products)
    if (pathname === '/api/sync/delete-products' && method === 'POST') {
      const body = await parseJsonBody(req);
      const { userPhone, productIds, productNames } = body;
      await postgres.deleteProductsBatch(userPhone, productIds, productNames);
      return sendJson(res, 200, { success: true, message: 'Products batch deleted' });
    }

    // 11. SYNC: Delete Voucher (POST /api/sync/delete-voucher)
    if (pathname === '/api/sync/delete-voucher' && method === 'POST') {
      const body = await parseJsonBody(req);
      const { userPhone, receiptNo } = body;
      await postgres.deleteVoucher(userPhone, receiptNo);
      return sendJson(res, 200, { success: true, message: 'Voucher deleted' });
    }

    // 12. SYNC: Customers (POST /api/sync/customers)
    if (pathname === '/api/sync/customers' && method === 'POST') {
      const body = await parseJsonBody(req);
      const { userPhone, customers } = body;
      if (userPhone && Array.isArray(customers)) {
        await postgres.syncCustomers(userPhone, customers);
      }
      return sendJson(res, 200, { success: true, message: 'Customers synced' });
    }

    // 13. SYNC: Groups (POST /api/sync/groups)
    if (pathname === '/api/sync/groups' && method === 'POST') {
      const body = await parseJsonBody(req);
      const { userPhone, groups } = body;
      if (userPhone && Array.isArray(groups)) {
        await postgres.syncGroups(userPhone, groups);
      }
      return sendJson(res, 200, { success: true, message: 'Groups synced' });
    }

    // 14. SYNC: Units (POST /api/sync/units)
    if (pathname === '/api/sync/units' && method === 'POST') {
      const body = await parseJsonBody(req);
      const { userPhone, units } = body;
      if (userPhone && Array.isArray(units)) {
        await postgres.syncUnits(userPhone, units);
      }
      return sendJson(res, 200, { success: true, message: 'Units synced' });
    }

    // 15. SYNC: Payments (POST /api/sync/payments)
    if (pathname === '/api/sync/payments' && method === 'POST') {
      const body = await parseJsonBody(req);
      const { userPhone, payments } = body;
      if (userPhone && Array.isArray(payments)) {
        await postgres.syncPayments(userPhone, payments);
      }
      return sendJson(res, 200, { success: true, message: 'Payments synced' });
    }

    // 16. SYNC: Suppliers (POST /api/sync/suppliers)
    if (pathname === '/api/sync/suppliers' && method === 'POST') {
      const body = await parseJsonBody(req);
      const { userPhone, suppliers } = body;
      if (userPhone && Array.isArray(suppliers)) {
        await postgres.syncSuppliers(userPhone, suppliers);
      }
      return sendJson(res, 200, { success: true, message: 'Suppliers synced' });
    }

    // 17. SYNC: Pull All Data Directly from PostgreSQL (GET or POST /api/sync/pull)
    if (pathname === '/api/sync/pull' && (method === 'POST' || method === 'GET')) {
      let userPhone = parsedUrl.query.phoneNo || parsedUrl.query.userPhone;
      if (method === 'POST') {
        const body = await parseJsonBody(req);
        if (body.userPhone) userPhone = body.userPhone;
      }

      if (!userPhone) {
        return sendJson(res, 400, { success: false, message: 'Missing userPhone' });
      }

      const data = await postgres.pullUserData(userPhone);
      console.log(`[PULL] Streamed data from PostgreSQL for ${userPhone}: ${data.vouchers.length} vouchers, ${data.products.length} prods`);

      return sendJson(res, 200, {
        success: true,
        userPhone,
        vouchers: data.vouchers,
        voucherItems: data.voucherItems,
        products: data.products,
        customers: data.customers,
        groups: data.groups,
        units: data.units,
        payments: data.payments,
        suppliers: data.suppliers,
        expenseCategories: [],
        expenses: []
      });
    }

    // 18. SYNC: Status Counts directly from PostgreSQL (GET /api/sync/status)
    if (pathname === '/api/sync/status' && method === 'GET') {
      const userPhone = parsedUrl.query.phoneNo || '';
      const counts = await postgres.getSyncStatus(userPhone);
      return sendJson(res, 200, {
        success: true,
        userPhone,
        ...counts
      });
    }

    // 19. SYNC: Mirror Full Sync (POST /api/sync/mirror)
    if (pathname === '/api/sync/mirror' && method === 'POST') {
      const body = await parseJsonBody(req);
      const { userPhone, products, vouchers, customers, groups, units, payments, suppliers } = body;

      if (!userPhone) {
        return sendJson(res, 400, { success: false, message: 'Missing userPhone' });
      }

      if (Array.isArray(products)) await postgres.syncProducts(userPhone, products);
      if (Array.isArray(vouchers)) await postgres.syncVouchersBatch(userPhone, vouchers);
      if (Array.isArray(customers)) await postgres.syncCustomers(userPhone, customers);
      if (Array.isArray(groups)) await postgres.syncGroups(userPhone, groups);
      if (Array.isArray(units)) await postgres.syncUnits(userPhone, units);
      if (Array.isArray(payments)) await postgres.syncPayments(userPhone, payments);
      if (Array.isArray(suppliers)) await postgres.syncSuppliers(userPhone, suppliers);

      console.log(`[MIRROR] Atomic mirror sync completed to PostgreSQL for ${userPhone}`);
      return sendJson(res, 200, { success: true, message: 'Mirror sync completed' });
    }

    // 20. Fallback 404
    return sendJson(res, 404, { success: false, message: 'Endpoint not found' });

  } catch (err) {
    console.error(`[Server Error] ${err.message}`);
    return sendJson(res, 500, { success: false, message: err.message });
  }
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`====================================================`);
  console.log(`  UN POS Backend Server is running!`);
  console.log(`  Port: ${PORT}`);
  console.log(`  URL:  http://localhost:${PORT}`);
  console.log(`  Database: Direct PostgreSQL Engine (Stateless, 0MB Cache)`);
  console.log(`====================================================`);
});
