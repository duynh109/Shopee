package com.duynh.shopee;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// App không đăng nhập bằng username/password của Spring Security mà tự lo bằng JWT, nên không có bean
// UserDetailsService nào. Không exclude thì Spring Boot tự tạo một user ảo và in "Using generated
// security password" ra log — vô dụng và gây hiểu nhầm.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class ShopeeApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShopeeApplication.class, args);
    }

}
