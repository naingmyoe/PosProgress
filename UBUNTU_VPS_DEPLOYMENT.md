# UN POS - Ubuntu VPS ပေါ်တွင် အဆင့်ဆင့် တင်နည်းလမ်းညွှန် (Complete Guide)

ဤလမ်းညွှန်သည် **Ubuntu 20.04 / 22.04 / 24.04 LTS** VPS Server ပေါ်တွင် **PostgreSQL Database** နှင့် **UN POS Backend Server** ကို အလွယ်ကူဆုံးနှင့် အတိကျဆုံး အဆင့်ဆင့် တင်သွင်း run နည်း ဖြစ်ပါသည်။

---

## ၁။ လိုအပ်ချက်များ (Requirements)
- **VPS Server**: Ubuntu OS တင်ထားသော မည်သည့် VPS မဆို (ဥပမာ - DigitalOcean, Hetzner, Linode, AWS Lightsail, Contabo, Vultr)
- **အနည်းဆုံး Server Specs**: 1 vCPU, 1 GB RAM, 20 GB SSD (အလုံအလောက် ဖြစ်ပါသည်)
- **IP Address**: သင့် VPS ၏ Public IP (ဥပမာ `159.89.123.45`)

---

## ၂။ အဆင့်ဆင့် လုပ်ဆောင်ရန် နည်းလမ်း (Step-by-Step)

### အဆင့် (၁) - VPS ထဲသို့ Terminal / Command Prompt မှ ဝင်ရောက်ပါ (SSH Login)

သင့်ကွန်ပျူတာ၏ Terminal (သို့မဟုတ် Windows ရှိ PowerShell / PuTTY) တွင် အောက်ပါ command ကို ရိုက်ပါ:

```bash
ssh root@YOUR_VPS_IP
```
*(ဥပမာ: `ssh root@159.89.123.45`)*  
Password တောင်းပါက သင့် VPS password ကို ရိုက်ထည့်ပါ။

---

### အဆင့် (၂) - ပရောဂျက်ဖိုင်များကို VPS ပေါ်သို့ ကူးယူပါ (Download Project)

VPS ထဲသို့ ရောက်ရှိပါက အောက်ပါအတိုင်း GitHub မှ တိုက်ရိုက် clone ယူပါ:

```bash
git clone https://github.com/naingmyoe/PosOnlineCenter.git /var/www/un-pos
cd /var/www/un-pos
```

---

### အဆင့် (၃) - 1-Click Automated Setup Script ကို Run ပါ

ကျွန်ုပ်တို့ ကြိုတင်ပြင်ဆင်ပေးထားသော **`deploy_ubuntu.sh`** ကို အောက်ပါအတိုင်း run လိုက်ပါ:

```bash
bash deploy_ubuntu.sh
```

**ဤ script မှ အောက်ပါတို့ကို အလိုအလျောက် ၁၀၀% အပြည့်အစုံ လုပ်ဆောင်သွားပါမည်:**
1. Ubuntu packages များကို update လုပ်ခြင်း
2. **Node.js 20 LTS** နှင့် **npm** သွင်းယူခြင်း
3. **PostgreSQL Database** ကို သွင်းယူပြီး `pos_db` database နှင့် `pos_user` ကို လုံခြုံသော password ဖြင့် တည်ဆောက်ပေးခြင်း
4. **`schema.sql`** ရှိ ဇယားများ (users, products, vouchers, customers စသည်) အားလုံးကို အလိုအလျောက် create လုပ်ပေးခြင်း
5. Node.js backend dependencies (`pg` driver) သွင်းယူခြင်း
6. Firewall (UFW) တွင် **Port 8081** ကို ဖွင့်ပေးခြင်း
7. **PM2 Process Manager** ဖြင့် Server ကို နောက်ကွယ်တွင် ၂၄ နာရီ မရပ်မနား run ထားပေးပြီး VPS reboot ဖြစ်သွားပါကလည်း auto-restart ပြန်ဖြစ်အောင် စီစဉ်ပေးခြင်း

ပြီးဆုံးပါက Terminal မျက်နှာပြင်တွင် Server URL နှင့် ချိတ်ဆက်မှု အချက်အလက်များ ပေါ်လာပါမည်။

---

### အဆင့် (၄) - Web Admin Dashboard စမ်းသပ်ဝင်ရောက်ပါ

ကွန်ပျူတာ သို့မဟုတ် ဖုန်း browser (Chrome / Safari) တွင် အောက်ပါအတိုင်း ရိုက်ထည့်ပါ:

```
http://YOUR_VPS_IP:8081/admin
```
- **Default Admin Password**: `admin1234`
- Dashboard ထဲတွင် စာရင်းသွင်းထားသော အကောင့်များ၊ အရောင်းစာရင်း၊ ဘောက်ချာများ၊ စက်အရေအတွက် (Devices) များကို တိုက်ရိုက် ကြည့်ရှု/ပြင်ဆင်နိုင်ပါသည်။

---

### အဆင့် (၅) - Android POS App တွင် Server URL ချိတ်ဆက်ပါ

1. ဖုန်းထဲရှိ **UN POS** အက်ပ်ကို ဖွင့်ပါ။
2. အောက်ခြေ သို့မဟုတ် Menu ထဲရှိ **Settings (ဆက်တင်)** သို့ သွားပါ။
3. **"Cloud / VPS Server Endpoint"** နေရာရှိ **Change Server URL** ကို နှိပ်ပါ။
4. သင့် VPS ၏ URL ကို ထည့်ပါ:
   ```
   http://YOUR_VPS_IP:8081
   ```
   *(ဥပမာ: `http://159.89.123.45:8081`)*
5. **Save** နှိပ်ပါ။
6. **"Cloud သို့ ဒေတာများ ပို့မည် (Sync Now)"** ကို နှိပ်လိုက်သည်နှင့် ဖုန်းပေါ်ရှိ အရောင်းဒေတာအားလုံး VPS ရှိ **PostgreSQL Database** ထဲသို့ ချက်ချင်း အလိုအလျောက် သိမ်းဆည်းသွားပါမည်။

---

## ၃။ အသုံးဝင်သော VPS စီမံခန့်ခွဲမှု Commands များ (Daily Commands)

Server စစ်ဆေးရန် သို့မဟုတ် ပြင်ဆင်ရန် အောက်ပါ command များကို အသုံးပြုနိုင်ပါသည်:

```bash
# ၁။ Server Run နေမှု အခြေအနေ စစ်ဆေးရန်
pm2 status

# ၂။ Real-time Log (အလုပ်လုပ်နေမှု မှတ်တမ်း) ကြည့်ရှုရန်
pm2 logs un-pos

# ၃။ Server ကို Restart ချရန်
pm2 restart un-pos

# ၄။ Server ကို ရပ်ထားရန် / ပြန်စတင်ရန်
pm2 stop un-pos
pm2 start un-pos

# ၅။ PostgreSQL Database ကို Backup ယူရန်
bash backup_db.sh
```

---

## ၄။ (Optional) Domain အမည် နှင့် အခမဲ့ HTTPS/SSL (Let's Encrypt) တပ်ဆင်လိုပါက

Domain အမည် (ဥပမာ `pos.yourdomain.com`) ဖြင့် အသုံးပြုလိုပါက Nginx Reverse Proxy ဖြင့် အောက်ပါအတိုင်း တပ်ဆင်နိုင်ပါသည်:

```bash
# Nginx နှင့် Certbot သွင်းပါ
sudo apt install -y nginx certbot python3-certbot-nginx

# Nginx configuration ဖန်တီးပါ
sudo nano /etc/nginx/sites-available/unpos
```

အောက်ပါ code ကို ထည့်သွင်းပြီး သိမ်းပါ (`Ctrl+O`, `Enter`, `Ctrl+X`):
```nginx
server {
    listen 80;
    server_name pos.yourdomain.com;

    location / {
        proxy_pass http://localhost:8081;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_cache_bypass $http_upgrade;
    }
}
```

Nginx ကို enable လုပ်ပြီး အခမဲ့ SSL ရယူပါ:
```bash
sudo ln -s /etc/nginx/sites-available/unpos /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl restart nginx
sudo certbot --nginx -d pos.yourdomain.com
```

ပြီးပါက Android App ထဲတွင် `https://pos.yourdomain.com` ဟု လုံခြုံစိတ်ချစွာ အသုံးပြုနိုင်ပါပြီ။
