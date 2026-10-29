# Image chạy FE: build ra file tĩnh bằng Node, rồi phục vụ chúng bằng nginx. Image cuối không có Node.

FROM node:22-alpine AS build
WORKDIR /app
# Cài package trước khi chép mã nguồn, để chỉ sửa code thì layer node_modules được dùng lại.
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.28-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
