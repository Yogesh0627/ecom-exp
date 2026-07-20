package com.ecoexpress.engagement.repository;

import com.ecoexpress.engagement.domain.ReviewImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReviewImageRepository extends JpaRepository<ReviewImage, UUID> {
}
