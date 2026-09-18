# Shopee Clone

Dự án thương mại điện tử gồm hai nửa trong cùng một repo: một frontend React và một backend Spring Boot
được thiết kế lại từ đầu.

Frontend được xây trước nên đang dùng một contract API cũ. Backend là phần đang làm hiện tại:
được thiết kế lại từ đầu theo chuẩn REST thông dụng của hệ sinh thái Java, có tài liệu thiết kế riêng,
và **không** bám theo contract cũ đó. Bước tích hợp sẽ sửa frontend để gọi backend này.

## Trạng thái

> **Hai nửa chưa nối với nhau.** Backend đang được làm cho đủ và đúng trước; phần tích hợp frontend
> là giai đoạn sau. Chạy được từng nửa riêng, nhưng chưa chạy được luồng end-to-end.

| | Trạng thái |
|---|---|
| **Backend** | Đang làm — xong 2/9 bước của lộ trình. Hiện có: đăng ký, đăng nhập, đăng xuất bằng JWT; phân quyền USER/ADMIN theo từng nhóm endpoint; xử lý lỗi theo RFC 9457; CRUD sản phẩm ở mức tối thiểu |
| **Frontend** | Xong phần danh sách sản phẩm, chi tiết sản phẩm, tìm kiếm, lọc, phân trang, đăng nhập/đăng ký. Giỏ hàng và trang cá nhân còn là stub. Vẫn đang gọi contract API cũ, chưa trỏ sang backend này |
| **Tích hợp** | Chưa bắt đầu — danh sách việc cần sửa ở `docs/API_SPEC.md` mục 12 |

## Cấu trúc

```
docs/   Tài liệu thiết kế dùng chung cho cả hai nửa
BE/     Spring Boot — REST API, port 8081
FE/     React + Vite — port 3000
```

## Tech stack

**Backend** — Java 21 · Spring Boot 4.1 (Spring Framework 7, Spring Security 7) · Spring Data JPA /
Hibernate · MySQL · JJWT 0.12 · Bean Validation · Maven

**Frontend** — React 18 · TypeScript 5 · Vite 5 · TailwindCSS 3 · TanStack Query 5 ·
React Hook Form + Yup · React Router 6 · Axios

## Tài liệu thiết kế

Toàn bộ thiết kế nằm trong `docs/`, viết trước khi code và cập nhật liên tục theo tiến độ:

| File | Nội dung |
|---|---|
| [`docs/API_SPEC.md`](docs/API_SPEC.md) | Contract API giữa hai nửa. Mục **1.10** là bảng tổng hợp cả 32 endpoint: ai gọi được (công khai / đã đăng nhập / ADMIN), đã làm chưa, và màn frontend nào gọi tới kèm route của màn đó |
| [`docs/DATABASE_DESIGN.md`](docs/DATABASE_DESIGN.md) | Schema 7 bảng, kèm lý do đằng sau từng quyết định (vì sao giỏ hàng không lưu giá, vì sao đơn hàng phải snapshot, vì sao chưa làm biến thể sản phẩm) |
| [`docs/SYSTEM_DESIGN.md`](docs/SYSTEM_DESIGN.md) | Lộ trình mở rộng khi lượng truy cập tăng — chưa cần cho phiên bản hiện tại |

Vài quyết định thiết kế đáng chú ý ở backend:

- **Response thành công trả DTO trần**, không bọc trong `{ message, data }`. Trạng thái nằm ở HTTP status.
- **Lỗi theo RFC 9457 Problem Details** (`type`, `title`, `status`, `instance`) với
  `Content-Type: application/problem+json`. `type` là chuỗi ổn định để client rẽ nhánh logic —
  ví dụ token hết hạn trả `/errors/token-expired` để frontend tự gọi refresh, khác với đăng nhập sai
  mật khẩu dù cả hai đều là `401`.
- **Lỗi phát sinh trong servlet filter cũng trả đúng định dạng đó.** `@RestControllerAdvice` không với
  tới được tầng filter, nên có `AuthenticationEntryPoint` và `AccessDeniedHandler` riêng.
- **Phân quyền hai tầng**: Spring Security trả lời "đã đăng nhập chưa, role gì"; còn "bản ghi này có
  thuộc về người đang gọi không" là việc của tầng service, và trả `404` chứ không phải `403` để không
  tiết lộ rằng bản ghi đó tồn tại.

## Chạy thử

**Cần có:** JDK 21, MySQL 8, Node 18+

### Backend

```bash
mysql -u root -p -e "CREATE DATABASE ecommerce_db;"

cd BE
# sửa datasource và jwt.secret trong src/main/resources/application.properties
./mvnw spring-boot:run
```

API chạy ở `http://localhost:8081`. Hibernate tự tạo bảng (`ddl-auto=update`).

Thử nhanh:

```bash
curl -X POST http://localhost:8081/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"test@gmail.com","password":"123456"}'
```

Đăng ký luôn tạo tài khoản role `USER`. Muốn có admin thì `UPDATE users SET role='ADMIN' WHERE email=...`.

### Frontend

```bash
cd FE
npm install
npm run dev
```

Chạy ở `http://localhost:3000` — origin này đã được cấu hình CORS ở backend.

## Lộ trình backend

| | Bước | |
|---|---|---|
| ✅ | 1. Nền tảng: xử lý lỗi theo RFC 9457, validation, CORS | |
| ✅ | 2. JWT filter + phân quyền theo nhóm endpoint | |
| ⬜ | 3. Hoàn thiện User, `GET/PUT /api/users/me` | |
| ⬜ | 4. Category CRUD | |
| ⬜ | 5. Nâng cấp Product: đủ field, phân trang, lọc, sắp xếp, soft delete | |
| ⬜ | 6. Giỏ hàng | |
| ⬜ | 7. Đơn hàng: đặt và huỷ | |
| ⬜ | 8. Nhóm API admin + upload ảnh | |
| ⬜ | 9. Refresh token *(tuỳ chọn)* | |

Chi tiết từng bước ở `docs/API_SPEC.md` mục 11.

## Những gì chưa làm

Liệt kê thẳng để khỏi phải đọc code mới biết:

- Frontend và backend **chưa nối**; frontend vẫn đang gọi contract API cũ
- Entity `Product` mới có `name`, `price`, `stock`; chưa phân trang, chưa lọc, chưa soft delete
- Chưa có giỏ hàng, đơn hàng, category ở backend
- Chưa có màn quản trị ở frontend — các endpoint admin hiện gọi bằng Postman
- **Chưa có test tự động.** Mỗi bước được kiểm chứng bằng cách chạy app rồi curl toàn bộ các ca và
  đối chiếu status code; viết test là việc cần làm
- Secret và mật khẩu database còn nằm trong `application.properties`, cần chuyển sang biến môi trường
- Token chưa thu hồi được: đăng xuất chỉ xoá token ở phía client, token cũ vẫn hợp lệ cho tới khi hết
  hạn (24 giờ). Cần refresh token hoặc danh sách chặn — bước 9
