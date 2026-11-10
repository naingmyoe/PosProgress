# UN POS - PostgreSQL Database Setup Guide

UN POS စနစ်တွင် **PostgreSQL Database** ကို ချိတ်ဆက်အသုံးပြုရန် အပြည့်အစုံ ပြင်ဆင်ပေးထားပါသည်။

---

## ၁။ စနစ်၏ လုပ်ဆောင်ပုံ အကျဉ်းချုပ် (Architecture)

```
[Android Mobile POS (Offline-first)]
          │
          │ (HTTP REST / JSON Sync)
          ▼
[UN POS Backend Server (server.js)]
          │
          │ (pg Connection Pool / DATABASE_URL)
          ▼
[PostgreSQL Database (schema.sql)]
```

- **Android App**: ဖုန်းပေါ်တွင် အင်တာနက် မရှိသည့်အခါ၌လည်း အရောင်းစာရင်း၊ ဘောက်ချာများ၊ ကုန်ပစ္စည်းများကို ပုံမှန်အတိုင်း သုံးနိုင်သည် (Local Room SQLite)။
- **Sync System**: အင်တာနက် ရရှိသည့်အခါ သို့မဟုတ် "Sync Now" နှိပ်သည့်အခါ Server မှတစ်ဆင့် **PostgreSQL** သို့ အလိုအလျောက် ရောက်ရှိသိမ်းဆည်းပေးသည်။
- **Multi-Device**: ဆိုင်ခွဲများ သို့မဟုတ် စက်အများအပြားမှ ဒေတာများကို PostgreSQL တွင် စုစည်းပြီး တစ်ပြိုင်နက် ကြည့်ရှုနိုင်ပါသည်။

---

## ၂။ အသုံးပြုနိုင်သော နည်းလမ်းများ (Setup Options)

### နည်းလမ်း (A) - Docker Compose ဖြင့် 1-Click Run ရန် (အလွယ်ဆုံး)

VPS သို့မဟုတ် ကွန်ပျူတာပေါ်တွင် Docker ရှိပါက အောက်ပါ command တစ်ခုတည်းဖြင့် PostgreSQL 16 နှင့် POS Server ကို တစ်ပြိုင်နက် run နိုင်ပါသည်:

```bash
docker compose up -d
```
- PostgreSQL Port: `5432`
- POS Server Port: `8081`
- Database Name: `pos_db`
- User: `pos_user`
- Password: `pos_secure_password_123`

---

### နည်းလမ်း (B) - Managed Cloud PostgreSQL (Supabase / Neon / Railway) ဖြင့် သုံးရန်

အခမဲ့ရသော Cloud PostgreSQL Service (ဥပမာ [Neon.tech](https://neon.tech) သို့မဟုတ် [Supabase](https://supabase.com)) တွင် Database တစ်ခု create လုပ်ပြီး ရလာသော Connection String ကို အောက်ပါအတိုင်း ထည့်ပြီး run နိုင်ပါသည်:

```bash
# ၁။ pg package သွင်းပါ
npm install pg

# ၂။ DATABASE_URL သတ်မှတ်ပါ
export DATABASE_URL="postgresql://username:password@ep-cool-db.neon.tech/pos_db?sslmode=require"

# ၃။ Server စတင်ပါ
node server.js
```

---

### နည်းလမ်း (C) - Ubuntu/Debian VPS ပေါ်တွင် PostgreSQL သွင်း၍ သုံးရန်

```bash
# ၁။ PostgreSQL နှင့် Node.js သွင်းပါ
sudo apt update
sudo apt install -y postgresql postgresql-contrib nodejs npm

# ၂။ PostgreSQL ထဲတွင် database နှင့် user ဖန်တီးပါ
sudo -u postgres psql -c "CREATE USER pos_user WITH PASSWORD 'mypassword123';"
sudo -u postgres psql -c "CREATE DATABASE pos_db OWNER pos_user;"
sudo -u postgres psql -c "GRANT ALL PRIVILEGES ON DATABASE pos_db TO pos_user;"

# ၃။ Schema တည်ဆောက်ပါ
sudo -u postgres psql -d pos_db -f schema.sql

# ၄။ Server ကို DATABASE_URL ဖြင့် စတင်ပါ
npm install pg
export DATABASE_URL="postgresql://pos_user:mypassword123@localhost:5432/pos_db"
node server.js

# PM2 ဖြင့် နောက်ကွယ်တွင် အမြဲ run ထားရန်:
npm install -g pm2
pm2 start server.js --name "un-pos" --env DATABASE_URL="postgresql://pos_user:mypassword123@localhost:5432/pos_db"
pm2 save
```

---

## ၃။ Database Tables ဖွဲ့စည်းပုံ (`schema.sql`)

1. **`users`**: အကောင့်များ၊ ဆိုင်အမည်၊ စကားဝှက်၊ ခွင့်ပြုထားသော စက်အရေအတွက် (devices)
2. **`products`**: ကုန်ပစ္စည်းများ၊ စျေးနှုန်း၊ ဝယ်ရင်းစျေး၊ လက်ကျန်စာရင်း၊ SKU၊ Barcode
3. **`initial_stock`**: ကုန်ပစ္စည်း မူလစတင်လက်ကျန် (Stock Reconciliation အတွက်)
4. **`vouchers`**: အရောင်း/အဝယ် ဘောက်ချာများ၊ ဘောက်ချာနံပါတ်၊ စုစုပေါင်းငွေ၊ လျှော့စျေး၊ အခွန်
5. **`voucher_items`**: ဘောက်ချာတစ်ခုချင်းစီတွင် ပါဝင်သော ကုန်ပစ္စည်းစာရင်းနှင့် အရေအတွက်
6. **`customers`**: ဝယ်ယူသူစာရင်း၊ ဖုန်းနံပါတ်၊ အကြွေးစာရင်း၊ အမှတ် (points)
7. **`suppliers`**: ကုန်ရောင်းသူ (Supplier) စာရင်း၊ ပေးရန်ကျန်ငွေ
8. **`payments`**: ငွေပေးချေမှုမှတ်တမ်းများ (Cash / KPay / Wave / AYA / CB / KBZ Pay)
9. **`expenses`**: ကုန်ကျစရိတ်များ
10. **`admin_settings`**: Admin စကားဝှက်နှင့် System Configuration များ

---

## ၄။ Android App တွင် Server ချိတ်ဆက်ရန်

1. UN POS အက်ပ်ကို ဖွင့်ပါ။
2. **Settings (ဆက်တင်)** သို့ သွားပါ။
3. **Cloud / VPS Server Endpoint** နေရာတွင် သင့် Server IP/Domain ကို ရိုက်ထည့်ပါ (ဥပမာ `http://YOUR_SERVER_IP:8081`)။
4. **"Check Status"** သို့မဟုတ် **"Sync Now"** နှိပ်၍ ချိတ်ဆက်မှု အောင်မြင်ကြောင်း စစ်ဆေးနိုင်ပါသည်။
