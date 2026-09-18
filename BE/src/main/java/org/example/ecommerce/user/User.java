package org.example.ecommerce.user;

import java.time.Instant;
import java.time.LocalDate;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Không đặt @Setter ở cấp class: Lombok sẽ sinh setter cho MỌI field, kể cả id, email, role
 * và hai mốc thời gian kiểm toán — những thứ không được phép sửa tuỳ tiện. @Setter chỉ đặt trên
 * đúng các field mà nghiệp vụ thật sự cần đổi sau khi user đã tồn tại.
 *
 * Hibernate không cần setter: vì @Id nằm trên field nên nó truy cập field trực tiếp.
 */
@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Định danh đăng nhập, cố ý không cho đổi — xem API_SPEC §4.2. */
    @Column(nullable = false, unique = true, length = 160)
    private String email;

    @Setter
    @Column(nullable = false, length = 100)
    private String password;

    /**
     * Không có setter ở v1: register luôn tạo "USER", muốn phong ADMIN thì UPDATE bằng SQL.
     * Bước 8 làm API admin phong quyền thì thêm @Setter — khi đó là một quyết định có ý thức.
     */
    @Column(nullable = false, length = 20)
    private String role; // "USER" hoặc "ADMIN"

    @Setter
    @Column(length = 160)
    private String name;

    @Setter
    @Column(length = 20)
    private String phone;

    @Setter
    @Column(length = 255)
    private String address;

    @Setter
    private LocalDate dateOfBirth;

    @Setter
    @Column(length = 255)
    private String avatar;

    /** Do AuditingEntityListener điền, không setter để không ai sửa được dấu vết kiểm toán. */
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    public User(String email, String password, String role) {
        this.email = email;
        this.password = password;
        this.role = role;
    }

}
