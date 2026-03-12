package com.mprs.security;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Data access for the users table.
 * Used exclusively by the security layer.
 */
@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {

    /**
     * Finds a user by their username.
     * Used during login and JWT token validation.
     *
     * @param username the username to search for
     * @return Optional containing the user if found
     */
    Optional<UserEntity> findByUsername(String username);

    /**
     * Checks if a user with the given username exists.
     * Used by DevDataInitializer to avoid duplicate seed inserts.
     */
    boolean existsByUsername(String username);
}