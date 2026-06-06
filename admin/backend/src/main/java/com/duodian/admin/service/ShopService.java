package com.duodian.admin.service;

import com.duodian.admin.entity.Shop;
import com.duodian.admin.repository.ShopRepository;
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

    public Shop create(Shop shop) {
        shop.setDeleted(ACTIVE);
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

    public Optional<Shop> findPendingByUserPackage(Long userId, String packageName) {
        return shopRepository.findFirstByUserIdAndPackageNameAndShopIdStartingWithAndDeleted(
                userId,
                packageName,
                "NEW-",
                ACTIVE
        );
    }

    public boolean hasPendingShopByPackage(Long userId, String packageName, String shopIdPrefix) {
        return shopRepository.existsByUserIdAndPackageNameAndShopIdStartingWithAndDeleted(userId, packageName, shopIdPrefix, ACTIVE);
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
        existing.setCloneInstanceId(shop.getCloneInstanceId());
        existing.setLastDeductedAt(shop.getLastDeductedAt());
        existing.setExpireAt(shop.getExpireAt());
        return shopRepository.save(existing);
    }

    public void delete(Long id) {
        Shop shop = shopRepository.findByIdAndDeleted(id, ACTIVE)
                .orElseThrow(() -> new RuntimeException("店铺不存在"));
        shop.setCloneInstanceId(null);
        shop.setDeleted(DELETED);
        shopRepository.save(shop);
    }
}
