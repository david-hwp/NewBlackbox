package com.duodian.admin.service;

import com.duodian.admin.entity.Shop;
import com.duodian.admin.repository.ShopRepository;
import com.duodian.admin.util.ShopExpiration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ShopService {
    private static final byte ACTIVE = 0;
    private static final byte DELETED = 1;

    private final ShopRepository shopRepository;

    public ShopService(ShopRepository shopRepository) {
        this.shopRepository = shopRepository;
    }

    public List<Shop> findAll() {
        return shopRepository.findByDeleted(ACTIVE);
    }

    public Optional<Shop> findById(Long id) {
        return shopRepository.findByIdAndDeleted(id, ACTIVE);
    }

    public List<Shop> findByUserId(Long userId) {
        return shopRepository.findByUserIdAndDeleted(userId, ACTIVE);
    }

    public List<Shop> findByUserIdAndPackageName(Long userId, String packageName) {
        return shopRepository.findByUserIdAndPackageNameAndDeleted(userId, packageName, ACTIVE);
    }

    public List<Shop> findByPackageName(String packageName) {
        return shopRepository.findByPackageNameAndDeleted(packageName, ACTIVE);
    }

    public Page<Shop> search(
            Long userId,
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
        existing.setShopName(shop.getShopName());
        existing.setShopId(shop.getShopId());
        existing.setPlatform(shop.getPlatform());
        existing.setPlatformName(shop.getPlatformName());
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
        ShopExpiration.applyRemainingDays(existing);
        return shopRepository.save(existing);
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
}
