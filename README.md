# Shopee Clone

Dự án thương mại điện tử gồm hai nửa trong cùng một repo: một frontend React và một backend Spring Boot
được thiết kế lại từ đầu.

Frontend được xây trước theo một contract API cũ. Backend được thiết kế lại từ đầu theo chuẩn REST thông dụng
của hệ sinh thái Java, có tài liệu thiết kế riêng, và **không** bám theo contract cũ đó. Frontend sau đó được
sửa để gọi đúng backend này.

## Trạng thái

> **Hai nửa đã nối với nhau.** Chạy được trọn luồng của người mua: đăng ký → xem và tìm sản phẩm → giỏ hàng →
> đặt hàng → xem và huỷ đơn → hồ sơ và đổi mật khẩu.

| | Trạng thái |
|---|---|
| **Backend** | **v1 xong**: 9/9 bước của lộ trình, đủ 29/29 endpoint, hồi quy cuối 242/242 ca. Có: đăng ký, đăng nhập, đăng xuất bằng JWT kèm refresh token (xoay vòng, phát hiện token bị lộ, tự dọn token quá hạn); phân quyền USER/ADMIN theo từng nhóm endpoint; xử lý lỗi theo RFC 9457; hồ sơ cá nhân; danh mục; sản phẩm có phân trang, lọc, sắp xếp, album ảnh, xoá mềm; giỏ hàng; đặt hàng, xem đơn và huỷ đơn; admin xem mọi đơn, đổi trạng thái đơn theo máy trạng thái, xem danh sách người dùng; upload ảnh sản phẩm và avatar; gỡ sản phẩm thì tự dọn khỏi giỏ hàng của mọi người |
| **Frontend** | Xong phần người mua: danh sách và chi tiết sản phẩm (tìm kiếm, lọc, phân trang), đăng ký/đăng nhập với refresh token tự đổi, giỏ hàng, đặt hàng (từ giỏ hoặc "Mua ngay"), đơn mua (tab trạng thái, chi tiết, huỷ), hồ sơ kèm avatar, đổi mật khẩu. Gọi đúng contract của backend. Chưa có màn quản trị |
| **Tích hợp** | Xong — cách frontend nối với API ở `docs/API_SPEC.md` mục 12, màn nào gọi API nào ở mục 1.10 |

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
| [`docs/API_SPEC.md`](docs/API_SPEC.md) | Contract API giữa hai nửa. Mục **1.10** là bảng tổng hợp cả 29 endpoint: ai gọi được (công khai / đã đăng nhập / ADMIN), đã làm chưa, và màn frontend nào gọi tới kèm route của màn đó |
| [`docs/DATABASE_DESIGN.md`](docs/DATABASE_DESIGN.md) | Schema 8 bảng, kèm lý do đằng sau từng quyết định (vì sao giỏ hàng không lưu giá, vì sao đơn hàng phải snapshot, vì sao chưa làm biến thể sản phẩm) |
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
- **Đơn hàng chụp lại (snapshot)** tên, giá, ảnh sản phẩm và thông tin nhận hàng tại lúc đặt. Sản phẩm
  đổi giá hay bị gỡ khỏi sàn thì đơn cũ vẫn hiển thị đúng như lúc mua.
- **Tồn kho đúng cả khi nhiều người mua cùng lúc.** Kiểm tra và trừ kho nằm trong cùng một câu `UPDATE`
  có điều kiện; bấm "Đặt hàng" hai lần không tạo ra hai đơn; huỷ đơn khoá dòng để kho không bị hoàn hai
  lần. Đã thử bằng request gửi song song: 20 người cùng mua 5 món cuối → đúng 5 đơn, 15 người nhận `409`.
- **Refresh token xoay vòng và tự phát hiện token bị lộ.** Access token chỉ sống 15 phút; mỗi lần đổi
  token thì token cũ bị thu hồi và cấp token mới. Một token đã đổi rồi mà vẫn bị gửi lên nghĩa là có hai
  người cùng cầm nó, nên mọi phiên đăng nhập của người đó bị thu hồi. Database chỉ lưu bản băm SHA-256 của
  refresh token, không lưu chuỗi gốc.

## Chạy thử

**Cần có:** JDK 21, MySQL 8, Node 18+

### Backend

```bash
mysql -u root -p -e "CREATE DATABASE shopee_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"

cd BE
cp .env.example .env    # rồi điền DB_PASSWORD và JWT_SECRET — cách tạo secret ghi ngay trong file
./mvnw spring-boot:run
```

Secret không nằm trong `application.properties`: file đó chỉ ghi `${JWT_SECRET}` và `${DB_PASSWORD}`, giá trị
lấy từ biến môi trường hoặc từ `BE/.env` (không commit; biến môi trường thật được ưu tiên). Thiếu thì app
không khởi động và báo tên biến còn thiếu.

API chạy ở `http://localhost:8081`. Hibernate tự tạo bảng (`ddl-auto=update`). Ảnh upload lưu ở `BE/uploads/`
(tính từ thư mục chạy app, đường dẫn đầy đủ in ra log lúc khởi động) và được phục vụ ở `/images/**`.

Thử nhanh:

```bash
curl -X POST http://localhost:8081/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"test@gmail.com","password":"123456"}'
```

Đăng ký luôn tạo tài khoản role `USER`. Muốn có admin thì `UPDATE users SET role='ADMIN' WHERE email=...`.

**Dữ liệu mẫu.** Để giao diện có sản phẩm, đơn hàng, giỏ hàng trông như thật, chạy khi app đang chạy và DB còn trống:

```bash
pip install pillow
python3 BE/scripts/seed.py
```

Script nạp 12 danh mục, 69 sản phẩm (207 ảnh, tải từ DummyJSON rồi upload qua API), 8 đơn ở đủ các trạng thái
và 5 món trong giỏ, đi qua chính API của backend. Nó từ chối chạy nếu DB đã có dữ liệu. Tài khoản tạo ra, mật
khẩu đều là `123456`: `admin@shopeeclone.vn` (ADMIN), `an.nguyen@gmail.com`, `linh.tran@gmail.com`,
`minh.le@gmail.com`. Cần lệnh `mysql` trong PATH.

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
| ✅ | 3. Hoàn thiện User, `GET/PUT /api/users/me` | |
| ✅ | 4. Category CRUD | |
| ✅ | 5. Nâng cấp Product: đủ field, phân trang, lọc, sắp xếp, soft delete | |
| ✅ | 6. Giỏ hàng | |
| ✅ | 7. Đơn hàng: đặt, xem và huỷ | |
| ✅ | 8. Nhóm API admin + upload ảnh | |
| ✅ | 9. Refresh token | |

Sau 9 bước là một đợt dọn dẹp chốt v1: trả `409` khi hai request cùng lúc trùng ràng buộc `UNIQUE`, chặn
dữ liệu vượt độ dài cột, chuyển secret sang biến môi trường, tắt `open-in-view`, rà lại toàn bộ tài liệu.
Chi tiết từng bước ở `docs/API_SPEC.md` mục 11.

## Những gì chưa làm

Liệt kê thẳng để khỏi phải đọc code mới biết:

- Ảnh upload lưu trên đĩa của máy chạy app — chạy nhiều server thì phải chuyển sang object storage; file
  upload rồi không dùng tới chưa được dọn
- Chưa có màn quản trị ở frontend — các endpoint admin hiện gọi bằng Postman
- **Chưa có test tự động.** Mỗi bước của backend được kiểm chứng bằng cách chạy app rồi gọi API thật cho
  toàn bộ các ca và đối chiếu status code — phần đặt hàng còn thử cả request gửi song song. Các màn của
  frontend được kiểm bằng cách chạy trên trình duyệt. Các script đó chưa nằm trong repo; viết test tự động
  là việc cần làm
- Chưa dùng Flyway — schema do Hibernate tự tạo từ entity (`ddl-auto=update`), không có lịch sử thay đổi.
  Đủ cho học và chạy local, không dùng cho production (`docs/DATABASE_DESIGN.md` mục 9)
- Người dùng không xoá trống được tên, số điện thoại, địa chỉ đã nhập: backend hiểu `null` là "đừng đổi"
  (`docs/API_SPEC.md` §4.2)
- Mất response lúc đổi refresh token (mạng rớt đúng lúc) thì người dùng bị đăng xuất; backend chưa có thời gian
  ân hạn cho token vừa bị thu hồi (`docs/API_SPEC.md` §3.4)
- Access token không thu hồi được: đăng xuất và đổi mật khẩu thu hồi refresh token ngay, nhưng access
  token đang cầm vẫn dùng được tới khi hết hạn (tối đa 15 phút); đổi role trong database cũng cần tối đa
  chừng đó mới có hiệu lực
