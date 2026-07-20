package com.ecoexpress.common.storage;

import com.ecoexpress.common.exception.ApiExceptions.BadRequestException;
import com.ecoexpress.common.exception.ApiExceptions.NotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * Upload and serve binary assets. Upload is staff-only (product:write, so catalog managers who add
 * images and certificates); serving is public (wired open in SecurityConfig) because the storefront
 * shows product photos and organic certificates to signed-out visitors. Serving only does anything
 * for the local adapter — with R2/S3, files are fetched from the store's own public URL.
 */
@Tag(name = "Files")
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    /** Guard the upload surface: images for the catalog, PDFs for certificates/invoices. */
    private static final Set<String> ALLOWED = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif", "application/pdf");
    private static final long MAX_BYTES = 10 * 1024 * 1024; // 10 MB

    private final StorageService storage;

    @Operation(summary = "Upload a file (staff) — returns its key and public URL")
    @PostMapping
    @PreAuthorize("hasAuthority('product:write')")
    public ResponseEntity<Map<String, String>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "product-images") String category) {
        if (file.isEmpty()) {
            throw new BadRequestException("The file is empty.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BadRequestException("File is larger than 10 MB.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED.contains(contentType)) {
            throw new BadRequestException("Unsupported file type. Allowed: JPEG, PNG, WebP, GIF, PDF.");
        }
        try {
            StorageService.StoredFile stored =
                    storage.store(category, file.getOriginalFilename(), contentType, file.getBytes());
            return ResponseEntity.ok(Map.of("key", stored.key(), "url", stored.url()));
        } catch (IOException e) {
            throw new BadRequestException("Could not read the uploaded file.");
        }
    }

    @Operation(summary = "Serve a stored file (public; local adapter only)")
    @GetMapping("/{category}/{name}")
    public ResponseEntity<byte[]> serve(@PathVariable String category, @PathVariable String name) {
        String key = category + "/" + name;
        StorageService.LoadedFile file = storage.load(key)
                .orElseThrow(() -> new NotFoundException("No file " + key));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .body(file.bytes());
    }
}
