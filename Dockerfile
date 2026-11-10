FROM node:20-alpine

WORKDIR /app

# Copy dependency files
COPY package*.json ./
RUN npm install --production

# Copy server files
COPY server.js ./
COPY db_postgres.js ./
COPY schema.sql ./
COPY admin.html ./
COPY pos_db.json* ./

EXPOSE 8081

CMD ["node", "server.js"]
