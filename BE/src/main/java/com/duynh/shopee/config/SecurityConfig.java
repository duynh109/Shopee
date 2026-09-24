package com.duynh.shopee.config;

import java.util.List;

import com.duynh.shopee.auth.ApiAccessDeniedHandler;
import com.duynh.shopee.auth.JwtAuthenticationEntryPoint;
import com.duynh.shopee.auth.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final ApiAccessDeniedHandler apiAccessDeniedHandler;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
            JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
            ApiAccessDeniedHandler apiAccessDeniedHandler) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
        this.apiAccessDeniedHandler = apiAccessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(apiAccessDeniedHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // ---- Công khai: không cần đăng nhập ----
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/products/**", "/api/categories/**").permitAll()

                        // ---- Chỉ ADMIN ----
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // ---- Phải đăng nhập, role nào cũng được ----
                        // Quyền trên từng bản ghi ("profile này có phải của bạn?") không phải
                        // việc của SecurityConfig — /me tự giải quyết bằng danh tính trong token.
                        .requestMatchers("/api/users/**").authenticated()
                        .requestMatchers("/api/cart/**").authenticated()
                        // ---- Còn lại: phải đăng nhập ----
                        .anyRequest().authenticated());
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:3000"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
