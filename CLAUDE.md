# Shopee Clone — monorepo

```
docs/                thiết kế dùng chung cho cả hai nửa — ĐỌC TRƯỚC KHI SỬA BẤT KỲ ĐÂU
BE/                  Spring Boot 4.1 / Java 21 / MySQL / Flyway — port 8081
FE/                  React + TypeScript + Vite — port 3000
docker-compose.yml   MySQL + BE + FE (Dockerfile ở BE/ và FE/)
```

## Đọc `docs/` trước

| File | Nội dung |
|---|---|
| `docs/API_SPEC.md` | Contract API giữa BE và FE. **§1.10 là bảng quyền chính thức**: cả 29 endpoint, ai gọi được, đã làm chưa, và **màn FE nào gọi tới** kèm route của màn đó. §10 là tình trạng hiện thực từng hạng mục. §12 ghi cách FE nối với API. |
| `docs/DATABASE_DESIGN.md` | Schema v1 (8 bảng) và lý do đằng sau từng quyết định; mục 9 là Flyway và dữ liệu mẫu |
| `docs/SYSTEM_DESIGN.md` | Lộ trình mở rộng khi lượng truy cập tăng |

## Trạng thái

BE đủ 29/29 endpoint (cột `BE` ở `docs/API_SPEC.md` §1.10), FE người mua xong, chưa có màn admin. §10 đối chiếu
từng hạng mục với code; cột "Giới hạn / việc tiếp theo" là phần còn thiếu. Đừng suy ra trạng thái từ code: thêm hay
đổi gì thì cập nhật §1.10 và §10 cùng lúc. Docs mô tả repo hiện tại — không ghi lịch sử làm ("ở bước N", ngày làm).

## Thứ tự làm việc

**`docs/API_SPEC.md` là contract chung: đổi API thì sửa spec và BE trước, rồi FE theo.** FE gọi đúng contract đó
(DTO trần, field `camelCase`, lỗi `ProblemDetail`); cách nối ở §12. Chưa có màn admin (§1.10 ⁶). Thêm hay đổi API
thì cập nhật cột "Màn FE" ở §1.10 cùng lúc.

## Quy ước dễ sai

- **Response thành công: trả DTO trần**, không bọc envelope. Response lỗi: **RFC 9457 `ProblemDetail`**
  (`type`, `title`, `status`, `instance`), `Content-Type: application/problem+json`.
- `type` là chuỗi để **máy** so sánh, đặt rồi coi như bất biến. `title` là câu tiếng Việt cho **người**.
  Bảng `type` đầy đủ ở `docs/API_SPEC.md` §1.6.
- **Thêm endpoint thì phải cập nhật cả ba chỗ**: controller, rule trong `BE/.../config/SecurityConfig.java`,
  và một dòng trong `docs/API_SPEC.md` §1.10. Thiếu rule thì endpoint rơi vào
  `.anyRequest().authenticated()` — an toàn nhưng sẽ trả `401` khó hiểu.
- **Phân quyền có hai tầng.** `SecurityConfig` chỉ trả lời "đã đăng nhập chưa, role gì".
  Còn "bản ghi này có thuộc về user đang gọi không" là việc của tầng service, và trả **`404` chứ không
  phải `403`**. Xem chú thích ² ở §1.10.
- Project dùng **Jackson 3** (`tools.jackson.databind`), **không** phải `com.fasterxml.jackson.databind`.
  Jackson 2 có trên classpath nhưng chỉ ở scope `runtime` do `jjwt-jackson` kéo vào. Riêng annotation
  (`@JsonProperty`, `@JsonIgnore`…) Jackson 3 vẫn để ở `com.fasterxml.jackson.annotation` — import đó là đúng.
- `HttpStatus.UNPROCESSABLE_CONTENT` (không dùng `UNPROCESSABLE_ENTITY` — đã deprecated ở Spring 7).
- **Service gọi service của module khác, không gọi repository của nó.** Ví dụ `OrderService` trừ kho qua
  `ProductService.decreaseStock()`, không inject `ProductRepository`. Hai ngoại lệ đã biết, kèm lý do, ở
  `docs/SYSTEM_DESIGN.md` Level 2.
- `@Transactional` lấy từ `org.springframework.transaction.annotation`, **không** phải
  `jakarta.transaction` — IDE hay tự import nhầm. Khoá dòng thì dùng `@Lock(LockModeType...)`, không viết
  `FOR UPDATE` trong chuỗi JPQL (JPQL không có cú pháp đó, app sẽ không khởi động).
- **`spring.jpa.open-in-view=false`**: DTO phải dựng xong bên trong method `@Transactional` của service.
  Quên `@Transactional` ở một hàm đọc có quan hệ lazy thì nhận `LazyInitializationException` → `500`.
- **Secret không bao giờ ghi vào `application.properties`** — file đó chỉ có `${JWT_SECRET}`, `${DB_PASSWORD}`,
  không giá trị mặc định. Giá trị thật ở `BE/.env` (chạy `./mvnw`) và `.env` ở gốc repo (chạy Docker), cả hai đều
  gitignore. Không in nội dung `.env` ra terminal hay chat.
- **Schema do Flyway quản lý** (`BE/src/main/resources/db/migration`), Hibernate chỉ `ddl-auto=validate`. Đổi schema
  = thêm file `V<n>__mo_ta.sql` mới (kèm sửa entity), **không bao giờ sửa file đã chạy**: checksum lệch thì app
  không khởi động. `validate` bắt entity lệch bảng/cột/kiểu, nhưng **không** kiểm index hay `CHECK` — thêm giá trị
  enum mà quên migration `DROP/ADD CHECK` thì chỉ lỗi `500` lúc ghi (`docs/DATABASE_DESIGN.md` §3.6).
- **Dữ liệu mẫu là migration riêng ở `db/seed`, chỉ chạy ở profile `demo`** (version xen kẽ: `V1_1`, sau này
  `V2_1`...). `./mvnw spring-boot:run` tự bật `demo` (thuộc tính `spring-boot.run.profiles` trong `pom.xml`), test
  cũng `@ActiveProfiles("demo")`. DB dev đã nạp seed thì luôn chạy với `demo`: khi đã có `V2`, thiếu profile là
  Flyway báo migration 1.1 "not resolved locally" và app không khởi động. Seed viết `${image-base-url}` thay cho
  địa chỉ ảnh.
- **FE: `priceBeforeDiscount` có thể `null`** (sản phẩm và dòng đơn hàng; `null` = không giảm giá). Dùng
  `hasDiscount()` ở `FE/src/utils/utils.ts` trước khi hiện giá gạch hay `-x%`: `formatCurrency(null)` ra `₫0` còn
  `rateSale` ra `-Infinity`.
- **FE: upload file phải khai `Content-Type: multipart/form-data` ngay ở request.** Instance axios đặt sẵn
  `application/json`, gặp header đó axios biến `FormData` thành chuỗi JSON và BE trả `415`.
- **FE: cùng một route mà khác tham số thì React không dựng lại component**, nên state cũ (số lượng đã chọn, vị
  trí slider ảnh) bị mang sang sản phẩm mới. Bọc bằng component có `key={tham số}`, xem `ProductDetail`.

## Chạy

```bash
# Cả hệ thống bằng Docker (MySQL + BE + FE, .env ở gốc repo — khác BE/.env)
cp .env.example .env && docker compose up --build

# BE
cd BE && cp .env.example .env            # một lần: điền DB_PASSWORD, JWT_SECRET
cd BE && ./mvnw spring-boot:run          # cần JAVA_HOME trỏ tới JDK 21; profile demo, DB tự tạo + migrate
cd BE && ./mvnw clean compile            # kiểm tra compile thật
cd BE && ./mvnw test                     # cần MySQL dev đang chạy: test khởi động app trên shopee_db, profile demo

# FE
cd FE && npm run dev
cd FE && npm run build                   # tsc -b + vite build
cd FE && npm run lint
```

> Compiler của VSCode Java extension vẫn sinh `.class` cho code sai (thay thân method bằng
> `throw new Error(...)`), nên **file `.class` mới không chứng minh code compile được**.
> Dùng `./mvnw clean compile` khi cần chắc chắn.

> `npx tsc --noEmit` ở gốc `FE/` **không kiểm file nào** (`tsconfig.json` có `"files": []`, chỉ trỏ tới hai
> project con), nên im lặng không có nghĩa là sạch. Dùng `npm run build` hoặc `npx tsc -p tsconfig.app.json --noEmit`.

## Cách làm việc mà chủ repo muốn

Mục tiêu chính là một sản phẩm hoàn chỉnh để demo; học Java là mục tiêu phụ. Claude viết code (cả BE lẫn FE), rồi
**giải thích kiến thức, kỹ thuật, công nghệ đã dùng**: sửa file nào, vì sao chọn cách đó, cái bẫy nó tránh. Khi
làm xong một việc thì **chạy thật và curl kiểm chứng**, in bảng kết quả, đừng chỉ báo "đã xong".
