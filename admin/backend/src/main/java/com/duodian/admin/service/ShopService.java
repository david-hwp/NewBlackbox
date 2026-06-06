package com.duodian.admin.service;

import com.duodian.admin.entity.Shop;
import com.duodian.admin.repository.ShopRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ShopService {

    private final ShopRepository shopRepository;

    public ShopService(ShopRepository shopRepository) {
        this.shopRepository = shopRepository;
    }

    public List<Shop> findAll() {
        return shopRepository.findAll();
    }

    public Optional<Shop> findById(Long id) {
        return shopRepository.findById(id);
    }

    public List<Shop> findByUserId(Long userId) {
        return shopRepository.findByUserId(userId);
    }

    public List<Shop> findByUserIdAndPackageName(Long userId, String packageName) {
        return shopRepository.findByUserIdAndPackageName(userId, packageName);
    }

    public List<Shop> findByPackageName(String packageName) {
        return shopRepository.findByPackageName(packageName);
    }

    public Shop create(Shop shop) {
        return shopRepository.save(shop);
    }

    public Optional<Shop> findByUserIdAndShopId(Long userId, String shopId) {
        return shopRepository.findByUserIdAndShopId(userId, shopId);
    }

    public Optional<Shop> findByUserIdAndShopIdAndPackageName(Long userId, String shopId, String packageName) {
        return shopRepository.findByUserIdAndShopIdAndPackageName(userId, shopId, packageName);
    }

    public Optional<Shop> findByUserIdAndCloneInstanceId(Long userId, String cloneInstanceId) {
        return shopRepository.findByUserIdAndCloneInstanceId(userId, cloneInstanceId);
    }

    public Optional<Shop> findPendingByUserPackage(Long userId, String packageName) {
        return shopRepository.findFirstByUserIdAndPackageNameAndShopIdStartingWith(
                userId,
                packageName,
                "NEW-"
        );
    }

    public boolean hasPendingShopByPackage(Long userId, String packageName, String shopIdPrefix) {
        return shopRepository.existsByUserIdAndPackageNameAndShopIdStartingWith(userId, packageName, shopIdPrefix);
    }

    public Shop update(Long id, Shop shop) {
        Shop existing = shopRepository.findById(id)
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
        shopRepository.deleteById(id);
    }
}
