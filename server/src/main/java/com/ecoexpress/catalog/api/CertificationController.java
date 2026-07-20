package com.ecoexpress.catalog.api;

import com.ecoexpress.catalog.domain.CertificationType;
import com.ecoexpress.catalog.domain.Product;
import com.ecoexpress.catalog.domain.ProductCertification;
import com.ecoexpress.catalog.repository.ProductCertificationRepository;
import com.ecoexpress.catalog.repository.ProductRepository;
import com.ecoexpress.common.exception.ApiExceptions.NotFoundException;
import com.ecoexpress.common.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Organic/quality certificates for a product (V9, differentiator). Reads are public — the storefront
 * shows the certificate documents as proof of the organic claim. Writes need product:write.
 */
@Tag(name = "Certifications")
@RestController
@RequiredArgsConstructor
public class CertificationController {

    private final ProductCertificationRepository certRepository;
    private final ProductRepository productRepository;

    public record CreateCertRequest(
            @NotNull CertificationType certType,
            String issuingBody,
            String certificateNumber,
            @NotBlank String documentUrl,
            LocalDate validFrom,
            LocalDate validUntil,
            String notes) {}

    public record CertView(
            UUID id, String certType, String issuingBody, String certificateNumber,
            String documentUrl, LocalDate validFrom, LocalDate validUntil,
            boolean verified, boolean expired) {}

    @Operation(summary = "A product's certificates (public — proof of the organic claim)")
    @GetMapping("/api/v1/products/{slug}/certifications")
    @Transactional(readOnly = true)
    public ResponseEntity<List<CertView>> forProduct(@PathVariable String slug) {
        Product product = productRepository.findBySlugWithDetails(slug)
                .orElseThrow(() -> new NotFoundException("No product '" + slug + "'."));
        List<CertView> certs = certRepository.findByProductIdOrderByCreatedAtDesc(product.getId())
                .stream().map(this::view).toList();
        return ResponseEntity.ok(certs);
    }

    @Operation(summary = "Add a certificate to a product (admin)")
    @PostMapping("/api/v1/admin/products/{productId}/certifications")
    @PreAuthorize("hasAuthority('product:write')")
    @Transactional
    public ResponseEntity<CertView> add(@PathVariable UUID productId,
                                        @Valid @RequestBody CreateCertRequest r) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("No product " + productId));
        ProductCertification cert = certRepository.save(ProductCertification.builder()
                .product(product)
                .certType(r.certType())
                .issuingBody(r.issuingBody())
                .certificateNumber(r.certificateNumber())
                .documentUrl(r.documentUrl())
                .validFrom(r.validFrom())
                .validUntil(r.validUntil())
                .notes(r.notes())
                .verified(false)
                .build());
        return ResponseEntity.status(HttpStatus.CREATED).body(view(cert));
    }

    @Operation(summary = "Mark a certificate verified (admin checked the document)")
    @PostMapping("/api/v1/admin/certifications/{id}/verify")
    @PreAuthorize("hasAuthority('product:write')")
    @Transactional
    public ResponseEntity<CertView> verify(@AuthenticationPrincipal AuthenticatedUser actor,
                                           @PathVariable UUID id) {
        ProductCertification cert = certRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("No certification " + id));
        cert.setVerified(true);
        cert.setVerifiedAt(Instant.now());
        cert.setVerifiedBy(actor.getId());
        return ResponseEntity.ok(view(cert));
    }

    @Operation(summary = "Remove a certificate (admin)")
    @DeleteMapping("/api/v1/admin/certifications/{id}")
    @PreAuthorize("hasAuthority('product:write')")
    @Transactional
    public ResponseEntity<Void> remove(@PathVariable UUID id) {
        ProductCertification cert = certRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("No certification " + id));
        cert.setDeletedAt(Instant.now());
        return ResponseEntity.noContent().build();
    }

    private CertView view(ProductCertification c) {
        boolean expired = c.getValidUntil() != null && c.getValidUntil().isBefore(LocalDate.now());
        return new CertView(c.getId(), c.getCertType().name(), c.getIssuingBody(),
                c.getCertificateNumber(), c.getDocumentUrl(), c.getValidFrom(), c.getValidUntil(),
                Boolean.TRUE.equals(c.getVerified()), expired);
    }
}
