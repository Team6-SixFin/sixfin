package com.sparta.user.infrastructure.persistence.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.sparta.user.application.dto.request.UserSearchCondition;
import com.sparta.user.domain.entity.User;
import com.sparta.user.domain.model.UserRole;
import com.sparta.user.domain.model.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.sparta.user.domain.entity.QUser.user;

/**
 * QueryDSL 동적 쿼리 구현체
 */
@RequiredArgsConstructor
public class UserRepositoryCustomImpl implements UserRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<User> searchUsers(UserSearchCondition condition, Pageable pageable) {
        List<User> content = queryFactory
                .selectFrom(user)
                .where(
                        emailContains(condition.getEmail()),
                        nicknameContains(condition.getNickname()),
                        roleEq(condition.getRole()),
                        statusEq(condition.getStatus()),
                        user.deletedAt.isNull()
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(user.createdAt.desc())
                .fetch();

        Long total = queryFactory
                .select(user.count())
                .from(user)
                .where(
                        emailContains(condition.getEmail()),
                        nicknameContains(condition.getNickname()),
                        roleEq(condition.getRole()),
                        statusEq(condition.getStatus()),
                        user.deletedAt.isNull()
                )
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    private BooleanExpression emailContains(String email) {
        return StringUtils.hasText(email) ? user.email.contains(email) : null;
    }

    private BooleanExpression nicknameContains(String nickname) {
        return StringUtils.hasText(nickname) ? user.nickname.contains(nickname) : null;
    }

    private BooleanExpression roleEq(UserRole role) {
        return role != null ? user.role.eq(role) : null;
    }

    private BooleanExpression statusEq(UserStatus status) {
        return status != null ? user.status.eq(status) : null;
    }
}