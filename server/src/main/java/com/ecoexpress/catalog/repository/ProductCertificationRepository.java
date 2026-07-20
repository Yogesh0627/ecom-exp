package com.ecoexpress.catalog.repository;

import com.ecoexpress.catalog.domain.ProductCertification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductCertificationRepository extends JpaRepository<ProductCertification, UUID> {

    List<ProductCertification> findByProductIdOrderByCreatedAtDesc(UUID productId);
}
