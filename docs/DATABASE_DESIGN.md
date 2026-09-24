# Thiết kế Database — Ecommerce (Shopee Clone)

> Tài liệu này chốt schema cho **v1** và vạch lộ trình nâng cấp lên v2/v3.
> Nguyên tắc: làm nhỏ nhưng làm đúng — những quyết định khó sửa về sau (kiểu dữ liệu tiền, snapshot dữ liệu
> giao dịch, ranh giới giữa giỏ hàng và đơn hàng) thì làm chuẩn ngay từ đầu; những thứ dễ thêm sau
> (đánh giá, voucher, nhiều shop) thì để dành.

---

## 1. Phạm vi từng phiên bản

| Phiên bản | Gồm gì |
|---|---|
| **v1 — làm ngay** | 7 bảng: `users`, `categories`, `products`, `product_images`, `cart_items`, `orders`, `order_items` |
| **v2 — nâng cấp** | `addresses`, `product_variants`, `reviews`, `vouchers` |
| **v3 — mô hình sàn** | `shops`, `payments`, `shipments`, `inventory_logs`, `notifications` |

---

## 2. ERD v1

```
┌──────────────┐
│  categories  │
├──────────────┤
│ id (PK)      │
│ name         │
└──────┬───────┘
       │ 1
       │ n
┌──────▼───────────────┐        ┌────────────────┐
│      products        │──1:n──►│ product_images │
├──────────────────────┤        ├────────────────┤
│ id (PK)              │        │ id (PK)        │
│ category_id (FK)     │        │ product_id (FK)│
│ name, description    │        │ url            │
│ price                │        │ sort_order     │
│ price_before_discount│        └────────────────┘
│ quantity  ← tồn kho  │
│ sold, view, rating   │
│ image, deleted_at    │
└───┬──────────────┬───┘
    │ 1            │ 1
    │ n            │ n
┌───▼────────────┐ │        ┌──────────────────────┐
│   cart_items   │ │        │        users         │
├────────────────┤ │        ├──────────────────────┤
│ id (PK)        │ │   1:n  │ id (PK)              │
│ user_id (FK)   │◄┼────────┤ email (UNIQUE)       │
│ product_id (FK)│ │        │ password, name       │
│ quantity       │ │        │ phone, address       │
│                │ │        │ avatar, role         │
│ UNIQUE(user_id,│ │        └──────────┬───────────┘
│       product) │ │                   │ 1
└────────────────┘ │                   │ n
                   │        ┌──────────▼───────────┐
                   │        │        orders        │
                   │        ├──────────────────────┤
                   │        │ id (PK)              │
                   │        │ user_id (FK)         │
                   │        │ order_code (UNIQUE)  │
                   │        │ status               │
                   │        │ subtotal             │
                   │        │ shipping_fee         │
                   │        │ total_amount         │
                   │        │ recipient_name   ┐   │
                   │        │ recipient_phone  ├ snapshot
                   │        │ shipping_address ┘   │
                   │        │ payment_method       │
                   │        │ payment_status       │
                   │        └──────────┬───────────┘
                   │                   │ 1
                   │                   │ n
                   │        ┌──────────▼───────────┐
                   │   n    │     order_items      │
                   └───────►├──────────────────────┤
                            │ id (PK)              │
                            │ order_id (FK)        │
                            │ product_id (FK)      │
                            │ product_name     ┐   │
                            │ product_image    ├ snapshot
                            │ price            │   │
                            │ price_before_disc┘   │
                            │ quantity             │
                            └──────────────────────┘
```

Hai nhánh cần phân biệt rõ ngay từ đầu:

- **`cart_items`** — giỏ hàng. Dữ liệu tạm, thay đổi liên tục, **không lưu giá** (luôn hiển thị giá hiện
  tại của sản phẩm).
- **`orders` + `order_items`** — đơn hàng. Dữ liệu giao dịch, **bất biến sau khi tạo**, chụp lại mọi thứ
  tại thời điểm đặt.

### Vì sao `cart_items` và `orders` không nối với nhau?

Đơn hàng được tạo **từ** giỏ hàng, nhưng giữa hai bảng **cố ý không có khoá ngoại**. Cần phân biệt hai
loại quan hệ:

| Loại | Ví dụ | Có lưu vào DB không? |
|---|---|---|
| **Quan hệ cấu trúc** — đúng lâu dài | `orders → users`, `order_items → orders` | Có, bằng khoá ngoại |
| **Quan hệ quy trình** — chỉ tồn tại trong một khoảnh khắc | `cart_items → orders` | Không |

Lý do rất cụ thể: **sau khi đặt hàng xong, các dòng giỏ hàng bị xoá** (bước 7, mục 6). Nếu đặt khoá ngoại
`cart_items.order_id` thì ngay sau khi đơn được tạo, những dòng mang khoá ngoại đó đã biến mất — một cột
không bao giờ có dữ liệu để đọc. Khoá ngoại dùng để mô tả sự thật **bền vững**, còn giỏ hàng chỉ là
nguyên liệu đầu vào của một quy trình.

Lưu ý thêm: cả hai bảng đều trỏ tới `users`, nhưng **cùng có chung một cha không tạo thành quan hệ giữa
hai con**. Join chúng qua `user_id` sẽ cho ra tích của mọi dòng giỏ hàng với mọi đơn hàng của người đó —
kết quả vô nghĩa. Chúng là quan hệ anh em, không phải cha–con.

Tách rời như vậy còn mang lại hai lợi ích:

1. **Đơn hàng tự đứng vững một mình.** Nhờ snapshot (mục 4.1), `order_items` đã chép sẵn tên, ảnh, giá —
   đơn hàng không cần biết nó sinh ra từ đâu, cũng không phụ thuộc vào việc sản phẩm sau này còn tồn tại
   hay không.
2. **Giỏ hàng không phải nguồn duy nhất.** Nút "Mua ngay" ở trang chi tiết sản phẩm đặt hàng thẳng, không
   qua giỏ. Nếu `orders` bị buộc chặt vào `cart_items` thì luồng này không làm được, hoặc phải tạo dòng
   giỏ hàng giả rồi xoá ngay.

Ví von: giỏ hàng như cái rổ nhựa trong siêu thị, đơn hàng như tờ hoá đơn in ở quầy. Hàng chuyển từ rổ sang
hoá đơn, rồi rổ được trả về chỗ cũ. Tờ hoá đơn không có dòng nào ghi "hàng này lấy từ rổ số 7" — tự nó
đã đầy đủ thông tin.

---

## 3. Chi tiết từng bảng

### 3.1. `users`

```sql
CREATE TABLE users (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password      VARCHAR(100) NOT NULL,
    name          VARCHAR(255)     NULL,
    phone         VARCHAR(20)      NULL,
    address       VARCHAR(255)     NULL,
    date_of_birth DATE             NULL,
    avatar        VARCHAR(255)     NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER',
    created_at    DATETIME     NOT NULL,
    updated_at    DATETIME     NOT NULL
);
```

| Cột | Ghi chú |
|---|---|
| `email` | `UNIQUE` ở tầng DB, **không chỉ** kiểm tra ở tầng code. Hai request đăng ký cùng lúc cùng email có thể cùng vượt qua bước `findByEmail()` — chỉ ràng buộc DB mới chặn được triệt để. Độ dài `255` vì RFC 5321 cho phép địa chỉ tới 254 kí tự — cắt ngắn hơn là từ chối email hợp lệ |
| `password` | Lưu **hash BCrypt**, luôn dài đúng 60 ký tự. `VARCHAR(100)` cho dư. Cột này không bao giờ chứa dữ liệu người dùng nhập nên không áp mặc định 255 |
| `role` | v1 dùng 1 role/user. Khi cần nhiều role → tách bảng `roles` + `user_roles` (n-n) |
| `address` | Địa chỉ mặc định, dạng chuỗi đơn giản. Sổ địa chỉ nhiều mục là v2 |
| `name`, `phone`... | Cho `NULL` vì lúc đăng ký chỉ có email + password |

**Quy ước độ dài cột:** không ghi `length` trong entity khi giá trị bằng mặc định của JPA (255) —
ghi lại chỉ thêm một con số phải giữ đồng bộ với `@Size` ở DTO. Cột nào có con số riêng
(`password VARCHAR(100)`, `phone VARCHAR(20)`, `role VARCHAR(20)`) thì ghi tường minh.

> `length` **không phải là validate**: nó chỉ sinh ra `VARCHAR(n)`. Vượt quá thì MySQL ném
> `ERROR 1406 Data too long`, qua Spring thành `DataIntegrityViolationException` → `500` chứ không
> phải `422`. Ràng buộc DB là *lớp chặn cuối*, không thay được `@Size` ở tầng DTO.

**Giới hạn 72 byte của BCrypt — không liên quan tới độ dài cột.** `BCryptPasswordEncoder` ném
`IllegalArgumentException: password cannot be more than 72 bytes`, tức `500` nếu không chặn từ
tầng validate (đo được: 72 kí tự ASCII → `201`, 73 kí tự → lỗi). Vì thế luật độ dài mật khẩu phải
tính theo **byte**: `@Size` đếm kí tự nên một mật khẩu 25 kí tự tiếng Việt có dấu (3 byte/kí tự =
75 byte) vẫn lọt qua `@Size(max = 72)`. Dùng annotation riêng `@MaxBytes(72)` ở
`BE/.../validation/` cho đúng đơn vị.

### 3.2. `categories`

```sql
CREATE TABLE categories (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(255) NOT NULL UNIQUE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
);
```

`name` để `UNIQUE`: hai danh mục trùng tên là lỗi dữ liệu — không có cách nào phân biệt chúng khi
hiển thị hay khi lọc. Tầng service vẫn kiểm tra trước để trả lỗi thân thiện, nhưng ràng buộc DB mới
chặn được hai request tạo cùng lúc — đúng nguyên tắc mục 4.6.

Danh mục một cấp. Muốn nhiều cấp (Thời trang nam → Áo → Áo thun) thì thêm
`parent_id BIGINT NULL REFERENCES categories(id)` — bảng tự tham chiếu chính nó.

### 3.3. `products`

```sql
CREATE TABLE products (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    category_id           BIGINT       NOT NULL,
    name                  VARCHAR(255) NOT NULL,
    description           TEXT             NULL,
    price                 BIGINT       NOT NULL,
    price_before_discount BIGINT           NULL,
    quantity              INT          NOT NULL DEFAULT 0,
    sold                  INT          NOT NULL DEFAULT 0,
    view                  INT          NOT NULL DEFAULT 0,
    rating                DECIMAL(2,1) NOT NULL DEFAULT 0,
    image                 VARCHAR(255)     NULL,
    deleted_at            DATETIME         NULL,
    created_at            DATETIME     NOT NULL,
    updated_at            DATETIME     NOT NULL,

    CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES categories(id),
    INDEX idx_product_category (category_id),
    INDEX idx_product_price (price),
    INDEX idx_product_created (created_at),
    FULLTEXT INDEX ft_product_name (name)
);
```

| Cột | Ghi chú |
|---|---|
| `price` | **`BIGINT`, đơn vị VND**, không dùng `DOUBLE`/`FLOAT` — xem mục 4.4 |
| `quantity` | Tồn kho. Trừ tại thời điểm **đặt hàng**, không phải lúc thêm vào giỏ. Xem mục 5 |
| `sold`, `view`, `rating` | **Cố tình phi chuẩn hoá** — xem mục 4.2 |
| `deleted_at` | **Soft delete** — xem mục 4.3 |
| `image` | Ảnh bìa, lặp lại một URL trong `product_images`. Chấp nhận trùng để danh sách sản phẩm khỏi phải nạp bảng ảnh — danh sách chỉ trả `image`, album chỉ có ở trang chi tiết (`API_SPEC.md` §2.3). Đồng thời tách "ảnh bìa" khỏi thứ tự album: đổi `sort_order` không làm đổi bìa |

**Về các index:** mỗi index giúp đọc nhanh nhưng làm ghi chậm hơn (mỗi lần INSERT/UPDATE phải cập nhật
thêm index) và tốn dung lượng. Chỉ đánh index cho cột thực sự hay xuất hiện trong `WHERE` / `ORDER BY`.
Ở đây: `category_id` (lọc theo danh mục), `price` (lọc khoảng giá + sắp xếp theo giá),
`created_at` (sắp xếp mới nhất), và full-text cho ô tìm kiếm.

**Trạng thái thực tế:** mới có index trên `category_id` — do MySQL tự tạo kèm khoá ngoại. Hai index
`price` và `created_at` chưa tạo; khai bằng `@Table(indexes = {...})` trong entity là đủ, chưa làm vì
dữ liệu còn nhỏ nên chưa đo được khác biệt. `FULLTEXT` thì JPA không khai được (phải chạy SQL tay) và
hiện cũng **chưa có câu nào dùng tới**: ô tìm kiếm ở `API_SPEC.md` §5.1 làm bằng
`LIKE '%từ khoá%'`, mà câu đó không tận dụng được full-text. Chỉ tạo khi đổi sang `MATCH ... AGAINST`.

> **Vì sao cần FULLTEXT cho tìm kiếm:** câu `WHERE name LIKE '%áo thun%'` **không dùng được index**
> vì có dấu `%` ở đầu — MySQL buộc phải quét toàn bộ bảng. Vài nghìn dòng thì không sao, vài trăm nghìn
> dòng là chậm thấy rõ. `FULLTEXT` + `MATCH ... AGAINST` giải quyết được ở mức vừa; quy mô lớn hơn nữa
> thì dùng Elasticsearch (xem `SYSTEM_DESIGN.md`).

### 3.4. `product_images`

```sql
CREATE TABLE product_images (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT       NOT NULL,
    url        VARCHAR(255) NOT NULL,
    sort_order INT          NOT NULL DEFAULT 0,

    CONSTRAINT fk_image_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    INDEX idx_image_product (product_id)
);
```

Vì sao phải tách bảng riêng thay vì nhét mảng URL vào một cột? Quy tắc chuẩn hoá cơ bản: **một ô không
chứa nhiều giá trị**. Nhét `"url1,url2,url3"` vào một cột `VARCHAR` sẽ khiến bạn không thể sắp xếp,
không thể đếm, không thể xoá một ảnh mà không phải đọc-sửa-ghi lại cả chuỗi.

> JPA có `@ElementCollection` để map thẳng sang `List<String>` mà không cần tạo entity riêng — bản chất
> Hibernate vẫn tạo ra một bảng phụ y như trên. Ở đây tạo entity riêng vì còn có thêm cột `sort_order`.

### 3.5. `cart_items` — giỏ hàng

```sql
CREATE TABLE cart_items (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT   NOT NULL,
    product_id BIGINT   NOT NULL,
    quantity   INT      NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,

    CONSTRAINT fk_cart_user    FOREIGN KEY (user_id)    REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_cart_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT uq_cart_item UNIQUE (user_id, product_id)
);
```

Bảng đơn giản nhất hệ thống, nhưng có sáu điểm đáng chú ý:

**`UNIQUE(user_id, product_id)`** — mỗi user chỉ có tối đa một dòng cho mỗi sản phẩm. Thêm sản phẩm đã
có trong giỏ thì **cộng dồn** `quantity` vào dòng cũ chứ không tạo dòng mới. Ràng buộc đặt ở tầng DB nên
kể cả hai request thêm cùng lúc cũng không tạo ra hai dòng trùng.

**Không có cột giá.** Giỏ hàng luôn hiển thị **giá hiện tại** của sản phẩm — đúng như Shopee thật: sản
phẩm giảm giá trong lúc còn nằm trong giỏ thì khách được hưởng giá mới. Giá chỉ đóng băng tại thời điểm
đặt hàng (xem `order_items`). Đây chính là lý do `cart_items` và `order_items` phải là hai bảng khác nhau
dù nhìn qua khá giống.

**Không trừ tồn kho.** Thêm vào giỏ không giữ chỗ hàng. Có kiểm tra `quantity <= product.quantity` để báo
sớm cho người dùng, nhưng tồn kho chỉ thực sự bị trừ khi đặt hàng.

**`ON DELETE CASCADE` trên `user_id`** — xoá user thì giỏ hàng biến mất theo, hợp lý vì giỏ hàng là dữ
liệu tạm. Ngược lại, khoá ngoại từ `orders` tới `users` **không** cascade: đơn hàng là dữ liệu giao dịch,
phải giữ lại.

JPA thuần **không** sinh được mệnh đề này — `@JoinColumn` chỉ tạo một `FOREIGN KEY` trơn, và xoá user sẽ
bị MySQL chặn vì còn dòng giỏ hàng tham chiếu. Phải dùng annotation riêng của Hibernate:

```java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "user_id", nullable = false)
@OnDelete(action = OnDeleteAction.CASCADE)     // org.hibernate.annotations
private User user;
```

Nó chỉ có tác dụng lúc bảng được **tạo mới**. `ddl-auto=update` không sửa khoá ngoại đã tồn tại, nên bỏ
sót lúc đầu thì sau phải `ALTER TABLE` bằng tay.

**Không cần index riêng cho `user_id`.** Truy vấn hay chạy nhất là `WHERE user_id = ?` (lấy cả giỏ), và
nó đã được `uq_cart_item (user_id, product_id)` phục vụ: index tổng hợp dùng được cho mọi truy vấn lọc
theo **tiền tố trái** của nó — `(user_id)` và `(user_id, product_id)`, nhưng không phải `(product_id)`
một mình. Thêm `INDEX idx_cart_user (user_id)` nữa chỉ tốn chỗ và làm chậm mọi lệnh ghi.

**Khoá ngoại `product_id` không cascade, và đó là một cái bẫy.** `products` dùng xoá mềm, nên dòng sản
phẩm không bao giờ bị `DELETE` thật — `ON DELETE CASCADE` ở đây sẽ không bao giờ kích hoạt. Nhưng
`@SQLRestriction("deleted_at IS NULL")` khiến Hibernate coi sản phẩm đó như không tồn tại, trong khi
`cart_items.product_id` vẫn trỏ tới nó. Nạp dòng giỏ hàng ấy qua `@ManyToOne(optional = false)` sẽ ném
`EntityNotFoundException`. Cách xử lý và lý do chọn nó nằm ở [`API_SPEC.md` §7.6](API_SPEC.md).

### 3.6. `orders` — đơn hàng

```sql
CREATE TABLE orders (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id           BIGINT       NOT NULL,
    order_code        VARCHAR(32)  NOT NULL UNIQUE,
    status            VARCHAR(20)  NOT NULL,
    subtotal          BIGINT       NOT NULL,
    shipping_fee      BIGINT       NOT NULL DEFAULT 0,
    discount          BIGINT       NOT NULL DEFAULT 0,
    total_amount      BIGINT       NOT NULL,
    recipient_name    VARCHAR(160) NOT NULL,
    recipient_phone   VARCHAR(20)  NOT NULL,
    shipping_address  VARCHAR(255) NOT NULL,
    payment_method    VARCHAR(20)  NOT NULL,
    payment_status    VARCHAR(20)  NOT NULL DEFAULT 'UNPAID',
    note              VARCHAR(500)     NULL,
    cancelled_reason  VARCHAR(255)     NULL,
    created_at        DATETIME     NOT NULL,
    updated_at        DATETIME     NOT NULL,

    CONSTRAINT fk_order_user FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_order_user_status (user_id, status),
    INDEX idx_order_created (created_at)
);
```

| Cột | Ghi chú |
|---|---|
| `order_code` | Mã đơn cho người dùng đọc và tra cứu, ví dụ `SP20260916000123`. `UNIQUE` |
| `subtotal` | Tổng tiền hàng, bằng tổng `price × quantity` của các `order_items` |
| `total_amount` | `subtotal + shipping_fee - discount`. Lưu sẵn để khỏi tính lại và để đối chiếu về sau |
| `recipient_*`, `shipping_address` | **Snapshot** thông tin nhận hàng — xem mục 4.1 |
| `payment_method` | v1 chỉ hỗ trợ `COD` |
| `payment_status` | `UNPAID` / `PAID` / `REFUNDED` |
| `discount` | v1 luôn bằng 0, chỉ có ý nghĩa khi làm voucher ở v2 |

**Index tổ hợp `(user_id, status)`** phục vụ đúng câu truy vấn hay dùng nhất: "lấy đơn của tôi ở trạng
thái X". Thứ tự cột trong index tổ hợp có ý nghĩa: index `(user_id, status)` dùng được cho truy vấn chỉ
lọc `user_id`, nhưng **không** dùng được cho truy vấn chỉ lọc `status`. Nguyên tắc: cột lọc trước đặt trước.

**Trạng thái đơn hàng** (`status`):

| Giá trị | Ý nghĩa | Chuyển tiếp được sang |
|---|---|---|
| `PENDING` | Chờ xác nhận | `CONFIRMED`, `CANCELLED` |
| `CONFIRMED` | Đã xác nhận, chờ lấy hàng | `SHIPPING`, `CANCELLED` |
| `SHIPPING` | Đang giao | `DELIVERED` |
| `DELIVERED` | Đã giao — **trạng thái cuối** | — |
| `CANCELLED` | Đã huỷ — **trạng thái cuối** | — |

Khách chỉ được tự huỷ khi đơn còn ở `PENDING` hoặc `CONFIRMED`; đã bàn giao cho đơn vị vận chuyển thì
không. Khi huỷ phải **hoàn lại tồn kho** đã trừ và trừ lại `sold`.

> **Vì sao lưu trạng thái dạng chuỗi chứ không phải số?**
> Trong JPA, `@Enumerated(EnumType.STRING)` lưu `"PENDING"`, còn `EnumType.ORDINAL` lưu `0, 1, 2...`
> theo **thứ tự khai báo trong enum**. Dùng ORDINAL rất nguy hiểm: chỉ cần chèn thêm một giá trị vào
> giữa enum là toàn bộ dữ liệu cũ bị hiểu sai ý nghĩa, mà không có lỗi nào báo ra cả.
> Chuỗi tốn thêm vài byte nhưng nhìn thẳng vào DB là hiểu, và an toàn khi enum thay đổi.
> **Luôn dùng `EnumType.STRING`.**

### 3.7. `order_items` — chi tiết đơn hàng

```sql
CREATE TABLE order_items (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id              BIGINT       NOT NULL,
    product_id            BIGINT       NOT NULL,
    product_name          VARCHAR(255) NOT NULL,
    product_image         VARCHAR(255)     NULL,
    price                 BIGINT       NOT NULL,
    price_before_discount BIGINT           NULL,
    quantity              INT          NOT NULL,
    created_at            DATETIME     NOT NULL,

    CONSTRAINT fk_item_order   FOREIGN KEY (order_id)   REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_item_product FOREIGN KEY (product_id) REFERENCES products(id),
    INDEX idx_item_order (order_id)
);
```

`product_name`, `product_image`, `price` đều là **bản sao tại thời điểm đặt hàng**, không phải tham chiếu
sang `products`. Vẫn giữ `product_id` để có đường dẫn quay về trang sản phẩm, nhưng khi hiển thị đơn hàng
thì đọc từ các cột snapshot.

Không lưu thành tiền từng dòng vì tính được ngay (`price × quantity`) và kết quả không đổi theo thời gian.
Chỉ lưu sẵn những giá trị mà việc tính lại sau này sẽ cho ra kết quả **khác đi**.

---

## 4. Sáu nguyên tắc thiết kế DB cho ecommerce

### 4.1. Snapshot: đơn hàng phải "đóng băng" dữ liệu tại thời điểm mua

Đây là khái niệm **quan trọng nhất** của cả tài liệu.

Sai lầm thường gặp: `order_items` chỉ lưu `product_id`, khi hiển thị đơn hàng thì join sang `products`
để lấy tên và giá. Hậu quả: hôm nay shop đổi giá áo từ 79.000 lên 99.000, thì **toàn bộ đơn hàng cũ trong
lịch sử đột nhiên hiển thị giá mới** — hoá đơn của khách sai, doanh thu thống kê sai. Tệ hơn nữa: sản
phẩm bị đổi tên hoặc gỡ bán thì đơn cũ hiển thị sai hoặc trống.

Vì vậy `order_items` chép lại `product_name`, `product_image`, `price`, `price_before_discount`.
Tương tự, `orders` chép lại `recipient_name`, `recipient_phone`, `shipping_address` — khách đổi địa chỉ
mặc định sau khi đặt thì đơn cũ vẫn phải giữ địa chỉ lúc đặt.

Ngược lại, `cart_items` **không** snapshot gì cả, vì giỏ hàng là dữ liệu tạm, luôn phản ánh trạng thái
hiện tại.

> Quy tắc chung: **dữ liệu giao dịch phải bất biến**. Cái gì ảnh hưởng đến tiền bạc, hợp đồng, nghĩa vụ
> pháp lý thì chép lại; cái gì chỉ là trạng thái tạm thời thì tham chiếu.

### 4.2. Phi chuẩn hoá có chủ đích: `sold`, `rating`, `view`

Theo lý thuyết chuẩn hoá, `sold` (đã bán) không nên tồn tại — nó tính được bằng cách cộng `quantity` của
các `order_items` thuộc đơn đã hoàn tất. Tương tự, `rating` tính được từ trung bình bảng reviews.

Nhưng trang chủ hiển thị 20 sản phẩm, mỗi sản phẩm phải chạy một câu SUM trên bảng `order_items` (bảng lớn
nhất hệ thống) → rất chậm. Nên ta **cố ý lưu dư** giá trị đã tính sẵn vào `products`.

Cái giá phải trả: mỗi lần có đơn hàng mới, phải nhớ cập nhật `sold`. Nếu quên một chỗ → số liệu lệch.
Đây là đánh đổi kinh điển: **đọc nhanh hơn, đổi lại ghi phức tạp hơn và có nguy cơ sai lệch dữ liệu**.

Quy tắc: chỉ phi chuẩn hoá khi (1) dữ liệu đọc nhiều hơn ghi rất nhiều lần, và (2) sai lệch nhỏ không
gây hậu quả nghiêm trọng. `sold` lệch vài đơn không chết ai; **số dư tài khoản** thì tuyệt đối không được
phi chuẩn hoá kiểu này.

### 4.3. Soft delete: không bao giờ xoá thật sản phẩm

Nếu `DELETE FROM products WHERE id = 12` trong khi sản phẩm đó đang được tham chiếu bởi `order_items`:
- Có khoá ngoại → DB từ chối xoá, API văng lỗi 500.
- Không có khoá ngoại → đơn hàng cũ trỏ vào sản phẩm không tồn tại.

Giải pháp: cột `deleted_at DATETIME NULL`. "Xoá" nghĩa là `UPDATE products SET deleted_at = NOW()`.
Mọi truy vấn hiển thị đều thêm điều kiện `WHERE deleted_at IS NULL`.

Nhờ có snapshot ở `order_items`, đơn hàng cũ vẫn hiển thị đầy đủ tên và giá sản phẩm kể cả khi sản phẩm
đã bị gỡ bán — hai nguyên tắc này bổ trợ cho nhau.

> Trong JPA có thể dùng `@SQLRestriction("deleted_at IS NULL")` trên entity để Hibernate tự thêm điều kiện,
> khỏi phải nhớ viết tay ở từng câu query.

**Cái bẫy kèm theo:** `@SQLRestriction` áp cho **mọi** câu query của entity, kể cả những câu hỏi
không nên bị lọc. Ví dụ "danh mục này còn sản phẩm không?" — dùng để chặn xoá danh mục — phải đếm cả
sản phẩm đã gỡ bán, vì chúng vẫn là dòng thật đang giữ khoá ngoại. Bị lọc mất thì câu trả lời thành
"rỗng", lệnh `DELETE` chạy, và khoá ngoại ở tầng DB mới chặn lại bằng một lỗi `500` khó hiểu. Lối
thoát: cho riêng câu đếm ấy chạy **native SQL**, vì `@SQLRestriction` chỉ can thiệp vào JPQL/Criteria.
Xem `API_SPEC.md` §5.3.

### 4.4. Tiền luôn là số nguyên

`DOUBLE` và `FLOAT` lưu số theo hệ nhị phân, không biểu diễn chính xác được số thập phân hệ 10.
Kinh điển: `0.1 + 0.2 = 0.30000000000000004`. Cộng dồn hàng nghìn dòng đơn hàng là lệch tiền thật.

VND không có đơn vị nhỏ hơn 1 đồng → dùng `BIGINT` là gọn nhất. Nếu làm hệ thống đa tiền tệ (USD có cent)
thì quy ước lưu theo **đơn vị nhỏ nhất** (lưu cent thay vì dollar), vẫn là số nguyên. Hoặc dùng `DECIMAL`.

### 4.5. Mọi bảng đều có `created_at` / `updated_at`

Nghe thừa nhưng cực kỳ hữu ích khi debug ("đơn này tạo lúc nào?", "ai sửa giá sản phẩm hôm qua?").
JPA có sẵn: `@CreatedDate`, `@LastModifiedDate` + `@EnableJpaAuditing` — khai báo một lần, tự động điền.

Riêng `order_items` chỉ cần `created_at` vì nó không bao giờ bị sửa sau khi tạo.

### 4.6. Ràng buộc đặt ở tầng DB, không chỉ ở tầng code

Tầng code kiểm tra là để báo lỗi thân thiện cho người dùng. Tầng DB ràng buộc là để **đảm bảo dữ liệu
không bao giờ sai**, kể cả khi có bug, có race condition, hay khi ai đó sửa tay bằng SQL.

Cụ thể trong schema này: `UNIQUE` trên `users.email` và `orders.order_code`, `UNIQUE(user_id, product_id)`
trên `cart_items`, `NOT NULL` trên mọi cột bắt buộc, và `FOREIGN KEY` giữa các bảng.

> Một dấu hiệu nhận biết thiết kế tốt: **ràng buộc viết ra rất tự nhiên**. Nếu thấy mình phải bịa ra thủ
> thuật vòng vo để né một ràng buộc lẽ ra đơn giản, thường là do đang nhét hai khái niệm khác nhau vào
> chung một bảng.

---

## 5. Xử lý tranh chấp tồn kho (race condition)

Tình huống: sản phẩm còn **1** cái. Hai người bấm "Đặt hàng" cùng lúc.

```
Thời điểm   Request A                      Request B
   T1       đọc quantity = 1
   T2                                      đọc quantity = 1
   T3       kiểm tra 1 >= 1 → OK
   T4                                      kiểm tra 1 >= 1 → OK
   T5       ghi quantity = 0
   T6                                      ghi quantity = 0   ← bán mất 2 cái!
```

Cả hai đều thấy còn hàng vì đọc trước khi bên kia kịp ghi. `@Transactional` **không** giải quyết được
chuyện này — transaction đảm bảo "được ăn cả ngã về không", chứ không ngăn hai transaction cùng đọc một
giá trị cũ.

Có ba cách xử lý:

### Cách 1 — Trừ kho bằng một câu UPDATE có điều kiện (khuyến nghị cho v1)

```sql
UPDATE products
SET quantity = quantity - :quantity,
    sold     = sold + :quantity
WHERE id = :productId AND quantity >= :quantity;
```

Câu lệnh này **đọc và ghi trong cùng một thao tác nguyên tử** — DB tự khoá dòng trong lúc thực thi.
Kiểm tra số dòng bị ảnh hưởng: trả về `0` nghĩa là không đủ hàng → ném lỗi, rollback cả đơn.

Ưu điểm: không cần cấu hình gì thêm, hiệu năng tốt. Nhược điểm: logic nằm trong câu SQL nên khó diễn đạt
các quy tắc nghiệp vụ phức tạp.

### Cách 2 — Khoá lạc quan (optimistic locking)

Thêm cột `version INT` vào `products` và đánh dấu `@Version` trong JPA. Mỗi lần update, Hibernate tự thêm
`WHERE version = :versionCũ` và tăng version lên 1. Nếu có người khác ghi trước, điều kiện không khớp →
ném `OptimisticLockException` → bắt lỗi rồi thử lại.

Hợp khi **ít khi đụng độ**. Nếu đụng độ liên tục (flash sale) thì retry hoài rất tốn.

### Cách 3 — Khoá bi quan (pessimistic locking)

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
Optional<Product> findById(Long id);
```
Sinh ra `SELECT ... FOR UPDATE`, khoá hẳn dòng đó lại; request khác phải xếp hàng chờ.

Chắc chắn nhất nhưng tốn kém nhất — giữ khoá càng lâu thì càng nhiều request bị chặn, và dễ gây deadlock
nếu khoá nhiều dòng theo thứ tự khác nhau.

> **Khuyến nghị:** dùng **cách 1**. Khi làm xong API đặt hàng, viết thử một test gọi đồng thời 10 request
> mua cùng lúc để tự thấy hiện tượng bán vượt kho nếu làm sai — đây là bài học đáng giá nhất về concurrency.

---

## 6. Luồng đặt hàng — các bước ghi DB

Đây là nghiệp vụ phức tạp nhất hệ thống, cần `@Transactional` bao toàn bộ:

```
POST /api/orders
{ cartItemIds: [1, 3], recipientName, recipientPhone, shippingAddress, paymentMethod }

 1. Đọc các cart_items theo id       → kiểm tra thuộc về user đang đăng nhập
 2. Đọc products tương ứng           → kiểm tra chưa bị xoá (deleted_at IS NULL)
 3. Với từng dòng:
    UPDATE products SET quantity = quantity - n, sold = sold + n
    WHERE id = ? AND quantity >= n  → dòng nào trả về 0 → ném lỗi, rollback tất cả
 4. Tính subtotal, shipping_fee, total_amount
 5. INSERT orders                    → sinh order_code
 6. INSERT order_items               → chép product_name, product_image, price từ products
 7. DELETE các cart_items đã đặt     → chỉ xoá dòng được chọn, dòng khác vẫn nằm trong giỏ
```

Bước 3 phải đặt **trước** bước 5: nếu hết hàng thì dừng sớm, khỏi tạo đơn rồi lại phải xoá.

Nếu bất kỳ bước nào lỗi → rollback toàn bộ. Không được để tình trạng đã trừ kho mà chưa tạo đơn, hoặc
đã tạo đơn mà giỏ hàng vẫn còn.

**Sinh `order_code`:** cách đơn giản là `"SP" + yyyyMMdd + số thứ tự`. Cần đảm bảo không trùng — dựa vào
ràng buộc `UNIQUE` ở DB, nếu đụng thì sinh lại. Đừng dùng `Random` thuần vì xác suất trùng tăng nhanh
theo số lượng đơn.

**Luồng huỷ đơn** là luồng ngược lại, cũng cần `@Transactional`: kiểm tra đơn đang ở `PENDING` hoặc
`CONFIRMED` → đổi `status` sang `CANCELLED` → cộng trả `quantity` và trừ lại `sold` cho từng sản phẩm.

---

## 7. Lộ trình nâng cấp schema

### v2.1 — `addresses` (sổ địa chỉ)

```
addresses: id, user_id, recipient_name, phone, province, district, ward, street, is_default
```
Một user có nhiều địa chỉ, chọn một cái khi đặt hàng. v1 đang để `address` là một chuỗi trên `users`.

Lưu ý: dù có bảng này, `orders` **vẫn phải snapshot** địa chỉ vào các cột `recipient_*` chứ không chỉ lưu
`address_id` — theo đúng nguyên tắc 4.1.

### v2.2 — `product_variants` (phân loại hàng)

**Đây là thay đổi lớn nhất còn lại.** Sản phẩm Shopee thật hầu như luôn có phân loại: áo có size S/M/L,
màu đỏ/xanh — mỗi tổ hợp có **giá và tồn kho riêng**.

```
product_variants: id, product_id, sku, name ("Đỏ / Size M"), price, quantity, image
```

Khi thêm bảng này, `quantity` và `price` **chuyển từ `products` xuống `product_variants`**, và
`cart_items` / `order_items` phải trỏ tới `variant_id` thay vì `product_id`.

> **Quyết định cho dự án này: v1 KHÔNG làm variant.** Lý do:
>
> 1. **Không có gì tiêu thụ nó** — chưa có giao diện chọn phân loại ở client nào.
> 2. **Độ khó nhảy vọt** — variant tử tế (màu × size) cần tới 4 bảng lồng nhau, quá nặng khi mới học JPA.
> 3. **Việc migration sau này tự nó là bài học giá trị** — di chuyển cột giữa các bảng trên hệ thống đã có
>    dữ liệu, giữ API không gãy: đó chính là công việc hằng ngày khi đi làm.
>
> **Ba việc cần làm để migration sau này rẻ:**
>
> - Dồn toàn bộ logic trừ/cộng kho vào **đúng một method** (`ProductService.decreaseStock()`). Khi cột
>   `quantity` chuyển xuống bảng variant, chỉ một chỗ phải sửa.
> - **Không trả entity thẳng ra API, luôn qua DTO.** DTO là lớp đệm: sau này `price` lấy từ đâu thì chỉ
>   sửa chỗ map, client không cần biết.
> - **Chuyển sang Flyway khi schema v1 ổn định**, rồi lấy chính việc thêm variant làm migration đầu tiên —
>   bài tập Flyway hoàn hảo vì có đủ: thêm bảng, di chuyển cột, chuyển dữ liệu cũ.
>
> **Điều kiện kèm theo:** nếu làm variant, hãy làm **trước** voucher và thanh toán. Càng nhiều tính năng
> dựng trên `products.quantity` thì càng khó gỡ.

### v2.3 — `reviews`

```
reviews: id, user_id, product_id, order_item_id, rating (1-5), comment, images, created_at
```
Ràng buộc nghiệp vụ quan trọng: chỉ cho đánh giá khi **đã mua và đã nhận hàng** (`order_item_id` phải trỏ
tới một dòng thuộc đơn `status = DELIVERED`), và mỗi order_item chỉ đánh giá một lần → `UNIQUE(order_item_id)`.

Khi có bảng này thì `products.rating` trở thành giá trị tính sẵn từ đây (xem 4.2).

### v2.4 — `vouchers`

```
vouchers: id, code (UNIQUE), discount_type (PERCENT|FIXED), discount_value, min_order_amount,
          max_discount_amount, usage_limit, used_count, valid_from, valid_to, is_active
voucher_usages: id, voucher_id, user_id, order_id, used_at
```
Bảng `voucher_usages` để chặn một người dùng nhiều lần một mã. Việc trừ `used_count` cũng gặp đúng bài
toán race condition ở mục 5 — xử lý y hệt. Khi có voucher, cột `discount` trên `orders` mới thực sự
được dùng đến.

### v3 — Mô hình sàn thương mại điện tử

| Bảng | Vai trò |
|---|---|
| `shops` | Shopee là **sàn**: sản phẩm thuộc về shop, không thuộc về sàn. `products` thêm `shop_id` |
| `payments` | Bản ghi giao dịch thanh toán (cổng thanh toán, mã giao dịch, trạng thái, số tiền) |
| `shipments` | Vận đơn: đơn vị vận chuyển, mã vận đơn, lịch sử trạng thái |
| `inventory_logs` | Nhật ký biến động kho — mỗi lần cộng/trừ ghi một dòng, để đối soát khi số liệu lệch |
| `notifications` | Thông báo cho user |

Khi có `shops`, một "đơn hàng" của khách mua từ 3 shop khác nhau thực chất phải tách thành 3 đơn con
(vì mỗi shop giao riêng) → sinh ra khái niệm đơn cha/đơn con. Đây là lúc mô hình bắt đầu thật sự phức tạp.

---

## 8. Những thứ Shopee có mà v1 cố tình bỏ

Liệt kê để biết mình đang đứng ở đâu so với hệ thống thật, **không phải** để làm ngay:

| Bỏ qua | Vì sao |
|---|---|
| Nhiều shop / người bán | Cần toàn bộ khu vực quản lý dành cho người bán |
| Phân loại hàng (variant) | Xem v2.2 |
| Thanh toán online | Cần tích hợp cổng thanh toán, sandbox, webhook |
| Tính phí ship theo khu vực | Cần tích hợp API đơn vị vận chuyển. v1 để phí cố định |
| Chat người mua – người bán | Cần WebSocket, là một hệ thống riêng |
| Flash sale / đấu giá | Bài toán concurrency ở mức cao |
| Gợi ý sản phẩm | Cần hệ thống recommendation |
| Ví ShopeePay | Hệ thống tài chính — yêu cầu độ chính xác tuyệt đối |

---

## 9. Chốt lại cho v1

Bảy bảng ở mục 3, tuân thủ sáu nguyên tắc ở mục 4, xử lý tồn kho theo cách 1 ở mục 5, và luồng đặt hàng
theo đúng thứ tự ở mục 6.

Về cách tạo schema: hiện đang dùng `spring.jpa.hibernate.ddl-auto=update` — tiện lúc học vì Hibernate tự
tạo bảng từ entity. Nhưng nó không quản lý được lịch sử thay đổi schema (không rollback được, không biết
ai đổi gì lúc nào) nên **không dùng cho production**. Khi schema đã ổn định thì chuyển sang **Flyway**:
mỗi thay đổi schema là một file SQL đánh số (`V1__create_tables.sql`, `V2__add_variants.sql`), chạy
tuần tự và ghi lại lịch sử — đây là cách làm chuẩn trong dự án thật.
