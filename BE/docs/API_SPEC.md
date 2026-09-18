# API Spec — Ecommerce BE (Shopee Clone)

> **Bộ tài liệu thiết kế:**
> - `API_SPEC.md` *(tài liệu này)* — contract API giữa BE và FE
> - [`DATABASE_DESIGN.md`](DATABASE_DESIGN.md) — schema database và các nguyên tắc thiết kế
> - [`SYSTEM_DESIGN.md`](SYSTEM_DESIGN.md) — lộ trình mở rộng hệ thống
>
> **Nguyên tắc thiết kế:** BE được thiết kế theo chuẩn REST thông dụng của hệ sinh thái Java/SQL, **không**
> bám theo contract của FE hiện tại. FE sẽ được sửa lại cho khớp với spec này khi tích hợp — danh sách
> việc cần sửa nằm ở [mục 12](#12-danh-sách-việc-cần-sửa-ở-fe-khi-tích-hợp).
>
> **Thứ tự làm việc:** làm BE cho đúng và đầy đủ trước; FE sửa sau.

---

## 1. Quy ước chung

### 1.1. Base URL và prefix `/api`

| Môi trường | URL |
|---|---|
| Local | `http://localhost:8081/api` |

Mọi endpoint đều nằm dưới prefix `/api`. Lý do dùng prefix thay vì đặt ở root:

- Tách bạch API với tài nguyên tĩnh khi cùng một server phục vụ cả hai (ảnh upload nằm ở `/images/**`,
  không lẫn với `/api/**`).
- Khi deploy sau nginx chỉ cần một rule `location /api/ { proxy_pass http://localhost:8081; }`.
- Dễ mở rộng sang versioning sau này (`/api/v1`, `/api/v2`) nếu cần.

**Ba cách thêm prefix trong Spring Boot:**

| Cách | Code | Ảnh hưởng |
|---|---|---|
| **Ghi thẳng vào từng controller** (khuyến nghị) | `@RequestMapping("/api/products")` | Rõ ràng, nhìn controller là biết full path |
| Cấu hình tập trung | `configurer.addPathPrefix("/api", HandlerTypePredicate.forAnnotation(RestController.class))` trong `WebMvcConfigurer` | Chỉ áp cho `@RestController`, file tĩnh vẫn ở root |
| `server.servlet.context-path=/api` | 1 dòng trong properties | Prefix **mọi thứ**, kể cả file tĩnh và trang lỗi → ảnh upload cũng bị đẩy vào `/api` |

Giai đoạn học nên dùng cách 1 cho dễ theo dõi.

### 1.2. Hình dạng response

**Không bọc response thành công trong envelope.** Body *chính là* dữ liệu; kết quả của request do HTTP
status code diễn đạt.

| Trường hợp | Status | Body |
|---|---|---|
| Trả về một tài nguyên | `200` | chính object đó |
| Tạo mới một tài nguyên | `201` | object vừa tạo |
| Danh sách có phân trang | `200` | `{ items, page, limit, totalItems, totalPages }` |
| Danh sách ngắn, không phân trang | `200` | mảng JSON trực tiếp |
| Không có gì để trả về | `204` | rỗng |
| Lỗi | `4xx` / `5xx` | Problem Details — xem mục 1.6 |

```json
GET /api/products/12  →  200
{ "id": "12", "name": "Áo thun nam cổ tròn", "price": 79000 }
```

**Vì sao bỏ envelope `{message, data}`:** một chuỗi kiểu `"Lấy sản phẩm thành công"` được sinh ra,
serialize, truyền qua mạng trên **mọi** response — rồi không FE nào hiển thị nó. Status `200` đã nói
"thành công" rồi. Còn khi cần hiện thông báo sau một thao tác, FE tự biết mình vừa làm gì nên tự sinh câu
chữ được, không cần server nhắc.

**Vỏ chỉ dùng khi nó mang thông tin thật.** Danh sách phân trang có vỏ, vì `totalPages` là thứ FE không
thể tự suy ra:

```json
GET /api/products?page=1  →  200
{
  "items": [ { "...": "object Product" } ],
  "page": 1,
  "limit": 20,
  "totalItems": 97,
  "totalPages": 5
}
```

Hình dạng này dùng lại cho **mọi** danh sách phân trang (sản phẩm, đơn hàng, user) — FE chỉ cần một kiểu
`Paginated<T>` duy nhất.

### 1.3. Định danh: `id` dạng string

Khoá chính trong response đặt tên `id`, kiểu **string**.

```java
// Entity giữ Long cho DB, DTO trả string cho FE
public record ProductResponse(String id, String name, ...) { }
```

Vì sao trả string mà không trả số? JavaScript dùng `number` 64-bit dấu phẩy động, chỉ biểu diễn chính xác
số nguyên đến 2^53. ID tự tăng thì còn lâu mới chạm ngưỡng đó, nhưng trả string là thói quen tốt: sau này
đổi sang UUID hay snowflake ID cũng không phải sửa FE.

FE dùng `id` để build URL sản phẩm dạng `ten-san-pham-i.<id>` rồi tách ngược ra khi vào trang chi tiết,
nên `id` không được chứa dấu chấm. ID số tự tăng hoàn toàn thoả mãn.

### 1.4. Xác thực: header `Authorization: Bearer <token>`

```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

Đây là chuẩn RFC 6750, được Postman, Swagger UI và mọi thư viện HTTP hỗ trợ sẵn.

> Nên viết filter chấp nhận **cả hai** dạng (có và không có tiền tố) để đỡ vướng lúc test:
> ```java
> String header = request.getHeader("Authorization");
> String token = (header != null && header.startsWith("Bearer ")) ? header.substring(7) : header;
> ```

### 1.5. Quy ước đặt tên

| Loại | Quy ước | Ví dụ |
|---|---|---|
| Field trong JSON | **camelCase** | `priceBeforeDiscount`, `orderCode`, `shippingFee` |
| Đường dẫn URL | **kebab-case** | `/api/cart/items`, `/api/users/me/avatar` |
| Giá trị enum | **UPPER_SNAKE_CASE** | `PENDING`, `BANK_TRANSFER` |

camelCase là mặc định của Jackson và đúng convention Java — không cần cấu hình gì thêm.

### 1.6. Mã lỗi HTTP và hình dạng lỗi

**Status khi thành công**

| Status | Khi nào |
|---|---|
| `200` | GET, PUT, và POST mang tính hành động (login) |
| `201` | POST tạo ra tài nguyên mới (đăng ký, đặt hàng, admin tạo sản phẩm) |
| `204` | Xong việc nhưng không có gì để trả (DELETE, logout) |

**Status khi lỗi**

| Status | Khi nào dùng |
|---|---|
| `401` | Chưa đăng nhập, token sai/hết hạn, **hoặc sai email/mật khẩu lúc login** |
| `403` | Đã đăng nhập nhưng không đủ quyền |
| `404` | Không tìm thấy tài nguyên |
| `409` | Xung đột trạng thái (huỷ đơn đã giao, hết hàng khi đặt) |
| `422` | **Lỗi validate dữ liệu đầu vào** |
| `500` | Lỗi server |

**Phân biệt 401 / 422 / 409** — chỗ này hay nhầm:
- `422` = dữ liệu gửi lên **sai định dạng** (email sai cú pháp, mật khẩu dưới 6 ký tự, số lượng âm).
- `401` = **danh tính không đúng** (sai mật khẩu, token hỏng). Dữ liệu hợp lệ, chỉ là không đúng người.
- `409` = dữ liệu hợp lệ, danh tính đúng, nhưng **trạng thái hệ thống không cho phép** (sản phẩm vừa hết
  hàng, đơn đã giao rồi nên không huỷ được).

#### Hình dạng lỗi: RFC 9457 Problem Details

Mọi response lỗi dùng chuẩn RFC 9457 với `Content-Type: application/problem+json`. Spring có sẵn class
`org.springframework.http.ProblemDetail` cài đặt chuẩn này — **không cần tự viết class nào**.

```json
POST /api/auth/login  →  401
{
  "type": "/errors/invalid-credentials",
  "title": "Email hoặc mật khẩu không đúng",
  "status": 401
}
```

| Field | Bắt buộc | Viết cho ai | Ý nghĩa |
|---|---|---|---|
| `type` | ✅ | **máy** | Định danh loại lỗi; FE so sánh chuỗi này để rẽ nhánh logic |
| `title` | ✅ | **người** | Câu tiếng Việt, FE đem hiện lên toast |
| `status` | ✅ | cả hai | Lặp lại HTTP status cho tiện đọc log |
| `detail` | tuỳ | người | Mô tả cụ thể hơn `title` khi cần |
| `errors` | chỉ `422` | máy | Phần mở rộng: map `tênField → thông báo` |

`type` và `title` phục vụ hai người đọc khác nhau, đừng lẫn lộn: `title` sửa câu chữ lúc nào cũng được;
`type` thì đặt rồi coi như bất biến, vì FE so sánh đúng từng ký tự.

Vì sao cần `type`: nhiều tình huống rất khác nhau đều trả về cùng status `401`. Token hết hạn thì FE phải
âm thầm gọi refresh rồi retry; sai mật khẩu lúc login thì phải hiện lỗi cho người dùng. Chỉ nhìn status
code không phân biệt được hai ca đó.

**Bảng `type` dùng trong dự án này**

| Status | `type` | Khi nào |
|---|---|---|
| `401` | `/errors/invalid-credentials` | Sai email hoặc mật khẩu lúc login |
| `401` | `/errors/token-invalid` | Token hỏng, sai chữ ký, hoặc thiếu |
| `401` | `/errors/token-expired` | Token hết hạn — FE dùng để kích hoạt refresh |
| `403` | `/errors/forbidden` | Đã đăng nhập nhưng không đủ quyền |
| `404` | `/errors/not-found` | Không tìm thấy tài nguyên |
| `409` | `/errors/conflict` | Xung đột trạng thái |
| `422` | `/errors/validation` | Dữ liệu đầu vào không hợp lệ |
| `500` | `/errors/internal-server-error` | Lỗi không lường trước |

**Lỗi 422** có thêm field mở rộng `errors`, key phải **trùng tên field** FE gửi lên:

```json
POST /api/auth/register  →  422
{
  "type": "/errors/validation",
  "title": "Dữ liệu không hợp lệ",
  "status": 422,
  "errors": {
    "email": "Email đã tồn tại",
    "password": "Độ dài từ 6-160 kí tự"
  }
}
```

### 1.7. Rule validate

BE **luôn phải validate** dù FE đã validate (không bao giờ tin client):

| Field | Rule | Thông báo mẫu |
|---|---|---|
| `email` | Bắt buộc, đúng định dạng email, độ dài 5–160 | `Email không đúng định dạng` |
| `password` | Bắt buộc, độ dài 6–160 | `Độ dài từ 6-160 kí tự` |
| `quantity` | Số nguyên ≥ 1, ≤ tồn kho | `Số lượng vượt quá số lượng sản phẩm` |
| `recipientPhone` | Bắt buộc khi đặt hàng, 10–11 chữ số | `Số điện thoại không hợp lệ` |

### 1.8. Kiểu dữ liệu

| Loại | Quy ước | Ví dụ |
|---|---|---|
| Thời gian | Chuỗi ISO-8601 UTC | `"2026-09-16T08:30:00.000Z"` |
| Tiền | Số nguyên VND, **không** có phần thập phân | `3190000` |
| Ảnh | URL đầy đủ | `"http://localhost:8081/images/abc.jpg"` |

### 1.9. CORS

FE chạy ở `http://localhost:3000`, BE ở `http://localhost:8081` → khác origin, cần bật CORS trong
`SecurityConfig`: cho phép origin `http://localhost:3000`, method `GET, POST, PUT, DELETE, OPTIONS`,
header `Authorization` và `Content-Type`.

---

## 2. Data models

### 2.1. User

```json
{
  "id": "1",
  "roles": ["USER"],
  "email": "duy@gmail.com",
  "name": "Nguyễn Duy",
  "dateOfBirth": "1998-05-20",
  "address": "Hà Nội",
  "phone": "0912345678",
  "avatar": "http://localhost:8081/images/avatar-1.jpg",
  "createdAt": "2026-09-16T08:30:00.000Z",
  "updatedAt": "2026-09-16T08:30:00.000Z"
}
```

- `roles` là mảng string: `"USER"` hoặc `"ADMIN"`. BE hiện lưu `String role` → map sang mảng khi trả về.
- `name`, `address`, `phone`, `dateOfBirth`, `avatar` cho phép `null` (lúc mới đăng ký chưa có).
- **Không bao giờ** trả field `password` ra ngoài.

### 2.2. Category

```json
{ "id": "1", "name": "Áo thun" }
```

### 2.3. Product

```json
{
  "id": "12",
  "name": "Áo thun nam cổ tròn",
  "image": "http://localhost:8081/images/ao-thun-1.jpg",
  "images": [
    "http://localhost:8081/images/ao-thun-1.jpg",
    "http://localhost:8081/images/ao-thun-2.jpg"
  ],
  "price": 79000,
  "priceBeforeDiscount": 129000,
  "quantity": 138,
  "sold": 520,
  "view": 4141,
  "rating": 4.5,
  "description": "<p>Chất liệu cotton 100%...</p>",
  "category": { "id": "1", "name": "Áo thun" },
  "createdAt": "2026-09-16T08:30:00.000Z",
  "updatedAt": "2026-09-16T08:30:00.000Z"
}
```

| Field | Ý nghĩa | Ghi chú |
|---|---|---|
| `image` | Ảnh đại diện | Dùng ở card sản phẩm |
| `images[]` | Album ảnh | Slider ở trang chi tiết; nên chứa cả `image` |
| `price` | Giá bán hiện tại | |
| `priceBeforeDiscount` | Giá gốc | FE tính % giảm = `(before - price) / before` |
| `quantity` | Tồn kho | Dùng để chặn không cho mua quá số này |
| `rating` | Điểm trung bình 0–5 | v1 để số tĩnh, tính thật khi có `reviews` |
| `description` | **HTML** | FE sanitize trước khi render |
| `category` | Object lồng, không phải id | |

### 2.4. CartItem — một dòng trong giỏ hàng

```json
{
  "id": "3",
  "quantity": 2,
  "product": { "...": "nguyên object Product ở mục 2.3" },
  "createdAt": "2026-09-16T08:30:00.000Z",
  "updatedAt": "2026-09-16T08:30:00.000Z"
}
```

**Không có field giá.** Giá luôn đọc từ `product.price` — giỏ hàng phản ánh giá hiện tại, sản phẩm giảm
giá trong lúc còn trong giỏ thì khách được hưởng giá mới. Tổng tiền do FE tự tính từ `product.price × quantity`.

### 2.5. Order — đơn hàng

```json
{
  "id": "5",
  "orderCode": "SP20260916000123",
  "status": "PENDING",
  "subtotal": 408000,
  "shippingFee": 30000,
  "discount": 0,
  "totalAmount": 438000,
  "recipientName": "Nguyễn Duy",
  "recipientPhone": "0912345678",
  "shippingAddress": "123 Đường ABC, Quận 1, TP.HCM",
  "paymentMethod": "COD",
  "paymentStatus": "UNPAID",
  "note": "Giao giờ hành chính",
  "cancelledReason": null,
  "items": [ { "...": "object OrderItem ở mục 2.6" } ],
  "createdAt": "2026-09-16T08:30:00.000Z",
  "updatedAt": "2026-09-16T08:30:00.000Z"
}
```

**Trạng thái đơn hàng** (`status`):

| Giá trị | Ý nghĩa | Chuyển tiếp được sang |
|---|---|---|
| `PENDING` | Chờ xác nhận | `CONFIRMED`, `CANCELLED` |
| `CONFIRMED` | Đã xác nhận, chờ lấy hàng | `SHIPPING`, `CANCELLED` |
| `SHIPPING` | Đang giao | `DELIVERED` |
| `DELIVERED` | Đã giao — trạng thái cuối | — |
| `CANCELLED` | Đã huỷ — trạng thái cuối | — |

`paymentMethod`: `COD` (v1 chỉ hỗ trợ cái này) | `BANK_TRANSFER`.
`paymentStatus`: `UNPAID` | `PAID` | `REFUNDED`.

Công thức: `totalAmount = subtotal + shippingFee - discount`. v1 để `shippingFee` cố định 30.000₫ và
`discount` luôn bằng 0 (chưa có voucher).

### 2.6. OrderItem — một dòng trong đơn hàng

```json
{
  "id": "9",
  "productId": "12",
  "productName": "Áo thun nam cổ tròn",
  "productImage": "http://localhost:8081/images/ao-thun-1.jpg",
  "price": 79000,
  "priceBeforeDiscount": 129000,
  "quantity": 2
}
```

Tên, ảnh và giá là **bản sao tại thời điểm đặt hàng**, không đọc động từ `products`. Giữ `productId` chỉ
để có đường dẫn quay về trang sản phẩm.

---

## 3. Nhóm API: Auth

### 3.1. `POST /api/auth/register`

Đăng ký tài khoản mới. **Đăng ký xong là đăng nhập luôn** — API trả token ngay.

**Request**
```json
{ "email": "duy@gmail.com", "password": "123456" }
```

**Response `201`**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "expires": 86400,
  "user": { "...": "object User ở mục 2.1" }
}
```
`expires` là số giây token còn hiệu lực. Dùng `201` vì request này tạo ra một tài nguyên mới (user).

**Response `422`**
```json
{
  "type": "/errors/validation",
  "title": "Dữ liệu không hợp lệ",
  "status": 422,
  "errors": { "email": "Email đã tồn tại" }
}
```

**Xử lý phía BE**
1. Validate email + password.
2. Kiểm tra email tồn tại chưa → nếu rồi ném lỗi 422.
3. Hash password bằng BCrypt.
4. Lưu user với role mặc định `USER`.
5. Sinh JWT, trả về kèm user (đã loại bỏ password).

### 3.2. `POST /api/auth/login`

**Request**
```json
{ "email": "duy@gmail.com", "password": "123456" }
```

**Response `200`** — body giống `/register`. Dùng `200` chứ không phải `201` vì login không tạo ra gì cả.

**Response `401`**
```json
{
  "type": "/errors/invalid-credentials",
  "title": "Email hoặc mật khẩu không đúng",
  "status": 401
}
```
> Cố tình **không** nói rõ sai email hay sai mật khẩu — nếu phân biệt, kẻ tấn công có thể dò xem email nào
> đã đăng ký trên hệ thống (user enumeration).

### 3.3. `POST /api/auth/logout`

Cần header `Authorization`. Với JWT thuần (stateless) thì BE không thực sự huỷ được token; endpoint này
để FE có chỗ gọi rồi tự xoá token đã lưu.

**Response `204`** — body rỗng.

### 3.4. `POST /api/auth/refresh-token` *(giai đoạn sau)*

Khi làm refresh token, `/login` và `/register` trả thêm `refreshToken`, và lỗi token hết hạn có dạng:

```json
{
  "type": "/errors/token-expired",
  "title": "Token hết hạn",
  "status": 401
}
```
FE dựa vào `type === '/errors/token-expired'` để tự động gọi refresh rồi retry request.

---

## 4. Nhóm API: User / Profile

Tất cả endpoint nhóm này cần header `Authorization`.

### 4.1. `GET /api/users/me`

Lấy thông tin user đang đăng nhập (dựa vào token, không truyền id).

**Response `200`** — object User ở mục 2.1.

### 4.2. `PUT /api/users/me`

Cập nhật profile. Tất cả field đều optional — gửi field nào cập nhật field đó.

**Request**
```json
{
  "name": "Nguyễn Duy",
  "phone": "0912345678",
  "address": "Hà Nội",
  "dateOfBirth": "1998-05-20",
  "avatar": "http://localhost:8081/images/avatar-1.jpg",
  "password": "matkhaucu",
  "newPassword": "matkhaumoi"
}
```
- Đổi mật khẩu dùng chung endpoint này: gửi kèm `password` (mật khẩu hiện tại) + `newPassword`.
- Nếu `password` sai → `422` với `errors: { "password": "Mật khẩu không đúng" }`.
- **Không** cho đổi `email` và `roles` qua endpoint này.

**Response `200`** — user sau khi cập nhật.

### 4.3. `POST /api/users/me/avatar`

**Request**: `multipart/form-data`, field name là `image`. Giới hạn ≤ 1MB, chỉ nhận `.jpg` / `.jpeg` / `.png`.

**Response `200`**
```json
{ "url": "http://localhost:8081/images/avatar-1.jpg" }
```
> Trả object có field `url` chứ không trả chuỗi trần — sau này muốn kèm thêm `width`, `size` thì không
> phải đổi contract. FE lấy URL này rồi gọi tiếp `PUT /api/users/me` với field `avatar`.

---

## 5. Nhóm API: Product

### 5.1. `GET /api/products`

Dùng chung cho trang chủ, tìm kiếm, lọc, sắp xếp, phân trang — tất cả qua query params. Không cần đăng nhập.

| Param | Kiểu | Mặc định | Ý nghĩa |
|---|---|---|---|
| `page` | int | `1` | Trang hiện tại, **bắt đầu từ 1** (Spring `Pageable` đếm từ 0 → nhớ trừ 1) |
| `limit` | int | `20` | Số sản phẩm mỗi trang |
| `sortBy` | enum | `createdAt` | `createdAt` \| `view` \| `sold` \| `price` |
| `order` | enum | `desc` | `asc` \| `desc` |
| `category` | string | — | Lọc theo id danh mục |
| `name` | string | — | Từ khoá tìm kiếm (không phân biệt hoa thường) |
| `priceMin` | int | — | Giá từ |
| `priceMax` | int | — | Giá đến |
| `ratingFilter` | int | — | Lọc sản phẩm có `rating >= giá trị này` (1–5) |
| `exclude` | string | — | Loại trừ 1 product id (dùng cho mục "sản phẩm tương tự") |

**Response `200`**
```json
{
  "items": [ { "...": "object Product" } ],
  "page": 1,
  "limit": 20,
  "totalItems": 97,
  "totalPages": 5
}
```

> `totalPages = ceil(totalItems / limit)`. `Page<T>` của Spring Data cho sẵn cả hai qua `getTotalPages()`
> và `getTotalElements()`.

**Gợi ý triển khai:** dùng `Specification<Product>` để ghép động các điều kiện lọc (mỗi param là một
`Predicate`, param nào null thì bỏ qua), kết hợp `PageRequest.of(page - 1, limit, Sort.by(...))`.
Luôn thêm điều kiện `deleted_at IS NULL`.

### 5.2. `GET /api/products/{id}`

**Response `200`** — object Product ở mục 2.3.

**Response `404`**
```json
{
  "type": "/errors/not-found",
  "title": "Không tìm thấy sản phẩm",
  "status": 404
}
```

Mỗi lần gọi thành công thì `view += 1`.

---

## 6. Nhóm API: Category

### 6.1. `GET /api/categories`

**Response `200`**
```json
[
  { "id": "1", "name": "Áo thun" },
  { "id": "2", "name": "Điện thoại" }
]
```
> Mảng trực tiếp, không bọc thêm gì. Danh mục ít và ổn định nên không phân trang.

---

## 7. Nhóm API: Cart (giỏ hàng)

Tất cả endpoint nhóm này **bắt buộc** có header `Authorization`, và chỉ thao tác trên giỏ hàng của chính
user đang đăng nhập (lấy user từ token, **không** tin `userId` do FE gửi).

### 7.1. `GET /api/cart`

**Response `200`**
```json
[ { "...": "object CartItem ở mục 2.4" } ]
```
Mỗi phần tử nhúng nguyên object product nên FE render được toàn bộ một dòng mà không cần gọi thêm API.

### 7.2. `POST /api/cart/items` — thêm vào giỏ

**Request**
```json
{ "productId": "12", "quantity": 2 }
```

**Response `200`** — object CartItem sau khi thêm.

> Dùng `200` chứ không `201`: nếu sản phẩm đã có trong giỏ thì endpoint này **cộng dồn** vào dòng cũ chứ
> không tạo dòng mới, nên không phải lúc nào cũng là "tạo tài nguyên".

**Logic nghiệp vụ**
1. Tìm product theo `productId`, không có hoặc đã bị xoá → `404`.
2. Nếu user đã có dòng cho product này → **cộng dồn** `quantity` vào dòng cũ (ràng buộc
   `UNIQUE(user_id, product_id)` đảm bảo không bao giờ có 2 dòng trùng).
3. Nếu chưa có → tạo dòng mới.
4. Kiểm tra tổng `quantity` không vượt `product.quantity` → vượt thì `422` với
   `errors: { "quantity": "Số lượng vượt quá số lượng sản phẩm" }`.
5. **Không** trừ tồn kho ở bước này — chỉ trừ khi đặt hàng.

### 7.3. `PUT /api/cart/items/{id}` — sửa số lượng

**Request**
```json
{ "quantity": 5 }
```
> `quantity` là **giá trị tuyệt đối** (gán bằng), không phải cộng thêm — khác với API thêm vào giỏ.

**Response `200`** — dòng giỏ hàng sau khi cập nhật.

Nếu `id` không thuộc về user đang đăng nhập → `404` (không phải `403`, để không tiết lộ rằng dòng đó tồn tại).

### 7.4. `DELETE /api/cart/items/{id}` — xoá 1 sản phẩm

**Response `204`** — body rỗng.

### 7.5. `DELETE /api/cart/items?ids=1,2,3` — xoá nhiều sản phẩm

Dùng cho nút "Xoá" sau khi tick chọn nhiều dòng trong trang giỏ hàng.

**Response `204`** — body rỗng.

Chỉ xoá những dòng thuộc về user đang đăng nhập; id lạ thì bỏ qua, không báo lỗi.

---

## 8. Nhóm API: Order (đơn hàng)

Tất cả endpoint nhóm này **bắt buộc** có header `Authorization`.

### 8.1. `POST /api/orders` — đặt hàng

Chuyển các dòng được chọn trong giỏ thành một đơn hàng.

**Request**
```json
{
  "cartItemIds": ["1", "3"],
  "recipientName": "Nguyễn Duy",
  "recipientPhone": "0912345678",
  "shippingAddress": "123 Đường ABC, Quận 1, TP.HCM",
  "paymentMethod": "COD",
  "note": "Giao giờ hành chính"
}
```

> Client gửi **id của dòng giỏ hàng**, không gửi giá hay số lượng. Server tự đọc số lượng từ giỏ và giá
> từ bảng products — đây là nguyên tắc bảo mật quan trọng: **không bao giờ để client quyết định giá tiền**.

**Response `201`** — object Order ở mục 2.5.

**Response `409`** — một sản phẩm vừa hết hàng
```json
{
  "type": "/errors/conflict",
  "title": "Sản phẩm \"Áo thun nam cổ tròn\" không đủ số lượng",
  "status": 409
}
```

**Logic nghiệp vụ** — API phức tạp nhất, bắt buộc `@Transactional`. Chi tiết từng bước ghi DB xem
[`DATABASE_DESIGN.md`](DATABASE_DESIGN.md) mục 6:

1. Đọc các `cart_items` theo id, kiểm tra thuộc về user đang đăng nhập.
2. Đọc products tương ứng, kiểm tra chưa bị xoá.
3. Trừ tồn kho bằng UPDATE có điều kiện (chống race condition), cộng `sold`.
4. Tính `subtotal`, `shippingFee`, `totalAmount`.
5. Tạo `orders` với `status = PENDING`, sinh `orderCode`.
6. Tạo `order_items`, **chép** tên/ảnh/giá từ products.
7. Xoá các `cart_items` đã đặt — dòng không được chọn vẫn nằm lại trong giỏ.

Lỗi ở bất kỳ bước nào → rollback toàn bộ.

### 8.2. `GET /api/orders` — danh sách đơn của tôi

| Param | Kiểu | Mặc định | Ý nghĩa |
|---|---|---|---|
| `status` | enum | — | Lọc theo trạng thái. Bỏ trống = lấy tất cả |
| `page` | int | `1` | |
| `limit` | int | `10` | |

**Response `200`**
```json
{
  "items": [ { "...": "object Order, có kèm items" } ],
  "page": 1,
  "limit": 10,
  "totalItems": 24,
  "totalPages": 3
}
```
Mỗi đơn kèm sẵn `items` để trang "Đơn mua" render được ngay danh sách sản phẩm trong từng đơn mà không
phải gọi thêm API cho mỗi đơn.

### 8.3. `GET /api/orders/{id}` — chi tiết một đơn

**Response `200`** — object Order đầy đủ.
Nếu đơn không thuộc về user đang đăng nhập → `404`.

### 8.4. `PUT /api/orders/{id}/cancel` — huỷ đơn

**Request**
```json
{ "cancelledReason": "Đặt nhầm sản phẩm" }
```

**Response `200`** — đơn sau khi huỷ (`status = CANCELLED`).

**Response `409`** — đơn không ở trạng thái cho phép huỷ
```json
{
  "type": "/errors/conflict",
  "title": "Đơn hàng đang được giao, không thể huỷ",
  "status": 409
}
```

**Logic nghiệp vụ** (cũng cần `@Transactional`):
1. Kiểm tra đơn đang ở `PENDING` hoặc `CONFIRMED`, không thì `409`.
2. Đổi `status` sang `CANCELLED`, ghi `cancelledReason`.
3. **Hoàn lại tồn kho**: cộng trả `quantity` và trừ lại `sold` cho từng sản phẩm.

---

## 9. Nhóm API: Admin

Yêu cầu token có role `ADMIN`. Dùng `@PreAuthorize("hasRole('ADMIN')")`.
Nhóm này cần thiết vì phải có cách tạo dữ liệu sản phẩm/danh mục và xử lý đơn hàng.

| Method | Path | Mô tả |
|---|---|---|
| `POST` | `/api/admin/categories` | Tạo danh mục — body `{ name }` |
| `PUT` | `/api/admin/categories/{id}` | Sửa danh mục |
| `DELETE` | `/api/admin/categories/{id}` | Xoá danh mục (chặn nếu còn sản phẩm) |
| `POST` | `/api/admin/products` | Tạo sản phẩm — body gồm các field ở mục 2.3 (trừ `id`, `sold`, `view`, `rating`) |
| `PUT` | `/api/admin/products/{id}` | Sửa sản phẩm |
| `DELETE` | `/api/admin/products/{id}` | Xoá sản phẩm — **soft delete**, xem `DATABASE_DESIGN.md` mục 4.3 |
| `POST` | `/api/admin/upload-image` | Upload ảnh sản phẩm, `multipart/form-data`, trả URL string |
| `GET` | `/api/admin/orders` | Xem tất cả đơn hàng (mọi user), có phân trang + lọc theo status |
| `PUT` | `/api/admin/orders/{id}/status` | Đổi trạng thái đơn — body `{ "status": "CONFIRMED" }` |
| `GET` | `/api/admin/users` | Danh sách user, có phân trang |

**Quy tắc chuyển trạng thái** ở `PUT /api/admin/orders/{id}/status`: chỉ cho phép đi theo đúng luồng
`PENDING → CONFIRMED → SHIPPING → DELIVERED`, hoặc sang `CANCELLED` từ `PENDING`/`CONFIRMED`.
Chuyển sai luồng (ví dụ từ `DELIVERED` về `PENDING`) → `409`.

> Giai đoạn đầu có thể bỏ qua upload ảnh, dùng URL ảnh sẵn có trên internet và seed dữ liệu bằng
> file `data.sql` cho nhanh.

---

## 10. Bảng đối chiếu: BE hiện tại vs spec

| Hạng mục | Hiện tại | Cần làm |
|---|---|---|
| Hình dạng response thành công | ⚠️ Đang bọc trong `ApiResponse<T>` | Bỏ vỏ, trả DTO trần |
| Hình dạng lỗi | ⚠️ Handler trả `{message, data}` | Đổi sang `ProblemDetail` |
| Đường dẫn | ✅ `/api/*` đã đúng prefix | — |
| `id` dạng string | ⚠️ `UserResponse` đã đúng | Product/Category/Order làm tương tự |
| Response register | ❌ Chưa trả token | Trả `{accessToken, expires, user}` |
| Validation | ✅ Auth đã có `@Valid` | Áp cho các request record còn lại |
| CORS | ✅ Đã bật cho `localhost:3000` | — |
| JWT filter | ❌ Chưa có | Viết `OncePerRequestFilter` |
| Bảo vệ endpoint | ❌ `.anyRequest().permitAll()` | Phân quyền theo nhóm API |
| JWT secret | ❌ Hardcode trong source | Đưa ra `application.properties` |
| Entity Product | ⚠️ Chỉ có `name, price, stock` | Bổ sung ~10 field, đổi `price` sang `Long` |
| Entity User | ⚠️ Chỉ có `email, password, role` | Bổ sung `name, phone, address, dateOfBirth, avatar, createdAt, updatedAt` |
| Entity Category | ❌ Chưa có | Tạo mới |
| Entity CartItem | ❌ Chưa có | Tạo mới |
| Entity Order / OrderItem | ❌ Chưa có | Tạo mới |
| Phân trang / lọc | ❌ Chưa có | `Pageable` + `Specification` |

---

## 11. Lộ trình triển khai

| Bước | Nội dung | Kiến thức Java/Spring học được |
|---|---|---|
| **1** | Nền tảng chung: `GlobalExceptionHandler` trả `ProblemDetail`, validation, bật CORS | `@RestControllerAdvice`, `ProblemDetail`, CORS |
| **2** | JWT filter + phân quyền thật sự | `OncePerRequestFilter`, `SecurityContextHolder`, filter chain |
| **3** | Hoàn thiện User + `GET/PUT /api/users/me` | `@AuthenticationPrincipal`, DTO mapping, JPA auditing |
| **4** | Category CRUD | Quan hệ `@ManyToOne` / `@OneToMany` |
| **5** | Nâng cấp Product: đủ field, phân trang, lọc, sắp xếp, soft delete | `Pageable`, `Specification`, `@SQLRestriction` |
| **6** | Cart | Ràng buộc `UNIQUE`, logic cộng dồn |
| **7** | Order: đặt hàng + huỷ đơn | `@Transactional`, snapshot, race condition tồn kho |
| **8** | Admin + upload ảnh | `@PreAuthorize`, `MultipartFile`, máy trạng thái |
| **9** | *(Tuỳ chọn)* Refresh token | Token lifecycle |

Sau bước 7 là đã đủ để FE chạy hoàn chỉnh với BE này.

---

## 12. Danh sách việc cần sửa ở FE khi tích hợp

FE hiện tại được viết theo contract của API công khai dùng trong khoá học. Để dùng BE này, cần sửa:

### 12.1. `FE/src/utils/http.ts`

```diff
- baseURL: 'https://api-ecom.duthanhduoc.com/',
+ baseURL: 'http://localhost:8081/api/',

- config.headers.Authorization = this.access_token
+ config.headers.Authorization = `Bearer ${this.access_token}`
```

Ngoài ra, interceptor đang so sánh `url === path.login` để biết khi nào lưu token — mà `path.login`
đồng thời là route của React Router. Khi API đổi thành `auth/login`, hai thứ này tách đôi → cần hằng số
riêng cho API path (ví dụ `constants/apiPath.ts`).

### 12.2. Đọc response — không còn `.data.data`

BE không bọc envelope nữa, nên axios trả thẳng dữ liệu:

```diff
- const products = response.data.data.products
+ const products = response.data.items
```

`types/utils.type.ts` bỏ hẳn `SuccessResponse<Data>` và `ErrorResponse<Data>`, thay bằng hai kiểu mới:

```ts
export interface Paginated<T> {
  items: T[]
  page: number
  limit: number
  totalItems: number
  totalPages: number
}

// RFC 9457 — hình dạng chung của MỌI response lỗi
export interface ProblemDetail {
  type: string
  title: string
  status: number
  detail?: string
  errors?: Record<string, string>
}
```

Chỗ bắt lỗi form ở `Login.tsx` và `Register.tsx` đổi theo — lỗi từng field giờ nằm ở `errors`:

```diff
- if (isAxiosUnprocessableEntityError<ErrorResponse<FormData>>(error)) {
-   const formError = error.response?.data.data
+ if (isAxiosUnprocessableEntityError<ProblemDetail>(error)) {
+   const formError = error.response?.data.errors
```

Và câu thông báo hiện cho người dùng đọc từ `title` thay vì `message`.

### 12.3. Đổi tên field — find & replace toàn bộ `FE/src`

| Cũ | Mới |
|---|---|
| `_id` | `id` |
| `price_before_discount` | `priceBeforeDiscount` |
| `buy_count` | `quantity` |
| `product_id` | `productId` |
| `access_token` | `accessToken` |
| `date_of_birth` | `dateOfBirth` |
| `sort_by` | `sortBy` |
| `rating_filter` | `ratingFilter` |
| `price_min` / `price_max` | `priceMin` / `priceMax` |
| `page_size` | `totalPages` |

### 12.4. Viết lại tầng API

| File cũ | Thay bằng |
|---|---|
| `apis/auth.api.ts` | Đổi path sang `auth/register`, `auth/login`, `auth/logout` |
| `apis/purchase.api.ts` | Tách thành `apis/cart.api.ts` (mục 7) và `apis/order.api.ts` (mục 8) |
| `types/purchase.type.ts` | Tách thành `types/cart.type.ts` và `types/order.type.ts` |
| `constants/purchase.ts` | Thay `purchaseStatus` (số) bằng `orderStatus` (chuỗi enum) |

### 12.5. Trang cần viết mới

- **Cart** (hiện là stub) — dựng theo API mục 7. Lưu ý giá đọc từ `item.product.price`, không còn field
  giá riêng trên dòng giỏ hàng.
- **Trang đặt hàng** — form nhập `recipientName`, `recipientPhone`, `shippingAddress`, `note` rồi gọi
  `POST /api/orders`.
- **Đơn mua** — chưa tồn tại. Hiển thị danh sách đơn theo tab trạng thái, mỗi đơn là một thẻ chứa nhiều
  sản phẩm (khác với contract cũ vốn hiển thị danh sách sản phẩm phẳng).
- **Profile** (hiện là stub) — dựng theo API mục 4.

### 12.6. Sửa 2 lỗi build còn tồn đọng

`FE/src/components/Pagination/Pagination.tsx` và
`FE/src/pages/ProductList/components/RatingStars/RatingStars.tsx` đang import `QueryConfig` từ
`pages/ProductList/ProductList` — đường dẫn cũ, nay nằm ở `src/hooks/useQueryConfig.tsx`.
Hiện `npm run build` đang fail vì lỗi này.
