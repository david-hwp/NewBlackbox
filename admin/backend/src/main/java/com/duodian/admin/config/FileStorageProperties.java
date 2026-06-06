package com.duodian.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.storage")
public class FileStorageProperties {
    private String type = "local";
    private String localRoot = "~/zhanghaoguanjia/files/";
    private String endpoint;
    private String bucket;
    private String region;
    private String accessKey;
    private String secretKey;
    private String publicBaseUrl;
    private String addressingStyle = "path";

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getLocalRoot() { return localRoot; }
    public void setLocalRoot(String localRoot) { this.localRoot = localRoot; }

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }

    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }

    public String getPublicBaseUrl() { return publicBaseUrl; }
    public void setPublicBaseUrl(String publicBaseUrl) { this.publicBaseUrl = publicBaseUrl; }

    public String getAddressingStyle() { return addressingStyle; }
    public void setAddressingStyle(String addressingStyle) { this.addressingStyle = addressingStyle; }
}
