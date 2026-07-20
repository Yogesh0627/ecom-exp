package com.ecoexpress.identity.repository;

import com.ecoexpress.identity.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Fetches roles and permissions in one query. Without the joins this is
     * 1 + N(roles) + N(permissions) queries on every single login and token refresh.
     * DISTINCT because the two collection joins multiply the rows.
     */
    @Query("""
            SELECT DISTINCT u FROM User u
            LEFT JOIN FETCH u.roles r
            LEFT JOIN FETCH r.permissions
            WHERE lower(u.email) = lower(:email)
            """)
    Optional<User> findByEmailWithAuthorities(@Param("email") String email);

    @Query("""
            SELECT DISTINCT u FROM User u
            LEFT JOIN FETCH u.roles r
            LEFT JOIN FETCH r.permissions
            WHERE u.id = :id
            """)
    Optional<User> findByIdWithAuthorities(@Param("id") UUID id);

    /** Email is CITEXT in the DB, but lower() keeps the check correct regardless of JPA dialect. */
    @Query("SELECT COUNT(u) > 0 FROM User u WHERE lower(u.email) = lower(:email)")
    boolean existsByEmailIgnoreCase(@Param("email") String email);
}
