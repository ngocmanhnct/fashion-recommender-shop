# Bước 5 Tuần 4 — image cho frontend React.
# 2 giai đoạn: (1) Node build ra file tĩnh trong dist/, (2) Nginx phục vụ đúng các file đó.

# ---- Giai đoạn 1: build ----
FROM node:24-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
# Vite "đóng cứng" biến VITE_* vào file JS lúc build (không đọc lúc chạy như Spring Boot),
# nên địa chỉ backend phải truyền vào đây qua build arg.
ARG VITE_API_BASE_URL=http://localhost:8080
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build

# ---- Giai đoạn 2: chạy ----
FROM nginx:1.28-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
