package com.duodian.admin.service;

import com.duodian.admin.entity.Shop;
import com.duodian.admin.repository.ShopRepository;
import com.duodian.admin.repository.UserRepository;
import com.duodian.admin.util.ShopExpiration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ShopService {
    private static final byte ACTIVE = 0;
    private static final byte DELETED = 1;

    private final ShopRepository shopRepository;
    private final UserRepository userRepository;

    public ShopService(ShopRepository shopRepository, UserRepository userRepository) {
        this.shopRepository = shopRepository;
        this.userRepository = userRepository;
    }

    public List<Shop> findAll() {
        return shopRepository.findByDeleted(ACTIVE);
    }

    public List<Shop> findByChannelId(Long channelId) {
        return shopRepository.findByChannelIdAndDeleted(channelId, ACTIVE);
    }

    public Optional<Shop> findById(Long id) {
        return shopRepository.findByIdAndDeleted(id, ACTIVE);
    }

    public List<Shop> findByUserId(Long userId) {
        return shopRepository.findByUserIdAndDeletedOrderByCardSortOrderAscCreatedAtDescIdDesc(userId, ACTIVE);
    }

    public List<Shop> findByUserIdAndPackageName(Long userId, String packageName) {
        return shopRepository.findByUserIdAndPackageNameAndDeletedOrderByCardSortOrderAscCreatedAtDescIdDesc(userId, packageName, ACTIVE);
    }

    public List<Shop> findByPackageName(String packageName) {
        return shopRepository.findByPackageNameAndDeleted(packageName, ACTIVE);
    }

    public Page<Shop> search(
            Long userId,
            Long channelId,
            String packageName,
            String platform,
            String phone,
            String userKeyword,
            String shopName,
            Pageable pageable
    ) {
        return shopRepository.searchShops(
                ACTIVE,
                userId,
                channelId,
                normalize(packageName),
                normalize(platform),
                normalize(phone),
                normalize(userKeyword),
                normalize(shopName),
                pageable
        );
    }

    public Shop create(Shop shop) {
        shop.setDeleted(ACTIVE);
        stampChannel(shop);
        if (shop.getCardSortOrder() == null || shop.getCardSortOrder() <= 0) {
            shop.setCardSortOrder(nextCardSortOrder(shop.getUserId(), shop.getPackageName(), shop.getPlatform()));
        }
        ShopExpiration.applyRemainingDays(shop);
        return shopRepository.save(shop);
    }

    public Optional<Shop> findByUserIdAndShopId(Long userId, String shopId) {
        return shopRepository.findByUserIdAndShopIdAndDeleted(userId, shopId, ACTIVE);
    }

    public Optional<Shop> findByUserIdAndShopIdAndPackageName(Long userId, String shopId, String packageName) {
        return shopRepository.findByUserIdAndShopIdAndPackageNameAndDeleted(userId, shopId, packageName, ACTIVE);
    }

    public Optional<Shop> findByUserIdAndCloneInstanceId(Long userId, String cloneInstanceId) {
        return shopRepository.findByUserIdAndCloneInstanceIdAndDeleted(userId, cloneInstanceId, ACTIVE);
    }

    public Optional<Shop> findByCloneInstanceId(String cloneInstanceId) {
        return shopRepository.findByCloneInstanceIdAndDeleted(cloneInstanceId, ACTIVE);
    }

    public long countByUserIdAndPackageName(Long userId, String packageName) {
        return shopRepository.countByUserIdAndPackageNameAndDeleted(userId, packageName, ACTIVE);
    }

    public Optional<Shop> findPendingByUserPackage(Long userId, String packageName) {
        return shopRepository.findFirstByUserIdAndPackageNameAndShopIdStartingWithAndDeleted(
                userId,
                packageName,
                "NEW-",
                ACTIVE
        );
    }

    public Shop update(Long id, Shop shop) {
        Shop existing = shopRepository.findByIdAndDeleted(id, ACTIVE)
                .orElseThrow(() -> new RuntimeException("店铺不存在"));
        if (shop.getShopName() != null) {
            existing.setShopName(shop.getShopName());
        }
        if (shop.getChannelId() != null) {
            existing.setChannelId(shop.getChannelId());
        } else if (existing.getChannelId() == null) {
            stampChannel(existing);
        }
        if (shop.getShopId() != null) {
            existing.setShopId(resolveEditableShopId(shop.getShopId()));
        }
        if (Boolean.TRUE.equals(shop.getIdentityVerified()) || shop.getIdentityVerifiedAt() != null) {
            existing.setIdentityVerified(shop.getIdentityVerified());
        }
        if (shop.getIdentityVerifiedAt() != null) {
            existing.setIdentityVerifiedAt(shop.getIdentityVerifiedAt());
        }
        if (shop.getPlatform() != null) {
            existing.setPlatform(shop.getPlatform());
        }
        if (shop.getPlatformName() != null) {
            existing.setPlatformName(shop.getPlatformName());
        }
        if (shop.getCardSortOrder() != null) {
            existing.setCardSortOrder(shop.getCardSortOrder());
        }
        existing.setRemainingDays(shop.getRemainingDays());
        existing.setAutoRenew(shop.getAutoRenew());
        existing.setPackageName(shop.getPackageName());
        if (shop.getCloneInstanceId() != null &&
                (existing.getCloneInstanceId() == null || existing.getCloneInstanceId().equals(shop.getCloneInstanceId()))) {
            existing.setCloneInstanceId(shop.getCloneInstanceId());
        }
        if (shop.getCloneSequence() != null) {
            existing.setCloneSequence(shop.getCloneSequence());
        }
        if (shop.getLocalVirtualUserId() != null) {
            existing.setLocalVirtualUserId(shop.getLocalVirtualUserId());
        }
        if (shop.getCloneValidationCode() != null) {
            existing.setCloneValidationCode(shop.getCloneValidationCode());
        }
        if (shop.getCloneValidationHash() != null) {
            existing.setCloneValidationHash(shop.getCloneValidationHash());
        }
        if (shop.getCredentialVersion() != null) {
            existing.setCredentialVersion(shop.getCredentialVersion());
        }
        if (shop.getAuthStartAt() != null) {
            existing.setAuthStartAt(shop.getAuthStartAt());
        }
        if (shop.getAuthExpireAt() != null) {
            existing.setAuthExpireAt(shop.getAuthExpireAt());
        }
        if (shop.getAuthorizationJti() != null) {
            existing.setAuthorizationJti(shop.getAuthorizationJti());
        }
        if (shop.getLastDeductedAt() != null) {
            existing.setLastDeductedAt(shop.getLastDeductedAt());
        }
        if (shop.getExpireAt() != null) {
            existing.setExpireAt(shop.getExpireAt());
        }
        existing.setWechatReceiverId(shop.getWechatReceiverId());
        existing.setWechatReceiverName(shop.getWechatReceiverName());
        existing.setWechatReceiverType(shop.getWechatReceiverType());
        existing.setRemark(shop.getRemark());
        ShopExpiration.applyRemainingDays(existing);
        return shopRepository.save(existing);
    }

    public List<Shop> reorderUserPlatformShops(Long userId, List<Long> orderedShopIds) {
        if (userId == null) {
            throw new IllegalArgumentException("未登录");
        }
        List<Long> ids = normalizeIds(orderedShopIds);
        if (ids.isEmpty()) {
            return findByUserId(userId);
        }
        List<Shop> shops = shopRepository.findByIdInAndDeleted(ids, ACTIVE);
        if (shops.size() != ids.size()) {
            throw new IllegalArgumentException("排序列表中包含不存在的店铺");
        }
        String packageName = null;
        String platform = null;
        boolean packageInitialized = false;
        boolean platformInitialized = false;
        for (Shop shop : shops) {
            if (!userId.equals(shop.getUserId())) {
                throw new IllegalArgumentException("只能调整自己的店铺排序");
            }
            String currentPackage = normalize(shop.getPackageName());
            String currentPlatform = normalize(shop.getPlatform());
            if (!packageInitialized) {
                packageName = currentPackage;
                packageInitialized = true;
            } else if (!equalsNullable(packageName, currentPackage)) {
                throw new IllegalArgumentException("一次只能调整同一平台下的店铺排序");
            }
            if (!platformInitialized) {
                platform = currentPlatform;
                platformInitialized = true;
            } else if (!equalsNullable(platform, currentPlatform)) {
                throw new IllegalArgumentException("一次只能调整同一平台下的店铺排序");
            }
        }

        List<Shop> scopeShops = platformScopeShops(userId, packageName, platform);
        LinkedHashSet<Long> completeIds = new LinkedHashSet<>(ids);
        for (Shop shop : scopeShops) {
            completeIds.add(shop.getId());
        }

        Map<Long, Shop> shopById = scopeShops.stream()
                .collect(Collectors.toMap(Shop::getId, Function.identity()));
        List<Shop> ordered = new ArrayList<>();
        int order = 10;
        for (Long id : completeIds) {
            Shop shop = shopById.get(id);
            if (shop == null) {
                continue;
            }
            shop.setCardSortOrder(order);
            ordered.add(shop);
            order += 10;
        }
        shopRepository.saveAll(ordered);
        return findByUserId(userId);
    }

    public int refreshRemainingDays() {
        List<Shop> shops = shopRepository.findActiveShopsWithExpiration(ACTIVE);
        int updated = 0;
        for (Shop shop : shops) {
            if (ShopExpiration.applyRemainingDays(shop)) {
                shopRepository.save(shop);
                updated++;
            }
        }
        return updated;
    }

    public Shop updateLoginState(
            Long id,
            String profile,
            String manifest,
            byte[] blob,
            String sha256,
            LocalDateTime artifactCreatedAt
    ) {
        Shop existing = shopRepository.findByIdAndDeleted(id, ACTIVE)
                .orElseThrow(() -> new RuntimeException("店铺不存在"));
        LocalDateTime incomingCreatedAt = artifactCreatedAt == null ? LocalDateTime.now() : artifactCreatedAt;
        LocalDateTime storedCreatedAt = existing.getLoginStateArtifactCreatedAt();
        String normalizedSha256 = normalize(sha256);
        if (storedCreatedAt != null && incomingCreatedAt.isBefore(storedCreatedAt)) {
            return existing;
        }
        if (storedCreatedAt != null
                && incomingCreatedAt.isEqual(storedCreatedAt)
                && existing.getLoginStateSha256() != null
                && normalizedSha256 != null
                && !existing.getLoginStateSha256().equalsIgnoreCase(normalizedSha256)) {
            return existing;
        }
        existing.setLoginStateProfile(normalize(profile));
        existing.setLoginStateManifest(manifest);
        existing.setLoginStateBlob(blob);
        existing.setLoginStateSize(blob == null ? null : (long) blob.length);
        existing.setLoginStateSha256(normalizedSha256);
        existing.setLoginStateUpdatedAt(blob == null ? null : LocalDateTime.now());
        existing.setLoginStateArtifactCreatedAt(blob == null ? null : incomingCreatedAt);
        return shopRepository.save(existing);
    }

    public void delete(Long id) {
        Shop shop = shopRepository.findByIdAndDeleted(id, ACTIVE)
                .orElseThrow(() -> new RuntimeException("店铺不存在"));
        shop.setCloneInstanceId(null);
        shop.setDeleted(DELETED);
        shopRepository.save(shop);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private void stampChannel(Shop shop) {
        if (shop.getChannelId() != null || shop.getUserId() == null) {
            return;
        }
        userRepository.findByIdAndDeleted(shop.getUserId(), ACTIVE)
                .ifPresent(user -> shop.setChannelId(user.getChannelId()));
    }

    private Integer nextCardSortOrder(Long userId, String packageName, String platform) {
        if (userId == null) {
            return 0;
        }
        return platformScopeShops(userId, packageName, platform).stream()
                .map(Shop::getCardSortOrder)
                .filter(value -> value != null)
                .max(Integer::compareTo)
                .orElse(0) + 10;
    }

    private List<Shop> platformScopeShops(Long userId, String packageName, String platform) {
        String normalizedPackage = normalize(packageName);
        String normalizedPlatform = normalize(platform);
        return findByUserId(userId).stream()
                .filter(shop -> equalsNullable(normalizedPackage, normalize(shop.getPackageName())))
                .filter(shop -> equalsNullable(normalizedPlatform, normalize(shop.getPlatform())))
                .toList();
    }

    private List<Long> normalizeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(new LinkedHashSet<>(ids.stream()
                .filter(id -> id != null && id > 0)
                .toList()));
    }

    private boolean equalsNullable(String left, String right) {
        if (left == null) {
            return right == null;
        }
        return left.equals(right);
    }

    private String resolveEditableShopId(String requestedShopId) {
        String normalizedRequestedShopId = normalize(requestedShopId);
        if (normalizedRequestedShopId == null) {
            return "-";
        }
        return normalizedRequestedShopId;
    }

}
