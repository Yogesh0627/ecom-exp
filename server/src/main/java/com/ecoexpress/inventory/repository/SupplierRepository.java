package com.ecoexpress.inventory.repository;

import com.ecoexpress.inventory.domain.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

    Optional<Supplier> findByCode(String code);

    boolean existsByCode(String code);

    java.util.List<Supplier> findAllByOrderByNameAsc();
}
