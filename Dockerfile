FROM node:22-alpine
WORKDIR /app
COPY package*.json ./
RUN npm ci --only=production
COPY proxy-server.js ./
EXPOSE 8080
CMD ["node", "proxy-server.js"]
