package com.ecommerce.auth.repository;

import com.ecommerce.auth.entity.RefreshToken;
import com.ecommerce.auth.entity.User;

import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Modifying;

import org.springframework.data.jpa.repository.Query;

import org.springframework.data.repository.query.Param;

import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository
        extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByToken(String token);

    @Modifying
    @Transactional
    @Query("""
           delete from RefreshToken rt
           where rt.user = :user
           """)
    void deleteByUser(@Param("user") User user);
}