package com.ecoexpress.common.storage;

import java.util.Optional;

/**
 * The single doorway for storing and retrieving binary assets — product images, organic
 * certificates, generated invoice PDFs.
 *
 * <p>Provider-agnostic on purpose, the same way {@code AiClient} hides the AI provider. Callers
 * depend only on this interface, so moving from local disk (dev) to Cloudflare R2 / AWS S3 (prod)
 * is one adapter + config, and touches no feature code. R2 and S3 speak the same protocol, so a
 * single {@code S3StorageService} serves both — you switch by endpoint and keys.
 */
public interface StorageService {

    /** A stored object: the opaque key we persist on the entity, and a URL to fetch it. */
    record StoredFile(String key, String url) {}

    /** Bytes loaded back for serving (used by adapters that stream through the app, i.e. local). */
    record LoadedFile(byte[] bytes, String contentType) {}

    /**
     * Store bytes under a logical category (e.g. {@code product-images}, {@code certificates},
     * {@code invoices}) and return the key + public URL.
     */
    StoredFile store(String category, String originalFilename, String contentType, byte[] bytes);

    /**
     * Load an object's bytes by key. Adapters that serve directly from the cloud (R2/S3 public URL)
     * may return empty — only the local adapter streams bytes back through the app.
     */
    Optional<LoadedFile> load(String key);

    void delete(String key);

    /** Public URL for an already-stored key (so callers can rebuild a link without re-storing). */
    String urlFor(String key);
}
