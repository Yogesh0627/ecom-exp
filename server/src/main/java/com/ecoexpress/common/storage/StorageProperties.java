package com.ecoexpress.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Storage config. {@code provider} selects the adapter: {@code local} (dev, filesystem) or
 * {@code s3} (prod — Cloudflare R2 or AWS S3, same protocol).
 */
@ConfigurationProperties(prefix = "ecoexpress.storage")
public class StorageProperties {

    /** local | s3 */
    private String provider = "local";
    private final Local local = new Local();
    private final S3 s3 = new S3();

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public Local getLocal() { return local; }
    public S3 getS3() { return s3; }

    public static class Local {
        /** Directory the files are written to. */
        private String dir = "./storage";
        /** Base URL of this app, so a stored key becomes a fetchable {base}/api/v1/files/{key}. */
        private String publicBaseUrl = "http://localhost:8081";

        public String getDir() { return dir; }
        public void setDir(String dir) { this.dir = dir; }
        public String getPublicBaseUrl() { return publicBaseUrl; }
        public void setPublicBaseUrl(String publicBaseUrl) { this.publicBaseUrl = publicBaseUrl; }
    }

    public static class S3 {
        /** R2: https://<account>.r2.cloudflarestorage.com. AWS: leave blank for the default. */
        private String endpoint = "";
        /** R2 uses "auto"; AWS uses a real region like ap-south-1. */
        private String region = "auto";
        private String bucket = "";
        private String accessKey = "";
        private String secretKey = "";
        /** Public/CDN base URL objects are served from (R2 public bucket or a custom domain). */
        private String publicBaseUrl = "";

        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
        public String getRegion() { return region; }
        public void setRegion(String region) { this.region = region; }
        public String getBucket() { return bucket; }
        public void setBucket(String bucket) { this.bucket = bucket; }
        public String getAccessKey() { return accessKey; }
        public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
        public String getSecretKey() { return secretKey; }
        public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
        public String getPublicBaseUrl() { return publicBaseUrl; }
        public void setPublicBaseUrl(String publicBaseUrl) { this.publicBaseUrl = publicBaseUrl; }
    }
}
