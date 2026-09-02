package com.sparta.user.application.service;

import com.sparta.user.application.dto.request.LoginRequestDto;
import com.sparta.user.application.dto.request.SignupRequestDto;
import com.sparta.user.application.dto.response.TokenResponseDto;
import com.sparta.user.domain.entity.User;
import com.sparta.user.domain.model.UserRole;
import com.sparta.user.global.exception.CustomException;
import com.sparta.user.global.exception.UserErrorCode;
import com.sparta.user.global.security.JwtUtil;
import com.sparta.user.infrastructure.persistence.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserAuthServiceTest {

    @InjectMocks
    private UserAuthService userAuthService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Nested
    @DisplayName("회원가입 테스트")
    class SignupTest {

        @Test
        @DisplayName("성공: 올바른 회원가입 정보가 들어오면 정상 가입된다.")
        void signup_success() {
            // given
            SignupRequestDto requestDto = createSignupDto("test@example.com", "password123!", "테스터");
            given(userRepository.existsByEmailAndDeletedAtIsNull(requestDto.getEmail())).willReturn(false);
            given(userRepository.existsByNicknameAndDeletedAtIsNull(requestDto.getNickname())).willReturn(false);
            given(passwordEncoder.encode(requestDto.getPassword())).willReturn("encodedPassword");

            // when
            userAuthService.signup(requestDto);

            // then
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("실패: 이메일 중복 시 CustomException(DUPLICATE_EMAIL)이 발생한다.")
        void signup_duplicateEmail() {
            // given
            SignupRequestDto requestDto = createSignupDto("duplicate@example.com", "password123!", "테스터");
            given(userRepository.existsByEmailAndDeletedAtIsNull(requestDto.getEmail())).willReturn(true);

            // when & then
            assertThatThrownBy(() -> userAuthService.signup(requestDto))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(UserErrorCode.DUPLICATE_EMAIL);
        }

        @Test
        @DisplayName("실패: 닉네임 중복 시 CustomException(DUPLICATE_NICKNAME)이 발생한다.")
        void signup_duplicateNickname() {
            // given
            SignupRequestDto requestDto = createSignupDto("test@example.com", "password123!", "중복닉네임");
            given(userRepository.existsByEmailAndDeletedAtIsNull(requestDto.getEmail())).willReturn(false);
            given(userRepository.existsByNicknameAndDeletedAtIsNull(requestDto.getNickname())).willReturn(true);

            // when & then
            assertThatThrownBy(() -> userAuthService.signup(requestDto))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(UserErrorCode.DUPLICATE_NICKNAME);
        }
    }

    @Nested
    @DisplayName("로그인 테스트")
    class LoginTest {

        @Test
        @DisplayName("성공: 비밀번호가 일치하면 토큰 발급 및 Redis에 Refresh Token이 저장된다.")
        void login_success() throws Exception {
            // given
            LoginRequestDto requestDto = createLoginDto("test@example.com", "password123!");
            UUID userId = UUID.randomUUID();
            User user = User.builder()
                    .email(requestDto.getEmail())
                    .password("encodedPassword")
                    .nickname("테스터")
                    .role(UserRole.ROLE_USER)
                    .build();
            setPrivateField(user, "userId", userId);

            given(userRepository.findByEmailAndDeletedAtIsNull(requestDto.getEmail())).willReturn(Optional.of(user));
            given(passwordEncoder.matches(requestDto.getPassword(), user.getPassword())).willReturn(true);
            given(jwtUtil.createAccessToken(userId, UserRole.ROLE_USER)).willReturn("mockAccessToken");
            given(jwtUtil.createRefreshToken(userId)).willReturn("mockRefreshToken");
            given(redisTemplate.opsForValue()).willReturn(valueOperations);

            // when
            TokenResponseDto result = userAuthService.login(requestDto);

            // then
            assertThat(result.getAccessToken()).isEqualTo("mockAccessToken");
            assertThat(result.getRefreshToken()).isEqualTo("mockRefreshToken");
            verify(valueOperations).set(eq("RT:" + userId), eq("mockRefreshToken"), any(Duration.class));
        }

        @Test
        @DisplayName("실패: 존재하지 않는 이메일 로그인 시 USER_NOT_FOUND 에러가 발생한다.")
        void login_notFound() {
            // given
            LoginRequestDto requestDto = createLoginDto("none@example.com", "password123!");
            given(userRepository.findByEmailAndDeletedAtIsNull(requestDto.getEmail())).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> userAuthService.login(requestDto))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(UserErrorCode.USER_NOT_FOUND);
        }

        @Test
        @DisplayName("실패: 비밀번호가 불일치하면 INVALID_PASSWORD 에러가 발생한다.")
        void login_invalidPassword() {
            // given
            LoginRequestDto requestDto = createLoginDto("test@example.com", "wrongPassword");
            User user = User.builder()
                    .email(requestDto.getEmail())
                    .password("encodedPassword")
                    .nickname("테스터")
                    .build();

            given(userRepository.findByEmailAndDeletedAtIsNull(requestDto.getEmail())).willReturn(Optional.of(user));
            given(passwordEncoder.matches(requestDto.getPassword(), user.getPassword())).willReturn(false);

            // when & then
            assertThatThrownBy(() -> userAuthService.login(requestDto))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(UserErrorCode.INVALID_PASSWORD);
        }
    }

    private SignupRequestDto createSignupDto(String email, String password, String nickname) {
        SignupRequestDto dto = new SignupRequestDto();
        setPrivateField(dto, "email", email);
        setPrivateField(dto, "password", password);
        setPrivateField(dto, "nickname", nickname);
        return dto;
    }

    private LoginRequestDto createLoginDto(String email, String password) {
        LoginRequestDto dto = new LoginRequestDto();
        setPrivateField(dto, "email", email);
        setPrivateField(dto, "password", password);
        return dto;
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