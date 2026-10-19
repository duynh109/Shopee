"""Nạp dữ liệu mẫu trông như thật vào shopee_db: 4 tài khoản, 12 danh mục, 69 sản phẩm (207 ảnh), 8 đơn, 5 món trong giỏ.

Cách dùng (từ thư mục BE/):
    pip install pillow
    ./mvnw spring-boot:run         # app phải đang chạy ở :8081, và DB phải TRỐNG (bảng đã tạo, chưa có dòng nào)
    python3 scripts/seed.py

Hầu hết dữ liệu đi qua API thật (đăng ký, tạo danh mục/sản phẩm, upload ảnh, đặt hàng, đổi trạng thái đơn) nên
nó cũng là một lượt thử API. Chỉ vài việc không có API thì làm bằng SQL: nâng admin@shopeeclone.vn lên ADMIN, đặt
đã bán / lượt xem / đánh giá / ngày đăng cho sản phẩm, dọn refresh_tokens sinh ra khi nạp. SQL chạy bằng lệnh
`mysql` với DB_PASSWORD đọc từ BE/.env (không in ra).

Ảnh tải từ DummyJSON (bộ dữ liệu mẫu miễn phí), đổi WebP → JPEG rồi upload qua POST /api/admin/upload-image;
file nằm ở BE/uploads (đã gitignore). Ảnh tải về được cache ở thư mục tạm của hệ điều hành để chạy lại cho nhanh.
Tài khoản: admin@shopeeclone.vn, an.nguyen@gmail.com, linh.tran@gmail.com, minh.le@gmail.com — mật khẩu đều 123456."""
import io, json, os, random, subprocess, sys, tempfile, urllib.error, urllib.request, uuid
from PIL import Image

B = "http://localhost:8081/api"
HERE = os.path.dirname(os.path.abspath(__file__))
ENV_FILE = os.path.join(HERE, "..", ".env")
DB_NAME = "shopee_db"
PASSWORD = "123456"
UA = {"User-Agent": "Mozilla/5.0 (seed script)"}
random.seed(2026)


def http(method, path, body=None, token=None, raw=None, ctype="application/json"):
    data = raw if raw is not None else (None if body is None else json.dumps(body).encode())
    req = urllib.request.Request(B + path, method=method, data=data)
    if ctype:
        req.add_header("Content-Type", ctype)
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            t = r.read().decode()
            return r.status, (json.loads(t) if t else None)
    except urllib.error.HTTPError as e:
        t = e.read().decode()
        return e.code, (json.loads(t) if t else None)


def must(res, *ok):
    s, b = res
    if s not in ok:
        sys.exit(f"LỖI {s}: {b}")
    return b


def db_password():
    for line in open(ENV_FILE, encoding="utf-8"):
        if line.startswith("DB_PASSWORD="):
            return line.split("=", 1)[1].strip()
    sys.exit("Không thấy DB_PASSWORD trong BE/.env")


def sql(q):
    env = dict(os.environ, MYSQL_PWD=db_password())  # truyền qua biến môi trường để mật khẩu không lộ trong danh sách tiến trình
    out = subprocess.run(["mysql", "-uroot", DB_NAME, "-N", "-B", "-e", q], capture_output=True, text=True, env=env)
    if out.returncode:
        sys.exit(out.stderr)
    return out.stdout


# ------------------------------------------------------------------ danh mục và sản phẩm
CATEGORIES = ["Thời Trang Nam", "Thời Trang Nữ", "Điện Thoại & Phụ Kiện", "Máy Tính & Laptop", "Đồng Hồ",
              "Giày Dép Nam", "Giày Dép Nữ", "Túi Ví Nữ", "Sắc Đẹp", "Nhà Cửa & Đời Sống",
              "Thể Thao & Du Lịch", "Phụ Kiện Thời Trang"]

CATEGORY_NOTES = {
    "Thời Trang Nam": ["Đường may chắc chắn, không xù lông sau nhiều lần giặt", "Size M–XXL, xem bảng size trong ảnh để chọn đúng"],
    "Thời Trang Nữ": ["Vải mềm, đứng form, không nhăn khi gấp gọn", "Size S–XL, shop tư vấn size theo chiều cao cân nặng"],
    "Điện Thoại & Phụ Kiện": ["Hàng nguyên seal, bảo hành chính hãng 12 tháng", "Hỗ trợ trả góp 0% qua thẻ tín dụng"],
    "Máy Tính & Laptop": ["Máy mới 100%, nguyên seal, bảo hành 12 tháng tại hãng", "Tặng kèm túi chống sốc và chuột không dây"],
    "Đồng Hồ": ["Kính chống trầy, chống nước 3ATM đi mưa thoải mái", "Bảo hành máy 12 tháng, thay pin miễn phí trọn đời"],
    "Giày Dép Nam": ["Đế cao su chống trơn trượt, êm chân khi đi cả ngày", "Đủ size 39–44, đổi size miễn phí trong 7 ngày"],
    "Giày Dép Nữ": ["Lót đệm êm, không đau chân khi đi lâu", "Đủ size 35–39, đổi size miễn phí trong 7 ngày"],
    "Túi Ví Nữ": ["Khoá kim loại không gỉ, lót trong chống thấm", "Nhiều ngăn tiện để điện thoại, ví và mỹ phẩm"],
    "Sắc Đẹp": ["Hàng chính hãng, có tem chống hàng giả", "Hạn sử dụng còn trên 18 tháng"],
    "Nhà Cửa & Đời Sống": ["Chất liệu an toàn, dễ vệ sinh", "Bảo hành 12 tháng, lỗi 1 đổi 1 trong 7 ngày đầu"],
    "Thể Thao & Du Lịch": ["Đạt tiêu chuẩn tập luyện và thi đấu phong trào", "Bền bỉ khi dùng ngoài trời"],
    "Phụ Kiện Thời Trang": ["Thiết kế tinh tế, dễ phối đồ", "Kèm hộp đựng xinh xắn, thích hợp làm quà tặng"],
}

# (id DummyJSON, danh mục, tên, giá, giá gốc hoặc None, tồn kho, câu giới thiệu, [điểm nổi bật riêng])
PRODUCTS = [
    (83, "Thời Trang Nam", "Áo Sơ Mi Nam Kẻ Caro Xanh Đen Tay Dài Vải Cotton Form Regular", 259000, 350000, 120,
     "Áo sơ mi caro xanh đen kinh điển, mặc đi làm hay đi chơi đều hợp.", ["Cotton pha thoáng mát, thấm hút mồ hôi tốt", "Form regular vừa người, không bó"]),
    (84, "Thời Trang Nam", "Áo Thun Nam Cổ Tròn In Hình Gaming Chất Cotton 100%", 189000, 249000, 200,
     "Áo thun in hình phong cách gaming cho các anh em mê game.", ["Cotton 100% co giãn 4 chiều", "Hình in công nghệ cao, không bong tróc"]),
    (85, "Thời Trang Nam", "Áo Sơ Mi Nam Kẻ Caro Đỏ Phong Cách Hàn Quốc", 279000, 390000, 85,
     "Sơ mi caro đỏ trẻ trung, khoác ngoài áo thun cực chất.", ["Vải flannel mềm, giữ ấm nhẹ", "Có thể mặc như áo khoác mỏng"]),
    (86, "Thời Trang Nam", "Áo Sơ Mi Nam Tay Ngắn Họa Tiết Đi Biển Mùa Hè", 199000, None, 150,
     "Sơ mi tay ngắn họa tiết rực rỡ cho những chuyến du lịch biển.", ["Vải lụa mát mẻ, nhanh khô", "Cổ bẻ rộng thoải mái"]),
    (87, "Thời Trang Nam", "Áo Sơ Mi Nam Caro Công Sở Chống Nhăn", 299000, 420000, 60,
     "Sơ mi caro nhỏ lịch sự, phù hợp môi trường công sở.", ["Chất vải chống nhăn, không cần ủi nhiều", "Đóng thùng hay bỏ ngoài đều đẹp"]),

    (162, "Thời Trang Nữ", "Váy Xoè Chấm Bi Xanh Dương Tay Cánh Tiên", 329000, 450000, 70,
     "Váy xoè nền trắng chấm bi xanh dương ngọt ngào, che khuyết điểm vòng eo.", ["Tay cánh tiên nữ tính", "Dáng xoè nhẹ, dễ vận động"]),
    (163, "Thời Trang Nữ", "Đầm Maxi Trễ Vai Họa Tiết Lá Đi Biển", 289000, None, 95,
     "Đầm maxi trễ vai họa tiết lá nhiệt đới cho mùa hè.", ["Dáng dài thướt tha, lên ảnh đẹp", "Vải voan có lớp lót, không lộ"]),
    (164, "Thời Trang Nữ", "Đầm Suông Xám Công Sở Thanh Lịch", 359000, 499000, 40,
     "Đầm suông màu xám trung tính, thanh lịch nơi công sở.", ["Dáng suông che bụng", "Màu trung tính dễ phối giày túi"]),
    (165, "Thời Trang Nữ", "Váy Cúp Ngực Xanh Ngọc Thắt Nơ Eo Tầng Bèo", 249000, 320000, 110,
     "Váy cúp ngực xanh ngọc, nơ thắt eo và chân váy tầng bèo.", ["Nơ eo tôn dáng", "Có quần bảo hộ bên trong"]),
    (166, "Thời Trang Nữ", "Đầm Kẻ Caro Tartan Phong Cách Vintage", 389000, 520000, 35,
     "Đầm caro tartan mang hơi thở cổ điển châu Âu.", ["Vải dạ mỏng đứng form", "Phù hợp mùa thu đông"]),
    (177, "Thời Trang Nữ", "Đầm Dạ Hội Trễ Vai Nền Đen Họa Tiết Hoa", 890000, 1250000, 15,
     "Đầm dạ hội trễ vai nền đen hoa rực rỡ, nổi bật trong mọi bữa tiệc.", ["Chất lụa satin bóng nhẹ", "Dáng dài chấm gót sang trọng"]),
    (180, "Thời Trang Nữ", "Đầm Cúp Ngực Trắng Chấm Bi Đỏ Phong Cách Vintage", 319000, None, 50,
     "Đầm cúp ngực nền trắng chấm bi đỏ, đậm chất cổ điển.", ["Chân váy xoè bồng bềnh", "Nơ eo điệu đà"]),

    (123, "Điện Thoại & Phụ Kiện", "Điện Thoại iPhone 13 Pro 128GB Chính Hãng VN/A", 18490000, 24990000, 25,
     "iPhone 13 Pro với màn hình ProMotion 120Hz và cụm 3 camera chuyên nghiệp.", ["Chip A15 Bionic mạnh mẽ", "Quay video chế độ điện ảnh"]),
    (133, "Điện Thoại & Phụ Kiện", "Điện Thoại Samsung Galaxy S10 128GB", 6990000, 9990000, 18,
     "Galaxy S10 màn hình Dynamic AMOLED tràn viền, cảm biến vân tay siêu âm.", ["Màn hình 6.1 inch QHD+", "Sạc không dây ngược cho phụ kiện"]),
    (126, "Điện Thoại & Phụ Kiện", "Điện Thoại OPPO F19 Pro+ 5G 8GB/128GB", 6490000, 8990000, 30,
     "OPPO F19 Pro+ 5G mỏng nhẹ, sạc nhanh 50W.", ["Kết nối 5G tốc độ cao", "Camera chân dung AI sắc nét"]),
    (128, "Điện Thoại & Phụ Kiện", "Điện Thoại Realme C35 4GB/64GB Pin 5000mAh", 3290000, 3990000, 60,
     "Realme C35 giá tốt, pin trâu dùng cả ngày.", ["Pin 5000mAh, sạc nhanh 18W", "Màn hình 6.6 inch Full HD+"]),
    (134, "Điện Thoại & Phụ Kiện", "Điện Thoại Vivo S1 6GB/128GB", 4590000, None, 22,
     "Vivo S1 thiết kế gradient bắt mắt, camera selfie 32MP.", ["Vân tay dưới màn hình", "Bộ nhớ 128GB thoải mái lưu trữ"]),
    (100, "Điện Thoại & Phụ Kiện", "Tai Nghe Bluetooth Apple AirPods 3 Chính Hãng", 2690000, 3490000, 80,
     "AirPods 3 kết nối tức thì với iPhone, âm thanh rõ ràng.", ["Pin tổng 30 giờ kèm hộp sạc", "Gọi \"Hey Siri\" rảnh tay"]),
    (101, "Điện Thoại & Phụ Kiện", "Tai Nghe Chụp Tai Apple AirPods Max Bạc", 11990000, 13990000, 8,
     "AirPods Max với chống ồn chủ động và âm thanh không gian.", ["Chống ồn chủ động ANC", "Đệm tai mút hoạt tính êm ái"]),
    (105, "Điện Thoại & Phụ Kiện", "Pin Sạc Dự Phòng Apple MagSafe Battery Pack", 2190000, 2590000, 40,
     "Pin dự phòng MagSafe hít chặt mặt lưng iPhone.", ["Sạc không dây tiện lợi", "Nhỏ gọn bỏ túi"]),
    (108, "Điện Thoại & Phụ Kiện", "Ốp Lưng iPhone 12 Silicone MagSafe Màu Mận", 390000, 590000, 150,
     "Ốp silicone mềm mịn, tương thích sạc MagSafe.", ["Lót microfiber bảo vệ máy", "Gờ nổi bảo vệ camera"]),
    (107, "Điện Thoại & Phụ Kiện", "Tai Nghe Không Dây Beats Flex Dây Đeo Cổ Màu Vàng Chanh", 1490000, 1990000, 45,
     "Beats Flex dây đeo cổ, nam châm tự ngắt nhạc.", ["Pin 12 giờ nghe nhạc", "Sạc nhanh 10 phút nghe 1.5 giờ"]),

    (78, "Máy Tính & Laptop", "Laptop Apple MacBook Pro 14 inch M1 Pro 16GB/512GB Space Grey", 42990000, 52990000, 10,
     "MacBook Pro 14 inch với chip M1 Pro, màn hình Liquid Retina XDR.", ["Pin tới 17 giờ xem video", "Cổng HDMI, khe thẻ SD, MagSafe 3"]),
    (82, "Máy Tính & Laptop", "Laptop Dell XPS 13 9300 Core i7 16GB/512GB", 28990000, 34990000, 7,
     "Dell XPS 13 nhỏ gọn, màn hình InfinityEdge viền siêu mỏng.", ["Khung nhôm nguyên khối", "Bàn phím có đèn nền"]),
    (81, "Máy Tính & Laptop", "Laptop Lenovo Yoga 920 Màn Hình Cảm Ứng Xoay Gập 360°", 19490000, None, 12,
     "Lenovo Yoga 920 xoay gập 360°, dùng như máy tính bảng.", ["Màn hình cảm ứng 13.9 inch", "Hỗ trợ bút Active Pen"]),
    (80, "Máy Tính & Laptop", "Laptop Huawei MateBook X Pro Màn Hình 3K", 25990000, 29990000, 6,
     "MateBook X Pro màn hình 3K tỉ lệ 3:2, mỏng nhẹ cao cấp.", ["Màn hình cảm ứng 3K", "Loa 4 kênh âm thanh sống động"]),
    (159, "Máy Tính & Laptop", "Máy Tính Bảng iPad Mini 6 2021 Wi-Fi 64GB Starlight", 11490000, 13990000, 20,
     "iPad mini 6 nhỏ gọn một tay cầm, hỗ trợ Apple Pencil 2.", ["Chip A15 Bionic", "Cổng USB-C tiện lợi"]),
    (160, "Máy Tính & Laptop", "Máy Tính Bảng Samsung Galaxy Tab S8+ 128GB", 16990000, 21990000, 9,
     "Galaxy Tab S8+ màn hình Super AMOLED 12.4 inch, kèm bút S Pen.", ["Tặng kèm bút S Pen", "Pin 10090mAh"]),

    (93, "Đồng Hồ", "Đồng Hồ Nam Dây Da Nâu Mặt Tròn Cổ Điển", 459000, 690000, 75,
     "Đồng hồ nam dây da nâu phong cách cổ điển, lịch lãm.", ["Dây da bò thật", "Máy quartz Nhật Bản chạy chính xác"]),
    (94, "Đồng Hồ", "Đồng Hồ Nam Longines Master Collection Automatic", 52000000, None, 3,
     "Longines Master Collection máy cơ automatic, đẳng cấp Thụy Sĩ.", ["Máy cơ automatic trữ cót 64 giờ", "Mặt kính sapphire"]),
    (193, "Đồng Hồ", "Đồng Hồ Nữ Dây Kim Loại Mạ Vàng Thời Trang", 399000, 590000, 90,
     "Đồng hồ nữ dây kim loại mạ vàng sang trọng.", ["Mạ vàng PVD bền màu", "Mặt số nhỏ xinh"]),
    (194, "Đồng Hồ", "Đồng Hồ Nữ Dây Thép Không Gỉ Mặt Đen Tối Giản", 349000, None, 64,
     "Đồng hồ dây thép mặt đen tối giản, thanh lịch mỗi ngày.", ["Dây thép không gỉ không phai màu", "Thiết kế tối giản"]),

    (88, "Giày Dép Nam", "Giày Sneaker Nike Air Jordan 1 Retro High Đỏ Đen", 3890000, 4590000, 14,
     "Air Jordan 1 phối màu đỏ đen huyền thoại.", ["Da cao cấp bền đẹp", "Đế Air êm ái"]),
    (90, "Giày Dép Nam", "Giày Thể Thao Nam Puma Future Rider Đế Êm", 1690000, 2190000, 33,
     "Puma Future Rider phong cách retro chạy bộ những năm 80.", ["Đế giữa đệm êm", "Thân giày lưới thoáng khí"]),
    (91, "Giày Dép Nam", "Giày Sneaker Nam Trắng Phối Đỏ Phong Cách Đường Phố", 650000, 890000, 58,
     "Sneaker trắng phối đỏ năng động, dễ phối đồ.", ["Đế cao 3cm tôn dáng", "Lót giày tháo rời được"]),

    (186, "Giày Dép Nữ", "Sandal Nữ Calvin Klein Quai Ngang Bản To Màu Đen", 1290000, 1690000, 26,
     "Sandal quai bản to màu đen cá tính, dễ phối với quần jeans hay váy.", ["Đế vững, đi lại thoải mái", "Da tổng hợp cao cấp"]),
    (187, "Giày Dép Nữ", "Giày Cao Gót Nữ Ánh Kim Vàng Dự Tiệc", 459000, 620000, 40,
     "Giày cao gót ánh kim nổi bật trong các buổi tiệc.", ["Da ánh kim sang trọng", "Gót nhọn 9cm"]),
    (188, "Giày Dép Nữ", "Giày Cao Gót Nữ Pampi Mũi Nhọn Da Đen", 299000, None, 85,
     "Giày cao gót mũi nhọn da đen, kiểu dáng kinh điển nơi công sở.", ["Gót nhọn 9cm", "Lót da màu nude êm chân"]),
    (189, "Giày Dép Nữ", "Giày Cao Gót Nữ Đỏ Mũi Nhọn Da Bóng", 499000, 690000, 30,
     "Giày cao gót đỏ quyến rũ, điểm nhấn cho trang phục.", ["Da bóng màu đỏ nổi bật", "Gót cao 8cm"]),
    (185, "Giày Dép Nữ", "Giày Cao Gót Nữ Mũi Nhọn Đen Lót Nâu", 359000, 459000, 80,
     "Giày cao gót mũi nhọn đen lót nâu, thanh lịch dễ phối đồ.", ["Gót nhọn 8cm", "Lót da nâu êm chân"]),

    (172, "Túi Ví Nữ", "Túi Xách Nữ Xanh Navy Dáng Hộp Quai Cầm", 459000, 650000, 48,
     "Túi dáng hộp xanh navy, xách tay hoặc đeo chéo.", ["Dây đeo tháo rời", "Da PU chống trầy"]),
    (173, "Túi Ví Nữ", "Túi Da Nữ Heshe Màu Nâu Công Sở Cao Cấp", 1890000, 2490000, 12,
     "Túi da thật Heshe màu nâu bò, sức chứa lớn cho dân công sở.", ["Da bò thật mềm mại", "Có túi khoá kéo mặt sau"]),
    (175, "Túi Ví Nữ", "Balo Nữ Da PU Trắng Mini Thời Trang", 389000, 520000, 66,
     "Balo mini da PU trắng trẻ trung cho các cô nàng năng động.", ["Kích thước nhỏ gọn", "Quai đeo điều chỉnh"]),
    (176, "Túi Ví Nữ", "Túi Xách Nữ Đen Dáng Công Sở Khoá Cài", 529000, None, 37,
     "Túi xách đen dáng công sở, khoá cài kim loại sang trọng.", ["Khoá cài kim loại mạ vàng", "Kèm dây đeo chéo tháo rời"]),

    (1, "Sắc Đẹp", "Mascara Essence Lash Princess Làm Dày Và Cong Mi", 129000, 159000, 300,
     "Mascara bán chạy hàng đầu, cho hàng mi dày và cong vút.", ["Chống lem, không vón cục", "Đầu cọ sợi tổng hợp"]),
    (2, "Sắc Đẹp", "Bảng Phấn Mắt Nhiều Màu Kèm Gương Tiện Lợi", 189000, 250000, 140,
     "Bảng phấn mắt nhiều tông từ tự nhiên đến lấp lánh.", ["Hạt phấn mịn, lên màu chuẩn", "Có gương tiện trang điểm"]),
    (4, "Sắc Đẹp", "Son Thỏi Màu Đỏ Cổ Điển Lâu Trôi", 159000, None, 220,
     "Son thỏi đỏ cổ điển, tôn da và quyến rũ.", ["Chất son lì mịn môi", "Giữ màu tới 8 giờ"]),
    (5, "Sắc Đẹp", "Sơn Móng Tay Màu Đỏ Bóng Khô Nhanh", 59000, 79000, 400,
     "Sơn móng màu đỏ bóng, khô chỉ sau 60 giây.", ["Khô nhanh 60 giây", "Bền màu 7 ngày"]),
    (119, "Sắc Đẹp", "Sữa Tắm Olay Ultra Moisture Bơ Hạt Mỡ 700ml", 219000, 269000, 180,
     "Sữa tắm Olay dưỡng ẩm sâu với bơ hạt mỡ.", ["Dưỡng ẩm suốt 24 giờ", "Hương thơm dịu nhẹ"]),
    (120, "Sắc Đẹp", "Sữa Dưỡng Thể Và Mặt Vaseline Men 200ml", 145000, None, 160,
     "Lotion Vaseline Men dùng cho cả mặt và cơ thể.", ["Thấm nhanh, không bết dính", "Dành riêng cho da nam"]),
    (8, "Sắc Đẹp", "Nước Hoa Nữ Dior J'adore Eau de Parfum 100ml", 3290000, 3890000, 15,
     "Dior J'adore hương hoa cỏ sang trọng, nữ tính.", ["Lưu hương 6–8 giờ", "Chai 100ml nguyên seal"]),
    (6, "Sắc Đẹp", "Nước Hoa Unisex Calvin Klein CK One EDT 100ml", 1190000, 1590000, 28,
     "CK One hương cam chanh tươi mát, dùng được cho cả nam và nữ.", ["Hương thơm trẻ trung", "Phù hợp dùng hằng ngày"]),

    (51, "Nhà Cửa & Đời Sống", "Máy Xay Sinh Tố Cối Lớn 1.5 Lít 600W", 690000, 890000, 55,
     "Máy xay sinh tố cối lớn, xay đá và trái cây nhuyễn mịn.", ["Công suất 600W", "Lưỡi dao thép không gỉ"]),
    (56, "Nhà Cửa & Đời Sống", "Bếp Điện Âm Mặt Kính 4 Vùng Nấu", 3490000, 4290000, 15,
     "Bếp điện mặt kính đen 4 vùng nấu, lắp âm hoặc đặt nổi đều được.", ["Mặt kính chịu nhiệt", "Núm vặn chỉnh nhiệt riêng từng vùng"]),
    (66, "Nhà Cửa & Đời Sống", "Lò Vi Sóng Có Nướng 20 Lít", 1990000, 2490000, 18,
     "Lò vi sóng 20 lít có chức năng nướng tiện lợi.", ["Dung tích 20 lít", "Chế độ rã đông nhanh"]),
    (52, "Nhà Cửa & Đời Sống", "Chảo Thép Carbon Sâu Lòng Tay Cầm Gỗ 28cm", 259000, None, 74,
     "Chảo thép carbon sâu lòng, xào nấu nhanh chín.", ["Đường kính 28cm", "Dùng được bếp từ"]),
    (47, "Nhà Cửa & Đời Sống", "Đèn Bàn Cổ Điển Chụp Vải Ánh Sáng Vàng", 239000, 320000, 90,
     "Đèn bàn kiểu cổ điển, chụp vải toả ánh sáng vàng ấm áp.", ["Bóng LED tiết kiệm điện", "Chụp vải mềm mại"]),
    (46, "Nhà Cửa & Đời Sống", "Chậu Cây Xanh Để Bàn Kèm Chậu Gốm Đen", 149000, None, 120,
     "Cây xanh nhỏ xinh kèm chậu gốm đen, cho góc làm việc thêm tươi mát.", ["Chậu gốm men đen", "Có lỗ thoát nước"]),
    (13, "Nhà Cửa & Đời Sống", "Tủ Đầu Giường Gỗ Gụ Một Ngăn Kéo Kèm Kệ Mở", 1590000, 1990000, 10,
     "Tủ đầu giường gỗ gụ một ngăn kéo kèm kệ mở, vân gỗ tự nhiên.", ["Gỗ tự nhiên chống mối mọt", "Ray trượt êm"]),

    (140, "Thể Thao & Du Lịch", "Bóng Rổ Số 7 Da PU Chống Trơn", 299000, 390000, 70,
     "Bóng rổ số 7 da PU bám tay, dùng sân trong nhà và ngoài trời.", ["Da PU chống trơn", "Giữ hơi lâu"]),
    (147, "Thể Thao & Du Lịch", "Bóng Đá Số 5 Khâu Tay Tiêu Chuẩn Thi Đấu", 249000, None, 95,
     "Bóng đá số 5 khâu tay, nảy đều và bay ổn định.", ["Khâu tay 32 múi", "Ruột cao su giữ hơi"]),
    (152, "Thể Thao & Du Lịch", "Vợt Tennis Khung Carbon Nhẹ 280g", 1290000, 1590000, 0,
     "Vợt tennis khung carbon nhẹ, phù hợp người mới chơi.", ["Trọng lượng 280g", "Tặng kèm bao vợt"]),
    (146, "Thể Thao & Du Lịch", "Hộp 12 Quả Cầu Lông Lông Vũ Tự Nhiên", 189000, 220000, 130,
     "Cầu lông lông vũ tự nhiên, bay đầm và ổn định.", ["Hộp 12 quả", "Đầu bần bọc da"]),
    (153, "Thể Thao & Du Lịch", "Bóng Chuyền Da Mềm Thi Đấu Số 5", 279000, None, 60,
     "Bóng chuyền da mềm số 5, không đau tay khi đỡ bóng.", ["Da mềm chống đau tay", "Đạt chuẩn thi đấu phong trào"]),

    (154, "Phụ Kiện Thời Trang", "Kính Râm Nam Nữ Gọng Đen Chống Tia UV400", 159000, 250000, 210,
     "Kính râm gọng đen basic, chống tia UV400 bảo vệ mắt.", ["Chống tia UV400", "Gọng nhựa dẻo bền"]),
    (155, "Phụ Kiện Thời Trang", "Kính Mát Phi Công Gọng Kim Loại Unisex", 179000, None, 140,
     "Kính mát dáng phi công cổ điển cho cả nam và nữ.", ["Tròng phân cực giảm chói", "Gọng kim loại mảnh"]),
    (182, "Phụ Kiện Thời Trang", "Bông Tai Nữ Đá Pha Lê Xanh Lục", 129000, 189000, 160,
     "Bông tai đá pha lê xanh lục lấp lánh.", ["Chốt bạc 925 không gây kích ứng", "Đá pha lê cắt giác"]),
    (183, "Phụ Kiện Thời Trang", "Bông Tai Nữ Tròn Họa Tiết Caro Xanh Mint", 149000, None, 120,
     "Bông tai tròn họa tiết caro xanh mint, phong cách retro cá tính.", ["Chất liệu acrylic siêu nhẹ", "Khoá bấm chắc chắn"]),
    (184, "Phụ Kiện Thời Trang", "Bông Tai Nữ Hình Lá Phong Cách Nhiệt Đới", 99000, 139000, 180,
     "Bông tai hình lá nhiệt đới, cực hợp đi biển.", ["Siêu nhẹ, đeo cả ngày", "Màu sắc tươi tắn"]),
]


def description(cat, intro, highlights):
    items = "".join(f"<li>{x}</li>" for x in highlights + CATEGORY_NOTES[cat])
    return (f"<p>{intro}</p><p><strong>Đặc điểm nổi bật:</strong></p><ul>{items}</ul>"
            "<p><strong>Cam kết của shop:</strong> giao hàng toàn quốc, được kiểm tra hàng trước khi thanh toán, "
            "đổi trả trong 7 ngày nếu sản phẩm lỗi do nhà sản xuất.</p>")


def to_jpeg(webp_bytes):
    im = Image.open(io.BytesIO(webp_bytes))
    if im.mode in ("RGBA", "LA", "P"):
        im = im.convert("RGBA")
        bg = Image.new("RGB", im.size, (255, 255, 255))
        bg.paste(im, mask=im.split()[3])
        im = bg
    else:
        im = im.convert("RGB")
    im.thumbnail((800, 800))
    out = io.BytesIO()
    im.save(out, "JPEG", quality=85)
    return out.getvalue()


def upload(token, jpeg):
    boundary = uuid.uuid4().hex
    body = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"image\"; filename=\"anh.jpg\"\r\n"
            f"Content-Type: image/jpeg\r\n\r\n").encode() + jpeg + f"\r\n--{boundary}--\r\n".encode()
    return must(http("POST", "/admin/upload-image", raw=body, token=token, ctype=f"multipart/form-data; boundary={boundary}"), 201)["url"]


CACHE = os.path.join(tempfile.gettempdir(), "shopee_seed_img_cache")


def fetch(url):
    f = os.path.join(CACHE, url.replace("https://", "").replace("/", "_"))
    if os.path.exists(f):
        return open(f, "rb").read()
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60) as r:
        data = r.read()
    os.makedirs(CACHE, exist_ok=True)
    open(f, "wb").write(data)
    return data


# ------------------------------------------------------------------ 0. chỉ nạp vào DB trống
if sql("SELECT (SELECT COUNT(*) FROM users) + (SELECT COUNT(*) FROM products) + (SELECT COUNT(*) FROM categories)").strip() != "0":
    sys.exit("shopee_db đã có dữ liệu — script chỉ nạp vào DB trống (tài khoản/danh mục sẽ trùng và bị 409).")

# ------------------------------------------------------------------ 1. tài khoản
print("1. Tài khoản")
must(http("POST", "/auth/register", {"email": "admin@shopeeclone.vn", "password": PASSWORD}), 201)
sql("UPDATE users SET role = 'ADMIN' WHERE email = 'admin@shopeeclone.vn'")
ADMIN = must(http("POST", "/auth/login", {"email": "admin@shopeeclone.vn", "password": PASSWORD}), 200)["accessToken"]
must(http("PUT", "/users/me", {"name": "Quản Trị Viên"}, ADMIN), 200)
CUSTOMERS = [
    ("an.nguyen@gmail.com", {"name": "Nguyễn Văn An", "phone": "0912345678", "dateOfBirth": "1998-05-20",
                             "address": "12 Nguyễn Trãi, Phường Bến Thành, Quận 1, TP. Hồ Chí Minh"}),
    ("linh.tran@gmail.com", {"name": "Trần Thuỳ Linh", "phone": "0987654321", "dateOfBirth": "2000-11-02",
                             "address": "45 Cầu Giấy, Phường Quan Hoa, Quận Cầu Giấy, Hà Nội"}),
    ("minh.le@gmail.com", {"name": "Lê Quang Minh", "phone": "0905123456", "dateOfBirth": "1995-03-14",
                           "address": "88 Bạch Đằng, Phường Hải Châu 1, Quận Hải Châu, Đà Nẵng"}),
]
TOK = {}
for email, profile in CUSTOMERS:
    TOK[email] = must(http("POST", "/auth/register", {"email": email, "password": PASSWORD}), 201)["accessToken"]
    must(http("PUT", "/users/me", profile, TOK[email]), 200)
print("   admin + 3 khách hàng")

# ------------------------------------------------------------------ 2. danh mục
print("2. Danh mục")
CAT = {name: must(http("POST", "/admin/categories", {"name": name}, ADMIN), 201)["id"] for name in CATEGORIES}
print(f"   {len(CAT)} danh mục")

# ------------------------------------------------------------------ 3. sản phẩm + ảnh
print("3. Sản phẩm (tải ảnh, đổi sang JPEG, upload qua API)")
PID = {}
n_img = 0
for dj_id, cat, name, price, before, qty, intro, highlights in PRODUCTS:
    src = json.loads(fetch(f"https://dummyjson.com/products/{dj_id}?select=images"))["images"]
    urls = [upload(ADMIN, to_jpeg(fetch(u))) for u in src[:4]]
    n_img += len(urls)
    body = {"name": name, "description": description(cat, intro, highlights), "price": price,
            "priceBeforeDiscount": before, "quantity": qty, "image": urls[0], "images": urls, "categoryId": CAT[cat]}
    PID[dj_id] = must(http("POST", "/admin/products", body, ADMIN), 201)["id"]
    print(f"   + {name[:60]:<60} {len(urls)} ảnh")
print(f"   {len(PID)} sản phẩm, {n_img} ảnh")

# ------------------------------------------------------------------ 4. số liệu hiển thị: đã bán, lượt xem, đánh giá, ngày đăng
print("4. Đã bán / lượt xem / đánh giá / ngày đăng")
stmts = []
for dj_id, cat, name, price, before, qty, *_ in PRODUCTS:
    base = 4000 if price < 300000 else 1500 if price < 1500000 else 400 if price < 10000000 else 60
    sold = max(3, int(random.lognormvariate(0, 1) * base / 3))
    view = sold * random.randint(6, 18) + random.randint(80, 900)
    rating = round(random.uniform(4.2, 5.0), 1)
    days, minutes = random.randint(1, 120), random.randint(0, 1439)
    stmts.append(f"UPDATE products SET sold = {sold}, view = {view}, rating = {rating}, "
                 f"created_at = UTC_TIMESTAMP(6) - INTERVAL {days} DAY - INTERVAL {minutes} MINUTE, "
                 f"updated_at = created_at WHERE id = {PID[dj_id]};")
sql(" ".join(stmts))

# ------------------------------------------------------------------ 5. giỏ hàng và đơn hàng
print("5. Đơn hàng và giỏ hàng")


def order(email, items, note=None):
    tok = TOK[email]
    ids = [must(http("POST", "/cart/items", {"productId": PID[d], "quantity": q}, tok), 200)["id"] for d, q in items]
    profile = dict(CUSTOMERS)[email]
    body = {"cartItemIds": ids, "recipientName": profile["name"], "recipientPhone": profile["phone"],
            "shippingAddress": profile["address"], "paymentMethod": "COD", "note": note}
    return must(http("POST", "/orders", body, tok), 201)["id"]


def admin_to(oid, *statuses, reason=None):
    for st in statuses:
        must(http("PUT", f"/admin/orders/{oid}/status", {"status": st, "cancelledReason": reason}, ADMIN), 200)


AN, LINH, MINH = (c[0] for c in CUSTOMERS)
admin_to(order(AN, [(83, 1), (91, 1)]), "CONFIRMED", "SHIPPING", "DELIVERED")
admin_to(order(AN, [(100, 1)], "Giao giờ hành chính giúp em"), "CONFIRMED", "SHIPPING")
admin_to(order(AN, [(47, 2)]), "CONFIRMED")
order(AN, [(1, 2), (4, 1)], "Gói quà giúp mình nhé")
must(http("PUT", f"/orders/{order(AN, [(154, 1)])}/cancel", {"cancelledReason": "Đặt nhầm mẫu, muốn đổi sang màu khác"}, TOK[AN]), 200)
admin_to(order(LINH, [(162, 1), (186, 1), (182, 1)]), "CONFIRMED", "SHIPPING", "DELIVERED")
order(LINH, [(119, 2)])
admin_to(order(MINH, [(140, 1), (147, 1)]), "CANCELLED", reason="Kho tạm hết bóng đá, shop đã liên hệ hoàn tiền")
for d, q in [(123, 1), (108, 1), (87, 2)]:
    must(http("POST", "/cart/items", {"productId": PID[d], "quantity": q}, TOK[AN]), 200)
for d, q in [(175, 1), (2, 1)]:
    must(http("POST", "/cart/items", {"productId": PID[d], "quantity": q}, TOK[LINH]), 200)

# phiên đăng nhập sinh ra trong lúc nạp dữ liệu — không cần giữ
sql("DELETE FROM refresh_tokens")
print("   xong")
print(sql("""SELECT CONCAT('users ', (SELECT COUNT(*) FROM users), ' | categories ', (SELECT COUNT(*) FROM categories),
                  ' | products ', (SELECT COUNT(*) FROM products), ' | product_images ', (SELECT COUNT(*) FROM product_images),
                  ' | orders ', (SELECT COUNT(*) FROM orders), ' | order_items ', (SELECT COUNT(*) FROM order_items),
                  ' | cart_items ', (SELECT COUNT(*) FROM cart_items), ' | refresh_tokens ', (SELECT COUNT(*) FROM refresh_tokens))"""))
