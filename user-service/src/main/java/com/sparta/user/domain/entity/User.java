package com.sparta.user.domain.entity;

import com.sparta.user.domain.model.UserRole;
import com.sparta.user.domain.model.UserStatus;
import com.sparta.user.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "p_users")
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_id", updatable = false, nullable = false)
    private UUID userId;

    @Column(name = "email", nullable = false, length = 100)
    private String email;

    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @Column(name = "nickname", nullable = false, length = 50)
    private String nickname;

    // 네이티브 ENUM 타입을 피하고 DB 컬럼 VARCHAR(20)에 매핑되도록 처리
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role;

    // 네이티브 ENUM 타입을 피하고 DB 컬럼 VARCHAR(20)에 매핑되도록 처리
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status;

    @Builder
    public User(String email, String password, String nickname, UserRole role, UserStatus status) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.role = role != null ? role : UserRole.ROLE_USER;
        this.status = status != null ? status : UserStatus.ACTIVE;
    }

}
