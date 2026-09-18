# System Design — Lộ trình từ dự án học đến hệ thống lớn

> Tài liệu này không phải việc cần làm ngay. Mục đích là để biết **mình đang đứng ở đâu**, bước tiếp theo
> là gì, và quan trọng nhất: **khi nào thì KHÔNG nên nâng cấp**.
>
> Thông điệp chính: kiến trúc phức tạp không phải là kiến trúc tốt. Mỗi tầng phức tạp thêm vào phải
> đổi lấy một vấn đề thật đang gặp phải. Thêm vì "nghe nói hay" là cách nhanh nhất để tự làm khổ mình.

---

## Bản đồ tổng quát

```
Level 0  Monolith                    ← ĐANG Ở ĐÂY
         1 app + 1 DB

Level 1  Monolith tối ưu             ← đích đến thực tế của dự án này
         + index, cache, object storage, logging

Level 2  Modular monolith
         ranh giới module rõ ràng

Level 3  Scale ngang
         nhiều instance + read replica + queue + search engine

Level 4  Microservices
         tách service theo nghiệp vụ
```

Phần lớn hệ thống thương mại điện tử thật **dừng ở Level 1–3** và vẫn phục vụ tốt hàng triệu người dùng.
Level 4 chủ yếu để giải bài toán **tổ chức con người** (nhiều đội làm song song không giẫm chân nhau),
chứ không phải bài toán kỹ thuật thuần tuý.

---

## Level 0 — Monolith (hiện tại)

```
┌─────────────┐        ┌──────────────────┐       ┌─────────┐
│  React FE   │ ─────► │  Spring Boot app │ ────► │  MySQL  │
│  port 3000  │  HTTP  │    port 8081     │  JDBC │  :3306  │
└─────────────┘        └──────────────────┘       └─────────┘
                        auth │ product │ user
                        category │ cart │ order
```

Toàn bộ code nằm trong một ứng dụng, một database, deploy một lần.

**Đây là lựa chọn đúng** cho dự án này, và cũng đúng cho đa số dự án khởi đầu ngoài đời thật. Ưu điểm:
gọi hàm trực tiếp (không qua mạng), transaction đơn giản (một DB, `@Transactional` là đủ), debug dễ,
deploy một nút.

Giới hạn sẽ gặp: một chỗ lỗi nặng có thể kéo sập cả app; muốn scale phần xử lý ảnh thì phải nhân bản
toàn bộ app; và khi codebase phình to mà không có ranh giới rõ ràng thì mọi thứ dính vào nhau.

---

## Level 1 — Monolith tối ưu (đích thực tế của dự án này)

Vẫn một app, một DB, nhưng xử lý hết những nút thắt hay gặp. Đây là nơi có **tỉ lệ lợi ích trên công sức
cao nhất** — làm tốt Level 1 thường nhanh hơn Level 3 làm ẩu.

### 1.1. Vấn đề N+1 query — bệnh kinh điển của JPA

Sẽ gặp ngay ở bước 5 của lộ trình (làm API danh sách sản phẩm). Code trông vô hại:

```java
List<Product> products = productRepository.findAll();   // 1 câu query
for (Product p : products) {
    p.getCategory().getName();                          // mỗi vòng lặp thêm 1 query!
    p.getImages().size();                               // thêm 1 query nữa!
}
```

Lấy 20 sản phẩm → **41 câu query** thay vì 1. Với 100 sản phẩm là 201 câu. App vẫn chạy đúng, chỉ là chậm
dần đều cho đến lúc không chịu nổi — nên rất khó phát hiện nếu không chủ động tìm.

Cách phát hiện: bật `spring.jpa.show-sql=true` (bạn đã bật sẵn) rồi đếm số câu SQL in ra khi gọi một API.

Cách xử lý:
- `JOIN FETCH` trong JPQL: `SELECT p FROM Product p JOIN FETCH p.category`
- `@EntityGraph` trên method của repository
- Với quan hệ `@OneToMany` thì dùng `@BatchSize` để gom N query thành vài query

> Nguyên tắc phòng bệnh: đặt tất cả quan hệ là `FetchType.LAZY` (mặc định của `@OneToMany`, nhưng
> `@ManyToOne` mặc định là `EAGER` — nhớ đổi), rồi chủ động fetch những gì cần ở từng truy vấn.

### 1.2. Cache — Redis

Danh sách danh mục gần như không bao giờ đổi, nhưng mỗi lần vào trang chủ lại query DB.
Chi tiết sản phẩm cũng vậy — đọc nhiều hơn ghi hàng nghìn lần.

```
Request → kiểm tra Redis → có sẵn? trả luôn (dưới 1ms)
                         → chưa có? query MySQL → lưu vào Redis → trả về
```

Spring có sẵn `@Cacheable` / `@CacheEvict`, khai báo rất gọn.

**Phần khó không phải là lưu cache, mà là xoá cache đúng lúc.** Sản phẩm đổi giá mà cache cũ vẫn còn →
khách thấy giá sai. Ba chiến lược phổ biến: đặt thời gian hết hạn (TTL) ngắn và chấp nhận sai lệch tạm
thời; hoặc chủ động xoá cache khi dữ liệu đổi; hoặc kết hợp cả hai.

Có một câu nói vui trong ngành: *hai vấn đề khó nhất của khoa học máy tính là đặt tên biến và vô hiệu
hoá cache.* Đừng cache những thứ thay đổi liên tục hoặc phải chính xác tuyệt đối (như tồn kho).

### 1.3. Lưu ảnh: đừng để trên đĩa của app server

Ba cách, từ tệ đến tốt:

| Cách | Vấn đề |
|---|---|
| Lưu vào DB dạng BLOB | Phình DB, backup chậm, không dùng được CDN. **Đừng làm** |
| Lưu vào thư mục trên máy chạy app | Chạy được với 1 server. Nhân lên 2 server là hỏng ngay: ảnh upload lên server A thì server B không có |
| **Object storage** (AWS S3, hoặc MinIO tự host) | DB chỉ lưu URL. Scale thoải mái, gắn CDN được |

Dự án học thì lưu thư mục local cũng được, nhưng biết trước giới hạn để không bất ngờ.

### 1.4. Connection pool

Mỗi kết nối tới MySQL rất tốn tài nguyên để mở, nên không mở/đóng liên tục mà giữ sẵn một "hồ" kết nối
để tái sử dụng. Spring Boot dùng HikariCP mặc định, không cần cấu hình gì thêm ở quy mô nhỏ.

Điều cần biết: pool mặc định 10 kết nối. Nếu có API nào giữ kết nối quá lâu (query chậm, hoặc gọi API
bên ngoài **bên trong** một transaction) thì pool cạn, toàn bộ hệ thống đứng hình dù CPU vẫn nhàn rỗi.
→ Quy tắc: **không gọi API bên ngoài bên trong transaction**.

### 1.5. Idempotency — chống bấm nút hai lần

Khách bấm "Đặt hàng", mạng lag, bấm thêm lần nữa → tạo 2 đơn hàng giống hệt. Đây là lỗi thật, rất hay gặp.

Cách xử lý: FE sinh một `requestId` duy nhất cho mỗi lần bấm, gửi kèm request. BE lưu lại `requestId` đã
xử lý; gặp lại `requestId` cũ thì trả về kết quả của lần trước thay vì tạo đơn mới.

Khái niệm **idempotent** nghĩa là: gọi 1 lần hay 10 lần cùng một request thì kết quả vẫn như nhau.
Theo chuẩn HTTP, `GET`, `PUT`, `DELETE` phải idempotent; `POST` thì không — nên các API `POST` liên quan
tiền bạc cần tự xử lý.

### 1.6. Logging và giám sát

Khi hệ thống lỗi lúc 2 giờ sáng, thứ duy nhất cứu bạn là log. Cần:
- Log có cấu trúc (JSON) thay vì `System.out.println`
- Mỗi request gắn một `traceId` để lần theo toàn bộ hành trình của nó
- Spring Boot Actuator cho các endpoint health check, metrics

---

## Level 2 — Modular monolith

Vẫn deploy một app, nhưng code được chia thành các module có **ranh giới rõ ràng**.

Bạn đang đi đúng hướng rồi: package hiện tại đã chia theo nghiệp vụ (`auth`, `product`, `user`) chứ không
chia theo tầng kỹ thuật (`controllers/`, `services/`, `repositories/`). Cách chia theo nghiệp vụ tốt hơn
vì mọi thứ liên quan đến một tính năng nằm cạnh nhau, và về sau muốn tách ra thành service riêng thì
chỉ việc bê cả thư mục đi.

Nguyên tắc để ranh giới không bị phá vỡ:

1. **Module chỉ nói chuyện với nhau qua interface công khai**, không gọi thẳng vào repository của nhau.
   Ví dụ: `order` cần trừ tồn kho thì gọi `ProductService.decreaseStock()`, tuyệt đối không tự inject
   `ProductRepository`.
2. **Mỗi bảng chỉ thuộc về một module.** Không để hai module cùng ghi vào một bảng.
3. Muốn chặt chẽ hơn thì dùng công cụ như ArchUnit để viết test kiểm tra ranh giới — vi phạm là fail build.

Làm tốt Level 2 thì việc lên microservices sau này chỉ còn là chuyện kỹ thuật. Bỏ qua Level 2 mà nhảy
thẳng lên microservices sẽ ra thứ tệ nhất: **distributed monolith** — các service vẫn dính chặt vào nhau
nhưng giờ phải gọi nhau qua mạng, tức là gộp đủ nhược điểm của cả hai mô hình.

---

## Level 3 — Scale ngang

Khi một server không kham nổi nữa.

```
                  ┌─────────────┐
                  │Load Balancer│
                  └──────┬──────┘
              ┌──────────┼──────────┐
         ┌────▼───┐ ┌────▼───┐ ┌────▼───┐
         │ App #1 │ │ App #2 │ │ App #3 │
         └────┬───┘ └────┬───┘ └────┬───┘
              └──────────┼──────────┘
              ┌──────────┼──────────┐
        ┌─────▼────┐ ┌───▼────┐ ┌───▼──────────┐
        │  MySQL   │ │ Redis  │ │Elasticsearch │
        │ master   │ │ cache  │ │   search     │
        │   + 2    │ └────────┘ └──────────────┘
        │ replica  │
        └──────────┘
```

### 3.1. App phải stateless

Đây là lý do kỹ thuật quan trọng nhất khiến JWT được ưa chuộng: server **không lưu** phiên đăng nhập,
nên request của cùng một người có thể rơi vào App #1 hay App #3 đều được. Nếu dùng session lưu trong RAM
của server thì phải cấu hình "dính" người dùng vào một server, hoặc đẩy session ra Redis.

Hệ quả: **không lưu bất cứ trạng thái nào trong RAM hoặc trên đĩa của app** — kể cả file ảnh upload
(xem 1.3) hay bộ đếm.

### 3.2. Read replica

Ecommerce đọc nhiều hơn ghi rất nhiều (xem sản phẩm >> đặt hàng). Tách một master chuyên ghi, vài replica
chuyên đọc.

Cái bẫy: dữ liệu đồng bộ từ master sang replica có **độ trễ**. Vừa đặt hàng xong, chuyển sang trang
"Đơn mua" mà đọc từ replica chưa kịp đồng bộ → khách không thấy đơn của mình, tưởng mất tiền.
→ Quy tắc: thao tác cần đọc lại ngay sau khi ghi thì phải đọc từ master.

### 3.3. Message queue — xử lý bất đồng bộ

Đặt hàng xong cần: gửi email xác nhận, gửi thông báo, cập nhật thống kê, đồng bộ kho. Nếu làm hết trong
một request thì khách phải chờ 5 giây và chỉ cần dịch vụ email lỗi là cả API đặt hàng lỗi theo.

Giải pháp: việc gì không cần trả lời ngay thì đẩy vào hàng đợi (RabbitMQ/Kafka), worker xử lý sau.
API trả về cho khách trong 200ms.

### 3.4. Elasticsearch cho tìm kiếm

Khi `FULLTEXT` của MySQL không đủ: cần tìm gần đúng (gõ sai chính tả vẫn ra), gợi ý khi đang gõ, xếp hạng
theo độ liên quan, lọc nhiều chiều cùng lúc. Đánh đổi: phải đồng bộ dữ liệu từ MySQL sang Elasticsearch
và chấp nhận độ trễ.

### 3.5. CDN cho ảnh

Ảnh sản phẩm chiếm phần lớn băng thông. CDN đặt bản sao ở nhiều nơi, người dùng tải từ điểm gần nhất.
Đây thường là nâng cấp **rẻ nhất mà hiệu quả rõ nhất** về tốc độ tải trang.

---

## Level 4 — Microservices

### Khi nào thì thật sự cần

Cần khi:
- Có **nhiều đội** cùng làm và đang giẫm chân nhau khi deploy.
- Các phần có **nhu cầu scale rất khác nhau** (tìm kiếm cần 50 máy, thanh toán cần 2).
- Một phần cần **công nghệ khác** (ví dụ gợi ý sản phẩm viết bằng Python).
- Cần **cách ly sự cố**: dịch vụ gợi ý chết không được làm sập chức năng đặt hàng.

**Không** cần khi: chỉ có 1–2 người làm, hệ thống chưa gặp giới hạn nào, hoặc lý do duy nhất là
"microservices nghe hiện đại hơn".

### Chia thế nào

Chia theo **nghiệp vụ**, không chia theo tầng kỹ thuật:

```
        ┌──────────────┐
        │ API Gateway  │  ← xác thực, rate limit, định tuyến
        └──────┬───────┘
    ┌──────┬───┴───┬────────┬──────────┐
┌───▼──┐┌──▼───┐┌──▼────┐┌──▼─────┐┌───▼──────┐
│ user ││product││ order ││payment ││notification│
│  svc ││  svc  ││  svc  ││  svc   ││    svc     │
└───┬──┘└──┬───┘└──┬────┘└──┬─────┘└───┬──────┘
  ┌─▼─┐  ┌─▼─┐  ┌─▼─┐    ┌─▼─┐      ┌─▼─┐
  │DB │  │DB │  │DB │    │DB │      │DB │   ← mỗi service một DB riêng
  └───┘  └───┘  └───┘    └───┘      └───┘
```

Quy tắc bất di bất dịch: **mỗi service sở hữu database riêng**. Service khác muốn dữ liệu thì phải gọi API,
không được truy cập thẳng DB. Dùng chung DB thì đó không phải microservices, chỉ là monolith bị xé lẻ.

### Cái giá phải trả

Đây là phần người ta hay bỏ qua khi quảng cáo microservices:

**1. Transaction phân tán.** Ở monolith, đặt hàng (trừ kho + tạo đơn + trừ tiền) chỉ cần một
`@Transactional` — lỗi thì rollback tất cả. Khi tách service, mỗi thao tác nằm ở một DB khác nhau,
**không còn rollback chung được nữa**.

Giải pháp là mô hình **Saga**: chia thành chuỗi bước, mỗi bước có một hành động bù trừ nếu bước sau lỗi.

```
Trừ kho ──► Tạo đơn ──► Thanh toán
   │           │            │ lỗi!
   │           ◄── huỷ đơn ─┘
   ◄── hoàn kho ┘
```
Phức tạp hơn hẳn một dòng `@Transactional`, và phải tự xử lý trường hợp chính hành động bù trừ cũng lỗi.

**2. Nhất quán cuối (eventual consistency).** Dữ liệu giữa các service không đồng bộ tức thời. Phải chấp
nhận có những khoảnh khắc số liệu ở hai nơi khác nhau, và thiết kế giao diện sao cho người dùng không
hoang mang.

**3. Gọi qua mạng thì có thể lỗi.** Gọi hàm trong monolith không bao giờ "timeout". Gọi qua mạng thì có,
nên cần retry, circuit breaker, timeout — mỗi thứ đều phải cấu hình đúng, sai là hỏng theo kiểu khác.

**4. Debug khó hơn nhiều.** Một request đi qua 5 service, lỗi ở đâu? Cần distributed tracing
(OpenTelemetry/Jaeger) và log tập trung — tức là phải dựng thêm hạ tầng chỉ để nhìn thấy chuyện gì
đang xảy ra.

**5. Vận hành nặng hơn.** 5 service là 5 pipeline CI/CD, 5 bộ cấu hình, 5 thứ cần giám sát. Thường phải
kèm Kubernetes, và Kubernetes tự nó đã là một thứ cần học riêng.

> **Lời khuyên thẳng thắn cho dự án này:** đừng làm microservices. Không phải vì khó, mà vì nó giải quyết
> những vấn đề bạn chưa có. Nếu muốn học microservices như một kỹ năng, hãy học **sau khi** đã làm chủ
> Level 1 và Level 2 — vì mọi khái niệm ở Level 4 (ranh giới nghiệp vụ, giao tiếp bất đồng bộ, xử lý lỗi)
> đều bắt nguồn từ đó. Một người viết monolith gọn gàng sẽ viết microservices tốt; ngược lại thì không.

---

## Bảng tra nhanh: gặp vấn đề gì thì làm gì

| Triệu chứng | Nguyên nhân thường gặp | Cách xử lý | Level |
|---|---|---|---|
| API danh sách chậm dần | N+1 query | `JOIN FETCH` / `@EntityGraph` | 1 |
| Query chậm khi dữ liệu nhiều | Thiếu index | Thêm index đúng cột | 1 |
| Trang chủ tải chậm | Query lặp lại dữ liệu ít đổi | Cache bằng Redis | 1 |
| Ảnh mất khi deploy | Lưu ảnh trên đĩa app server | Object storage | 1 |
| Tạo trùng đơn hàng | Thiếu idempotency | requestId | 1 |
| Bán vượt tồn kho | Race condition | UPDATE có điều kiện | 1 |
| Deploy hay xung đột giữa các đội | Ranh giới module mờ | Modular monolith | 2 |
| 1 server không chịu nổi tải | — | Load balancer + nhiều instance | 3 |
| DB quá tải phần đọc | Đọc nhiều hơn ghi | Read replica | 3 |
| Đặt hàng chậm vì chờ gửi mail | Làm đồng bộ việc không cần ngay | Message queue | 3 |
| Tìm kiếm kém, không gợi ý được | `LIKE %...%` | Elasticsearch | 3 |
| Nhiều đội giẫm chân nhau | Tổ chức, không phải kỹ thuật | Microservices | 4 |

---

## Đề xuất cho dự án này

1. **Bây giờ:** làm xong Level 0 cho đúng — đủ 5 bảng, API chạy, bảo mật hoạt động.
2. **Ngay sau đó:** nhặt vài món ở Level 1 có giá trị học cao nhất — sửa N+1 query, đánh index đúng chỗ,
   xử lý race condition tồn kho. Ba thứ này gặp trong mọi dự án Java đi làm.
3. **Khi muốn học tiếp:** thêm Redis cache và Flyway migration — hai công cụ gần như chắc chắn gặp khi đi làm.
4. **Level 3 trở lên:** chỉ đụng đến khi muốn học có chủ đích, không phải vì dự án cần.

Giá trị thật của tài liệu này không nằm ở việc làm hết, mà ở chỗ khi phỏng vấn hay khi gặp vấn đề thật,
bạn biết có những lựa chọn nào và mỗi lựa chọn đánh đổi cái gì.
