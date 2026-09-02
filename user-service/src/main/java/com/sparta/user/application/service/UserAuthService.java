package com.sparta.user.application.service;

import com.sparta.user.application.dto.request.LoginRequestDto;
import com.sparta.user.application.dto.request.SignupRequestDto;
import com.sparta.user.application.dto.response.TokenResponseDto;
import com.sparta.user.domain.entity.User;
import com.sparta.user.global.exception.CustomException;
import com.sparta.user.global.exception.UserErrorCode;
import com.sparta.user.global.security.JwtUtil;
import com.sparta.user.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/**
 * 회원 인증 비즈니스 서비스
 */
@Service
@RequiredArgsConstructor
public class UserAuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, String> redisTemplate;

    @Transactional
    public void signup(SignupRequestDto requestDto) {
        if (userRepository.existsByEmailAndDeletedAtIsNull(requestDto.getEmail())) {
            throw new CustomException(UserErrorCode.DUPLICATE_EMAIL);
        }
        if (userRepository.existsByNicknameAndDeletedAtIsNull(requestDto.getNickname())) {
            throw new CustomException(UserErrorCode.DUPLICATE_NICKNAME);
        }

        User user = User.builder()
                .email(requestDto.getEmail())
                .password(passwordEncoder.encode(requestDto.getPassword()))
                .nickname(requestDto.getNickname())
                .build();

        userRepository.save(user);
    }

    @Transactional
    public TokenResponseDto login(LoginRequestDto requestDto) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(requestDto.getEmail())
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(requestDto.getPassword(), user.getPassword())) {
            throw new CustomException(UserErrorCode.INVALID_PASSWORD);
        }

        String accessToken = jwtUtil.createAccessToken(user.getUserId(), user.getRole());
        String refreshToken = jwtUtil.createRefreshToken(user.getUserId());

        redisTemplate.opsForValue().set(
                "RT:" + user.getUserId(),
                refreshToken,
                Duration.ofDays(14)
        );

        return new TokenResponseDto(accessToken, refreshToken);
    }
}
