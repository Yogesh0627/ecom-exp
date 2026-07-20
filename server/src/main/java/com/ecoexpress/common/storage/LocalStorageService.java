package com.ecoexpress.common.storage;

import com.ecoexpress.common.exception.ApiExceptions.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.UUID;

/**
 * Development storage adapter: writes to a local directory and serves bytes back through
 * {@code GET /api/v1/files/**}. The default when {@code ecoexpress.storage.provider} is unset, so
 * the app runs with zero cloud setup. Prod swaps in {@link S3StorageService} by config alone.
 */
@Slf4j
@Service
@EnableConfigurationProperties(StorageProperties.class)
@ConditionalOnProperty(name = "ecoexpress.storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    private final Path root;
    private final String publicBaseUrl;

    public LocalStorageService(StorageProperties props) {
        this.root = Paths.get(props.getLocal().getDir()).toAbsolutePath().normalize();
        this.publicBaseUrl = trimTrailingSlash(props.getLocal().getPublicBaseUrl());
        try {
            Files.createDirectories(root);
            log.info("Local storage at {}", root);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create storage dir " + root, e);
        }
    }

    @Override
    public StoredFile store(String category, String originalFilename, String contentType, byte[] bytes) {
        String key = safeCategory(category) + "/" + UUID.randomUUID() + extension(originalFilename);
        Path target = resolveWithinRoot(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store " + key, e);
        }
        return new StoredFile(key, urlFor(key));
    }

    @Override
    public Optional<LoadedFile> load(String key) {
        Path path = resolveWithinRoot(key);
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        try {
            String contentType = Files.probeContentType(path);
            return Optional.of(new LoadedFile(Files.readAllBytes(path),
                    contentType == null ? "application/octet-stream" : contentType));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolveWithinRoot(key));
        } catch (IOException e) {
            log.warn("Failed to delete {}: {}", key, e.getMessage());
        }
    }

    @Override
    public String urlFor(String key) {
        return publicBaseUrl + "/api/v1/files/" + key;
    }

    /** Guard against path traversal: a key must resolve inside the storage root. */
    private Path resolveWithinRoot(String key) {
        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root)) {
            throw new BadRequestException("Invalid storage key.");
        }
        return resolved;
    }

    private static String safeCategory(String category) {
        if (category == null || !category.matches("[a-z0-9-]{1,40}")) {
            throw new BadRequestException("Invalid storage category.");
        }
        return category;
    }

    private static String extension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) return "";
        String ext = filename.substring(dot).toLowerCase();
        return ext.matches("\\.[a-z0-9]{1,8}") ? ext : "";
    }

    private static String trimTrailingSlash(String s) {
        return s != null && s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
