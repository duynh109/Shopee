package com.duynh.shopee.config;

import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Phục vụ ảnh đã upload: GET /images/abc.jpg → đọc file
 * <app.upload.dir>/abc.jpg trên đĩa.
 * Spring tự đặt Content-Type theo đuôi file, tự trả 304 khi trình duyệt đã có
 * bản cache,
 * và không cho đi ra ngoài thư mục bằng "..".
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final String location;

    public WebConfig(@Value("${app.upload.dir}") String uploadDir) {
        this.location = "file:" + Path.of(uploadDir).toAbsolutePath().normalize() + "/";
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/images/**").addResourceLocations(location);
    }
}
