package com.ecoexpress.identity.repository;

import com.ecoexpress.identity.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    @Query("""
            SELECT DISTINCT r FROM Role r
            LEFT JOIN FETCH r.permissions
            WHERE r.name = :name
            """)
    Optional<Role> findByNameWithPermissions(@Param("name") String name);

    Optional<Role> findByName(String name);
}
