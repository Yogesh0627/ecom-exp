package com.ecoexpress.common.storage;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;

/**
 * Production storage adapter for any S3-compatible object store. Because Cloudflare R2 and AWS S3
 * share the S3 protocol, this one class targets both — R2 by setting an endpoint and region "auto",
 * AWS by leaving the endpoint blank and using a real region. Objects are served directly from the
 * store's public/CDN URL, so {@link #load} returns empty (the app never proxies the bytes).
 *
 * <p>Activated only when {@code ecoexpress.storage.provider=s3}; dev keeps {@link LocalStorageService}.
 */
@Slf4j
@Service
@EnableConfigurationProperties(StorageProperties.class)
@ConditionalOnProperty(name = "ecoexpress.storage.provider", havingValue = "s3")
public class S3StorageService implements StorageService {

    private final StorageProperties.S3 cfg;
    private final String publicBaseUrl;
    private S3Client client;

    public S3StorageService(StorageProperties props) {
        this.cfg = props.getS3();
        this.publicBaseUrl = trimTrailingSlash(cfg.getPublicBaseUrl());
    }

    @PostConstruct
    void init() {
        var builder = S3Client.builder()
                .region(Region.of(cfg.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(cfg.getAccessKey(), cfg.getSecretKey())))
                // Path-style access is what R2 (and MinIO) expect.
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        if (cfg.getEndpoint() != null && !cfg.getEndpoint().isBlank()) {
            builder.endpointOverride(URI.create(cfg.getEndpoint()));
        }
        this.client = builder.build();
        log.info("S3-compatible storage: bucket={} endpoint={}", cfg.getBucket(),
                cfg.getEndpoint().isBlank() ? "(aws default)" : cfg.getEndpoint());
    }

    @PreDestroy
    void close() {
        if (client != null) {
            client.close();
        }
    }

    @Override
    public StoredFile store(String category, String originalFilename, String contentType, byte[] bytes) {
        String key = category + "/" + UUID.randomUUID() + extension(originalFilename);
        client.putObject(PutObjectRequest.builder()
                        .bucket(cfg.getBucket())
                        .key(key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(bytes));
        return new StoredFile(key, urlFor(key));
    }

    /** Cloud objects are fetched from the public URL, not proxied through the app. */
    @Override
    public Optional<LoadedFile> load(String key) {
        return Optional.empty();
    }

    @Override
    public void delete(String key) {
        client.deleteObject(DeleteObjectRequest.builder().bucket(cfg.getBucket()).key(key).build());
    }

    @Override
    public String urlFor(String key) {
        return publicBaseUrl + "/" + key;
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
