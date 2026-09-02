package com.sparta.user.application.service;

import com.sparta.user.application.dto.request.UserSearchCondition;
import com.sparta.user.application.dto.response.UserResponseDto;
import com.sparta.user.domain.entity.User;
import com.sparta.user.global.exception.CustomException;
import com.sparta.user.global.exception.UserErrorCode;
import com.sparta.user.global.response.PageResponse;
import com.sparta.user.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserQueryService {

    private final UserRepository userRepository;

    public UserResponseDto getUserById(UUID userId) {
        User user = userRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
        return UserResponseDto.from(user);
    }

    public PageResponse<UserResponseDto> getUsers(UserSearchCondition condition, Pageable pageable) {
        Page<User> userPage = userRepository.searchUsers(condition, pageable);
        List<UserResponseDto> content = userPage.getContent().stream()
                .map(UserResponseDto::from)
                .toList();

        return PageResponse.from(userPage, content);
    }

}
