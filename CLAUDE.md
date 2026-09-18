# Shopee Clone — monorepo

```
docs/   thiết kế dùng chung cho cả hai nửa — ĐỌC TRƯỚC KHI SỬA BẤT KỲ ĐÂU
BE/     Spring Boot 4.1 / Java 21 / MySQL — port 8081
FE/     React + TypeScript + Vite — port 3000
```

## Đọc `docs/` trước

| File | Nội dung |
|---|---|
| `docs/API_SPEC.md` | Contract API giữa BE và FE. **§1.10 là bảng quyền chính thức**: cả 32 endpoint, ai gọi được, đã làm chưa, và **màn FE nào gọi tới** kèm route của màn đó. §11 là lộ trình 9 bước. |
| `docs/DATABASE_DESIGN.md` | Schema v1 (7 bảng) và lý do đằng sau từng quyết định |
| `docs/SYSTEM_DESIGN.md` | Lộ trình mở rộng, chưa cần cho v1 |

## Đang làm tới đâu

**Nguồn duy nhất về tiến độ:** cột trạng thái ở `docs/API_SPEC.md` **§10** (từng hạng mục) và **§11**
(lộ trình 9 bước, bước nào xong có dấu ✅). §1.10 có cột `BE` cho từng endpoint. Đọc ba chỗ đó trước
khi làm gì — đừng suy ra tiến độ từ code, và khi làm xong một bước thì cập nhật lại cả ba.

## Thứ tự làm việc

**Làm BE cho đúng và đầy đủ trước, FE sửa sau.** FE hiện vẫn gọi contract API cũ
(`{message, data}`, field `snake_case`); danh sách việc cần sửa ở FE nằm ở
`docs/API_SPEC.md` §12. Đừng viết thêm code FE theo contract cũ đó.

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
- Project dùng **Jackson 3** (`tools.jackson.databind`), **không** phải `com.fasterxml.jackson`.
  Jackson 2 có trên classpath nhưng chỉ ở scope `runtime` do `jjwt-jackson` kéo vào.
- `HttpStatus.UNPROCESSABLE_CONTENT` (không dùng `UNPROCESSABLE_ENTITY` — đã deprecated ở Spring 7).

## Chạy

```bash
# BE
cd BE && ./mvnw spring-boot:run          # cần JAVA_HOME trỏ tới JDK 21
cd BE && ./mvnw clean compile            # kiểm tra compile thật

# FE
cd FE && npm run dev
```

> Compiler của VSCode Java extension vẫn sinh `.class` cho code sai (thay thân method bằng
> `throw new Error(...)`), nên **file `.class` mới không chứng minh code compile được**.
> Dùng `./mvnw clean compile` khi cần chắc chắn.

## Cách làm việc mà chủ repo muốn

BE là phương tiện để học Java — **giải thích, đừng chỉ đưa code**. Nói rõ sửa file nào, nội dung gì,
và **vì sao**. Khi làm xong một việc thì **chạy thật và curl kiểm chứng**, in bảng kết quả, đừng chỉ
báo "đã xong".
