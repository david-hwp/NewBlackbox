package com.duodian.admin.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "shops")
public class Shop {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "channel_id")
    private Long channelId;

    @Column(name = "shop_name", nullable = false)
    private String shopName;

    @Column(name = "shop_id", nullable = false)
    private String shopId;

    @Column(name = "identity_verified", nullable = false, columnDefinition = "TINYINT DEFAULT 0")
    private Boolean identityVerified = false;

    @Column(name = "identity_verified_at")
    private LocalDateTime identityVerifiedAt;

    @Column(nullable = false)
    private String platform; // meituan, taobao, jd, kuaishou, xiaohongshu, ali

    @Column(name = "platform_name")
    private String platformName;

    @Column(name = "remaining_days")
    private Integer remainingDays = 0;

    @Column(name = "auto_renew")
    private Boolean autoRenew = false;

    @Column(name = "package_name")
    private String packageName;

    @Column(name = "clone_instance_id", length = 255)
    private String cloneInstanceId;

    @Column(name = "clone_sequence")
    private Integer cloneSequence;

    @Column(name = "local_virtual_user_id")
    private Integer localVirtualUserId;

    @JsonIgnore
    @Column(name = "clone_validation_code", length = 64)
    private String cloneValidationCode;

    @JsonIgnore
    @Column(name = "clone_validation_hash", length = 128)
    private String cloneValidationHash;

    @Column(name = "credential_version")
    private Integer credentialVersion = 1;

    @Column(name = "auth_start_at")
    private LocalDateTime authStartAt;

    @Column(name = "auth_expire_at")
    private LocalDateTime authExpireAt;

    @Column(name = "authorization_jti", length = 64)
    private String authorizationJti;

    @Column(name = "last_deducted_at")
    private LocalDateTime lastDeductedAt;

    @Column(name = "expire_at")
    private LocalDateTime expireAt;

    @Column(name = "login_state_profile", length = 128)
    private String loginStateProfile;

    @Column(name = "login_state_size")
    private Long loginStateSize;

    @Column(name = "login_state_sha256", length = 64)
    private String loginStateSha256;

    @Column(name = "login_state_manifest", columnDefinition = "TEXT")
    private String loginStateManifest;

    @JsonIgnore
    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "login_state_blob", columnDefinition = "LONGBLOB")
    private byte[] loginStateBlob;

    @Column(name = "login_state_updated_at")
    private LocalDateTime loginStateUpdatedAt;

    @Column(name = "login_state_artifact_created_at")
    private LocalDateTime loginStateArtifactCreatedAt;

    @Column(nullable = false, columnDefinition = "TINYINT DEFAULT 0")
    private Byte deleted = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (deleted == null) {
            deleted = 0;
        }
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Shop() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getChannelId() { return channelId; }
    public void setChannelId(Long channelId) { this.channelId = channelId; }

    public String getShopName() { return shopName; }
    public void setShopName(String shopName) { this.shopName = shopName; }

    public String getShopId() { return shopId; }
    public void setShopId(String shopId) { this.shopId = shopId; }

    public Boolean getIdentityVerified() { return identityVerified; }
    public void setIdentityVerified(Boolean identityVerified) { this.identityVerified = Boolean.TRUE.equals(identityVerified); }

    public LocalDateTime getIdentityVerifiedAt() { return identityVerifiedAt; }
    public void setIdentityVerifiedAt(LocalDateTime identityVerifiedAt) { this.identityVerifiedAt = identityVerifiedAt; }

    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }

    public String getPlatformName() { return platformName; }
    public void setPlatformName(String platformName) { this.platformName = platformName; }

    public Integer getRemainingDays() { return remainingDays; }
    public void setRemainingDays(Integer remainingDays) { this.remainingDays = remainingDays; }

    public Boolean getAutoRenew() { return autoRenew; }
    public void setAutoRenew(Boolean autoRenew) { this.autoRenew = autoRenew; }

    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }

    public String getCloneInstanceId() { return cloneInstanceId; }
    public void setCloneInstanceId(String cloneInstanceId) {
        this.cloneInstanceId = (cloneInstanceId == null || cloneInstanceId.isBlank())
                ? null
                : cloneInstanceId.trim();
    }

    public Integer getCloneSequence() { return cloneSequence; }
    public void setCloneSequence(Integer cloneSequence) { this.cloneSequence = cloneSequence; }

    public Integer getLocalVirtualUserId() { return localVirtualUserId; }
    public void setLocalVirtualUserId(Integer localVirtualUserId) { this.localVirtualUserId = localVirtualUserId; }

    @JsonIgnore
    public String getCloneValidationCode() { return cloneValidationCode; }
    public void setCloneValidationCode(String cloneValidationCode) { this.cloneValidationCode = cloneValidationCode; }

    @JsonIgnore
    public String getCloneValidationHash() { return cloneValidationHash; }
    public void setCloneValidationHash(String cloneValidationHash) { this.cloneValidationHash = cloneValidationHash; }

    public Integer getCredentialVersion() { return credentialVersion; }
    public void setCredentialVersion(Integer credentialVersion) {
        this.credentialVersion = credentialVersion == null || credentialVersion < 1 ? 1 : credentialVersion;
    }

    public LocalDateTime getAuthStartAt() { return authStartAt; }
    public void setAuthStartAt(LocalDateTime authStartAt) { this.authStartAt = authStartAt; }

    public LocalDateTime getAuthExpireAt() { return authExpireAt; }
    public void setAuthExpireAt(LocalDateTime authExpireAt) { this.authExpireAt = authExpireAt; }

    public String getAuthorizationJti() { return authorizationJti; }
    public void setAuthorizationJti(String authorizationJti) { this.authorizationJti = authorizationJti; }

    public LocalDateTime getLastDeductedAt() { return lastDeductedAt; }
    public void setLastDeductedAt(LocalDateTime lastDeductedAt) { this.lastDeductedAt = lastDeductedAt; }

    public LocalDateTime getExpireAt() { return expireAt; }
    public void setExpireAt(LocalDateTime expireAt) { this.expireAt = expireAt; }

    public String getLoginStateProfile() { return loginStateProfile; }
    public void setLoginStateProfile(String loginStateProfile) {
        this.loginStateProfile = (loginStateProfile == null || loginStateProfile.isBlank())
                ? null
                : loginStateProfile.trim();
    }

    public Long getLoginStateSize() { return loginStateSize; }
    public void setLoginStateSize(Long loginStateSize) { this.loginStateSize = loginStateSize; }

    public String getLoginStateSha256() { return loginStateSha256; }
    public void setLoginStateSha256(String loginStateSha256) {
        this.loginStateSha256 = (loginStateSha256 == null || loginStateSha256.isBlank())
                ? null
                : loginStateSha256.trim();
    }

    public String getLoginStateManifest() { return loginStateManifest; }
    public void setLoginStateManifest(String loginStateManifest) { this.loginStateManifest = loginStateManifest; }

    @JsonIgnore
    public byte[] getLoginStateBlob() { return loginStateBlob; }
    public void setLoginStateBlob(byte[] loginStateBlob) { this.loginStateBlob = loginStateBlob; }

    public LocalDateTime getLoginStateUpdatedAt() { return loginStateUpdatedAt; }
    public void setLoginStateUpdatedAt(LocalDateTime loginStateUpdatedAt) { this.loginStateUpdatedAt = loginStateUpdatedAt; }

    public LocalDateTime getLoginStateArtifactCreatedAt() { return loginStateArtifactCreatedAt; }
    public void setLoginStateArtifactCreatedAt(LocalDateTime loginStateArtifactCreatedAt) { this.loginStateArtifactCreatedAt = loginStateArtifactCreatedAt; }

    public Byte getDeleted() { return deleted; }
    public void setDeleted(Byte deleted) { this.deleted = deleted == null ? 0 : deleted; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
