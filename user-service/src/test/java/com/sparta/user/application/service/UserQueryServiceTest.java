package com.sparta.user.application.service;

import com.sparta.user.application.dto.request.UserSearchCondition;
import com.sparta.user.application.dto.response.UserResponseDto;
import com.sparta.user.domain.entity.User;
import com.sparta.user.domain.model.UserRole;
import com.sparta.user.global.exception.CustomException;
import com.sparta.user.global.exception.UserErrorCode;
import com.sparta.user.global.response.PageResponse;
import com.sparta.user.infrastructure.persistence.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class UserQueryServiceTest {

    @InjectMocks
    private UserQueryService userQueryService;

    @Mock
    private UserRepository userRepository;

    @Nested
    @DisplayName("단건 유저 조회 테스트")
    class GetUserByIdTest {

        @Test
        @DisplayName("성공: 존재하는 UUID 조회 시 UserResponseDto가 반환된다.")
        void getUserById_success() {
            // given
            UUID userId = UUID.randomUUID();
            User user = User.builder()
                    .email("test@example.com")
                    .password("hash")
                    .nickname("테스터")
                    .role(UserRole.ROLE_USER)
                    .build();
            setPrivateField(user, "userId", userId);

            given(userRepository.findByUserIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(user));

            // when
            UserResponseDto result = userQueryService.getUserById(userId);

            // then
            assertThat(result.getUserId()).isEqualTo(userId);
            assertThat(result.getEmail()).isEqualTo("test@example.com");
            assertThat(result.getNickname()).isEqualTo("테스터");
        }

        @Test
        @DisplayName("실패: 존재하지 않거나 삭제된 유저 조회 시 USER_NOT_FOUND 에러가 발생한다.")
        void getUserById_notFound() {
            // given
            UUID userId = UUID.randomUUID();
            given(userRepository.findByUserIdAndDeletedAtIsNull(userId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> userQueryService.getUserById(userId))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(UserErrorCode.USER_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("회원 목록 QueryDSL 페이징 조회 테스트")
    class GetUsersTest {

        @Test
        @DisplayName("성공: 검색 조건 및 페이징 정보로 조회 시 공통 PageResponse가 정상 생성된다.")
        void getUsers_success() {
            // given
            UserSearchCondition condition = new UserSearchCondition();
            condition.setEmail("test");

            Pageable pageable = PageRequest.of(0, 10);

            User user1 = User.builder().email("test1@example.com").password("hash").nickname("유저1").build();
            User user2 = User.builder().email("test2@example.com").password("hash").nickname("유저2").build();
            setPrivateField(user1, "userId", UUID.randomUUID());
            setPrivateField(user2, "userId", UUID.randomUUID());

            Page<User> userPage = new PageImpl<>(List.of(user1, user2), pageable, 2);
            given(userRepository.searchUsers(condition, pageable)).willReturn(userPage);

            // when
            PageResponse<UserResponseDto> response = userQueryService.getUsers(condition, pageable);

            // then
            assertThat(response.content()).hasSize(2);
            assertThat(response.page()).isEqualTo(0);
            assertThat(response.size()).isEqualTo(10);
            assertThat(response.totalElements()).isEqualTo(2);
            assertThat(response.totalPages()).isEqualTo(1);
            assertThat(response.hasNext()).isFalse();
        }
    }

    private void setPrivateField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}