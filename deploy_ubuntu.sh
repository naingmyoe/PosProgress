#!/usr/bin/env bash
# ====================================================================
# UN POS - Automated Ubuntu VPS Deployment Script
# Automatically installs Node.js 20, PostgreSQL, sets up database,
# installs dependencies, and runs server 24/7 with PM2.
# Supported OS: Ubuntu 20.04 / 22.04 / 24.04 LTS (Debian compatible)
# ====================================================================

set -e

# Colors for terminal output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

echo -e "${PURPLE}====================================================================${NC}"
echo -e "${CYAN}        UN POS - Ubuntu VPS Automated Deployment Setup${NC}"
echo -e "${PURPLE}====================================================================${NC}"

# Check if script is run as root
if [ "$EUID" -ne 0 ]; then
  echo -e "${RED}[ERROR] ဤ script ကို root user ဖြင့် run ပေးပါ (Please run as root or with sudo)${NC}"
  echo -e "Usage: sudo bash deploy_ubuntu.sh"
  exit 1
fi

APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$APP_DIR"

# 1. Detect Public IP
echo -e "\n${YELLOW}[1/7] Public IP စစ်ဆေးနေပါသည်...${NC}"
SERVER_IP=$(curl -s --max-time 5 https://api.ipify.org || curl -s --max-time 5 https://ifconfig.me || echo "YOUR_VPS_IP")
echo -e "${GREEN}✓ VPS IP: ${SERVER_IP}${NC}"

# 2. Update System Packages
echo -e "\n${YELLOW}[2/7] Ubuntu Packages များ Update လုပ်နေပါသည်...${NC}"
apt update -y
apt install -y curl wget git ufw lsb-release gnupg software-properties-common

# 3. Install Node.js 20 LTS
echo -e "\n${YELLOW}[3/7] Node.js 20 LTS နှင့် npm သွင်းယူနေပါသည်...${NC}"
if ! command -v node >/dev/null 2>&1 || [ "$(node -v | cut -d'.' -f1 | tr -d 'v')" -lt 18 ]; then
  curl -fsSL https://deb.nodesource.com/setup_20.x | bash -
  apt install -y nodejs
fi
echo -e "${GREEN}✓ Node.js $(node -v) and npm $(npm -v) installed.${NC}"

# 4. Install & Configure PostgreSQL
echo -e "\n${YELLOW}[4/7] PostgreSQL Database သွင်းယူပြီး Configure လုပ်နေပါသည်...${NC}"
apt install -y postgresql postgresql-contrib

systemctl start postgresql
systemctl enable postgresql

DB_NAME="pos_db"
DB_USER="pos_user"
DB_PASS="pos_$(openssl rand -hex 6)" # Generate secure random password

# Create User and Database if not exists
sudo -u postgres psql -tc "SELECT 1 FROM pg_roles WHERE rolname='${DB_USER}'" | grep -q 1 || \
sudo -u postgres psql -c "CREATE USER ${DB_USER} WITH PASSWORD '${DB_PASS}';"

sudo -u postgres psql -tc "SELECT 1 FROM pg_database WHERE datname='${DB_NAME}'" | grep -q 1 || \
sudo -u postgres psql -c "CREATE DATABASE ${DB_NAME} OWNER ${DB_USER};"

sudo -u postgres psql -c "GRANT ALL PRIVILEGES ON DATABASE ${DB_NAME} TO ${DB_USER};"
sudo -u postgres psql -c "ALTER USER ${DB_USER} WITH SUPERUSER;"

echo -e "${GREEN}✓ PostgreSQL Database '${DB_NAME}' and User '${DB_USER}' ready.${NC}"

# Apply schema.sql
if [ -f "schema.sql" ]; then
  echo -e "${YELLOW}  Schema တည်ဆောက်နေပါသည် (Applying schema.sql)...${NC}"
  PGPASSWORD="${DB_PASS}" psql -h localhost -U "${DB_USER}" -d "${DB_NAME}" -f schema.sql >/dev/null 2>&1 || \
  sudo -u postgres psql -d "${DB_NAME}" -f schema.sql >/dev/null 2>&1
  echo -e "${GREEN}✓ PostgreSQL Schema initialized successfully.${NC}"
fi

# 5. Install Node.js Dependencies
echo -e "\n${YELLOW}[5/7] Backend dependencies (pg) သွင်းယူနေပါသည်...${NC}"
npm install --production

# Write .env configuration file
DATABASE_URL="postgresql://${DB_USER}:${DB_PASS}@localhost:5432/${DB_NAME}"
cat <<EOF > .env
PORT=8081
DATABASE_URL=${DATABASE_URL}
NODE_ENV=production
EOF

echo -e "${GREEN}✓ .env configuration created with PostgreSQL connection string.${NC}"

# 6. Setup Firewall (UFW)
echo -e "\n${YELLOW}[6/7] Firewall Port (8081 & 22) ဖွင့်ပေးနေပါသည်...${NC}"
ufw allow 22/tcp >/dev/null 2>&1 || true
ufw allow 8081/tcp >/dev/null 2>&1 || true
echo -e "${GREEN}✓ Port 8081 allowed.${NC}"

# 7. Start Server with PM2
echo -e "\n${YELLOW}[7/7] PM2 Process Manager ဖြင့် Server ကို စတင် run နေပါသည်...${NC}"
npm install -g pm2

# Stop existing if any
pm2 stop un-pos >/dev/null 2>&1 || true
pm2 delete un-pos >/dev/null 2>&1 || true

# Start with environment variables
pm2 start server.js --name "un-pos" --env production --time
pm2 save
pm2 startup systemd -u root --hp /root >/dev/null 2>&1 || true

# Wait 2 seconds and check status
sleep 2

echo -e "\n${GREEN}====================================================================${NC}"
echo -e "${GREEN}      🎉 UN POS Ubuntu VPS Deployment အောင်မြင်စွာ ပြီးဆုံးပါပြီ!${NC}"
echo -e "${GREEN}====================================================================${NC}"
echo -e ""
echo -e "${CYAN}▶ Backend Server URL:${NC}    http://${SERVER_IP}:8081"
echo -e "${CYAN}▶ Admin Dashboard:${NC}       http://${SERVER_IP}:8081/admin"
echo -e "${CYAN}▶ Admin စကားဝှက်:${NC}         admin1234 (admin.html တွင် ပြောင်းလဲနိုင်သည်)"
echo -e ""
echo -e "${YELLOW}▶ PostgreSQL Credentials:${NC}"
echo -e "  Database: ${DB_NAME}"
echo -e "  Username: ${DB_USER}"
echo -e "  Password: ${DB_PASS}"
echo -e "  URL:      ${DATABASE_URL}"
echo -e ""
echo -e "${PURPLE}▶ Android POS App တွင် ထည့်သွင်းရမည့် Server Endpoint:${NC}"
echo -e "  👉 ${BLUE}http://${SERVER_IP}:8081${NC}"
echo -e "  (App ထဲရှိ Settings > Cloud / VPS Server Endpoint တွင် ထည့်ပါ)"
echo -e ""
echo -e "${YELLOW}အသုံးဝင်သော Commands များ:${NC}"
echo -e "  - Server အခြေအနေကြည့်ရန်:   ${CYAN}pm2 status${NC}"
echo -e "  - Real-time Log ကြည့်ရန်:    ${CYAN}pm2 logs un-pos${NC}"
echo -e "  - Server Restart ချရန်:     ${CYAN}pm2 restart un-pos${NC}"
echo -e "  - Database Backup ယူရန်:     ${CYAN}bash backup_db.sh${NC}"
echo -e "${PURPLE}====================================================================${NC}"
