package com.duynh.shopee.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Phục vụ ảnh: GET /images/abc.jpg → đọc file abc.jpg.
 * - Tìm trước ở thư mục upload trên đĩa (<app.upload.dir>/abc.jpg).
 * - Không thấy thì tìm ở app.upload.seed-images-location, nếu có đặt. Chỉ profile demo đặt nó, trỏ tới ảnh của
 *   dữ liệu mẫu nằm sẵn trong jar, nhờ vậy DB vừa dựng có ngay ảnh mà không phải chép file nào vào thư mục upload.
 * Spring tự đặt Content-Type theo đuôi file, tự trả 304 khi trình duyệt đã có bản cache,
 * và không cho đi ra ngoài thư mục bằng "..".
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final List<String> locations = new ArrayList<>();

    // "${...:}" — dấu hai chấm cuối là giá trị mặc định rỗng: không đặt thuộc tính thì nhận "" chứ không lỗi.
    public WebConfig(@Value("${app.upload.dir}") String uploadDir,
            @Value("${app.upload.seed-images-location:}") String seedImagesLocation) {
        locations.add("file:" + Path.of(uploadDir).toAbsolutePath().normalize() + "/");
        if (!seedImagesLocation.isBlank()) {
            locations.add(seedImagesLocation);
        }
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/images/**").addResourceLocations(locations.toArray(String[]::new));
    }
}
