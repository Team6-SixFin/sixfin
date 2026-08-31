package com.sparta.user.infrastructure.persistence.repository;

import com.sparta.user.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

}