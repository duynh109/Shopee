# API Spec — Ecommerce BE (Shopee Clone)

> **Bộ tài liệu thiết kế:**
> - `API_SPEC.md` *(tài liệu này)* — contract API giữa BE và FE
> - [`DATABASE_DESIGN.md`](DATABASE_DESIGN.md) — schema database và các nguyên tắc thiết kế
> - [`SYSTEM_DESIGN.md`](SYSTEM_DESIGN.md) — lộ trình mở rộng hệ thống
>
> **Nguyên tắc thiết kế:** BE theo chuẩn REST thông dụng của hệ sinh thái Java/SQL — DTO trần, field
> `camelCase`, lỗi RFC 9457. FE gọi đúng spec này; cách nối ở [mục 12](#12-fe-cách-nối-với-api).
>
> **Vị trí:** thư mục `docs/` nằm ở **gốc repo, ngang cấp với `BE/` và `FE/`** — vì đây là thiết kế
> chung của cả hai nửa, không phải tài liệu riêng của BE. Trước khi sửa FE, đọc lại bộ tài liệu này:
> §1.10 ghi rõ **màn nào của FE gọi tới** từng endpoint và route của màn đó.

---

## 1. Quy ước chung

### 1.1. Base URL và prefix `/api`

| Môi trường | URL |
|---|---|
| Local | `http://localhost:8081/api` |

Mọi endpoint đều nằm dưới prefix `/api`. Lý do dùng prefix thay vì đặt ở root:

- Tách bạch API với tài nguyên tĩnh khi cùng một server phục vụ cả hai (ảnh upload nằm ở `/images/**`,
  không lẫn với `/api/**`).
- Nếu đặt BE sau nginx thì chỉ cần một rule `location /api/ { proxy_pass http://localhost:8081; }` (bản Docker
  hiện tại không làm vậy: nginx chỉ phục vụ FE, trình duyệt gọi thẳng cổng 8081).
- Dễ mở rộng sang versioning sau này (`/api/v1`, `/api/v2`) nếu cần.

**Ba cách thêm prefix trong Spring Boot:**

| Cách | Code | Ảnh hưởng |
|---|---|---|
| **Ghi thẳng vào từng controller** (khuyến nghị) | `@RequestMapping("/api/products")` | Rõ ràng, nhìn controller là biết full path |
| Cấu hình tập trung | `configurer.addPathPrefix("/api", HandlerTypePredicate.forAnnotation(RestController.class))` trong `WebMvcConfigurer` | Chỉ áp cho `@RestController`, file tĩnh vẫn ở root |
| `server.servlet.context-path=/api` | 1 dòng trong properties | Prefix **mọi thứ**, kể cả file tĩnh và trang lỗi → ảnh upload cũng bị đẩy vào `/api` |

Dự án dùng cách 1: mở controller ra là thấy full path, không phải nhớ có cấu hình nào thêm prefix.

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
  "items": [ { "...": "Product dạng RÚT GỌN — mục 2.3" } ],
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

> Endpoint nào cần token, endpoint nào không: xem bảng ở **§1.10**.

```
Authorization: Bearer eyJhbGciOiJIUzUxMiJ9...
```

Đây là chuẩn RFC 6750, được Postman, Swagger UI và mọi thư viện HTTP hỗ trợ sẵn. `JwtAuthenticationFilter`
chỉ nhận đúng dạng có tiền tố `Bearer `; header thiếu tiền tố bị coi như không gửi token, nên endpoint cần
đăng nhập trả `401 /errors/token-invalid`.

**Payload gồm `sub` (email), `role`, `iat`, `exp`**, ký HS512. Filter dựng quyền thẳng từ `role` trong token,
không hỏi DB — đổi `role` trong DB chỉ có hiệu lực khi access token được đổi mới (tối đa 15 phút), xem §3.4.

> Payload JWT chỉ được **Base64**, không phải mã hoá — ai cầm được token đều đọc được nội dung mà
> không cần secret. Chữ ký chỉ chống **sửa**, không chống **đọc**. Đừng đặt thông tin nhạy cảm vào đó.

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
| `400` | Request không đọc được: JSON hỏng, path variable hoặc query param sai kiểu, thiếu query param bắt buộc |
| `401` | Chưa đăng nhập, token sai/hết hạn, **hoặc sai email/mật khẩu lúc login** |
| `403` | Đã đăng nhập nhưng không đủ quyền |
| `404` | Không tìm thấy tài nguyên |
| `405` | Đường dẫn đúng nhưng sai HTTP method |
| `409` | Xung đột trạng thái (huỷ đơn đã giao, hết hàng khi đặt), hoặc hai request cùng lúc trùng một ràng buộc `UNIQUE` |
| `413` | File upload vượt dung lượng cho phép (ảnh > 1MB) |
| `415` | Thiếu hoặc sai `Content-Type` |
| `422` | **Lỗi validate dữ liệu đầu vào** |
| `500` | Lỗi server |

**Phân biệt 401 / 422 / 409** — chỗ này hay nhầm:
- `422` = dữ liệu gửi lên **sai định dạng** (email sai cú pháp, mật khẩu dưới 6 ký tự, số lượng âm).
- `401` = **danh tính không đúng** (sai mật khẩu, token hỏng). Dữ liệu hợp lệ, chỉ là không đúng người.
- `409` = dữ liệu hợp lệ, danh tính đúng, nhưng **trạng thái hệ thống không cho phép** (sản phẩm vừa hết
  hàng, đơn đã giao rồi nên không huỷ được).

**`400` hay `422` với query param nhận qua `@ModelAttribute`** (`ProductQuery`, `OrderQuery`): Spring gom
cả lỗi *đổi kiểu* (`?page=abc`) lẫn lỗi *vi phạm luật* vào cùng một `MethodArgumentNotValidException`.
`GlobalExceptionHandler` tách chúng bằng `FieldError.isBindingFailure()` — có ít nhất một lỗi đổi kiểu thì
`400`, còn lại `422`. Không tách thì `?page=abc` nhận `422` kèm câu `Failed to convert value of type
'java.lang.String' to required type 'java.lang.Integer'` hiện thẳng lên toast: sai status, và lộ chi tiết
nội bộ.

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
| `400` | `/errors/bad-request` | JSON hỏng, path variable hoặc query param sai kiểu (`?page=abc`), thiếu query param bắt buộc |
| `401` | `/errors/invalid-credentials` | Sai email hoặc mật khẩu lúc login |
| `401` | `/errors/token-invalid` | Token hỏng, sai chữ ký (kể cả bị sửa payload), thiếu claim `role`, hoặc không gửi token |
| `401` | `/errors/token-expired` | Token hết hạn — FE dùng để kích hoạt refresh |
| `401` | `/errors/refresh-token-invalid` | Refresh token không dùng được (không có, hết hạn, đã thu hồi) — FE đưa về màn đăng nhập, **không** gọi refresh lại (§3.4) |
| `403` | `/errors/forbidden` | Đã đăng nhập nhưng không đủ quyền |
| `404` | `/errors/not-found` | Không tìm thấy tài nguyên |
| `405` | `/errors/method-not-allowed` | Sai HTTP method |
| `409` | `/errors/conflict` | Xung đột trạng thái; hoặc hai request cùng lúc đụng một ràng buộc `UNIQUE` (xem dưới) |
| `413` | `/errors/content-too-large` | File upload vượt 1MB (§9.4) |
| `415` | `/errors/unsupported-media-type` | Thiếu hoặc sai `Content-Type`. `title` nhắc đúng loại endpoint cần: `application/json`, hoặc `multipart/form-data` với hai endpoint upload |
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
    "email": "Email không hợp lệ",
    "password": "Độ dài tối thiểu 6 kí tự"
  }
}
```

**Lỗi 409 khi hai request cùng lúc đụng một ràng buộc `UNIQUE`.** Service luôn kiểm tra trước khi ghi —
email đã có chưa, tên danh mục trùng chưa, sản phẩm đã nằm trong giỏ chưa — nên request đến *sau* nhận câu
trả lời rõ ràng (`422 errors.email`, giỏ cộng dồn…). Hai request đến *cùng lúc* thì cùng qua bước kiểm tra,
và chỉ ràng buộc `UNIQUE` ở DB chặn được request thứ hai (`DATABASE_DESIGN.md` §4.6). DB từ chối thì Spring
ném `DataIntegrityViolationException`; `GlobalExceptionHandler` xem nguyên nhân bên trong
(`org.hibernate.exception.ConstraintViolationException`) thuộc loại nào:

| `getKind()` | Trả về | Vì sao |
|---|---|---|
| `UNIQUE` | `409 /errors/conflict`, `title` = `Thao tác bị trùng với một yêu cầu khác vừa xảy ra, vui lòng thử lại`; log một dòng WARN chỉ ghi tên ràng buộc | Không ai sai, chỉ trùng thời điểm. Thử lại là nhận đúng câu trả lời của request đến sau: đăng ký → `422 errors.email`, giỏ → `200` cộng dồn |
| Mọi loại khác — `NOT_NULL`, `FOREIGN_KEY`, `CHECK` — và dữ liệu quá dài cột | `500` + log ERROR kèm stack trace | Code kiểm thiếu; thử lại lần nào cũng hỏng, nên phải để lộ ra cho người sửa code thấy |

Đã đo, mỗi loại 10 lượt hai request song song — đăng ký cùng email, tạo trùng tên danh mục, bấm "Thêm vào
giỏ" hai lần với sản phẩm chưa có trong giỏ: lượt nào cũng 1 thành công + 1×`409`, log không có ERROR.

Dòng WARN không ghi `ex.getMessage()` vì message chứa luôn giá trị bị trùng — email của người dùng. Hibernate
cũng tự ghi mỗi lỗi SQL ở mức WARN kèm giá trị đó (`Duplicate entry 'an@gmail.com' …`, logger
`org.hibernate.orm.jdbc.error`), nên `application.properties` nâng logger này lên `ERROR`. Không mất thông tin:
lỗi thật vẫn được `GlobalExceptionHandler` ghi ERROR kèm stack trace (đã thử với `Data too long`). Đo lại qua
các luồng đăng ký, giỏ hàng, danh mục: log không chứa email nào.

Hai cách viết sai, đã thử:

- **So chuỗi trong message**, kiểu `getMessage().contains("UNIQUE")`: message của MySQL là
  `Duplicate entry '…' for key 'users.uq_user_email'`, không có chữ "UNIQUE", lại chứa luôn giá trị người dùng
  nhập. Kết quả phụ thuộc vào dữ liệu: tạo trùng danh mục "Áo thun" → `500`, "Hàng UNIQUE" → `409`. Message
  là câu viết cho người đọc log, giống `title`; `getKind()` mới là giá trị để code so sánh, giống `type`.
- **Bắt cả `DataIntegrityViolationException` thành `409`**: một URL ảnh quá 255 kí tự (giả lập thiếu `@Size`)
  nhận "vui lòng thử lại" ở cả 3 lần thử, và log không có dòng ERROR nào — bug bị che mất.

### 1.7. Rule validate

BE **luôn phải validate** dù FE đã validate (không bao giờ tin client):

| Field | Rule | Thông báo mẫu |
|---|---|---|
| `email` | Bắt buộc, đúng định dạng email, độ dài 5–255 ¹ | `Email không hợp lệ` |
| `password` | Bắt buộc, tối thiểu 6 kí tự, **tối đa 72 byte** ² | `Mật khẩu tối đa 72 kí tự` |
| `quantity` | Số nguyên ≥ 1, ≤ tồn kho | `Số lượng vượt quá số lượng sản phẩm trong kho` |
| `phone`, `recipientPhone` | Số Việt Nam dạng trong nước: `0` + 9–10 chữ số ³. `recipientPhone` bắt buộc khi đặt hàng | `Số điện thoại không hợp lệ` |

¹ `@Email` của Hibernate Validator còn tự áp giới hạn của RFC: phần trước `@` tối đa 64 kí tự.
Nên một email 200 kí tự với phần local quá dài bị chặn vì *sai định dạng*, không phải vì *quá dài*.

² **Tính theo byte, không phải kí tự.** BCrypt chỉ băm được 72 byte đầu; `BCryptPasswordEncoder`
ném `IllegalArgumentException` khi vượt, tức `500` nếu không chặn từ tầng validate. Một kí tự
tiếng Việt có dấu chiếm 3 byte UTF-8 và một emoji chiếm 4, nên 25 kí tự có dấu đã là 75 byte.
`@Size` đếm kí tự nên không đủ — dùng annotation riêng `@MaxBytes` ở
`BE/.../validation/MaxBytes.java`.

Trần trên do **một mình** `@MaxBytes` giữ, không đặt thêm `@Size(max = ...)` song song: khi hai
luật cùng vi phạm thì `GlobalExceptionHandler` giữ lỗi nào tới trước, mà thứ tự đó không xác định
— mật khẩu 200 kí tự ASCII có lúc nhận câu nói về dấu. Thay vào đó `MaxBytesValidator` tự đổi câu
theo tình huống:

| Đầu vào | Thông báo |
|---|---|
| dài hơn 72 **kí tự** | `Mật khẩu tối đa 72 kí tự` |
| ngắn hơn 72 kí tự nhưng vượt 72 **byte** | `Mật khẩu quá dài, vui lòng bớt kí tự có dấu hoặc kí tự đặc biệt` |

³ **Một luật cho cả hai field, định nghĩa ở một chỗ:** hằng `ValidationPatterns.PHONE` (`"0\\d{9,10}"`)
trong `BE/.../validation/`, dùng làm `@Pattern(regexp = ValidationPatterns.PHONE)` ở cả
`UpdateProfileRequest.phone` lẫn `CreateOrderRequest.recipientPhone`. Hai lý do:

- Trang đặt hàng điền sẵn số từ hồ sơ. Hai luật lệch nhau thì có số hồ sơ đã nhận mà lúc đặt hàng
  lại `422` — ví dụ một luật nhận `8412345678` còn luật kia chỉ nhận `+84912345678`, thì số nào cũng
  có một màn từ chối.
- Không nhận `+84...`: cùng một số không được có hai cách viết trong `orders`, nếu không thì tìm đơn
  theo số điện thoại phải xử lý cả hai.

Giá trị trong annotation phải là **hằng số lúc biên dịch** — một `static final String` gán bằng chuỗi
literal thì dùng được, gọi method thì không. `recipientPhone` ghép `@NotNull` (không phải `@NotBlank`)
với `@Pattern` để không input nào vi phạm cả hai luật cùng lúc: `null` chỉ trượt `@NotNull`, `""`
chỉ trượt `@Pattern`.

### 1.8. Kiểu dữ liệu

| Loại | Quy ước | Ví dụ |
|---|---|---|
| Thời gian | Chuỗi ISO-8601 UTC | `"2026-09-16T08:30:00.000Z"` |
| Tiền | Số nguyên VND, **không** có phần thập phân | `3190000` |
| Ảnh | URL đầy đủ | `"http://localhost:8081/images/abc.jpg"` |

### 1.9. CORS

FE chạy ở `http://localhost:3000`, BE ở `http://localhost:8081` → khác origin, nên CORS được bật trong
`SecurityConfig`: cho phép origin `http://localhost:3000`, method `GET, POST, PUT, DELETE, OPTIONS`,
header `Authorization` và `Content-Type`.


### 1.10. Bảng quyền: ai gọi được API nào

Ba mức quyền dùng trong toàn bộ tài liệu này:

| Ký hiệu | Nghĩa | Thiếu quyền thì nhận |
|---|---|---|
| 🌐 **Công khai** | Không cần token | — |
| 🔒 **Đã đăng nhập** | Token hợp lệ, role nào cũng được | `401` |
| 👑 **ADMIN** | Token hợp lệ **và** role `ADMIN` | `401` nếu thiếu token, `403` nếu là USER |

| Method | Path API | Ai gọi được | BE | Màn FE gọi tới | Route màn FE |
|---|---|---|---|---|---|
| `POST` | `/api/auth/register` | 🌐 Công khai | ✅ | `Register` | `/register` |
| `POST` | `/api/auth/login` | 🌐 Công khai | ✅ | `Login` | `/login` |
| `POST` | `/api/auth/logout` | 🌐 Công khai ¹ | ✅ | `Header` ³ | mọi màn `MainLayout` |
| `POST` | `/api/auth/refresh-token` | 🌐 Công khai ¹ | ✅ | interceptor `utils/http.ts` ⁴ | — |
| `GET` | `/api/users/me` | 🔒 Đã đăng nhập ² | ✅ | `Profile` | `/user/profile` |
| `PUT` | `/api/users/me` | 🔒 Đã đăng nhập ² | ✅ | `Profile`, `ChangePassword` | `/user/profile`, `/user/password` |
| `POST` | `/api/users/me/avatar` | 🔒 Đã đăng nhập ² | ✅ | `Profile` | `/user/profile` |
| `GET` | `/api/products` | 🌐 Công khai | ✅ | `ProductList`, `ProductDetail` ⁵ | `/`, `/:nameId` |
| `GET` | `/api/products/{id}` | 🌐 Công khai | ✅ | `ProductDetail` | `/:nameId` |
| `GET` | `/api/categories` | 🌐 Công khai | ✅ | `ProductList` (thanh lọc) | `/` |
| `GET` | `/api/cart` | 🔒 Đã đăng nhập ² | ✅ | `Cart`, `Checkout`, `Header` ⁷ | `/cart`, `/checkout` + mọi màn |
| `POST` | `/api/cart/items` | 🔒 Đã đăng nhập ² | ✅ | `ProductDetail` (Thêm vào giỏ, Mua ngay) | `/:nameId` |
| `PUT` | `/api/cart/items/{id}` | 🔒 Đã đăng nhập ² | ✅ | `Cart` (ô số lượng) | `/cart` |
| `DELETE` | `/api/cart/items/{id}` | 🔒 Đã đăng nhập ² | ✅ | `Cart` (xoá 1 dòng) | `/cart` |
| `DELETE` | `/api/cart/items?ids=1,2,3` | 🔒 Đã đăng nhập ² | ✅ | `Cart` (xoá nhiều) | `/cart` |
| `POST` | `/api/orders` | 🔒 Đã đăng nhập ² | ✅ | `Checkout` | `/checkout` |
| `GET` | `/api/orders` | 🔒 Đã đăng nhập ² | ✅ | `Purchase` | `/user/purchase` |
| `GET` | `/api/orders/{id}` | 🔒 Đã đăng nhập ² | ✅ | `PurchaseDetail` | `/user/purchase/:id` |
| `PUT` | `/api/orders/{id}/cancel` | 🔒 Đã đăng nhập ² | ✅ | `Purchase`, `PurchaseDetail` (hộp thoại `CancelOrderDialog`) | `/user/purchase`, `/user/purchase/:id` |
| `POST` | `/api/admin/categories` | 👑 ADMIN | ✅ | chưa có màn admin ⁶ | — |
| `PUT` | `/api/admin/categories/{id}` | 👑 ADMIN | ✅ | chưa có màn admin ⁶ | — |
| `DELETE` | `/api/admin/categories/{id}` | 👑 ADMIN | ✅ | chưa có màn admin ⁶ | — |
| `POST` | `/api/admin/products` | 👑 ADMIN | ✅ | chưa có màn admin ⁶ | — |
| `PUT` | `/api/admin/products/{id}` | 👑 ADMIN | ✅ | chưa có màn admin ⁶ | — |
| `DELETE` | `/api/admin/products/{id}` | 👑 ADMIN | ✅ | chưa có màn admin ⁶ | — |
| `POST` | `/api/admin/upload-image` | 👑 ADMIN | ✅ | chưa có màn admin ⁶ | — |
| `GET` | `/api/admin/orders` | 👑 ADMIN | ✅ | chưa có màn admin ⁶ | — |
| `PUT` | `/api/admin/orders/{id}/status` | 👑 ADMIN | ✅ | chưa có màn admin ⁶ | — |
| `GET` | `/api/admin/users` | 👑 ADMIN | ✅ | chưa có màn admin ⁶ | — |

¹ Phải công khai, vì được gọi đúng lúc access token đã (`refresh-token`) hoặc có thể đã (`logout`) hết hạn,
nên không thể đòi access token hợp lệ. Cả hai tự xác thực bằng refresh token nằm trong body.

³ `components/Header/Header.tsx` là component dùng chung, có mặt trên mọi màn bọc bởi `MainLayout`
(`/`, `/:nameId`, `/cart`, `/checkout`, `/user/…`). Nút đăng xuất nằm ở menu tài khoản của nó.

⁴ Không màn nào gọi trực tiếp. Interceptor trong `FE/src/utils/http.ts` tự gọi khi gặp `401` có
`type === "/errors/token-expired"`, rồi retry request gốc — người dùng không thấy gì.

⁵ `ProductList` gọi để hiển thị danh sách + tìm kiếm + lọc; `ProductDetail` gọi lần nữa cho khối
"sản phẩm tương tự" (cùng category, loại chính sản phẩm đang xem bằng `exclude`).

⁶ **Chưa có màn admin nào ở FE.** Các API này hiện gọi bằng Postman hoặc curl. Màn admin nếu làm thì là
một app riêng, không nhét vào FE người mua.

⁷ `Cart` gọi để hiển thị giỏ; `Checkout` đọc lại để lấy các dòng đã chọn; `Header` gọi để hiện con số trên icon giỏ hàng.

² **Hai tầng kiểm tra, đừng lẫn lộn.** Cột này chỉ nói về tầng thứ nhất.

| | Câu hỏi | Ai trả lời | Thất bại |
|---|---|---|---|
| **Tầng 1 — xác thực & phân quyền** | "Đã đăng nhập chưa? Có role gì?" | `SecurityConfig` | `401` / `403` |
| **Tầng 2 — quyền trên từng bản ghi** | "Đơn hàng này có phải của bạn?" | tầng service | `404` |

`SecurityConfig` **không thể** trả lời tầng 2 — lúc nó chạy thì chưa ai truy vấn DB, nó chỉ biết
đường dẫn và role. Cho nên `GET /api/orders/{id}` phải tự kiểm tra trong service rằng đơn đó thuộc
về user đang đăng nhập. Quên tầng 2 là mọi user đọc được đơn hàng của nhau — dù tầng 1 vẫn "đúng".

Lưu ý tầng 2 trả **`404` chứ không phải `403`**: trả `403` là đã tiết lộ rằng bản ghi đó tồn tại.

**Cài đặt tầng 2 bằng điều kiện query, không bằng câu `if`.** Nhóm Cart (§7.6) làm mẫu: điều kiện sở
hữu nằm ngay trong câu truy vấn (`findByIdAndUserId`) chứ không phải kiểm tra sau khi đã lấy dữ liệu
ra. Nhờ vậy "không tồn tại" và "không phải của bạn" hội tụ về cùng một `Optional.empty()`, nên chỉ có
một nhánh lỗi để viết và không có chỗ nào để lỡ tay trả `403`. Kiểm bằng `if` thì quên một lần là thủng
một chỗ, mà triệu chứng là `200` — không có gì để nhận ra.

**Một điều cần biết về thứ tự:** phân quyền chạy **trước** khi Spring tìm controller. Nên URL không
tồn tại hoặc sai HTTP method mà **không có token** sẽ nhận `401`, không phải `404`/`405` — người
chưa đăng nhập không dò được API có những đường dẫn nào. Có token hợp lệ thì vẫn trả `404`/`405`
đúng như mong đợi.

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

**Dạng đầy đủ** — trả ở `GET /api/products/{id}`:

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
| `image` | Ảnh bìa | Admin **chọn** ảnh nào làm bìa — không phải `images[0]`, để đổi thứ tự album không làm đổi bìa |
| `images[]` | Album ảnh | Slider ở trang chi tiết; nên chứa cả `image`. **Chỉ có ở dạng đầy đủ** |
| `price` | Giá bán hiện tại | |
| `priceBeforeDiscount` | Giá gốc | **`null` nếu sản phẩm không giảm giá** (cột DB cho phép NULL, admin được bỏ trống). BE không bắt buộc nó ≥ `price`. FE chỉ hiện giá gạch và % giảm khi có giá gốc **và** nó cao hơn `price`; % giảm = `(before - price) / before` |
| `quantity` | Tồn kho | Dùng để chặn không cho mua quá số này |
| `rating` | Điểm trung bình 0–5 | v1 để số tĩnh, tính thật khi có `reviews` |
| `description` | **HTML**, tối đa 10.000 kí tự | FE sanitize trước khi render. Giới hạn đếm theo `String.length()` của Java — emoji tính 2 — và vì sao là 10.000: `DATABASE_DESIGN.md` §3.3 |
| `category` | Object lồng, không phải id | |

**Dạng rút gọn** — trả ở `GET /api/products` (danh sách). Giống dạng đầy đủ nhưng **bỏ hai field
nặng nhất**: `images[]` và `description`.

```json
{
  "id": "12",
  "name": "Áo thun nam cổ tròn",
  "image": "http://localhost:8081/images/ao-thun-1.jpg",
  "price": 79000,
  "priceBeforeDiscount": 129000,
  "quantity": 138,
  "sold": 520,
  "view": 4141,
  "rating": 4.5,
  "category": { "id": "1", "name": "Áo thun" },
  "createdAt": "2026-09-16T08:30:00.000Z",
  "updatedAt": "2026-09-16T08:30:00.000Z"
}
```

Vì sao tách hai dạng:

- Một trang danh sách 20 thẻ sản phẩm chỉ cần **một** ảnh mỗi thẻ. Trả cả album nghĩa là phải nạp
  thêm bảng `product_images` cho cả 20 dòng, rồi gửi qua mạng một đống URL không màn nào hiển thị.
- `description` là HTML mô tả dài, chỉ trang chi tiết mới render.
- Chính vì danh sách không đụng tới `images[]` mà cột `products.image` mới có lý do tồn tại
  (`DATABASE_DESIGN.md` §3.3). Nếu danh sách cũng trả album thì cột đó thành dư thừa.

Hai dạng ⇒ hai DTO ở BE: `ProductSummaryResponse` cho danh sách, `ProductResponse` cho chi tiết.

Cả hai dạng đều giữ `createdAt`/`updatedAt`: màn quản trị cần biết sản phẩm tạo và sửa lúc nào, và
`sortBy=createdAt` (§5.1) sắp xếp theo đúng mốc này nên trả ra để đối chiếu được.

### 2.4. CartItem — một dòng trong giỏ hàng

```json
{
  "id": "3",
  "quantity": 2,
  "product": { "...": "Product dạng RÚT GỌN ở mục 2.3" },
  "createdAt": "2026-09-16T08:30:00.000Z",
  "updatedAt": "2026-09-16T08:30:00.000Z"
}
```

**Không có field giá.** Giá luôn đọc từ `product.price` — giỏ hàng phản ánh giá hiện tại, sản phẩm giảm
giá trong lúc còn trong giỏ thì khách được hưởng giá mới. Tổng tiền do FE tự tính từ `product.price × quantity`.

**`product` luôn có mặt, không bao giờ `null`.** Dòng giỏ hàng trỏ tới sản phẩm đã bị xoá mềm sẽ không
xuất hiện trong response — xem §7.6. FE không cần xử lý trường hợp product rỗng.

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
`paymentStatus`: `UNPAID` | `PAID` | `REFUNDED`. Đơn tạo ra luôn là `UNPAID`. Đơn COD tự thành `PAID`
đúng lúc admin chuyển sang `DELIVERED` (shipper thu tiền khi giao), không có API nào sửa riêng
`paymentStatus` — xem §9.1.

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
để có đường dẫn quay về trang sản phẩm. `priceBeforeDiscount` được sao nguyên từ sản phẩm nên cũng có thể là `null`.

---

## 3. Nhóm API: Auth

### 3.1. `POST /api/auth/register`

**Quyền:** 🌐 **Công khai** — không cần token

**Màn FE:** `Register` — route `/register`

Đăng ký tài khoản mới. **Đăng ký xong là đăng nhập luôn** — API trả token ngay.

**Request**
```json
{ "email": "duy@gmail.com", "password": "123456" }
```

**Response `201`**
```json
{
  "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
  "expires": 900,
  "refreshToken": "NJNfU__P8x1_fXcGkmzFRcp9ZikDy49Qg6L3K6ncbvs",
  "user": { "...": "object User ở mục 2.1" }
}
```
`expires` là số giây access token còn hiệu lực (900 = 15 phút, §3.4). `refreshToken` là chuỗi ngẫu nhiên 43 kí tự, sống 7 ngày,
dùng để xin access token mới — xem §3.4; DB chỉ lưu bản băm của nó (`DATABASE_DESIGN.md` §3.8). Mỗi lần
đăng ký / đăng nhập phát một refresh token riêng. Dùng `201` vì request này tạo ra một tài nguyên mới (user).

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
2. Kiểm tra email tồn tại chưa → nếu rồi ném lỗi 422. Hai request cùng email đến cùng lúc thì cả hai qua
   bước này; ràng buộc `UNIQUE` chặn request sau, request đó nhận `409` (§1.6).
3. Hash password bằng BCrypt.
4. Lưu user với role mặc định `USER`.
5. Phát access token (JWT) và refresh token, trả về kèm user (đã loại bỏ password).

### 3.2. `POST /api/auth/login`

**Quyền:** 🌐 **Công khai** — không cần token

**Màn FE:** `Login` — route `/login`

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

**Quyền:** 🌐 **Công khai** — xác thực bằng chính refresh token trong body. Lúc người dùng bấm đăng xuất,
access token có thể đã hết hạn; bắt buộc phải có nó thì FE phải đổi token trước rồi mới đăng xuất được.
Còn ai cầm refresh token thì vốn đã dùng được nó, nên cho người đó thu hồi nó là an toàn.

**Màn FE:** `components/Header/Header.tsx` (menu tài khoản) — có mặt trên mọi màn dùng `MainLayout`

**Request**
```json
{ "refreshToken": "NJNfU__P8x1_fXcGkmzFRcp9ZikDy49Qg6L3K6ncbvs" }
```

**Response `204`** — body rỗng. Refresh token đó bị thu hồi; token của các thiết bị khác không bị đụng tới.
Access token đang cầm vẫn hợp lệ tới lúc hết hạn (tối đa 15 phút) — FE tự xoá cả hai.

**Luôn `204`**, kể cả khi token lạ, đã hết hạn hoặc đã thu hồi: mục đích của logout là "token này không dùng
được nữa", và trong các trường hợp đó điều này đã đúng sẵn; trả `204` cho mọi trường hợp cũng không cho
người dò biết token nào từng tồn tại. Thiếu `refreshToken` → `422`, không gửi body → `400`.

Logout chạy **một câu** `UPDATE refresh_tokens SET revoked_at = ? WHERE token_hash = ? AND revoked_at IS NULL`,
không khoá dòng và không kiểm tra gì thêm — khác hẳn việc đổi token ở §3.4. Dùng logic đổi token cho logout
là sai: bấm đăng xuất hai lần thì lần sau gặp token "đã thu hồi", bị coi là token bị lộ và mọi thiết bị khác
của người dùng bị đăng xuất theo.

Một token đã đăng xuất mà sau đó vẫn bị đem đi **đổi** thì §3.4 coi là token bị lộ: mọi refresh token của
user đó bị thu hồi. Đã kiểm: logout token A (thiết bị kia vẫn sống) → đem A đi đổi → `401`, và token thiết bị
kia bị thu hồi theo.

### 3.4. `POST /api/auth/refresh-token`

**Quyền:** 🌐 **Công khai** — phải công khai, vì nó được gọi đúng lúc access token đã hết hạn; xác thực bằng
refresh token trong body. Header `Authorization` có gửi kèm (kể cả token rác) cũng bị bỏ qua.

**Màn FE:** Không màn nào gọi trực tiếp; interceptor `FE/src/utils/http.ts` tự gọi rồi retry request gốc
— xem §12.1.

**Request**
```json
{ "refreshToken": "NJNfU__P8x1_fXcGkmzFRcp9ZikDy49Qg6L3K6ncbvs" }
```

**Response `200`** — cùng hình dạng với `/login`: `{ accessToken, expires, refreshToken, user }`.
`refreshToken` trong response là token **mới**; token vừa gửi lên bị thu hồi ngay (xoay vòng — *rotation*),
nên FE phải lưu đè token mới sau mỗi lần đổi.

**Các lỗi**

| Tình huống | Status | `type` / `title` |
|---|---|---|
| `refreshToken` thiếu hoặc toàn dấu cách | `422` | `errors.refreshToken = "Thiếu refresh token"` |
| Không gửi body | `400` | `/errors/bad-request` |
| Không tồn tại, đã hết hạn, hoặc đã bị thu hồi | `401` | `/errors/refresh-token-invalid`, `Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.` |

Ba trường hợp `401` trùng cả `type` lẫn `title`: FE xử lý như nhau (đưa về màn đăng nhập), và người dò
không biết được token nào từng tồn tại. `type` **khác** `/errors/token-expired` là có chủ đích: FE gặp
`token-expired` thì tự gọi refresh — nếu refresh hỏng cũng trả `token-expired`, FE sẽ gọi refresh mãi
không dừng.

**Phát hiện token bị dùng lại.** Token đã thu hồi vẫn nằm trong bảng `refresh_tokens` cho tới khi quá hạn
(xem "Dọn token quá hạn" bên dưới). Nếu một token
**đã đổi rồi** lại được gửi lên, nghĩa là có hai người cùng cầm nó — token đã lọt ra ngoài. BE thu hồi
**mọi** refresh token còn sống của user đó (mọi thiết bị) rồi mới trả `401`. Chủ thật đăng nhập lại bằng
mật khẩu, kẻ gian thì không làm được. Ai đưa token lên sau — chủ thật hay kẻ gian — cũng làm lộ chuyện, nên
kẻ gian giữ được quyền truy cập nhiều nhất tới lần đổi token kế tiếp. Token chỉ **hết hạn** thì trả `401`,
không thu hồi hết.

Đo với một user đăng nhập trên 2 thiết bị: đổi T1 → `200`, nhận T2; T1 bị thu hồi, T2 và token của thiết bị
kia vẫn sống. Gửi lại T1 → `401`, đồng thời T2 lẫn token thiết bị kia bị thu hồi; đổi T2 → `401`; đăng nhập
lại bằng mật khẩu thì bình thường.

**Logic** — một transaction ở `AuthService.refresh`:

1. `RefreshTokenRepository.findByTokenHashForUpdate(sha256(token))` — `@Lock(PESSIMISTIC_WRITE)` +
   `JOIN FETCH rt.user`, Hibernate sinh `… for update of rt1_0`. Không thấy → `401`.
2. Đã thu hồi → `revokeAllActiveByUserId` (một câu `UPDATE`) → `401`.
3. Hết hạn → `401`.
4. Thu hồi token này, phát cặp token mới bằng `buildAuthResponse` — dùng chung với login.

Một lần đổi token chạy **3 câu SQL**: `select … for update` (kèm user), `insert` token mới, `update` thu hồi
token cũ (ghi lúc commit). Không có câu `select users` nào vì user đã nạp kèm ở câu đầu.

**Ba chỗ dễ sai** — đã đo bằng cách sửa tạm cho sai rồi khôi phục:

| Chỗ | Code đúng | Làm sai thì |
|---|---|---|
| `@Transactional(noRollbackFor = InvalidRefreshTokenException.class)` trên `AuthService.refresh` | Gửi lại T1 → `401`, T2 bị thu hồi | Bỏ `noRollbackFor`: vẫn `401`, nhưng exception làm transaction rollback — câu thu hồi hết bị huỷ theo. **T2 vẫn sống, kẻ gian cầm T2 đổi tiếp được (`200`)** |
| `RefreshTokenService.consume()` **không** gắn `@Transactional`, chạy nhờ transaction của người gọi | như trên | Gắn thêm `@Transactional` (kể cả mặc định `REQUIRED`): exception đi qua proxy của `consume` làm cả transaction bị đánh dấu "chỉ được rollback", `noRollbackFor` ở ngoài không cứu được → **`500 UnexpectedRollbackException`**, T2 vẫn sống, kẻ gian đổi được (`200`) |
| `@Lock` trên câu tìm token | 2 request đổi cùng một token cùng lúc ×10 → luôn 1×`200` + 1×`401`; 5 request ×3 → luôn 1×`200` + 4×`401` | Bỏ `@Lock`: **10/10 lượt cả hai cùng `200`** — một token đẻ ra hai token con, việc phát hiện dùng lại bị qua mặt |

Hệ quả của khoá: với request đến sau, "hai request đổi cùng lúc" trông y hệt kẻ gian dùng lại token, nên mọi
token của user bị thu hồi và người dùng bị đăng xuất. Vì vậy FE **chỉ được gửi một request refresh tại một
thời điểm** — xem §12.1.

#### Access token mang sẵn `role`, filter không hỏi DB

Payload của access token: `{"sub":"an@gmail.com","role":"USER","iat":…,"exp":…}`. `JwtAuthenticationFilter`
gọi `JwtService.parse()` rồi dựng `UserDetails` thẳng từ `sub` và `role` — **không** truy vấn bảng `users`.
Payload ai cũng đọc được (chỉ là Base64) nhưng không sửa được: đã thử đổi `USER` thành `ADMIN` rồi giữ chữ
ký cũ → `401 /errors/token-invalid`. Token không có claim `role` cũng bị coi là hỏng → `401 token-invalid`.

Mỗi request đã đăng nhập bớt đúng một câu `SELECT users` so với cách để filter nạp user từ DB (đã đo):

| Request | Filter hỏi DB | Role lấy từ token |
|---|---|---|
| `GET /api/users/me` | 2 câu — cả hai là `SELECT users` | 1 |
| `GET /api/cart` | 3 | 2 |
| `GET /api/admin/orders` | 4 | 3 |
| `GET /api/products/{id}` kèm token | 3 | 2 |

Câu `SELECT users` còn lại ở `/users/me`, `/cart`, `/orders` là của service: nó cần `id` của người gọi,
còn token chỉ mang email.

**Cái giá: `role` trong token là bản chụp lúc phát.** Đổi role trong DB chỉ có hiệu lực khi access token hết
hạn và được đổi mới — lúc đổi, refresh token nạp kèm user từ DB nên token mới luôn mang role mới nhất. Vì
vậy access token chỉ sống **15 phút** (`jwt.expiration-seconds=900`): quyền cũ tồn tại tối đa chừng đó. Đã
kiểm: nâng user lên `ADMIN` trong DB → token đang cầm vẫn bị `403` ở API admin, đổi token xong thì `200`; hạ
về `USER` → token đang cầm **vẫn `200`** cho tới khi đổi token, sau đó `403`.

**Không có bean `UserDetailsService`.** App không dùng cơ chế đăng nhập username/password của Spring
Security, nên không khai bean đó. Khi thiếu nó, Spring Boot (`UserDetailsServiceAutoConfiguration`) tự tạo
một user ảo và in `Using generated security password: …` ra log — `ShopeeApplication` exclude cấu hình
này. Đã kiểm: bỏ `exclude` thì dòng log đó xuất hiện.

#### Dọn token quá hạn

Mỗi lần đăng nhập và mỗi lần đổi token đều thêm một bản ghi vào `refresh_tokens`; bản ghi cũ chỉ bị đánh
dấu thu hồi chứ không xoá. Người dùng hoạt động liên tục 8 tiếng là đổi token khoảng 32 lần.
`RefreshTokenService.purgeExpired()` chạy theo lịch — `@Scheduled`, cron đọc từ `jwt.refresh-purge-cron`,
mặc định `0 0 3 * * *` tức 3h sáng giờ Việt Nam (`zone = "Asia/Ho_Chi_Minh"`, để giờ chạy không đổi theo múi
giờ của máy chủ) — và xoá hẳn các bản ghi **đã quá `expires_at`** bằng một câu
`delete … from refresh_tokens where expires_at < ?` (JPQL `@Modifying`, không nạp entity nào lên).

| Bản ghi | Job có xoá không | Vì sao |
|---|---|---|
| Đã thu hồi, **còn** hạn | Không | Còn nó thì mới phát hiện được token bị dùng lại (ở trên) |
| **Quá hạn**, đã thu hồi hay chưa | Có | Đem đi đổi thì đằng nào cũng `401`. Xoá rồi thì chỉ mất báo động dùng lại cho một token vốn đã vô dụng |

Đã kiểm với lịch 5 giây/lần (đặt qua biến môi trường, không sửa file): một lần chạy xoá đúng 11 bản ghi quá
hạn, log `Đã xoá 11 refresh token đã quá hạn` ở luồng `scheduling-1` chứ không phải luồng request; số bản ghi
"thu hồi còn hạn" và "còn dùng được" giữ nguyên; token thu hồi còn hạn đem đi đổi vẫn làm mọi token của user
bị thu hồi. Lần chạy không xoá gì thì không in log. Với lịch mặc định, khởi động app không kích hoạt job.

| Chỗ | Làm sai thì — đã đo |
|---|---|
| `@EnableScheduling` ở `config/SchedulingConfig` | Bỏ đi: Spring lờ mọi `@Scheduled`. Không lỗi, không cảnh báo, không câu `DELETE` nào chạy — bảng cứ phình mà không ai biết |
| `@Transactional` trên `purgeExpired` | Bỏ đi: job chạy ở luồng riêng nên không có transaction sẵn, câu `DELETE` bị từ chối (`TransactionRequiredException`). Mỗi lần chạy chỉ để lại một dòng `ERROR … Unexpected error occurred in scheduled task`, app vẫn phục vụ request bình thường |

Chạy nhiều server thì server nào cũng chạy job — vô hại, lần chạy sau chỉ xoá 0 bản ghi
(`SYSTEM_DESIGN.md` §3.1). Câu xoá quét cả bảng vì `expires_at` không có index — xem `DATABASE_DESIGN.md` §3.8.

#### Giới hạn của v1: mất response đổi token thì bị đăng xuất

BE thu hồi refresh token cũ ngay khi đổi, rồi mới trả cặp mới. Nếu response đó không tới được client (mạng rớt đúng
lúc) thì client vẫn cầm token cũ đã bị thu hồi. Lần đổi sau, BE coi đó là token bị dùng lại (xem ở trên) nên thu
hồi mọi token của user — người dùng bị đăng xuất khỏi mọi thiết bị. FE không cứu được ca này, vì nó không biết BE
đã xử lý request hay chưa.

v1 chấp nhận vì ca này rất hẹp: mạng chỉ cần liền mạch trong vài mili giây của một request đổi token, và chỉ xảy ra
khi access token vừa hết hạn. Muốn sửa thì cho phép token **vừa** bị thu hồi được đổi lại trong một khoảng ân hạn
ngắn (vài chục giây) thay vì coi là bị lộ ngay. Cái giá: báo động token bị lộ yếu đi trong khoảng đó.

---

## 4. Nhóm API: User / Profile

Tất cả endpoint nhóm này cần header `Authorization`.

### 4.1. `GET /api/users/me`

**Quyền:** 🔒 **Đã đăng nhập**, và chỉ thao tác được trên dữ liệu của **chính mình**

**Màn FE:** `Profile` — route `/user/profile`

Lấy thông tin user đang đăng nhập (dựa vào token, không truyền id).

**Response `200`** — object User ở mục 2.1.

### 4.2. `PUT /api/users/me`

**Quyền:** 🔒 **Đã đăng nhập**, và chỉ thao tác được trên dữ liệu của **chính mình**

**Màn FE:** `Profile` — route `/user/profile` (hồ sơ, avatar); `ChangePassword` — route `/user/password` (đổi mật khẩu)

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
  Gửi `newPassword` mà thiếu `password` cũng `422`, với `errors.password = "Vui lòng nhập mật khẩu hiện tại"`.
- **Không** cho đổi `email` và `roles` qua endpoint này. Request DTO không khai báo hai field đó,
  nên client có gửi kèm thì Jackson cũng bỏ qua — không cần code chặn riêng.
- **`null` nghĩa là "đừng đổi", không phải "xoá về trống".** JSON không gửi field và JSON gửi
  `field: null` đều đến BE dưới dạng `null`, nên v1 không phân biệt được hai ca đó. Hệ quả: không có
  cách nào xoá `name`/`phone`/`address` về `null` qua API. Màn `Profile` không có thao tác nào cần
  việc đó nên chấp nhận được; muốn làm đúng thì phải dùng `JsonNullable` cho từng field.
- **Chuỗi rỗng `""` cũng không phải cách xoá, và BE xử lý không đồng nhất:** `name` và `address` bị ghi thành `""`,
  `phone` bị `422` (không khớp regex), `dateOfBirth` bị bỏ qua. Nên FE không gửi ô để trống, và sau mỗi lần lưu đặt
  lại form theo user BE trả về, để ô vừa xoá trống hiện lại giá trị thật.
- **Đổi mật khẩu thu hồi mọi refresh token của user** — mọi thiết bị, kể cả thiết bị đang dùng, không đổi
  được token nữa (người ta thường đổi mật khẩu vì nghi bị lộ). Access token đang cầm vẫn hợp lệ tới lúc hết
  hạn (tối đa 15 phút); FE nên đăng nhập lại ngay bằng mật khẩu mới để có cặp token mới. `UserService` chỉ phát
  `PasswordChangedEvent`; module `auth` nghe và thu hồi, trong cùng transaction — đã kiểm: cho bên nghe ném
  lỗi thì `PUT` trả `500` và mật khẩu **không** bị đổi. Sai mật khẩu hiện tại (`422`) hoặc chỉ sửa tên thì
  không thu hồi gì.

**Response `200`** — user sau khi cập nhật.

### 4.3. `POST /api/users/me/avatar`

**Quyền:** 🔒 **Đã đăng nhập**

**Màn FE:** `Profile` — route `/user/profile`

**Request**: `multipart/form-data`, field name là `image`. Tối đa 1 MiB, nội dung phải là JPEG hoặc PNG.

**Response `201`**
```json
{ "url": "http://localhost:8081/images/82d308f4-89c6-41ad-af00-7a7608583d1f.jpg" }
```
> Trả object có field `url` chứ không trả chuỗi trần — sau này muốn kèm thêm `width`, `size` thì không
> phải đổi contract. FE lấy URL này rồi gọi tiếp `PUT /api/users/me` với field `avatar`.

Endpoint này chỉ lưu file, **không** sửa `users.avatar`. Luật validate, các lỗi, cách lưu và cách phục vụ
ảnh dùng chung với upload ảnh sản phẩm — xem §9.4. Đã kiểm cả luồng của FE: upload → `PUT /api/users/me`
với `avatar` là URL vừa nhận → `200`, rồi `GET` URL đó → `200 image/jpeg`.

---

## 5. Nhóm API: Product

### 5.1. `GET /api/products`

**Quyền:** 🌐 **Công khai** — không cần token

**Màn FE:** `ProductList` (route `/`) và `ProductDetail` (route `/:nameId`, khối "sản phẩm tương tự")

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

**Tham số sai thì xử lý thế nào** — hai kiểu, cố ý khác nhau:

| Trường hợp | Kết quả |
|---|---|
| `sortBy` ngoài 4 giá trị cho phép (kể cả chuỗi rỗng, kể cả sai hoa thường như `Price`) | `422` kèm `errors.sortBy` |
| `order` khác `asc`/`desc` | **bỏ qua**, dùng `desc` |
| `page` < 1 hoặc thiếu | dùng `1` |
| `limit` < 1 hoặc thiếu | dùng `20` |
| `limit` > 100 | ép về `100` |
| `category`, `exclude`, `priceMin`, `priceMax`, `ratingFilter` không phải số | `400` (Spring chặn lúc binding) |

Vì sao `sortBy` nghiêm mà `order` thì không: `sortBy` sai thường là **lỗi lập trình ở FE** (gõ nhầm
tên field), im lặng trả dữ liệu sắp sai thứ tự sẽ khiến FE đi tìm bug ở chỗ khác. Còn `order` chỉ có
hai giá trị và sai thì ý định vẫn rõ, nên nhận mặc định là đủ.

Trần `limit = 100` để một request không kéo được cả bảng về.

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

**Cài đặt:** query param nhận vào record `ProductQuery` (`@ModelAttribute`); `ProductSpecifications.withFilter()`
ghép động các điều kiện lọc bằng `Specification<Product>` (mỗi param là một `Predicate`, param nào null thì bỏ
qua); `ProductService` gọi `findAll(spec, PageRequest.of(page - 1, limit, Sort.by(direction, sortBy)))`. Điều
kiện `deleted_at IS NULL` không phải viết tay — `@SQLRestriction` trên entity tự thêm vào mọi truy vấn (§5.3).

### 5.2. `GET /api/products/{id}`

**Quyền:** 🌐 **Công khai** — không cần token

**Màn FE:** `ProductDetail` — route `/:nameId`

**Response `200`** — Product **dạng đầy đủ** ở mục 2.3 (có `images[]` và `description`).

**Response `404`**
```json
{
  "type": "/errors/not-found",
  "title": "Không tìm thấy sản phẩm id: 12",
  "status": 404
}
```

Mỗi lần gọi thành công thì `view += 1`.

Thực hiện bằng một câu `UPDATE products SET view = view + 1 WHERE id = ?` chứ **không** đọc rồi ghi
lại từ Java — hai người xem cùng lúc mà đọc-rồi-ghi thì mất một lượt đếm, đúng bài race condition ở
`DATABASE_DESIGN.md` mục 5. Để DB tự cộng thì thao tác là nguyên tử.

> Đây là một `GET` có ghi DB, phá quy ước "GET không có tác dụng phụ". Chấp nhận vì tác dụng phụ chỉ
> là một bộ đếm. Cái giá: mỗi lượt xem trang chi tiết là một câu `UPDATE`. Quy mô lớn thì đếm ở
> Redis rồi ghi dồn xuống DB theo lô.


### 5.3. Soft delete và hệ quả của nó

`DELETE /api/admin/products/{id}` **không xoá dòng**, nó ghi `deleted_at = NOW()`
(`DATABASE_DESIGN.md` mục 4.3). Entity mang `@SQLRestriction("deleted_at IS NULL")`, nên Hibernate tự
thêm điều kiện đó vào **mọi** câu query sinh cho `Product` — kể cả câu do `Specification` dựng ở
§5.1. Không phải nhớ viết tay ở từng chỗ.

Hệ quả nhìn thấy được:

| | |
|---|---|
| `DELETE /api/admin/products/{id}` | `204`, dòng vẫn còn dưới DB; mọi dòng giỏ hàng trỏ tới nó bị xoá thật |
| `GET /api/products/{id}` sau đó | `404` |
| `PUT /api/admin/products/{id}` sau đó | `404` |
| `GET /api/products` | sản phẩm biến mất khỏi danh sách và khỏi `totalItems` |

**Gỡ sản phẩm thì giỏ hàng được dọn theo.** Sau khi ghi `deleted_at`, `ProductService.deleteProduct`
phát `ProductDeletedEvent(productId)`; `CartService.onProductDeleted` (`@EventListener`) xoá mọi dòng
`cart_items` trỏ tới sản phẩm đó, của mọi user. Dùng sự kiện thay vì gọi thẳng `CartService` vì
`CartService` đã phụ thuộc `ProductService` (thêm vào giỏ phải đọc sản phẩm) — gọi ngược lại thành vòng
tròn. Đã thử: Spring in sơ đồ vòng `cartService ↔ productService` rồi dừng, app không khởi động.

Listener chạy **đồng bộ**: cùng luồng, cùng transaction với `deleteProduct` (khai `MANDATORY`). Đo với một
sản phẩm nằm trong 4 giỏ:

| | Kết quả |
|---|---|
| `DELETE` → `204` | 4 dòng giỏ của sản phẩm → 0; dòng giỏ của sản phẩm khác còn nguyên; `deleted_at` được ghi |
| SQL | 3 câu: `select products`, `delete ci1_0 from cart_items ci1_0 where ci1_0.product_id=?`, `update products` |
| Gỡ lại lần nữa, hoặc id không tồn tại | `404`, không có câu `delete` nào |
| Đơn cũ có sản phẩm đó | vẫn đọc được, snapshot giữ nguyên |

Câu `update products` đứng **sau** câu `delete`, dù trong code `setDeletedAt` được gọi trước: thay đổi trên
entity nằm trong persistence context và chỉ được ghi lúc commit, còn câu `@Modifying` thì chạy ngay.

Các bản làm sai đã đo (sửa tạm rồi khôi phục):

| Làm sai | Hậu quả đo được |
|---|---|
| Viết `DELETE ci FROM CartItem ci …` (cú pháp MySQL) trong `@Query` | App không khởi động: `BadJpqlGrammarException`. JPQL chỉ có dạng `DELETE FROM CartItem ci WHERE …`; chính Hibernate mới dịch nó sang `delete ci1_0 from cart_items ci1_0 …` của MySQL |
| Thiếu `@Modifying` | `500` (`… must be a 'select' query`). Vì cùng transaction, việc gỡ sản phẩm bị huỷ theo: `deleted_at` vẫn `NULL`, dòng giỏ còn nguyên — không có trạng thái nửa vời |
| Bật `@Modifying(clearAutomatically = true)` | **`204` mà sản phẩm vẫn còn bán**: giỏ đã dọn nhưng `deleted_at` vẫn `NULL`, log không có câu `update products` nào. Persistence context bị xoá trước khi kịp ghi `deleted_at`, và không có lỗi nào báo ra |
| Bỏ `@Query`, để Spring Data tự sinh `deleteByProductId` từ tên method | Vẫn đúng, nhưng 1 `select cart_items` rồi **mỗi dòng một câu `delete`** (3 dòng → 3 câu). Câu `@Query` luôn là 1 câu |

**Một cái bẫy mà `@SQLRestriction` gây ra.** `CategoryService.delete()` hỏi "danh mục này còn sản
phẩm không?" để chặn xoá. Nếu câu hỏi đó đi qua JPQL, Hibernate chèn `and deleted_at is null`, và
một danh mục chỉ còn toàn sản phẩm **đã gỡ bán** sẽ bị coi là rỗng → cho xoá → nhưng dưới DB các dòng
`products` đó vẫn tồn tại và vẫn trỏ khoá ngoại vào danh mục → MySQL từ chối → `500`.

Vì vậy phép đếm ấy cố ý dùng **native SQL** để nằm ngoài tầm với của `@SQLRestriction`:

```java
@Query(value = "select count(*) from products where category_id = :categoryId", nativeQuery = true)
long countByCategoryIdIncludingDeleted(@Param("categoryId") Long categoryId);
```

Nghĩa nghiệp vụ: *"còn sản phẩm là không xoá được danh mục, kể cả sản phẩm đã gỡ bán"* → `409`.

> Hai điều cần nhớ khi viết native query: (1) phải dùng tên **bảng** và tên **cột**, không phải tên
> entity và field; (2) kiểu trả về của method phải khớp kiểu cột SQL trả ra — MySQL `EXISTS(...)` trả
> `BIGINT` chứ không phải boolean, khai `boolean` sẽ nhận `ClassCastException`. Cả hai lỗi này chỉ lộ
> ra **lúc chạy**, vì Hibernate không kiểm tra chuỗi native lúc khởi động như với JPQL.

### 5.4. Album ảnh

`images[]` lưu ở bảng `product_images` (`DATABASE_DESIGN.md` §3.4), nối bằng
`@OneToMany(mappedBy = "product", cascade = ALL, orphanRemoval = true)` kèm `@OrderBy("sortOrder ASC")`.

- `POST` và `PUT` nhận **mảng URL**; thứ tự trong mảng chính là `sort_order`. Mỗi URL không được rỗng và
  tối đa 255 kí tự (cột `url VARCHAR(255)`); sai thì `422` với key chỉ đúng phần tử, ví dụ `errors["images[1]"]`.
- `PUT` **thay toàn bộ album**: ảnh cũ không còn trong mảng sẽ bị xoá khỏi DB nhờ `orphanRemoval`.
- Chỉ `GET /api/products/{id}` trả `images[]`; danh sách không trả (§2.3), nên câu truy vấn danh sách
  không đụng tới bảng ảnh.

> Quan hệ này là hai chiều (`Product.images` ↔ `ProductImage.product`), khác với `Category` cố ý một
> chiều ở §6.3. Lý do: ở đây thật sự cần **duyệt** danh sách để dựng `images[]`, còn với `Category`
> chỉ cần một câu đếm. Cái giá của hai chiều là JSON đệ quy vô tận nếu trả entity ra ngoài — đó là lý
> do `ProductResponse`/`ProductSummaryResponse` tồn tại.

---

## 6. Nhóm API: Category

### 6.1. `GET /api/categories`

**Quyền:** 🌐 **Công khai** — không cần token

**Màn FE:** `ProductList` (thanh lọc category bên trái) — route `/`

**Response `200`**
```json
[
  { "id": "1", "name": "Áo thun" },
  { "id": "2", "name": "Điện thoại" }
]
```
> Mảng trực tiếp, không bọc thêm gì. Danh mục ít và ổn định nên không phân trang.

### 6.2. Ba endpoint quản trị danh mục

Đường dẫn và bảng tổng hợp ở §9; phần này chốt hợp đồng chi tiết.

**Quyền:** 👑 **ADMIN** cho cả ba. **Màn FE:** chưa có màn admin.

| Method | Path | Thành công | Lỗi |
|---|---|---|---|
| `POST` | `/api/admin/categories` | `201` + object vừa tạo | `422` trùng tên · `422` tên rỗng/quá 255 |
| `PUT` | `/api/admin/categories/{id}` | `200` + object sau khi sửa | `404` không có id · `422` trùng tên danh mục **khác** |
| `DELETE` | `/api/admin/categories/{id}` | `204` body rỗng | `404` không có id · `409` còn sản phẩm thuộc danh mục |

Body của `POST` và `PUT` đều là `{ "name": "Áo thun" }`.

**Vì sao trùng tên là `422` chứ không phải `409`:** theo §1.6, `422` là *dữ liệu người dùng nhập vào
không hợp lệ* — mà "tên này đã có người dùng rồi" đúng là loại đó, y hệt ca đăng ký trùng email đang
trả `422` kèm `errors.email`. Còn `409` dành cho *trạng thái hệ thống không cho phép*, đúng với ca xoá
danh mục còn sản phẩm: tên hợp lệ, danh mục tồn tại, chỉ là không được xoá lúc này. (Ngoại lệ duy nhất: hai
request tạo cùng một tên **cùng lúc** thì request sau bị ràng buộc `UNIQUE` chặn và nhận `409` — §1.6.)

```json
POST /api/admin/categories  →  422
{ "type": "/errors/validation", "title": "Dữ liệu không hợp lệ", "status": 422,
  "errors": { "name": "Tên danh mục đã tồn tại" } }
```

> **Bẫy ở `PUT`:** kiểm tra trùng phải loại chính bản ghi đang sửa ra, nếu không thì sửa danh mục mà
> giữ nguyên tên cũ sẽ bị báo "tên đã tồn tại" — nó tìm thấy chính nó. Dùng
> `existsByNameAndIdNot(name, id)` chứ không phải `existsByName(name)`.

### 6.3. Quan hệ `Product` → `Category`

- **`products.category_id` là `NOT NULL`** theo đúng `DATABASE_DESIGN.md` §3.3: `ProductRequest.categoryId`
  bắt buộc (`@NotNull`), `ProductService` tra cứu danh mục, không thấy thì `404`.
- **Quan hệ một chiều.** Chỉ `Product` có `@ManyToOne(fetch = FetchType.LAZY)`; `Category` **không** có
  `@OneToMany`. Câu hỏi duy nhất theo chiều ngược — "danh mục còn sản phẩm không" lúc xoá — là một câu đếm
  (`countByCategoryIdIncludingDeleted`, §5.3), khỏi nạp cả danh sách, và tránh luôn hai cái bẫy của quan hệ
  hai chiều là JSON đệ quy vô tận với `LazyInitializationException`.
- **`@ManyToOne` mặc định là `EAGER`** (ngược với `@OneToMany` mặc định `LAZY`) nên phải ghi `LAZY`
  tường minh.
- **Phân quyền hai lớp.** Endpoint danh mục không cần rule riêng trong `SecurityConfig`: `/api/admin/**` đã
  `hasRole("ADMIN")` và `GET /api/categories/**` đã `permitAll` — ngoại lệ với quy tắc "thêm endpoint phải sửa
  ba chỗ" ở `CLAUDE.md`. `AdminCategoryController` còn mang `@PreAuthorize("hasRole('ADMIN')")` ở mức class:
  đã thử tạm tắt rule `/api/admin/**`, một mình `@PreAuthorize` vẫn trả `403` cho USER.

### 6.4. Nạp kèm danh mục: `@EntityGraph` trên từng câu query

`Product.category` khai `LAZY`, nên sau một truy vấn thuần, field đó **không** giữ một `Category` mà giữ
một **proxy** — class con do Hibernate sinh lúc chạy, bên trong rỗng, chỉ nhớ khoá ngoại. Đo được bằng cách
in class thật:

```
tự tay getEntityById  →  com.duynh.shopee.category.Category
qua findAll thuần     →  com.duynh.shopee.category.Category$HibernateProxy
```

Proxy chỉ đi lấy dữ liệu khi có ai gọi getter của nó. DTO sản phẩm gọi `product.getCategory().getName()` để
dựng object `category` lồng, nên mỗi sản phẩm sinh thêm một câu `select … from categories where id = ?` —
**N+1**: đã đo 5 sản phẩm thuộc 4 danh mục → 5 câu SQL. Nếu trả thẳng entity ra JSON thì proxy còn làm lòi
ra field rác `"category": { …, "hibernateLazyInitializer": {}, … }` (Jackson coi method
`getHibernateLazyInitializer()` là một property) — thêm một lý do để luôn trả DTO.

Lazy loading chỉ chạy được sau khi service đã return nếu `spring.jpa.open-in-view` bật — mặc định của
Spring Boot: session Hibernate, và cả kết nối DB, được giữ suốt request tới lúc Jackson ghi xong JSON.

**App này tắt nó** (`spring.jpa.open-in-view=false`). Bật thì mỗi request giữ kết nối lâu hơn cần thiết
(pool chỉ có 10), và truy vấn có thể lén chạy ngoài service lúc ghi JSON mà không ai thấy. Quy tắc thay thế:
mọi DTO được dựng xong **bên trong** một method `@Transactional` của service, cái gì cần thì nạp kèm bằng
`@EntityGraph`/`JOIN FETCH` hoặc nạp theo lô bằng `@BatchSize` khi transaction còn mở. Đã đo: tắt đi, mọi luồng vẫn
chạy đúng. Tắt còn làm thiếu sót lộ ra thay vì bị giấu: bỏ `@Transactional(readOnly = true)` ở
`OrderService.getMyOrders` → `GET /api/orders` trả `500 LazyInitializationException … (no session)` (dòng
hàng nạp theo lô sau khi transaction đã đóng); bật `open-in-view` lại thì cùng code đó trả `200`, không ai
biết thiếu transaction. `getMyOrder` — một đơn — thì không bị ảnh hưởng vì `@EntityGraph(attributePaths =
"orderItems")` đã nạp dòng hàng ngay trong câu SELECT đầu.

**Cách xử lý đã chọn:** `ProductRepository` ghi đè hai câu và gắn `@EntityGraph` lên từng câu —
`findAll(Specification, Pageable)` cho danh sách nạp kèm `category`, `findById()` cho chi tiết nạp kèm
`category` lẫn `images`. Hibernate nạp kèm trong **cùng một câu SELECT** bằng JOIN, nên field giữ `Category`
thật. Đo lại: `GET /api/products?limit=5` chạy 1 câu SELECT (đã JOIN danh mục) + 1 câu `count` cho phân
trang; `GET /api/products/{id}` chạy 1 câu `UPDATE` tăng `view` (§5.2) + 1 câu SELECT.

Quan hệ **vẫn khai `LAZY`**: đó là mặc định cho mọi truy vấn khác, `@EntityGraph` chỉ nói "riêng câu
này nạp kèm". Annotation gắn lên **từng câu query**, không phải lên entity — chữa `findAll()` không
tự chữa `findById()`.

Hai cách khác đã cân nhắc rồi bỏ:

| Cách | Vì sao bỏ |
|---|---|
| Đổi sang `EAGER` | Mọi truy vấn đụng `Product` đều gánh thêm danh mục, kể cả chỗ không cần |
| `@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})` trên `Category` | Chỉ giấu field rác, **N+1 vẫn còn**; lại là annotation Jackson 2 trong dự án Jackson 3 (xem `CLAUDE.md`) |

---

## 7. Nhóm API: Cart (giỏ hàng)

Tất cả endpoint nhóm này **bắt buộc** có header `Authorization`, và chỉ thao tác trên giỏ hàng của chính
user đang đăng nhập (lấy user từ token, **không** tin `userId` do FE gửi).

Ràng buộc `UNIQUE(user_id, product_id)` dưới DB đảm bảo mỗi user chỉ có tối đa một dòng cho mỗi sản
phẩm — xem [`DATABASE_DESIGN.md` §3.5](DATABASE_DESIGN.md).

### 7.1. `GET /api/cart`

**Quyền:** 🔒 **Đã đăng nhập**, và chỉ thao tác được trên dữ liệu của **chính mình**

**Màn FE:** `Cart` route `/cart`, `Checkout` route `/checkout`, và `Header` cho con số trên icon giỏ

**Response `200`**
```json
[ { "...": "object CartItem ở mục 2.4" } ]
```
Mỗi phần tử nhúng nguyên object product nên FE render được toàn bộ một dòng mà không cần gọi thêm API.

**Thứ tự:** dòng thêm sau nằm trước (`createdAt DESC`). Sắp theo `createdAt` chứ không phải `updatedAt`
— nếu theo `updatedAt` thì mỗi lần người dùng bấm tăng số lượng, dòng đó lại nhảy lên đầu danh sách
ngay dưới con trỏ chuột của họ.

**Phần giỏ chỉ một câu SQL.** Truy vấn dùng `JOIN FETCH ci.product` + `JOIN FETCH p.category`, nên giỏ bao
nhiêu món cũng chỉ một lần xuống DB (cả request là 2 câu: thêm một câu `SELECT users` để lấy id người gọi
từ email trong token — §3.4). Để lazy loading tự xoay thì mỗi dòng giỏ hàng sinh thêm một câu
cho product và một câu cho category — giỏ 30 món thành hơn 60 câu.

**Dòng trỏ tới sản phẩm đã xoá mềm sẽ không xuất hiện.** Xem §7.6.

### 7.2. `POST /api/cart/items` — thêm vào giỏ

**Quyền:** 🔒 **Đã đăng nhập**, và chỉ thao tác được trên dữ liệu của **chính mình**

**Màn FE:** `ProductDetail` (nút "Thêm vào giỏ hàng" và "Mua ngay") — route `/:nameId`

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
   `errors: { "quantity": "Số lượng vượt quá số lượng sản phẩm trong kho" }`.
5. **Không** trừ tồn kho ở bước này — chỉ trừ khi đặt hàng.

Bấm "Thêm vào giỏ" hai lần thật nhanh với sản phẩm **chưa có** trong giỏ: cả hai request cùng thấy "chưa có"
ở bước 2 và cùng tạo dòng mới; `UNIQUE(user_id, product_id)` chặn request sau, request đó nhận `409` (§1.6).
Giỏ lúc đó có số lượng của lần bấm đầu; thử lại thì cộng dồn bình thường. FE nên khoá nút trong lúc chờ
response để ca này hiếm khi xảy ra.

Phép cộng ở bước 2 được thực hiện ở kiểu `long` rồi mới so với tồn kho. Cộng bằng `int` thì hai số
gần 2,1 tỷ cộng lại cho ra **số âm** — và số âm thì lọt qua mọi phép kiểm tra `<= tồn kho`.

**Validation body**

| Trường | Luật | Vi phạm |
|---|---|---|
| `productId` | bắt buộc | `422` |
| `quantity` | bắt buộc, `> 0` | `422` |

Hai trường đều khai kiểu bọc (`Long`, `Integer`) chứ không phải `long`/`int`. Kiểu nguyên thuỷ nhận
giá trị mặc định `0` khi client không gửi field, nên `@NotNull` sẽ **không** bắt được request thiếu
`quantity` — nó lọt vào service như một lệnh "thêm 0 sản phẩm".

### 7.3. `PUT /api/cart/items/{id}` — sửa số lượng

**Quyền:** 🔒 **Đã đăng nhập**, và chỉ thao tác được trên dữ liệu của **chính mình**

**Màn FE:** `Cart` (ô nhập số lượng) — route `/cart`

**Request**
```json
{ "quantity": 5 }
```
> `quantity` là **giá trị tuyệt đối** (gán bằng), không phải cộng thêm — khác với API thêm vào giỏ.

**Response `200`** — dòng giỏ hàng sau khi cập nhật.

Vẫn kiểm tồn kho như §7.2 bước 4. `quantity = 0` → `422`, **không** hiểu là "xoá dòng này": FE đã có
nút xoá riêng ở §7.4, và một `PUT` âm thầm xoá dữ liệu là loại bất ngờ không nên có.

Trả `404` khi: `id` không tồn tại, `id` không thuộc về user đang đăng nhập, hoặc sản phẩm của dòng đó
đã bị xoá mềm. Cả ba trường hợp trả **cùng một** `ProblemDetail` — xem §7.6.

### 7.4. `DELETE /api/cart/items/{id}` — xoá 1 sản phẩm

**Quyền:** 🔒 **Đã đăng nhập**, và chỉ thao tác được trên dữ liệu của **chính mình**

**Màn FE:** `Cart` (nút xoá một dòng) — route `/cart`

**Response `204`** — body rỗng.

`404` trong đúng các trường hợp như §7.3, kể cả khi gọi lại lần thứ hai trên dòng vừa xoá.

### 7.5. `DELETE /api/cart/items?ids=1,2,3` — xoá nhiều sản phẩm

**Quyền:** 🔒 **Đã đăng nhập**, và chỉ thao tác được trên dữ liệu của **chính mình**

**Màn FE:** `Cart` (nút xoá sau khi tick chọn nhiều dòng) — route `/cart`

Dùng cho nút "Xoá" sau khi tick chọn nhiều dòng trong trang giỏ hàng.

**Response `204`** — body rỗng.

Chỉ xoá những dòng thuộc về user đang đăng nhập; id lạ thì bỏ qua, không báo lỗi.

| Đầu vào | Kết quả |
|---|---|
| `?ids=1,2,3` hoặc `?ids=1&ids=2&ids=3` | `204`, xoá những dòng thuộc về mình |
| Trong danh sách có id lạ hoặc id của người khác | `204`, **bỏ qua im lặng**, vẫn xoá các dòng hợp lệ |
| Thiếu hẳn `?ids=` | `400` |
| `?ids=1,abc` | `400` |
| Danh sách rỗng | `422`, `errors: { "ids": "Danh sách id không được để trống" }` |

**Vì sao xoá-một thì `404` mà xoá-nhiều thì im lặng.** Xoá một là người dùng bấm vào đúng một dòng
đang nhìn thấy — dòng đó biến mất là chuyện bất thường, đáng báo. Xoá nhiều là tick 5 dòng rồi bấm
một lần: nếu 1 trong 5 vừa bị xoá ở tab khác mà cả lệnh thất bại thì 4 dòng còn lại vẫn nằm nguyên
đó, tệ hơn hẳn việc lặng lẽ xoá 4 dòng làm được. **Thao tác đơn thì nghiêm khắc, thao tác hàng loạt
thì khoan dung.**

Lệnh xoá chạy bằng **một câu `DELETE ... WHERE id IN (...) AND user_id = ?`** duy nhất. Viết theo
derived query (`deleteAllByIdInAndUserId`) thì Spring Data `SELECT` hết ra rồi gọi `remove()` từng
dòng — xoá 3 dòng thành 4 câu SQL.

### 7.6. Quyền sở hữu, và chuyện sản phẩm biến mất khỏi giỏ

**Ba tình huống, một câu trả lời.** `PUT`/`DELETE` trên một dòng giỏ hàng trả `404` **giống hệt nhau**
cho cả ba: id không tồn tại, id của người khác, và sản phẩm của dòng đó đã bị xoá mềm. `ProblemDetail`
trả về chỉ khác nhau ở trường `instance` (là URL được gọi), còn `type`/`title`/`status` trùng khít.

Đây không phải lười phân loại lỗi. Trả `403` cho trường hợp thứ hai là xác nhận với người hỏi rằng
dòng đó **có tồn tại** — thử lần lượt id từ 1 tới 1000 là đếm được số dòng giỏ hàng của cả hệ thống.

**Cách cài đặt quyết định việc này có đúng hay không.** Điều kiện sở hữu nằm **trong câu query**
(`WHERE ci.id = :id AND ci.user.id = :userId`) chứ không phải trong một câu `if` sau khi đã lấy dữ
liệu ra. Hai lý do:

- "Không tồn tại" và "không phải của bạn" hội tụ thành cùng một `Optional.empty()`, nên chỉ có **một**
  nhánh lỗi để viết — không có chỗ nào để lỡ tay trả `403`.
- Quên viết câu `if` thì code vẫn chạy, vẫn trả `200`, và mọi user sửa được giỏ của nhau mà không có
  triệu chứng gì. Quên một điều kiện trong query thì lỗi lộ ra ngay ở lần thử đầu tiên.

**Sản phẩm bị xoá mềm thì dòng giỏ hàng lặng lẽ biến mất.** `products` dùng xoá mềm
(`@SQLRestriction("deleted_at IS NULL")`), nhưng `cart_items.product_id` vẫn trỏ tới dòng đó — khoá
ngoại này **không** cascade. Hệ quả: `@ManyToOne(optional = false)` tạo một proxy, proxy xuống DB tìm
không thấy, và ném `EntityNotFoundException` → `500` cho **toàn bộ** `GET /api/cart`. Một sản phẩm bị
gỡ khỏi sàn làm hỏng trang giỏ hàng của mọi khách từng bỏ nó vào giỏ.

Cách chặn: dùng `JOIN FETCH` (là **INNER** JOIN) thay vì `@EntityGraph` (Spring Data sinh **LEFT**
JOIN và không cho chọn kiểu join). Với INNER JOIN, điều kiện `deleted_at IS NULL` do `@SQLRestriction`
chèn vào khiến dòng giỏ hàng không khớp được với sản phẩm nào và **rơi khỏi kết quả** — đúng hành vi
mong muốn, và không còn proxy nào để ném lỗi.

Bình thường không có bản ghi nào như vậy: admin gỡ sản phẩm thì các bản ghi giỏ hàng trỏ tới nó bị xoá
ngay trong cùng transaction (§5.3). `JOIN FETCH` vẫn giữ làm lớp chặn thứ hai — một bản ghi giỏ được ghi
đúng lúc admin đang gỡ sản phẩm vẫn có thể lọt qua.

---

## 8. Nhóm API: Order (đơn hàng)

Tất cả endpoint nhóm này **bắt buộc** có header `Authorization`.

### 8.1. `POST /api/orders` — đặt hàng

**Quyền:** 🔒 **Đã đăng nhập**, và chỉ thao tác được trên dữ liệu của **chính mình**

**Màn FE:** `Checkout` — route `/checkout`

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

Record `CreateOrderRequest` không có field nào về tiền, số lượng hay trạng thái. Client gửi kèm
`totalAmount`, `subtotal`, `shippingFee`, `status` thì Jackson bỏ qua — đã thử gửi
`"totalAmount": 1, "status": "DELIVERED"`, đơn vẫn ra đúng tổng tiền và `PENDING`.

**Validation body**

| Trường | Luật | Vi phạm |
|---|---|---|
| `cartItemIds` | bắt buộc, ít nhất 1 phần tử, không phần tử nào `null` | `422` — phần tử `null` báo ở key `cartItemIds[1]` |
| `recipientName` | bắt buộc, không toàn dấu cách, tối đa 255 | `422` |
| `recipientPhone` | bắt buộc, `0` + 9–10 chữ số (§1.7 ³) | `422` |
| `shippingAddress` | bắt buộc, không toàn dấu cách, tối đa 255 | `422` |
| `paymentMethod` | bắt buộc, `COD` hoặc `BANK_TRANSFER`; v1 chỉ nhận `COD` | thiếu → `422`; `BANK_TRANSFER` → `422`; giá trị ngoài enum (`"CASH"`, `"cod"`) → `400` |
| `note` | không bắt buộc, tối đa 500 | `422` |

`cartItemIds` trùng nhau (`["1","1"]`) được bỏ trùng trước khi xử lý, không phải lỗi.

**Response `201`** — object Order ở mục 2.5.

**Các lỗi nghiệp vụ**

| Tình huống | Status | `title` |
|---|---|---|
| Có **dù chỉ một** id không đặt được: không tồn tại, của người khác, hoặc sản phẩm đã ngừng bán | `404` | `Một số sản phẩm trong giỏ hàng không tồn tại hoặc đã bị xóa` |
| Một món không đủ tồn kho | `409` | `Sản phẩm Áo thun nam cổ tròn không đủ số lượng` |
| Các dòng giỏ vừa bị một request khác đặt mất (bấm "Đặt hàng" hai lần) | `409` | `Giỏ hàng vừa thay đổi vui lòng thử lại` |

Cả ba đều **huỷ toàn bộ đơn**: đã kiểm với đơn 2 món mà món thứ hai hết hàng — câu `UPDATE` trừ kho
món thứ nhất đã chạy, rồi bị rollback; kho, giỏ và bảng `orders` đều y như trước request.

**Nghiêm khắc, khác với xoá nhiều ở §7.5.** Xoá nhiều dòng giỏ thì bỏ qua id lạ; đặt hàng thì một id lạ
là từ chối cả đơn. Lặng lẽ bỏ một món khỏi đơn nghĩa là khách trả tiền và nhận hàng khác với thứ họ đã
chọn — thao tác dính tới tiền thì thiếu một là hỏng cả.

**Logic nghiệp vụ** — một `@Transactional` bao trọn. Chi tiết từng bước ghi DB xem
[`DATABASE_DESIGN.md`](DATABASE_DESIGN.md) mục 6. Mỗi luật nằm ở module sở hữu dữ liệu của nó
(`SYSTEM_DESIGN.md` Level 2) — `OrderService` chỉ inject `OrderRepository`, còn lại gọi qua service:

| Bước | Ai làm | Lỗi |
|---|---|---|
| 1–2. Đọc các dòng giỏ được chọn: của chính mình, sản phẩm còn bán, nạp kèm product | `CartService.getItemsForCheckout` | `404` |
| 3. Trừ kho từng món bằng `UPDATE` có điều kiện, cộng `sold` | `ProductService.decreaseStock` | `409` |
| 4–6. Tạo đơn, sinh `orderCode`, chép tên/ảnh/giá vào `order_items`; tổng tiền do `Order.addItem()` tự cộng | `OrderService` | — |
| 7. Xoá các dòng đã đặt — dòng không chọn vẫn nằm lại trong giỏ | `CartService.removeCheckedoutItems` | `409` |

Hai method ghi dữ liệu ở bước 3 và 7 khai `Propagation.MANDATORY`: chỉ chạy được bên trong transaction
của `placeOrder`. Đã kiểm bằng cách gọi lẻ `decreaseStock`: Spring ném
`IllegalTransactionStateException: No existing transaction found for transaction marked with
propagation 'mandatory'` và kho không bị đụng tới.

**Đã kiểm khi chạy song song** (`DATABASE_DESIGN.md` §5): 20 người mua cùng lúc 5 món cuối → đúng 5
đơn; một user gửi cùng lúc 2 request đặt cùng một dòng giỏ → đúng 1 đơn, request kia `409`.

**Số câu SQL** cho một đơn 2 món: **9** — 1 `SELECT users` (service cần `id` của người gọi), 1 `SELECT` giỏ
kèm product, 2 `UPDATE products`, 1 `SELECT` kiểm mã đơn trùng,
1 `INSERT orders`, 2 `INSERT order_items`, 1 `DELETE cart_items`. Không có câu nào nạp lại product
khi dựng response.

### 8.2. `GET /api/orders` — danh sách đơn của tôi

**Quyền:** 🔒 **Đã đăng nhập**, và chỉ thao tác được trên dữ liệu của **chính mình**

**Màn FE:** `Purchase` — route `/user/purchase`

| Param | Kiểu | Mặc định | Ý nghĩa |
|---|---|---|---|
| `status` | enum | — | Lọc theo trạng thái. Bỏ trống = lấy tất cả. Phân biệt hoa thường: `pending` → `400` |
| `page` | int | `1` | Thiếu hoặc `< 1` → `1`. Quá số trang → `items` rỗng, `totalItems` vẫn đúng |
| `limit` | int | `10` | Thiếu hoặc `< 1` → `10`; **tối đa `50`**, lớn hơn thì bị chặn về `50` |

Tham số sai kiểu (`?page=abc`, `?status=ABC`) → `400 /errors/bad-request`, xem §1.6. Nhận qua record
`OrderQuery` + `@ModelAttribute`, cùng kiểu với `ProductQuery`; giá trị thiếu hoặc vô lý được thay bằng
mặc định ngay trong compact constructor.

**Thứ tự:** mới nhất trước — `ORDER BY created_at DESC, id DESC`. Cột `id` là trọng tài: hai đơn trùng
`created_at` tới mili-giây thì DB được phép trả chúng theo thứ tự bất kỳ, và mỗi lần query có thể khác
nhau — trang 1 và trang 2 lệch thứ tự thì một đơn hiện ở cả hai trang còn một đơn biến mất. Thêm một cột
duy nhất làm tiêu chí phụ thì thứ tự luôn xác định.

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

**Số câu SQL không phụ thuộc số đơn trong trang.** Dòng hàng nạp bằng `@BatchSize(size = 50)` trên
`Order.orderItems`: lần đầu cần `items` của một đơn, Hibernate nạp luôn `items` của mọi đơn trong trang
bằng **một** câu `WHERE order_id IN (...)`. Đo được:

| Trang | Câu SQL |
|---|---|
| Trang 1, 10 đơn | 4 — 1 `SELECT users`, 1 `COUNT`, 1 lấy đơn (`LIMIT`), 1 lấy dòng hàng |
| Trang 2, 2 đơn (trang cuối) | 3 — **không có `COUNT`**: trang trả về ít dòng hơn `limit` nên Spring Data tự suy ra tổng số = `offset + số dòng`, khỏi đếm |

`limit` tối đa 50 cho khớp `@BatchSize(50)`: một trang luôn nằm gọn trong một lần nạp. Câu `IN` luôn có
đủ 50 dấu `?` kể cả khi trang chỉ có 10 đơn — Hibernate đệm cho đủ để mọi trang dùng chung một câu
prepared statement.

> **Vì sao không `JOIN FETCH` / `@EntityGraph` trên câu có phân trang.** Nối `orders` với `order_items`
> thì mỗi đơn thành nhiều dòng, `LIMIT 10` sẽ cắt giữa chừng một đơn. Hibernate 5–6 xử lý bằng cách bỏ
> `LIMIT`, tải hết rồi cắt trang trong bộ nhớ. **Hibernate 7.4.1 của project thì không** — đo được: nó
> viết lại thành `select ... from (select ... from orders where user_id=? order by ... limit ?,?) o1_0
> left join order_items ...`, tức phân trang đơn trong subquery trước rồi mới nối dòng hàng; kết quả đúng
> và không có cảnh báo. Vẫn giữ `@BatchSize` vì một annotation trên collection phủ **mọi** câu nạp đơn
> (có lọc `status`, không lọc, danh sách của admin ở §9.2), còn `@EntityGraph` phải gắn lại trên từng
> method — quên một method là N+1 quay về mà không có triệu chứng gì.

### 8.3. `GET /api/orders/{id}` — chi tiết một đơn

**Quyền:** 🔒 **Đã đăng nhập**, và chỉ thao tác được trên dữ liệu của **chính mình**

**Màn FE:** `PurchaseDetail` — route `/user/purchase/:id`

**Response `200`** — object Order đầy đủ.

Đơn không tồn tại và đơn của người khác trả **cùng một** `404` (`Không tìm thấy đơn hàng`) — chỉ khác
`instance`. Quyền sở hữu nằm trong câu query (`findByIdAndUserId`), cùng mẫu với Cart (§7.6).
`/api/orders/abc` → `400`.

**Đơn cũ không phụ thuộc vào sản phẩm còn bán hay không.** Đã kiểm: xoá mềm sản phẩm của một đơn rồi
gọi lại endpoint này → vẫn `200`, tên/giá/`productId` vẫn là bản chụp lúc đặt, và log không có câu SQL
nào đụng tới bảng `products` (chỉ `users`, `orders`, `order_items`). Đó là nhờ `OrderItemResponse`
chỉ gọi `getProduct().getId()` — proxy đã biết sẵn id — xem `DATABASE_DESIGN.md` §3.7.

### 8.4. `PUT /api/orders/{id}/cancel` — huỷ đơn

**Quyền:** 🔒 **Đã đăng nhập**, và chỉ thao tác được trên dữ liệu của **chính mình**

**Màn FE:** `Purchase` và `PurchaseDetail` (hộp thoại `CancelOrderDialog`) — route `/user/purchase`, `/user/purchase/:id`

**Request**
```json
{ "cancelledReason": "Đặt nhầm sản phẩm" }
```

**Validation body**

| Trường | Luật | Vi phạm |
|---|---|---|
| `cancelledReason` | bắt buộc, không toàn dấu cách, tối đa 255 (cột `cancelled_reason`) | `422` |

Không gửi body → `400`.

**Response `200`** — đơn sau khi huỷ (`status = CANCELLED`, `cancelledReason` đã lưu, `updatedAt` mới).

**Các lỗi**

| Trạng thái hiện tại của đơn | Status | `title` |
|---|---|---|
| `PENDING`, `CONFIRMED` | `200` | — huỷ được |
| `SHIPPING` | `409` | `Đơn hàng đang được giao, không thể hủy` |
| `DELIVERED` | `409` | `Đơn hàng đã giao, không thể hủy` |
| `CANCELLED` | `409` | `Đơn hàng đã được hủy trước đó` |
| *(không tồn tại / của người khác)* | `404` | `Không tìm thấy đơn hàng` |

`409` không đụng tới kho: trạng thái được kiểm **trước** khi hoàn kho.

**Logic nghiệp vụ** — một `@Transactional`:

1. Đọc đơn của chính mình **và khoá dòng đó** tới hết transaction:
   `@Lock(LockModeType.PESSIMISTIC_WRITE)` → Hibernate sinh `... for update of o1_0`.
2. `order.cancel(reason)` — cửa **duy nhất** để đơn thành `CANCELLED` (`Order` không có setter). Sai trạng
   thái → `409`.
3. Hoàn kho từng dòng qua `ProductService.increaseStock(productId, quantity)` (`MANDATORY`): cộng trả
   `quantity`, trừ lại `sold`.
4. `saveAndFlush` rồi mới dựng response — `@LastModifiedDate` chỉ được điền lúc flush; `save()` thường
   hoãn việc ghi tới lúc commit, tức **sau** khi response đã dựng xong, nên trả về `updatedAt` cũ. Mọi API
   sửa một bản ghi có sẵn rồi trả lại nó đều dùng `saveAndFlush` vì cùng lý do: `PUT /api/users/me`, sửa danh
   mục, sửa sản phẩm, sửa số lượng giỏ, **thêm vào giỏ khi cộng dồn** (§7.2), huỷ đơn, đổi trạng thái đơn. Tạo
   bản ghi mới thì `save()` là đủ: với id `IDENTITY`, câu `INSERT` chạy ngay lúc `save()`. Đã đo với thêm
   vào giỏ: cộng dồn mà dùng `save()` thì response trả `updatedAt` của lần thêm trước, `GET /api/cart` ngay
   sau đó mới thấy giờ mới.

**Vì sao khoá dòng.** Bấm "Huỷ" hai lần (hoặc hai tab): không có khoá thì cả hai request cùng đọc thấy
`PENDING`, cùng hoàn kho — kho bị cộng gấp đôi. Có khoá thì request sau đứng chờ ngay ở câu `SELECT`, tới
khi request trước commit mới đọc được trạng thái **mới** là `CANCELLED` → `409`. Ở đây dùng khoá bi quan
(`DATABASE_DESIGN.md` §5 cách 3) thay vì `UPDATE` có điều kiện như khi trừ kho, vì huỷ đơn có nhiều nhánh
với câu thông báo riêng rồi còn hoàn kho cho từng dòng — khoá **một dòng** trong một transaction ngắn là
cách đơn giản nhất để cả chuỗi chạy tuần tự. API đổi trạng thái đơn của admin (§9.1) khoá **cùng dòng
đó**, để "khách huỷ" và "admin chuyển sang `SHIPPING`" không chạy chồng lên nhau — đã đo ở §9.1.

Khoá nằm ở `@Lock`, **không** viết `FOR UPDATE` trong chuỗi `@Query`: JPQL không có mệnh đề đó —
`BadJpqlGrammarException: ... mismatched input 'FOR'` và app không khởi động. Hibernate tự dịch `@Lock`
sang cú pháp của từng DB.

Đã kiểm khi chạy song song: 5 request huỷ cùng một đơn → 1 × `200`, 4 × `409`, kho chỉ được cộng lại
một lần. Bỏ `@Lock` thì cả 5 đều `200` và kho bị cộng gấp năm (`DATABASE_DESIGN.md` §5).

**Đơn có sản phẩm đã bị gỡ vẫn huỷ được.** `@SQLRestriction` cũng áp vào câu `UPDATE` hoàn kho, nên với
sản phẩm đã xoá mềm câu đó sửa `0` dòng — và **không** được coi là lỗi: hàng đã gỡ thì không ai bán nữa,
còn ném lỗi thì đơn đó sẽ không bao giờ huỷ được. Đã kiểm với đơn 2 món, 1 món đã gỡ: `200`, món còn bán
được hoàn kho, món đã gỡ giữ nguyên. `increaseStock` nhận `productId` chứ không nhận `Product`, vì trên
`OrderItem.getProduct()` chỉ được gọi `getId()`.

**Thanh toán:** v1 chỉ có COD nên đơn huỷ vẫn `paymentStatus = UNPAID`. `REFUNDED` chỉ có nghĩa khi làm
`BANK_TRANSFER`.

---

## 9. Nhóm API: Admin

**Quyền:** 👑 **Chỉ ADMIN**

**Màn FE:** không có. Chưa có màn admin ở FE — gọi bằng Postman hoặc curl.

`SecurityConfig` chặn cả nhóm bằng `.requestMatchers("/api/admin/**").hasRole("ADMIN")`. Các controller
admin còn mang `@PreAuthorize("hasRole('ADMIN')")` ở mức class — lớp phòng thủ thứ hai (§6.3).
Nhóm này là cách duy nhất để sửa danh mục, sản phẩm và xử lý đơn hàng; dữ liệu mẫu ban đầu do migration seed ở
profile `demo` nạp (`DATABASE_DESIGN.md` mục 9).

| Method | Path | Mô tả |
|---|---|---|
| `POST` | `/api/admin/categories` | Tạo danh mục — body `{ name }` |
| `PUT` | `/api/admin/categories/{id}` | Sửa danh mục |
| `DELETE` | `/api/admin/categories/{id}` | Xoá danh mục (chặn nếu còn sản phẩm) |
| `POST` | `/api/admin/products` | Tạo sản phẩm — body `{ name, description?, price, priceBeforeDiscount?, quantity, image?, images?, categoryId }`, rule ở bảng dưới |
| `PUT` | `/api/admin/products/{id}` | Sửa sản phẩm |
| `DELETE` | `/api/admin/products/{id}` | Xoá sản phẩm — **soft delete**, xem `DATABASE_DESIGN.md` mục 4.3 |
| `POST` | `/api/admin/upload-image` | Upload ảnh sản phẩm, `multipart/form-data`, trả `{ url }` — xem §9.4 |
| `GET` | `/api/admin/orders` | Xem tất cả đơn hàng (mọi user), có phân trang + lọc theo status |
| `PUT` | `/api/admin/orders/{id}/status` | Đổi trạng thái đơn — body `{ status, cancelledReason? }`, xem §9.1 |
| `GET` | `/api/admin/users` | Danh sách user, có phân trang |

Rule của body tạo/sửa sản phẩm (`ProductRequest`), sai thì `422` kèm `errors`:

| Field | Rule |
|---|---|
| `name` | Bắt buộc, tối đa 255 kí tự |
| `description` | Tuỳ chọn, tối đa 10.000 kí tự (`DATABASE_DESIGN.md` §3.3) |
| `price` | Bắt buộc, > 0 |
| `priceBeforeDiscount` | Tuỳ chọn, > 0; `null` = không giảm giá |
| `quantity` | Bắt buộc, ≥ 0 |
| `image` | Tuỳ chọn, tối đa 255 kí tự |
| `images[]` | Tuỳ chọn; từng phần tử không rỗng, tối đa 255 kí tự (§5.4) |
| `categoryId` | Bắt buộc; danh mục không tồn tại → `404` (§6.3) |

### 9.1. `PUT /api/admin/orders/{id}/status` — đổi trạng thái đơn

**Request**
```json
{ "status": "CANCELLED", "cancelledReason": "Hết hàng" }
```

**Validation body**

| Trường | Luật | Vi phạm |
|---|---|---|
| `status` | bắt buộc, một trong 5 giá trị ở §2.5, phân biệt hoa thường | thiếu → `422`; giá trị lạ (`"ABC"`, `"pending"`) → `400` vì Jackson không đọc được enum (§1.6) |
| `cancelledReason` | tối đa 255; **bắt buộc khi `status = CANCELLED`**, không được toàn dấu cách | `422` |

"Bắt buộc khi `CANCELLED`" là luật giữa **hai** field nên annotation không diễn đạt được. Nó nằm đầu
`OrderService.changeStatus` và ném `FieldValidationException`, nên lỗi ra cùng hình dạng `422` với lỗi
annotation (`errors.cancelledReason`). Kiểm trước khi đọc DB: request chắc chắn sai thì không cần khoá
dòng. Gửi `cancelledReason` kèm trạng thái khác thì bị bỏ qua.

**Máy trạng thái** — đã đo đủ 25 cặp (5 trạng thái hiện tại × 5 trạng thái đích):

| Hiện tại ↓ / sang → | `PENDING` | `CONFIRMED` | `SHIPPING` | `DELIVERED` | `CANCELLED` |
|---|---|---|---|---|---|
| `PENDING` | `409` | `200` | `409` | `409` | `200` + hoàn kho |
| `CONFIRMED` | `409` | `409` | `200` | `409` | `200` + hoàn kho |
| `SHIPPING` | `409` | `409` | `409` | `200` + `PAID` | `409` |
| `DELIVERED` | `409` | `409` | `409` | `409` | `409` |
| `CANCELLED` | `409` | `409` | `409` | `409` | `409` |

Chuyển sang chính nó (`PENDING → PENDING`) cũng là `409`: request không đổi được gì thì báo cho admin
biết, thay vì lặng lẽ trả `200`.

`title` của `409`: `Không thể chuyển đơn hàng từ <hiện tại> sang <đích>`, ví dụ `Không thể chuyển đơn
hàng từ Đã giao sang Chờ xác nhận`. Nhãn lấy từ `OrderStatus.getLabel()`. Khác với khách huỷ (§8.4) có
một câu riêng cho từng trạng thái: phía admin có 20 cặp sai nên dùng một câu chung, ghép tên hai trạng thái.

**Luật nằm ở đâu.** `OrderStatus.canTransitionTo(next)` là một `switch` *expression* trên enum; trình
biên dịch bắt phải liệt kê đủ mọi trạng thái, nên thêm trạng thái mới mà quên khai luồng đi thì không
compile được. `Order.changeStatus(next, reason)` là cửa duy nhất để admin đổi trạng thái (khách thì qua
`Order.cancel`) — entity không có setter nào cho `status`.

**Tác dụng kèm theo**

| Chuyển sang | Ngoài `status`, còn đổi gì |
|---|---|
| `CANCELLED` | Lưu `cancelledReason`; hoàn kho từng dòng qua `restock(order)` — **chung** với khách huỷ (§8.4), kể cả trường hợp sản phẩm đã xoá mềm |
| `DELIVERED` (đơn COD) | `paymentStatus` → `PAID`. Đo cả luồng qua API: `CONFIRMED` và `SHIPPING` vẫn `UNPAID`, tới `DELIVERED` thành `PAID`; kho không đổi suốt luồng |
| còn lại | Không đổi gì khác, không đụng kho |

**Logic nghiệp vụ** — một `@Transactional`:

1. Kiểm luật "huỷ phải có lý do" → `422`.
2. `OrderRepository.findByIdForUpdate(id)` — `@Lock(PESSIMISTIC_WRITE)`, **không** có điều kiện `user_id`
   vì admin đổi được đơn của mọi người. Không tồn tại → `404 Không tìm thấy đơn hàng`.
3. `order.changeStatus(...)` — sai luồng → `409`, xảy ra **trước** khi hoàn kho nên `409` không đụng kho.
4. Sang `CANCELLED` thì `restock(order)`.
5. `saveAndFlush` rồi mới dựng response, để `updatedAt` là giờ mới (như §8.4).

**Response `200`** — đơn sau khi đổi, object Order ở §2.5, kèm `items`.

**Chạy cùng lúc với khách huỷ đơn.** Hai API cùng khoá **một dòng** `orders`, nên bên tới sau đứng chờ
ở câu `select … for update` rồi đọc được trạng thái bên kia vừa ghi. Đã đo, mỗi kịch bản 10 lượt, đơn
đang `CONFIRMED` có 2 món:

| Kịch bản | Code thật | Tạm bỏ khoá ở phía admin (`findById`) |
|---|---|---|
| Admin gửi `SHIPPING`, khách gửi huỷ, cùng xuất phát | 10/10: admin `200`, khách `409 Đơn hàng đang được giao, không thể hủy`. Kho giữ nguyên | **8/10 cả hai cùng `200`**: DB ghi `SHIPPING`, lý do huỷ bị xoá mất, **kho vẫn bị cộng 2** — hàng đang đi giao mà kho đã nhận lại, còn khách thì được báo "đã huỷ" |
| Khách gửi huỷ trước 30 ms | 10/10: khách `200`, admin `409 Không thể chuyển đơn hàng từ Đã hủy sang Đang giao`. Kho `+2` | 2/10 cả hai cùng `200`, cùng hậu quả như trên |

Ai thắng không quan trọng; cái được đảm bảo là **chỉ một bên thắng**. Bản làm sai hỏng vì câu `SELECT`
thường của admin không chờ khoá của khách — `SELECT` thường không bao giờ chờ khoá dòng, nó đọc bản đã
commit gần nhất (`DATABASE_DESIGN.md` §6) — nên vẫn thấy `CONFIRMED`. Tới câu `UPDATE` nó mới phải chờ, và khi khách commit xong thì nó ghi đè lên. Hibernate
mặc định `UPDATE` **mọi cột** của entity, nên `cancelled_reason` cũng bị ghi lại thành `NULL`. Bài học:
khoá chỉ có tác dụng khi **mọi** đường ghi vào dòng đó đều khoá — một bên khoá một bên không thì cũng
như không khoá.

### 9.2. `GET /api/admin/orders` — mọi đơn hàng

Giống hệt `GET /api/orders` (§8.2) — cùng record `OrderQuery`, cùng param `status`/`page`/`limit`, cùng
mặc định và mức chặn `limit ≤ 50`, cùng thứ tự `created_at DESC, id DESC`, cùng response
`PagedResponse` kèm `items`. Khác duy nhất: **không** lọc theo `user_id`. Đã kiểm: `totalItems` khớp
`COUNT(*)` của bảng `orders`, 10 đơn đầu đúng là 10 đơn mới nhất, `?status=CANCELLED` khớp số đơn huỷ.

**3 câu SQL mỗi trang:** 1 lấy đơn (`LIMIT`), 1 `COUNT`, 1 lấy dòng hàng (`@BatchSize`). Ít hơn §8.2 một
câu `SELECT users`: danh sách admin không cần `id` của người gọi, nên không tra user nào.

Câu lấy đơn dùng index `idx_order_created`, đo bằng `EXPLAIN` ở `DATABASE_DESIGN.md` §3.6.

### 9.3. `GET /api/admin/users` — danh sách user

| Param | Kiểu | Mặc định | Ý nghĩa |
|---|---|---|---|
| `page` | int | `1` | như §8.2 |
| `limit` | int | `10` | như §8.2, tối đa `50` |

Nhận qua record `UserQuery`, cùng luật với `OrderQuery`. Thứ tự: đăng ký mới nhất trước,
`created_at DESC, id DESC`.

**Response `200`** — `PagedResponse` của object User ở §2.1. **Không có `password`**: `UserResponse`
không có field đó, nên hash không thể lọt ra dù entity `User` có. Đã kiểm trên JSON thật: không có
chuỗi `password`, cũng không có tiền tố BCrypt `$2a$`.

2 câu SQL mỗi trang: 1 lấy trang, 1 `COUNT`. Bảng `users` không có
index `created_at` nên MySQL sắp xếp cả bảng (`EXPLAIN`: `ALL`, `Using filesort`). Với v1 thì chấp nhận
được; khi số user lớn thì thêm index như `idx_order_created`.

### 9.4. Upload ảnh: `POST /api/admin/upload-image` và `POST /api/users/me/avatar`

Hai endpoint dùng chung `ImageStorageService.store()` và chỉ khác nhau ở quyền: ảnh sản phẩm thì 👑 ADMIN,
avatar thì 🔒 đã đăng nhập. Cả hai **chỉ lưu file và trả URL**, không ghi DB. FE lấy URL đó rồi gửi tiếp
trong `POST`/`PUT /api/admin/products` (field `image`, `images[]`) hoặc `PUT /api/users/me` (field `avatar`).

**Request:** `multipart/form-data`, một field tên `image`.
```bash
curl -X POST http://localhost:8081/api/admin/upload-image \
  -H "Authorization: Bearer <token>" -F "image=@ao-thun.jpg"
```

**Response `201`** — tạo ra một file mới, nên theo §1.6 là `201`:
```json
{ "url": "http://localhost:8081/images/43b94864-6a4d-4e6f-8888-fa052744d0f6.png" }
```

**Các lỗi**

| Tình huống | Status | `type` / `title` |
|---|---|---|
| Thiếu field `image`, sai tên field, hoặc file 0 byte | `422` | `errors.image = "Vui lòng chọn ảnh"` |
| Nội dung không phải JPEG/PNG (kể cả HTML đổi đuôi `.jpg`, SVG) | `422` | `errors.image = "Chỉ nhận ảnh PNG hoặc JPEG"` |
| File lớn hơn 1 MiB (1.048.576 byte) | `413` | `/errors/content-too-large`, `Ảnh tải lên tối đa 1MB` |
| Body không phải multipart (ví dụ JSON) | `415` | `… hãy dùng multipart/form-data` |
| Không token / USER gọi endpoint admin | `401` / `403` | |

Thiếu field `image` ra `422` chứ không phải `400`: controller khai `@RequestParam(required = false)` để service
tự kiểm và trả lỗi có tên field, giống thiếu một field trong JSON. Các ca bị từ chối không để lại file nào
trên đĩa.

**Kiểm định dạng bằng nội dung, không bằng thứ client khai.** Tên file, đuôi file và `Content-Type` của
từng phần trong multipart đều do client tự ghi. Service đọc 8 byte đầu và so với *magic number*: JPEG
`FF D8 FF`, PNG `89 50 4E 47 0D 0A 1A 0A`. Đuôi file trên đĩa do **server** đặt theo kết quả đó, nên một
PNG gửi với tên `ao-thun.jpg` và `type=image/jpeg` vẫn được lưu thành `.png`, và lúc phục vụ lại thì
`Content-Type` luôn khớp với nội dung.

Đã đo bản làm sai — lấy đuôi theo tên file client gửi, cách nhiều hướng dẫn trên mạng vẫn làm: file
`hack.html` khai `type=image/png` được nhận `201`, lưu thành `<uuid>.html`, và `GET` URL đó trả `200
Content-Type: text/html` kèm nguyên thẻ `<script>`. Nghĩa là ai có quyền upload cũng đặt được một trang
web chạy JavaScript dưới domain của BE (stored XSS). Code thật trả `422` cho đúng file đó.

Giới hạn: magic number chỉ kiểm phần đầu. File có đầu PNG mà phần sau là rác vẫn lọt qua. Nó không gây
hại, vì được phục vụ với `image/png` kèm header `X-Content-Type-Options: nosniff` (Spring Security tự thêm),
cấm trình duyệt tự đoán lại kiểu file.

**Tên file là UUID**, không dùng tên client gửi. Không ký tự nào của client đi vào đường dẫn, nên không có
chuyện thoát ra khỏi thư mục: gửi `filename="../../../evil.png"` → lưu thành `<uuid>.png` trong
`BE/uploads`, bên ngoài không xuất hiện file nào.

**Nơi lưu:** thư mục `app.upload.dir` (đang là `uploads`, tính từ thư mục chạy app — chạy `./mvnw` trong
`BE/` thì là `BE/uploads`, đã có trong `.gitignore`). Đường dẫn tuyệt đối được in ra log lúc khởi động. Chạy bằng
`docker compose` thì là `/app/uploads` trong container, gắn vào volume `uploads` nên tạo lại container vẫn còn ảnh.
Ở profile `demo`, `/images/**` không thấy file trong thư mục đó thì tìm tiếp ở ảnh của dữ liệu mẫu nằm sẵn trong
jar (`src/main/resources/seed/images`, cấu hình ở `WebConfig`), nên DB vừa dựng có ảnh ngay.
Một server thì đủ dùng; chạy nhiều server thì phải chuyển sang object storage (`SYSTEM_DESIGN.md` §1.3).
Chỉ `ImageStorageService` (ghi) và `WebConfig` (phục vụ) biết file nằm ở đâu, nên lúc đó chỉ phải sửa hai class.

**Giới hạn dung lượng** đặt bằng `spring.servlet.multipart.max-file-size=1MB` (cả request tối đa `2MB`, vì
còn boundary và header). Spring kiểm lúc đọc body, **trước** khi vào controller, rồi ném
`MaxUploadSizeExceededException` → `413`. Đo được: đúng 1.048.576 byte → `201`, thêm 1 byte → `413`.
File 5 MiB và 30 MiB vẫn nhận đủ response `413`, Tomcat không cắt kết nối giữa chừng.

**Phục vụ ảnh: `GET /images/{tên file}`** — 🌐 công khai, vì thẻ `<img>` không gửi được header
`Authorization`. `WebConfig.addResourceHandlers` nối `/images/**` với thư mục upload. Đo được:

| Gọi | Kết quả |
|---|---|
| URL vừa nhận, không token | `200`, `Content-Type` đúng `image/png` / `image/jpeg`, byte giống hệt bản gửi, có `nosniff` |
| Gọi lại kèm `If-Modified-Since` | `304` — trình duyệt dùng bản đã cache |
| File không tồn tại | `404` ProblemDetail |
| `/images/../application.properties`, `/images/..%2f..%2fpom.xml`, `/images/%2e%2e/…` | `401` / `400`, không lộ file nào |

**Dấu `/` cuối của `app.upload.public-url` chỉ được có đúng một lần.** Ghép thêm một dấu nữa trong code thì
URL thành `/images//<uuid>.png`. `StrictHttpFirewall` của Spring Security từ chối mọi URL chứa `//`, kể cả
khi có token hợp lệ, nên client nhận `401` khó hiểu — trong khi URL hỏng đã kịp lưu vào DB. Vì vậy URL
được ghép bằng `publicUrl + filename`, không chèn thêm `/`.

**Giới hạn của v1:** file đã upload mà không ai dùng tới (upload xong không gọi `PUT`, hoặc đổi avatar lần
nữa) vẫn nằm lại trên đĩa, chưa có việc dọn. Cũng chưa giới hạn số lần upload của một user.

---

## 10. Tình trạng hiện thực

| Hạng mục | Hiện tại | Giới hạn / việc tiếp theo |
|---|---|---|
| Hình dạng response thành công | ✅ Trả DTO trần, không vỏ bọc | — |
| Hình dạng lỗi | ✅ `ProblemDetail` (RFC 9457), phủ cả lỗi trong filter | — |
| Đường dẫn | ✅ `/api/*` đã đúng prefix | — |
| `id` dạng string | ✅ Mọi DTO response, kể cả `OrderResponse` và `productId` trong `OrderItemResponse` | — |
| Validation | ✅ Body của Auth, User, Category, Product, Cart, Order, đổi trạng thái đơn của admin qua `@Valid`; luật chéo giữa hai field (huỷ phải có lý do) ở service. Query param của `GET /api/products`, `GET /api/orders`, `GET /api/admin/orders`, `GET /api/admin/users` nhận qua record + `@ModelAttribute`; `sortBy` kiểm ở `ProductQuery.validate()`; sai kiểu → `400` (§1.6) | — |
| CORS | ✅ Đã bật cho `localhost:3000` | — |
| Refresh token | ✅ Phát khi login/register, đổi token có xoay vòng + phát hiện dùng lại (§3.4), logout thu hồi (§3.3), đổi mật khẩu thu hồi mọi token (§4.2), access token mang `role` và sống 15 phút, filter không hỏi DB, job hằng ngày xoá token quá hạn (§3.4) | — |
| Bảo vệ endpoint | ✅ Phân quyền theo nhóm + `STATELESS` | Thêm rule khi có endpoint mới |
| JWT secret, mật khẩu DB | ✅ `application.properties` chỉ ghi `${JWT_SECRET}` / `${DB_PASSWORD}`, không có giá trị mặc định — thiếu thì app không khởi động và báo tên biến. Giá trị lấy từ biến môi trường, hoặc file `BE/.env` (không commit, mẫu ở `BE/.env.example`). Secret cũ còn trong lịch sử git không ký được token nữa: thử ký token ADMIN bằng nó → `401 token-invalid` | — |
| Schema DB | ✅ Flyway: `V1__baseline.sql` dựng 8 bảng với tên ràng buộc tường minh, Hibernate chỉ `validate`; dữ liệu mẫu là migration riêng chỉ chạy ở profile `demo` (`DATABASE_DESIGN.md` mục 9) | Đổi schema = thêm file migration mới |
| Entity & DTO | ✅ 8 entity khớp schema V1 (Hibernate `validate` lúc khởi động); mọi response là DTO (§2), không trả entity | — |
| Entity CartItem | ✅ Entity + 5 endpoint, `UNIQUE(user_id, product_id)`, quyền sở hữu ở tầng service | — |
| Entity Order / OrderItem | ✅ Entity + 4 endpoint §8, đã kiểm cả chạy song song (`DATABASE_DESIGN.md` §5) | — |
| Máy trạng thái đơn | ✅ `PUT /api/admin/orders/{id}/status`: đủ 25 cặp chuyển, huỷ thì hoàn kho, COD giao xong thành `PAID`, khoá chung dòng với khách huỷ (§9.1) | — |
| API quản trị | ✅ Đơn hàng, user, danh mục, sản phẩm, upload ảnh | — |
| Dọn giỏ khi gỡ sản phẩm | ✅ `ProductDeletedEvent` → `CartService.onProductDeleted`, một câu `DELETE`, cùng transaction (§5.3) | — |
| Upload ảnh | ✅ `MultipartFile`, kiểm magic number, tên UUID, tối đa 1MB → `413`, phục vụ ở `/images/**` (§9.4) | File không dùng tới vẫn nằm trên đĩa; chuyển object storage khi chạy nhiều server |
| Phân trang / lọc | ✅ `GET /api/products` (`Pageable` + `Specification`), `GET /api/orders` và `GET /api/admin/orders` (`Pageable` + lọc `status`), `GET /api/admin/users` | — |
| Tranh chấp ràng buộc `UNIQUE` | ✅ Hai request cùng lúc cùng đụng một `UNIQUE` (`users.email`, `categories.name`, `cart_items(user_id, product_id)`, `orders.order_code`): request đến sau nhận `409`, thử lại là đúng; các vi phạm ràng buộc khác vẫn `500` (§1.6) | — |
| Kiểm thử tự động | ⚠️ Chỉ có test `contextLoads` mặc định; các phép đo trong tài liệu chạy bằng request thật, script không nằm trong repo | Viết test tích hợp, chạy trên CI |

---

## 11. Lộ trình v1

| Bước | Nội dung | Kiến thức Java/Spring học được |
|---|---|---|
| **1** ✅ | Nền tảng chung: `GlobalExceptionHandler` trả `ProblemDetail`, validation, bật CORS | `@RestControllerAdvice`, `ProblemDetail`, CORS |
| **2** ✅ | JWT filter + phân quyền thật sự | `OncePerRequestFilter`, `SecurityContextHolder`, filter chain, `AuthenticationEntryPoint` |
| **3** ✅ | Hoàn thiện User + `GET/PUT /api/users/me` | `@AuthenticationPrincipal`, DTO mapping, JPA auditing |
| **4** ✅ | Category CRUD | Quan hệ `@ManyToOne`, `@EntityGraph`, `@EnableMethodSecurity`, constraint tự viết |
| **5** ✅ | Nâng cấp Product: đủ field, phân trang, lọc, sắp xếp, soft delete | `Pageable`, `Specification`, `@SQLRestriction`, `@OneToMany` + `orphanRemoval` |
| **6** ✅ | Cart | Ràng buộc `UNIQUE`, logic cộng dồn, quyền sở hữu theo bản ghi, `JOIN FETCH` |
| **7** ✅ | Order: đặt hàng + huỷ đơn | `@Transactional`, snapshot, race condition tồn kho |
| **8** ✅ | Admin + upload ảnh: đổi trạng thái đơn, danh sách đơn/user cho admin, upload ảnh, dọn giỏ khi gỡ sản phẩm | `@PreAuthorize`, `MultipartFile`, máy trạng thái, Spring event |
| **9** ✅ | Refresh token + đưa `role` vào payload token, hạ TTL, tự dọn token quá hạn — xem §3.4 | Token lifecycle, `SecureRandom`, rollback rule (`noRollbackFor`), `@Scheduled` |

Cả 29 endpoint ở §1.10 đã có; FE người mua gọi đủ phần của nó (§12).

---

## 12. FE: cách nối với API

FE (`FE/`, React + TypeScript + Vite, cổng 3000) gọi đúng contract của tài liệu này. Bảng "màn nào gọi API nào"
nằm ở §1.10. Mục này ghi những chỗ nối khó thấy nếu chỉ đọc từng màn.

### 12.1. `FE/src/utils/http.ts`

Một instance axios dùng chung (`baseURL` là `http://localhost:8081/api/`), gắn `Authorization: Bearer <accessToken>`
vào mọi request. Interceptor response xử lý lỗi theo `type` (§1.6):

- `401` `/errors/token-expired` → gọi `POST auth/refresh-token`, lưu đè **cả** `accessToken` lẫn `refreshToken` mới,
  rồi gửi lại request gốc đúng một lần. Người dùng không thấy gì.
- `401` `/errors/refresh-token-invalid` hoặc `/errors/token-invalid` → xoá phiên (`clearLS`). `AppContext` nghe
  tín hiệu đó, đưa giao diện về trạng thái chưa đăng nhập và xoá dữ liệu riêng đã tải (giỏ, đơn, hồ sơ) nhưng giữ dữ
  liệu công khai (`PUBLIC_QUERY_KEYS`: sản phẩm, danh mục) để trang đang mở không bị trắng; `ProtectedRoute` chuyển
  về `/login`.
- Lỗi khác, trừ `422` → toast hiện `title` (lỗi mạng không có response thì dùng `message` của axios). `422` không
  toast vì mỗi form tự đưa `errors` về đúng ô nhập.

**Chỉ một request refresh tại một thời điểm.** BE xoay vòng token: gửi song song hai lần với cùng một refresh token
thì lần sau bị coi là token bị dùng lại và người dùng bị đăng xuất khỏi mọi thiết bị (§3.4). Vì vậy:

- Nhiều request cùng nhận `token-expired` thì cùng chờ một promise refresh dùng chung.
- Nhiều tab thì xếp hàng qua Web Locks API. Tab đến sau thấy localStorage đã có access token mới hơn thì dùng luôn,
  không đổi thêm lần nữa.
- Refresh token luôn đọc từ localStorage lúc cần chứ không giữ trong RAM, vì tab khác có thể đã xoay vòng.
- Chỉ `401`/`422` từ chính `auth/refresh-token` mới tính là refresh token hỏng. Lỗi mạng hoặc `5xx` thì giữ phiên và
  chỉ báo lỗi.

Các điểm khác:

- Đăng xuất gửi `{ refreshToken }` trong body của `POST auth/logout`, không cần header `Authorization`.
- Đổi mật khẩu thành công thì mọi refresh token cũ bị thu hồi (§4.2), nên `ChangePassword` gọi lại `auth/login`
  bằng mật khẩu mới để có cặp token mới.
- Interceptor lưu token và profile khi response của `auth/login` hoặc `auth/register` về, nhận ra bằng
  `url === apiPath.login || url === apiPath.register`. `constants/apiPath.ts` (đường dẫn API) tách khỏi `constants/path.ts` (route của React
  Router) vì hai thứ này khác nhau dù trùng chữ.

### 12.2. Đọc response

BE không bọc envelope, nên `response.data` chính là DTO, và danh sách phân trang là `response.data.items`. Hai kiểu
dùng chung ở `types/utils.type.ts`:

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

Form bắt lỗi `422` bằng `isAxiosUnprocessableEntityError<ProblemDetail>(error)` rồi đưa
`error.response?.data.errors` về từng ô. Câu hiện cho người dùng lấy từ `title`.

### 12.3. Quy ước dữ liệu

- Field JSON là `camelCase`, `id` là string (kể cả khi gửi lên, như `cartItemIds: string[]`).
- `priceBeforeDiscount` có thể `null` (§2.3, §2.6). `hasDiscount()` trong `utils/utils.ts` quyết định có hiện giá
  gạch và `-x%` hay không.
- Tiền là số nguyên VND, định dạng bằng `formatCurrency`. Ngày của `<input type="date">` có cùng dạng `yyyy-MM-dd` với
  `LocalDate` ở BE; `toISODate` tạo chuỗi đó theo giờ máy.
- Trạng thái đơn là chuỗi enum ở `constants/order.ts`, không phải số.

### 12.4. Tầng API

Mỗi module một file ở `FE/src/apis/`: `auth.api.ts`, `user.api.ts`, `product.api.ts`, `category.api.ts`,
`cart.api.ts`, `order.api.ts`; kiểu dữ liệu tương ứng ở `FE/src/types/*.type.ts`.

Upload file (`user.api.ts` → `uploadAvatar`) phải khai `Content-Type: multipart/form-data` ngay ở request: instance
đặt sẵn `application/json`, gặp header đó axios biến `FormData` thành chuỗi JSON và BE trả `415`.

### 12.5. Route

| Route | Màn | Khung | Ai vào được |
|---|---|---|---|
| `/` | `ProductList` | `MainLayout` | mọi người |
| `/:nameId` | `ProductDetail` | `MainLayout` | mọi người |
| `/login`, `/register` | `Login`, `Register` | `RegisterLayout` | chỉ khi chưa đăng nhập |
| `/cart`, `/checkout` | `Cart`, `Checkout` | `MainLayout` | đã đăng nhập |
| `/user/profile`, `/user/password`, `/user/purchase` | `Profile`, `ChangePassword`, `Purchase` | `MainLayout` + `UserLayout` (menu tài khoản bên trái) | đã đăng nhập |
| `/user/purchase/:id` | `PurchaseDetail` | `MainLayout` | đã đăng nhập |

- React Router ưu tiên route tĩnh (`/cart`, `/login`…) hơn `/:nameId`, nên `ProductDetail` không nuốt các đường dẫn
  này.
- `Checkout` nhận danh sách dòng giỏ đã chọn qua `navigate(path.checkout, { state: { cartItemIds } })`. Vào thẳng
  `/checkout` không qua giỏ thì bị chuyển về `/cart`.

### 12.6. Kiểm tra

`npm run build` (`tsc -b && vite build`) và `npm run lint` trong `FE/`. Chỉ cần kiểm kiểu thì dùng
`npx tsc -p tsconfig.app.json --noEmit`; lệnh `npx tsc --noEmit` ở gốc `FE/` không kiểm file nào vì `tsconfig.json`
chỉ trỏ tới hai project con.
