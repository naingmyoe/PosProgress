-- ====================================================================
-- UN POS (Point of Sale & Inventory Management System)
-- PostgreSQL Database Schema
-- ====================================================================

-- 1. Users & Accounts Table
CREATE TABLE IF NOT EXISTS users (
    id SERIAL PRIMARY KEY,
    phone VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    shop_name VARCHAR(255) DEFAULT '',
    password VARCHAR(255) NOT NULL,
    role VARCHAR(50) DEFAULT 'user',
    status VARCHAR(50) DEFAULT 'active',
    device_id VARCHAR(255) DEFAULT '',
    devices JSONB DEFAULT '[]'::jsonb,
    created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    expires_at BIGINT,
    notes TEXT DEFAULT ''
);

CREATE INDEX IF NOT EXISTS idx_users_phone ON users(phone);
CREATE INDEX IF NOT EXISTS idx_users_status ON users(status);

-- 2. Products Table
CREATE TABLE IF NOT EXISTS products (
    id BIGINT NOT NULL,
    user_phone VARCHAR(50) NOT NULL REFERENCES users(phone) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    category VARCHAR(100) DEFAULT 'General',
    group_name VARCHAR(100) DEFAULT '',
    unit VARCHAR(50) DEFAULT 'Pcs',
    price NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    cost NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    quantity NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    initial_stock NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    sku VARCHAR(100) DEFAULT '',
    barcode VARCHAR(100) DEFAULT '',
    image_url TEXT DEFAULT '',
    is_active BOOLEAN DEFAULT TRUE,
    created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    updated_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    PRIMARY KEY (user_phone, id)
);

CREATE INDEX IF NOT EXISTS idx_products_user ON products(user_phone);
CREATE INDEX IF NOT EXISTS idx_products_name ON products(user_phone, name);
CREATE INDEX IF NOT EXISTS idx_products_category ON products(user_phone, category);
CREATE INDEX IF NOT EXISTS idx_products_barcode ON products(user_phone, barcode);

-- 3. Initial Baseline Stock Table
CREATE TABLE IF NOT EXISTS initial_stock (
    user_phone VARCHAR(50) NOT NULL REFERENCES users(phone) ON DELETE CASCADE,
    product_key VARCHAR(255) NOT NULL,
    initial_stock NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    updated_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    PRIMARY KEY (user_phone, product_key)
);

-- 4. Vouchers / Transactions Table
CREATE TABLE IF NOT EXISTS vouchers (
    receipt_no VARCHAR(100) NOT NULL,
    user_phone VARCHAR(50) NOT NULL REFERENCES users(phone) ON DELETE CASCADE,
    voucher_id BIGINT,
    customer_name VARCHAR(255) DEFAULT 'Walk-in Customer',
    customer_phone VARCHAR(50) DEFAULT '',
    total_amount NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    discount NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    net_amount NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    tax NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    paid_amount NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    change_amount NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    payment_method VARCHAR(50) DEFAULT 'Cash',
    is_completed BOOLEAN DEFAULT TRUE,
    is_purchase BOOLEAN DEFAULT FALSE,
    is_credit BOOLEAN DEFAULT FALSE,
    notes TEXT DEFAULT '',
    created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    updated_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    PRIMARY KEY (user_phone, receipt_no)
);

CREATE INDEX IF NOT EXISTS idx_vouchers_user_receipt ON vouchers(user_phone, receipt_no);
CREATE INDEX IF NOT EXISTS idx_vouchers_created_at ON vouchers(user_phone, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_vouchers_customer ON vouchers(user_phone, customer_name);

-- 5. Voucher Line Items Table
CREATE TABLE IF NOT EXISTS voucher_items (
    id SERIAL PRIMARY KEY,
    user_phone VARCHAR(50) NOT NULL REFERENCES users(phone) ON DELETE CASCADE,
    receipt_no VARCHAR(100) NOT NULL,
    voucher_id BIGINT,
    product_id BIGINT,
    product_name VARCHAR(255) NOT NULL,
    quantity NUMERIC(12, 2) NOT NULL DEFAULT 1.00,
    unit_price NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    total_price NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    cost_price NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    unit VARCHAR(50) DEFAULT 'Pcs'
);

CREATE INDEX IF NOT EXISTS idx_voucher_items_user_receipt ON voucher_items(user_phone, receipt_no);
CREATE INDEX IF NOT EXISTS idx_voucher_items_product ON voucher_items(user_phone, product_id);

-- 6. Customers Table
CREATE TABLE IF NOT EXISTS customers (
    id SERIAL PRIMARY KEY,
    user_phone VARCHAR(50) NOT NULL REFERENCES users(phone) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    phone VARCHAR(50) DEFAULT '',
    email VARCHAR(255) DEFAULT '',
    address TEXT DEFAULT '',
    total_debt NUMERIC(12, 2) DEFAULT 0.00,
    points INT DEFAULT 0,
    created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    UNIQUE (user_phone, name)
);

CREATE INDEX IF NOT EXISTS idx_customers_user ON customers(user_phone);

-- 7. Suppliers Table
CREATE TABLE IF NOT EXISTS suppliers (
    id SERIAL PRIMARY KEY,
    user_phone VARCHAR(50) NOT NULL REFERENCES users(phone) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    phone VARCHAR(50) DEFAULT '',
    email VARCHAR(255) DEFAULT '',
    address TEXT DEFAULT '',
    total_payable NUMERIC(12, 2) DEFAULT 0.00,
    created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    UNIQUE (user_phone, name)
);

CREATE INDEX IF NOT EXISTS idx_suppliers_user ON suppliers(user_phone);

-- 8. Payments Table
CREATE TABLE IF NOT EXISTS payments (
    id SERIAL PRIMARY KEY,
    user_phone VARCHAR(50) NOT NULL REFERENCES users(phone) ON DELETE CASCADE,
    voucher_id VARCHAR(100) DEFAULT '',
    customer_name VARCHAR(255) DEFAULT '',
    amount NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    payment_method VARCHAR(50) DEFAULT 'Cash',
    payment_type VARCHAR(50) DEFAULT 'Sale',
    date BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    notes TEXT DEFAULT ''
);

CREATE INDEX IF NOT EXISTS idx_payments_user ON payments(user_phone);

-- 9. Expenses Table
CREATE TABLE IF NOT EXISTS expenses (
    id SERIAL PRIMARY KEY,
    user_phone VARCHAR(50) NOT NULL REFERENCES users(phone) ON DELETE CASCADE,
    category VARCHAR(100) NOT NULL,
    amount NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    description TEXT DEFAULT '',
    date BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT
);

CREATE INDEX IF NOT EXISTS idx_expenses_user ON expenses(user_phone);

-- 10. Metadata / Auxiliary Entities
CREATE TABLE IF NOT EXISTS product_groups (
    id SERIAL PRIMARY KEY,
    user_phone VARCHAR(50) NOT NULL REFERENCES users(phone) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    UNIQUE (user_phone, name)
);

CREATE TABLE IF NOT EXISTS product_units (
    id SERIAL PRIMARY KEY,
    user_phone VARCHAR(50) NOT NULL REFERENCES users(phone) ON DELETE CASCADE,
    name VARCHAR(50) NOT NULL,
    UNIQUE (user_phone, name)
);

CREATE TABLE IF NOT EXISTS expense_categories (
    id SERIAL PRIMARY KEY,
    user_phone VARCHAR(50) NOT NULL REFERENCES users(phone) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    UNIQUE (user_phone, name)
);

-- 11. Admin Settings & System State
CREATE TABLE IF NOT EXISTS admin_settings (
    setting_key VARCHAR(100) PRIMARY KEY,
    setting_value TEXT NOT NULL,
    updated_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT
);

-- Insert Default Super Admin Password Hash if not present
INSERT INTO admin_settings (setting_key, setting_value)
VALUES ('admin_password', 'admin1234')
ON CONFLICT (setting_key) DO NOTHING;
